package com.exiledradio.cavesandcliffsbackport.spawning;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 1.18's local mob cap, {@code LocalMobCapCalculator} of 1.18.2: besides the world's cap, every player has a cap of
 * their own for each mob category, the category's number (70 monsters, 10 animals, 15 ambient, 5 water creatures).
 * A mob counts toward the cap of each player within 128 blocks (horizontally) of its chunk's center, and a chunk is
 * spawned in only while one of the players within 128 blocks of its center has room in that category. A chunk with
 * no player that close is not spawned in at all. Spectators neither count nor are counted for.
 *
 * <p>As in 1.18.2's {@code NaturalSpawner}, the counts are taken when a tick's spawning starts (here: the first spawn
 * list the tick asks for), from the loaded mobs that the world's own cap counts (1.12.2's {@code countEntities(type,
 * true)}: not persistent), and every mob that joins the world in that tick is added. Each chunk is decided once per
 * category and tick, before its groups are spawned, so a group already started finishes, as in 1.18.2.
 *
 * <p>1.12.2's {@code WorldEntitySpawner} asks {@code WorldServer.getSpawnListEntryForTypeAt} and {@code
 * canCreatureTypeSpawnHere} for what may spawn at a place, and both fire Forge's {@code PotentialSpawns}; a chunk
 * whose players are all full gets an empty list, so nothing spawns there. The world's own cap, the spawn heights
 * (the extended-height patch of {@code WorldEntitySpawner}) and everything else of the spawner stay as they are.
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class LocalMobCap {

    /** 1.18.2's {@code ChunkMap.playerIsCloseEnoughForSpawning}: within 128 blocks of the chunk's center. */
    public static final double RANGE_SQ = 16384.0D;

    private static final Map<World, State> STATES = new WeakHashMap<World, State>();

    private LocalMobCap() {
    }

    /** One tick's counts in one world. */
    private static final class State {
        final long tick;
        final EnumCreatureType[] types = EnumCreatureType.values();
        final List<EntityPlayer> players = new ArrayList<EntityPlayer>();
        final Map<EntityPlayer, int[]> counts = new IdentityHashMap<EntityPlayer, int[]>();
        final Map<Long, List<EntityPlayer>> near = new HashMap<Long, List<EntityPlayer>>();
        final Map<Long, Boolean>[] decided;

        @SuppressWarnings("unchecked")
        State(WorldServer world) {
            this.tick = world.getTotalWorldTime();
            this.decided = new Map[this.types.length];
            for (int i = 0; i < this.types.length; i++) {
                this.decided[i] = new HashMap<Long, Boolean>();
            }
            for (EntityPlayer p : world.playerEntities) {
                if (!p.isSpectator()) {
                    this.players.add(p);
                }
            }
            for (Entity e : world.loadedEntityList) {
                this.add(e);
            }
        }

        /** 1.18.2's {@code addMob}: the mob counts for every player near its chunk. */
        void add(Entity e) {
            if (!(e instanceof EntityLiving) || e.isDead || this.players.isEmpty()) {
                return;
            }
            List<EntityPlayer> close = null;
            for (int i = 0; i < this.types.length; i++) {
                if (!e.isCreatureType(this.types[i], true)) {
                    continue;
                }
                if (close == null) {
                    close = this.near(MathHelper.floor(e.posX) >> 4, MathHelper.floor(e.posZ) >> 4);
                }
                for (EntityPlayer p : close) {
                    int[] c = this.counts.get(p);
                    if (c == null) {
                        c = new int[this.types.length];
                        this.counts.put(p, c);
                    }
                    c[i]++;
                }
            }
        }

        /** 1.18.2's {@code getPlayersCloseForSpawning}. */
        List<EntityPlayer> near(int cx, int cz) {
            Long key = key(cx, cz);
            List<EntityPlayer> out = this.near.get(key);
            if (out == null) {
                double x = (cx << 4) + 8;
                double z = (cz << 4) + 8;
                out = Collections.emptyList();
                for (EntityPlayer p : this.players) {
                    double dx = x - p.posX;
                    double dz = z - p.posZ;
                    if (dx * dx + dz * dz < RANGE_SQ) {
                        if (out.isEmpty()) {
                            out = new ArrayList<EntityPlayer>(2);
                        }
                        out.add(p);
                    }
                }
                this.near.put(key, out);
            }
            return out;
        }

        /** 1.18.2's {@code canSpawn}: one player near the chunk with room in the category is enough. */
        boolean canSpawn(EnumCreatureType type, int cx, int cz) {
            int i = type.ordinal();
            if (i >= this.types.length) {
                return true;
            }
            Long key = key(cx, cz);
            Boolean known = this.decided[i].get(key);
            if (known == null) {
                known = false;
                for (EntityPlayer p : this.near(cx, cz)) {
                    int[] c = this.counts.get(p);
                    if (c == null || c[i] < type.getMaxNumberOfCreature()) {
                        known = true;
                        break;
                    }
                }
                this.decided[i].put(key, known);
            }
            return known;
        }
    }

    private static Long key(int cx, int cz) {
        return (long) cx & 0xFFFFFFFFL | ((long) cz & 0xFFFFFFFFL) << 32;
    }

    private static State state(WorldServer world) {
        State s = STATES.get(world);
        if (s == null || s.tick != world.getTotalWorldTime()) {
            s = new State(world);
            STATES.put(world, s);
        }
        return s;
    }

    /** @return whether 1.18's local cap lets this category spawn in this chunk now */
    public static boolean canSpawn(WorldServer world, EnumCreatureType type, int chunkX, int chunkZ) {
        return state(world).canSpawn(type, chunkX, chunkZ);
    }

    /** @return how many mobs of a category count toward a player's own cap now */
    public static int count(WorldServer world, EntityPlayer player, EnumCreatureType type) {
        int[] c = state(world).counts.get(player);
        return c == null ? 0 : c[type.ordinal()];
    }

    /** Forgets this tick's counts, so they are taken again (for the self-test, which changes them within a tick). */
    public static void forget(World world) {
        STATES.remove(world);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void potentialSpawns(WorldEvent.PotentialSpawns event) {
        if (!ModConfig.changes.localMobCap || !(event.getWorld() instanceof WorldServer) || event.getList() == null
                || event.getList().isEmpty()) {
            return;
        }
        WorldServer world = (WorldServer) event.getWorld();
        if (!canSpawn(world, event.getType(), event.getPos().getX() >> 4, event.getPos().getZ() >> 4)) {
            event.setCanceled(true);
        }
    }

    /** A mob that joins while a tick's counts are kept (one the spawner just made) counts at once. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void join(EntityJoinWorldEvent event) {
        World world = event.getWorld();
        if (world.isRemote || !(event.getEntity() instanceof EntityLiving)) {
            return;
        }
        State s = STATES.get(world);
        if (s != null && s.tick == world.getTotalWorldTime()) {
            s.add(event.getEntity());
        }
    }
}
