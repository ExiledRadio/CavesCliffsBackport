package com.exiledradio.cavesandcliffsbackport.height;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import javax.annotation.Nullable;

/**
 * What the patched Alfheim 1.6 sites call ({@code asm/AlfheimPatches}): its lighting engine's position key and its
 * chunks' boundary flags, in the layout of {@link AlfheimKey}.
 *
 * <p>The key layout is one for the session, fixed the first time Alfheim's engine is used: wide when extended height
 * is on, else Alfheim's own (every method here then computes exactly what the code it replaces did).
 * {@link HeightContent} reads {@link #wide()} right after it decides, and refuses extended height if the layout was
 * already fixed narrow; so a wide key never meets a narrow one.
 */
public final class AlfheimHooks {

    private static final boolean WIDE = HeightContent.allowed();

    private AlfheimHooks() {
    }

    public static boolean wide() {
        return WIDE;
    }

    /** {@code LightingEngine.encodeWorldCoord(BlockPos)}. */
    public static long encode(BlockPos pos) {
        return AlfheimKey.encode(pos.getX(), pos.getY(), pos.getZ(), WIDE);
    }

    /** {@code LightingEngine.decodeWorldCoord(MutableBlockPos, long)}. */
    public static BlockPos.MutableBlockPos decode(BlockPos.MutableBlockPos pos, long key) {
        return pos.setPos(AlfheimKey.x(key, WIDE), AlfheimKey.y(key, WIDE), AlfheimKey.z(key, WIDE));
    }

    /** {@code LightingEngine.<clinit>}: the neighbor shifts' y shift. */
    public static int yShift() {
        return AlfheimKey.yShift(WIDE);
    }

    /** {@code LightingEngine.<clinit>}: the neighbor shifts' x shift. */
    public static int xShift() {
        return AlfheimKey.xShift(WIDE);
    }

    /** {@code LightingEngine.M_CHUNK}. */
    public static long chunkMask() {
        return AlfheimKey.chunkMask(WIDE);
    }

    /** {@code LightingEngine.fetchNeighborDataFromCursor}: {@code (key & Y_CHECK) != 0}. */
    public static boolean outside(long key, World world) {
        return AlfheimKey.outside(key, WIDE, Heights.minY(world), Heights.maxY(world));
    }

    /**
     * {@code LightingEngine.processLightUpdatesForTypeInner}: a queued position's chunk, or null (skipped, as an
     * unloaded chunk is) when the position is outside the world's height. Alfheim's own key garbles such a y into one
     * inside; a wide key keeps it, and the section read would throw.
     */
    @Nullable
    public static Chunk inWorld(@Nullable Chunk chunk, World world, BlockPos pos) {
        return chunk != null && Heights.inside(world, pos.getY()) ? chunk : null;
    }

    /** {@code ChunkMixin.alfheim$relightSkylightColumn}: {@code 1 << sectionY}. */
    public static int flagMask(int sectionY, World world) {
        return AlfheimKey.flagMask(sectionY, Heights.isExtended(world), Heights.minSection(world));
    }

    /** {@code ChunkMixin.alfheim$scheduleRelightChecksForBoundary}: {@code bit << 4}. */
    public static int flagBottom(int bit, World world) {
        return AlfheimKey.flagBottom(bit, Heights.isExtended(world), Heights.minY(world));
    }

    /** {@code ChunkMixin.alfheim$scheduleRelightChecksForBoundary}: {@code (bit << 4) + 15}. */
    public static int flagTop(int bit, World world) {
        return AlfheimKey.flagTop(bit, Heights.isExtended(world), Heights.minY(world));
    }
}
