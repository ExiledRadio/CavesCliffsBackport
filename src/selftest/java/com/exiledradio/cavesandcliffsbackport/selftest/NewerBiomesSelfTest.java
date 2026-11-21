package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.backportcore.terrain.DecorationSource;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernGenerator;
import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Biomes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Editions;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Rules;
import com.exiledradio.backportcore.waterlog.Waterlogging;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import java.util.HashMap;
import java.util.Map;

/**
 * The newer biomes in a 1.18 terrain world (part of {@code terrain}): which ones the world got (Wild Update's blocks
 * give 1.19's mangrove swamp and deep dark, Trails &amp; Tales' cherry wood 1.20's cherry grove), that their features
 * generate with the backports' blocks, that the deep dark spawns nothing, that 1.18's bee nests (Buzzy Bees') have
 * their bees, and what the generator tells the backports' 1.12 hooks through Backport Core's DecorationSource.
 */
final class NewerBiomesSelfTest {

    private NewerBiomesSelfTest() {
    }

    static void run(Check check, WorldServer world, ModernGenerator generator) {
        int e = generator.editions();
        boolean wild = registered("mud") && registered("mangrove_log") && registered("mangrove_propagule");
        boolean cherry = registered("cherry_log") && registered("pink_petals");
        boolean pale = net.minecraftforge.fml.common.Loader.isModLoaded("trickytrialsbackport")
                && net.minecraftforge.fml.common.registry.ForgeRegistries.BIOMES.containsKey(PALE_GARDEN);
        boolean gameDrops = net.minecraftforge.fml.common.Loader.isModLoaded("gamedropsbackport")
                && net.minecraftforge.fml.common.registry.ForgeRegistries.BIOMES.containsKey(new ResourceLocation("gamedropsbackport", "sulfur_caves"))
                && net.minecraftforge.fml.common.registry.ForgeRegistries.BIOMES.containsKey(new ResourceLocation("gamedropsbackport", "dappled_forest"));
        check.eq((wild ? Editions.WILD : 0) | (cherry ? Editions.CHERRY : 0) | (pale ? (com.exiledradio.cavesandcliffsbackport.ModConfig.height.paleGarden1214 ? Editions.PALE
                : Editions.PALE | Editions.SPRING) : 0) | (gameDrops ? Editions.GAME_DROPS : 0), e,
                "the world's newer biomes are the ones the installed backports have blocks for (" + Editions.name(e) + ")");
        int rules = net.minecraftforge.fml.common.Loader.isModLoaded("trickytrialsbackport") ? Rules.V121
                : net.minecraftforge.fml.common.Loader.isModLoaded("trailsandtalesbackport") ? Rules.V120
                : net.minecraftforge.fml.common.Loader.isModLoaded("wildupdatebackport") ? Rules.V119 : Rules.V118;
        check.eq(rules, generator.rules(), "1.18.2's features run the newest installed backport's code (" + Rules.name(rules) + ")");
        check.ok(DecorationSource.places(world, "bee_nest"), "the generator says it places bee nests (1.12 nest hooks step aside)");
        check.ok(DecorationSource.places(world, "trees_plains"), "the generator says it places trees_plains");
        check.eq((e & Editions.WILD) != 0, DecorationSource.places(world, "trees_mangrove"),
                "the generator places trees_mangrove only with the 1.19 biomes");
        check.eq((e & Editions.CHERRY) != 0, DecorationSource.places(world, "trees_cherry"),
                "the generator places trees_cherry only with the 1.20 biomes");
        check.eq((e & Editions.PALE) != 0, DecorationSource.places(world, "pale_garden_vegetation"),
                "the generator places pale_garden_vegetation only with the 1.21.4 biome");
        OverworldNoise noise = new OverworldNoise(world.getSeed(), e);
        BiomeSource source = new BiomeSource(noise, new ColumnCache(noise), new Climate(e));
        if ((e & Editions.PALE) != 0) {
            paleGarden(check, world, generator, noise, source, e);
        }
        if ((e & Editions.WILD) != 0) {
            int[] c = find(source, Biomes.MANGROVE_SWAMP, 16);
            check.ok(c != null, "a mangrove swamp within 300 chunks");
            if (c != null) {
                Map<String, Integer> n = census(world, c[0], c[1]);
                ok(check, n.getOrDefault("mud", 0) > 100, "mangrove swamp: mud (" + n.get("mud") + ")");
                ok(check, n.getOrDefault("mangrove_log", 0) > 20, "mangrove swamp: mangrove logs (" + n.get("mangrove_log") + ")");
                ok(check, n.getOrDefault("mangrove_leaves", 0) > 100, "mangrove swamp: mangrove leaves (" + n.get("mangrove_leaves") + ")");
                ok(check, n.getOrDefault("mangrove_roots", 0) + n.getOrDefault("muddy_mangrove_roots", 0) > 10,
                        "mangrove swamp: roots (" + n.get("mangrove_roots") + " and " + n.get("muddy_mangrove_roots") + " muddy)");
                ok(check, n.getOrDefault("mangrove_propagule", 0) > 0, "mangrove swamp: hanging propagules (" + n.get("mangrove_propagule") + ")");
                wetLeaves(check, world, generator, noise, e, c[0], c[1]);
            }
            int[] d = find(source, Biomes.DEEP_DARK, -8);
            check.ok(d != null, "a deep dark within 300 chunks");
            if (d != null) {
                BlockPos p = new BlockPos(d[0] * 16 + 8, -32, d[1] * 16 + 8);
                world.getChunkProvider().provideChunk(d[0], d[1]);
                check.ok(generator.biome3d(p.getX(), p.getY(), p.getZ()) == Biomes.DEEP_DARK, "the deep dark is where the core says");
                check.ok(generator.getPossibleCreatures(EnumCreatureType.MONSTER, p).isEmpty()
                        && generator.getPossibleCreatures(EnumCreatureType.CREATURE, p).isEmpty()
                        && generator.getPossibleCreatures(EnumCreatureType.AMBIENT, p).isEmpty(), "the deep dark spawns nothing");
            }
        }
        if ((e & Editions.CHERRY) != 0) {
            int[] c = find(source, Biomes.CHERRY_GROVE, 16);
            check.ok(c != null, "a cherry grove within 300 chunks");
            if (c != null) {
                Map<String, Integer> n = census(world, c[0], c[1]);
                ok(check, n.getOrDefault("cherry_log", 0) > 5, "cherry grove: cherry logs (" + n.get("cherry_log") + ")");
                ok(check, n.getOrDefault("cherry_leaves", 0) > 50, "cherry grove: cherry leaves (" + n.get("cherry_leaves") + ")");
                ok(check, n.getOrDefault("pink_petals", 0) > 20, "cherry grove: pink petals (" + n.get("pink_petals") + ")");
            }
        }
        if (registered("bee_nest")) {
            // Meadows' trees are the super birch with a beehive every time (1.0) or fancy oaks with one.
            int[] c = find(source, Biomes.MEADOW, 16);
            check.ok(c != null, "a meadow within 300 chunks");
            if (c != null) {
                int nests = 0;
                int withBees = 0;
                int wrongBees = 0;
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        world.getChunkProvider().provideChunk(c[0] + dx, c[1] + dz);
                    }
                }
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        for (TileEntity te : world.getChunk(c[0] + dx, c[1] + dz).getTileEntityMap().values()) {
                            ResourceLocation id = world.getBlockState(te.getPos()).getBlock().getRegistryName();
                            if (id == null || !id.getPath().equals("bee_nest")) {
                                continue;
                            }
                            nests++;
                            int bees = te.writeToNBT(new NBTTagCompound()).getTagList("Bees", 10).tagCount();
                            if (bees > 0) {
                                withBees++;
                            }
                            if (bees < 2 || bees > 3) {
                                wrongBees++;
                            }
                        }
                    }
                }
                // The core's own nests in the same chunks: more in the world would be a 1.12 hook's second nest.
                com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline own =
                        new com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline(noise, new Climate(e), 0, 1024);
                own.setBeards(generator.beards());
                generator.sameDecoration(own);
                int coreNests = 0;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        for (char code : own.finish(c[0] + dx, c[1] + dz).blocks) {
                            if (com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes.name(code).equals("bee_nest")) {
                                coreNests++;
                            }
                        }
                    }
                }
                own.shutdown();
                ok(check, nests == coreNests, "the world has exactly the core's bee nests, no 1.12 hook added more (world "
                        + nests + ", core " + coreNests + ")");
                // Tile entities a chunk makes lazily do not count: the generator makes the nests' own.
                ok(check, nests > 0, "meadow trees have bee nests (" + nests + " in 25 chunks)");
                check.eq(nests, withBees, "every generated bee nest has bees");
                check.eq(0, wrongBees, "every generated bee nest has 2 or 3 bees");
            }
        }
    }

    private static final ResourceLocation PALE_GARDEN = new ResourceLocation("trickytrialsbackport", "pale_garden");

    /**
     * 1.21.4's pale garden (Tricky Trials Backport): a dense one found on a coarse search, its biome in the world,
     * its blocks, and a creaking heart the core grows (found by decorating the core's own chunks) in the world, its
     * tile natural.
     */
    private static void paleGarden(Check check, WorldServer world, ModernGenerator generator, OverworldNoise noise,
                                   BiomeSource source, int e) {
        int[] c = null;
        for (int r = 0; r < 300 && c == null; r++) {
            for (int dx = -r; dx <= r && c == null; dx++) {
                for (int dz = -r; dz <= r && c == null; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int cx = dx * 8;
                    int cz = dz * 8;
                    int n = 0;
                    for (int i = -1; i <= 1; i++) {
                        for (int j = -1; j <= 1; j++) {
                            n += source.biome((cx + i) * 4 + 2, 18, (cz + j) * 4 + 2) == Biomes.PALE_GARDEN ? 1 : 0;
                        }
                    }
                    if (n == 9) {
                        c = new int[]{cx, cz};
                    }
                }
            }
        }
        check.ok(c != null, "a pale garden within 2400 chunks");
        if (c == null) {
            return;
        }
        Map<String, Integer> n = census(world, c[0], c[1]);
        BlockPos at = world.getHeight(new BlockPos(c[0] * 16 + 8, 0, c[1] * 16 + 8));
        ok(check, PALE_GARDEN.equals(world.getBiome(at).getRegistryName()), "the pale garden at " + at
                + " is Tricky Trials Backport's biome (" + world.getBiome(at).getRegistryName() + ")");
        ok(check, n.getOrDefault("pale_oak_log", 0) > 20, "pale garden: pale oak logs (" + n.get("pale_oak_log") + ")");
        ok(check, n.getOrDefault("pale_oak_leaves", 0) > 100, "pale garden: pale oak leaves (" + n.get("pale_oak_leaves") + ")");
        ok(check, n.getOrDefault("pale_moss_block", 0) > 20, "pale garden: pale moss (" + n.get("pale_moss_block") + ")");
        ok(check, n.getOrDefault("pale_moss_carpet", 0) > 5, "pale garden: pale moss carpets (" + n.get("pale_moss_carpet") + ")");
        ok(check, n.getOrDefault("pale_hanging_moss", 0) > 10, "pale garden: hanging moss (" + n.get("pale_hanging_moss") + ")");
        // A creaking heart: the core's own decoration of the chunks around, the first holding one.
        com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline own =
                new com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline(noise, new Climate(e), 0, 1024);
        own.setBeards(generator.beards());
        generator.sameDecoration(own);
        int heart = com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes.byState((e & Editions.SPRING) != 0
                ? "creaking_heart[axis=y,creaking_heart_state=dormant,natural=true]" : "creaking_heart[active=true,axis=y,natural=true]");
        BlockPos found = null;
        for (int r = 0; r < 12 && found == null; r++) {
            for (int dx = -r; dx <= r && found == null; dx++) {
                for (int dz = -r; dz <= r && found == null; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    char[] blocks = own.finish(c[0] + dx, c[1] + dz).blocks;
                    for (int i = 0; i < blocks.length && found == null; i++) {
                        if (blocks[i] == heart) {
                            int col = i / com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline.HEIGHT;
                            found = new BlockPos(((c[0] + dx) << 4) + (col >> 4),
                                    com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline.MIN_Y
                                            + i % com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline.HEIGHT,
                                    ((c[1] + dz) << 4) + (col & 15));
                        }
                    }
                }
            }
        }
        own.shutdown();
        check.ok(found != null, "the core grows a creaking heart within 12 chunks of the pale garden");
        if (found == null) {
            return;
        }
        // The world's own hearts near it (decoration crosses chunk borders, so the world, which decorated in another
        // order, need not have that very tree): each a Tricky Trials heart, dormant, its tile natural.
        int fx = found.getX() >> 4;
        int fz = found.getZ() >> 4;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                world.getChunkProvider().provideChunk(fx + dx, fz + dz);
            }
        }
        int hearts = 0;
        int natural = 0;
        String first = null;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (TileEntity te : world.getChunk(fx + dx, fz + dz).getTileEntityMap().values()) {
                    IBlockState s = world.getBlockState(te.getPos());
                    if (!"creaking_heart".equals(s.getBlock().getRegistryName().getPath())) {
                        continue;
                    }
                    hearts++;
                    natural += te.writeToNBT(new NBTTagCompound()).getBoolean("Natural") ? 1 : 0;
                    if (first == null) {
                        first = te.getPos() + " " + s;
                    }
                }
            }
        }
        ok(check, hearts > 0, "the world has creaking hearts near the core's (" + hearts + " in 7 x 7 chunks; " + first + ")");
        check.eq(hearts, natural, "every generated creaking heart's tile is natural");
    }

    private static boolean ok(Check check, boolean condition, String what) {
        com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport.LOGGER.info("SELFTEST INFO {}", what);
        return check.ok(condition, what);
    }

    /**
     * 1.19 waterlogs leaves that grow into water: the core's waterlogged mangrove leaves in the 3 x 3 chunks are the
     * world's mangrove leaves holding water (Backport Core's waterlogging, with Fluidlogged API), and without
     * Fluidlogged API none hold any.
     */
    private static void wetLeaves(Check check, WorldServer world, ModernGenerator generator, OverworldNoise noise, int e,
                                  int cx, int cz) {
        com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline own =
                new com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline(noise, new Climate(e), 0, 1024);
        own.setBeards(generator.beards());
        generator.sameDecoration(own);
        int core = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (char code : own.finish(cx + dx, cz + dz).blocks) {
                    String state = com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes.state(code);
                    if (state.startsWith("mangrove_leaves[") && state.contains("waterlogged=true")) {
                        core++;
                    }
                }
            }
        }
        own.shutdown();
        int wet = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = (cx - 1) * 16; x < (cx + 2) * 16; x++) {
            for (int z = (cz - 1) * 16; z < (cz + 2) * 16; z++) {
                for (int y = -64; y < 320; y++) {
                    p.setPos(x, y, z);
                    ResourceLocation id = world.getBlockState(p).getBlock().getRegistryName();
                    if (id != null && id.getPath().equals("mangrove_leaves") && Waterlogging.hasWater(world, p)) {
                        wet++;
                    }
                }
            }
        }
        if (Waterlogging.isAvailable()) {
            ok(check, wet == core, "mangrove leaves grown into water hold it (world " + wet + ", core " + core + ")");
        } else {
            ok(check, wet == 0, "without Fluidlogged API no mangrove leaves hold water (core: " + core + " in water)");
        }
    }

    private static boolean registered(String path) {
        for (ResourceLocation id : net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.getKeys()) {
            if (id.getPath().equals(path)) {
                return true;
            }
        }
        return false;
    }

    /** The nearest chunk to 0, 0 (rings, up to 300 out) whose centre quart at quart y qy is the biome. */
    private static int[] find(BiomeSource source, int biome, int qy) {
        for (int r = 0; r < 300; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r && source.biome(dx * 4 + 2, qy, dz * 4 + 2) == biome) {
                        return new int[]{dx, dz};
                    }
                }
            }
        }
        return null;
    }

    /** Block counts by registry path over the 3 x 3 chunks around one, generated with their neighbors. */
    private static Map<String, Integer> census(WorldServer world, int cx, int cz) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getChunkProvider().provideChunk(cx + dx, cz + dz);
            }
        }
        Map<String, Integer> n = new HashMap<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = (cx - 1) * 16; x < (cx + 2) * 16; x++) {
            for (int z = (cz - 1) * 16; z < (cz + 2) * 16; z++) {
                for (int y = 40; y < 140; y++) {
                    IBlockState s = world.getBlockState(p.setPos(x, y, z));
                    ResourceLocation id = s.getBlock().getRegistryName();
                    if (id != null && !id.getNamespace().equals("minecraft")) {
                        n.merge(id.getPath(), 1, Integer::sum);
                    }
                }
            }
        }
        return n;
    }
}
