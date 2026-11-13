package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.backportcore.structure.ScatteredPlacement;
import com.exiledradio.backportcore.structure.ScatteredStructure;
import com.exiledradio.backportcore.structure.StartRule;
import com.exiledradio.backportcore.structure.StartRules;
import com.exiledradio.cavesandcliffsbackport.common.CaveRegions;
import com.exiledradio.cavesandcliffsbackport.glowsquid.GlowSquidContent;
import com.exiledradio.cavesandcliffsbackport.height.Heights;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernGenerator;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernStructures;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernTerrain;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Beardifier;
import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeZoom;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Biomes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ChunkFiller;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Structures;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraft.world.gen.structure.StructureVillagePieces;

import java.util.List;
import java.util.Random;

/**
 * The 1.18 generator's structure terrain, family structures and underground spawning, in a Caves &amp; Cliffs world
 * ({@code sh tools/height/selftest.sh height,terrain}); in a Default world nothing (the generator is not in charge).
 *
 * <ul>
 *   <li>Beardifier: a village's houses are on the ground at layout (the noise's average ground) and the fill with
 *       the generator's beard puts solid ground under them and air over them; a stronghold's pieces are buried;
 *       1.18's reference order is fastutil's.</li>
 *   <li>Family structures: Backport Core's start rule for shipwrecks, ocean ruins, buried treasure, ruined portals
 *       and outposts is the core's 1.18 placement, on its own grid; a ScatteredStructure follows it (starts, the
 *       nearest search, the variant).</li>
 *   <li>Spawning: 1.18's biome in three dimensions (the core's zoom, exactly); lush and dripstone caves spawn 1.18's
 *       lists there (no animals, bats, zombies), the surface its own; the axolotls' lush caves are the biome's.</li>
 *   <li>Held chunks survive an encode and decode exactly; the generator writes some when the world saves.</li>
 * </ul>
 */
public final class TerrainGapsSelfTest implements SelfTest {

    /** Logs a passing check's numbers too (Check only logs failures). */
    private static boolean ok(Check check, boolean condition, String what) {
        com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport.LOGGER.info("SELFTEST INFO {}", what);
        return check.ok(condition, what);
    }

    @Override
    public String name() {
        return "terrain";
    }

    @Override
    public void run(Check check) throws Exception {
        WorldServer world = check.world;
        if (!Heights.isExtended(world) || !ModernTerrain.owns(world)) {
            check.ok(StartRules.of(world, com.exiledradio.backportcore.structure.Structures.SHIPWRECK) == null,
                    "a Default world's generator decides no family structure's start");
            return;
        }
        ModernGenerator generator = (ModernGenerator) world.getChunkProvider().chunkGenerator;
        OverworldNoise noise = new OverworldNoise(world.getSeed(), generator.editions());
        ColumnCache columns = new ColumnCache(noise);
        Climate climate = new Climate(generator.editions());
        Structures core = new Structures(noise, columns, new BiomeSource(noise, columns, climate));
        referenceOrder(check);
        village(check, world, generator, noise, core);
        stronghold(check, generator, noise, core);
        family(check, world, core);
        installed(check, world, generator, core);
        spawning(check, world, generator, noise, climate);
        held(check, world, generator);
    }

    // ---------------------------------------------------------------------------------------------
    // Beardifier

    private static void referenceOrder(Check check) {
        Random r = new Random(1718);
        int wrong = 0;
        for (int round = 0; round < 2000; round++) {
            int n = 1 + r.nextInt(6);
            long[] keys = new long[n];
            int cx = r.nextInt(4000) - 2000;
            int cz = r.nextInt(4000) - 2000;
            for (int i = 0; i < n; i++) {
                keys[i] = ChunkPos.asLong(cx + r.nextInt(17) - 8, cz + r.nextInt(17) - 8);
            }
            keys = java.util.Arrays.stream(keys).distinct().toArray();
            // createReferences' scan: x outer, z inner.
            long[] scan = keys.clone();
            java.util.Arrays.sort(scan);
            Long[] boxed = java.util.Arrays.stream(scan).boxed().toArray(Long[]::new);
            java.util.Arrays.sort(boxed, (a, b) -> {
                int c = Integer.compare((int) (long) a, (int) (long) b);
                return c != 0 ? c : Integer.compare((int) (a >> 32), (int) (b >> 32));
            });
            LongOpenHashSet set = new LongOpenHashSet();
            for (Long k : boxed) {
                set.add(k);
            }
            long[] expected = new long[set.size()];
            int at = 0;
            for (LongIterator it = set.iterator(); it.hasNext(); ) {
                expected[at++] = it.nextLong();
            }
            if (!java.util.Arrays.equals(expected, ModernStructures.referenceOrder(keys, keys.length))) {
                wrong++;
            }
        }
        check.eq(0, wrong, "a chunk's structure references in fastutil's LongOpenHashSet order (2000 sets)");
    }

    /** The village nearest the world's origin: houses on the ground at layout, ground and air by the beard. */
    private static void village(Check check, WorldServer world, ModernGenerator generator, OverworldNoise noise, Structures core) {
        if (!generator.ownVillages()) {
            backportVillage(check, world, generator, core);
            return;
        }
        List<int[]> starts = core.starts(Structures.VILLAGES, -96, -96, 96, 96);
        int[] best = null;
        for (int[] s : starts) {
            if (best == null || (long) s[0] * s[0] + (long) s[1] * s[1] < (long) best[0] * best[0] + (long) best[1] * best[1]) {
                best = s;
            }
        }
        if (!check.ok(best != null, "a 1.18 village start within 96 chunks of 0, 0")) {
            return;
        }
        List<StructureStart> near = generator.adaptingStartsNear(best[0], best[1], false);
        StructureStart village = null;
        for (StructureStart s : near) {
            if (s.getChunkPosX() == best[0] && s.getChunkPosZ() == best[1]) {
                village = s;
            }
        }
        if (!check.ok(village != null, "the village at chunk " + best[0] + "," + best[1] + " is laid out for the terrain")) {
            return;
        }
        int houses = 0;
        int wrongGround = 0;
        StructureComponent house = null;
        int floor = world.provider.getAverageGroundLevel() - 1;
        for (StructureComponent c : village.getComponents()) {
            if (!ModernStructures.grounded(c)) {
                continue;
            }
            houses++;
            StructureBoundingBox b = c.getBoundingBox();
            long sum = 0;
            int count = 0;
            for (int z = b.minZ; z <= b.maxZ; z++) {
                for (int x = b.minX; x <= b.maxX; x++) {
                    sum += Math.max(generator.surface(x, z), floor);
                    count++;
                }
            }
            wrongGround += ModernStructures.groundOf(c) != (int) (sum / count) ? 1 : 0;
            if (house == null && !(c instanceof StructureVillagePieces.Well) && b.getXSize() >= 5 && b.getZSize() >= 5) {
                house = c;
            }
        }
        ok(check, houses >= 3, "the village has houses set on the ground at layout (" + houses + ")");
        check.eq(0, wrongGround, "each house's ground is its footprint's average noise ground");
        if (house == null) {
            check.fail("no house of 5x5 or more in the village");
            return;
        }
        StructureBoundingBox b = house.getBoundingBox();
        int ground = ModernStructures.groundOf(house);
        int cx = ((b.minX + b.maxX) / 2) >> 4;
        int cz = ((b.minZ + b.maxZ) / 2) >> 4;
        Beardifier beard = generator.beards().forChunk(cx, cz);
        check.ok(beard != null, "the house's chunk " + cx + "," + cz + " gets a beard");
        ChunkFiller filler = new ChunkFiller(noise, new ColumnCache(noise));
        char[] with = new char[Pipeline.VOLUME];
        char[] without = new char[Pipeline.VOLUME];
        filler.fill(cx, cz, with, beard);
        filler.fill(cx, cz, without);
        int changed = 0;
        for (int i = 0; i < with.length; i++) {
            changed += with[i] != without[i] ? 1 : 0;
        }
        ok(check, changed > 0, "the beard changes the village chunk's terrain (" + changed + " blocks)");
        int[] good = new int[2];
        int columns = 0;
        for (int x = Math.max(b.minX + 1, cx << 4); x <= Math.min(b.maxX - 1, (cx << 4) + 15); x++) {
            for (int z = Math.max(b.minZ + 1, cz << 4); z <= Math.min(b.maxZ - 1, (cz << 4) + 15); z++) {
                columns++;
                char[][] both = {with, without};
                for (int k = 0; k < 2; k++) {
                    int at = ((x & 15) * 16 + (z & 15)) * Pipeline.HEIGHT - Pipeline.MIN_Y;
                    boolean solid = both[k][at + ground - 1] != BlockCodes.AIR && !BlockCodes.liquid(both[k][at + ground - 1]);
                    boolean air = both[k][at + ground] == BlockCodes.AIR && both[k][at + ground + 1] == BlockCodes.AIR
                            && both[k][at + ground + 2] == BlockCodes.AIR;
                    good[k] += solid && air ? 1 : 0;
                }
            }
        }
        ok(check, columns > 0 && good[0] * 10 >= columns * 9, "under the house solid ground at " + (ground - 1)
                + " and air from " + ground + " in " + good[0] + " of " + columns + " columns with the beard (" + good[1]
                + " without)");
    }

    /**
     * With a backport building the villages in place of 1.12's (Village &amp; Pillage's 1.14 villages, through Backport
     * Core's StructureGenerators): the first 1.18 village start near the spawn, generated, is the backport's village,
     * of the type 1.18 names; its houses stand on solid ground (the beard filled under them); no 1.12 village is laid
     * out or saved; the terrain adapts to the village; Core's /locate and 1.12's /locate Village name 1.18 starts.
     */
    private static void backportVillage(Check check, WorldServer world, ModernGenerator generator, Structures core) {
        ok(check, com.exiledradio.backportcore.structure.StructureGenerators.replaced(
                com.exiledradio.backportcore.structure.Structures.VILLAGE),
                "villages are a backport's: " + generator.villageGenerator().getClass().getName());
        BlockPos spawn = world.getSpawnPoint();
        int scx = spawn.getX() >> 4;
        int scz = spawn.getZ() >> 4;
        int[] best = null;
        for (int[] s : core.starts(Structures.VILLAGES, scx - 96, scz - 96, scx + 96, scz + 96)) {
            long d = (long) (s[0] - scx) * (s[0] - scx) + (long) (s[1] - scz) * (s[1] - scz);
            if (best == null || d < (long) (best[0] - scx) * (best[0] - scx) + (long) (best[1] - scz) * (best[1] - scz)) {
                best = s;
            }
        }
        if (!check.ok(best != null, "a 1.18 village start within 96 chunks of the spawn")) {
            return;
        }
        String type = Structures.STRUCTURE_NAMES[Structures.VILLAGES][best[2]];
        com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport.LOGGER.info(
                "SELFTEST INFO first 1.18 village near the spawn {}: {} at chunk {},{} (block {}, {})", spawn, type, best[0],
                best[1], best[0] << 4, best[1] << 4);
        ok(check, generator.adaptingStartsNear(best[0], best[1], false).isEmpty(), "no 1.12 village is laid out there");
        // The rule the backport asks names the same start.
        StartRule rule = StartRules.of(world, com.exiledradio.backportcore.structure.Structures.VILLAGE);
        ok(check, rule != null && type.equals(rule.startAt(best[0], best[1])) && rule.grid().spacing() == 34,
                "Core's village rule is 1.18's (" + (rule == null ? null : rule.startAt(best[0], best[1])) + ")");
        // Generate and populate the chunks around the start.
        int r = 6;
        for (int x = best[0] - r - 1; x <= best[0] + r + 1; x++) {
            for (int z = best[1] - r - 1; z <= best[1] + r + 1; z++) {
                world.getChunkProvider().provideChunk(x, z);
            }
        }
        StructureStart start = null;
        for (com.exiledradio.backportcore.structure.TerrainAdaptation.Entry e
                : com.exiledradio.backportcore.structure.TerrainAdaptation.registered()) {
            if (e.kind.equals(com.exiledradio.backportcore.structure.Structures.VILLAGE)) {
                final int[] at = best;
                final StructureStart[] found = new StructureStart[1];
                e.planner.startsNear(world, at[0], at[1], s -> {
                    if (s.getChunkPosX() == at[0] && s.getChunkPosZ() == at[1]) {
                        found[0] = s;
                    }
                });
                start = found[0];
            }
        }
        if (!check.ok(start != null && start.getClass().getSimpleName().equals("ModernVillageStart"),
                "the start at chunk " + best[0] + "," + best[1] + " is a 1.14 village: "
                        + (start == null ? null : start.getClass().getName()))) {
            return;
        }
        int rigid = 0;
        int standing = 0;
        int corners = 0;
        int oldPieces = 0;
        int[] spread = {Integer.MAX_VALUE, Integer.MIN_VALUE};
        for (StructureComponent c : start.getComponents()) {
            oldPieces += c instanceof StructureVillagePieces.Village ? 1 : 0;
            if (!(c instanceof com.exiledradio.backportcore.structure.TerrainAdaptation.AdaptingPiece)
                    || !((com.exiledradio.backportcore.structure.TerrainAdaptation.AdaptingPiece) c).rigid()) {
                continue;
            }
            StructureBoundingBox b = c.getBoundingBox();
            if (b.getXSize() < 3 || b.getZSize() < 3) {
                continue;
            }
            rigid++;
            int ground = b.minY + ((com.exiledradio.backportcore.structure.TerrainAdaptation.AdaptingPiece) c).groundLevelDelta();
            int surface = generator.surface((b.minX + b.maxX) / 2, (b.minZ + b.maxZ) / 2);
            spread[0] = Math.min(spread[0], ground - surface);
            spread[1] = Math.max(spread[1], ground - surface);
            int solid = 0;
            int[][] cs = {{b.minX, b.minZ}, {b.maxX, b.minZ}, {b.minX, b.maxZ}, {b.maxX, b.maxZ}};
            for (int[] q : cs) {
                net.minecraft.block.state.IBlockState under = world.getBlockState(new BlockPos(q[0], b.minY - 1, q[1]));
                solid += under.getMaterial().isSolid() ? 1 : 0;
            }
            corners += solid;
            standing += solid >= 3 ? 1 : 0;
        }
        check.eq(0, oldPieces, "the village has no 1.12 village pieces");
        ok(check, rigid >= 3 && standing * 10 >= rigid * 8, "its houses stand on solid ground: " + standing + " of " + rigid
                + " rigid pieces have 3+ solid corners under them (" + corners + " corners), ground "
                + spread[0] + " to " + spread[1] + " from the noise surface");
        Beardifier beard = generator.beards().forChunk(best[0], best[1]);
        StructureBoundingBox box = start.getBoundingBox();
        ok(check, beard != null && beard.touches(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ),
                "the village's chunk gets a beard");
        // Nothing saved under 1.12's village id.
        net.minecraft.world.gen.structure.MapGenStructureData data = (net.minecraft.world.gen.structure.MapGenStructureData)
                world.getPerWorldStorage().getOrLoadData(net.minecraft.world.gen.structure.MapGenStructureData.class, "Village");
        int saved = 0;
        int legacy = 0;
        if (data != null) {
            net.minecraft.nbt.NBTTagCompound features = data.getTagCompound();
            for (String key : features.getKeySet()) {
                saved++;
                legacy += "Village".equals(features.getCompoundTag(key).getString("id")) ? 1 : 0;
            }
        }
        check.eq(0, legacy, "no 1.12 village saved (" + saved + " village starts saved)");
        // /locate agrees.
        try {
            com.exiledradio.backportcore.locate.StructureLocate.Found found =
                    com.exiledradio.backportcore.locate.StructureLocate.nearest(world, spawn, "#minecraft:village");
            int index = found == null ? -1 : core.startAt(Structures.VILLAGES, found.pos.getX() >> 4, found.pos.getZ() >> 4);
            ok(check, index >= 0 && found.id.equals("minecraft:" + Structures.STRUCTURE_NAMES[Structures.VILLAGES][index])
                    && (found.pos.getX() >> 4) == best[0] && (found.pos.getZ() >> 4) == best[1],
                    "/locate structure #minecraft:village names the first 1.18 start: " + found);
            for (String name : Structures.STRUCTURE_NAMES[Structures.VILLAGES]) {
                found = com.exiledradio.backportcore.locate.StructureLocate.nearest(world, spawn, "minecraft:" + name);
                index = found == null ? -1 : core.startAt(Structures.VILLAGES, found.pos.getX() >> 4, found.pos.getZ() >> 4);
                ok(check, found == null || index >= 0 && name.equals(Structures.STRUCTURE_NAMES[Structures.VILLAGES][index]),
                        "/locate structure minecraft:" + name + " finds a 1.18 start of it: " + found);
            }
        } catch (com.exiledradio.backportcore.locate.StructureLocate.Unavailable e) {
            check.fail("/locate villages: " + e.getMessage());
        }
        BlockPos old = world.findNearestStructure("Village", spawn, false);
        ok(check, old != null && core.startAt(Structures.VILLAGES, old.getX() >> 4, old.getZ() >> 4) >= 0,
                "/locate Village names a 1.18 start: " + old);
    }

    /** A property's value in a state as its name in the block state string. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String valueName(net.minecraft.block.properties.IProperty prop, net.minecraft.block.state.IBlockState state) {
        return prop.getName(state.getValue(prop));
    }

    /** The stronghold nearest 0, 0: its pieces buried in solid ground by the beard. */
    private static void stronghold(Check check, ModernGenerator generator, OverworldNoise noise, Structures core) {
        int[] rings = core.ringPositions();
        int rx = rings[0];
        int rz = rings[1];
        for (int i = 2; i < rings.length; i += 2) {
            if ((long) rings[i] * rings[i] + (long) rings[i + 1] * rings[i + 1] < (long) rx * rx + (long) rz * rz) {
                rx = rings[i];
                rz = rings[i + 1];
            }
        }
        StructureStart start = null;
        for (StructureStart s : generator.adaptingStartsNear(rx, rz, true)) {
            if (s.getChunkPosX() == rx && s.getChunkPosZ() == rz) {
                start = s;
            }
        }
        if (!check.ok(start != null, "the stronghold at chunk " + rx + "," + rz + " is laid out for the terrain")) {
            return;
        }
        // Every piece in every chunk it touches: solid with the beard, against the bare terrain.
        ChunkFiller filler = new ChunkFiller(noise, new ColumnCache(noise));
        char[] with = new char[Pipeline.VOLUME];
        char[] without = new char[Pipeline.VOLUME];
        long[] totals = new long[3];
        java.util.Set<Long> chunks = new java.util.TreeSet<>();
        for (StructureComponent c : start.getComponents()) {
            StructureBoundingBox pb = c.getBoundingBox();
            for (int x = pb.minX >> 4; x <= pb.maxX >> 4; x++) {
                for (int z = pb.minZ >> 4; z <= pb.maxZ >> 4; z++) {
                    chunks.add(ChunkPos.asLong(x, z));
                }
            }
        }
        int beards = 0;
        int lifted = 0;
        int centers = 0;
        for (long k : chunks) {
            int cx = (int) k;
            int cz = (int) (k >> 32);
            Beardifier beard = generator.beards().forChunk(cx, cz);
            beards += beard != null ? 1 : 0;
            for (StructureComponent c : start.getComponents()) {
                StructureBoundingBox pb = c.getBoundingBox();
                int x = (pb.minX + pb.maxX) / 2;
                int z = (pb.minZ + pb.maxZ) / 2;
                if (beard != null && x >> 4 == cx && z >> 4 == cz) {
                    centers++;
                    // Bury: 1.0 at the floor's middle, more where pieces overlap.
                    lifted += beard.compute(x, pb.minY, z) >= 1.0D ? 1 : 0;
                }
            }
            filler.fill(cx, cz, with, beard);
            filler.fill(cx, cz, without);
            for (StructureComponent c : start.getComponents()) {
                int[] a = count(c.getBoundingBox(), cx, cz, with);
                int[] o = count(c.getBoundingBox(), cx, cz, without);
                totals[0] += a[0];
                totals[1] += o[0];
                totals[2] += a[1];
            }
        }
        check.eq(chunks.size(), beards, "every chunk the stronghold touches gets a beard");
        ok(check, centers > 0 && lifted == centers, "each stronghold piece is buried (the beard adds at least 1.0 at "
                + lifted + " of " + centers + " floor middles)");
        ok(check, totals[2] > 0 && totals[0] * 100 >= totals[2] * 99 && totals[0] >= totals[1], "the stronghold is buried: "
                + totals[0] + " of " + totals[2] + " blocks of its pieces solid with the beard (" + totals[1] + " without)");
    }

    /** Solid blocks (not air, not liquid) of a box within chunk cx, cz, and the box's blocks there. */
    private static int[] count(StructureBoundingBox b, int cx, int cz, char[] blocks) {
        int solid = 0;
        int total = 0;
        for (int x = Math.max(b.minX, cx << 4); x <= Math.min(b.maxX, (cx << 4) + 15); x++) {
            for (int z = Math.max(b.minZ, cz << 4); z <= Math.min(b.maxZ, (cz << 4) + 15); z++) {
                for (int y = Math.max(b.minY, Pipeline.MIN_Y); y <= Math.min(b.maxY, 319); y++) {
                    int at = ((x & 15) * 16 + (z & 15)) * Pipeline.HEIGHT + y - Pipeline.MIN_Y;
                    total++;
                    solid += blocks[at] != BlockCodes.AIR && !BlockCodes.liquid(blocks[at]) ? 1 : 0;
                }
            }
        }
        return new int[]{solid, total};
    }

    // ---------------------------------------------------------------------------------------------
    // Family structures

    private static final String[] KINDS = {com.exiledradio.backportcore.structure.Structures.SHIPWRECK,
        com.exiledradio.backportcore.structure.Structures.OCEAN_RUIN,
        com.exiledradio.backportcore.structure.Structures.BURIED_TREASURE,
        com.exiledradio.backportcore.structure.Structures.RUINED_PORTAL,
        com.exiledradio.backportcore.structure.Structures.PILLAGER_OUTPOST};
    private static final int[] SETS = {Structures.SHIPWRECKS, Structures.OCEAN_RUINS, Structures.BURIED_TREASURES,
        Structures.RUINED_PORTALS, Structures.PILLAGER_OUTPOSTS};

    private static void family(Check check, WorldServer world, Structures core) {
        for (int i = 0; i < KINDS.length; i++) {
            StartRule rule = StartRules.of(world, KINDS[i]);
            if (!check.ok(rule != null, "the generator decides where " + KINDS[i] + " starts")) {
                continue;
            }
            ScatteredPlacement grid = rule.grid();
            int set = SETS[i];
            check.ok(grid.spacing() == Structures.spacing(set) && grid.separation() == Structures.separation(set)
                    && grid.salt() == Structures.salt(set), KINDS[i] + " is on 1.18's grid (" + grid.spacing() + "/"
                    + grid.separation() + " salt " + grid.salt() + ")");
            int wrong = 0;
            int found = 0;
            int off = 0;
            int r = set == Structures.BURIED_TREASURES ? 48 : 160;
            for (int[] s : core.starts(set, -r, -r, r, r)) {
                found++;
                String id = rule.startAt(s[0], s[1]);
                wrong += !Structures.STRUCTURE_NAMES[set][s[2]].equals(id) ? 1 : 0;
                off += grid.isCandidate(world.getSeed(), s[0], s[1]) ? 0 : 1;
            }
            int extra = 0;
            for (int x = -24; x < 24; x++) {
                for (int z = -24; z < 24; z++) {
                    if (rule.startAt(x, z) != null && core.startAt(set, x, z) < 0) {
                        extra++;
                    }
                }
            }
            ok(check, found > 0 && wrong == 0 && extra == 0, KINDS[i] + ": the rule names 1.18's " + found
                    + " starts and their structures (" + wrong + " wrong, " + extra + " more)");
            check.eq(0, off, KINDS[i] + "'s starts are candidates of the rule's grid");
        }
        // A backport's ScatteredStructure follows the rule: a stand-in for Update Aquatic's shipwrecks.
        String kind = com.exiledradio.backportcore.structure.Structures.SHIPWRECK;
        // (Asking ScatteredStructure.of first would remember this world as one without shipwrecks.)
        if (!com.exiledradio.backportcore.structure.Structures.has(kind)) {
            ScatteredStructure.register(kind, w -> new StandIn());
        }
        ScatteredStructure structure = ScatteredStructure.of(world, kind);
        if (!check.ok(structure != null && structure.ruled(world), "a ScatteredStructure shipwreck in this world is ruled")) {
            return;
        }
        check.eq(Structures.spacing(Structures.SHIPWRECKS), structure.placement().spacing(),
                "its placement is the rule's grid");
        int wrong = 0;
        int starts = 0;
        for (int x = -64; x < 64; x++) {
            for (int z = -64; z < 64; z++) {
                boolean expected = core.startAt(Structures.SHIPWRECKS, x, z) >= 0;
                starts += expected ? 1 : 0;
                wrong += structure.startsAt(world, x, z) != expected ? 1 : 0;
                if (expected && !Structures.STRUCTURE_NAMES[Structures.SHIPWRECKS][core.startAt(Structures.SHIPWRECKS, x, z)]
                        .equals(structure.ruledStart(world, x, z))) {
                    wrong++;
                }
            }
        }
        ok(check, starts > 0 && wrong == 0, "it starts (and names the variant) exactly where 1.18 does (" + starts
                + " starts in 128x128 chunks, " + wrong + " wrong)");
        BlockPos near = structure.nearest(world, new BlockPos(0, 64, 0), 400);
        ok(check, near != null && core.startAt(Structures.SHIPWRECKS, near.getX() >> 4, near.getZ() >> 4) >= 0,
                "its nearest search finds a 1.18 start (" + near + ")");
    }

    /**
     * With the family's backports installed: their variants (warm or cold ruins, the ruined portal's type) are the
     * rule's, and outposts get the beard (Village &amp; Pillage registers them with TerrainAdaptation).
     */
    private static void installed(Check check, WorldServer world, ModernGenerator generator, Structures core) {
        String[] kinds = {com.exiledradio.backportcore.structure.Structures.OCEAN_RUIN,
            com.exiledradio.backportcore.structure.Structures.RUINED_PORTAL};
        int[] sets = {Structures.OCEAN_RUINS, Structures.RUINED_PORTALS};
        String[] prefixes = {"ocean_ruin_", "ruined_portal_"};
        for (int i = 0; i < kinds.length; i++) {
            ScatteredStructure structure = ScatteredStructure.of(world, kinds[i]);
            if (structure == null) {
                continue;
            }
            int n = 0;
            int wrong = 0;
            for (int[] s : core.starts(sets[i], -160, -160, 160, 160)) {
                String id = Structures.STRUCTURE_NAMES[sets[i]][s[2]];
                String expected = id.startsWith(prefixes[i]) ? id.substring(prefixes[i].length()) : "standard";
                n++;
                wrong += !expected.equals(structure.variantAt(world, s[0], s[1])) || !structure.startsAt(world, s[0], s[1]) ? 1 : 0;
            }
            ok(check, n > 0 && wrong == 0, kinds[i] + " (installed): " + n + " starts with 1.18's variant, " + wrong + " wrong");
        }
        // /locate (Backport Core's) agrees: the start it names is 1.18's, of the structure asked for.
        String[] ids = {"minecraft:shipwreck", "minecraft:ocean_ruin_warm", "minecraft:ocean_ruin_cold",
            "minecraft:buried_treasure", "minecraft:ruined_portal", "minecraft:ruined_portal_desert",
            "minecraft:pillager_outpost"};
        int[] idSets = {Structures.SHIPWRECKS, Structures.OCEAN_RUINS, Structures.OCEAN_RUINS, Structures.BURIED_TREASURES,
            Structures.RUINED_PORTALS, Structures.RUINED_PORTALS, Structures.PILLAGER_OUTPOSTS};
        for (int i = 0; i < ids.length; i++) {
            if (!com.exiledradio.backportcore.locate.StructureLocate.known(ids[i])) {
                continue;
            }
            com.exiledradio.backportcore.locate.StructureLocate.Found found;
            try {
                found = com.exiledradio.backportcore.locate.StructureLocate.nearest(world, BlockPos.ORIGIN, ids[i]);
            } catch (com.exiledradio.backportcore.locate.StructureLocate.Unavailable e) {
                check.fail("/locate " + ids[i] + ": " + e.getMessage());
                continue;
            }
            int index = found == null ? -1 : core.startAt(idSets[i], found.pos.getX() >> 4, found.pos.getZ() >> 4);
            ok(check, index >= 0 && ids[i].equals("minecraft:" + Structures.STRUCTURE_NAMES[idSets[i]][index]),
                    "/locate " + ids[i] + " (installed) finds a 1.18 start of it: " + found);
        }
        // Village & Pillage's plants: the 1.18 features place its blocks (not the fallbacks), and its own
        // generators for them step aside.
        if (net.minecraftforge.fml.common.Loader.isModLoaded("villagerbackport")) {
            ok(check, com.exiledradio.backportcore.worldgen.FeatureSource.places(world, "1.14"),
                    "the generator places 1.14's features itself (Village & Pillage's generators step aside)");
            com.exiledradio.cavesandcliffsbackport.terrain.StateMap map = new com.exiledradio.cavesandcliffsbackport.terrain.StateMap();
            int seen = 0;
            StringBuilder wrong = new StringBuilder();
            for (int c = 0; c < BlockCodes.count(); c++) {
                String full = BlockCodes.state(c);
                String name = full.indexOf('[') < 0 ? full : full.substring(0, full.indexOf('['));
                if (!name.equals("bamboo") && !name.equals("sweet_berry_bush") && !name.equals("cornflower")
                        && !name.equals("lily_of_the_valley")) {
                    continue;
                }
                seen++;
                net.minecraft.block.state.IBlockState state = map.state(c);
                String id = String.valueOf(state.getBlock().getRegistryName());
                boolean good = id.equals("villagerbackport:" + name);
                if (good && name.equals("bamboo")) {
                    // 1.18's leaves and age carried over by name.
                    for (String kv : full.substring(full.indexOf('[') + 1, full.length() - 1).split(",")) {
                        String[] p = kv.split("=");
                        for (net.minecraft.block.properties.IProperty<?> prop : state.getPropertyKeys()) {
                            if (prop.getName().equals(p[0]) && !valueName(prop, state).equals(p[1])) {
                                good = false;
                            }
                        }
                    }
                }
                if (!good) {
                    wrong.append(full).append(" -> ").append(state).append("; ");
                }
            }
            ok(check, seen >= 4 && wrong.length() == 0, "1.18's bamboo, sweet berry bushes, cornflowers and lilies of the "
                    + "valley are Village & Pillage's blocks (" + seen + " states) " + wrong);
        }
        boolean outposts = false;
        for (com.exiledradio.backportcore.structure.TerrainAdaptation.Entry e
                : com.exiledradio.backportcore.structure.TerrainAdaptation.registered()) {
            outposts |= e.kind.equals(com.exiledradio.backportcore.structure.Structures.PILLAGER_OUTPOST);
        }
        if (outposts) {
            List<int[]> starts = core.starts(Structures.PILLAGER_OUTPOSTS, -256, -256, 256, 256);
            if (ok(check, !starts.isEmpty(), "an outpost within 256 chunks (" + starts.size() + ")")) {
                int[] s = starts.get(0);
                Beardifier beard = generator.beards().forChunk(s[0], s[1]);
                ok(check, beard != null && beard.touches((s[0] << 4) - 16, -64, (s[1] << 4) - 16, (s[0] << 4) + 31, 319,
                        (s[1] << 4) + 31), "the outpost at chunk " + s[0] + "," + s[1] + " (installed) gets a beard");
            }
        }
    }

    /** A shipwreck kind that lays out nothing, for the rule test when Update Aquatic is not installed. */
    private static final class StandIn extends ScatteredStructure {
        StandIn() {
            super("CCB_SelfTestShipwreck", new ScatteredPlacement(16, 8, 165745295));
        }

        @Override
        protected boolean canStartAt(World world, int chunkX, int chunkZ) {
            return false;
        }

        @Override
        protected StructureStart getStructureStart(int chunkX, int chunkZ) {
            return new StructureStart(chunkX, chunkZ) {
            };
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Spawning

    private static void spawning(Check check, WorldServer world, ModernGenerator generator, OverworldNoise noise, Climate climate) {
        BiomeZoom zoom = new BiomeZoom(noise, new BiomeSource(noise, new ColumnCache(noise), climate));
        // Look for lush caves under other biomes, around 0, 0, without generating anything.
        BlockPos lush = null;
        BlockPos drip = null;
        int wrongZoom = 0;
        for (int i = 0; i < 4000 && (lush == null || drip == null); i++) {
            int x = (i % 64) * 37 - 1200;
            int z = (i / 64) * 41 - 1300;
            int top = generator.surface(x, z);
            for (int y = -40; y < top - 12; y += 6) {
                int b = generator.biome3d(x, y, z);
                wrongZoom += b != zoom.biome(x, y, z) ? 1 : 0;
                if (b == Biomes.LUSH_CAVES && lush == null && zoom.biome(x, top - 1, z) != Biomes.LUSH_CAVES) {
                    lush = new BlockPos(x, y, z);
                }
                if (b == Biomes.DRIPSTONE_CAVES && drip == null && zoom.biome(x, top - 1, z) != Biomes.DRIPSTONE_CAVES) {
                    drip = new BlockPos(x, y, z);
                }
            }
        }
        check.eq(0, wrongZoom, "the generator's 3D biome is 1.18's zoomed biome");
        if (!ok(check, lush != null && drip != null, "lush caves (" + lush + ") and dripstone caves (" + drip
                + ") under other biomes")) {
            return;
        }
        for (BlockPos p : new BlockPos[]{lush, drip}) {
            world.getChunkProvider().provideChunk(p.getX() >> 4, p.getZ() >> 4);
            String where = (p == lush ? "lush" : "dripstone") + " caves at " + p;
            check.ok(generator.getPossibleCreatures(EnumCreatureType.CREATURE, p).isEmpty(), where + " spawn no animals");
            check.ok(has(generator.getPossibleCreatures(EnumCreatureType.AMBIENT, p), EntityBat.class), where + " spawn bats");
            check.ok(has(generator.getPossibleCreatures(EnumCreatureType.MONSTER, p), EntityZombie.class), where + " spawn zombies");
            if (GlowSquidContent.undergroundWater != null) {
                check.ok(!generator.getPossibleCreatures(GlowSquidContent.undergroundWater, p).isEmpty(),
                        where + " spawn glow squid");
            }
            // The column's 1.12 biome is 1.18's at its top block: there the 3D biome gives the same list.
            BlockPos up = new BlockPos(p.getX(), generator.surface(p.getX(), p.getZ()) - 1, p.getZ());
            int surfaceBiome = generator.biome3d(up.getX(), up.getY(), up.getZ());
            check.ok(surfaceBiome == Biomes.LUSH_CAVES || surfaceBiome == Biomes.DRIPSTONE_CAVES
                            || generator.getPossibleCreatures(EnumCreatureType.CREATURE, up)
                            == world.getBiome(up).getSpawnableList(EnumCreatureType.CREATURE),
                    where + ": the surface above spawns its column biome's animals");
        }
        check.ok(CaveRegions.lush(world, lush.getX(), lush.getY(), lush.getZ()), "the axolotls' lush caves are 1.18's lush caves");
        check.ok(!CaveRegions.lush(world, drip.getX(), drip.getY(), drip.getZ()), "dripstone caves are not lush caves");
        check.ok(CaveRegions.dripstone(world, drip.getX(), drip.getY(), drip.getZ()), "dripstone regions are 1.18's dripstone caves");
        check.ok(generator.biomeChunks() > 0, "3D biomes are kept for loaded chunks (" + generator.biomeChunks() + ")");
    }

    private static boolean has(List<Biome.SpawnListEntry> list, Class<?> mob) {
        for (Biome.SpawnListEntry e : list) {
            if (e.entityClass == mob) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Held chunks

    private static void held(Check check, WorldServer world, ModernGenerator generator) {
        Pipeline own = new Pipeline(new OverworldNoise(world.getSeed(), generator.editions()),
                new Climate(generator.editions()), 0, 1024);
        Pipeline.Proto p = own.finish(-4000, 4000);
        byte[] a = Pipeline.encode(p);
        Pipeline.Proto q = own.decode(a);
        check.ok(q != null && java.util.Arrays.equals(a, Pipeline.encode(q)) && java.util.Arrays.equals(p.blocks, q.blocks),
                "a held chunk comes back from its bytes exactly");
        own.shutdown();
        world.getChunkProvider().provideChunk(-4100, 4100);
        int n = generator.held();
        generator.saveHeld();
        generator.flushHeld();
        java.io.File dir = new java.io.File(world.getSaveHandler().getWorldDirectory(), "cavesandcliffsbackport_terrain");
        String[] files = dir.list();
        ok(check, files != null && files.length >= 2, "held chunks (" + n + ") are saved with the world ("
                + (files == null ? 0 : files.length) + " files in " + dir.getName() + ")");
    }
}
