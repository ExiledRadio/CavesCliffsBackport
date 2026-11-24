package com.exiledradio.cavesandcliffsbackport.upgrade;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDirt;
import net.minecraft.block.BlockHugeMushroom;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockSand;
import net.minecraft.block.BlockStone;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.chunk.storage.RegionFileCache;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import javax.annotation.Nullable;
import java.io.DataInputStream;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The old chunks of an upgraded world as 1.18's blending and retrogen read them: their blocks from y 0 to 255, biomes
 * and whether 1.12 populated them. A loaded chunk is read in place; any other from the chunk loader's queue of chunks
 * waiting to be written or from its region file, without loading it into the world (no events, no capabilities).
 * Server thread only.
 */
public final class OldChunks {

    /** A block state as the sections store it: block id << 4 | meta. */
    public static int stateId(IBlockState state) {
        Block block = state.getBlock();
        return Block.getIdFromBlock(block) << 4 | block.getMetaFromState(state);
    }

    @Nullable
    public static IBlockState state(int id) {
        Block block = Block.getBlockById(id >> 4);
        try {
            return block.getStateFromMeta(id & 15);
        } catch (RuntimeException e) {
            return block.getDefaultState();
        }
    }

    private static final int AIR = 0;
    private static final int BEDROCK = Block.getIdFromBlock(Blocks.BEDROCK) << 4;

    /** One old chunk. */
    public static final class OldChunk {
        public final int cx;
        public final int cz;
        /** State ids, (x * 16 + z) * 256 + y. */
        final char[] ids = new char[65536];
        /** 1.12 biome ids, z << 4 | x. */
        final byte[] biomes;
        /** 1.12 populated it (TerrainPopulated). */
        public final boolean populated;

        OldChunk(int cx, int cz, byte[] biomes, boolean populated) {
            this.cx = cx;
            this.cz = cz;
            this.biomes = biomes;
            this.populated = populated;
        }

        public int stateId(int x, int y, int z) {
            return ids[(x * 16 + z) * 256 + y];
        }

        /** The state ids from y 0 to 255, (x * 16 + z) * 256 + y. Not to be written. */
        public char[] ids() {
            return ids;
        }

        @Nullable
        public Biome biome(int x, int z) {
            return Biome.getBiome(biomes[(z & 15) << 4 | (x & 15)] & 255);
        }

        /** BelowZeroRetrogen's missing bedrock: the columns (x * 16 + z) with air at y 0. */
        public boolean[] holes() {
            boolean[] out = new boolean[256];
            for (int c = 0; c < 256; c++) {
                out[c] = ids[c * 256] == AIR;
            }
            return out;
        }

        /**
         * ChunkHeightAndBiomeFix: an old chunk is upgraded below 0 only if it has bedrock somewhere at y 0 and is not
         * air all over y 0 (a chunk with no bedrock floor, of a flat or void world, stays as it is).
         */
        public boolean eligible() {
            boolean bedrock = false;
            boolean allAir = true;
            for (int c = 0; c < 256; c++) {
                int id = ids[c * 256];
                bedrock |= id == BEDROCK;
                allAir &= id == AIR;
            }
            return bedrock && !allAir;
        }
    }

    private final WorldServer world;
    private final LinkedHashMap<Long, OldChunk> cache = new LinkedHashMap<Long, OldChunk>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, OldChunk> eldest) {
            return size() > 96;
        }
    };
    @Nullable
    private Map<?, NBTTagCompound> queued;
    private boolean warned;

    public OldChunks(WorldServer world) {
        this.world = world;
    }

    private boolean looked;

    /** The chunk loader's queue of chunks waiting to be written (the provider exists only after the generator). */
    private void findQueue() {
        looked = true;
        ChunkProviderServer provider = world.getChunkProvider();
        IChunkLoader loader = provider == null ? null : provider.chunkLoader;
        if (loader instanceof AnvilChunkLoader) {
            try {
                this.queued = ReflectionHelper.getPrivateValue(AnvilChunkLoader.class, (AnvilChunkLoader) loader,
                        "chunksToSave", "field_75828_a");
            } catch (RuntimeException e) {
                this.queued = null;
            }
        }
    }

    private static long key(int cx, int cz) {
        return (long) cx << 32 | (cz & 0xFFFFFFFFL);
    }

    /** The old chunk at cx, cz, or null if the world has no such chunk. */
    @Nullable
    public OldChunk get(int cx, int cz) {
        long k = key(cx, cz);
        OldChunk c = cache.get(k);
        if (c != null) {
            return c;
        }
        if (!looked) {
            findQueue();
        }
        ChunkProviderServer provider = world.getChunkProvider();
        Chunk loaded = provider == null ? null : provider.getLoadedChunk(cx, cz);
        c = loaded != null ? read(loaded) : read(cx, cz);
        if (c != null) {
            cache.put(k, c);
        }
        return c;
    }

    /** A chunk as it loads (its blocks are what the world now holds). */
    public OldChunk refresh(Chunk chunk) {
        OldChunk c = read(chunk);
        cache.put(key(chunk.x, chunk.z), c);
        return c;
    }

    private static OldChunk read(Chunk chunk) {
        OldChunk c = new OldChunk(chunk.x, chunk.z, chunk.getBiomeArray().clone(), chunk.isTerrainPopulated());
        net.minecraft.world.chunk.storage.ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        for (int s = 0; s < 16 && s < sections.length; s++) {
            net.minecraft.world.chunk.storage.ExtendedBlockStorage section = sections[s];
            if (section == Chunk.NULL_BLOCK_STORAGE) {
                continue;
            }
            int last = -1;
            IBlockState lastState = null;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int at = (x * 16 + z) * 256 + (s << 4);
                    for (int y = 0; y < 16; y++) {
                        IBlockState state = section.get(x, y, z);
                        if (state != lastState) {
                            lastState = state;
                            last = stateId(state);
                        }
                        c.ids[at + y] = (char) last;
                    }
                }
            }
        }
        return c;
    }

    @Nullable
    private OldChunk read(int cx, int cz) {
        NBTTagCompound nbt = null;
        try {
            if (queued != null) {
                nbt = queued.get(new net.minecraft.util.math.ChunkPos(cx, cz));
            }
            if (nbt == null) {
                File dir = world.getChunkSaveLocation();
                DataInputStream in = RegionFileCache.getChunkInputStream(dir, cx, cz);
                if (in == null) {
                    return null;
                }
                try {
                    nbt = CompressedStreamTools.read(in);
                } finally {
                    in.close();
                }
            }
        } catch (Exception e) {
            if (!warned) {
                warned = true;
                CavesAndCliffsBackport.LOGGER.warn("World upgrade: could not read old chunk {}, {} ({}); it does not blend.",
                        cx, cz, e.toString());
            }
            return null;
        }
        NBTTagCompound level = nbt.getCompoundTag("Level");
        byte[] biomes = level.getByteArray("Biomes");
        if (biomes.length != 256) {
            biomes = new byte[256];
            Arrays.fill(biomes, (byte) Biome.getIdForBiome(net.minecraft.init.Biomes.PLAINS));
        }
        OldChunk c = new OldChunk(cx, cz, biomes, level.getBoolean("TerrainPopulated"));
        NBTTagList sections = level.getTagList("Sections", 10);
        for (int i = 0; i < sections.tagCount(); i++) {
            NBTTagCompound s = sections.getCompoundTagAt(i);
            int sy = s.getByte("Y");
            byte[] blocks = s.getByteArray("Blocks");
            byte[] dataBytes = s.getByteArray("Data");
            if (sy < 0 || sy > 15 || blocks.length != 4096 || dataBytes.length != 2048) {
                continue;
            }
            NibbleArray data = new NibbleArray(dataBytes);
            NibbleArray add = s.hasKey("Add", 7) ? new NibbleArray(s.getByteArray("Add")) : null;
            for (int j = 0; j < 4096; j++) {
                int id = (add == null ? 0 : add.getFromIndex(j)) << 8 | blocks[j] & 255;
                int x = j & 15;
                int z = j >> 4 & 15;
                int y = j >> 8;
                c.ids[(x * 16 + z) * 256 + (sy << 4) + y] = (char) (id << 4 | data.getFromIndex(j));
            }
        }
        return c;
    }

    // ---------------------------------------------------------------------------------------------
    // What BlendingData reads

    private static final byte KNOWN = 1;
    private static final byte GROUND = 2;
    private static final byte SURFACE = 4;
    private static final byte[] FLAGS = new byte[65536];

    private static int flags(int id) {
        int f = FLAGS[id];
        if (f == 0) {
            IBlockState state = state(id);
            f = KNOWN;
            if (state != null) {
                if (ground(state)) {
                    f |= GROUND;
                }
                if (surface(state)) {
                    f |= SURFACE;
                }
            }
            FLAGS[id] = (byte) f;
        }
        return f;
    }

    /** BlendingData.SURFACE_BLOCKS, as 1.12 has them. */
    private static boolean surface(IBlockState state) {
        Block b = state.getBlock();
        if (b == Blocks.GRAVEL || b == Blocks.GRASS || b == Blocks.MYCELIUM || b == Blocks.SNOW || b == Blocks.HARDENED_CLAY) {
            return true;
        }
        if (b == Blocks.STONE) {
            return state.getValue(BlockStone.VARIANT) == BlockStone.EnumType.STONE;
        }
        if (b == Blocks.DIRT) {
            // Dirt, coarse dirt and podzol: all three are on Mojang's list.
            return state.getValue(BlockDirt.VARIANT) != null;
        }
        return b == Blocks.SAND && state.getValue(BlockSand.VARIANT) != null;
    }

    /** BlendingData.isGround. */
    private static boolean ground(IBlockState state) {
        Block b = state.getBlock();
        if (state.getMaterial() == Material.AIR || b instanceof BlockLeaves || b instanceof BlockLog || b instanceof BlockHugeMushroom) {
            return false;
        }
        try {
            return state.getCollisionBoundingBox(EMPTY, BlockPos.ORIGIN) != Block.NULL_AABB;
        } catch (RuntimeException e) {
            return state.getMaterial().blocksMovement();
        }
    }

    /** An old chunk's blocks for {@code Blending.Data}, biomes as 1.18 ids. */
    public static com.exiledradio.cavesandcliffsbackport.terrain.core.Blending.OldBlocks blocks(final OldChunk c) {
        return new com.exiledradio.cavesandcliffsbackport.terrain.core.Blending.OldBlocks() {
            @Override
            public int surfaceHeight(int x, int z) {
                int at = (x * 16 + z) * 256;
                for (int y = 255; y >= 0; y--) {
                    if ((flags(c.ids[at + y]) & SURFACE) != 0) {
                        return y;
                    }
                }
                return 0;
            }

            @Override
            public boolean ground(int x, int y, int z) {
                return y >= 0 && y < 256 && (flags(c.ids[(x * 16 + z) * 256 + y]) & GROUND) != 0;
            }

            @Override
            public int biome(int x, int z) {
                return com.exiledradio.cavesandcliffsbackport.terrain.BiomeMap.upgraded(c.biome(x, z));
            }
        };
    }

    /** A world of nothing but air, for collision boxes. */
    private static final IBlockAccess EMPTY = new IBlockAccess() {
        @Nullable
        @Override
        public TileEntity getTileEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getCombinedLight(BlockPos pos, int lightValue) {
            return 0;
        }

        @Override
        public IBlockState getBlockState(BlockPos pos) {
            return Blocks.AIR.getDefaultState();
        }

        @Override
        public boolean isAirBlock(BlockPos pos) {
            return true;
        }

        @Override
        public Biome getBiome(BlockPos pos) {
            return net.minecraft.init.Biomes.PLAINS;
        }

        @Override
        public int getStrongPower(BlockPos pos, EnumFacing direction) {
            return 0;
        }

        @Override
        public WorldType getWorldType() {
            return WorldType.DEFAULT;
        }

        @Override
        public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
            return _default;
        }
    };
}
