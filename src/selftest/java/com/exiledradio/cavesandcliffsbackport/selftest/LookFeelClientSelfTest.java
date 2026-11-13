package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.client.FogPipeline;
import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.lookfeel.LookAndFeel;
import com.exiledradio.cavesandcliffsbackport.lookfeel.client.LookAndFeelClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Stream Lf's client test: the title screen (screenshot {@code screenshots/cc_lookfeel_title.png}, with 1.18's
 * panorama), the brightness default applied, then a superflat world: a save shows the autosave indicator
 * ({@code cc_lookfeel_saving.png}), the overworld's clouds are at 192, and the F3 rows are 1.18.2's
 * ({@code cc_lookfeel_f3.png}). The screenshots are for a person to look at; the game then quits through
 * {@code Minecraft.shutdown}.
 *
 * <pre>    ./gradlew runClient -Pselftest=lookfeel -Pclienttest=lookfeel</pre>
 */
public final class LookFeelClientSelfTest {

    private static final String WORLD = "cc_lookfeel_selftest";

    private final List<String> failures = new ArrayList<String>();
    private int passed;
    private int state;
    private int wait;
    private String shot;
    private int savesBefore;

    public static void start(String mode) {
        MinecraftForge.EVENT_BUS.register(new LookFeelClientSelfTest());
        CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST {} requested", mode);
    }

    @SubscribeEvent
    public void render(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END && this.shot != null) {
            Minecraft mc = Minecraft.getMinecraft();
            ScreenShotHelper.saveScreenshot(mc.gameDir, this.shot, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST screenshot {}", this.shot);
            this.shot = null;
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.state == 99) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (this.wait > 0) {
            this.wait--;
            return;
        }
        try {
            step(mc);
        } catch (Throwable t) {
            CavesAndCliffsBackport.LOGGER.error("CLIENT SELFTEST threw", t);
            fail("threw " + t);
            finish(mc);
        }
    }

    private void step(Minecraft mc) {
        switch (this.state) {
            case 0:
                if (!(mc.currentScreen instanceof GuiMainMenu)) {
                    return;
                }
                mc.gameSettings.pauseOnLostFocus = false;
                // Let the panorama's textures load and 1.13's first-start fade-in end.
                this.wait = 120;
                this.state = 1;
                return;
            case 1: {
                GameVersion newest = Presentation.newest();
                ok(newest.isAtLeast("1.18.2"), "the newest version is 1.18.2: " + newest);
                List<Presentation.Panorama> all = Presentation.panoramas();
                Presentation.Panorama last = all.get(all.size() - 1);
                eq(GameVersion.parse("1.18"), last.version, "the newest panorama");
                ok(mc.getTextureManager().getTexture(last.faces[0]) != null, "1.18's panorama face 0 is loaded (drawn on the title screen)");
                eq(0.5F, mc.gameSettings.gammaSetting, "brightness is 1.18's default, 50, once applied");
                this.shot = "cc_lookfeel_title.png";
                this.wait = 5;
                this.state = 2;
                return;
            }
            case 2:
                mc.getSaveLoader().deleteWorldDirectory(WORLD);
                mc.launchIntegratedServer(WORLD, WORLD, new WorldSettings(1718L, GameType.CREATIVE, false, false, WorldType.FLAT));
                this.state = 3;
                return;
            case 3:
                if (mc.world == null || mc.player == null || mc.getIntegratedServer() == null) {
                    return;
                }
                this.wait = 60;
                this.state = 4;
                return;
            case 4: {
                eq(192.0F, FogPipeline.cloudHeight(mc.world.provider, 128.0F), "the overworld's clouds are at 192");
                ok(LookAndFeel.AUTOSAVE_INDICATOR.get(), "Autosave Indicator is on");
                this.savesBefore = LookAndFeelClient.indicatorFrames;
                IntegratedServer server = mc.getIntegratedServer();
                server.addScheduledTask(() -> {
                    server.getPlayerList().saveAllPlayerData();
                    for (WorldServer w : server.worlds) {
                        try {
                            w.saveAllChunks(true, null);
                        } catch (Exception e) {
                            CavesAndCliffsBackport.LOGGER.error("save", e);
                        }
                    }
                });
                // The save takes one server tick, so the indicator peaks after one client tick and fades.
                this.wait = 1;
                this.state = 5;
                return;
            }
            case 5:
                ok(LookAndFeelClient.indicatorFrames > this.savesBefore, "a save shows \"Saving world\": " + LookAndFeelClient.indicatorFrames + " frames");
                this.shot = "cc_lookfeel_saving.png";
                mc.gameSettings.showDebugInfo = true;
                this.wait = 40;
                this.state = 6;
                return;
            case 6:
                ok(LookAndFeelClient.debugEdits > 0, "the F3 rows went through 1.18.2's step: " + LookAndFeelClient.debugEdits);
                this.shot = "cc_lookfeel_f3.png";
                this.wait = 5;
                this.state = 7;
                return;
            default:
                finish(mc);
        }
    }

    private void ok(boolean condition, String what) {
        if (condition) {
            this.passed++;
        } else {
            fail(what);
        }
    }

    private void eq(Object expected, Object actual, String what) {
        ok(expected == null ? actual == null : expected.equals(actual), what + ": expected " + expected + ", got " + actual);
    }

    private void fail(String what) {
        this.failures.add(what);
        CavesAndCliffsBackport.LOGGER.error("CLIENT SELFTEST FAIL {}", what);
    }

    private void finish(Minecraft mc) {
        this.state = 99;
        CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST DONE pass={} fail={}", this.passed, this.failures.size());
        try {
            PrintWriter out = new PrintWriter(new File(mc.gameDir, "selftest-client-report.txt"), "UTF-8");
            out.println("pass=" + this.passed + " fail=" + this.failures.size());
            for (String f : this.failures) {
                out.println(f);
            }
            out.close();
        } catch (Exception ignored) {
        }
        mc.shutdown();
    }
}
