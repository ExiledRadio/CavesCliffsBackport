package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.backportcore.structure.ScatteredPlacement;
import com.exiledradio.backportcore.structure.StartRule;
import com.exiledradio.backportcore.structure.StructureSource;
import com.exiledradio.backportcore.terrain.ColumnTerrain;
import com.exiledradio.backportcore.terrain.DecorationSource;
import com.exiledradio.backportcore.terrain.TerrainSource;
import com.exiledradio.backportcore.waterlog.Waterlogging;
import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.height.HeightLayout;
import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ChunkFiller;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Structures;
import net.minecraft.block.BlockFalling;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldEntitySpawner;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraftforge.common.DungeonHooks;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.event.terraingen.TerrainGen;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The chunk generator of a Caves &amp; Cliffs world: 1.18.2's overworld from y -64 to 319, from the terrain core.
 *
 * <ul>
 *   <li>{@link #generateChunk}: the {@link Pipeline} finishes the chunk (noise fill, biomes, surface and carvers on
 *       worker threads ahead of need; then the placed features of every chunk around it, in 1.18's step order), and
 *       its blocks go straight into the chunk's 24 sections ({@code height.HeightLayout} slots). The column biomes
 *       are 1.18's at each column's top block, as 1.12 biomes ({@link BiomeMap}). 1.12's structures get their
 *       starts from 1.18's structure sets ({@link ModernStructures}).</li>
 *   <li>{@link #populate}: what 1.12 still does after the terrain: the structures' pieces, and the animals of
 *       world generation. No 1.12 lakes, dungeons, ores, trees or snow: the 1.18 features did all that. Other
 *       mods' world generators run after it as in any world; this mod's own 1.17 ones step aside
 *       ({@link ModernTerrain#owns}).</li>
 * </ul>
 */
public final class ModernGenerator implements IChunkGenerator, TerrainSource, StructureSource, DecorationSource {

    /** How far around each asked-for chunk the workers fill ahead, in chunks. */
    static final int PREFETCH = 4;

    private final World world;
    private final ModernTerrain terrain;
    private final Pipeline pipeline;
    private final StateMap states = new StateMap();
    private final SectionWriter sections = new SectionWriter(this.states);
    private final boolean features;
    private final boolean sky;
    private final Random rand = new Random();
    private final ModernStructures structures;
    private final ModernStructures.Village village;
    private final ModernStructures.Temple temple;
    private final ModernStructures.Monument monument;
    private final ModernStructures.Mineshaft mineshaft;
    private final ModernStructures.Stronghold stronghold;
    private final ModernStructures.Mansion mansion;
    /** Heights before any chunk exists (structures, villages, the spawn), on the server thread. */
    private final ChunkFiller heights;
    private final char[] column = new char[OverworldNoise.HEIGHT];
    /** Waterlogged positions of generated chunks, placed once the chunk is in the world ({@link #placeWater}). */
    private final Map<Long, long[]> water = new HashMap<>();
    /** The structure pieces' terrain adaptation for the fills (null without structures). */
    private final StructureBeards beards;
    /** Held chunks across restarts (null for a world without a save directory). */
    private final HeldStore store;
    /** 1.18's biomes in three dimensions for loaded chunks: mob spawning, the axolotls' lush caves. */
    final ChunkBiomes biomes3d;

    /** Time spent writing finished chunks into 1.12 chunks, for the benchmark. */
    public long writeNanos;
    public long chunks;

    ModernGenerator(World world, ModernTerrain terrain) {
        this.world = world;
        this.terrain = terrain;
        this.features = world.getWorldInfo().isMapFeaturesEnabled();
        this.sky = world.provider.hasSkyLight();
        this.pipeline = new Pipeline(terrain.noise, terrain.climate, ModernTerrain.threads(), ModernTerrain.cap());
        this.states.resolveAll();
        // Dynamic Trees grows its own trees where it cancels vanilla's; the 1.18 trees step aside there. Asked on the
        // server thread, which finishes chunks.
        this.pipeline.treeGate(DynamicTreesGate.of(world));
        ColumnCache columns = new ColumnCache(terrain.noise);
        this.heights = new ChunkFiller(terrain.noise, columns);
        this.structures = new ModernStructures(new Structures(terrain.noise, columns,
                new BiomeSource(terrain.noise, columns, terrain.climate)));
        this.village = structures.new Village(this);
        this.temple = structures.new Temple(this);
        this.monument = structures.new Monument();
        this.mineshaft = structures.new Mineshaft();
        this.stronghold = structures.new Stronghold();
        this.mansion = structures.new Mansion(this);
        this.biomes3d = new ChunkBiomes(world, terrain);
        if (this.features) {
            this.beards = new StructureBeards(world, this.village, this.stronghold);
            this.pipeline.setBeards(this.beards);
        } else {
            this.beards = null;
        }
        java.io.File dir = world.getSaveHandler().getWorldDirectory();
        if (dir != null && world instanceof net.minecraft.world.WorldServer) {
            this.store = new HeldStore((net.minecraft.world.WorldServer) world, dir);
            this.store.loadState();
            this.pipeline.setStore(this.store);
        } else {
            this.store = null;
        }
        CavesAndCliffsBackport.LOGGER.info("1.18 terrain for {}: {} worker threads, up to {} chunks held ahead.",
                world.getWorldInfo().getWorldName(), ModernTerrain.threads(), ModernTerrain.cap());
    }

    Pipeline pipeline() {
        return pipeline;
    }

    /** The structure pieces' terrain adaptation, for the self-test's second pipeline (null without structures). */
    public Pipeline.BeardSource beards() {
        return beards;
    }

    /** Saves the held chunks decoration wrote into and the decoration state, for a restart (when the world saves). */
    public void saveHeld() {
        if (store != null) {
            int n = pipeline.saveHeld();
            store.saveState();
            if (n > 0) {
                CavesAndCliffsBackport.LOGGER.debug("Saved {} held chunks of {}.", n, world.getWorldInfo().getWorldName());
            }
        }
    }

    /** Waits for every held chunk handed to the store to be written (the self-test). */
    public void flushHeld() {
        if (store != null) {
            store.flush();
        }
    }

    void shutdown() {
        saveHeld();
        if (store != null) {
            store.close();
        }
        pipeline.shutdown();
    }

    // ---------------------------------------------------------------------------------------------
    // Generation

    @Override
    public Chunk generateChunk(int x, int z) {
        Pipeline.Proto p = pipeline.finish(x, z);
        long t = System.nanoTime();
        biomes3d.put(x, z, p.quarts.clone());
        java.util.function.BiConsumer<Long, char[]> hook = onFinished;
        if (hook != null) {
            hook.accept(ChunkPos.asLong(x, z), p.blocks);
        }
        Chunk chunk = new Chunk(this.world, x, z);
        this.rand.setSeed((long) x * 341873128712L + (long) z * 132897987541L);
        write(chunk, p);
        byte[] biomes = chunk.getBiomeArray();
        BiomeMap map = terrain.map;
        for (int c = 0; c < 256; c++) {
            // Pipeline columns are x * 16 + z; 1.12's biome array is z << 4 | x.
            biomes[(c & 15) << 4 | c >> 4] = map.id(p.surfaceBiomes[c]);
        }
        if (this.features) {
            this.mineshaft.generate(this.world, x, z, null);
            this.village.generate(this.world, x, z, null);
            this.stronghold.generate(this.world, x, z, null);
            this.temple.generate(this.world, x, z, null);
            this.monument.generate(this.world, x, z, null);
            this.mansion.generate(this.world, x, z, null);
        }
        chunk.generateSkylightMap();
        writeNanos += System.nanoTime() - t;
        chunks++;
        pipeline.prefetch(x, z, PREFETCH);
        return chunk;
    }

    /** The finished blocks into the chunk's sections; chests and spawners get their tile entities. */
    private void write(Chunk chunk, Pipeline.Proto p) {
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        char[] blocks = p.blocks;
        for (int slot = 0; slot < sections.length; slot++) {
            int base = HeightLayout.slotBase(slot);
            int at = base - Pipeline.MIN_Y;
            if (at >= 0 && at + 16 <= Pipeline.HEIGHT) {
                sections[slot] = this.sections.write(blocks, base, this.sky);
            }
        }
        // Blocks that need more than their state: water kept in them, a chest's loot, a spawner's mob.
        StateMap map = this.states;
        long[] wet = null;
        int wetCount = 0;
        int last = -1;
        int lastFlags = 0;
        for (int i = 0; i < blocks.length; i++) {
            int code = blocks[i];
            if (code == BlockCodes.AIR) {
                continue;
            }
            if (code != last) {
                last = code;
                lastFlags = map.flags(code);
            }
            if (lastFlags == 0 || lastFlags == StateMap.TILE) {
                continue;
            }
            int c = i / Pipeline.HEIGHT;
            BlockPos pos = new BlockPos((chunk.x << 4) + (c >> 4), Pipeline.MIN_Y + i % Pipeline.HEIGHT, (chunk.z << 4) + (c & 15));
            if ((lastFlags & StateMap.WATER) != 0) {
                if (wet == null) {
                    wet = new long[16];
                } else if (wetCount == wet.length) {
                    wet = java.util.Arrays.copyOf(wet, wetCount * 2);
                }
                wet[wetCount++] = pos.toLong();
            }
            if ((lastFlags & StateMap.CHEST) != 0) {
                TileEntityChest chest = new TileEntityChest();
                chest.setLootTable(LootTableList.CHESTS_SIMPLE_DUNGEON, this.rand.nextLong());
                chunk.addTileEntity(pos, chest);
            } else if ((lastFlags & StateMap.SPAWNER) != 0) {
                TileEntityMobSpawner spawner = new TileEntityMobSpawner();
                spawner.getSpawnerBaseLogic().setEntityId(DungeonHooks.getRandomDungeonMob(this.rand));
                chunk.addTileEntity(pos, spawner);
            } else if ((lastFlags & StateMap.NEST) != 0) {
                Nests.fill(this.world, chunk, pos, map.state(code));
            }
        }
        if (wet != null) {
            synchronized (this.water) {
                this.water.put(ChunkPos.asLong(chunk.x, chunk.z), java.util.Arrays.copyOf(wet, wetCount));
            }
        }
    }

    /**
     * Gives the waterlogged blocks of a chunk just generated their water (Backport Core's waterlogging; without
     * Fluidlogged API, nothing). Called when the chunk is loaded into the world, right after {@link #generateChunk}.
     */
    void placeWater(Chunk chunk) {
        long[] wet;
        synchronized (this.water) {
            wet = this.water.remove(ChunkPos.asLong(chunk.x, chunk.z));
        }
        if (wet == null || !Waterlogging.isAvailable()) {
            return;
        }
        // 2 | 16: no neighbor or observer updates. An observer update reads the six neighbors, and one on the chunk's
        // edge loads (and so generates) the next chunk, whose load lands back here: with Fluidlogged API installed
        // that chain ran until the stack overflowed while the spawn area was generated.
        for (long l : wet) {
            Waterlogging.setWater(this.world, BlockPos.fromLong(l), true, 2 | 16);
        }
    }

    @Override
    public void populate(int x, int z) {
        BlockFalling.fallInstantly = true;
        int i = x * 16;
        int j = z * 16;
        BlockPos blockpos = new BlockPos(i, 0, j);
        Biome biome = this.world.getBiome(blockpos.add(16, 0, 16));
        this.rand.setSeed(this.world.getSeed());
        long k = this.rand.nextLong() / 2L * 2L + 1L;
        long l = this.rand.nextLong() / 2L * 2L + 1L;
        this.rand.setSeed((long) x * k + (long) z * l ^ this.world.getSeed());
        boolean village = false;
        ChunkPos chunkpos = new ChunkPos(x, z);
        ForgeEventFactory.onChunkPopulate(true, this, this.world, this.rand, x, z, false);
        if (this.features) {
            this.mineshaft.generateStructure(this.world, this.rand, chunkpos);
            village = this.village.generateStructure(this.world, this.rand, chunkpos);
            this.stronghold.generateStructure(this.world, this.rand, chunkpos);
            this.temple.generateStructure(this.world, this.rand, chunkpos);
            this.monument.generateStructure(this.world, this.rand, chunkpos);
            this.mansion.generateStructure(this.world, this.rand, chunkpos);
        }
        if (TerrainGen.populate(this, this.world, this.rand, x, z, village, PopulateChunkEvent.Populate.EventType.ANIMALS)) {
            WorldEntitySpawner.performWorldGenSpawning(this.world, biome, i + 8, j + 8, 16, 16, this.rand);
        }
        ForgeEventFactory.onChunkPopulate(false, this, this.world, this.rand, x, z, village);
        BlockFalling.fallInstantly = false;
    }

    @Override
    public boolean generateStructures(Chunk chunkIn, int x, int z) {
        boolean flag = false;
        if (this.features && chunkIn.getInhabitedTime() < 3600L) {
            flag |= this.monument.generateStructure(this.world, this.rand, new ChunkPos(x, z));
        }
        return flag;
    }

    /**
     * The spawn list at a position: 1.18's biome there in three dimensions ({@link ChunkBiomes}), so lush caves and
     * dripstone caves under other biomes spawn what 1.18's do; swamp huts and monuments as 1.12's.
     */
    @Override
    public List<Biome.SpawnListEntry> getPossibleCreatures(EnumCreatureType creatureType, BlockPos pos) {
        if (this.features && creatureType == EnumCreatureType.MONSTER) {
            if (this.temple.isSwampHut(pos)) {
                return this.temple.getMonsters();
            }
            if (this.monument.isPositionInStructure(this.world, pos)) {
                return this.monument.getMonsters();
            }
        }
        return biomes3d.spawns(creatureType, pos.getX(), pos.getY(), pos.getZ());
    }

    /** 1.18's biome id at a block (three dimensions: the cave biomes under the surface ones). Server thread. */
    public int biome3d(int x, int y, int z) {
        return biomes3d.biome(x, y, z);
    }

    // ---------------------------------------------------------------------------------------------
    // Where the family's structures start (Backport Core's StructureSource)

    /**
     * 1.18.2's structure sets for the structures other backports build (Update Aquatic's shipwrecks, ocean ruins and
     * buried treasure, the Nether backport's ruined portals, Village &amp; Pillage's outposts): the terrain core's
     * placement, biome checks at the start's height and weighted choice, as for this generator's own structures.
     */
    @Nullable
    @Override
    public StartRule startRule(World world, String kind) {
        if (world != this.world || !this.features) {
            return null;
        }
        final int set;
        switch (kind) {
            case com.exiledradio.backportcore.structure.Structures.SHIPWRECK:
                set = Structures.SHIPWRECKS;
                break;
            case com.exiledradio.backportcore.structure.Structures.OCEAN_RUIN:
                set = Structures.OCEAN_RUINS;
                break;
            case com.exiledradio.backportcore.structure.Structures.BURIED_TREASURE:
                set = Structures.BURIED_TREASURES;
                break;
            case com.exiledradio.backportcore.structure.Structures.RUINED_PORTAL:
                set = Structures.RUINED_PORTALS;
                break;
            case com.exiledradio.backportcore.structure.Structures.PILLAGER_OUTPOST:
                set = Structures.PILLAGER_OUTPOSTS;
                break;
            default:
                return null;
        }
        final ScatteredPlacement grid = new ScatteredPlacement(Structures.spacing(set), Structures.separation(set),
                Structures.salt(set), Structures.triangular(set));
        return new StartRule() {
            @Override
            public ScatteredPlacement grid() {
                return grid;
            }

            @Nullable
            @Override
            public String startAt(int chunkX, int chunkZ) {
                int i = structures.startAt(set, chunkX, chunkZ);
                return i < 0 ? null : Structures.STRUCTURE_NAMES[set][i];
            }
        };
    }

    @Override
    public boolean isInsideStructure(World worldIn, String structureName, BlockPos pos) {
        if (!this.features) {
            return false;
        }
        switch (structureName) {
            case "Stronghold":
                return this.stronghold.isInsideStructure(pos);
            case "Mansion":
                return this.mansion.isInsideStructure(pos);
            case "Monument":
                return this.monument.isInsideStructure(pos);
            case "Village":
                return this.village.isInsideStructure(pos);
            case "Mineshaft":
                return this.mineshaft.isInsideStructure(pos);
            case "Temple":
                return this.temple.isInsideStructure(pos);
            default:
                return false;
        }
    }

    @Nullable
    @Override
    public BlockPos getNearestStructurePos(World worldIn, String structureName, BlockPos position, boolean findUnexplored) {
        if (!this.features) {
            return null;
        }
        switch (structureName) {
            case "Stronghold":
                return this.stronghold.getNearestStructurePos(worldIn, position, findUnexplored);
            case "Mansion":
                return this.mansion.getNearestStructurePos(worldIn, position, findUnexplored);
            case "Monument":
                return this.monument.getNearestStructurePos(worldIn, position, findUnexplored);
            case "Village":
                return this.village.getNearestStructurePos(worldIn, position, findUnexplored);
            case "Mineshaft":
                return this.mineshaft.getNearestStructurePos(worldIn, position, findUnexplored);
            case "Temple":
                return this.temple.getNearestStructurePos(worldIn, position, findUnexplored);
            default:
                return null;
        }
    }

    @Override
    public void recreateStructures(Chunk chunkIn, int x, int z) {
        if (this.features) {
            this.mineshaft.generate(this.world, x, z, null);
            this.village.generate(this.world, x, z, null);
            this.stronghold.generate(this.world, x, z, null);
            this.temple.generate(this.world, x, z, null);
            this.monument.generate(this.world, x, z, null);
            this.mansion.generate(this.world, x, z, null);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Heights before generation

    /**
     * 1.18's WORLD_SURFACE_WG of the bare noise terrain at block x, z (one above the top block, water included), as
     * 1.18's structures read it. Server thread.
     */
    public int surface(int x, int z) {
        synchronized (this.heights) {
            return this.heights.baseHeight(x, z, false);
        }
    }

    /** OCEAN_FLOOR_WG: one above the top block that is not water or air. */
    public int floor(int x, int z) {
        synchronized (this.heights) {
            return this.heights.baseHeight(x, z, true);
        }
    }

    /** Whether the top of the bare terrain at x, z is water. */
    public boolean wet(int x, int z) {
        synchronized (this.heights) {
            int h = this.heights.baseHeight(x, z, false);
            if (h <= OverworldNoise.MIN_Y) {
                return false;
            }
            this.heights.baseColumn(x, z, this.column);
            return BlockCodes.liquid(this.column[h - 1 - OverworldNoise.MIN_Y]);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // What this generator decorates itself (Backport Core's DecorationSource)

    private java.util.Set<String> placed;

    /**
     * Every placed feature the world's biomes list (1.18.2's, and the newer ones it has), and every block the core's
     * features put down: 1.12 hooks the
     * family's backports use to add them (bee nests on trees, say) step aside in these worlds.
     */
    @Override
    public boolean placesFeature(World world, String feature) {
        if (world != this.world) {
            return false;
        }
        java.util.Set<String> names = placed;
        if (names == null) {
            names = com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator.placedFeatureNames(terrain.editions);
            for (int c = 0; c < BlockCodes.count(); c++) {
                names.add(BlockCodes.name(c));
            }
            placed = names;
        }
        return names.contains(feature);
    }

    @Nullable
    @Override
    public ColumnTerrain columnTerrain(World world) {
        return new ColumnTerrain() {
            @Override
            public int height(int x, int z) {
                return surface(x, z);
            }

            @Override
            public boolean wet(int x, int z) {
                return ModernGenerator.this.wet(x, z);
            }

            @Override
            public int floor(int x, int z) {
                return ModernGenerator.this.floor(x, z);
            }
        };
    }

    /**
     * A spawn point near 0, 0 on dry land: the nearest chunk center, spiralling out, whose bare terrain is above
     * the sea and not under water. Null if none within 128 chunks (the world then uses 1.12's search).
     */
    @Nullable
    BlockPos findSpawn() {
        for (int r = 0; r <= 128; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int x = (dx << 4) + 8;
                    int z = (dz << 4) + 8;
                    int h = surface(x, z);
                    if (h > OverworldNoise.SEA_LEVEL + 1 && h == floor(x, z) && h < 200) {
                        return new BlockPos(x, h, z);
                    }
                }
            }
        }
        return null;
    }

    /** Counters for the benchmark: where the server thread's time per chunk went. */
    public String stats() {
        Pipeline p = this.pipeline;
        long n = Math.max(1, this.chunks);
        return String.format(java.util.Locale.ROOT,
                "chunks=%d fill_ms=%.3f(x%d, workers) decorate_ms=%.3f(x%d) wait_ms=%.3f write_ms=%.3f beard_ms=%.3f"
                        + " stolen=%d held=%d stored=%d restored=%d threads=%d",
                this.chunks, p.fillNanos.get() / 1e6 / Math.max(1, p.filled.get()), p.filled.get(),
                p.decorateNanos / 1e6 / Math.max(1, p.decorated), p.decorated, p.waitNanos / 1e6 / n,
                this.writeNanos / 1e6 / n, p.beardNanos / 1e6 / n, p.stolen, p.held(), p.stored, p.restored,
                ModernTerrain.threads());
    }

    /** For the self-test: the laid-out villages (or strongholds) whose 1.18 adapt_noise box reaches the chunk. */
    public List<net.minecraft.world.gen.structure.StructureStart> adaptingStartsNear(int cx, int cz, boolean strongholds) {
        List<net.minecraft.world.gen.structure.StructureStart> out = new java.util.ArrayList<>();
        if (strongholds) {
            stronghold.startsNear(world, cx, cz, out::add);
        } else {
            village.startsNear(world, cx, cz, out::add);
        }
        return out;
    }

    /** For the restart self-test: told each chunk's finished 1.18 blocks as the generator makes it. */
    public static volatile java.util.function.BiConsumer<Long, char[]> onFinished;

    /** For the self-test: how many chunks' 3D biomes are kept. */
    public int biomeChunks() {
        return biomes3d.size();
    }

    /** For the self-test: the pipeline's held chunk count. */
    public int held() {
        return pipeline.held();
    }
}
