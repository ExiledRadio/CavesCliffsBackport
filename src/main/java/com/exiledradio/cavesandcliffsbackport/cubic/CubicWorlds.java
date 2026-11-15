package com.exiledradio.cavesandcliffsbackport.cubic;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.terrain.FluidUpdates;
import io.github.opencubicchunks.cubicchunks.api.world.CubeEvent;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.api.world.ICubeProviderServer;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorldServer;
import io.github.opencubicchunks.cubicchunks.api.worldgen.ICubeGenerator;
import io.github.opencubicchunks.cubicchunks.core.CubicChunksConfig;
import io.github.opencubicchunks.cubicchunks.core.world.WorldSavedCubicChunksData;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nullable;

/**
 * What a cubic Caves &amp; Cliffs world needs from Cubic Chunks beyond its world type: its height limits, the cubes'
 * tile entities and water once they exist, and the columns' cubes all generated together. Loaded only with Cubic
 * Chunks installed (it names its classes everywhere).
 */
public final class CubicWorlds {

    /** Cubic Chunks' per-world settings ({@code data/cubicChunksData.dat}): it reads its height limits from them. */
    private static final String DATA = "cubicChunksData";

    private CubicWorlds() {
    }

    static WorldType register() {
        CubicWorldType type = new CubicWorldType();
        MinecraftForge.EVENT_BUS.register(new CubicWorlds.Events());
        // 1.18's fluid updates wait for the populations that write into a column; in a cubic world those are its
        // anchor cubes' (CubicGenerator#populate).
        FluidUpdates.populated = CubicWorlds::populated;
        return type;
    }

    /** Whether Cubic Chunks makes the overworld cubic (it is unless its config excludes dimension 0). */
    static boolean dimensionCubic(World world) {
        return !CubicChunksConfig.isDimensionExcluded(world.provider.getDimension());
    }

    static int minY(World world) {
        return world instanceof ICubicWorld && ((ICubicWorld) world).isCubicWorld() ? ((ICubicWorld) world).getMinHeight() : 0;
    }

    static int maxY(World world) {
        return world instanceof ICubicWorld && ((ICubicWorld) world).isCubicWorld() ? ((ICubicWorld) world).getMaxHeight() : 256;
    }

    /** The world's cube generator, when it is ours. */
    @Nullable
    static CubicGenerator generator(World world) {
        if (!(world instanceof ICubicWorldServer) || !((ICubicWorld) world).isCubicWorld()) {
            return null;
        }
        ICubeGenerator g;
        try {
            g = ((ICubicWorldServer) world).getCubeGenerator();
        } catch (RuntimeException e) {
            return null; // still being set up
        }
        return g instanceof CubicGenerator ? (CubicGenerator) g : null;
    }

    /**
     * {@code FluidUpdates}' "the populations that write into this column ran" in a cubic world: the column's anchor
     * cube was populated (loaded from disk if it is not loaded; never generated here). Null for a world that is not
     * ours, where vanilla's flag answers.
     */
    @Nullable
    private static Boolean populated(WorldServer world, Chunk column) {
        if (generator(world) == null) {
            return null;
        }
        ICube cube = ((ICubeProviderServer) ((ICubicWorld) world).getCubeCache()).getCube(column.x, CubicGenerator.ANCHOR,
                column.z, ICubeProviderServer.Requirement.LOAD);
        return cube != null && cube.isPopulated();
    }

    public static final class Events {

        /**
         * Before Cubic Chunks sets a new world up (its own handler, at normal priority, reads its settings or makes
         * them from its config's default limits, which are all of y): the settings of a new cubic Caves &amp; Cliffs
         * overworld get 1.18.2's limits, -64 to 320. A world that has them already keeps them.
         */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onAttachWorld(AttachCapabilitiesEvent<World> event) {
            World world = event.getObject();
            if (!(world instanceof WorldServer) || !CubicSupport.willBeCubic(world)) {
                return;
            }
            MapStorage storage = world.getPerWorldStorage();
            if (storage.getOrLoadData(WorldSavedCubicChunksData.class, DATA) != null) {
                return;
            }
            storage.setData(DATA, new WorldSavedCubicChunksData(DATA, true, CubicSupport.MIN_Y, CubicSupport.MAX_Y));
            CavesAndCliffsBackport.LOGGER.info("New cubic Caves & Cliffs world: Cubic Chunks' height limits set to y {} to {}.",
                    CubicSupport.MIN_Y, CubicSupport.MAX_Y - 1);
        }

        /** A cube our generator made is in the world: its tile entities and water. */
        @SubscribeEvent
        public void onCubeLoad(CubeEvent.Load event) {
            ICube cube = event.getCube();
            World world = (World) cube.getWorld();
            if (world.isRemote) {
                return;
            }
            CubicGenerator g = generator(world);
            if (g != null) {
                g.onCubeLoad(cube);
            }
        }

        /** Every column's cubes are made in the tick its column was: nothing is left to a later, different pipeline. */
        @SubscribeEvent
        public void onWorldTick(TickEvent.WorldTickEvent event) {
            if (event.phase == TickEvent.Phase.END && !event.world.isRemote) {
                CubicGenerator g = generator(event.world);
                if (g != null) {
                    g.drain();
                }
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onWorldSave(WorldEvent.Save event) {
            if (!event.getWorld().isRemote) {
                CubicGenerator g = generator(event.getWorld());
                if (g != null) {
                    g.drain();
                }
            }
        }
    }
}
