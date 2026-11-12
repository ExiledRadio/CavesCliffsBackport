package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.cavesandcliffsbackport.terrain.core.Structures;
import net.minecraft.init.Biomes;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.MapGenMineshaft;
import net.minecraft.world.gen.structure.MapGenScatteredFeature;
import net.minecraft.world.gen.structure.MapGenStronghold;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.MapGenVillage;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureMineshaftStart;
import net.minecraft.world.gen.structure.StructureOceanMonument;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraft.world.gen.structure.WoodlandMansionPieces;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * 1.12's own structures where 1.18.2 puts them: each generator's start check asks the terrain core's
 * {@link Structures} (1.18.2's structure sets: placement, the structure chosen and its own start checks, so a start
 * is where Chunkbase shows one), and its pieces stay 1.12's. {@code /locate} and explorer maps search the same starts.
 *
 * <p>What 1.12 has no pieces for (pillager outposts, buried treasure, ruined portals, shipwrecks, ocean ruins) is not
 * generated here; the family's backports place their own.
 */
public final class ModernStructures {

    private final Structures structures;

    ModernStructures(Structures structures) {
        this.structures = structures;
    }

    /** The structure of set starting at the chunk (its index in Structures.STRUCTURE_NAMES[set]), or -1. */
    int startAt(int set, int cx, int cz) {
        synchronized (structures) {
            return structures.startAt(set, cx, cz);
        }
    }

    /**
     * The block position of the nearest start of any of the sets, searched in growing squares up to maxRadius
     * chunks; skips chunks already generated when unexplored is set (explorer maps).
     */
    BlockPos nearest(World world, BlockPos from, boolean unexplored, int maxRadius, int... sets) {
        int cx = from.getX() >> 4;
        int cz = from.getZ() >> 4;
        for (int r = Math.min(32, maxRadius); ; r = Math.min(r * 2, maxRadius)) {
            int[] best = null;
            long bestD = Long.MAX_VALUE;
            for (int set : sets) {
                List<int[]> starts;
                synchronized (structures) {
                    starts = structures.starts(set, cx - r, cz - r, cx + r + 1, cz + r + 1);
                }
                for (int[] s : starts) {
                    if (unexplored && world.isChunkGeneratedAt(s[0], s[1])) {
                        continue;
                    }
                    long dx = s[0] - cx;
                    long dz = s[1] - cz;
                    long d = dx * dx + dz * dz;
                    if (d < bestD) {
                        bestD = d;
                        best = s;
                    }
                }
            }
            // A start outside the square could be closer than one in a corner: accept within the inscribed circle.
            if (best != null && (bestD <= (long) r * r || r >= maxRadius)) {
                return new BlockPos((best[0] << 4) + 8, 64, (best[1] << 4) + 8);
            }
            if (r >= maxRadius) {
                return null;
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    final class Village extends MapGenVillage {
        @Override
        protected boolean canSpawnStructureAtCoords(int chunkX, int chunkZ) {
            return startAt(Structures.VILLAGES, chunkX, chunkZ) >= 0;
        }

        @Override
        public BlockPos getNearestStructurePos(World worldIn, BlockPos pos, boolean findUnexplored) {
            this.world = worldIn;
            return nearest(worldIn, pos, findUnexplored, 1024, Structures.VILLAGES);
        }
    }

    /** Desert pyramids, jungle temples, swamp huts and igloos, on their four 1.18 grids. */
    final class Temple extends MapGenScatteredFeature {
        private final int[] sets = {Structures.DESERT_PYRAMIDS, Structures.JUNGLE_TEMPLES, Structures.SWAMP_HUTS,
            Structures.IGLOOS};
        private final Biome[] styles = {Biomes.DESERT, Biomes.JUNGLE, Biomes.SWAMPLAND, Biomes.ICE_PLAINS};
        private final ModernGenerator generator;

        Temple(ModernGenerator generator) {
            this.generator = generator;
        }

        private int which(int cx, int cz) {
            for (int i = 0; i < sets.length; i++) {
                if (startAt(sets[i], cx, cz) >= 0) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        protected boolean canSpawnStructureAtCoords(int chunkX, int chunkZ) {
            return which(chunkX, chunkZ) >= 0;
        }

        @Override
        protected StructureStart getStructureStart(int chunkX, int chunkZ) {
            int i = which(chunkX, chunkZ);
            StructureStart start = new MapGenScatteredFeature.Start(this.world, this.rand, chunkX, chunkZ, styles[Math.max(i, 0)]);
            if (i == 0) {
                // 1.12's pyramid stands at y 64; 1.18's on the lowest corner of its 21x21 footprint, one block down.
                int x = chunkX << 4;
                int z = chunkZ << 4;
                int y = Math.min(Math.min(generator.surface(x, z), generator.surface(x + 20, z)),
                        Math.min(generator.surface(x, z + 20), generator.surface(x + 20, z + 20)));
                shift(start, y - 1 - 64);
            }
            return start;
        }

        @Override
        public BlockPos getNearestStructurePos(World worldIn, BlockPos pos, boolean findUnexplored) {
            this.world = worldIn;
            return nearest(worldIn, pos, findUnexplored, 1024, sets);
        }
    }

    final class Monument extends StructureOceanMonument {
        @Override
        protected boolean canSpawnStructureAtCoords(int chunkX, int chunkZ) {
            return startAt(Structures.OCEAN_MONUMENTS, chunkX, chunkZ) >= 0;
        }

        @Override
        public BlockPos getNearestStructurePos(World worldIn, BlockPos pos, boolean findUnexplored) {
            this.world = worldIn;
            return nearest(worldIn, pos, findUnexplored, 1024, Structures.OCEAN_MONUMENTS);
        }
    }

    final class Mineshaft extends MapGenMineshaft {
        @Override
        protected boolean canSpawnStructureAtCoords(int chunkX, int chunkZ) {
            return startAt(Structures.MINESHAFTS, chunkX, chunkZ) >= 0;
        }

        @Override
        protected StructureStart getStructureStart(int chunkX, int chunkZ) {
            Type type = startAt(Structures.MINESHAFTS, chunkX, chunkZ) == 1 ? Type.MESA : Type.NORMAL;
            return new StructureMineshaftStart(this.world, this.rand, chunkX, chunkZ, type);
        }

        @Override
        public BlockPos getNearestStructurePos(World worldIn, BlockPos pos, boolean findUnexplored) {
            this.world = worldIn;
            return nearest(worldIn, pos, findUnexplored, 128, Structures.MINESHAFTS);
        }
    }

    final class Stronghold extends MapGenStronghold {
        @Override
        protected boolean canSpawnStructureAtCoords(int chunkX, int chunkZ) {
            synchronized (structures) {
                return structures.isFeatureChunk(Structures.STRONGHOLDS, chunkX, chunkZ);
            }
        }

        @Override
        public BlockPos getNearestStructurePos(World worldIn, BlockPos pos, boolean findUnexplored) {
            this.world = worldIn;
            int[] rings;
            synchronized (structures) {
                rings = structures.ringPositions();
            }
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (int i = 0; i < rings.length; i += 2) {
                BlockPos p = new BlockPos((rings[i] << 4) + 8, 32, (rings[i + 1] << 4) + 8);
                double d = p.distanceSq(pos);
                if (d < bestD) {
                    best = p;
                    bestD = d;
                }
            }
            return best;
        }
    }

    /** Woodland mansions: 1.12's pieces, on the height of our terrain (1.12's start reads vanilla 1.12's noise). */
    final class Mansion extends MapGenStructure {
        private final ModernGenerator generator;

        Mansion(ModernGenerator generator) {
            this.generator = generator;
        }

        @Override
        public String getStructureName() {
            return "Mansion";
        }

        @Override
        protected boolean canSpawnStructureAtCoords(int chunkX, int chunkZ) {
            return startAt(Structures.WOODLAND_MANSIONS, chunkX, chunkZ) >= 0;
        }

        @Override
        protected StructureStart getStructureStart(int chunkX, int chunkZ) {
            return new MansionStart(this.world, generator, this.rand, chunkX, chunkZ);
        }

        @Override
        public BlockPos getNearestStructurePos(World worldIn, BlockPos pos, boolean findUnexplored) {
            this.world = worldIn;
            return nearest(worldIn, pos, findUnexplored, 1024, Structures.WOODLAND_MANSIONS);
        }
    }

    /**
     * WoodlandMansion.Start with the corner heights from our terrain. Saved under its own name; its pieces are
     * 1.12's ({@code WMP}), and after loading it behaves as 1.12's start (the cobblestone under the floor included).
     */
    public static final class MansionStart extends StructureStart {
        public MansionStart() {
        }

        MansionStart(World world, ModernGenerator generator, Random rand, int chunkX, int chunkZ) {
            super(chunkX, chunkZ);
            Rotation rotation = Rotation.values()[rand.nextInt(Rotation.values().length)];
            int i = 5;
            int j = 5;
            if (rotation == Rotation.CLOCKWISE_90) {
                i = -5;
            } else if (rotation == Rotation.CLOCKWISE_180) {
                i = -5;
                j = -5;
            } else if (rotation == Rotation.COUNTERCLOCKWISE_90) {
                j = -5;
            }
            int x = (chunkX << 4) + 7;
            int z = (chunkZ << 4) + 7;
            int k = Math.min(Math.min(generator.surface(x, z), generator.surface(x, z + j)),
                    Math.min(generator.surface(x + i, z), generator.surface(x + i, z + j))) - 1;
            if (k >= 60) {
                BlockPos pos = new BlockPos((chunkX << 4) + 8, k + 1, (chunkZ << 4) + 8);
                List<WoodlandMansionPieces.MansionTemplate> list = new LinkedList<>();
                WoodlandMansionPieces.generateMansion(world.getSaveHandler().getStructureTemplateManager(), pos, rotation, list, rand);
                this.components.addAll(list);
                this.updateBoundingBox();
            }
        }

        @Override
        public void generateStructure(World worldIn, Random rand, net.minecraft.world.gen.structure.StructureBoundingBox structurebb) {
            super.generateStructure(worldIn, rand, structurebb);
            int y = this.boundingBox.minY;
            for (int x = structurebb.minX; x <= structurebb.maxX; ++x) {
                for (int z = structurebb.minZ; z <= structurebb.maxZ; ++z) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!worldIn.isAirBlock(pos) && this.boundingBox.isVecInside(pos)) {
                        boolean inside = false;
                        for (StructureComponent c : this.components) {
                            if (c.getBoundingBox().isVecInside(pos)) {
                                inside = true;
                                break;
                            }
                        }
                        if (inside) {
                            for (int l = y - 1; l > com.exiledradio.cavesandcliffsbackport.height.Heights.minY(worldIn) + 1; --l) {
                                BlockPos below = new BlockPos(x, l, z);
                                if (!worldIn.isAirBlock(below) && !worldIn.getBlockState(below).getMaterial().isLiquid()) {
                                    break;
                                }
                                worldIn.setBlockState(below, net.minecraft.init.Blocks.COBBLESTONE.getDefaultState(), 2);
                            }
                        }
                    }
                }
            }
        }

        @Override
        public boolean isSizeableStructure() {
            return !this.components.isEmpty();
        }
    }

    /** Registers the starts this class saves under its own names. Once, in init. */
    public static void register() {
        MapGenStructureIO.registerStructure(MansionStart.class, "CavesAndCliffsMansion");
    }

    private static void shift(StructureStart start, int dy) {
        if (dy == 0) {
            return;
        }
        for (StructureComponent c : start.getComponents()) {
            c.offset(0, dy, 0);
        }
        start.getBoundingBox().offset(0, dy, 0);
    }
}
