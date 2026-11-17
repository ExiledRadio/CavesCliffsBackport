package com.exiledradio.cavesandcliffsbackport.changes;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerPickupXpEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.17's experience orbs ({@code ExperienceOrb} of 1.18.2):
 * <ul>
 *   <li><b>Count.</b> An orb stands for a number of orbs of its value. A player picks it up one at a time, every 2
 *       ticks as before, and it is gone when the last is taken.</li>
 *   <li><b>Merging when made.</b> A new orb ({@code ExperienceOrb.award}) whose value an orb within half a block
 *       already has joins that one instead (one random group of the 40 below is tried), and that orb's age starts
 *       again.</li>
 *   <li><b>Merging while lying around.</b> Once a second (when {@code tickCount % 20 == 1}), an orb takes in the orbs
 *       of its value touching it (its box grown by half a block) whose entity id is in its group (the same id modulo
 *       40); the merged orb keeps the younger age.</li>
 *   <li><b>Dead players.</b> An orb stops following a player who has died; it follows the nearest living one within
 *       8 blocks instead, if any.</li>
 * </ul>
 *
 * <h2>How, in 1.12.2</h2>
 * The count is kept in the orb's Forge data ({@value #COUNT}), so it is saved. A pickup of an orb that stands for more
 * than one leaves an orb behind in its place with one fewer (made before 1.12.2's pickup changes the orb's value for
 * Mending); the orb taken is the one 1.12.2 picks up, so its sound, its flight to the player and every mod's pickup
 * handling stay. 1.12.2's orb looks for the nearest player within 8 blocks, dead or alive, while it moves; with a dead
 * player that close, the orb is given the nearest living player before it moves and its search is put off for that
 * tick, then let go again.
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class ExperienceOrbs {

    /** The orb's count in its Forge data (1.17's {@code Count}). */
    public static final String COUNT = "cavesandcliffsbackport:Count";

    private static final Field CLOSEST_PLAYER = ObfuscationReflectionHelper.findField(EntityXPOrb.class, "field_80001_f");
    private static final Field TARGET_COLOR = ObfuscationReflectionHelper.findField(EntityXPOrb.class, "field_80002_g");

    /** Orbs whose search this tick was put off, with the value to give back. */
    private static final Map<EntityXPOrb, Integer> HELD = new IdentityHashMap<EntityXPOrb, Integer>();

    /** While an orb left behind by a pickup joins: it does not merge into the orb being picked up. */
    private static boolean leavingBehind;

    private ExperienceOrbs() {
    }

    public static int count(EntityXPOrb orb) {
        NBTTagCompound data = orb.getEntityData();
        return data.hasKey(COUNT) ? Math.max(data.getInteger(COUNT), 1) : 1;
    }

    public static void setCount(EntityXPOrb orb, int count) {
        if (count <= 1) {
            orb.getEntityData().removeTag(COUNT);
        } else {
            orb.getEntityData().setInteger(COUNT, count);
        }
    }

    /** 1.18.2's {@code canMerge(orb, id, value)}. */
    private static boolean canMerge(EntityXPOrb orb, int id, int value) {
        return !orb.isDead && (orb.getEntityId() - id) % 40 == 0 && orb.xpValue == value;
    }

    /** {@code ExperienceOrb.tryMergeToExisting}: a newly made orb joins an orb within half a block. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void join(EntityJoinWorldEvent event) {
        if (!ModConfig.changes.experienceOrbs || leavingBehind || !(event.getEntity() instanceof EntityXPOrb)) {
            return;
        }
        World world = event.getWorld();
        EntityXPOrb orb = (EntityXPOrb) event.getEntity();
        // An orb read from a chunk is in it already; one being spawned is not yet.
        if (world.isRemote || orb.addedToChunk) {
            return;
        }
        if (mergeToExisting(world, orb)) {
            event.setCanceled(true);
        }
    }

    /** @return whether the new orb went into one already there (it must then not be spawned) */
    public static boolean mergeToExisting(World world, EntityXPOrb orb) {
        int group = world.rand.nextInt(40);
        int value = orb.xpValue;
        AxisAlignedBB box = new AxisAlignedBB(orb.posX - 0.5D, orb.posY - 0.5D, orb.posZ - 0.5D,
                orb.posX + 0.5D, orb.posY + 0.5D, orb.posZ + 0.5D);
        List<EntityXPOrb> list = world.getEntitiesWithinAABB(EntityXPOrb.class, box, o -> o != orb && canMerge(o, group, value));
        if (list.isEmpty()) {
            return false;
        }
        EntityXPOrb into = list.get(0);
        setCount(into, count(into) + count(orb));
        into.xpOrbAge = 0;
        return true;
    }

    /** {@code ExperienceOrb.merge}. */
    private static void merge(EntityXPOrb into, EntityXPOrb other) {
        setCount(into, count(into) + count(other));
        into.xpOrbAge = Math.min(into.xpOrbAge, other.xpOrbAge);
        other.setDead();
    }

    /**
     * {@code ExperienceOrb.playerTouch}: an orb that stands for more is not used up. 1.12.2 picks up the orb as it
     * always does; one with one fewer is left in its place.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void pickup(PlayerPickupXpEvent event) {
        EntityXPOrb orb = event.getOrb();
        if (!ModConfig.changes.experienceOrbs || orb.world.isRemote) {
            return;
        }
        int count = count(orb);
        if (count <= 1) {
            return;
        }
        setCount(orb, 1);
        EntityXPOrb rest = new EntityXPOrb(orb.world, orb.posX, orb.posY, orb.posZ, orb.xpValue);
        rest.motionX = orb.motionX;
        rest.motionY = orb.motionY;
        rest.motionZ = orb.motionZ;
        rest.rotationYaw = orb.rotationYaw;
        rest.xpOrbAge = orb.xpOrbAge;
        rest.xpColor = orb.xpColor;
        rest.delayBeforeCanPickup = orb.delayBeforeCanPickup;
        setCount(rest, count - 1);
        leavingBehind = true;
        try {
            orb.world.spawnEntity(rest);
        } finally {
            leavingBehind = false;
        }
    }

    @SubscribeEvent
    public static void worldTick(TickEvent.WorldTickEvent event) {
        World world = event.world;
        if (world.isRemote) {
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            if (ModConfig.changes.experienceOrbs) {
                awayFromDead(world);
            }
            return;
        }
        letGo(world);
        if (!ModConfig.changes.experienceOrbs) {
            return;
        }
        List<Entity> entities = world.loadedEntityList;
        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            if (e instanceof EntityXPOrb && !e.isDead && e.ticksExisted % 20 == 1) {
                scan((EntityXPOrb) e);
            }
        }
    }

    /** {@code ExperienceOrb.scanForEntities}' merging. */
    private static void scan(EntityXPOrb orb) {
        int id = orb.getEntityId();
        int value = orb.xpValue;
        for (EntityXPOrb other : orb.world.getEntitiesWithinAABB(EntityXPOrb.class, orb.getEntityBoundingBox().grow(0.5D),
                o -> o != orb && canMerge(o, id, value))) {
            merge(orb, other);
        }
    }

    /**
     * The orbs within 8 blocks of a dead player (1.12.2's orbs follow only players within 8 blocks): each follows the
     * nearest living player within 8 blocks, or none, and does not look again while it moves this tick.
     */
    private static void awayFromDead(World world) {
        List<EntityPlayer> dead = null;
        for (EntityPlayer p : world.playerEntities) {
            if (!p.isSpectator() && (p.isDead || p.getHealth() <= 0.0F)) {
                if (dead == null) {
                    dead = new ArrayList<EntityPlayer>(1);
                }
                dead.add(p);
            }
        }
        if (dead == null) {
            return;
        }
        try {
            for (EntityPlayer p : dead) {
                for (EntityXPOrb orb : world.getEntitiesWithinAABB(EntityXPOrb.class, p.getEntityBoundingBox().grow(8.5D))) {
                    if (HELD.containsKey(orb) || orb.isDead) {
                        continue;
                    }
                    HELD.put(orb, TARGET_COLOR.getInt(orb));
                    CLOSEST_PLAYER.set(orb, nearestLiving(world, orb));
                    TARGET_COLOR.setInt(orb, Integer.MAX_VALUE);
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The orbs held this tick look for players again from the next tick on, as if they had looked this tick. */
    private static void letGo(World world) {
        if (HELD.isEmpty()) {
            return;
        }
        try {
            for (Map.Entry<EntityXPOrb, Integer> e : HELD.entrySet()) {
                EntityXPOrb orb = e.getKey();
                if (orb.world == world) {
                    // 1.12.2 sets the search's mark to the orb's color when it looks; the color has gone up by one since.
                    TARGET_COLOR.setInt(orb, orb.xpColor - 1);
                }
            }
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
        HELD.keySet().removeIf(orb -> orb.world == world);
    }

    /** 1.12.2's {@code getClosestPlayerToEntity(orb, 8)}, without spectators and players who have died. */
    public static EntityPlayer nearestLiving(World world, Entity orb) {
        EntityPlayer best = null;
        double bestSq = 64.0D;
        for (EntityPlayer p : world.playerEntities) {
            if (p.isSpectator() || p.isDead || p.getHealth() <= 0.0F) {
                continue;
            }
            double d = p.getDistanceSq(orb.posX, orb.posY, orb.posZ);
            if (d < bestSq) {
                bestSq = d;
                best = p;
            }
        }
        return best;
    }

    /** The player an orb follows (1.12.2's private {@code closestPlayer}), for the self-test. */
    public static EntityPlayer following(EntityXPOrb orb) {
        try {
            return (EntityPlayer) CLOSEST_PLAYER.get(orb);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
