package com.exiledradio.cavesandcliffsbackport.lookfeel;

/**
 * The numbers of stream Lf's client steps, as plain functions (no client types), so the server self-test can check
 * them against 1.18.2's code.
 */
public final class LookHooks {

    /** 1.12.2's overworld cloud height ({@code WorldProvider.getCloudHeight}). */
    public static final float CLOUDS_1_12 = 128.0F;
    /** 1.18's ({@code DimensionSpecialEffects.OverworldEffects}, {@code CLOUD_LEVEL}). */
    public static final float CLOUDS_1_18 = 192.0F;

    private LookHooks() {
    }

    /**
     * 1.18's cloud height: 192 in the overworld (dimension 0) where the provider kept 1.12.2's 128. Another
     * dimension, or a provider (or an earlier step) that chose its own height, keeps it.
     */
    public static float cloudHeight(int dimension, float height) {
        return dimension == 0 && height == CLOUDS_1_12 ? CLOUDS_1_18 : height;
    }

    /**
     * 1.18.1's {@code FogRenderer.setupFog}, terrain fog in clear air: it starts {@code clamp(far / 10, 4, 64)}
     * blocks before the render distance (1.12.2's started at 75% of it). {@code far} is the render distance in
     * blocks; 1.18.1 passes at least 32.
     */
    public static float terrainFogStart(float far) {
        float f = Math.max(far, 32.0F);
        return f - Math.min(Math.max(f / 10.0F, 4.0F), 64.0F);
    }

    /** @return the end of 1.18.1's terrain fog: the render distance (at least 32) */
    public static float terrainFogEnd(float far) {
        return Math.max(far, 32.0F);
    }

    /**
     * Hide Lightning Flashes: undoes 1.12.2's {@code World.getSkyColorBody} lightning blend
     * ({@code c * (1 - f) + target * f}, {@code f = min(bolt - partialTicks, 1) * 0.45}), as 1.18's
     * {@code ClientLevel.getSkyColor} skips it. {@code rgb} is changed in place.
     */
    public static void undoLightningFlash(double[] rgb, int lastLightningBolt, float partialTicks) {
        if (lastLightningBolt <= 0) {
            return;
        }
        float f = (float) lastLightningBolt - partialTicks;
        if (f > 1.0F) {
            f = 1.0F;
        }
        f *= 0.45F;
        if (f <= 0.0F) {
            return;
        }
        double[] target = {0.8, 0.8, 1.0};
        for (int i = 0; i < 3; i++) {
            rgb[i] = (rgb[i] - target[i] * f) / (1.0 - f);
        }
    }

    /** 1.18.2's F3 Block row: {@code Block: x y z [x&15 y&15 z&15]}. */
    public static String blockRow(int x, int y, int z) {
        return String.format("Block: %d %d %d [%d %d %d]", x, y, z, x & 15, y & 15, z & 15);
    }

    /** 1.18.2's F3 Chunk row: {@code Chunk: cx cy cz [cx&31 cz&31 in r.rx.rz.mca]}. */
    public static String chunkRow(int x, int y, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        return String.format("Chunk: %d %d %d [%d %d in r.%d.%d.mca]", cx, y >> 4, cz, cx & 31, cz & 31, cx >> 5, cz >> 5);
    }

    /** 1.18's autosave indicator: {@code Mth.lerp(0.2, value, saving ? 1 : 0)} each client tick. */
    public static float autosaveStep(float value, boolean saving) {
        return value + 0.2F * ((saving ? 1.0F : 0.0F) - value);
    }

    /** @return the indicator's alpha, 0 to 255 ({@code Gui.renderSavingIndicator}); drawn only above 8 */
    public static int autosaveAlpha(float last, float value, float partialTicks) {
        float v = last + partialTicks * (value - last);
        return (int) Math.floor(255.0F * Math.min(Math.max(v, 0.0F), 1.0F));
    }
}
