package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.height.Heights;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.List;
import java.util.Set;

/**
 * {@code hybridrespawn} ({@code tools/height/hybrid.sh}, a dedicated server whose overworld is extended though its
 * world type is another mod's): the client learns that height from {@code height.HeightPacket}, sent when the player
 * joins the overworld, and every chunk it got before then must reach it again afterwards. Over real server ticks, with
 * players on connections that drop every packet: a witness logs in at the spawn and stays (so the chunks there are
 * sent already, and a respawn sends them to the respawning player at once, before it joins the world); then a player
 * logs in, dies in the Nether and respawns, then dies in the End and respawns. The order of the server's chunk sends,
 * unloads and the join (HybridSelfTest.Order) is checked after each; the report is {@code selftest-report.txt}.
 */
final class RespawnProbe {

    private int ticks;
    private final Check check;
    private final HybridSelfTest.Order order = new HybridSelfTest.Order("Testrespawner");
    private EntityPlayerMP witness;
    private EntityPlayerMP player;
    private int mark;
    private String from;

    private RespawnProbe(Check check) {
        this.check = check;
    }

    static void start() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        RespawnProbe probe = new RespawnProbe(new Check(server.getWorld(0)));
        probe.check.section("hybridrespawn");
        MinecraftForge.EVENT_BUS.register(probe);
        MinecraftForge.EVENT_BUS.register(probe.order);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        WorldServer world = server.getWorld(0);
        this.ticks++;
        try {
            switch (this.ticks) {
                case 40:
                    check.ok(Heights.isExtended(world), "the overworld is extended");
                    this.witness = HybridSelfTest.connect(server, world, "Testwitness");
                    break;
                case 240:
                    HybridSelfTest.info("witness at " + this.witness.getPosition() + ": "
                            + sentAround(world, this.witness) + " of the 5x5 chunks around it sent");
                    this.mark = this.order.events.size();
                    this.player = HybridSelfTest.connect(server, world, "Testrespawner");
                    break;
                case 400:
                    verify("login");
                    die(-1);
                    break;
                case 420:
                    respawn(server, "the Nether");
                    break;
                case 620:
                    verify("respawn from " + this.from);
                    die(1);
                    break;
                case 640:
                    respawn(server, "the End");
                    break;
                case 840:
                    verify("respawn from " + this.from);
                    server.getPlayerList().playerLoggedOut(this.player);
                    server.getPlayerList().playerLoggedOut(this.witness);
                    MinecraftForge.EVENT_BUS.unregister(this.order);
                    MinecraftForge.EVENT_BUS.unregister(this);
                    check.report(server.getFile("selftest-report.txt"));
                    server.initiateShutdown();
                    break;
                default:
            }
        } catch (Throwable t) {
            CavesAndCliffsBackport.LOGGER.error("SELFTEST hybridrespawn threw", t);
            check.fail("threw " + t);
            MinecraftForge.EVENT_BUS.unregister(this.order);
            MinecraftForge.EVENT_BUS.unregister(this);
            check.report(server.getFile("selftest-report.txt"));
            server.initiateShutdown();
        }
    }

    private static int sentAround(WorldServer world, EntityPlayerMP p) {
        int cx = (int) Math.floor(p.posX) >> 4;
        int cz = (int) Math.floor(p.posZ) >> 4;
        int n = 0;
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                net.minecraft.server.management.PlayerChunkMapEntry e = world.getPlayerChunkMap().getEntry(x, z);
                n += e != null && e.isSentToPlayers() ? 1 : 0;
            }
        }
        return n;
    }

    private void die(int dim) {
        this.player.changeDimension(dim, (to, entity, yaw) -> {
            to.setBlockState(new BlockPos(0, 64, 0), Blocks.OBSIDIAN.getDefaultState(), 2);
            entity.setLocationAndAngles(0.5, 65, 0.5, 0F, 0F);
        });
        check.eq(dim, this.player.dimension, "the player is in dimension " + dim);
    }

    private void respawn(MinecraftServer server, String from) {
        this.from = from;
        this.player.setHealth(0F);
        this.player.onDeath(net.minecraft.util.DamageSource.OUT_OF_WORLD);
        this.mark = this.order.events.size();
        this.player = server.getPlayerList().recreatePlayerEntity(this.player, 0, false);
        check.eq(0, this.player.dimension, "respawned into the overworld from " + from);
    }

    /** Since the mark: the join happened, chunks were sent, and every one sent before the join went again after it. */
    private void verify(String what) {
        List<String> after = this.order.events.subList(this.mark, this.order.events.size());
        int sent = 0;
        int early = 0;
        boolean joined = false;
        for (String s : after) {
            joined |= s.equals("join0");
            if (s.startsWith("+")) {
                sent++;
                early += joined ? 0 : 1;
            }
        }
        HybridSelfTest.info(what + ": " + after.size() + " events, " + sent + " chunk sends, " + early
                + " before the player joined the overworld (" + (after.contains("join0") ? "joined at event "
                + after.indexOf("join0") : "never joined") + ")");
        check.ok(after.contains("join0"), what + ": the player joins the overworld");
        check.ok(sent > 20, what + ": the chunks around the player are sent (" + sent + ")");
        if (what.equals("login")) {
            // The notice goes as the connection starts, before the join packet and any chunk.
            int told = after.indexOf("told");
            int firstChunk = -1;
            for (int i = 0; i < after.size(); i++) {
                if (after.get(i).startsWith("+")) {
                    firstChunk = i;
                    break;
                }
            }
            check.ok(told >= 0 && told < after.indexOf("join0") && (firstChunk < 0 || told < firstChunk),
                    "login: the client is told the overworld's height before it joins and before any chunk (told at event "
                            + told + ", join at " + after.indexOf("join0") + ", first chunk at " + firstChunk + ")");
        } else {
            // Chunks before the join: the respawn the connection-time notice is for (a client told only on joining
            // the world would read them as a 0-255 world's).
            check.ok(early > 0, what + ": chunks went before the player joined the overworld: " + early);
            check.ok(!after.contains("told"), what + ": no second notice (one per connection)");
        }
    }
}
