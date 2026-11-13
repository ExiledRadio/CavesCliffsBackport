package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.height.Heights;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Editions;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Which worlds get 1.18 terrain, and what their biome provider and chunk generator share.
 *
 * <p>A Caves &amp; Cliffs overworld (extended height) created with this generator gets it for good: the choice is
 * saved in the world ({@code data/cavesandcliffsbackport_terrain.dat}). One created before it existed (its chunks
 * are 1.12 terrain with stone below 0) keeps that terrain, so no seam runs through it.
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class ModernTerrain {

    final OverworldNoise noise;
    final Climate climate;
    final BiomeMap map;

    private static final Map<World, ModernTerrain> WORLDS = new WeakHashMap<>();

    /** The newer versions' additions this world has ({@link Editions}), decided when it was made. */
    final int editions;

    private ModernTerrain(long seed, int editions) {
        this.editions = editions;
        this.noise = new OverworldNoise(seed, editions);
        this.climate = new Climate(editions);
        this.map = new BiomeMap();
    }

    /** The shared terrain objects of a server world that gets 1.18 terrain, or null. */
    @Nullable
    public static synchronized ModernTerrain of(World world) {
        if (world.isRemote || world.provider.getDimension() != 0 || !Heights.isExtended(world)) {
            return null;
        }
        ModernTerrain t = WORLDS.get(world);
        if (t == null) {
            int editions = decide(world);
            if (editions < 0) {
                return null;
            }
            t = new ModernTerrain(world.getSeed(), editions);
            WORLDS.put(world, t);
        }
        return t;
    }

    /** The 1.18 chunk generator for a world type's getChunkGenerator, or null for 1.12's. */
    @Nullable
    public static IChunkGenerator generator(World world) {
        ModernTerrain t = of(world);
        return t == null ? null : new ModernGenerator(world, t);
    }

    /** The 1.18 biome provider for a world type's getBiomeProvider, or null for 1.12's. */
    @Nullable
    public static net.minecraft.world.biome.BiomeProvider biomeProvider(World world) {
        ModernTerrain t = of(world);
        return t == null ? null : new ModernBiomeProvider(t.noise, t.climate, t.map);
    }

    /** True when this world's chunks come from the 1.18 generator: this mod's 1.17 world generators step aside there. */
    public static boolean owns(World world) {
        if (!(world instanceof WorldServer)) {
            return false;
        }
        ChunkProviderServer provider = ((WorldServer) world).getChunkProvider();
        IChunkGenerator generator = provider == null ? null : provider.chunkGenerator;
        return generator instanceof ModernGenerator;
    }

    /** Worker threads: the config, or cores less two (one for the server thread, one for the rest), 1 to 6. */
    static int threads() {
        int configured = ModConfig.height.terrainThreads;
        if (configured > 0) {
            return configured;
        }
        return Math.max(1, Math.min(6, Runtime.getRuntime().availableProcessors() - 2));
    }

    /** Chunks held ahead at most: about 200 KB each, a sixteenth of the heap, 256 to 2048. */
    static int cap() {
        long bytes = Runtime.getRuntime().maxMemory() / 16;
        return (int) Math.max(256, Math.min(2048, bytes / (210L * 1024L)));
    }

    // ---------------------------------------------------------------------------------------------
    // The choice, saved

    private static final String DATA_FILE = CavesAndCliffsBackport.MOD_ID + "_terrain.dat";

    /**
     * The newer versions' additions the installed backports have the blocks for: 1.19's mangrove swamp and deep dark
     * with a mod registering mud and the mangrove wood (Wild Update Backport), 1.20's cherry grove with one registering
     * the cherry wood and pink petals (Trails &amp; Tales Backport). Read when a world is made.
     */
    static int installedEditions() {
        int e = Editions.NONE;
        if (ModConfig.height.newerBiomes) {
            if (blocks("mud", "mangrove_log", "mangrove_leaves", "mangrove_roots", "muddy_mangrove_roots", "mangrove_propagule")) {
                e |= Editions.WILD;
            }
            if (blocks("cherry_log", "cherry_leaves", "pink_petals")) {
                e |= Editions.CHERRY;
            }
        }
        return e;
    }

    private static boolean blocks(String... names) {
        java.util.Set<String> want = new java.util.HashSet<>(java.util.Arrays.asList(names));
        for (net.minecraft.util.ResourceLocation id : net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.getKeys()) {
            want.remove(id.getPath());
        }
        return want.isEmpty();
    }

    /**
     * Reads the world's saved choice, or makes it (1.18 for a world with no chunks yet, with the newer biomes the
     * installed backports have blocks for) and saves it: the editions (1.18 terrain), or -1 for 1.12's. Runs in the
     * world's constructor, before its per-world storage can save, so the file is read and written directly. A world
     * keeps its biomes when a backport is added or removed later, so no seam runs through it.
     */
    private static int decide(World world) {
        File dir = world.getSaveHandler().getWorldDirectory();
        if (dir == null) {
            return installedEditions();
        }
        File file = new File(new File(dir, "data"), DATA_FILE);
        try {
            if (file.isFile()) {
                try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                    NBTTagCompound tag = CompressedStreamTools.readCompressed(in);
                    if (!"1.18".equals(tag.getString("Terrain"))) {
                        return -1;
                    }
                    // Worlds made before the newer biomes have no Biomes entry: 1.18.2's.
                    int e = tag.hasKey("Biomes") ? Editions.byName(tag.getString("Biomes")) : Editions.NONE;
                    if (e < 0) {
                        CavesAndCliffsBackport.LOGGER.warn("This world's 1.18 terrain has biomes from a newer version of "
                                + "this mod ({}); it gets 1.18.2's.", tag.getString("Biomes"));
                        e = Editions.NONE;
                    }
                    return e;
                }
            }
            String[] files = new File(dir, "region").list();
            boolean old = false;
            if (files != null) {
                for (String f : files) {
                    old |= f.endsWith(".mca");
                }
            }
            if (old) {
                CavesAndCliffsBackport.LOGGER.info("This Caves & Cliffs world was made before the 1.18 terrain: it keeps "
                        + "1.12's terrain (with stone below y 0), so no seam runs through it.");
            }
            int editions = old ? -1 : installedEditions();
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("Terrain", old ? "1.12" : "1.18");
            if (!old) {
                tag.setString("Biomes", Editions.name(editions));
                if (editions != Editions.NONE) {
                    CavesAndCliffsBackport.LOGGER.info("1.18 terrain with {}'s biomes (the installed backports have their "
                            + "blocks).", Editions.name(editions));
                }
            }
            file.getParentFile().mkdirs();
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                CompressedStreamTools.writeCompressed(tag, out);
            }
            return editions;
        } catch (java.io.IOException e) {
            CavesAndCliffsBackport.LOGGER.error("Could not read or save {}; this world gets the 1.18 terrain.", file, e);
            return installedEditions();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Events

    /** 1.18's biome id at a block of a world with 1.18 terrain (three dimensions), or -1 for any other world. */
    public static int biomeAt(World world, int x, int y, int z) {
        if (world.isRemote || !owns(world)) {
            return -1;
        }
        return ((ModernGenerator) ((WorldServer) world).getChunkProvider().chunkGenerator).biome3d(x, y, z);
    }

    /** The held chunks go to disk with the world, so a restart decorates the chunks beyond the explored area alike. */
    @SubscribeEvent
    public static void onWorldSave(WorldEvent.Save event) {
        World world = event.getWorld();
        if (!world.isRemote && owns(world)) {
            ((ModernGenerator) ((WorldServer) world).getChunkProvider().chunkGenerator).saveHeld();
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        World world = event.getWorld();
        if (!world.isRemote && owns(world)) {
            ((ModernGenerator) ((WorldServer) world).getChunkProvider().chunkGenerator).biomes3d.remove(
                    event.getChunk().x, event.getChunk().z);
        }
    }

    /** A chunk the generator just made is now in the world: its waterlogged blocks get their water. */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        World world = event.getWorld();
        if (!world.isRemote && owns(world)) {
            ((ModernGenerator) ((WorldServer) world).getChunkProvider().chunkGenerator).placeWater(event.getChunk());
        }
    }

    /** The spawn point: dry land near 0, 0 by the bare terrain, without generating chunks to look. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCreateSpawn(WorldEvent.CreateSpawnPosition event) {
        World world = event.getWorld();
        if (world.isRemote || event.isCanceled() || !owns(world)) {
            return;
        }
        BlockPos spawn = ((ModernGenerator) ((WorldServer) world).getChunkProvider().chunkGenerator).findSpawn();
        if (spawn != null) {
            world.getWorldInfo().setSpawn(spawn);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        World world = event.getWorld();
        if (!world.isRemote && owns(world)) {
            ((ModernGenerator) ((WorldServer) world).getChunkProvider().chunkGenerator).shutdown();
        }
        synchronized (ModernTerrain.class) {
            WORLDS.remove(world);
        }
    }
}
