package com.exiledradio.cavesandcliffsbackport.height;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.chunk.BlockStateContainer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Roughly Enough IDs and Just Enough IDs (both mod id {@code jeid}; JEID 1.0.3-1.0.4, REID 2.0-2.3 share these parts).
 *
 * <p><b>Blocks.</b> Their mixin on {@code BlockStateContainer.getDataForNBT} writes palette indices into
 * {@code Blocks}/{@code Data} and keeps the palette in the container, and their {@code AnvilChunkLoader} mixin saves it
 * as the section's {@code Palette} (and reads it back before {@code setDataFromNBT}) for vanilla's {@code Sections}
 * only. Our list of the sections below 0 and above 255 is written and read by {@code HeightHooks}, so it does the same
 * here, through their container interface ({@code INewBlockStateContainer}, resolved by name: no compile dependency).
 * Without this, every section outside 0-255 came back as palette indices read as block ids: air, stone and nulls.
 *
 * <p><b>Biomes.</b> They make a chunk's biomes an int array: {@code Chunk.getBiomeArray()} answers a new array of their
 * error biome each call and {@code setBiomeArray} does nothing, and after {@code IChunkGenerator.generateChunk} they
 * fill the int array from the biome provider. Our generator's surface biomes are put into the int array
 * ({@code INewChunk.setIntBiomeArray}) once the chunk is in the world, and our code that reads a chunk's biomes reads
 * the int array ({@link #biomeBytes(Chunk)}).
 *
 * <p>At start-up {@link #check()} resolves both interfaces and round-trips a section through our save code; if either
 * fails, extended height stays off (a log line says why), as with any patch it cannot apply.
 */
public final class IdsCompat {

    static final String MOD_ID = "jeid";

    private static final String[] CONTAINER = {"org.dimdev.jeid.ducks.INewBlockStateContainer", "org.dimdev.jeid.INewBlockStateContainer"};
    private static final String CHUNK = "org.dimdev.jeid.INewChunk";

    /** Sections kept aside per world directory this session (logged once each). */
    private static final Map<File, int[]> LOST = Collections.synchronizedMap(new WeakHashMap<File, int[]>());
    /** Biomes our generator made, for chunks not yet in the world (server thread). */
    private static final Map<Chunk, byte[]> GENERATED = new WeakHashMap<Chunk, byte[]>();

    /** REID/JEID's palette handled in our save code (resolved, and the round trip passed). */
    private static boolean present;
    /** REID/JEID's int biome array reachable (independent of extended height: the generator writes biomes anyway). */
    private static boolean biomes;
    /** Why REID/JEID's palette cannot be handled, or null. */
    @Nullable
    private static String refusal;
    @Nullable
    private static Method getPalette;
    @Nullable
    private static Method setPalette;
    /** REID's NEID-format {@code Add2} (not in JEID). */
    @Nullable
    private static Method setAdd2;
    @Nullable
    private static Method getBiomes;
    @Nullable
    private static Method setBiomes;

    private IdsCompat() {
    }

    /** Whether REID or JEID is installed and its palette is handled by our save code. */
    public static boolean present() {
        return present;
    }

    static String version() {
        ModContainer mod = Loader.instance().getIndexedModList().get(MOD_ID);
        return mod == null ? "?" : mod.getName() + " " + mod.getVersion();
    }

    /**
     * Pre-init, before extended height is decided: resolves REID/JEID's interfaces and round-trips a section through
     * our save code. Registers the biome hook when their int biome array is reachable.
     */
    static void init() {
        if (!Loader.isModLoaded(MOD_ID)) {
            return;
        }
        String why = resolveBiomes();
        if (why == null) {
            biomes = true;
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(IdsCompat.class);
        } else {
            CavesAndCliffsBackport.LOGGER.warn("{} is installed and {}; the Caves & Cliffs generator's surface biomes are "
                    + "left to it.", version(), why);
        }
        why = resolvePalette();
        if (why == null) {
            present = true;
            why = roundTrip();
        }
        if (why != null) {
            present = false;
            refusal = version() + " is installed and " + why + "; blocks below y 0 and above y 255 would be lost when "
                    + "chunks save (made for Just Enough IDs 1.0.3-1.0.4 and Roughly Enough IDs 2.0-2.3)";
            return;
        }
        CavesAndCliffsBackport.LOGGER.info("{}: sections below y 0 and above y 255 save with its block palette.", version());
    }

    /** Why extended height cannot run with the installed REID/JEID, or null (not installed, or handled). */
    @Nullable
    static String refusal() {
        return refusal;
    }

    @Nullable
    private static String resolvePalette() {
        ClassLoader loader = IdsCompat.class.getClassLoader();
        Class<?> container = null;
        for (String name : CONTAINER) {
            try {
                container = Class.forName(name, false, loader);
                break;
            } catch (Throwable ignored) {
                // The other name.
            }
        }
        if (container == null) {
            return "its block palette interface (INewBlockStateContainer) was not found";
        }
        if (!container.isAssignableFrom(BlockStateContainer.class)) {
            return "its block palette mixin is not on BlockStateContainer";
        }
        try {
            getPalette = container.getMethod("getTemporaryPalette");
            setPalette = container.getMethod("setTemporaryPalette", int[].class);
        } catch (Throwable t) {
            return "its block palette methods were not found (" + t + ")";
        }
        try {
            setAdd2 = container.getMethod("setLegacyAdd2", NibbleArray.class);
        } catch (Throwable ignored) {
            setAdd2 = null;
        }
        return null;
    }

    @Nullable
    private static String resolveBiomes() {
        try {
            Class<?> chunk = Class.forName(CHUNK, false, IdsCompat.class.getClassLoader());
            if (!chunk.isAssignableFrom(Chunk.class)) {
                return "its biome mixin is not on Chunk";
            }
            getBiomes = chunk.getMethod("getIntBiomeArray");
            setBiomes = chunk.getMethod("setIntBiomeArray", int[].class);
        } catch (Throwable t) {
            return "its biome interface (INewChunk) was not found (" + t + ")";
        }
        return null;
    }

    /** Our save code on a section of many states (more than 256, so the palette needs two bytes) and back. */
    @Nullable
    private static String roundTrip() {
        List<IBlockState> states = new ArrayList<IBlockState>();
        for (Block block : Block.REGISTRY) {
            for (IBlockState s : block.getBlockState().getValidStates()) {
                // Only states a save keeps as they are (not ones whose properties come from the neighbors).
                try {
                    if (block.getStateFromMeta(block.getMetaFromState(s)) == s) {
                        states.add(s);
                    }
                } catch (RuntimeException ignored) {
                    // A block whose meta mapping throws is left out.
                }
            }
        }
        if (states.size() < 2) {
            return "the block state registry is empty";
        }
        ExtendedBlockStorage section = new ExtendedBlockStorage(-64, true);
        for (int i = 0; i < 4096; i++) {
            IBlockState state = i % 7 == 0 ? Blocks.AIR.getDefaultState() : states.get((i * 31) % states.size());
            if (Block.BLOCK_STATE_IDS.get(state) < 0) {
                state = Blocks.STONE.getDefaultState();
            }
            section.set(i & 15, i >> 8 & 15, i >> 4 & 15, state);
        }
        try {
            NBTTagCompound tag = HeightHooks.writeSection(section, true);
            ExtendedBlockStorage back = HeightHooks.readSection(tag, -4, true);
            for (int i = 0; i < 4096; i++) {
                int x = i & 15, y = i >> 8 & 15, z = i >> 4 & 15;
                IBlockState expected = section.get(x, y, z);
                IBlockState got = back.get(x, y, z);
                if (expected != got) {
                    return "a section saved and read back came out different (" + expected + " became " + got + ")";
                }
            }
        } catch (Throwable t) {
            return "saving and reading a section failed (" + t + ")";
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Blocks
    // ---------------------------------------------------------------------------------------------

    /** After {@code getDataForNBT}: the palette it left in the container, as their own save code writes it. */
    static void writePalette(BlockStateContainer data, NBTTagCompound tag) {
        if (!present) {
            return;
        }
        try {
            int[] palette = (int[]) getPalette.invoke(data);
            if (palette != null) {
                tag.setIntArray("Palette", palette);
            }
        } catch (Throwable t) {
            throw new IllegalStateException("Roughly Enough IDs palette", t);
        }
    }

    /** Before {@code setDataFromNBT}: the section's palette (and REID's {@code Add2}), as their own read code sets them. */
    static void readPalette(BlockStateContainer data, NBTTagCompound tag) {
        if (!present) {
            return;
        }
        try {
            setPalette.invoke(data, (Object) (tag.hasKey("Palette", 11) ? tag.getIntArray("Palette") : null));
            if (setAdd2 != null) {
                setAdd2.invoke(data, tag.hasKey("Add2", 7) ? new NibbleArray(tag.getByteArray("Add2")) : null);
            }
        } catch (Throwable t) {
            throw new IllegalStateException("Roughly Enough IDs palette", t);
        }
    }

    /**
     * Whether a section of our list was written by Caves &amp; Cliffs 1.1.5 or older under REID/JEID: no palette, in a
     * chunk whose vanilla sections have one (they wrote it, and their {@code getDataForNBT} always makes one). Its
     * {@code Blocks} are palette indices whose palette was never saved.
     */
    static boolean paletteLost(NBTTagCompound level, NBTTagCompound section) {
        if (!present || section.hasKey("Palette", 11)) {
            return false;
        }
        NBTTagList vanilla = level.getTagList("Sections", 10);
        for (int i = 0; i < vanilla.tagCount(); i++) {
            if (vanilla.getCompoundTagAt(i).hasKey("Palette", 11)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Keeps the sections {@link #paletteLost} found, untouched, in {@code <world>/cavesandcliffsbackport_lost_sections/}
     * (the first copy of each chunk is never overwritten), so nothing that a later repair could use is destroyed.
     */
    static void keepLost(Chunk chunk, NBTTagList sections) {
        try {
            File world = chunk.getWorld().getSaveHandler().getWorldDirectory();
            String dim = chunk.getWorld().provider.getSaveFolder();
            File dir = new File(dim == null ? world : new File(world, dim), CavesAndCliffsBackport.MOD_ID + "_lost_sections");
            File file = new File(dir, "c." + chunk.x + "." + chunk.z + ".dat");
            if (!file.exists()) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger("x", chunk.x);
                tag.setInteger("z", chunk.z);
                tag.setTag(HeightHooks.SECTIONS, sections);
                HeightHooks.writeData(file, tag);
            }
            int[] count;
            synchronized (LOST) {
                count = LOST.get(dir);
                if (count == null) {
                    count = new int[1];
                    LOST.put(dir, count);
                }
            }
            if (count[0]++ == 0) {
                CavesAndCliffsBackport.LOGGER.warn("Extended height: chunk {}, {} has sections below y 0 or above y 255 "
                        + "that Caves & Cliffs 1.1.5 or older saved under {} without their block palette; their blocks "
                        + "cannot be read back, so they are left empty. Their data is kept in {} (one file per chunk; "
                        + "further chunks are not logged).", chunk.x, chunk.z, version(), dir);
            }
        } catch (Throwable t) {
            CavesAndCliffsBackport.LOGGER.error("Extended height: could not keep the unreadable sections of chunk {}, {}.",
                    chunk.x, chunk.z, t);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Biomes
    // ---------------------------------------------------------------------------------------------

    /**
     * A chunk's biomes as vanilla's byte array ({@code z << 4 | x}; 255 = not set): the chunk's own array, or with
     * REID/JEID a copy of its int array (ids above 255 wrap, as everything here that reads bytes did before).
     */
    public static byte[] biomeBytes(Chunk chunk) {
        if (!biomes) {
            return chunk.getBiomeArray();
        }
        int[] ids = intBiomes(chunk);
        byte[] out = new byte[ids.length];
        for (int i = 0; i < ids.length; i++) {
            out[i] = (byte) (ids[i] < 0 ? 255 : ids[i]);
        }
        return out;
    }

    /** A saved chunk's {@code Level.Biomes}: bytes as vanilla writes them, or REID/JEID's int array; null if absent. */
    @Nullable
    public static byte[] biomeBytes(NBTTagCompound level) {
        if (level.hasKey("Biomes", 7)) {
            return level.getByteArray("Biomes");
        }
        if (level.hasKey("Biomes", 11)) {
            int[] ids = level.getIntArray("Biomes");
            byte[] out = new byte[ids.length];
            for (int i = 0; i < ids.length; i++) {
                out[i] = (byte) (ids[i] < 0 ? 255 : ids[i]);
            }
            return out;
        }
        return null;
    }

    /** REID/JEID's int biome array of a chunk (the live array); with neither installed, the bytes as ints. */
    public static int[] intBiomes(Chunk chunk) {
        if (!biomes) {
            byte[] bytes = chunk.getBiomeArray();
            int[] out = new int[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                out[i] = bytes[i] & 255;
            }
            return out;
        }
        try {
            return (int[]) getBiomes.invoke(chunk);
        } catch (Throwable t) {
            throw new IllegalStateException("Roughly Enough IDs biomes", t);
        }
    }

    /**
     * Our generator's biomes for a chunk it just made ({@code generateChunk}). Vanilla keeps the bytes it wrote; with
     * REID/JEID they go into the int array now and again once the chunk is in the world, after their own
     * post-generation fill from the biome provider has run ({@link #onChunkLoad}).
     */
    public static void generated(Chunk chunk, byte[] ids) {
        if (!biomes) {
            return;
        }
        byte[] copy = ids.clone();
        setIntBiomes(chunk, copy);
        synchronized (GENERATED) {
            GENERATED.put(chunk, copy);
        }
    }

    private static void setIntBiomes(Chunk chunk, byte[] biomes) {
        int[] ids = new int[biomes.length];
        for (int i = 0; i < biomes.length; i++) {
            int id = biomes[i] & 255;
            ids[i] = id == 255 ? -1 : id;
        }
        try {
            setBiomes.invoke(chunk, (Object) ids);
        } catch (Throwable t) {
            throw new IllegalStateException("Roughly Enough IDs biomes", t);
        }
    }

    /** {@code ChunkProviderServer.provideChunk}'s {@code onLoad}: after their fill, puts our biomes back. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getWorld().isRemote) {
            return;
        }
        byte[] ids;
        synchronized (GENERATED) {
            if (GENERATED.isEmpty()) {
                return;
            }
            ids = GENERATED.remove(event.getChunk());
        }
        if (ids != null) {
            setIntBiomes(event.getChunk(), ids);
        }
    }
}
