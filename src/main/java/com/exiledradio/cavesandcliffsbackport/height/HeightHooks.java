package com.exiledradio.cavesandcliffsbackport.height;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * What the patched sites call that is more than arithmetic: deciding a world's height when it is constructed, the
 * extra section list in the save, the chunk packet's section mask, and the side list of block changes outside 0-255.
 */
public final class HeightHooks {

    /** The save's list of the sections below y 0 and above y 255; vanilla never reads it. */
    public static final String SECTIONS = CavesAndCliffsBackport.MOD_ID + ":Sections";
    /** The world's height, in the save's {@code data/}. */
    public static final String DATA_FILE = CavesAndCliffsBackport.MOD_ID + "_height.dat";

    /** Block changes outside y 0-255 waiting for their entry's next update (server thread only). */
    private static final Map<PlayerChunkMapEntry, Set<BlockPos>> OUTSIDE = new WeakHashMap<PlayerChunkMapEntry, Set<BlockPos>>();

    private HeightHooks() {
    }

    // ---------------------------------------------------------------------------------------------
    // Which worlds
    // ---------------------------------------------------------------------------------------------

    /**
     * End of {@code World}'s constructor, before the world has a chunk. A Caves & Cliffs overworld gets y -64..319;
     * every other world keeps vanilla's range. The server takes the height saved with the world when there is one
     * (writing it for a new world), so a world never changes height; the client goes by the world type the server
     * sent in its join and respawn packets, which only a Caves & Cliffs overworld has.
     */
    public static void worldConstructed(World world) {
        if (!HeightContent.allowed() || world.provider == null || world.provider.getDimension() != 0) {
            return;
        }
        WorldInfo info = world.getWorldInfo();
        if (info == null) {
            return;
        }
        boolean ours = info.getTerrainType() == HeightContent.worldType();
        if (world.isRemote) {
            if (ours) {
                extend(world);
            }
            return;
        }
        File dir = worldDirectory(world.getSaveHandler());
        if (dir == null) {
            if (ours) {
                extend(world);
            }
            return;
        }
        File file = new File(new File(dir, "data"), DATA_FILE);
        int min;
        int max;
        NBTTagCompound saved = read(file);
        if (saved != null && saved.hasKey("MinY") && saved.hasKey("MaxY")) {
            min = saved.getInteger("MinY");
            max = saved.getInteger("MaxY");
        } else {
            min = ours ? HeightLayout.EXTENDED_MIN_Y : 0;
            max = ours ? HeightLayout.EXTENDED_MAX_Y : 256;
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("MinY", min);
            tag.setInteger("MaxY", max);
            write(file, tag);
        }
        boolean extended = min == HeightLayout.EXTENDED_MIN_Y && max == HeightLayout.EXTENDED_MAX_Y;
        if (extended && ours) {
            extend(world);
            CavesAndCliffsBackport.LOGGER.info("Extended height: this world runs from y {} to {}.", min, max - 1);
        } else if (extended || ours) {
            // A world whose saved height and world type disagree (an edited level.dat): it keeps 0-255, since the
            // clients decide from the world type.
            CavesAndCliffsBackport.LOGGER.warn("Extended height: {} says y {}..{} but the world type is {}; the world keeps y 0-255.",
                    file, min, max - 1, info.getTerrainType().getName());
        }
    }

    private static void extend(World world) {
        ((HeightWorld) world).cavesandcliffsbackport$setHeight(HeightLayout.EXTENDED_MIN_Y, HeightLayout.EXTENDED_MAX_Y);
    }

    @Nullable
    private static File worldDirectory(@Nullable ISaveHandler handler) {
        try {
            return handler == null ? null : handler.getWorldDirectory();
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static NBTTagCompound read(File file) {
        if (!file.isFile()) {
            return null;
        }
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(file);
            try {
                return CompressedStreamTools.readCompressed(in);
            } finally {
                in.close();
            }
        } catch (Exception e) {
            CavesAndCliffsBackport.LOGGER.error("Extended height: could not read {}.", file, e);
            return null;
        }
    }

    private static void write(File file, NBTTagCompound tag) {
        try {
            file.getParentFile().mkdirs();
            java.io.FileOutputStream out = new java.io.FileOutputStream(file);
            try {
                CompressedStreamTools.writeCompressed(tag, out);
            } finally {
                out.close();
            }
        } catch (Exception e) {
            CavesAndCliffsBackport.LOGGER.error("Extended height: could not write {}.", file, e);
        }
    }

    /**
     * End of {@code WorldInfo(WorldSettings, String)}: a new save. With {@code height.defaultForNewWorlds} on, a new
     * world that would get the Default type gets the Caves & Cliffs type. The client's own copy ("MpServer") takes
     * whatever the server sent.
     */
    public static void newWorldInfo(WorldInfo info, String name) {
        if ("MpServer".equals(name) || !HeightContent.allowed() || !ModConfig.height.defaultForNewWorlds) {
            return;
        }
        if (info.getTerrainType() == WorldType.DEFAULT && HeightContent.worldType() != null) {
            info.setTerrainType(HeightContent.worldType());
            CavesAndCliffsBackport.LOGGER.info("Extended height: new world '{}' gets the Caves & Cliffs world type (height.defaultForNewWorlds).", name);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Saving
    // ---------------------------------------------------------------------------------------------

    /** {@code AnvilChunkLoader.writeChunkToNBT}: the sections vanilla's {@code Sections} list gets (y 0-255). */
    public static ExtendedBlockStorage[] vanillaSections(Chunk chunk) {
        ExtendedBlockStorage[] all = chunk.getBlockStorageArray();
        return all.length == HeightLayout.VANILLA_SECTIONS ? all : Arrays.copyOf(all, HeightLayout.VANILLA_SECTIONS);
    }

    /** After vanilla's {@code Sections}: the other sections, as vanilla writes a section but with a signed {@code Y}. */
    public static void writeSections(Chunk chunk, World world, NBTTagCompound level) {
        ExtendedBlockStorage[] all = chunk.getBlockStorageArray();
        if (all.length <= HeightLayout.VANILLA_SECTIONS) {
            return;
        }
        boolean sky = world.provider.hasSkyLight();
        NBTTagList list = new NBTTagList();
        for (int slot = HeightLayout.VANILLA_SECTIONS; slot < all.length; slot++) {
            ExtendedBlockStorage section = all[slot];
            if (section == Chunk.NULL_BLOCK_STORAGE) {
                continue;
            }
            NBTTagCompound tag = new NBTTagCompound();
            tag.setByte("Y", (byte) (section.getYLocation() >> 4));
            byte[] blocks = new byte[4096];
            NibbleArray data = new NibbleArray();
            NibbleArray add = section.getData().getDataForNBT(blocks, data);
            tag.setByteArray("Blocks", blocks);
            tag.setByteArray("Data", data.getData());
            if (add != null) {
                tag.setByteArray("Add", add.getData());
            }
            tag.setByteArray("BlockLight", section.getBlockLight().getData());
            tag.setByteArray("SkyLight", sky ? section.getSkyLight().getData() : new byte[section.getBlockLight().getData().length]);
            list.appendTag(tag);
        }
        level.setTag(SECTIONS, list);
    }

    /** After vanilla set the chunk's sections from {@code Sections}: puts back the ones from our list. */
    public static void readSections(Chunk chunk, NBTTagCompound level) {
        ExtendedBlockStorage[] all = chunk.getBlockStorageArray();
        if (all.length <= HeightLayout.VANILLA_SECTIONS || !level.hasKey(SECTIONS, 9)) {
            return;
        }
        boolean sky = chunk.getWorld().provider.hasSkyLight();
        NBTTagList list = level.getTagList(SECTIONS, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            int y = tag.getByte("Y");
            int slot = HeightLayout.sectionSlot(y);
            if (slot < HeightLayout.VANILLA_SECTIONS || slot >= all.length) {
                continue;
            }
            ExtendedBlockStorage section = new ExtendedBlockStorage(y << 4, sky);
            NibbleArray data = new NibbleArray(tag.getByteArray("Data"));
            NibbleArray add = tag.hasKey("Add", 7) ? new NibbleArray(tag.getByteArray("Add")) : null;
            section.getData().setDataFromNBT(tag.getByteArray("Blocks"), data, add);
            section.setBlockLight(new NibbleArray(tag.getByteArray("BlockLight")));
            if (sky) {
                section.setSkyLight(new NibbleArray(tag.getByteArray("SkyLight")));
            }
            section.recalculateRefCounts();
            all[slot] = section;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Sending
    // ---------------------------------------------------------------------------------------------

    /** {@code SPacketChunkData}: vanilla's "full chunk" filter means every section of an extended chunk. */
    public static int chunkPacketFilter(int filter, Chunk chunk) {
        return filter == 65535 ? fullChunkFilter(chunk) : filter;
    }

    public static int fullChunkFilter(Chunk chunk) {
        int sections = chunk.getBlockStorageArray().length;
        return sections <= HeightLayout.VANILLA_SECTIONS ? 65535 : (1 << sections) - 1;
    }

    /** {@code PlayerChunkMapEntry.blockChanged} for a y outside 0-255: kept here until the entry's next update. */
    public static void blockChangedOutside(PlayerChunkMapEntry entry, PlayerChunkMap map, boolean sent, int x, int y, int z) {
        if (!sent) {
            return;
        }
        Set<BlockPos> pending = OUTSIDE.get(entry);
        if (pending == null) {
            pending = new LinkedHashSet<BlockPos>();
            OUTSIDE.put(entry, pending);
        }
        ChunkPos pos = entry.getPos();
        pending.add(new BlockPos((pos.x << 4) + x, y, (pos.z << 4) + z));
        map.entryChanged(entry);
    }

    /** Start of {@code PlayerChunkMapEntry.update}: one block-change packet (and block entity) per pending change. */
    public static void flushOutside(PlayerChunkMapEntry entry) {
        if (OUTSIDE.isEmpty()) {
            return;
        }
        Set<BlockPos> pending = OUTSIDE.remove(entry);
        Chunk chunk = entry.getChunk();
        if (pending == null || chunk == null || !entry.isSentToPlayers()) {
            return;
        }
        World world = chunk.getWorld();
        for (BlockPos pos : pending) {
            entry.sendPacket(new SPacketBlockChange(world, pos));
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock().hasTileEntity(state)) {
                TileEntity te = world.getTileEntity(pos);
                SPacketUpdateTileEntity update = te == null ? null : te.getUpdatePacket();
                if (update != null) {
                    entry.sendPacket(update);
                }
            }
        }
        sentOutside += pending.size();
    }

    /** Block changes sent through the side list so far (read by the self-test). */
    public static volatile long sentOutside;

    // ---------------------------------------------------------------------------------------------
    // Celeritas
    // ---------------------------------------------------------------------------------------------

    /** Celeritas's {@code ClonedChunkSection.getChunkSection(chunk, sectionY)}, by our slots. */
    @Nullable
    public static ExtendedBlockStorage sectionAt(Chunk chunk, int sectionY) {
        ExtendedBlockStorage[] all = chunk.getBlockStorageArray();
        int slot = HeightLayout.sectionSlot(sectionY);
        return slot < all.length ? all[slot] : null;
    }
}
