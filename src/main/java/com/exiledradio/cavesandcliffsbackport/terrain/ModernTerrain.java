package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.height.Heights;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;
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

    private ModernTerrain(long seed) {
        this.noise = new OverworldNoise(seed);
        this.climate = new Climate();
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
            if (!decide(world)) {
                return null;
            }
            t = new ModernTerrain(world.getSeed());
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

    /** Chunks held ahead at most: about 200 KB each, an eighth of the heap, 512 to 4096. */
    static int cap() {
        long bytes = Runtime.getRuntime().maxMemory() / 8;
        return (int) Math.max(512, Math.min(4096, bytes / (210L * 1024L)));
    }

    // ---------------------------------------------------------------------------------------------
    // The choice, saved

    private static final String DATA_FILE = CavesAndCliffsBackport.MOD_ID + "_terrain.dat";

    /**
     * Reads the world's saved choice, or makes it (1.18 for a world with no chunks yet) and saves it. Runs in the
     * world's constructor, before its per-world storage can save, so the file is read and written directly.
     */
    private static boolean decide(World world) {
        File dir = world.getSaveHandler().getWorldDirectory();
        if (dir == null) {
            return true;
        }
        File file = new File(new File(dir, "data"), DATA_FILE);
        try {
            if (file.isFile()) {
                try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                    return "1.18".equals(CompressedStreamTools.readCompressed(in).getString("Terrain"));
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
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("Terrain", old ? "1.12" : "1.18");
            file.getParentFile().mkdirs();
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                CompressedStreamTools.writeCompressed(tag, out);
            }
            return !old;
        } catch (java.io.IOException e) {
            CavesAndCliffsBackport.LOGGER.error("Could not read or save {}; this world gets the 1.18 terrain.", file, e);
            return true;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Events

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
