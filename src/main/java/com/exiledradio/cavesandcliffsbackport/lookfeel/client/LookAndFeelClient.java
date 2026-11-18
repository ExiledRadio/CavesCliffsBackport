package com.exiledradio.cavesandcliffsbackport.lookfeel.client;

import com.exiledradio.backportcore.presentation.client.DebugText;
import com.exiledradio.backportcore.presentation.client.FogPipeline;
import com.exiledradio.cavesandcliffsbackport.lookfeel.LookAndFeel;
import com.exiledradio.cavesandcliffsbackport.lookfeel.LookHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Stream Lf's client side: the clouds at 192 (1.18), the fog farther out (1.18.1), Hide Lightning Flashes and the
 * autosave indicator (1.18), and the F3 Block and Chunk rows (1.18.2), each as one step of Backport Core's
 * pipelines, which run them only while their version is installed and in version order.
 */
@SideOnly(Side.CLIENT)
public final class LookAndFeelClient {

    /** For the client self-test: frames that drew the indicator, sky colors undone, F3 frames edited. */
    public static volatile int indicatorFrames;
    public static volatile int flashesHidden;
    public static volatile int debugEdits;

    /** True while the integrated server is inside a save; {@link #saves} counts the saves begun. */
    private static volatile boolean saving;
    private static volatile int saves;
    private int seenSaves;
    private float indicator;
    private float lastIndicator;

    private static boolean registered;

    private LookAndFeelClient() {
    }

    public static void init() {
        if (registered) {
            return;
        }
        registered = true;
        FogPipeline.add("1.18", "clouds at 192, lightning flashes", new FogPipeline.Step() {
            @Override
            public void clouds(FogPipeline.Clouds clouds) {
                clouds.height = LookHooks.cloudHeight(clouds.provider.getDimension(), clouds.height);
            }

            @Override
            public void sky(FogPipeline.Sky sky) {
                if (LookAndFeel.HIDE_LIGHTNING_FLASHES.isShown() && LookAndFeel.HIDE_LIGHTNING_FLASHES.get() && sky.world != null && sky.world.getLastLightningBolt() > 0) {
                    double[] rgb = {sky.red, sky.green, sky.blue};
                    LookHooks.undoLightningFlash(rgb, sky.world.getLastLightningBolt(), sky.partialTicks);
                    sky.red = rgb[0];
                    sky.green = rgb[1];
                    sky.blue = rgb[2];
                    flashesHidden++;
                }
            }
        });
        FogPipeline.add("1.18.1", "fog starts farther out", new FogPipeline.Step() {
            @Override
            public void distance(FogPipeline.Distance d) {
                // Forge's fog event fires only for 1.12.2's clear-air branch (not in water, lava, blindness or
                // powder snow); its thick-fog case (the nether's, a boss's) and the sky keep vanilla's, as 1.18.1's.
                if (d.fogMode == -1 || !Float.isNaN(d.start()) || thickFog(d.entity)) {
                    return;
                }
                d.set(LookHooks.terrainFogStart(d.far), LookHooks.terrainFogEnd(d.far));
            }
        });
        DebugText.add("1.18.2", "block and chunk rows", LookAndFeelClient::debugText);
        MinecraftForge.EVENT_BUS.register(new LookAndFeelClient());
        if ("lookfeel".equals(System.getProperty(com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport.MOD_ID + ".clienttest"))) {
            // A test build's client test (runClient -Pclienttest=lookfeel), reached by name: a release has no test classes.
            try {
                Class.forName("com.exiledradio.cavesandcliffsbackport.selftest.LookFeelClientSelfTest")
                        .getMethod("start", String.class).invoke(null, "lookfeel");
            } catch (ReflectiveOperationException e) {
                com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport.LOGGER.error(
                        "CLIENT SELFTEST DONE pass=0 fail=1: the test classes are not in this build.", e);
            }
        }
    }

    private static boolean thickFog(Entity entity) {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.world == null || entity == null || mc.world.provider.doesXZShowFog((int) entity.posX, (int) entity.posZ)
                || mc.ingameGUI.getBossOverlay().shouldCreateFog();
    }

    /** 1.18.2's {@code DebugScreenOverlay}: "Block: x y z [x y z in chunk]", "Chunk: x y z [x z in r.X.Z.mca]". */
    static void debugText(DebugText.Lines lines) {
        Entity e = lines.mc.getRenderViewEntity();
        if (e == null) {
            return;
        }
        BlockPos pos = new BlockPos(e.posX, e.getEntityBoundingBox().minY, e.posZ);
        int block = DebugText.Lines.find(lines.left, "Block: ");
        int chunk = DebugText.Lines.find(lines.left, "Chunk: ");
        if (block >= 0) {
            lines.left.set(block, LookHooks.blockRow(pos.getX(), pos.getY(), pos.getZ()));
        }
        if (chunk >= 0) {
            lines.left.set(chunk, LookHooks.chunkRow(pos.getX(), pos.getY(), pos.getZ()));
        }
        debugEdits++;
    }

    // ---- 1.18's autosave indicator ----

    /** Every save of a world on the integrated server (the autosave, pausing, /save-all), on the server thread. */
    @SubscribeEvent
    public void onSave(WorldEvent.Save event) {
        World world = event.getWorld();
        if (!world.isRemote && Minecraft.getMinecraft().isIntegratedServerRunning()) {
            saving = true;
            saves++;
        }
    }

    /** The save ran inside this server tick ({@code MinecraftServer.isCurrentlySaving} goes false after it). */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            saving = false;
        }
    }

    /**
     * 1.18's {@code Gui.tickAutosaveIndicator}. A save is shorter than a client tick, so a save begun since the
     * last client tick counts as one in progress, as 1.18's would have been seen by a tick that overlapped it.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // 1.18's Gui.tick(paused) ticks the indicator while paused too, so the pause menu's save shows.
        Minecraft mc = Minecraft.getMinecraft();
        int now = saves;
        boolean flag = mc.isIntegratedServerRunning() && (saving || now != this.seenSaves);
        this.seenSaves = now;
        this.lastIndicator = this.indicator;
        this.indicator = LookHooks.autosaveStep(this.indicator, flag);
    }

    /** 1.18's {@code Gui.renderSavingIndicator}: "Saving world", white, fading, 10 from the right and 15 from the bottom. */
    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.hideGUI || !LookAndFeel.AUTOSAVE_INDICATOR.isShown() || !LookAndFeel.AUTOSAVE_INDICATOR.get() || (this.indicator <= 0.0F && this.lastIndicator <= 0.0F)) {
            return;
        }
        int alpha = LookHooks.autosaveAlpha(this.lastIndicator, this.indicator, event.getPartialTicks());
        if (alpha <= 8) {
            return;
        }
        ScaledResolution res = event.getResolution();
        FontRenderer font = mc.fontRenderer;
        String text = I18n.format("menu.savingLevel");
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        font.drawStringWithShadow(text, res.getScaledWidth() - font.getStringWidth(text) - 10, res.getScaledHeight() - 15,
                0xFFFFFF | alpha << 24);
        GlStateManager.disableBlend();
        indicatorFrames++;
    }
}
