package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.height.HeightContent;
import com.exiledradio.cavesandcliffsbackport.height.Heights;
import com.exiledradio.cavesandcliffsbackport.height.asm.PatchReport;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Stream H's client test (EXTENDED_HEIGHT section 5.4), through the integrated server: creates a single-player world
 * of the Caves & Cliffs type, waits for the chunks around the player, and checks the render frustum is 24 render
 * chunks high, that the client's chunks hold blocks at -64 and 319, and that render chunks below 0 and at the top
 * compile (the camera in a pocket at y -60, then above the world). Logged, not looked at; the game then quits.
 *
 * <pre>    ./gradlew runClient -Pselftest=heightclient</pre>
 * Started by name from {@code HeightContent} when the system property {@code cavesandcliffsbackport.clienttest} is
 * {@code height} (or {@code startup}: main menu only, for a modpack instance).
 * <p>
 * With OptiFine (section 1 of {@code docs/streams/optifine-alfheim.md}) it also checks: the drift report names the
 * OptiFine variants; {@code ChunkVisibility.getMaxChunkY} answers 320; the render chunks at y -64 and 304 are in
 * {@code renderInfos} (and, with a shader pack's shadow pass, in {@code renderInfosShadow}); the same with Render
 * Regions on and with shaders off. It saves F2-style screenshots (the world bottom, a cave at y -50, y 280) to
 * {@code screenshots/cc-height-*.png} for someone to look at, and logs the frame rate at spawn.
 * <p>
 * {@code -Dcavesandcliffsbackport.clienttest.world=default}, or a file {@code selftest-client-world-default} in the
 * game directory (a launcher that keeps its own copy of the JVM arguments): a Default world instead (frustum 16 high from y 0, the
 * frame rate at spawn), to compare with the same instance's {@code height.enabled=false}.
 */
public final class HeightClientSelfTest {

    private static final String WORLD = "cc_height_selftest";

    private final List<String> failures = new ArrayList<String>();
    private int passed;
    private int state;
    private int wait;
    private int px;
    private int pz;

    /** {@code height}: the whole test; {@code startup}: reach the main menu, log whether extended height is on, quit. */
    private final boolean startupOnly;
    /** A Default world instead of a Caves & Cliffs one. */
    private final boolean defaultWorld = "default".equals(System.getProperty(CavesAndCliffsBackport.MOD_ID + ".clienttest.world"))
            || new java.io.File("selftest-client-world-default").isFile();

    /** Frame-rate sampling: the state to go on to, and the samples (one a second). */
    private int fpsNext;
    private final List<Integer> fps = new ArrayList<Integer>();
    /** OptiFine's shader pack at the start, put back at the end. */
    private String shaderPack;

    private HeightClientSelfTest(boolean startupOnly) {
        this.startupOnly = startupOnly;
    }

    public static void start(String mode) {
        MinecraftForge.EVENT_BUS.register(new HeightClientSelfTest("startup".equals(mode)));
        CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST {} requested", mode);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        // A task on the integrated server first runs to its end (a busy pack's server can take many seconds to
        // get to it); the wait set with it counts from then.
        if (this.pending != null && !this.pending.get()) {
            if (++this.pendingTicks > 6000) {
                fail("a task on the integrated server did not run within 5 minutes");
                this.pending = null;
                finish(mc);
            }
            return;
        }
        this.pending = null;
        if (this.state >= 1 && this.state < 99 && mc.currentScreen instanceof net.minecraft.client.gui.GuiIngameMenu) {
            // The game paused itself (focus lost): the integrated server stops with it, and so would the test.
            CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST closing the pause menu");
            mc.displayGuiScreen(null);
        }
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

    private void step(Minecraft mc) throws Exception {
        switch (this.state) {
            case 0:
                // The title screen, or the one Custom Main Menu (RLCraft) puts in its place.
                if (!(mc.currentScreen instanceof GuiMainMenu) && (mc.currentScreen == null || mc.world != null
                        || !"lumien.custommainmenu.gui.GuiCustom".equals(mc.currentScreen.getClass().getName()))) {
                    return;
                }
                mc.gameSettings.pauseOnLostFocus = false;
                if (this.startupOnly) {
                    CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST startup: main menu reached; extended height {} ({} sites, refusal: {})",
                            HeightContent.allowed() ? "on" : "off", PatchReport.size(), HeightContent.refusal());
                    ok(true, "main menu reached");
                    finish(mc);
                    return;
                }
                if (this.defaultWorld) {
                    CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST Default world; extended height {} ({} sites, refusal: {})",
                            HeightContent.allowed() ? "on" : "off", PatchReport.size(), HeightContent.refusal());
                    if (HeightContent.allowed()) {
                        eq(new ArrayList<String>(), PatchReport.missed(), "client patch sites not found");
                        optifineReport();
                    }
                    mc.getSaveLoader().deleteWorldDirectory(WORLD + "_default");
                    mc.launchIntegratedServer(WORLD + "_default", WORLD + "_default",
                            new WorldSettings(1718L, GameType.CREATIVE, true, false, net.minecraft.world.WorldType.DEFAULT));
                    this.state = 1;
                    return;
                }
                ok(HeightContent.allowed(), "extended height is on (refusal: " + HeightContent.refusal() + ")");
                eq(new ArrayList<String>(), PatchReport.missed(), "client patch sites not found");
                optifineReport();
                if (!HeightContent.allowed()) {
                    // No Caves & Cliffs world type to make a world with.
                    finish(mc);
                    return;
                }
                mc.getSaveLoader().deleteWorldDirectory(WORLD);
                WorldSettings settings = new WorldSettings(1718L, GameType.CREATIVE, true, false, HeightContent.worldType());
                mc.launchIntegratedServer(WORLD, WORLD, settings);
                this.state = 1;
                return;
            case 1:
                if (mc.world == null || mc.player == null || mc.getIntegratedServer() == null) {
                    return;
                }
                mc.gameSettings.hideGUI = true;
                mc.gameSettings.pauseOnLostFocus = false;
                window(mc, 1600, 900);
                server(mc, world -> {
                    world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
                    world.setWorldTime(6000);
                    world.getWorldInfo().setCleanWeatherTime(1000000);
                    world.getWorldInfo().setRainTime(0);
                    world.getWorldInfo().setThunderTime(0);
                    world.getWorldInfo().setRaining(false);
                    world.getWorldInfo().setThundering(false);
                });
                // The frame rate at spawn first (camera at the surface), then the test proper.
                this.fpsNext = this.defaultWorld ? 20 : 2;
                this.wait = 200;
                this.state = 30;
                return;
            case 30:
                this.fps.add(Minecraft.getDebugFPS());
                if (this.fps.size() < 20) {
                    this.wait = 20;
                    return;
                }
                long sum = 0;
                for (int f : this.fps) {
                    sum += f;
                }
                CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST fps at spawn ({} world, extended height {}): mean {} over {} s, samples {}",
                        this.defaultWorld ? "Default" : "Caves & Cliffs", HeightContent.allowed() ? "on" : "off",
                        sum / this.fps.size(), this.fps.size(), this.fps);
                this.state = this.fpsNext;
                return;
            case 20:
                // Default world: vanilla's frustum (16 high from 0), and its blocks render.
                frustum(mc, 16, 0, 240);
                compiled(mc, mc.player.getPosition().down(2), "the render chunk under the player (Default world)");
                if (optifine()) {
                    CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST Default world: ChunkVisibility.getMaxChunkY = {}", maxChunkY(mc));
                }
                finish(mc);
                return;
            case 2: {
                ok(Heights.isExtended(mc.world), "the client world is extended");
                eq(-64, Heights.minY(mc.world), "client world min y");
                eq(320, Heights.maxY(mc.world), "client world max y");
                this.px = (int) Math.floor(mc.player.posX);
                this.pz = (int) Math.floor(mc.player.posZ);
                final int x = this.px;
                final int z = this.pz;
                server(mc, world -> {
                    // A sealed pocket: 1.18's lava aquifers sit just above the bottom.
                    for (int y = -63; y <= -56; y++) {
                        for (int dx = -2; dx <= 2; dx++) {
                            for (int dz = -2; dz <= 2; dz++) {
                                world.setBlockState(new BlockPos(x + dx, y, z + dz), Blocks.STONE.getDefaultState(), 3);
                            }
                        }
                    }
                    for (int y = -61; y <= -57; y++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                world.setBlockState(new BlockPos(x + dx, y, z + dz), Blocks.AIR.getDefaultState(), 3);
                            }
                        }
                    }
                    world.setBlockState(new BlockPos(x, -62, z), Blocks.DIAMOND_BLOCK.getDefaultState(), 3);
                    world.setBlockState(new BlockPos(x + 1, -59, z + 2), Blocks.GLOWSTONE.getDefaultState(), 3);
                    world.setBlockState(new BlockPos(x + 2, 319, z), Blocks.GLASS.getDefaultState(), 3);
                    world.setBlockState(new BlockPos(x + 3, 319, z), Blocks.GOLD_BLOCK.getDefaultState(), 3);
                    for (EntityPlayerMP p : world.getMinecraftServer().getPlayerList().getPlayers()) {
                        p.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.init.MobEffects.NIGHT_VISION, 1000000, 0, false, false));
                    }
                    teleport(world, x + 0.5, -61, z + 0.5, 200F, 35F);
                });
                this.wait = 200;
                this.state = 31;
                return;
            }
            case 31:
                // The client has the test blocks (a busy pack's server sends them late), or a minute has passed.
                if (++this.pendingTicks < 1200 && (mc.world.getBlockState(new BlockPos(this.px, -62, this.pz)).getBlock() != Blocks.DIAMOND_BLOCK
                        || mc.world.getBlockState(new BlockPos(this.px + 3, 319, this.pz)).getBlock() != Blocks.GOLD_BLOCK
                        || Math.abs(mc.player.posY + 61) > 1)) {
                    return;
                }
                this.wait = 100;
                this.state = 3;
                return;
            case 3: {
                frustum(mc);
                block(mc, this.px, -64, this.pz, Blocks.BEDROCK);
                block(mc, this.px, -62, this.pz, Blocks.DIAMOND_BLOCK);
                block(mc, this.px + 1, -59, this.pz + 2, Blocks.GLOWSTONE);
                block(mc, this.px + 2, 319, this.pz, Blocks.GLASS);
                block(mc, this.px + 3, 319, this.pz, Blocks.GOLD_BLOCK);
                // The client's light below 0 (Alfheim's engine, when installed, runs on the client too).
                eq(14, mc.world.getLightFor(net.minecraft.world.EnumSkyBlock.BLOCK, new BlockPos(this.px + 1, -59, this.pz + 1)),
                        "client block light at y -59 next to the glowstone");
                eq(11, mc.world.getLightFor(net.minecraft.world.EnumSkyBlock.BLOCK, new BlockPos(this.px, -60, this.pz)),
                        "client block light at y -60, 4 from the glowstone");
                compiled(mc, new BlockPos(this.px, -62, this.pz), "the render chunk at y -64 (camera at y -61)");
                if (optifine()) {
                    eq(320, maxChunkY(mc), "OptiFine: ChunkVisibility.getMaxChunkY");
                    rendered(mc, new BlockPos(this.px, -62, this.pz), "y -64", "shaders " + shaderName());
                }
                screenshot(mc, "cc-height-bottom");
                final int x = this.px;
                final int z = this.pz;
                server(mc, world -> teleport(world, x + 2.5, 322, z + 0.5, 0F, 60F));
                this.wait = 200;
                this.state = 4;
                return;
            }
            case 4: {
                ok(mc.player.posY > 319, "the camera is above the world (y " + mc.player.posY + ")");
                compiled(mc, new BlockPos(this.px + 3, 319, this.pz), "the render chunk at y 304 (camera above the world)");
                if (optifine()) {
                    rendered(mc, new BlockPos(this.px + 3, 319, this.pz), "y 304", "shaders " + shaderName());
                }
                final int x = this.px;
                final int z = this.pz;
                server(mc, world -> cave(world, x, z));
                this.wait = 200;
                this.state = 5;
                return;
            }
            case 5: {
                screenshot(mc, "cc-height-y-50");
                final int x = this.px;
                final int z = this.pz;
                server(mc, world -> teleport(world, x + 0.5, 280, z + 0.5, 30F, 20F));
                this.wait = 300;
                this.state = 6;
                return;
            }
            case 6: {
                if (this.deep == null) {
                    screenshot(mc, "cc-height-y280");
                    this.state = 50;
                    return;
                }
                if (!optifine()) {
                    finish(mc);
                    return;
                }
                // Where blocks are drawn, by pixels, with Render Regions on and shaders off (a plain sky and plain
                // gold): gold walls at y -40 and y 300, each seen from in front, and the sky over the low one.
                renderRegions(mc, true);
                this.regionShaders = shaderName();
                shaders(mc, "OFF");
                final int x = this.px + 60;
                final int z = this.pz;
                server(mc, world -> {
                    goldRoom(world, x, -40, z, true);
                    goldRoom(world, x, 300, z - 300, false);
                    teleport(world, x + 0.5, -40, z + 0.5, 0F, 0F);
                });
                this.wait = 300;
                this.state = 40;
                return;
            }
            case 50: {
                // Torch light below 0 (HeightSelfTest.deepTorches): the site in one tick (sent as whole sections),
                // the torches in a later one (single block changes: the client lights them itself).
                final int bx = (this.px & ~15) + 48;
                final int bz = (this.pz & ~15) + 16;
                server(mc, world -> {
                    HeightSelfTest.deepTorchSite(world, bx, bz);
                    teleport(world, bx - 8 + 0.5, -20, bz + 0.5, 270F, 0F);
                });
                this.wait = 200;
                this.state = 51;
                return;
            }
            case 51: {
                final int bx = (this.px & ~15) + 48;
                final int bz = (this.pz & ~15) + 16;
                server(mc, world -> {
                    Check check = new Check(world);
                    try {
                        this.deep = HeightSelfTest.deepTorchLights(check, world, bx, bz);
                    } catch (Exception e) {
                        check.fail("deep torches threw " + e);
                    }
                    this.serverFailures.addAll(check.failures());
                });
                this.wait = 100;
                this.state = 52;
                return;
            }
            case 52: {
                for (String f : this.serverFailures) {
                    fail("server: " + f);
                }
                this.serverFailures.clear();
                deepLight(mc, "client");
                final int bx = (this.px & ~15) + 96;
                final int bz = (this.pz & ~15) + 16;
                server(mc, world -> {
                    deepScene(world, bx, bz);
                    for (EntityPlayerMP p : world.getMinecraftServer().getPlayerList().getPlayers()) {
                        p.removePotionEffect(net.minecraft.init.MobEffects.NIGHT_VISION);
                    }
                    teleport(world, bx - 9.5, -22, bz + 0.5, 270F, 10F);
                });
                this.wait = 200;
                this.state = 60;
                return;
            }
            case 60: {
                deepView(mc, "cc-height-deep-tunnel", "the tunnel at y -21 toward a torch in the next chunk");
                final int bx = (this.px & ~15) + 96;
                final int bz = (this.pz & ~15) + 16;
                server(mc, world -> teleport(world, bx + 24.5, 12, bz + 0.5, 270F, 89F));
                this.wait = 200;
                this.state = 61;
                return;
            }
            case 61: {
                deepView(mc, "cc-height-deep-shaft", "down a shaft lit by wall torches at y 2 and -12");
                // A 1x1 pit from the surface to y -30 in a stone sleeve, dug a few blocks a tick as a player digs (a
                // chunk with 64 changes in a tick is sent whole, with the server's light): the client works out the
                // sky light down it itself.
                final int x = (this.px & ~15) + 40;
                final int z = (this.pz & ~15) + 40;
                server(mc, world -> {
                    int top = world.getHeight(x, z);
                    this.pitTop = top;
                    for (int y = top; y >= -31; y--) {
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                world.setBlockState(new BlockPos(x + dx, y, z + dz), Blocks.STONE.getDefaultState(), 2);
                            }
                        }
                    }
                });
                this.pitY = Integer.MIN_VALUE;
                this.wait = 40;
                this.state = 70;
                return;
            }
            case 70: {
                final int x = (this.px & ~15) + 40;
                final int z = (this.pz & ~15) + 40;
                if (this.pitY == Integer.MIN_VALUE) {
                    this.pitY = this.pitTop;
                }
                if (this.pitY < -30) {
                    this.wait = 60;
                    this.state = 71;
                    return;
                }
                final int from = this.pitY;
                this.pitY -= 4;
                server(mc, world -> {
                    for (int y = from; y > from - 4 && y >= -30; y--) {
                        world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3);
                    }
                });
                this.wait = 2;
                return;
            }
            case 71: {
                final int x = (this.px & ~15) + 40;
                final int z = (this.pz & ~15) + 40;
                int[] ys = {this.pitTop - 1, 16, 15, 1, 0, -1, -15, -16, -17, -29, -30};
                for (int y : ys) {
                    eq(15, mc.world.getLightFor(net.minecraft.world.EnumSkyBlock.SKY, new BlockPos(x, y, z)),
                            "client sky light down a pit dug from the surface (y " + y + ")");
                }
                eq(0, mc.world.getLightFor(net.minecraft.world.EnumSkyBlock.SKY, new BlockPos(x + 1, -20, z)),
                        "client sky light in the stone beside the pit");
                // The light blocks are drawn with: ChunkCache (the render chunk's region), whose getLightForExt
                // VintageFix overwrites with a 0-255 guard (it gave full sky light below 0, bright depths).
                BlockPos stone = new BlockPos(x + 1, -20, z);
                net.minecraft.world.ChunkCache region = new net.minecraft.world.ChunkCache(mc.world,
                        stone.add(-16, -16, -16), stone.add(16, 16, 16), 1);
                eq(0, region.getCombinedLight(stone, 0) >> 20 & 15, "render region's sky light in the stone at y -20");
                eq(15, region.getCombinedLight(new BlockPos(x, -16, z), 0) >> 20 & 15,
                        "render region's sky light down the pit at y -16");
                server(mc, world -> {
                    for (int y : ys) {
                        int got = world.getLightFor(net.minecraft.world.EnumSkyBlock.SKY, new BlockPos(x, y, z));
                        if (got != 15) {
                            this.serverFailures.add("sky light down the pit at y " + y + ": " + got + ", expected 15");
                        }
                    }
                });
                this.wait = 5;
                this.state = 72;
                return;
            }
            case 72: {
                for (String f : this.serverFailures) {
                    fail("server: " + f);
                }
                this.serverFailures.clear();
                // Leave the world and come back: the client then has the saved light, sent with the chunks.
                // No quitting packet first: with it the server can stop on its own before loadWorld's shutdown task
                // is queued, and loadWorld then waits for that task forever. loadWorld logs the player out itself.
                // Packets already queued for the client thread would run after it with no world (an NPE that some
                // packs' crash handlers turn into a crash): drop them.
                mc.loadWorld(null);
                java.util.Queue<?> tasks = ObfuscationReflectionHelper.getPrivateValue(Minecraft.class, mc, "field_152351_aB");
                synchronized (tasks) {
                    tasks.clear();
                }
                mc.displayGuiScreen(new GuiMainMenu());
                this.wait = 100;
                this.state = 53;
                return;
            }
            case 53:
                mc.launchIntegratedServer(WORLD, WORLD, null);
                this.pendingTicks = 0;
                this.state = 54;
                return;
            case 54:
                if (mc.world == null || mc.player == null || mc.getIntegratedServer() == null) {
                    return;
                }
                mc.gameSettings.pauseOnLostFocus = false;
                this.wait = 300;
                this.state = 55;
                return;
            case 55: {
                final int bx = (this.px & ~15) + 96;
                final int bz = (this.pz & ~15) + 16;
                server(mc, world -> {
                    for (EntityPlayerMP p : world.getMinecraftServer().getPlayerList().getPlayers()) {
                        p.removePotionEffect(net.minecraft.init.MobEffects.NIGHT_VISION);
                    }
                    teleport(world, bx - 9.5, -22, bz + 0.5, 270F, 10F);
                });
                this.wait = 300;
                this.state = 57;
                return;
            }
            case 57: {
                deepView(mc, "cc-height-deep-tunnel-relog", "the tunnel at y -21 after a relog");
                deepLight(mc, "client after a relog");
                final List<int[]> deep = this.deep;
                server(mc, world -> {
                    for (int[] e : deep) {
                        int got = world.getLightFor(net.minecraft.world.EnumSkyBlock.BLOCK, new BlockPos(e[0], e[1], e[2]));
                        if (got != e[3]) {
                            this.serverFailures.add("block light at " + e[0] + " " + e[1] + " " + e[2] + " after the reload: "
                                    + got + ", expected " + e[3]);
                        }
                    }
                });
                this.wait = 20;
                this.state = 56;
                return;
            }
            case 56:
                for (String f : this.serverFailures) {
                    fail("server: " + f);
                }
                eq(0, this.serverFailures.size(), "server deep torch light that changed in a save and reload");
                this.serverFailures.clear();
                // Night vision back for the checks after this one (the deep scenes were looked at without it).
                server(mc, world -> {
                    for (EntityPlayerMP p : world.getMinecraftServer().getPlayerList().getPlayers()) {
                        p.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.init.MobEffects.NIGHT_VISION, 1000000, 0, false, false));
                    }
                });
                this.wait = 20;
                this.state = 6;
                return;
            case 40: {
                int[] g = gold(mc);
                ok(g[1] * 3 > g[2], "a gold wall at y -40 is drawn at its own height, seen from y -40 (Render Regions on): "
                        + g[1] + " of " + g[2] + " middle pixels gold");
                screenshot(mc, "cc-height-regions-y-40");
                final int x = this.px + 60;
                final int z = this.pz - 300;
                server(mc, world -> teleport(world, x + 0.5, 300, z + 0.5, 0F, 0F));
                this.wait = 200;
                this.state = 41;
                return;
            }
            case 41: {
                int[] g = gold(mc);
                ok(g[1] * 3 > g[2], "a gold wall at y 300 is drawn at its own height, seen from y 300 (Render Regions on): "
                        + g[1] + " of " + g[2] + " middle pixels gold");
                screenshot(mc, "cc-height-regions-y300");
                // 200 blocks north of the low wall, 276 above it, looking down toward it (so its render chunks are in
                // view): the wall is hidden underground; anything drawn 256 too high hangs in the sky at y 192-255,
                // where the top of the frame shows only sky (the high room is behind the camera).
                final int x = this.px + 60;
                final int z = this.pz - 190;
                server(mc, world -> teleport(world, x + 0.5, 236, z + 0.5, 0F, 25F));
                this.wait = 300;
                this.state = 42;
                return;
            }
            case 42: {
                int dark = darkSky(mc);
                ok(dark < 2000, "nothing from below 0 is drawn in the sky (Render Regions on): " + dark
                        + " dark pixels in the top 40% of the view");
                screenshot(mc, "cc-height-regions-sky");
                if (this.regionShaders != null && !"OFF".equals(this.regionShaders)) {
                    shaders(mc, this.regionShaders);
                }
                this.wait = 300;
                this.state = 43;
                return;
            }
            case 43: {
                screenshot(mc, "cc-height-regions-sky-shaders");
                final int x = this.px;
                final int z = this.pz;
                server(mc, world -> teleport(world, x + 0.5, -61, z + 0.5, 200F, 35F));
                this.wait = 200;
                this.state = 7;
                return;
            }
            case 7: {
                compiled(mc, new BlockPos(this.px, -62, this.pz), "the render chunk at y -64 (Render Regions on)");
                rendered(mc, new BlockPos(this.px, -62, this.pz), "y -64", "Render Regions on");
                renderRegions(mc, false);
                // Shaders off.
                this.shaderPack = shaderName();
                shaders(mc, "OFF");
                this.wait = 200;
                this.state = 8;
                return;
            }
            case 8:
                eq(Boolean.FALSE, optifineStatic("net.optifine.shaders.Shaders", "shaderPackLoaded"), "OptiFine: no shader pack loaded");
                compiled(mc, new BlockPos(this.px, -62, this.pz), "the render chunk at y -64 (shaders off)");
                rendered(mc, new BlockPos(this.px, -62, this.pz), "y -64", "shaders off");
                screenshot(mc, "cc-height-bottom-noshaders");
                if (this.shaderPack != null && !"OFF".equals(this.shaderPack)) {
                    shaders(mc, this.shaderPack);
                }
                finish(mc);
                return;
            default:
        }
    }

    private void frustum(Minecraft mc) {
        frustum(mc, 24, -64, 304);
    }

    private void frustum(Minecraft mc, int chunksHigh, int lowest, int highest) {
        ViewFrustum frustum = ObfuscationReflectionHelper.getPrivateValue(RenderGlobal.class, mc.renderGlobal, "field_175008_n");
        if (frustum == null) {
            // A renderer that replaces vanilla's frustum (Nothirium): its own render chunks are checked in compiled().
            CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST: no vanilla frustum (another chunk renderer); frustum checks skipped");
            return;
        }
        int high = ObfuscationReflectionHelper.<Integer, ViewFrustum>getPrivateValue(ViewFrustum.class, frustum, "field_178168_c");
        eq(chunksHigh, high, "the render frustum is " + chunksHigh + " render chunks high");
        int low = Integer.MAX_VALUE;
        int top = Integer.MIN_VALUE;
        for (RenderChunk r : frustum.renderChunks) {
            low = Math.min(low, r.getPosition().getY());
            top = Math.max(top, r.getPosition().getY());
        }
        eq(lowest, low, "the lowest render chunk's y");
        eq(highest, top, "the highest render chunk's y");
    }

    private void compiled(Minecraft mc, BlockPos pos, String what) {
        if (celeritas(pos, what) || nothirium(pos, what)) {
            return;
        }
        ViewFrustum frustum = ObfuscationReflectionHelper.getPrivateValue(RenderGlobal.class, mc.renderGlobal, "field_175008_n");
        RenderChunk found = null;
        BlockPos base = new BlockPos(pos.getX() & ~15, pos.getY() & ~15, pos.getZ() & ~15);
        for (RenderChunk r : frustum.renderChunks) {
            if (r.getPosition().equals(base)) {
                found = r;
            }
        }
        if (!ok(found != null, what + " exists")) {
            return;
        }
        CompiledChunk c = found.getCompiledChunk();
        ok(c != CompiledChunk.DUMMY && !c.isEmpty(), what + " is compiled with blocks in it");
    }

    /**
     * With Celeritas, which draws through its own section manager instead of the frustum: the section holding pos
     * exists, is built and has something to draw, and the manager's sections span y -64..319. False without Celeritas.
     */
    private boolean celeritas(BlockPos pos, String what) {
        Object renderer;
        try {
            renderer = Class.forName("org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer").getMethod("instance").invoke(null);
        } catch (Throwable t) {
            return false;
        }
        try {
            Object manager = renderer.getClass().getMethod("getRenderSectionManager").invoke(renderer);
            java.util.Collection<?> sections = (java.util.Collection<?>) manager.getClass().getMethod("getAllRenderSections").invoke(manager);
            Object found = null;
            int low = Integer.MAX_VALUE;
            int high = Integer.MIN_VALUE;
            for (Object section : sections) {
                Class<?> c = section.getClass();
                int sx = (Integer) c.getMethod("getChunkX").invoke(section);
                int sy = (Integer) c.getMethod("getChunkY").invoke(section);
                int sz = (Integer) c.getMethod("getChunkZ").invoke(section);
                low = Math.min(low, sy);
                high = Math.max(high, sy);
                if (sx == pos.getX() >> 4 && sy == pos.getY() >> 4 && sz == pos.getZ() >> 4) {
                    found = section;
                }
            }
            eq(-4, low, "Celeritas: the lowest section y");
            eq(19, high, "Celeritas: the highest section y");
            if (ok(found != null, "Celeritas: " + what + " exists")) {
                boolean built = (Boolean) found.getClass().getMethod("isBuilt").invoke(found);
                boolean draws = (Boolean) found.getClass().getMethod("hasAnythingToRender").invoke(found);
                ok(built && draws, "Celeritas: " + what + " is built with something to draw (built " + built + ", draws " + draws + ")");
            }
        } catch (Throwable t) {
            fail("Celeritas: reading its sections threw " + t);
        }
        return true;
    }

    /** With Nothirium: the render chunk at pos exists, is compiled (not dirty) and is not empty. False without it. */
    private boolean nothirium(BlockPos pos, String what) {
        Object provider;
        try {
            provider = Class.forName("meldexun.nothirium.mc.renderer.ChunkRenderManager").getMethod("getProvider").invoke(null);
        } catch (Throwable t) {
            return false;
        }
        try {
            java.lang.reflect.Method at = Class.forName("meldexun.nothirium.api.renderer.chunk.IRenderChunkProvider")
                    .getMethod("getRenderChunkAt", int.class, int.class, int.class);
            Object chunk = at.invoke(provider, pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
            if (ok(chunk != null, "Nothirium: " + what + " exists")) {
                boolean dirty = (Boolean) chunk.getClass().getMethod("isDirty").invoke(chunk);
                boolean empty = (Boolean) chunk.getClass().getMethod("isEmpty").invoke(chunk);
                ok(!dirty && !empty, "Nothirium: " + what + " is compiled with blocks in it (dirty " + dirty + ", empty " + empty + ")");
            }
        } catch (Throwable t) {
            fail("Nothirium: reading its render chunks threw " + t);
        }
        return true;
    }

    private void block(Minecraft mc, int x, int y, int z, Block want) {
        BlockPos pos = new BlockPos(x, y, z);
        Block got = mc.world.getBlockState(pos).getBlock();
        eq(want, got, "client block at y " + y);
        if (got != want) {
            // Which layer lost it: the server's world, the client chunk's sections, or the lookup.
            net.minecraft.world.chunk.Chunk chunk = mc.world.getChunk(pos);
            net.minecraft.world.chunk.storage.ExtendedBlockStorage[] all = chunk.getBlockStorageArray();
            int slot = com.exiledradio.cavesandcliffsbackport.height.HeightLayout.slot(y);
            net.minecraft.world.chunk.storage.ExtendedBlockStorage section = slot < all.length ? all[slot] : null;
            Block server = mc.getIntegratedServer().getWorld(0).getBlockState(pos).getBlock();
            CavesAndCliffsBackport.LOGGER.error("CLIENT SELFTEST DIAG y {}: server {}, client chunk {} ({} sections), slot {} section {}{}, chunk.getBlockState {}",
                    y, server, chunk.getClass().getName(), all.length, slot, section == null ? "null" : "y" + section.getYLocation(),
                    section == null ? "" : " holding " + section.get(x & 15, y & 15, z & 15).getBlock(),
                    chunk.getBlockState(pos).getBlock());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // OptiFine (by reflection: the test runs with and without it)
    // ---------------------------------------------------------------------------------------------

    private static final String[] OPTIFINE_SITES = {
            "ViewFrustum.getRenderChunk.y (OptiFine)",
            "RenderGlobal.getRenderChunkOffset (OptiFine)",
            "RenderGlobal.setupTerrain.startHeight (OptiFine)",
            "RenderGlobal.setupTerrain.maxChunkY (OptiFine)",
            "RenderGlobal.setupTerrain.above (OptiFine)",
            "ChunkVisibility.getMaxChunkY.head",
            "RenderChunkUtils.getCountBlocks.section",
    };

    private static boolean optifine() {
        try {
            Class.forName("net.optifine.shaders.Shaders", false, HeightClientSelfTest.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** With OptiFine, the drift report names its variants (no site fell back to vanilla's). */
    private void optifineReport() {
        if (!optifine()) {
            return;
        }
        List<String> lines = PatchReport.lines();
        for (String id : OPTIFINE_SITES) {
            ok(lines.contains(id + ": found"), "OptiFine: drift report has " + id + " (report: " + lines.size() + " lines)");
        }
    }

    private static Object optifineStatic(String owner, String field) {
        try {
            return Class.forName(owner).getField(field).get(null);
        } catch (Throwable t) {
            return t.toString();
        }
    }

    private Object maxChunkY(Minecraft mc) {
        try {
            return Class.forName("net.optifine.render.ChunkVisibility")
                    .getMethod("getMaxChunkY", net.minecraft.world.World.class, net.minecraft.entity.Entity.class, int.class)
                    .invoke(null, mc.world, mc.player, mc.gameSettings.renderDistanceChunks);
        } catch (Throwable t) {
            return t.toString();
        }
    }

    /** The shader pack to put back after the Render Regions pixel checks. */
    private String regionShaders;

    /**
     * Gold pixels in the last frame: {all, in the middle third, middle third's size}. Gold blocks under a plain sky,
     * shaders off: nothing else in view is that yellow.
     */
    private static int[] gold(Minecraft mc) {
        java.awt.image.BufferedImage img = net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        int w = img.getWidth();
        int h = img.getHeight();
        int all = 0;
        int middle = 0;
        int size = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = rgb >> 16 & 255;
                int g = rgb >> 8 & 255;
                int b = rgb & 255;
                boolean isGold = r > 150 && g > 110 && b < 110 && r > b + 70;
                boolean inMiddle = x >= w / 3 && x < 2 * w / 3 && y >= h / 3 && y < 2 * h / 3;
                if (isGold) {
                    all++;
                }
                if (inMiddle) {
                    size++;
                    if (isGold) {
                        middle++;
                    }
                }
            }
        }
        CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST gold pixels at y {}: {} in view, {} of {} in the middle",
                (int) Math.floor(mc.player.posY), all, middle, size);
        return new int[] {all, middle, size};
    }

    /** Dark pixels (any block, not sky) in the top 40% of the last frame; shaders off, noon, clear. */
    private static int darkSky(Minecraft mc) {
        java.awt.image.BufferedImage img = net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        int n = 0;
        for (int y = 0; y < img.getHeight() * 2 / 5; y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                if ((rgb >> 16 & 255) < 110 && (rgb >> 8 & 255) < 110 && (rgb & 255) < 140) {
                    n++;
                }
            }
        }
        CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST dark pixels in the top of the sky view at y {}: {}", (int) Math.floor(mc.player.posY), n);
        return n;
    }

    /**
     * A sealed stone room around (x, y, z) with a 15x15 gold wall 10 blocks south of that point (+z), and, for a room
     * below the surface, a shaft from it to the sky (so the renderer's visibility walk reaches it from above).
     */
    private static void goldRoom(WorldServer world, int x, int y, int z, boolean shaft) {
        for (BlockPos p : BlockPos.getAllInBox(new BlockPos(x - 9, y - 9, z - 4), new BlockPos(x + 9, y + 9, z + 12))) {
            world.setBlockState(p, Blocks.STONE.getDefaultState(), 2);
        }
        for (BlockPos p : BlockPos.getAllInBox(new BlockPos(x - 8, y - 8, z - 3), new BlockPos(x + 8, y + 8, z + 11))) {
            world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
        }
        for (BlockPos p : BlockPos.getAllInBox(new BlockPos(x - 7, y - 7, z + 10), new BlockPos(x + 7, y + 7, z + 10))) {
            world.setBlockState(p, Blocks.GOLD_BLOCK.getDefaultState(), 2);
        }
        if (shaft) {
            int top = world.getHeight(x, z) + 2;
            for (int sy = y + 9; sy <= Math.max(top, 64); sy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                        BlockPos p = new BlockPos(x + dx, sy, z - 1 + dz);
                        if (!edge) {
                            world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
                        } else if (sy < 64 && !world.getBlockState(p).isOpaqueCube()) {
                            world.setBlockState(p, Blocks.STONE.getDefaultState(), 2);
                        }
                    }
                }
            }
        }
    }

    private static String shaderName() {
        Object name = optifineStatic("net.optifine.shaders.Shaders", "currentShaderName");
        return String.valueOf(name);
    }

    private static void shaders(Minecraft mc, String pack) throws ReflectiveOperationException {
        Class<?> shaders = Class.forName("net.optifine.shaders.Shaders");
        shaders.getMethod("setShaderPack", String.class).invoke(null, pack);
        shaders.getMethod("uninit").invoke(null);
        mc.renderGlobal.loadRenderers();
        CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST shader pack set to {}", pack);
    }

    private static void renderRegions(Minecraft mc, boolean on) throws ReflectiveOperationException {
        mc.gameSettings.getClass().getField("ofRenderRegions").setBoolean(mc.gameSettings, on);
        mc.renderGlobal.loadRenderers();
        CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST Render Regions {}", on ? "on" : "off");
    }

    /**
     * The render chunk holding pos is in OptiFine's {@code renderInfos} (this frame's visible chunks), and, while a
     * shader pack with a shadow pass is loaded, in {@code renderInfosShadow}.
     */
    private void rendered(Minecraft mc, BlockPos pos, String what, String setup) {
        BlockPos base = new BlockPos(pos.getX() & ~15, pos.getY() & ~15, pos.getZ() & ~15);
        if (ObfuscationReflectionHelper.getPrivateValue(RenderGlobal.class, mc.renderGlobal, "field_175008_n") == null) {
            // Nothirium replaces the chunk renderer: OptiFine's setupTerrain does not run and renderInfos stays
            // empty; Nothirium's own render chunks are checked in compiled().
            CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST OptiFine ({}): no vanilla frustum (another chunk renderer); renderInfos not used", setup);
            return;
        }
        try {
            List<?> infos = ObfuscationReflectionHelper.getPrivateValue(RenderGlobal.class, mc.renderGlobal, "field_72755_R");
            CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST OptiFine ({}): renderInfos {}", setup, range(infos));
            ok(holds(infos, base), "OptiFine (" + setup + "): the render chunk at " + what + " is in renderInfos " + range(infos));
            Object loaded = optifineStatic("net.optifine.shaders.Shaders", "shaderPackLoaded");
            if (Boolean.TRUE.equals(loaded)) {
                List<?> shadow = (List<?>) RenderGlobal.class.getField("renderInfosShadow").get(mc.renderGlobal);
                if (shadow.isEmpty()) {
                    CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST ({}): renderInfosShadow is empty (no shadow pass)", setup);
                } else {
                    CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST OptiFine ({}): renderInfosShadow {}", setup, range(shadow));
                    ok(holds(shadow, base), "OptiFine (" + setup + "): the render chunk at " + what + " is in the shadow pass "
                            + range(shadow));
                }
            }
        } catch (Throwable t) {
            fail("OptiFine (" + setup + "): reading renderInfos threw " + t);
        }
    }

    private static RenderChunk chunkOf(Object info) throws ReflectiveOperationException {
        if (info instanceof RenderChunk) {
            return (RenderChunk) info;
        }
        for (String name : new String[] {"field_178036_a", "renderChunk"}) {
            try {
                java.lang.reflect.Field f = info.getClass().getDeclaredField(name);
                f.setAccessible(true);
                return (RenderChunk) f.get(info);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException("renderChunk of " + info.getClass());
    }

    private static boolean holds(List<?> infos, BlockPos base) throws ReflectiveOperationException {
        for (Object info : new ArrayList<Object>(infos)) {
            RenderChunk r = chunkOf(info);
            if (r != null && r.getPosition().equals(base)) {
                return true;
            }
        }
        return false;
    }

    /** "(n chunks, y a..b)" for the log. */
    private static String range(List<?> infos) throws ReflectiveOperationException {
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        List<Object> copy = new ArrayList<Object>(infos);
        for (Object info : copy) {
            RenderChunk r = chunkOf(info);
            low = Math.min(low, r.getPosition().getY());
            high = Math.max(high, r.getPosition().getY());
        }
        return "(" + copy.size() + " chunks, y " + low + ".." + high + ")";
    }

    /** Resizes the game window (the instance's own size is the launcher's); a failure only logs. */
    private static void window(Minecraft mc, int width, int height) {
        try {
            if (mc.displayWidth < width) {
                org.lwjgl.opengl.Display.setDisplayMode(new org.lwjgl.opengl.DisplayMode(width, height));
                CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST window resized to {}x{}", width, height);
            }
        } catch (Throwable t) {
            CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST could not resize the window: {}", t.toString());
        }
    }

    /** An F2 screenshot of the last frame, as {@code screenshots/<name>.png}. */
    private void screenshot(Minecraft mc, String name) {
        java.io.File file = new java.io.File(new java.io.File(mc.gameDir, "screenshots"), name + ".png");
        file.delete();
        net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.gameDir, name + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        if (ok(file.isFile() && file.length() > 0, "screenshot " + name + " saved")) {
            CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST screenshot {} at y {} ({})", file.getAbsolutePath(),
                    (int) Math.floor(mc.player.posY), optifine() ? "shaders " + shaderName() : "no OptiFine");
        }
    }

    /** Moves the player into the most open cave at y -50 near (x, z); carves a lit room there if none is found. */
    private static void cave(WorldServer world, int x, int z) {
        BlockPos best = null;
        int bestScore = 0;
        for (int dx = -48; dx <= 48; dx += 2) {
            for (int dz = -48; dz <= 48; dz += 2) {
                BlockPos pos = new BlockPos(x + dx, -50, z + dz);
                if (!world.isBlockLoaded(pos) || !world.isAirBlock(pos) || !world.isAirBlock(pos.up())) {
                    continue;
                }
                int score = 0;
                for (BlockPos p : BlockPos.getAllInBox(pos.add(-3, -1, -3), pos.add(3, 3, 3))) {
                    if (world.isAirBlock(p)) {
                        score++;
                    }
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = pos;
                }
            }
        }
        if (best == null || bestScore < 60) {
            best = new BlockPos(x + 20, -50, z);
            for (BlockPos p : BlockPos.getAllInBox(best.add(-4, -1, -4), best.add(4, 3, 4))) {
                world.setBlockState(p, Blocks.AIR.getDefaultState(), 3);
            }
            for (int[] c : new int[][] {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
                world.setBlockState(best.add(c[0], 3, c[1]), Blocks.GLOWSTONE.getDefaultState(), 3);
            }
            CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST no open cave at y -50 near the player (best {} air); carved a room at {}",
                    bestScore, best);
        } else {
            CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST cave at {} ({} air of 245 around it)", best, bestScore);
        }
        teleport(world, best.getX() + 0.5, best.getY(), best.getZ() + 0.5, 45F, 10F);
    }

    /** The deep torch light the server expects (HeightSelfTest.deepTorchLights), and failures found on its thread. */
    private volatile List<int[]> deep;
    /** The sky-light pit: the surface's height there, and how far down it is dug. */
    private volatile int pitTop;
    private int pitY;
    private final List<String> serverFailures = java.util.Collections.synchronizedList(new ArrayList<String>());

    /**
     * A scene to look at, sealed in stone: a 3x3 tunnel at y -22..-20 along x from bx - 16 to bx + 16 with a floor
     * torch at bx + 2 (the chunk east of the camera), and a 3x3 shaft at bx + 24 from y 14 down to -24 with wall
     * torches at y 2 and -12, open at the top.
     */
    private static void deepScene(WorldServer world, int bx, int bz) {
        for (int x = bx - 18; x <= bx + 27; x++) {
            for (int z = bz - 3; z <= bz + 3; z++) {
                for (int y = -26; y <= 14; y++) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.STONE.getDefaultState(), 2);
                }
            }
        }
        for (int x = bx - 16; x <= bx + 16; x++) {
            for (int z = bz - 1; z <= bz + 1; z++) {
                for (int y = -22; y <= -20; y++) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3);
                }
            }
        }
        world.setBlockState(new BlockPos(bx + 2, -22, bz), Blocks.TORCH.getDefaultState(), 3);
        for (int x = bx + 23; x <= bx + 25; x++) {
            for (int z = bz - 1; z <= bz + 1; z++) {
                for (int y = -24; y <= 14; y++) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3);
                }
            }
        }
        // Stone over the shaft's top so sky light does not reach down it: block light only.
        for (int x = bx + 23; x <= bx + 25; x++) {
            for (int z = bz - 1; z <= bz + 1; z++) {
                world.setBlockState(new BlockPos(x, 15, z), Blocks.STONE.getDefaultState(), 3);
            }
        }
        net.minecraft.block.state.IBlockState wall = Blocks.TORCH.getDefaultState()
                .withProperty(net.minecraft.block.BlockTorch.FACING, net.minecraft.util.EnumFacing.EAST);
        world.setBlockState(new BlockPos(bx + 23, 2, bz), wall, 3);
        world.setBlockState(new BlockPos(bx + 23, -12, bz), wall, 3);
    }

    /**
     * Saves a screenshot and checks the scene is not black: the mean brightness (0-255) of the middle half of the
     * frame, and the share of pixels darker than 4, logged with the file.
     */
    private void deepView(Minecraft mc, String name, String what) {
        screenshot(mc, name);
        java.awt.image.BufferedImage img = net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        long sum = 0;
        int black = 0;
        int n = 0;
        for (int y = img.getHeight() / 4; y < img.getHeight() * 3 / 4; y += 2) {
            for (int x = img.getWidth() / 4; x < img.getWidth() * 3 / 4; x += 2) {
                int rgb = img.getRGB(x, y);
                int l = (((rgb >> 16) & 255) + ((rgb >> 8) & 255) + (rgb & 255)) / 3;
                sum += l;
                if (l < 4) {
                    black++;
                }
                n++;
            }
        }
        CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST {}: mean brightness {}, {}% black", name, sum / n, black * 100 / n);
        ok(sum / n >= 20 && black * 100 / n < 25, what + " is lit (mean brightness " + sum / n + ", " + black * 100 / n + "% black)");
    }

    /** The client's block light at every position the server checked. */
    private void deepLight(Minecraft mc, String what) {
        if (!ok(this.deep != null && !this.deep.isEmpty(), what + ": the deep torch site was built")) {
            return;
        }
        int wrong = 0;
        for (int[] e : this.deep) {
            int got = mc.world.getLightFor(net.minecraft.world.EnumSkyBlock.BLOCK, new BlockPos(e[0], e[1], e[2]));
            if (got != e[3]) {
                wrong++;
                if (wrong <= 40) {
                    fail(what + ": block light at " + e[0] + " " + e[1] + " " + e[2] + ": " + got + ", expected " + e[3]);
                }
            }
        }
        eq(0, wrong, what + ": deep torch light different from the server's (" + this.deep.size() + " positions)");
    }

    private interface ServerTask {
        void run(WorldServer world);
    }

    private java.util.concurrent.atomic.AtomicBoolean pending;
    private int pendingTicks;

    private void server(Minecraft mc, final ServerTask task) {
        final IntegratedServer server = mc.getIntegratedServer();
        final java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
        this.pending = done;
        this.pendingTicks = 0;
        server.addScheduledTask(() -> {
            try {
                task.run(server.getWorld(0));
            } finally {
                done.set(true);
            }
        });
    }

    private static void teleport(WorldServer world, double x, double y, double z, float yaw, float pitch) {
        for (EntityPlayerMP p : world.getMinecraftServer().getPlayerList().getPlayers()) {
            p.capabilities.isFlying = true;
            p.sendPlayerAbilities();
            p.connection.setPlayerLocation(x, y, z, yaw, pitch);
        }
    }

    private boolean ok(boolean condition, String what) {
        if (condition) {
            this.passed++;
        } else {
            fail(what);
        }
        return condition;
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
            PrintWriter out = new PrintWriter(new java.io.File(mc.gameDir, "selftest-client-report.txt"), "UTF-8");
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
