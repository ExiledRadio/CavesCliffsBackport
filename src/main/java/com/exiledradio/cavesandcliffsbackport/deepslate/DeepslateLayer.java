package com.exiledradio.cavesandcliffsbackport.deepslate;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRotatedPillar;
import net.minecraft.block.BlockSilverfish;
import net.minecraft.block.BlockStone;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * The deepslate layer in new chunks: stone (granite, diorite and andesite too, as 1.18 has none in its
 * deepslate) below {@code layerFull} becomes deepslate, between it and
 * {@code layerTop} more and more of it does, the further down; every stone ore there becomes its
 * deepslate ore, infested stone infested deepslate, and tuff blobs sit in it.
 *
 * <h2>Why after population, and when</h2>
 * Replacing stone while the terrain is laid out would stop 1.12's caves and ravines (they carve only
 * stone, dirt and the like) and every ore generator (they replace only stone) below the layer. So the
 * swap waits until a chunk can receive no more population: chunk C is written by its own populator and
 * those of the chunks west, north and north-west of it, so C is swapped once all four have populated.
 * It runs as the last world generator; a chunk whose neighbors were unloaded at that moment is picked
 * up when they load again or by a sweep every ten seconds.
 *
 * <h2>Which chunks</h2>
 * Only chunks first generated with this on. Each carries a status byte in its saved data
 * ({@code cavesandcliffsbackport.deepslate}: 0 never, 1 pending, 2 done), so chunks of a world from
 * before the mod - which have no byte - are never touched. Worlds whose height already goes below 0
 * (another extended-height terrain) are skipped, as are the world types in {@code skipWorldTypes}.
 *
 * <p>It only swaps blocks inside the chunk's own storage, with no neighbor updates and no light change
 * (every swapped block is opaque both ways). A chunk already sent to players is resent.
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class DeepslateLayer implements IWorldGenerator {

    static final byte NEVER = 0, PENDING = 1, DONE = 2;
    private static final String KEY = "deepslate";

    /** What the server knows about each loaded chunk. Server thread only. */
    private static final Map<Chunk, State> STATES = new WeakHashMap<Chunk, State>();

    private static Map<IBlockState, IBlockState> swaps;
    private static int sweepTicks;

    static final class State {
        byte status;
        /** Every populator has run for this chunk itself (not its neighbors). */
        boolean populated;

        State(byte status, boolean populated) {
            this.status = status;
            this.populated = populated;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Tracking

    static boolean eligible(World world) {
        if (world.isRemote || !DeepslateContent.enabled() || !ModConfig.deepslate.generate
                || com.exiledradio.cavesandcliffsbackport.terrain.ModernTerrain.owns(world)
                // A superflat world's layers are exactly its preset's, whatever skipWorldTypes says.
                || com.exiledradio.backportcore.worldgen.FlatWorlds.isFlat(world)) {
            return false;
        }
        int dim = world.provider.getDimension();
        boolean dimOk = false;
        for (int d : ModConfig.deepslate.dimensions) {
            dimOk |= d == dim;
        }
        if (!dimOk || Arrays.asList(ModConfig.deepslate.skipWorldTypes).contains(world.getWorldType().getName())) {
            return false;
        }
        return com.exiledradio.backportcore.terrain.WorldHeights.minY(world) >= 0;
    }

    /** @return the status of a loaded chunk, for tests; -1 if untracked */
    public static int status(Chunk chunk) {
        State s = STATES.get(chunk);
        return s == null ? -1 : s.status;
    }

    @SubscribeEvent
    public static void dataLoad(ChunkDataEvent.Load event) {
        if (event.getWorld().isRemote) {
            return;
        }
        NBTTagCompound tag = event.getData().getCompoundTag(CavesAndCliffsBackport.MOD_ID);
        byte status = tag.hasKey(KEY) ? tag.getByte(KEY) : NEVER;
        STATES.put(event.getChunk(), new State(status, event.getChunk().isTerrainPopulated()));
    }

    @SubscribeEvent
    public static void dataSave(ChunkDataEvent.Save event) {
        State s = STATES.get(event.getChunk());
        if (s == null) {
            return;
        }
        NBTTagCompound root = event.getData();
        NBTTagCompound tag = root.getCompoundTag(CavesAndCliffsBackport.MOD_ID);
        tag.setByte(KEY, s.status);
        root.setTag(CavesAndCliffsBackport.MOD_ID, tag);
    }

    @SubscribeEvent
    public static void load(ChunkEvent.Load event) {
        World world = event.getWorld();
        if (world.isRemote) {
            return;
        }
        Chunk chunk = event.getChunk();
        State s = STATES.get(chunk);
        if (s == null) {
            // No saved data was read: the chunk was just generated.
            STATES.put(chunk, new State(eligible(world) ? PENDING : NEVER, false));
            return;
        }
        if (s.populated) {
            tryAround(world, chunk.x, chunk.z);
        }
    }

    /** Runs last among world generators, so chunk (x, z) has finished populating. */
    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator generator, IChunkProvider provider) {
        Chunk chunk = provider.getLoadedChunk(chunkX, chunkZ);
        if (chunk == null) {
            return;
        }
        State s = STATES.get(chunk);
        if (s == null) {
            s = new State(eligible(world) ? PENDING : NEVER, true);
            STATES.put(chunk, s);
        }
        s.populated = true;
        tryAround(world, chunkX, chunkZ);
    }

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote || event.world.provider.getDimension() != 0
                || ++sweepTicks < 200) {
            return;
        }
        sweepTicks = 0;
        for (World world : net.minecraftforge.common.DimensionManager.getWorlds()) {
            sweep(world);
        }
    }

    /** Tries every pending, populated, loaded chunk of a world. */
    public static void sweep(World world) {
        List<Chunk> waiting = new ArrayList<Chunk>();
        for (Map.Entry<Chunk, State> e : STATES.entrySet()) {
            if (e.getKey().getWorld() == world && e.getValue().status == PENDING && e.getValue().populated) {
                waiting.add(e.getKey());
            }
        }
        for (Chunk c : waiting) {
            tryConvert(world, c.x, c.z);
        }
    }

    /** Chunk (x, z) populates itself and its east, south and south-east neighbors. */
    private static void tryAround(World world, int x, int z) {
        tryConvert(world, x, z);
        tryConvert(world, x + 1, z);
        tryConvert(world, x, z + 1);
        tryConvert(world, x + 1, z + 1);
    }

    private static boolean populated(World world, int x, int z) {
        Chunk c = world.getChunkProvider().getLoadedChunk(x, z);
        if (c == null) {
            return false;
        }
        State s = STATES.get(c);
        return s != null ? s.populated : c.isTerrainPopulated();
    }

    private static void tryConvert(World world, int x, int z) {
        Chunk chunk = world.getChunkProvider().getLoadedChunk(x, z);
        if (chunk == null) {
            return;
        }
        State s = STATES.get(chunk);
        if (s == null || s.status != PENDING || !s.populated
                || !populated(world, x - 1, z) || !populated(world, x, z - 1) || !populated(world, x - 1, z - 1)) {
            return;
        }
        s.status = DONE;
        int mask = convert(world, chunk);
        chunk.markDirty();
        if (mask != 0 && world instanceof WorldServer) {
            PlayerChunkMapEntry entry = ((WorldServer) world).getPlayerChunkMap().getEntry(x, z);
            if (entry != null && entry.isSentToPlayers()) {
                entry.sendPacket(new SPacketChunkData(chunk, mask));
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The swap

    private static Map<IBlockState, IBlockState> swaps() {
        if (swaps == null) {
            Map<IBlockState, IBlockState> m = new HashMap<IBlockState, IBlockState>();
            IBlockState deep = DeepslateContent.deepslate.getDefaultState().withProperty(BlockRotatedPillar.AXIS, EnumFacing.Axis.Y);
            // 1.18's granite, diorite and andesite blobs replace stone only, never deepslate, so the layer
            // has none: every variant of 1.12's stone block becomes deepslate.
            for (BlockStone.EnumType t : BlockStone.EnumType.values()) {
                m.put(Blocks.STONE.getDefaultState().withProperty(BlockStone.VARIANT, t), deep);
            }
            m.put(Blocks.MONSTER_EGG.getDefaultState().withProperty(BlockSilverfish.VARIANT, BlockSilverfish.EnumType.STONE),
                    DeepslateContent.infestedDeepslate.getDefaultState().withProperty(BlockRotatedPillar.AXIS, EnumFacing.Axis.Y));
            for (Map.Entry<String, String> e : DeepslateContent.ORE_NAMES.entrySet()) {
                Block stone = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(e.getKey()));
                Block ours = DeepslateContent.BLOCKS.get(e.getValue());
                if (stone != null && ours != null) {
                    m.put(stone.getDefaultState(), ours.getDefaultState());
                }
            }
            Block lit = DeepslateContent.BLOCKS.get("deepslate_redstone_ore");
            m.put(Blocks.LIT_REDSTONE_ORE.getDefaultState(), lit.getDefaultState().withProperty(BlockDeepslateRedstoneOre.LIT, true));
            // Stream Cu's copper ore, looked up by name: present only when that stream is on.
            Block copper = Owners.ourBlock("copper_ore");
            Block deepCopper = Owners.ourBlock("deepslate_copper_ore");
            if (copper != null && deepCopper != null) {
                m.put(copper.getDefaultState(), deepCopper.getDefaultState());
            }
            swaps = m;
        }
        return swaps;
    }

    /**
     * Swaps one chunk's blocks. Deterministic for a seed and chunk.
     *
     * @return the bit mask of the storage sections changed
     */
    public static int convert(World world, Chunk chunk) {
        int top = Math.max(1, Math.min(ModConfig.deepslate.layerTop, 128));
        int full = Math.max(0, Math.min(ModConfig.deepslate.layerFull, top));
        Random r = new Random(world.getSeed() ^ (chunk.x * 341873128712L + chunk.z * 132897987541L) ^ 0x2D5EE9L);
        Map<IBlockState, IBlockState> m = swaps();
        ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();
        int mask = 0;
        for (int y = 0; y < top; y++) {
            ExtendedBlockStorage s = storage[y >> 4];
            if (s == Chunk.NULL_BLOCK_STORAGE) {
                y |= 15;
                continue;
            }
            int span = top - full;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    // Below layerFull always; above it, a chance falling linearly to 1/span at the top.
                    boolean take = y < full || r.nextInt(span) < top - y;
                    if (!take) {
                        continue;
                    }
                    IBlockState swap = m.get(s.get(x, y & 15, z));
                    if (swap != null) {
                        s.set(x, y & 15, z, swap);
                        mask |= 1 << (y >> 4);
                    }
                }
            }
        }
        if (ModConfig.deepslate.tuff && full > 2 && r.nextInt(2) == 0) {
            IBlockState tuff = DeepslateContent.BLOCKS.get("tuff").getDefaultState();
            int cx = 3 + r.nextInt(10), cz = 3 + r.nextInt(10), cy = 1 + r.nextInt(full - 1);
            int rx = 2 + r.nextInt(3), ry = 1 + r.nextInt(2), rz = 2 + r.nextInt(3);
            for (int x = Math.max(0, cx - rx); x <= Math.min(15, cx + rx); x++) {
                for (int y = Math.max(0, cy - ry); y <= Math.min(top - 1, cy + ry); y++) {
                    for (int z = Math.max(0, cz - rz); z <= Math.min(15, cz + rz); z++) {
                        double dx = (x - cx) / (rx + 0.5), dy = (y - cy) / (ry + 0.5), dz = (z - cz) / (rz + 0.5);
                        ExtendedBlockStorage s = storage[y >> 4];
                        if (dx * dx + dy * dy + dz * dz <= 1 && s != Chunk.NULL_BLOCK_STORAGE
                                && s.get(x, y & 15, z).getBlock() == DeepslateContent.deepslate) {
                            s.set(x, y & 15, z, tuff);
                            mask |= 1 << (y >> 4);
                        }
                    }
                }
            }
        }
        return mask;
    }
}
