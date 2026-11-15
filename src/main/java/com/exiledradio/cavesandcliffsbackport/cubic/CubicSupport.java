package com.exiledradio.cavesandcliffsbackport.cubic;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.Loader;

import javax.annotation.Nullable;

/**
 * Cubic Chunks support: with it installed the height coremod stays off (it refuses to run beside it), and a Caves
 * &amp; Cliffs world is a cubic world from y -64 to 319 whose cubes come from the 1.18 terrain ({@link CubicGenerator}).
 *
 * <p>This class names no Cubic Chunks type in a field or signature, so the rest of the mod can call it whether or not
 * Cubic Chunks is installed; everything that does lives in the other classes of this package, which load only when it
 * is.
 */
public final class CubicSupport {

    /** Whether Cubic Chunks is installed: its API on the class path (true from the coremod phase on). */
    public static final boolean PRESENT = CubicSupport.class.getClassLoader()
            .getResource("io/github/opencubicchunks/cubicchunks/api/world/ICubicWorld.class") != null;

    /** The world's range: 1.18.2's. Cubic Chunks' own heights are set to it for these worlds. */
    public static final int MIN_Y = -64;
    public static final int MAX_Y = 320;

    /** Mods that change the world's height their own way: no cubic Caves &amp; Cliffs world beside them. */
    private static final String[][] CONFLICTS = {
            {"cavesnotcliffs", "Caves Not Cliffs"},
            {"cavebiomesapi", "Cave Biomes API (Caves Not Cliffs)"},
            {"farmers_future_delight", "Farmer's Future Delight"},
    };

    private static boolean on;

    private CubicSupport() {
    }

    /**
     * Pre-init, when the height coremod is off and Cubic Chunks is installed: registers the cubic Caves &amp; Cliffs
     * world type and the events behind it. Null (and a log line) when Cubic Chunks' API is not the one this was built
     * for, or a mod with heights of its own is installed.
     */
    @Nullable
    public static WorldType preInit() {
        for (String[] mod : CONFLICTS) {
            if (Loader.isModLoaded(mod[0])) {
                CavesAndCliffsBackport.LOGGER.warn("Cubic Chunks is installed, but so is {}: no Caves & Cliffs world type.",
                        mod[1]);
                return null;
            }
        }
        try {
            WorldType type = CubicWorlds.register();
            on = true;
            CavesAndCliffsBackport.LOGGER.info("Cubic Chunks is installed: the height coremod stays off, and Caves & Cliffs "
                    + "worlds ('{}') are cubic, with 1.18's terrain from y {} to {}.", type.getName(), MIN_Y, MAX_Y - 1);
            return type;
        } catch (LinkageError | RuntimeException e) {
            CavesAndCliffsBackport.LOGGER.error("Cubic Chunks is installed, but not a version this mod knows (built against "
                    + "1.12.2-0.0.1301): no Caves & Cliffs world type.", e);
            return null;
        }
    }

    /** Whether a cubic Caves &amp; Cliffs world type is registered this session. */
    public static boolean on() {
        return on;
    }

    /**
     * Whether the world is the overworld of a Caves &amp; Cliffs world that is, or is about to be made, cubic. True
     * from the world's constructor on, before Cubic Chunks sets the world up (its biome provider and first chunk
     * generator are asked for first).
     */
    public static boolean willBeCubic(World world) {
        return on && world != null && !world.isRemote && world.provider != null && world.provider.getDimension() == 0
                && world.getWorldType() instanceof CubicWorldType && CubicWorlds.dimensionCubic(world);
    }

    /** {@code Heights.minY} in a world the coremod did not patch: Cubic Chunks' bottom in a cubic Caves &amp; Cliffs world. */
    public static int minY(World world) {
        return on && world.getWorldType() instanceof CubicWorldType ? CubicWorlds.minY(world) : 0;
    }

    /** {@code Heights.maxY} likewise. */
    public static int maxY(World world) {
        return on && world.getWorldType() instanceof CubicWorldType ? CubicWorlds.maxY(world) : 256;
    }
}
