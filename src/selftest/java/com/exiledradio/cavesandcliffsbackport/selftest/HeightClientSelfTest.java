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
 * Started by name from {@code HeightContent} when {@code cavesandcliffsbackport.selftest} names {@code heightclient}.
 */
public final class HeightClientSelfTest {

    private static final String WORLD = "cc_height_selftest";

    private final List<String> failures = new ArrayList<String>();
    private int passed;
    private int state;
    private int wait;
    private int px;
    private int pz;

    public static void start() {
        MinecraftForge.EVENT_BUS.register(new HeightClientSelfTest());
        CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST heightclient requested");
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

    private void block(Minecraft mc, int x, int y, int z, Block want) {
        eq(want, mc.world.getBlockState(new BlockPos(x, y, z)).getBlock(), "client block at y " + y);
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
