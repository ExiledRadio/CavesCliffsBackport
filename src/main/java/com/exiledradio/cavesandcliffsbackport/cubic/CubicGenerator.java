package com.exiledradio.cavesandcliffsbackport.cubic;

import com.exiledradio.backportcore.waterlog.Waterlogging;
import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernGenerator;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline;
import io.github.opencubicchunks.cubicchunks.api.util.Box;
import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.api.world.ICubeProviderServer;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import io.github.opencubicchunks.cubicchunks.api.worldgen.CubePrimer;
import io.github.opencubicchunks.cubicchunks.api.worldgen.ICubeGenerator;
import io.github.opencubicchunks.cubicchunks.core.CubicChunksConfig;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.registry.GameRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The cube generator of a cubic Caves &amp; Cliffs world: 1.18.2's overworld, block for block what the world's
 * {@link ModernGenerator} gives a normal Caves &amp; Cliffs world, in Cubic Chunks' cubes.
 *
 * <ul>
 *   <li><b>Columns</b> ({@link #generateColumn}): 1.18 finishes a chunk as a whole column (its decoration crosses
 *       chunk borders, and a feature sees what was decorated before it), so a column is finished as in a normal world:
 *       the same pipeline, workers and order, its biomes, 3D biomes, fluid updates and structure starts. Its blocks are
 *       kept as the core's codes until each of its 24 cubes has taken its own.</li>
 *   <li><b>Cubes</b> ({@link #generateCube}): each cube reads its 16 blocks of height straight from the finished
 *       column's codes (no copy: {@link CodePrimer}). Above y 319 and below -64 (outside the world's limits, which
 *       Cubic Chunks keeps) a cube is air. A cube's chests, spawners, nests and waterlogged blocks are given to it when
 *       it is in the world ({@link #onCubeLoad}).</li>
 *   <li><b>All cubes of a column together.</b> A finished column is never finished again the same way (the pipeline
 *       drops it once nothing reads it), so a column's cubes are made in the tick its column is: population asks for
 *       whole columns ({@link #getPopulationPregenerationRequirements}), and whatever is left at the end of the tick, or
 *       when the world saves, is generated then ({@link #drain}).</li>
 *   <li><b>Population</b>: 1.12's population of a column (the structures' pieces, the decoration events other mods
 *       generate in, world-generation animals) and other mods' world generators, as in a normal world, run once per
 *       column, when its anchor cube (y 0 to 15) is populated. A cube is complete once the anchors of its own column
 *       and of the three to its -x and -z are populated ({@link #getFullPopulationRequirements}): 1.12's rule for a
 *       chunk, whose population writes into the chunk and the three to its +x and +z.</li>
 * </ul>
 */
public final class CubicGenerator implements ICubeGenerator {

    /** The lowest and highest cube y of the world (y -64 to 319). */
    static final int MIN_CUBE = CubicSupport.MIN_Y >> 4;
    static final int MAX_CUBE = (CubicSupport.MAX_Y >> 4) - 1;
    static final int CUBES = MAX_CUBE - MIN_CUBE + 1;
    private static final int ALL = (1 << CUBES) - 1;
    /** The cube whose population is its column's. */
    static final int ANCHOR = 0;

    private final WorldServer world;
    private final ModernGenerator generator;
    private final IBlockState[] states;
    private final IBlockState air = Blocks.AIR.getDefaultState();

    /** Finished columns whose cubes are not all made yet. */
    private final Map<Long, Column> columns = new HashMap<>();
    /** Tile entities and water for cubes made but not yet in the world (their load event follows at once). */
    private final Map<CubePos, Extras> extras = new HashMap<>();
    private boolean draining;
    private boolean warned;

    /** Counters for the self-test and benchmark. */
    public long columnsMade;
    public long cubesMade;
    public long drained;
    public long populated;
    public long cubeNanos;

    CubicGenerator(WorldServer world, ModernGenerator generator) {
        this.world = world;
        this.generator = generator;
        this.states = generator.states();
        CavesAndCliffsBackport.LOGGER.info("1.18 terrain in Cubic Chunks' cubes for {}, y {} to {}.",
                world.getWorldInfo().getWorldName(), CubicSupport.MIN_Y, CubicSupport.MAX_Y - 1);
    }

    /** The terrain generator behind the cubes. */
    public ModernGenerator terrain() {
        return generator;
    }

    /** A finished column: its codes, which cubes took theirs, and its specials by cube. */
    private static final class Column {
        final char[] blocks;
        int made;
        final Extras[] extras = new Extras[CUBES];

        Column(char[] blocks) {
            this.blocks = blocks;
        }

        Extras extras(int y) {
            int i = (y >> 4) - MIN_CUBE;
            Extras e = extras[i];
            if (e == null) {
                e = extras[i] = new Extras();
            }
            return e;
        }
    }

    private static final class Extras {
        final List<TileEntity> tiles = new ArrayList<>(2);
        long[] wet = new long[0];
        int wetCount;
    }


    private static long key(int x, int z) {
        return ChunkPos.asLong(x, z);
    }

    // ---------------------------------------------------------------------------------------------
    // Columns and cubes

    @Override
    public void generateColumn(Chunk chunk) {
        final List<Object[]> pending = new ArrayList<>();
        Pipeline.Proto p = generator.generateColumn(chunk, new ModernGenerator.Specials() {
            @Override
            public void tile(BlockPos pos, TileEntity te) {
                te.setPos(pos);
                pending.add(new Object[] {pos, te});
            }

            @Override
            public void water(long pos) {
                pending.add(new Object[] {pos});
            }
        });
        Column column = new Column(p.blocks);
        for (Object[] o : pending) {
            if (o.length == 2) {
                column.extras(((BlockPos) o[0]).getY()).tiles.add((TileEntity) o[1]);
            } else {
                long pos = (Long) o[0];
                Extras e = column.extras(BlockPos.fromLong(pos).getY());
                if (e.wetCount == e.wet.length) {
                    e.wet = java.util.Arrays.copyOf(e.wet, Math.max(8, e.wetCount * 2));
                }
                e.wet[e.wetCount++] = pos;
            }
        }
        synchronized (columns) {
            columns.put(key(chunk.x, chunk.z), column);
        }
        columnsMade++;
    }

    @Deprecated
    @Override
    public CubePrimer generateCube(int cubeX, int cubeY, int cubeZ) {
        return generateCube(cubeX, cubeY, cubeZ, new CubePrimer());
    }

    @Override
    public CubePrimer generateCube(int cubeX, int cubeY, int cubeZ, CubePrimer primer) {
        if (cubeY < MIN_CUBE || cubeY > MAX_CUBE) {
            return primer;
        }
        long t = System.nanoTime();
        long k = key(cubeX, cubeZ);
        Column column;
        synchronized (columns) {
            column = columns.get(k);
        }
        if (column == null) {
            column = lost(cubeX, cubeZ);
        }
        above(column, cubeX, cubeY, cubeZ);
        int bit = 1 << (cubeY - MIN_CUBE);
        column.made |= bit;
        if (column.made == ALL) {
            synchronized (columns) {
                columns.remove(k);
            }
        }
        Extras e = column.extras[cubeY - MIN_CUBE];
        if (e != null) {
            column.extras[cubeY - MIN_CUBE] = null;
            extras.put(new CubePos(cubeX, cubeY, cubeZ), e);
        }
        cubesMade++;
        CodePrimer out = new CodePrimer(column.blocks, (cubeY << 4) - Pipeline.MIN_Y, states, air);
        cubeNanos += System.nanoTime() - t;
        return out;
    }

    /**
     * Makes the column's cubes above this one first, top down. Cubic Chunks gives a new cube's sky light from the
     * column's surface as far as its cubes exist yet, and asks for a column's cubes from the bottom up: a cube under
     * the ground made before the ones over it starts in full sky light, which then spreads into its caves. Built top
     * down, every cube starts as a normal world's fresh chunk does, dark under the ground, so what reads the light
     * while it is built (1.12's mineshafts bridge gaps darker than 8 with planks) builds the same.
     */
    private void above(Column column, int x, int y, int z) {
        int above = ALL & ~((2 << (y - MIN_CUBE)) - 1);
        if ((column.made & above) == above) {
            return;
        }
        ICubeProviderServer cubes = (ICubeProviderServer) ((ICubicWorld) world).getCubeCache();
        for (int cy = MAX_CUBE; cy > y; cy--) {
            if ((column.made & 1 << (cy - MIN_CUBE)) == 0) {
                cubes.getCube(x, cy, z, ICubeProviderServer.Requirement.GENERATE);
            }
        }
    }

    /**
     * A cube whose column is not held: its column was made before the server stopped without saving (a crash) and
     * this cube never was. The pipeline gives the column again (filled; its decoration, which it already did, is not
     * repeated), so this cube may miss the features that reached into it.
     */
    private Column lost(int x, int z) {
        if (!warned) {
            warned = true;
            CavesAndCliffsBackport.LOGGER.warn("Cube column {}, {} was made before a crash and some of its cubes were not: "
                    + "they are made now, without the features that crossed into them.", x, z);
        }
        Pipeline.Proto p = generator.pipeline().finish(x, z);
        Column column = new Column(p.blocks);
        synchronized (columns) {
            columns.put(key(x, z), column);
        }
        return column;
    }

    /** A cube we made is in the world: its tile entities and the water in its waterlogged blocks. */
    void onCubeLoad(ICube cube) {
        Extras e = extras.remove(cube.getCoords());
        if (e == null) {
            return;
        }
        for (TileEntity te : e.tiles) {
            cube.addTileEntity(te);
        }
        if (e.wetCount > 0 && Waterlogging.isAvailable()) {
            // 2 | 16: no neighbor or observer updates (see ModernGenerator.placeWater).
            for (int i = 0; i < e.wetCount; i++) {
                Waterlogging.setWater(this.world, BlockPos.fromLong(e.wet[i]), true, 2 | 16);
            }
        }
    }

    /** Makes every cube of the columns finished so far that is not made yet (end of tick, world save). */
    void drain() {
        if (draining) {
            return;
        }
        List<long[]> todo;
        synchronized (columns) {
            if (columns.isEmpty()) {
                return;
            }
            todo = new ArrayList<>();
            for (Map.Entry<Long, Column> en : columns.entrySet()) {
                todo.add(new long[] {en.getKey(), en.getValue().made});
            }
        }
        draining = true;
        try {
            ICubeProviderServer cubes = (ICubeProviderServer) ((ICubicWorld) world).getCubeCache();
            for (long[] c : todo) {
                int x = (int) c[0];
                int z = (int) (c[0] >> 32);
                for (int y = MIN_CUBE; y <= MAX_CUBE; y++) {
                    if ((c[1] & 1L << (y - MIN_CUBE)) == 0) {
                        cubes.getCube(x, y, z, ICubeProviderServer.Requirement.GENERATE);
                        drained++;
                    }
                }
            }
        } finally {
            draining = false;
        }
    }

    /** For the self-test: columns finished whose cubes are not all made. */
    public int heldColumns() {
        synchronized (columns) {
            return columns.size();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Population

    @Override
    public void populate(ICube cube) {
        if (cube.getY() != ANCHOR) {
            return;
        }
        int x = cube.getX();
        int z = cube.getZ();
        generator.populate(x, z);
        if (!CubicChunksConfig.useVanillaChunkWorldGenerators) {
            // Other mods' world generators, as Chunk.populate runs them after the generator's populate. (With Cubic
            // Chunks' useVanillaChunkWorldGenerators on, it runs them itself for the anchor cube's column.)
            GameRegistry.generateWorld(x, z, world, generator, world.getChunkProvider());
        }
        populated++;
    }

    @Override
    public Box getFullPopulationRequirements(ICube cube) {
        int dy = ANCHOR - cube.getY();
        return new Box(-1, dy, -1, 0, dy, 0);
    }

    @Override
    public Box getPopulationPregenerationRequirements(ICube cube) {
        // A column's population writes into it and the three to its +x and +z, at any height.
        return new Box(0, MIN_CUBE - cube.getY(), 0, 1, MAX_CUBE - cube.getY(), 1);
    }

    @Override
    public void recreateStructures(ICube cube) {
    }

    @Override
    public void recreateStructures(Chunk column) {
        generator.recreateStructures(column, column.x, column.z);
    }

    @Override
    public List<Biome.SpawnListEntry> getPossibleCreatures(EnumCreatureType type, BlockPos pos) {
        return generator.getPossibleCreatures(type, pos);
    }

    @Nullable
    @Override
    public BlockPos getClosestStructure(String name, BlockPos pos, boolean findUnexplored) {
        return generator.getNearestStructurePos(world, name, pos, findUnexplored);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * A cube's blocks read straight from a finished column's codes: Cubic Chunks builds the cube by asking for each
     * block's state ({@code Cube(Chunk, int, CubePrimer)}), so nothing is copied or looked up by id.
     */
    static final class CodePrimer extends CubePrimer {
        private final char[] blocks;
        private final int base;
        private final IBlockState[] states;
        private final IBlockState air;

        CodePrimer(char[] blocks, int base, IBlockState[] states, IBlockState air) {
            super(EMPTY);
            this.blocks = blocks;
            this.base = base;
            this.states = states;
            this.air = air;
        }

        private static final char[] EMPTY = new char[4096];

        @Override
        public IBlockState getBlockState(int x, int y, int z) {
            IBlockState s = states[blocks[(x << 4 | z) * Pipeline.HEIGHT + base + y]];
            return s != null ? s : air;
        }

        @Override
        public void setBlockState(int x, int y, int z, IBlockState state) {
            throw new UnsupportedOperationException("a generated cube's blocks are the terrain's");
        }

        @Override
        public void reset() {
        }
    }
}
