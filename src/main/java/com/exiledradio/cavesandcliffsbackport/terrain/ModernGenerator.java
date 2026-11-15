package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.backportcore.structure.ScatteredPlacement;
import com.exiledradio.backportcore.structure.StartRule;
import com.exiledradio.backportcore.structure.StructureGenerators;
import com.exiledradio.backportcore.structure.StructureSource;
import com.exiledradio.backportcore.terrain.ColumnTerrain;
import com.exiledradio.backportcore.terrain.ModernGeneration;
import com.exiledradio.backportcore.terrain.DecorationSource;
import com.exiledradio.backportcore.terrain.TerrainSource;
import com.exiledradio.backportcore.waterlog.Waterlogging;
import com.exiledradio.backportcore.worldgen.FeatureSource;
import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.height.HeightLayout;
import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ChunkFiller;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline;
import com.exiledradio.cavesandcliffsbackport.terrain.core.SpawnSearch;
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
import net.minecraft.world.gen.structure.MapGenStructure;
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
public final class ModernGenerator implements IChunkGenerator, TerrainSource, StructureSource, FeatureSource, ModernGeneration, DecorationSource {

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
    /** 1.12's villages where 1.18 starts villages, or null where a backport builds them instead ({@link #villages}). */
    @Nullable
    private final ModernStructures.Village village;
    /**
     * The village generator in use: {@link #village}, or the one a backport put in its place through Backport Core's
     * {@code StructureGenerators} (Village &amp; Pillage's 1.14 villages, which ask {@link #startRule} where they start).
     */
    private final MapGenStructure villages;
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
    /** The aquifer's fluid-update flag, for the openings 1.12's population makes ({@link FluidUpdates}). */
    public com.exiledradio.cavesandcliffsbackport.terrain.core.FluidMarks fluidMarks() {
        return pipeline.fluidMarks();
    }

    /** The structure pieces' terrain adaptation for the fills (null without structures). */
    private final StructureBeards beards;
    /** Held chunks across restarts (null for a world without a save directory). */
    private final HeldStore store;
    /** 1.18's biomes in three dimensions for loaded chunks: mob spawning, the axolotls' lush caves. */
    final ChunkBiomes biomes3d;
    /** Other backports' newer placed features (Tricky Trials' spring plants), or null for none. */
    @Nullable
    private final ModernFeatures modern;

    /** Time spent writing finished chunks into 1.12 chunks, for the benchmark. */
    public long writeNanos;
    public long chunks;
    /** Whose code 1.18.2's own features run (terrain.core.Rules). */
    private final int rules;

    ModernGenerator(World world, ModernTerrain terrain) {
        this.world = world;
        this.terrain = terrain;
        this.features = world.getWorldInfo().isMapFeaturesEnabled();
        this.sky = world.provider.hasSkyLight();
        this.pipeline = new Pipeline(terrain.noise, terrain.climate, ModernTerrain.threads(), ModernTerrain.cap());
        this.rules = ModernTerrain.installedRules();
        this.pipeline.rules(this.rules);
        if (this.rules != com.exiledradio.cavesandcliffsbackport.terrain.core.Rules.V118) {
            CavesAndCliffsBackport.LOGGER.info("1.18 terrain: 1.18.2's features run {}'s code (the newest backport installed).",
                    com.exiledradio.cavesandcliffsbackport.terrain.core.Rules.name(this.rules));
        }
        this.states.resolveAll();
        // Dynamic Trees grows its own trees where it cancels vanilla's; the 1.18 trees step aside there. Asked on the
        // server thread, which finishes chunks.
        com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator.TreeGate trees = DynamicTreesGate.of(world);
        com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator.TreeGate gate = ModDecoration.gate(trees, ModDecoration.quarkStones());
        // Tricky Trials' 1.21.5 spring plants: its tree selectors, its cactus patches (see ModernFeatures).
        this.modern = ModernFeatures.of(trees);
        if (this.modern != null) {
            gate = ModDecoration.gate(gate, this.modern.cactusGate());
            List<String> skipped = this.pipeline.modernTrees(this.modern.selectors);
            CavesAndCliffsBackport.LOGGER.info("Tricky Trials is installed: 1.21.5's spring plants grow in the 1.18 terrain "
                    + "({} tree selectors{}).", this.modern.selectors.size() - skipped.size(),
                    skipped.isEmpty() ? "" : ", not matched: " + skipped);
        }
        this.pipeline.treeGate(gate);
        ColumnCache columns = new ColumnCache(terrain.noise);
        this.heights = new ChunkFiller(terrain.noise, columns);
        this.structures = new ModernStructures(new Structures(terrain.noise, columns,
                new BiomeSource(terrain.noise, columns, terrain.climate)));
        ModernStructures.Village own = structures.new Village(this);
        this.villages = StructureGenerators.replace(world, com.exiledradio.backportcore.structure.Structures.VILLAGE, own);
        this.village = this.villages == own ? own : null;
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
            this.store.open(this.pipeline);
        } else {
            this.store = null;
        }
        CavesAndCliffsBackport.LOGGER.info("1.18 terrain for {}: {} worker threads, up to {} chunks held ahead.",
                world.getWorldInfo().getWorldName(), ModernTerrain.threads(), ModernTerrain.cap());
    }

    public Pipeline pipeline() {
        return pipeline;
    }

    /** Other backports' newer placed features, or null for none. */
    @Nullable
    ModernFeatures modern() {
        return modern;
    }

    /** Whether other backports' newer placed features (Tricky Trials' spring plants) grow in this world. */
    public boolean hasModernFeatures() {
        return modern != null;
    }

    /**
     * Gives a second pipeline (the self-test's) this world's decoration in the core: the feature rules, and with other
     * backports' newer features the newer tree selectors and the cactus patches left to the backport.
     */
    public void sameDecoration(Pipeline other) {
        other.rules(rules);
        if (modern != null) {
            other.modernTrees(modern.selectors);
            other.treeGate(modern.cactusGate());
        }
    }

    /** Whose code 1.18.2's features run ({@code terrain.core.Rules}): the newest installed backport's. */
    public int rules() {
        return rules;
    }

    /** The newer versions' biomes this world has ({@code terrain.core.Editions} flags). */
    public int editions() {
        return terrain.editions;
    }

    /** The structure pieces' terrain adaptation, for the self-test's second pipeline (null without structures). */
    public Pipeline.BeardSource beards() {
        return beards;
    }

    /** Saves the held chunks decoration wrote into and the decoration state, for a restart (when the world saves). */
    public void saveHeld() {
        if (store != null) {
            store.recover(pipeline);
            int n = pipeline.saveHeld();
            store.saveState(pipeline.lsn());
            store.checkpoint();
            if (n > 0) {
                CavesAndCliffsBackport.LOGGER.debug("Saved {} held chunks of {}.", n, world.getWorldInfo().getWorldName());
            }
        }
    }

    /** The game is about to write a chunk of this world (ChunkDataEvent.Save): the decoration journal goes first. */
    void beforeChunkSave() {
        if (store != null) {
            store.beforeChunkSave();
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
        if (store != null) {
            store.recover(pipeline);
        }
        Pipeline.Proto p = pipeline.finish(x, z);
        long t = System.nanoTime();
        final Chunk chunk = new Chunk(this.world, x, z);
        writeSections(chunk, p);
        final long[][] wet = {null};
        final int[] wetCount = {0};
        column(chunk, p, new Specials() {
            @Override
            public void tile(BlockPos pos, net.minecraft.tileentity.TileEntity te) {
                chunk.addTileEntity(pos, te);
            }

            @Override
            public void water(long pos) {
                if (wet[0] == null) {
                    wet[0] = new long[16];
                } else if (wetCount[0] == wet[0].length) {
                    wet[0] = java.util.Arrays.copyOf(wet[0], wetCount[0] * 2);
                }
                wet[0][wetCount[0]++] = pos;
            }
        });
        if (wet[0] != null) {
            synchronized (this.water) {
                this.water.put(ChunkPos.asLong(chunk.x, chunk.z), java.util.Arrays.copyOf(wet[0], wetCount[0]));
            }
        }
        chunk.generateSkylightMap();
        writeNanos += System.nanoTime() - t;
        chunks++;
        pipeline.prefetch(x, z, PREFETCH);
        return chunk;
    }

    /**
     * Where a finished chunk's blocks that need more than their state go: a tile entity (a chest's loot, a spawner's
     * mob, a nest's bees) and water kept in a block. A Cubic Chunks world hands them to the cube that holds them.
     */
    public interface Specials {
        void tile(BlockPos pos, net.minecraft.tileentity.TileEntity te);

        void water(long pos);
    }

    /**
     * A column of a Cubic Chunks world ({@code cubic.CubicGenerator}): everything {@link #generateChunk} does but
     * writing the blocks, which the cubes take from the returned chunk's codes ({@link Pipeline.Proto#blocks}, final
     * once finished).
     */
    public Pipeline.Proto generateColumn(Chunk column, Specials specials) {
        int x = column.x;
        int z = column.z;
        Pipeline.Proto p = pipeline.finish(x, z);
        long t = System.nanoTime();
        column(column, p, specials);
        writeNanos += System.nanoTime() - t;
        chunks++;
        pipeline.prefetch(x, z, PREFETCH);
        return p;
    }

    /** The 1.12 state of every block code ({@link StateMap}), for the cubes. */
    public net.minecraft.block.state.IBlockState[] states() {
        net.minecraft.block.state.IBlockState[] out = new net.minecraft.block.state.IBlockState[BlockCodes.count()];
        for (int c = 0; c < out.length; c++) {
            out[c] = this.states.state(c);
        }
        return out;
    }

    /** The steps of a finished chunk after its blocks: the 3D biomes, its specials, updates, column biomes, starts. */
    private void column(Chunk chunk, Pipeline.Proto p, Specials specials) {
        int x = chunk.x;
        int z = chunk.z;
        biomes3d.put(x, z, p.quarts.clone());
        java.util.function.BiConsumer<Long, char[]> hook = onFinished;
        if (hook != null) {
            hook.accept(ChunkPos.asLong(x, z), p.blocks);
        }
        this.rand.setSeed((long) x * 341873128712L + (long) z * 132897987541L);
        specials(x, z, p.blocks, specials);
        // 1.18's fluid ticks and post-processing, run once the chunk's neighbors are in the world.
        FluidUpdates.put(chunk, this.modern == null ? p.updates : ModernFeatures.withVegetation(p.updates));
        byte[] biomes = chunk.getBiomeArray();
        BiomeMap map = terrain.map;
        for (int c = 0; c < 256; c++) {
            // Pipeline columns are x * 16 + z; 1.12's biome array is z << 4 | x.
            biomes[(c & 15) << 4 | c >> 4] = map.id(p.surfaceBiomes[c]);
        }
        if (this.features) {
            this.mineshaft.generate(this.world, x, z, null);
            this.villages.generate(this.world, x, z, null);
            this.stronghold.generate(this.world, x, z, null);
            this.temple.generate(this.world, x, z, null);
            this.monument.generate(this.world, x, z, null);
            this.mansion.generate(this.world, x, z, null);
        }
    }

    /** The finished blocks into the chunk's sections. */
    private void writeSections(Chunk chunk, Pipeline.Proto p) {
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        char[] blocks = p.blocks;
        for (int slot = 0; slot < sections.length; slot++) {
            int base = HeightLayout.slotBase(slot);
            int at = base - Pipeline.MIN_Y;
            if (at >= 0 && at + 16 <= Pipeline.HEIGHT) {
                sections[slot] = this.sections.write(blocks, base, this.sky);
            }
        }
    }

    /** Blocks that need more than their state: water kept in them, a chest's loot, a spawner's mob, a nest's bees. */
    private void specials(int cx, int cz, char[] blocks, Specials out) {
        StateMap map = this.states;
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
            BlockPos pos = new BlockPos((cx << 4) + (c >> 4), Pipeline.MIN_Y + i % Pipeline.HEIGHT, (cz << 4) + (c & 15));
            if ((lastFlags & StateMap.WATER) != 0) {
                out.water(pos.toLong());
            }
            if ((lastFlags & StateMap.CHEST) != 0) {
                TileEntityChest chest = new TileEntityChest();
                chest.setLootTable(LootTableList.CHESTS_SIMPLE_DUNGEON, this.rand.nextLong());
                out.tile(pos, chest);
            } else if ((lastFlags & StateMap.SPAWNER) != 0) {
                TileEntityMobSpawner spawner = new TileEntityMobSpawner();
                spawner.getSpawnerBaseLogic().setEntityId(DungeonHooks.getRandomDungeonMob(this.rand));
                out.tile(pos, spawner);
            } else if ((lastFlags & StateMap.NEST) != 0) {
                net.minecraft.tileentity.TileEntity nest = Nests.create(this.world, pos, map.state(code));
                if (nest != null) {
                    out.tile(pos, nest);
                }
            } else if ((lastFlags & StateMap.HEART) != 0) {
                net.minecraft.tileentity.TileEntity heart = naturalHeart(map.state(code));
                if (heart != null) {
                    out.tile(pos, heart);
                }
            }
        }
    }

    /**
     * A creaking heart a pale oak grew: its tile (Tricky Trials Backport's TileCreakingHeart, by name) marked
     * natural, as 1.21.4's CreakingHeartDecorator places it, so breaking it gives experience.
     */
    @javax.annotation.Nullable
    private net.minecraft.tileentity.TileEntity naturalHeart(net.minecraft.block.state.IBlockState state) {
        net.minecraft.tileentity.TileEntity tile = state.getBlock().createTileEntity(this.world, state);
        if (tile == null) {
            return null;
        }
        try {
            tile.getClass().getMethod("setNatural", boolean.class).invoke(tile, true);
        } catch (ReflectiveOperationException e) {
            // Another mod's heart without the method: an ordinary one.
        }
        return tile;
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
            village = this.villages.generateStructure(this.world, this.rand, chunkpos);
            this.stronghold.generateStructure(this.world, this.rand, chunkpos);
            this.temple.generateStructure(this.world, this.rand, chunkpos);
            this.monument.generateStructure(this.world, this.rand, chunkpos);
            this.mansion.generateStructure(this.world, this.rand, chunkpos);
        }
        // Other mods' generation in 1.12's dungeon and ore events (Quark's stones and underground biomes).
        ModDecoration.populate(this, this.world, x, z, village, this.modern);
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

    /** A loaded chunk's stored 1.18 biomes (4 x 4 quart columns of 96, not to be written), or null. Server thread. */
    byte[] quarts(int cx, int cz) {
        return biomes3d.quarts(cx, cz);
    }

    /** The world's zoom seed: the seed's SHA-256, which a client may know (never the seed). */
    long zoomSeed() {
        return com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeZoom.zoomSeed(terrain.noise.seed);
    }

    /** 1.18's biome id at a block (three dimensions: the cave biomes under the surface ones). Server thread. */
    public int biome3d(int x, int y, int z) {
        return biomes3d.biome(x, y, z);
    }

    // ---------------------------------------------------------------------------------------------
    // Backport Core's ModernGeneration: other backports' own generators of 1.18's features step aside here, and
    // their biome rules (Update Aquatic's ocean temperatures, fish, drowned) read 1.18's 3D biome.

    @Override
    public String generationVersion() {
        return "1.18.2";
    }

    @Override
    public String biomeId(int x, int y, int z) {
        return "minecraft:" + com.exiledradio.cavesandcliffsbackport.terrain.core.Biomes.name(biome3d(x, y, z));
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
            case com.exiledradio.backportcore.structure.Structures.VILLAGE:
                // For a backport's villages in place of 1.12's (Village & Pillage's): village_plains, _desert, ...
                set = Structures.VILLAGES;
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

    /**
     * Every 1.18.2 placed feature is the terrain core's ({@code Decorator}), with the family's blocks where their
     * backports are installed ({@link StateMap}): a backport's own generators for features 1.18 has step aside.
     */
    @Nullable
    @Override
    public String featureVersion(World world) {
        return world == this.world ? "1.18.2" : null;
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
                return this.villages.isInsideStructure(pos);
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
                return this.villages.getNearestStructurePos(worldIn, position, findUnexplored);
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
            this.villages.generate(this.world, x, z, null);
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
     * 1.18.2's world spawn ({@code MinecraftServer.setInitialSpawn}): the chunk holding the climate's spawn position
     * ({@link SpawnSearch}), at y 64 in its middle, then the first spawnable block of that chunk or of the chunks
     * spiralling out from it, 11 by 11 ({@code PlayerRespawnLogic.getSpawnPosInChunk}). Generates the chunks it
     * looks at, as vanilla does.
     */
    BlockPos findSpawn() {
        int[] climate = climateSpawn();
        int ccx = climate[0] >> 4;
        int ccz = climate[1] >> 4;
        BlockPos spawn = new BlockPos((ccx << 4) + 8, 64, (ccz << 4) + 8);
        int x = 0;
        int z = 0;
        int dx = 0;
        int dz = -1;
        for (int i = 0; i < 121; i++) {
            if (x >= -5 && x <= 5 && z >= -5 && z <= 5) {
                BlockPos pos = spawnInChunk(ccx + x, ccz + z);
                if (pos != null) {
                    return pos;
                }
            }
            if (x == z || x < 0 && x == -z || x > 0 && x == 1 - z) {
                int t = dx;
                dx = -dz;
                dz = t;
            }
            x += dx;
            z += dz;
        }
        return spawn;
    }

    /** The climate's spawn position, {x, z} ({@link SpawnSearch}); the self-test checks the spawn against it. */
    public int[] climateSpawn() {
        return new SpawnSearch(terrain.noise, new ColumnCache(terrain.noise)).find();
    }

    /** getSpawnPosInChunk: the first column, x outer and z inner, with a spawnable block. */
    @Nullable
    private BlockPos spawnInChunk(int cx, int cz) {
        Chunk chunk = this.world.getChunk(cx, cz);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                BlockPos pos = spawnInColumn(chunk, (cx << 4) + x, (cz << 4) + z);
                if (pos != null) {
                    return pos;
                }
            }
        }
        return null;
    }

    /**
     * getOverworldRespawnPos: none where the top block is a fluid; else the block above the highest one with a full
     * top face, searching down from two above the MOTION_BLOCKING height and stopping at a fluid.
     */
    @Nullable
    private BlockPos spawnInColumn(Chunk chunk, int x, int z) {
        int min = com.exiledradio.cavesandcliffsbackport.height.Heights.minY(this.world);
        int top = com.exiledradio.cavesandcliffsbackport.height.Heights.topY(this.world);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // MOTION_BLOCKING: the highest block that blocks motion or holds a fluid. The highest non-air block
        // (WORLD_SURFACE) is at or above it; when it is that block and the block does not block motion, it is a fluid.
        int motion = min - 1;
        boolean first = true;
        for (int y = top; y >= min; y--) {
            pos.setPos(x, y, z);
            net.minecraft.block.state.IBlockState state = chunk.getBlockState(pos);
            if (state.getMaterial() == net.minecraft.block.material.Material.AIR) {
                continue;
            }
            boolean blocks = state.getMaterial().blocksMovement();
            boolean fluid = fluid(state, pos);
            if (blocks || fluid) {
                if (first && !blocks) {
                    return null;
                }
                motion = y;
                break;
            }
            first = false;
        }
        if (motion < min) {
            return null;
        }
        for (int y = Math.min(motion + 2, top); y >= min; y--) {
            pos.setPos(x, y, z);
            net.minecraft.block.state.IBlockState state = chunk.getBlockState(pos);
            if (fluid(state, pos)) {
                return null;
            }
            if (state.getBlockFaceShape(this.world, pos, net.minecraft.util.EnumFacing.UP)
                    == net.minecraft.block.state.BlockFaceShape.SOLID
                    && state.getCollisionBoundingBox(this.world, pos) != null) {
                return new BlockPos(x, y + 1, z);
            }
        }
        return null;
    }

    private boolean fluid(net.minecraft.block.state.IBlockState state, BlockPos pos) {
        return state.getMaterial().isLiquid() || Waterlogging.hasWater(this.world, pos);
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

    /** For the self-test: whether the villages are 1.12's (false: a backport builds them in their place). */
    public boolean ownVillages() {
        return village != null;
    }

    /** For the self-test: the village generator in use (1.12's, or a backport's in its place). */
    public MapGenStructure villageGenerator() {
        return villages;
    }

    /**
     * For the self-test: the laid-out villages (or strongholds) whose 1.18 adapt_noise box reaches the chunk; no
     * villages where a backport builds them.
     */
    public List<net.minecraft.world.gen.structure.StructureStart> adaptingStartsNear(int cx, int cz, boolean strongholds) {
        List<net.minecraft.world.gen.structure.StructureStart> out = new java.util.ArrayList<>();
        if (strongholds) {
            stronghold.startsNear(world, cx, cz, out::add);
        } else if (village != null) {
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
