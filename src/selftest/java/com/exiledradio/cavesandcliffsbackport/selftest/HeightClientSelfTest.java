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
                if (this.startupOnly) {
                    CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST startup: main menu reached; extended height {} ({} sites, refusal: {})",
                            HeightContent.allowed() ? "on" : "off", PatchReport.size(), HeightContent.refusal());
                    ok(true, "main menu reached");
                    finish(mc);
                    return;
                }
                ok(HeightContent.allowed(), "extended height is on (refusal: " + HeightContent.refusal() + ")");
                eq(new ArrayList<String>(), PatchReport.missed(), "client patch sites not found");
                mc.getSaveLoader().deleteWorldDirectory(WORLD);
                WorldSettings settings = new WorldSettings(1718L, GameType.CREATIVE, true, false, HeightContent.worldType());
                mc.launchIntegratedServer(WORLD, WORLD, settings);
                this.state = 1;
                return;
            case 1:
                if (mc.world == null || mc.player == null || mc.getIntegratedServer() == null) {
                    return;
                }
                this.wait = 100;
                this.state = 2;
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
                    teleport(world, x + 0.5, -61, z + 0.5);
                });
                this.wait = 200;
                this.state = 3;
                return;
            }
            case 3: {
                frustum(mc);
                block(mc, this.px, -64, this.pz, Blocks.BEDROCK);
                block(mc, this.px, -62, this.pz, Blocks.DIAMOND_BLOCK);
                block(mc, this.px + 1, -59, this.pz + 2, Blocks.GLOWSTONE);
                block(mc, this.px + 2, 319, this.pz, Blocks.GLASS);
                block(mc, this.px + 3, 319, this.pz, Blocks.GOLD_BLOCK);
                compiled(mc, new BlockPos(this.px, -62, this.pz), "the render chunk at y -64 (camera at y -61)");
                final int x = this.px;
                final int z = this.pz;
                server(mc, world -> teleport(world, x + 2.5, 322, z + 0.5));
                this.wait = 200;
                this.state = 4;
                return;
            }
            case 4:
                ok(mc.player.posY > 319, "the camera is above the world (y " + mc.player.posY + ")");
                compiled(mc, new BlockPos(this.px + 3, 319, this.pz), "the render chunk at y 304 (camera above the world)");
                finish(mc);
                return;
            default:
        }
    }

    private void frustum(Minecraft mc) {
        ViewFrustum frustum = ObfuscationReflectionHelper.getPrivateValue(RenderGlobal.class, mc.renderGlobal, "field_175008_n");
        if (frustum == null) {
            // A renderer that replaces vanilla's frustum (Nothirium): its own render chunks are checked in compiled().
            CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST: no vanilla frustum (another chunk renderer); frustum checks skipped");
            return;
        }
        int high = ObfuscationReflectionHelper.<Integer, ViewFrustum>getPrivateValue(ViewFrustum.class, frustum, "field_178168_c");
        eq(24, high, "the render frustum is 24 render chunks high");
        int low = Integer.MAX_VALUE;
        int top = Integer.MIN_VALUE;
        for (RenderChunk r : frustum.renderChunks) {
            low = Math.min(low, r.getPosition().getY());
            top = Math.max(top, r.getPosition().getY());
        }
        eq(-64, low, "the lowest render chunk's y");
        eq(304, top, "the highest render chunk's y");
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

    private interface ServerTask {
        void run(WorldServer world);
    }

    private static void server(Minecraft mc, final ServerTask task) {
        final IntegratedServer server = mc.getIntegratedServer();
        server.addScheduledTask(() -> task.run(server.getWorld(0)));
    }

    private static void teleport(WorldServer world, double x, double y, double z) {
        for (EntityPlayerMP p : world.getMinecraftServer().getPlayerList().getPlayers()) {
            p.capabilities.isFlying = true;
            p.sendPlayerAbilities();
            p.setPositionAndUpdate(x, y, z);
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
