package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.terrain.ModernGenerator;
import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Biomes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Editions;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.List;

/**
 * The Game Drops edition ({@link Editions#GAME_DROPS}, part of {@code terrain}): 26.3's dappled forest and 26.2's
 * sulfur caves in the biome builder, their surface rules, and the edition's saved names. The core's part runs in any
 * world (a pipeline of its own with the edition); with Game Drops Backport installed the world itself is checked too.
 */
final class GameDropsEditionSelfTest {

    private static final int[][] DIRS = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private GameDropsEditionSelfTest() {
    }

    static void run(Check check, WorldServer world, ModernGenerator generator) {
        names(check);
        leaves(check);
        int e = (generator.editions() & ~Editions.GAME_DROPS) | Editions.GAME_DROPS;
        OverworldNoise noise = new OverworldNoise(world.getSeed(), e);
        BiomeSource source = new BiomeSource(noise, new ColumnCache(noise), new Climate(e));
        Pipeline own = new Pipeline(noise, new Climate(e), 0, 1024);
        own.setBeards(generator.beards());
        OverworldNoise plainNoise = new OverworldNoise(world.getSeed(), e & ~Editions.GAME_DROPS);
        Pipeline plain = new Pipeline(plainNoise, new Climate(e & ~Editions.GAME_DROPS), 0, 1024);
        plain.setBeards(generator.beards());
        try {
            dappled(check, source, own);
            sulfur(check, source, own, plain);
        } finally {
            own.shutdown();
            plain.shutdown();
        }
        if ((generator.editions() & Editions.GAME_DROPS) != 0) {
            inWorld(check, world, generator, source);
        }
    }

    /** Every edition set's saved name reads back as itself. */
    private static void names(Check check) {
        int bad = 0;
        for (int e = 0; e <= Editions.ALL; e++) {
            if ((e & Editions.SPRING) != 0 && (e & Editions.PALE) == 0) {
                continue;
            }
            if (Editions.byName(Editions.name(e)) != e) {
                bad++;
            }
        }
        check.eq(0, bad, "every edition set's saved name reads back as itself");
        check.eq("26.3", Editions.name(Editions.ALL), "every edition is 26.3");
        check.eq("1.18-26.3", Editions.name(Editions.GAME_DROPS), "Game Drops alone is 1.18-26.3");
    }

    /**
     * The builder's leaves with Game Drops: the dappled forest where plains were in the cool, driest variant slot
     * (weirdness above 0), the sulfur caves after the lush caves at 26.2's box; nothing else moved.
     */
    private static void leaves(Check check) {
        Climate with = new Climate(Editions.ALL);
        Climate without = new Climate(Editions.ALL & ~Editions.GAME_DROPS);
        List<String> rest = new ArrayList<>();
        int dappled = 0;
        int dappledWrong = 0;
        int sulfur = 0;
        int sulfurWrong = 0;
        for (int i = 0; i < with.leafCount(); i++) {
            String[] f = with.describeLeaf(i).split(" ");
            if (f[0].equals("dappled_forest")) {
                dappled++;
                // temperature -0.45..-0.15, humidity -1..-0.35, weirdness reaching 0 (pickMiddleBiome's max >= 0).
                if (!f[1].equals("-4500") || !f[2].equals("-1500") || !f[3].equals("-10000") || !f[4].equals("-3500")
                        || Long.parseLong(f[12]) < 0L) {
                    dappledWrong++;
                }
                f[0] = "plains";
            } else if (f[0].equals("sulfur_caves")) {
                sulfur++;
                String box = String.join(" ", f[5], f[6], f[7], f[8], f[9], f[10], f[11], f[12]);
                if (!box.equals("-1900 5500 4500 10000 2000 9000 -11000 -8500")) {
                    sulfurWrong++;
                }
                continue;
            }
            rest.add(String.join(" ", f));
        }
        List<String> old = new ArrayList<>();
        for (int i = 0; i < without.leafCount(); i++) {
            old.add(without.describeLeaf(i));
        }
        check.ok(dappled > 0 && dappledWrong == 0, "the dappled forest's leaves are 26.3's cool, driest variant (" + dappled
            + " leaves, " + dappledWrong + " wrong)");
        check.eq(1, sulfur, "one sulfur caves leaf");
        check.eq(0, sulfurWrong, "the sulfur caves' leaf is 26.2's box (continentalness -0.19..0.55, erosion 0.45..1, "
            + "depth 0.2..0.9, weirdness -1.1..-0.85)");
        check.ok(old.equals(rest), "with the dappled forest as plains and no sulfur caves, the leaves are the edition's without Game Drops");
    }

    private static void dappled(Check check, BiomeSource source, Pipeline own) {
        int[] c = interior(source, Biomes.DAPPLED_FOREST, 18);
        check.ok(c != null, "a dappled forest (5 chunks across) within 300 chunks");
        if (c == null) {
            return;
        }
        int grass = BlockCodes.GRASS_BLOCK;
        int coarse = BlockCodes.COARSE_DIRT;
        int grassTops = 0;
        int coarseTops = 0;
        int other = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                char[] b = own.finish(c[0] + dx, c[1] + dz).blocks;
                for (int col = 0; col < 256; col++) {
                    int x = ((c[0] + dx) << 4) + (col >> 4);
                    int z = ((c[1] + dz) << 4) + (col & 15);
                    int top = top(b, col);
                    if (top < 0) {
                        continue;
                    }
                    int y = Pipeline.MIN_Y + top;
                    if (source.biome(x >> 2, y >> 2, z >> 2) != Biomes.DAPPLED_FOREST || y < 63) {
                        continue;
                    }
                    int code = b[col * Pipeline.HEIGHT + top];
                    if (code == grass) {
                        grassTops++;
                    } else if (code == coarse) {
                        coarseTops++;
                    } else {
                        other++;
                    }
                }
            }
        }
        check.ok(grassTops > 300, "dappled forest: grass on top (" + grassTops + ")");
        check.ok(coarseTops > 0 && coarseTops < grassTops, "dappled forest: coarse dirt in small patches (" + coarseTops + ", "
            + other + " other)");
    }

    private static void sulfur(Check check, BiomeSource source, Pipeline own, Pipeline plain) {
        int[] c = find(source, Biomes.SULFUR_CAVES, 2);
        check.ok(c != null, "sulfur caves within 300 chunks");
        if (c == null) {
            return;
        }
        int sulfur = BlockCodes.byState("sulfur");
        int cinnabar = BlockCodes.byState("cinnabar");
        int bands = 0;
        int outside = 0;
        int plainBands = 0;
        int deepBands = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = c[0] + dx;
                int cz = c[1] + dz;
                char[] b = own.finish(cx, cz).blocks;
                char[] p = plain.finish(cx, cz).blocks;
                for (int i = 0; i < b.length; i++) {
                    if (p[i] == sulfur || p[i] == cinnabar) {
                        plainBands++;
                    }
                    if (b[i] != sulfur && b[i] != cinnabar) {
                        continue;
                    }
                    bands++;
                    int col = i / Pipeline.HEIGHT;
                    int y = Pipeline.MIN_Y + i % Pipeline.HEIGHT;
                    int x = (cx << 4) + (col >> 4);
                    int z = (cz << 4) + (col & 15);
                    if (y <= 0) {
                        deepBands++;
                    }
                    // The zoom picks one of the eight quarts around (x - 2, y - 2, z - 2).
                    boolean near = false;
                    for (int qx = (x - 2) >> 2; qx <= ((x - 2) >> 2) + 1 && !near; qx++) {
                        for (int qy = (y - 2) >> 2; qy <= ((y - 2) >> 2) + 1 && !near; qy++) {
                            for (int qz = (z - 2) >> 2; qz <= ((z - 2) >> 2) + 1 && !near; qz++) {
                                near = source.biome(qx, qy, qz) == Biomes.SULFUR_CAVES;
                            }
                        }
                    }
                    if (!near) {
                        outside++;
                    }
                }
            }
        }
        check.ok(bands > 500, "sulfur caves: sulfur and cinnabar bands (" + bands + ", " + deepBands + " at or below y 0)");
        check.eq(0, outside, "the bands are only where the sulfur caves are");
        check.eq(0, plainBands, "no bands without the Game Drops edition");
    }

    /** With Game Drops installed: the world's own biome at the sulfur caves and the dappled forest. */
    private static void inWorld(Check check, WorldServer world, ModernGenerator generator, BiomeSource source) {
        int[] s = find(source, Biomes.SULFUR_CAVES, 2);
        if (s != null) {
            // A quart's corner + 2: the zoom keeps the quart itself.
            int x = s[0] * 16 + 10;
            int z = s[1] * 16 + 10;
            world.getChunkProvider().provideChunk(s[0], s[1]);
            check.eq("minecraft:sulfur_caves", generator.biomeId(x, 10, z), "the 3D biome id of the sulfur caves");
        }
        int[] d = interior(source, Biomes.DAPPLED_FOREST, 18);
        if (d != null) {
            BlockPos at = new BlockPos(d[0] * 16 + 10, 72, d[1] * 16 + 10);
            for (int[] o : DIRS) {
                world.getChunkProvider().provideChunk(d[0] + o[0], d[1] + o[1]);
            }
            check.eq(new ResourceLocation("gamedropsbackport", "dappled_forest"), world.getBiome(at).getRegistryName(),
                "the dappled forest's column biome is Game Drops'");
            // Its vegetal step at population (Game Drops' PoplarFeatures.vegetation): poplars, logs and leaves.
            int logs = 0;
            int leaves = 0;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    world.getChunkProvider().provideChunk(d[0] + dx, d[1] + dz);
                }
            }
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int x = (d[0] - 1) * 16; x < (d[0] + 2) * 16; x++) {
                for (int z = (d[1] - 1) * 16; z < (d[1] + 2) * 16; z++) {
                    for (int y = 60; y < 130; y++) {
                        ResourceLocation id = world.getBlockState(p.setPos(x, y, z)).getBlock().getRegistryName();
                        if (id != null && id.getNamespace().equals("gamedropsbackport")) {
                            logs += id.getPath().equals("poplar_log") ? 1 : 0;
                            leaves += id.getPath().endsWith("poplar_leaves") ? 1 : 0;
                        }
                    }
                }
            }
            check.ok(logs > 0 && leaves > 0, "dappled forest: Game Drops' poplars grow at population (" + logs + " logs, "
                + leaves + " leaves in 3x3 chunks)");
        }
    }

    private static int top(char[] b, int col) {
        for (int y = Pipeline.HEIGHT - 1; y >= 0; y--) {
            int code = b[col * Pipeline.HEIGHT + y];
            String n = BlockCodes.name(code);
            if (code == BlockCodes.GRASS_BLOCK || code != BlockCodes.AIR && code != BlockCodes.WATER && !n.contains("leaves")
                    && !n.contains("log") && !n.contains("grass") && !n.contains("fern") && !n.contains("lichen")) {
                return y;
            }
        }
        return -1;
    }

    /** As find, but the biome all around: at the chunk and two chunks off on each side (a forest, not its edge). */
    private static int[] interior(BiomeSource source, int biome, int qy) {
        for (int r = 0; r < 300; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    boolean all = true;
                    for (int[] o : DIRS) {
                        all &= source.biome((dx + 2 * o[0]) * 4 + 2, qy, (dz + 2 * o[1]) * 4 + 2) == biome;
                    }
                    if (all) {
                        return new int[]{dx, dz};
                    }
                }
            }
        }
        return null;
    }

    /** A quart column of the biome at quart y qy, nearest the origin, in chunks (its quart at the chunk's corner + 2). */
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
}
