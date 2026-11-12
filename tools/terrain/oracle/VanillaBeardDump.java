package net.minecraft.world.level.levelgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.feature.NoiseEffect;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import java.util.zip.GZIPOutputStream;

/**
 * Vanilla 1.18.2 noise fill with a real Beardifier (its own constructor, over a feature manager that hands it one
 * structure start holding made-up pieces: plain BEARD pieces, BURY pieces, rigid and terrain-matching jigsaw
 * pieces with ground level deltas and junctions), for the Caves and Cliffs Backport's BeardCheck. Writes
 * beard_SEED.bin.gz: kernel hash, int areas; per area int pieces and each piece (minX minY minZ maxX maxY maxZ,
 * effect 0/1/2, jigsaw, groundLevelDelta, rigid, junction count, then x groundY z per junction), int chunks, and
 * per chunk int cx, int cz and the 16 * 16 * 384 block codes after fill.
 *
 * Arguments: outDir areaSide piecesPerArea [seeds...]
 */
public final class VanillaBeardDump {
    static final class Spec {
        int minX, minY, minZ, maxX, maxY, maxZ, effect, delta;
        boolean jigsaw, rigid;
        List<int[]> junctions = new ArrayList<>();
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        int side = Integer.parseInt(args[1]);
        int perArea = Integer.parseInt(args[2]);
        long[] seeds = {42L, 12345L, -7346534567L, 8675309L};
        if (args.length > 3) {
            seeds = new long[args.length - 3];
            for (int i = 3; i < args.length; i++) {
                seeds[i - 3] = Long.parseLong(args[i]);
            }
        }
        VanillaWorld.boot();
        Constructor<EmptyPoolElement> emptyCtor = EmptyPoolElement.class.getDeclaredConstructor();
        emptyCtor.setAccessible(true);
        EmptyPoolElement rigidElement = emptyCtor.newInstance();
        rigidElement.setProjection(StructureTemplatePool.Projection.RIGID);
        EmptyPoolElement matchingElement = emptyCtor.newInstance();
        matchingElement.setProjection(StructureTemplatePool.Projection.TERRAIN_MATCHING);
        Field kernelField = Beardifier.class.getDeclaredField("BEARD_KERNEL");
        kernelField.setAccessible(true);
        float[] kernel = (float[]) kernelField.get(null);
        long kernelHash = 1L;
        for (float f : kernel) {
            kernelHash = kernelHash * 31L + Float.floatToIntBits(f);
        }
        byte[] blocks = new byte[16 * 16 * 384];
        for (long seed : seeds) {
            VanillaWorld world = new VanillaWorld(seed);
            Random r = new Random(seed * 7L + 3L);
            int[][] centers = {{0, 0}, {r.nextInt(4000) - 2000, r.nextInt(4000) - 2000},
                {r.nextInt(40000) - 20000, r.nextInt(40000) - 20000}};
            try (DataOutputStream w = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(
                    new FileOutputStream(new File(out, "beard_" + seed + ".bin.gz")), 1 << 16), 1 << 16))) {
                w.writeLong(kernelHash);
                w.writeInt(centers.length);
                for (int a = 0; a < centers.length; a++) {
                    int ccx = centers[a][0];
                    int ccz = centers[a][1];
                    // The third area packs its pieces into a quarter of the space: many overlapping terms.
                    int span = a == 2 ? side * 4 : side * 16;
                    List<Spec> specs = new ArrayList<>();
                    for (int p = 0; p < perArea; p++) {
                        Spec s = new Spec();
                        int x = (ccx << 4) - span / 2 + r.nextInt(span);
                        int z = (ccz << 4) - span / 2 + r.nextInt(span);
                        int ground = world.generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, VanillaWorld.HEIGHT);
                        int kind = r.nextInt(100);
                        int sx = 3 + r.nextInt(18);
                        int sy = 3 + r.nextInt(10);
                        int sz = 3 + r.nextInt(18);
                        s.minX = x;
                        s.minZ = z;
                        s.maxX = x + sx - 1;
                        s.maxZ = z + sz - 1;
                        if (kind < 30) {
                            s.effect = 2;
                            s.minY = ground - 3 + r.nextInt(6);
                        } else if (kind < 45) {
                            s.effect = 1;
                            s.minY = ground - 25 + r.nextInt(20);
                        } else {
                            s.effect = 2;
                            s.jigsaw = true;
                            s.rigid = kind < 85;
                            s.delta = r.nextInt(3);
                            s.minY = ground - 2 + r.nextInt(4);
                            int nj = r.nextInt(4);
                            for (int j = 0; j < nj; j++) {
                                int jx = r.nextBoolean() ? s.minX - 1 : s.maxX + 1;
                                int jz = s.minZ + r.nextInt(sz);
                                s.junctions.add(new int[]{jx, s.minY + s.delta, jz});
                            }
                        }
                        s.maxY = s.minY + sy - 1;
                        specs.add(s);
                    }
                    w.writeInt(specs.size());
                    List<StructurePiece> pieces = new ArrayList<>();
                    for (Spec s : specs) {
                        w.writeInt(s.minX);
                        w.writeInt(s.minY);
                        w.writeInt(s.minZ);
                        w.writeInt(s.maxX);
                        w.writeInt(s.maxY);
                        w.writeInt(s.maxZ);
                        w.writeInt(s.effect);
                        w.writeBoolean(s.jigsaw);
                        w.writeInt(s.delta);
                        w.writeBoolean(s.rigid);
                        w.writeInt(s.junctions.size());
                        for (int[] j : s.junctions) {
                            w.writeInt(j[0]);
                            w.writeInt(j[1]);
                            w.writeInt(j[2]);
                        }
                        BoundingBox box = new BoundingBox(s.minX, s.minY, s.minZ, s.maxX, s.maxY, s.maxZ);
                        if (s.jigsaw) {
                            PoolElementStructurePiece piece = new PoolElementStructurePiece(null,
                                s.rigid ? rigidElement : matchingElement, new BlockPos(s.minX, s.minY, s.minZ), s.delta,
                                Rotation.NONE, box);
                            for (int[] j : s.junctions) {
                                piece.addJunction(new JigsawJunction(j[0], j[1], j[2], 0, StructureTemplatePool.Projection.RIGID));
                            }
                            pieces.add(piece);
                        } else {
                            final NoiseEffect effect = s.effect == 1 ? NoiseEffect.BURY : NoiseEffect.BEARD;
                            pieces.add(new StructurePiece(StructurePieceType.MINE_SHAFT_ROOM, 0, box) {
                                @Override
                                protected void addAdditionalSaveData(StructurePieceSerializationContext c, CompoundTag t) {
                                }

                                @Override
                                public void postProcess(WorldGenLevel l, StructureFeatureManager m, ChunkGenerator g,
                                                        Random rr, BoundingBox b, ChunkPos cp, BlockPos bp) {
                                }

                                @Override
                                public NoiseEffect getNoiseEffect() {
                                    return effect;
                                }
                            });
                        }
                    }
                    StructureStart start = new StructureStart(null, new ChunkPos(ccx, ccz), 0, new PiecesContainer(pieces));
                    StructureFeatureManager manager = new StructureFeatureManager(null, null, null) {
                        @Override
                        public List<StructureStart> startsForFeature(SectionPos pos,
                                                                     Predicate<ConfiguredStructureFeature<?, ?>> test) {
                            return List.of(start);
                        }
                    };
                    int areaSide = a == 2 ? Math.max(2, side / 3) : side;
                    w.writeInt(areaSide * areaSide);
                    for (int i = 0; i < areaSide * areaSide; i++) {
                        int cx = ccx - areaSide / 2 + i / areaSide;
                        int cz = ccz - areaSide / 2 + i % areaSide;
                        ChunkPos pos = new ChunkPos(cx, cz);
                        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, VanillaWorld.HEIGHT, world.biomes, null);
                        Beardifier beardifier = new Beardifier(manager, chunk);
                        NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(world.router, () -> beardifier,
                            world.settings, world.picker, Blender.empty());
                        VanillaFillBench.fill(noiseChunk, pos, VanillaWorld.codes, blocks);
                        w.writeInt(cx);
                        w.writeInt(cz);
                        w.write(blocks);
                    }
                }
            }
            System.out.println("seed " + seed + " done");
        }
    }
}
