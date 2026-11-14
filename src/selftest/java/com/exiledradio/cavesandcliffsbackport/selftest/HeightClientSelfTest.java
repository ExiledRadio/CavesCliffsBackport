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
                screenshot(mc, "cc-height-y280");
                if (!optifine()) {
                    finish(mc);
                    return;
                }
                // Render Regions (O7's path: RenderChunkUtils sizes the region buffers), back in the pocket at -61.
                renderRegions(mc, true);
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
