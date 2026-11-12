package oracle;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.WorldGenSettings;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Vanilla 1.18.2's terrain answers from Mojang's own classes, for the Caves and Cliffs Backport's terrain core.
 *
 * Per seed, two files:
 *  points_SEED.bin  - int count; then per point x, y, z (ints) and the 15 router functions (doubles), in the
 *                     order of ROUTER below, each computed at the single point (interpolation markers pass through).
 *  chunks_SEED.bin  - int count; then per chunk cx, cz (ints) and 16*16*384 bytes, index (x*16+z)*384 + (y+64),
 *                     the block codes of CODES below, from getBaseColumn (noise fill + aquifers + ore veins,
 *                     before surface rules, carvers and features).
 *
 * Arguments: outDir pointsPerSeed chunkRadius scatteredChunks [seeds...]
 */
public final class Oracle1182Terrain {
    static final long[] SEEDS = {42L, 12345L, -7346534567L, 8675309L};

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        int points = Integer.parseInt(args[1]);
        int radius = Integer.parseInt(args[2]);
        int scattered = Integer.parseInt(args[3]);
        long[] seeds = SEEDS;
        if (args.length > 4) {
            seeds = new long[args.length - 4];
            for (int i = 4; i < args.length; i++) {
                seeds[i - 4] = Long.parseLong(args[i]);
            }
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RegistryAccess access = RegistryAccess.builtinCopy();
        Map<BlockState, Integer> codes = new IdentityHashMap<>();
        codes.put(Blocks.AIR.defaultBlockState(), 0);
        codes.put(Blocks.STONE.defaultBlockState(), 1);
        codes.put(Blocks.WATER.defaultBlockState(), 2);
        codes.put(Blocks.LAVA.defaultBlockState(), 3);
        codes.put(Blocks.COPPER_ORE.defaultBlockState(), 4);
        codes.put(Blocks.RAW_COPPER_BLOCK.defaultBlockState(), 5);
        codes.put(Blocks.GRANITE.defaultBlockState(), 6);
        codes.put(Blocks.DEEPSLATE_IRON_ORE.defaultBlockState(), 7);
        codes.put(Blocks.RAW_IRON_BLOCK.defaultBlockState(), 8);
        codes.put(Blocks.TUFF.defaultBlockState(), 9);
        LevelHeightAccessor height = LevelHeightAccessor.create(-64, 384);
        for (long seed : seeds) {
            NoiseBasedChunkGenerator generator = WorldGenSettings.makeDefaultOverworld(access, seed);
            NoiseRouter r = generator.router();
            DensityFunction[] router = {r.barrierNoise(), r.fluidLevelFloodednessNoise(), r.fluidLevelSpreadNoise(),
                r.lavaNoise(), r.temperature(), r.humidity(), r.continents(), r.erosion(), r.depth(), r.ridges(),
                r.initialDensityWithoutJaggedness(), r.finalDensity(), r.veinToggle(), r.veinRidged(), r.veinGap()};
            java.util.Random rnd = new java.util.Random(seed * 31L + 7L);
            try (DataOutputStream w = new DataOutputStream(new BufferedOutputStream(
                    new FileOutputStream(new File(out, "points_" + seed + ".bin"))))) {
                w.writeInt(points);
                for (int i = 0; i < points; i++) {
                    int span = i % 3 == 0 ? 400 : i % 3 == 1 ? 20000 : 3000000;
                    int x = rnd.nextInt(span * 2) - span;
                    int z = rnd.nextInt(span * 2) - span;
                    int y = rnd.nextInt(384) - 64;
                    if (i % 2 == 0) {
                        x &= ~3;
                        z &= ~3;
                        y &= ~7;
                    }
                    w.writeInt(x);
                    w.writeInt(y);
                    w.writeInt(z);
                    DensityFunction.SinglePointContext at = new DensityFunction.SinglePointContext(x, y, z);
                    for (DensityFunction f : router) {
                        w.writeDouble(f.compute(at));
                    }
                }
            }
            int side = radius * 2;
            int count = side * side + scattered;
            long start = System.nanoTime();
            try (DataOutputStream w = new DataOutputStream(new BufferedOutputStream(
                    new FileOutputStream(new File(out, "chunks_" + seed + ".bin"))))) {
                w.writeInt(count);
                byte[] buffer = new byte[384];
                for (int i = 0; i < count; i++) {
                    int cx;
                    int cz;
                    if (i < side * side) {
                        cx = i / side - radius;
                        cz = i % side - radius;
                    } else {
                        int span = i % 2 == 0 ? 600 : 100000;
                        cx = rnd.nextInt(span * 2) - span;
                        cz = rnd.nextInt(span * 2) - span;
                    }
                    w.writeInt(cx);
                    w.writeInt(cz);
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            NoiseColumn column = generator.getBaseColumn(cx * 16 + lx, cz * 16 + lz, height);
                            for (int y = -64; y < 320; y++) {
                                Integer code = codes.get(column.getBlock(y));
                                buffer[y + 64] = (byte) (code == null ? 255 : code);
                            }
                            w.write(buffer);
                        }
                    }
                }
            }
            System.out.println("seed " + seed + " done: " + count + " chunks in "
                + (System.nanoTime() - start) / 1000000L + " ms");
        }
    }
}
