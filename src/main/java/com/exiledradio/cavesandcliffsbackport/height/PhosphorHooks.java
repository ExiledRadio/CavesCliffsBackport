package com.exiledradio.cavesandcliffsbackport.height;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import javax.annotation.Nullable;

/**
 * What the patched Phosphor sites call ({@code asm/PhosphorPatches}): its lighting engine's position key and its chunks'
 * boundary flags. Phosphor's key is the one Alfheim kept (z 26 bits, x 26, y 8, light 4; "outside 0-255" a carry into
 * the light bits), so the arithmetic is {@link AlfheimKey}'s: its narrow layout is Phosphor's own, its wide layout makes
 * room for y -64..319.
 *
 * <p>The key layout is one for the session, fixed the first time Phosphor's engine is used: wide when extended height
 * is on, else Phosphor's own (every method here then computes exactly what the code it replaces did).
 * {@link HeightContent} reads {@link #wide()} right after it decides, and refuses extended height if the layout was
 * already fixed narrow; so a wide key never meets a narrow one.
 */
public final class PhosphorHooks {

    private static final boolean WIDE = HeightContent.allowed();

    private PhosphorHooks() {
    }

    public static boolean wide() {
        return WIDE;
    }

    /** {@code LightingEngine.encodeWorldCoord(long, long, long)} (the {@code BlockPos} overload calls it). */
    public static long encode(long x, long y, long z) {
        return AlfheimKey.encode((int) x, (int) y, (int) z, WIDE);
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

    /** {@code LightingEngine.mChunk}. */
    public static long chunkMask() {
        return AlfheimKey.chunkMask(WIDE);
    }

    /** {@code LightingEngine.fetchNeighborDataFromCursor}: {@code (key & yCheck) != 0}. */
    public static boolean outside(long key, World world) {
        return AlfheimKey.outside(key, WIDE, Heights.minY(world), Heights.maxY(world));
    }

    /**
     * {@code LightingEngine.processLightUpdatesForTypeInner}: a queued position's chunk, or null (skipped, as an
     * unloaded chunk is) when the position is outside the world's height. Phosphor's {@code World.checkLightFor}
     * enqueues any position; its own key garbles such a y into one inside, a wide key keeps it, and the section read
     * would throw.
     */
    @Nullable
    public static Chunk inWorld(@Nullable Chunk chunk, World world, BlockPos pos) {
        return chunk != null && Heights.inside(world, pos.getY()) ? chunk : null;
    }

    /** {@code LightingHooks}: {@code 1 << sectionY}, a section's boundary flag bit. */
    public static int flagMask(int sectionY, World world) {
        return AlfheimKey.flagMask(sectionY, Heights.isExtended(world), Heights.minSection(world));
    }

    /** {@code LightingHooks.scheduleRelightChecksForBoundary}: {@code bit << 4}. */
    public static int flagBottom(int bit, World world) {
        return AlfheimKey.flagBottom(bit, Heights.isExtended(world), Heights.minY(world));
    }

    /** {@code LightingHooks.scheduleRelightChecksForBoundary}: {@code (bit << 4) + 15}. */
    public static int flagTop(int bit, World world) {
        return AlfheimKey.flagTop(bit, Heights.isExtended(world), Heights.minY(world));
    }
}
