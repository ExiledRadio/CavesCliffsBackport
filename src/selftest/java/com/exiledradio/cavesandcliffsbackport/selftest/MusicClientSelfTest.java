package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.height.HeightContent;
import com.exiledradio.cavesandcliffsbackport.music.MusicRegions;
import com.exiledradio.cavesandcliffsbackport.terrain.ClientBiomes;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernTerrain;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Biomes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Stream Mu's client test in a world with 1.18 terrain: the client's music biome is the server's 1.18 biome.
 *
 * <pre>    ./gradlew runClient -Pclienttest=music</pre>
 * Makes a single-player Caves &amp; Cliffs world, finds on the server (by 1.18's biome source, no chunk loaded) a
 * lush caves, a dripstone caves and a mountain spot, and for spawn and each spot: teleports there, waits for the
 * chunks and their biome packets, has the server read its 1.18 biome ({@code ModernTerrain.biomeAt}) on a grid
 * through 7 x 7 chunks from y -64 to 319, and checks the client's {@code ClientBiomes} gives the same id and
 * {@code MusicRegions.at} the same music biome at each point. Logs how often the 1.17 regions
 * ({@code MusicRegions.CAVES}) would have disagreed. No clicks or keys; the game quits by itself (report in
 * {@code selftest-client-report.txt}).
 */
public final class MusicClientSelfTest {

    private static final String WORLD = "cc_music_selftest";
    private static final long SEED = 1718L;
    private static final int RADIUS = 3;
    private static final int STEP_XZ = 3;
    private static final int STEP_Y = 5;

    private final List<String> failures = new ArrayList<String>();
    private int passed;
    private int state;
    private int wait;
    private int waited;

    /** The spots to check: x, y, z and a name. */
    private final List<int[]> spots = new ArrayList<int[]>();
    private final List<String> names = new ArrayList<String>();
    private int spot;
    /** The server's 1.18 biome ids on the grid of the current spot. */
    private volatile int[] serverBiomes;
    private final Map<MusicRegions.Region, Integer> seen = new EnumMap<MusicRegions.Region, Integer>(MusicRegions.Region.class);

    private java.util.concurrent.atomic.AtomicBoolean pending;
    private int pendingTicks;

    private MusicClientSelfTest() {
    }

    public static void start(String mode) {
        MinecraftForge.EVENT_BUS.register(new MusicClientSelfTest());
        CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST {} requested", mode);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.state == 99) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (this.pending != null && !this.pending.get()) {
            if (++this.pendingTicks > 6000) {
                fail("a task on the integrated server did not run within 5 minutes");
                this.pending = null;
                finish(mc);
            }
            return;
        }
        this.pending = null;
        if (this.state >= 1 && mc.currentScreen instanceof net.minecraft.client.gui.GuiIngameMenu) {
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

    private void step(Minecraft mc) {
        switch (this.state) {
            case 0:
                if (!(mc.currentScreen instanceof GuiMainMenu)) {
                    return;
                }
                mc.gameSettings.pauseOnLostFocus = false;
                if (!ok(HeightContent.allowed(), "extended height is on (refusal: " + HeightContent.refusal() + ")")) {
                    finish(mc);
                    return;
                }
                ok(ModConfig.music.enabled && ModConfig.music.biomeMusic, "music and biome music are on");
                mc.getSaveLoader().deleteWorldDirectory(WORLD);
                mc.launchIntegratedServer(WORLD, WORLD, new WorldSettings(SEED, GameType.CREATIVE, true, false, HeightContent.worldType()));
                this.state = 1;
                return;
            case 1:
                if (mc.world == null || mc.player == null || mc.getIntegratedServer() == null) {
                    return;
                }
                mc.gameSettings.pauseOnLostFocus = false;
                server(mc, world -> {
                    ok(ModernTerrain.owns(world), "the world has 1.18 terrain");
                    world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
                    world.getGameRules().setOrCreateGameRule("doMobSpawning", "false");
                    BlockPos p = world.getSpawnPoint();
                    find(world, p.getX(), p.getZ());
                });
                this.wait = 100;
                this.state = 2;
                return;
            case 2: {
                if (this.spot >= this.spots.size()) {
                    report();
                    finish(mc);
                    return;
                }
                final int[] s = this.spots.get(this.spot);
                CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST spot {} ({}): {} {} {}", this.spot, this.names.get(this.spot), s[0], s[1], s[2]);
                server(mc, world -> teleport(world, s[0] + 0.5, s[1], s[2] + 0.5));
                this.waited = 0;
                this.wait = 40;
                this.state = 3;
                return;
            }
            case 3: {
                // The chunks around the spot and their biomes.
                int[] s = this.spots.get(this.spot);
                int cx = s[0] >> 4;
                int cz = s[2] >> 4;
                boolean all = Math.abs(mc.player.posX - s[0]) < 2 && Math.abs(mc.player.posZ - s[2]) < 2;
                for (int dx = -RADIUS - 1; all && dx <= RADIUS + 1; dx++) {
                    for (int dz = -RADIUS - 1; all && dz <= RADIUS + 1; dz++) {
                        all = mc.world.getChunkProvider().getLoadedChunk(cx + dx, cz + dz) != null && ClientBiomes.has(cx + dx, cz + dz);
                    }
                }
                if (!all) {
                    if (++this.waited > 1200) {
                        fail(this.names.get(this.spot) + ": the chunks or their biomes did not arrive in a minute (client holds "
                                + ClientBiomes.size() + ")");
                        this.spot++;
                        this.state = 2;
                    }
                    return;
                }
                ok(ClientBiomes.active(mc.world), this.names.get(this.spot) + ": the client has 1.18's biomes");
                this.serverBiomes = null;
                final int x0 = (cx - RADIUS) << 4;
                final int z0 = (cz - RADIUS) << 4;
                server(mc, world -> {
                    int[] out = new int[count()];
                    int i = 0;
                    for (int x = x0; x < x0 + (2 * RADIUS + 1) * 16; x += STEP_XZ) {
                        for (int z = z0; z < z0 + (2 * RADIUS + 1) * 16; z += STEP_XZ) {
                            for (int y = -64; y < 320; y += STEP_Y) {
                                out[i++] = ModernTerrain.biomeAt(world, x, y, z);
                            }
                        }
                    }
                    this.serverBiomes = out;
                });
                this.state = 4;
                return;
            }
            case 4: {
                int[] s = this.spots.get(this.spot);
                String name = this.names.get(this.spot);
                int[] server = this.serverBiomes;
                int x0 = ((s[0] >> 4) - RADIUS) << 4;
                int z0 = ((s[2] >> 4) - RADIUS) << 4;
                int i = 0;
                int idWrong = 0;
                int musicWrong = 0;
                int oldWrong = 0;
                int points = 0;
                Map<MusicRegions.Region, Integer> here = new EnumMap<MusicRegions.Region, Integer>(MusicRegions.Region.class);
                for (int x = x0; x < x0 + (2 * RADIUS + 1) * 16; x += STEP_XZ) {
                    for (int z = z0; z < z0 + (2 * RADIUS + 1) * 16; z += STEP_XZ) {
                        for (int y = -64; y < 320; y += STEP_Y) {
                            int want = server[i++];
                            points++;
                            BlockPos pos = new BlockPos(x, y, z);
                            int got = ClientBiomes.biome(mc.world, x, y, z);
                            MusicRegions.Region expected = MusicRegions.of(want);
                            MusicRegions.Region region = MusicRegions.at(mc.world, pos);
                            if (got != want && idWrong++ < 5) {
                                fail(name + ": biome at " + x + " " + y + " " + z + ": server " + want + ", client " + got);
                            }
                            if (region != expected && musicWrong++ < 5) {
                                fail(name + ": music biome at " + x + " " + y + " " + z + ": server " + expected + ", client " + region);
                            }
                            if (MusicRegions.CAVES.at(mc.world, pos) != expected) {
                                oldWrong++;
                            }
                            here.merge(expected, 1, Integer::sum);
                            this.seen.merge(expected, 1, Integer::sum);
                        }
                    }
                }
                eq(0, idWrong, name + ": points where the client's 1.18 biome differs");
                eq(0, musicWrong, name + ": points where the client's music biome differs");
                CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST {}: {} points, music biomes {}; the 1.17 regions would differ at {}",
                        name, points, here, oldWrong);
                // At the player: what the music step asks.
                BlockPos feet = new BlockPos(mc.player);
                final int[] atFeet = {Integer.MIN_VALUE};
                this.serverBiomes = null;
                this.feet = feet;
                server(mc, world -> atFeet[0] = ModernTerrain.biomeAt(world, feet.getX(), feet.getY(), feet.getZ()));
                this.feetBiome = atFeet;
                this.state = 5;
                return;
            }
            case 5: {
                eq(MusicRegions.of(this.feetBiome[0]), MusicRegions.at(mc.world, this.feet),
                        this.names.get(this.spot) + ": the music biome at the player's feet " + this.feet);
                if (this.spot > 0) {
                    MusicRegions.Region want = this.spot == 1 ? MusicRegions.Region.LUSH_CAVES
                            : this.spot == 2 ? MusicRegions.Region.DRIPSTONE_CAVES : null;
                    if (want != null) {
                        eq(want, MusicRegions.at(mc.world, this.feet), this.names.get(this.spot) + ": the spot's music biome");
                    } else {
                        ok(MusicRegions.at(mc.world, this.feet).ordinal() >= MusicRegions.Region.MEADOW.ordinal(),
                                this.names.get(this.spot) + ": a mountain music biome at the spot, got " + MusicRegions.at(mc.world, this.feet));
                    }
                }
                this.spot++;
                this.state = 2;
                return;
            }
            default:
        }
    }

    private BlockPos feet;
    private int[] feetBiome;

    private static int count() {
        int n = (2 * RADIUS + 1) * 16;
        int xz = (n + STEP_XZ - 1) / STEP_XZ;
        int y = (384 + STEP_Y - 1) / STEP_Y;
        return xz * xz * y;
    }

    /**
     * Server thread: spawn, then the nearest lush caves and dripstone caves (by 1.18's biome there, y -40 to 40) and
     * the nearest mountain biome at the surface, searched outward every 32 blocks (the biome source, no chunks).
     */
    private void find(WorldServer world, int sx, int sz) {
        int y0 = world.getHeight(sx, sz) + 1;
        this.spots.add(new int[] {sx, y0, sz});
        this.names.add("spawn");
        int[] lush = null;
        int[] drip = null;
        int[] mountain = null;
        for (int r = 32; r <= 4096 && (lush == null || drip == null || mountain == null); r += 32) {
            for (int a = -r; a <= r; a += 32) {
                int[][] ring = {{sx + a, sz - r}, {sx + a, sz + r}, {sx - r, sz + a}, {sx + r, sz + a}};
                for (int[] c : ring) {
                    for (int y = -40; y <= 40 && (lush == null || drip == null); y += 8) {
                        int b = ModernTerrain.biomeAt(world, c[0], y, c[1]);
                        if (lush == null && b == Biomes.LUSH_CAVES && solidAround(world, c[0], y, c[1], Biomes.LUSH_CAVES)) {
                            lush = new int[] {c[0], y, c[1]};
                        } else if (drip == null && b == Biomes.DRIPSTONE_CAVES && solidAround(world, c[0], y, c[1], Biomes.DRIPSTONE_CAVES)) {
                            drip = new int[] {c[0], y, c[1]};
                        }
                    }
                    if (mountain == null) {
                        for (int y = 120; y <= 240; y += 8) {
                            int b = ModernTerrain.biomeAt(world, c[0], y, c[1]);
                            if (b >= Biomes.MEADOW && b <= Biomes.STONY_PEAKS
                                    && solidAround(world, c[0], y, c[1], b)) {
                                mountain = new int[] {c[0], y, c[1]};
                                break;
                            }
                        }
                    }
                }
            }
        }
        add(lush, "lush caves");
        add(drip, "dripstone caves");
        add(mountain, "mountains");
    }

    /** The biome holds 8 blocks around, so the zoom's jitter keeps the spot in it. */
    private static boolean solidAround(WorldServer world, int x, int y, int z, int biome) {
        for (int d = -8; d <= 8; d += 8) {
            if (ModernTerrain.biomeAt(world, x + d, y, z) != biome || ModernTerrain.biomeAt(world, x, y, z + d) != biome
                    || ModernTerrain.biomeAt(world, x, y + d / 2, z) != biome) {
                return false;
            }
        }
        return true;
    }

    private void add(int[] spot, String name) {
        if (ok(spot != null, "found a " + name + " spot within 4096 blocks")) {
            this.spots.add(spot);
            this.names.add(name);
        }
    }

    private void report() {
        CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST music biomes over all points: {}", this.seen);
        for (MusicRegions.Region r : new MusicRegions.Region[] {MusicRegions.Region.LUSH_CAVES, MusicRegions.Region.DRIPSTONE_CAVES}) {
            ok(this.seen.containsKey(r), "the checked points include " + r);
        }
        boolean mountain = false;
        for (MusicRegions.Region r : this.seen.keySet()) {
            mountain |= r.ordinal() >= MusicRegions.Region.MEADOW.ordinal();
        }
        ok(mountain, "the checked points include a mountain music biome");
    }

    private interface ServerTask {
        void run(WorldServer world);
    }

    private void server(Minecraft mc, final ServerTask task) {
        final IntegratedServer server = mc.getIntegratedServer();
        final java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
        this.pending = done;
        this.pendingTicks = 0;
        server.addScheduledTask(() -> {
            try {
                task.run(server.getWorld(0));
            } catch (Throwable t) {
                CavesAndCliffsBackport.LOGGER.error("CLIENT SELFTEST server task threw", t);
                fail("server task threw " + t);
            } finally {
                done.set(true);
            }
        });
    }

    private static void teleport(WorldServer world, double x, double y, double z) {
        for (EntityPlayerMP p : world.getMinecraftServer().getPlayerList().getPlayers()) {
            p.capabilities.isFlying = true;
            p.capabilities.disableDamage = true;
            p.sendPlayerAbilities();
            p.connection.setPlayerLocation(x, y, z, 0, 0);
        }
    }

    private synchronized boolean ok(boolean condition, String what) {
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

    private synchronized void fail(String what) {
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
