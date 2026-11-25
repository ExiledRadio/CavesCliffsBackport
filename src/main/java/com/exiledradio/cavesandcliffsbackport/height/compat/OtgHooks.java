package com.exiledradio.cavesandcliffsbackport.height.compat;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.height.Heights;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Method;

/**
 * Open Terrain Generator in a world from y -64 to 319 (patch in {@code height.asm.ModCompatPatches.OtgMaterialPatch}).
 * OTG's {@code ForgeWorld.getMaterial} answers null for y 256 and up (outside its world), and its own callers do not
 * expect a null there: its snow and ice pass ({@code FrozenSurfaceHelper.freezeLiquid}) walks down from a column's
 * height map, so anything built above y 255 beside a chunk OTG populates crashed the server. Above 255 it now gets the
 * block that is there (air where the chunk is not loaded or above the world).
 */
public final class OtgHooks {

    private static Method ofState;
    private static boolean failed;

    private OtgHooks() {
    }

    /**
     * {@code ForgeWorld.getMaterial(x, y, z, ...)} for y &gt;= 256: OTG's material for the block there, as a
     * {@code LocalMaterialData}; null only if OTG's material class cannot be reached (then OTG's own answer stands).
     */
    public static Object above(World world, int x, int y, int z) {
        IBlockState state = Blocks.AIR.getDefaultState();
        BlockPos pos = new BlockPos(x, y, z);
        if (world != null && y < Heights.maxY(world) && world.isBlockLoaded(pos, false)) {
            state = world.getBlockState(pos);
        }
        try {
            if (ofState == null) {
                if (failed) {
                    return null;
                }
                ofState = Class.forName("com.pg85.otg.forge.materials.ForgeMaterialData")
                        .getMethod("ofMinecraftBlockState", IBlockState.class);
            }
            return ofState.invoke(null, state);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!failed) {
                failed = true;
                CavesAndCliffsBackport.LOGGER.error("OTG above y 255: could not reach OTG's ForgeMaterialData", e);
            }
            return null;
        }
    }
}
