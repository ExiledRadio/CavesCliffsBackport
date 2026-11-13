package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.height.Heights;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A modpack probe, not a test: {@code -Dcavesandcliffsbackport.selftest=packprobe} on a pack's server (a jar built
 * with {@code -Pselftest}). After the world loads it puts a creative, invulnerable "player" (a real
 * {@link EntityPlayerMP} on a connection that drops every packet, ticked as a connected one is) at each spot of
 * {@code -Dcavesandcliffsbackport.packprobe.spots=x,z;x,z} (default 0,0), on the surface, keeps it night, and after
 * {@code -Dcavesandcliffsbackport.packprobe.minutes} (default 6) writes {@code packprobe-report.txt}: every living
 * mob within 128 blocks of a spot by id and height band (below 0, 0-62, 63 and up), how many carry Infernal Mobs'
 * tag, and every loaded entity in the world; then stops the server. Natural spawning needs a player that is not a
 * spectator and whose chunks were sent, which is why it is a real player and not a FakePlayer.
 */
public final class PackProbe {

    private final List<BlockPos> spots = new ArrayList<BlockPos>();
    private final List<EntityPlayerMP> players = new ArrayList<EntityPlayerMP>();
    private final int minutes;
    private int ticks;
    private final StringBuilder progress = new StringBuilder();

    private PackProbe() {
        String s = System.getProperty(CavesAndCliffsBackport.MOD_ID + ".packprobe.spots", "0,0");
        for (String p : s.split(";")) {
            String[] xz = p.trim().split(",");
            this.spots.add(new BlockPos(Integer.parseInt(xz[0].trim()), 0, Integer.parseInt(xz[1].trim())));
        }
        this.minutes = Integer.getInteger(CavesAndCliffsBackport.MOD_ID + ".packprobe.minutes", 6);
    }

    static void start() {
        MinecraftForge.EVENT_BUS.register(new PackProbe());
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
            if (this.ticks == 40) {
                place(world);
            }
            if (this.ticks > 40) {
                if (world.getWorldTime() % 24000 < 14000 || world.getWorldTime() % 24000 > 22000) {
                    world.setWorldTime(world.getWorldTime() - world.getWorldTime() % 24000 + 15000);
                }
                for (EntityPlayerMP p : this.players) {
                    try {
                        p.onUpdateEntity();
                    } catch (Throwable t) {
                        if (this.ticks % 1200 == 0) {
                            CavesAndCliffsBackport.LOGGER.warn("PACKPROBE player tick threw", t);
                        }
                    }
                }
                if ((this.ticks - 40) % 1200 == 0) {
                    String line = "PACKPROBE " + (this.ticks - 40) / 1200 + " min: " + world.loadedEntityList.size()
                            + " entities loaded, " + living(world) + " living";
                    CavesAndCliffsBackport.LOGGER.info(line);
                    this.progress.append(line).append('\n');
                }
            }
            if (this.ticks == 40 + this.minutes * 1200) {
                report(server, world);
                server.initiateShutdown();
            }
        } catch (Throwable t) {
            CavesAndCliffsBackport.LOGGER.error("PACKPROBE threw", t);
            server.initiateShutdown();
        }
    }

    private static int living(WorldServer world) {
        int n = 0;
        for (Entity e : world.loadedEntityList) {
            if (e instanceof EntityLiving) {
                n++;
            }
        }
        return n;
    }

    private void place(WorldServer world) {
        world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
        int i = 0;
        for (BlockPos spot : this.spots) {
            BlockPos top = world.getTopSolidOrLiquidBlock(spot);
            EntityPlayerMP player = Players.real(world, "probe" + i++);
            NetworkManager network = new NetworkManager(EnumPacketDirection.CLIENTBOUND) {
                @Override
                public void sendPacket(Packet<?> packet) {
                }

                @Override
                public void sendPacket(Packet<?> packet, GenericFutureListener<? extends Future<? super Void>> listener,
                                       GenericFutureListener<? extends Future<? super Void>>... listeners) {
                }
            };
            // A channel with no FML dispatcher: mods' FML packets to this player select no network and are dropped
            // (with no channel at all, FMLOutboundHandler throws for every packet).
            ReflectionHelper.setPrivateValue(NetworkManager.class, network, new EmbeddedChannel(), "channel", "field_150746_k");
            new NetHandlerPlayServer(world.getMinecraftServer(), network, player);
            player.setPositionAndRotation(top.getX() + 0.5, top.getY() + 1, top.getZ() + 0.5, 0, 0);
            player.setGameType(GameType.CREATIVE);
            player.capabilities.disableDamage = true;
            world.spawnEntity(player);
            world.getPlayerChunkMap().addPlayer(player);
            this.players.add(player);
            CavesAndCliffsBackport.LOGGER.info("PACKPROBE player at {} (world y {}..{})", player.getPosition(),
                    Heights.minY(world), Heights.maxY(world));
        }
    }

    private void report(MinecraftServer server, WorldServer world) throws Exception {
        PrintWriter out = new PrintWriter(server.getFile("packprobe-report.txt"), "UTF-8");
        out.println("world y " + Heights.minY(world) + ".." + Heights.maxY(world) + ", " + this.minutes + " min of night");
        out.print(this.progress);
        for (EntityPlayerMP p : this.players) {
            Map<String, int[]> near = new TreeMap<String, int[]>();
            int infernal = 0;
            int total = 0;
            for (Entity e : world.loadedEntityList) {
                if (!(e instanceof EntityLiving) || e.getDistanceSq(p.posX, e.posY, p.posZ) > 128 * 128) {
                    continue;
                }
                ResourceLocation id = EntityList.getKey(e);
                int[] c = near.get(String.valueOf(id));
                if (c == null) {
                    near.put(String.valueOf(id), c = new int[4]);
                }
                c[e.posY < 0 ? 0 : e.posY < 63 ? 1 : 2]++;
                if (e.getEntityData().hasKey("InfernalMobsMod")) {
                    c[3]++;
                    infernal++;
                }
                total++;
            }
            out.println("== near " + p.getPosition() + ": " + total + " mobs, " + infernal + " infernal"
                    + "  (id: below0 0-62 63+ infernal)");
            for (Map.Entry<String, int[]> e : near.entrySet()) {
                int[] c = e.getValue();
                out.println("  " + e.getKey() + ": " + c[0] + " " + c[1] + " " + c[2] + " " + c[3]);
            }
        }
        Map<String, Integer> all = new TreeMap<String, Integer>();
        for (Entity e : world.loadedEntityList) {
            String id = String.valueOf(EntityList.getKey(e));
            Integer n = all.get(id);
            all.put(id, n == null ? 1 : n + 1);
        }
        out.println("== every loaded entity: " + all);
        out.close();
        CavesAndCliffsBackport.LOGGER.info("PACKPROBE DONE");
    }
}
