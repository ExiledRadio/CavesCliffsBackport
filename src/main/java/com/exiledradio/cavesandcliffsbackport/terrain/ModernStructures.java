package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.cavesandcliffsbackport.terrain.core.Structures;
import net.minecraft.init.Biomes;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.MapGenMineshaft;
import net.minecraft.world.gen.structure.MapGenScatteredFeature;
import net.minecraft.world.gen.structure.MapGenStronghold;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.MapGenVillage;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureMineshaftStart;
import net.minecraft.world.gen.structure.StructureOceanMonument;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraft.world.gen.structure.StructureVillagePieces;
import net.minecraft.world.gen.structure.WoodlandMansionPieces;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

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
            if (!structures.isFeatureChunk(set, cx, cz)) {
                return -1;
            }
            // A candidate's start check reads the noise (biome and heights); 1.12's generators and the beard ask
            // the same few candidates for every chunk near them.
            Map<Long, Integer> m = chosen[set];
            long k = (long) cx << 32 | (cz & 0xFFFFFFFFL);
            Integer known = m.get(k);
            if (known == null) {
                known = structures.startAt(set, cx, cz);
                m.put(k, known);
            }
            return known;
        }
    }

    /** Per set, candidates' start checks, the most recently used 1024 kept (a pure function of seed and chunk). */
    @SuppressWarnings("unchecked")
    private final Map<Long, Integer>[] chosen = new Map[Structures.SETS];

    {
        for (int i = 0; i < chosen.length; i++) {
            chosen[i] = new java.util.LinkedHashMap<Long, Integer>(256, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Integer> eldest) {
                    return size() > 1024;
                }
            };
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

    /**
     * The laid-out start of a 1.12 generator at chunk x, z, laying it out first if the generator has not (as its
     * {@code generate} would), or null if none starts there. Server thread.
     */
    static StructureStart laidOut(MapGenStructure generator, Map<Long, StructureStart> map, World world, int x, int z) {
        long k = ChunkPos.asLong(x, z);
        StructureStart s = map.get(k);
        if (s == null) {
            // Lays out every start within eight chunks, each from its own seed, as when the chunk is generated.
            generator.generate(world, x, z, null);
            s = map.get(k);
        }
        return s;
    }

    /** Whether a start's box grown by 12 blocks (1.18's adapt_noise box) reaches chunk cx, cz. */
    static boolean reaches(StructureStart s, int cx, int cz) {
        StructureBoundingBox b = s.getBoundingBox();
        int x = cx << 4;
        int z = cz << 4;
        return b != null && b.maxX + 12 >= x && b.minX - 12 <= x + 15 && b.maxZ + 12 >= z && b.minZ - 12 <= z + 15;
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * 1.12's villages, where 1.18 starts villages. Each house is set on the ground when the village is laid out
     * rather than when its chunk is populated: 1.12's average ground level over the house's footprint (the first
     * free block, at least the world's average ground level less one, as {@code getAverageGroundLevel}), but read
     * from the terrain's noise before any chunk exists, so the terrain can adapt to the houses (1.18's beardifier)
     * and the heights do not depend on which chunks were generated first. Pieces of other mods keep measuring as
     * they build.
     */
    final class Village extends MapGenVillage {
        private final ModernGenerator generator;

        Village(ModernGenerator generator) {
            this.generator = generator;
        }

        @Override
        protected boolean canSpawnStructureAtCoords(int chunkX, int chunkZ) {
            return startAt(Structures.VILLAGES, chunkX, chunkZ) >= 0;
        }

        @Override
        protected StructureStart getStructureStart(int chunkX, int chunkZ) {
            StructureStart start = super.getStructureStart(chunkX, chunkZ);
            ground(start, this.world, generator);
            return start;
        }

        /**
         * Every laid-out village whose adapt_noise box reaches chunk cx, cz, in the order 1.18 lists a chunk's
         * references ({@link #referenceOrder}). Server thread.
         */
        void startsNear(World world, int cx, int cz, Consumer<StructureStart> out) {
            this.world = world;
            int s = Structures.spacing(Structures.VILLAGES);
            long[] found = new long[4];
            int n = 0;
            for (int gx = Math.floorDiv(cx - 8, s); gx <= Math.floorDiv(cx + 8, s); gx++) {
                for (int gz = Math.floorDiv(cz - 8, s); gz <= Math.floorDiv(cz + 8, s); gz++) {
                    long p;
                    synchronized (structures) {
                        p = structures.potentialChunk(Structures.VILLAGES, gx * s, gz * s);
                    }
                    int x = (int) (p >> 32);
                    int z = (int) p;
                    if (Math.abs(x - cx) <= 8 && Math.abs(z - cz) <= 8 && startAt(Structures.VILLAGES, x, z) >= 0) {
                        if (n == found.length) {
                            found = java.util.Arrays.copyOf(found, n * 2);
                        }
                        found[n++] = ChunkPos.asLong(x, z);
                    }
                }
            }
            for (long k : referenceOrder(found, n)) {
                StructureStart start = laidOut(this, this.structureMap, world, (int) k, (int) (k >> 32));
                if (start != null && start.isSizeableStructure() && reaches(start, cx, cz)) {
                    out.accept(start);
                }
            }
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

        /** Every laid-out stronghold whose adapt_noise box reaches chunk cx, cz, in 1.18's reference order. */
        void startsNear(World world, int cx, int cz, Consumer<StructureStart> out) {
            this.world = world;
            int[] rings;
            synchronized (structures) {
                rings = structures.ringPositions();
            }
            long[] found = new long[2];
            int n = 0;
            for (int i = 0; i < rings.length; i += 2) {
                if (Math.abs(rings[i] - cx) <= 8 && Math.abs(rings[i + 1] - cz) <= 8) {
                    if (n == found.length) {
                        found = java.util.Arrays.copyOf(found, n * 2);
                    }
                    found[n++] = ChunkPos.asLong(rings[i], rings[i + 1]);
                }
            }
            for (long k : referenceOrder(found, n)) {
                StructureStart start = laidOut(this, this.structureMap, world, (int) k, (int) (k >> 32));
                if (start != null && start.isSizeableStructure() && reaches(start, cx, cz)) {
                    out.accept(start);
                }
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

    // ---------------------------------------------------------------------------------------------
    // Village houses on the ground at layout

    /**
     * Per 1.12 village piece class, the K of its {@code boundingBox.offset(0, averageGroundLvl - maxY + K, 0)} in
     * addComponentParts (the box's top above the ground). Roads (Path) follow the ground block by block and are not
     * here; nor are other mods' pieces.
     */
    private static final Map<Class<?>, Integer> TOP_ABOVE_GROUND = new HashMap<>();
    private static final Field AVERAGE_GROUND;

    static {
        TOP_ABOVE_GROUND.put(StructureVillagePieces.Church.class, 11);
        TOP_ABOVE_GROUND.put(StructureVillagePieces.Field1.class, 3);
        TOP_ABOVE_GROUND.put(StructureVillagePieces.Field2.class, 3);
        TOP_ABOVE_GROUND.put(StructureVillagePieces.Hall.class, 6);
        TOP_ABOVE_GROUND.put(StructureVillagePieces.House1.class, 8);
        TOP_ABOVE_GROUND.put(StructureVillagePieces.House2.class, 5);
        TOP_ABOVE_GROUND.put(StructureVillagePieces.House3.class, 6);
        TOP_ABOVE_GROUND.put(StructureVillagePieces.House4Garden.class, 5);
        TOP_ABOVE_GROUND.put(StructureVillagePieces.Torch.class, 3);
        TOP_ABOVE_GROUND.put(StructureVillagePieces.Well.class, 3);
        TOP_ABOVE_GROUND.put(StructureVillagePieces.Start.class, 3);
        TOP_ABOVE_GROUND.put(StructureVillagePieces.WoodHut.class, 5);
        AVERAGE_GROUND = ObfuscationReflectionHelper.findField(StructureVillagePieces.Village.class, "field_143015_k");
    }

    /** A village piece's ground level (averageGroundLvl), or -1 where it is not set yet. */
    public static int groundOf(StructureComponent piece) {
        try {
            return AVERAGE_GROUND.getInt(piece);
        } catch (IllegalAccessException e) {
            return -1;
        }
    }

    /** Whether a piece is one {@link #ground} sets on the ground (a 1.12 village house, lamp or well). */
    public static boolean grounded(StructureComponent piece) {
        return TOP_ABOVE_GROUND.containsKey(piece.getClass()) && groundOf(piece) >= 0;
    }

    /** Sets each 1.12 village house of a start on the ground of the terrain's noise, and the start's box around them. */
    static void ground(StructureStart start, World world, ModernGenerator generator) {
        if (start.getComponents().isEmpty()) {
            return;
        }
        int floor = world.provider.getAverageGroundLevel() - 1;
        StructureBoundingBox all = null;
        for (StructureComponent c : start.getComponents()) {
            Integer top = TOP_ABOVE_GROUND.get(c.getClass());
            StructureBoundingBox b = c.getBoundingBox();
            if (top != null && groundOf(c) < 0) {
                int sum = 0;
                int count = 0;
                for (int z = b.minZ; z <= b.maxZ; ++z) {
                    for (int x = b.minX; x <= b.maxX; ++x) {
                        sum += Math.max(generator.surface(x, z), floor);
                        ++count;
                    }
                }
                int average = sum / count;
                try {
                    AVERAGE_GROUND.setInt(c, average);
                } catch (IllegalAccessException e) {
                    continue;
                }
                c.offset(0, average - b.maxY + top, 0);
            }
            if (all == null) {
                all = new StructureBoundingBox(b);
            } else {
                all.expandTo(b);
            }
        }
        StructureBoundingBox box = start.getBoundingBox();
        box.minX = all.minX;
        box.minY = all.minY;
        box.minZ = all.minZ;
        box.maxX = all.maxX;
        box.maxY = all.maxY;
        box.maxZ = all.maxZ;
    }

    // ---------------------------------------------------------------------------------------------
    // 1.18's order of a chunk's structure references

    /**
     * The order 1.18 iterates one structure's references from a chunk: a fastutil LongOpenHashSet (default
     * capacity, 32 slots below 24 entries) filled in createReferences' scan order (x outer, z inner), iterated as
     * its iterator does: the zero key first, then the slots from the top down.
     */
    public static long[] referenceOrder(long[] keys, int n) {
        if (n <= 1) {
            return java.util.Arrays.copyOf(keys, n);
        }
        // Scan order: x outer, z inner (ChunkPos.asLong: x low, z high).
        Long[] sorted = new Long[n];
        for (int i = 0; i < n; i++) {
            sorted[i] = keys[i];
        }
        java.util.Arrays.sort(sorted, (a, b) -> {
            int c = Integer.compare((int) (long) a, (int) (long) b);
            return c != 0 ? c : Integer.compare((int) (a >> 32), (int) (b >> 32));
        });
        int size = 32;
        while (n >= size * 3 / 4) {
            size *= 2;
        }
        long[] table = new long[size];
        boolean zero = false;
        for (Long boxed : sorted) {
            long k = boxed;
            if (k == 0L) {
                zero = true;
                continue;
            }
            long h = k * 0x9E3779B97F4A7C15L;
            h ^= h >>> 32;
            h ^= h >>> 16;
            int pos = (int) h & (size - 1);
            while (table[pos] != 0L) {
                pos = (pos + 1) & (size - 1);
            }
            table[pos] = k;
        }
        long[] out = new long[n];
        int at = 0;
        if (zero) {
            out[at++] = 0L;
        }
        for (int pos = size - 1; pos >= 0; pos--) {
            if (table[pos] != 0L) {
                out[at++] = table[pos];
            }
        }
        return out;
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
