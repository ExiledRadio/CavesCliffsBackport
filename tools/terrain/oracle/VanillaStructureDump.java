package net.minecraft.world.level.levelgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.zip.GZIPOutputStream;

/**
 * Vanilla 1.18.2 structure starts for the Caves and Cliffs Backport's StructureCheck: for every overworld structure
 * set, every candidate chunk of its placement in a region (random spread: the grid's potential chunk per cell;
 * concentric rings: all ring positions), ChunkGenerator.createStructures' choice among the set's structures run
 * with vanilla's own ConfiguredStructureFeature.generate (pieces and all; templates from the jar). The biome
 * search answers cold (the RTree's warm start is switched off; see VanillaWorld).
 *
 * Writes structures_SEED.bin.gz: int sets; per set UTF name, int count, then cx, cz, structure index per start.
 * Also writes structure_tables.txt once (biome tags as biome-id lists, the start pools' template boxes per
 * rotation, ruined portal template boxes per rotation and mirror, ocean and river biomes), from which the core's
 * tables were written.
 *
 * Arguments: outDir regionChunks everyChunkRegion [seeds...]   (region: chunks -R..R-1 in x and z)
 */
public final class VanillaStructureDump {
    static final String[] SETS = {"villages", "desert_pyramids", "igloos", "jungle_temples", "swamp_huts",
        "pillager_outposts", "ocean_monuments", "woodland_mansions", "buried_treasures", "mineshafts",
        "ruined_portals", "shipwrecks", "ocean_ruins", "strongholds"};

    /** A template manager over the jar's structure files, without a level (constructor skipped). */
    static final class JarTemplates extends StructureManager {
        static final Map<ResourceLocation, Optional<StructureTemplate>> CACHE = new ConcurrentHashMap<>();

        JarTemplates() {
            super(null, null, null);
        }

        @Override
        public Optional<StructureTemplate> get(ResourceLocation id) {
            return CACHE.computeIfAbsent(id, k -> {
                String path = "/data/" + k.getNamespace() + "/structures/" + k.getPath() + ".nbt";
                try (InputStream in = VanillaStructureDump.class.getResourceAsStream(path)) {
                    if (in == null) {
                        throw new IllegalStateException("no template " + path);
                    }
                    CompoundTag tag = NbtIo.readCompressed(in);
                    StructureTemplate t = new StructureTemplate();
                    t.load(tag);
                    return Optional.of(t);
                } catch (java.io.IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }

        @Override
        public StructureTemplate getOrCreate(ResourceLocation id) {
            return get(id).get();
        }
    }

    static StructureManager templates() throws Exception {
        Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        f.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
        return (StructureManager) unsafe.allocateInstance(JarTemplates.class);
    }

    /** Makes every RTree search cold: the per-thread last result always reads as none. */
    static void coldSearches(VanillaWorld world) throws Exception {
        Field parametersField = net.minecraft.world.level.biome.MultiNoiseBiomeSource.class.getDeclaredField("parameters");
        parametersField.setAccessible(true);
        Object parameters = parametersField.get(world.biomeSource);
        Field indexField = net.minecraft.world.level.biome.Climate.ParameterList.class.getDeclaredField("index");
        indexField.setAccessible(true);
        Object tree = indexField.get(parameters);
        Field lastField = tree.getClass().getDeclaredField("lastResult");
        lastField.setAccessible(true);
        lastField.set(tree, new ThreadLocal<Object>() {
            @Override
            public Object get() {
                return null;
            }

            @Override
            public void set(Object value) {
            }
        });
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        int region = Integer.parseInt(args[1]);
        int everyRegion = Integer.parseInt(args[2]);
        long[] seeds = {42L, 12345L, -7346534567L, 8675309L};
        if (args.length > 3) {
            seeds = new long[args.length - 3];
            for (int i = 3; i < args.length; i++) {
                seeds[i - 3] = Long.parseLong(args[i]);
            }
        }
        StructureManager templates = templates();
        boolean tablesWritten = false;
        for (long seed : seeds) {
            VanillaWorld world = new VanillaWorld(seed);
            VanillaWorld.bindBiomeTags();
            coldSearches(world);
            Registry<StructureSet> sets = VanillaWorld.access.registryOrThrow(Registry.STRUCTURE_SET_REGISTRY);
            if (!tablesWritten) {
                writeTables(world, sets, templates, new File(out, "structure_tables.txt"));
                tablesWritten = true;
            }
            long start = System.nanoTime();
            try (DataOutputStream w = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(
                    new FileOutputStream(new File(out, "structures_" + seed + ".bin.gz")), 1 << 16), 1 << 16))) {
                String only = System.getProperty("sets");
                List<String> picked = new ArrayList<>();
                for (String name : SETS) {
                    if (only == null || java.util.Arrays.asList(only.split(",")).contains(name)) {
                        picked.add(name);
                    }
                }
                w.writeInt(picked.size());
                for (String name : picked) {
                    long t = System.nanoTime();
                    StructureSet set = sets.get(new ResourceLocation(name));
                    StructurePlacement placement = set.placement();
                    List<ChunkPos> candidates = new ArrayList<>();
                    if (placement instanceof ConcentricRingsStructurePlacement rings) {
                        candidates.addAll(world.generator.getRingPositionsFor(rings));
                    } else {
                        RandomSpreadStructurePlacement spread = (RandomSpreadStructurePlacement) placement;
                        int r = spread.spacing() == 1 ? everyRegion : region;
                        int s = spread.spacing();
                        for (int gx = Math.floorDiv(-r, s); gx <= Math.floorDiv(r - 1, s); gx++) {
                            for (int gz = Math.floorDiv(-r, s); gz <= Math.floorDiv(r - 1, s); gz++) {
                                ChunkPos p = spread.getPotentialFeatureChunk(seed, gx * s, gz * s);
                                if (p.x >= -r && p.x < r && p.z >= -r && p.z < r) {
                                    candidates.add(p);
                                }
                            }
                        }
                    }
                    List<int[]> starts = new ArrayList<>();
                    for (ChunkPos p : candidates) {
                        int chosen = choose(world, set, templates, seed, p);
                        if (chosen >= 0) {
                            starts.add(new int[]{p.x, p.z, chosen});
                        }
                    }
                    w.writeUTF(name);
                    w.writeInt(starts.size());
                    for (int[] s : starts) {
                        w.writeInt(s[0]);
                        w.writeInt(s[1]);
                        w.writeInt(s[2]);
                    }
                    System.out.println("seed " + seed + " " + name + ": " + candidates.size() + " candidates, "
                        + starts.size() + " starts, " + (System.nanoTime() - t) / 1000000L + " ms");
                }
            }
            System.out.println("seed " + seed + ": " + (System.nanoTime() - start) / 1000000L + " ms");
        }
    }

    /** ChunkGenerator.createStructures for one set at a feature chunk: the index of the structure started, or -1. */
    static int choose(VanillaWorld world, StructureSet set, StructureManager templates, long seed, ChunkPos pos) {
        List<StructureSet.StructureSelectionEntry> list = set.structures();
        if (list.size() == 1) {
            return tryGenerate(world, list.get(0), templates, seed, pos) ? 0 : -1;
        }
        List<StructureSet.StructureSelectionEntry> remaining = new ArrayList<>(list);
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, pos.x, pos.z);
        int total = 0;
        for (StructureSet.StructureSelectionEntry e : remaining) {
            total += e.weight();
        }
        while (!remaining.isEmpty()) {
            int j = random.nextInt(total);
            int k = 0;
            for (StructureSet.StructureSelectionEntry e : remaining) {
                j -= e.weight();
                if (j < 0) {
                    break;
                }
                ++k;
            }
            StructureSet.StructureSelectionEntry e = remaining.get(k);
            if (tryGenerate(world, e, templates, seed, pos)) {
                return list.indexOf(e);
            }
            remaining.remove(k);
            total -= e.weight();
        }
        return -1;
    }

    static boolean tryGenerate(VanillaWorld world, StructureSet.StructureSelectionEntry entry, StructureManager templates,
                               long seed, ChunkPos pos) {
        ConfiguredStructureFeature<?, ?> feature = entry.structure().value();
        HolderSet<Biome> biomes = feature.biomes();
        Predicate<Holder<Biome>> predicate = biomes::contains;
        StructureStart start = feature.generate(VanillaWorld.access, world.generator, world.biomeSource, templates, seed, pos, 0,
            VanillaWorld.HEIGHT, predicate);
        return start.isValid();
    }

    static void writeTables(VanillaWorld world, Registry<StructureSet> sets, StructureManager templates, File file) throws Exception {
        try (PrintWriter p = new PrintWriter(file, "UTF-8")) {
            p.println("# biome tags: set structure-index feature-name biome-ids");
            for (String name : SETS) {
                StructureSet set = sets.get(new ResourceLocation(name));
                int i = 0;
                for (StructureSet.StructureSelectionEntry e : set.structures()) {
                    ConfiguredStructureFeature<?, ?> f = e.structure().value();
                    StringBuilder b = new StringBuilder();
                    for (int id = 0; id < world.holders.length; id++) {
                        if (f.biomes().contains(world.holders[id])) {
                            b.append(' ').append(id);
                        }
                    }
                    p.println("tag " + name + " " + i + " " + e.structure().unwrapKey().get().location().getPath()
                        + " weight " + e.weight() + " :" + b);
                    i++;
                }
            }
            StringBuilder ocean = new StringBuilder();
            for (int id = 0; id < world.holders.length; id++) {
                Biome.BiomeCategory c = Biome.getBiomeCategory(world.holders[id]);
                if (c == Biome.BiomeCategory.OCEAN || c == Biome.BiomeCategory.RIVER) {
                    ocean.append(' ').append(id);
                }
            }
            p.println("ocean_or_river :" + ocean);
            Registry<StructureTemplatePool> pools = VanillaWorld.access.registryOrThrow(Registry.TEMPLATE_POOL_REGISTRY);
            net.minecraft.data.worldgen.Pools.bootstrap();
            String[] startPools = {"village/plains/town_centers", "village/desert/town_centers", "village/savanna/town_centers",
                "village/snowy/town_centers", "village/taiga/town_centers", "pillager_outpost/base_plates"};
            p.println("# start pools: name, then per expanded entry: groundLevelDelta and box minX maxX minZ maxZ for rotations 0..3, or empty");
            for (String poolName : startPools) {
                StructureTemplatePool pool = pools.get(new ResourceLocation(poolName));
                Field templatesField = StructureTemplatePool.class.getDeclaredField("templates");
                templatesField.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<StructurePoolElement> entries = (List<StructurePoolElement>) templatesField.get(pool);
                p.println("pool " + poolName + " " + entries.size());
                for (StructurePoolElement e : entries) {
                    if (e == EmptyPoolElement.INSTANCE) {
                        p.println("  empty");
                        continue;
                    }
                    StringBuilder b = new StringBuilder("  " + e.getGroundLevelDelta());
                    for (Rotation r : Rotation.values()) {
                        BoundingBox box = e.getBoundingBox(templates, BlockPos.ZERO, r);
                        b.append(' ').append(box.minX()).append(' ').append(box.maxX()).append(' ').append(box.minZ())
                            .append(' ').append(box.maxZ());
                    }
                    p.println(b);
                }
            }
            String[] portals = {"ruined_portal/portal_1", "ruined_portal/portal_2", "ruined_portal/portal_3", "ruined_portal/portal_4",
                "ruined_portal/portal_5", "ruined_portal/portal_6", "ruined_portal/portal_7", "ruined_portal/portal_8",
                "ruined_portal/portal_9", "ruined_portal/portal_10", "ruined_portal/giant_portal_1",
                "ruined_portal/giant_portal_2", "ruined_portal/giant_portal_3"};
            p.println("# portals: name, then per rotation 0..3 and mirror NONE, FRONT_BACK: box minX maxX minZ maxZ ySpan");
            for (String name : portals) {
                StructureTemplate t = templates.getOrCreate(new ResourceLocation(name));
                BlockPos pivot = new BlockPos(t.getSize().getX() / 2, 0, t.getSize().getZ() / 2);
                StringBuilder b = new StringBuilder("portal " + name);
                for (Rotation r : Rotation.values()) {
                    for (Mirror m : new Mirror[]{Mirror.NONE, Mirror.FRONT_BACK}) {
                        BoundingBox box = t.getBoundingBox(BlockPos.ZERO, r, pivot, m);
                        b.append(' ').append(box.minX()).append(' ').append(box.maxX()).append(' ').append(box.minZ())
                            .append(' ').append(box.maxZ()).append(' ').append(box.getYSpan());
                    }
                }
                p.println(b);
            }
        }
    }
}
