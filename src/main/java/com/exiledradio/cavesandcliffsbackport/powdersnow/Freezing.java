package com.exiledradio.cavesandcliffsbackport.powdersnow;

import com.exiledradio.backportcore.freeze.FreezeTicks;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.Owners;
import com.exiledradio.cavesandcliffsbackport.network.NetworkHandler;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityEndermite;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.passive.EntityRabbit;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Powder snow and the entities in it. Freezing itself is Backport Core's freeze ticks (Core 1.5.0, {@code freeze.FreezeTicks}:
 * the counter, the damage, the slowdown, the frost overlay and the shaking of a frozen mob, shared with the family):
 * powder snow holds an entity each tick it touches it ({@link #markInSnow}, {@code FreezeTicks.markInFreezingBlock}),
 * which is 1.17's {@code isInPowderSnow} and the freeze tick it adds. What stays here is powder snow's own:
 *
 * <ul>
 *   <li>whether powder snow touched an entity this tick ({@link #inSnow}), for parrots, climbing and skeletons;</li>
 *   <li>climbing out of it as up a ladder, and the climb-on-top goal of mobs that walk on it;</li>
 *   <li>skeletons turning into strays ({@link StrayConversion}) and their shaking while they do, sent to watchers
 *       ({@link PacketFreeze}).</li>
 * </ul>
 *
 * <h2>Where the state lives</h2>
 * In the compound {@value #TAG} of the entity's Forge data: {@code Seen} (the world time powder snow last touched it),
 * for skeletons {@code SnowTime} and {@code Convert}, and {@code Sent}/{@code Flags} for the shaking. The compound exists
 * only while one of them matters, and an entity that never met powder snow is never given Forge data. A freeze count
 * saved by 1.1.7 and older ({@code Ticks} here) is not read: such an entity thaws.
 */
public final class Freezing {

    public static final String TAG = "CCBFreeze";
    public static final int FREEZE_TICKS = FreezeTicks.REQUIRED;
    public static final int SKELETON_SNOW_TICKS = 140;
    public static final int SKELETON_CONVERT_TICKS = 300;
    public static final String GAME_RULE = FreezeTicks.GAME_RULE;

    /** Java's {@code freeze} damage, Core's. */
    public static final DamageSource FREEZE = FreezeTicks.FREEZE;
    /** The slowdown's modifier, Core's. */
    public static final UUID SLOW = FreezeTicks.SLOW;

    static final int FLAG_SHAKING = 2;

    /** Forge's per-entity data, null until something asks for it. */
    private static final Field DATA = ReflectionHelper.findField(Entity.class, "customEntityData");
    private static final Field JUMPING = ReflectionHelper.findField(EntityLivingBase.class, "isJumping", "field_70703_bu");

    private static final Ids WALKERS = new Ids();

    private Freezing() {
    }

    // ---- state -------------------------------------------------------------------------------

    /** @return the entity's powder snow data, or null if it has none; never creates any */
    @Nullable
    static NBTTagCompound peek(Entity entity) {
        try {
            NBTTagCompound data = (NBTTagCompound) DATA.get(entity);
            return data != null && data.hasKey(TAG, 10) ? data.getCompoundTag(TAG) : null;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static NBTTagCompound state(Entity entity) {
        NBTTagCompound data = entity.getEntityData();
        if (!data.hasKey(TAG, 10)) {
            data.setTag(TAG, new NBTTagCompound());
        }
        return data.getCompoundTag(TAG);
    }

    /** Powder snow touched this entity this tick: it is held, and freezes while {@code powderSnow.freezing} is on. */
    public static void markInSnow(EntityLivingBase entity) {
        NBTTagCompound f = state(entity);
        long now = entity.world.getTotalWorldTime();
        // Once a tick, however many powder snow blocks it touches (1.17's isInPowderSnow is a flag): a tall mob in two
        // blocks of it freezes no faster.
        boolean first = !f.hasKey("Seen") || f.getLong("Seen") != now || f.getInteger("SeenTick") != entity.ticksExisted;
        f.setLong("Seen", now);
        f.setInteger("SeenTick", entity.ticksExisted);
        if (first && ModConfig.powderSnow.freezing && !entity.world.isRemote) {
            FreezeTicks.markInFreezingBlock(entity);
        }
    }

    private static boolean seen(Entity entity, @Nullable NBTTagCompound f, int within) {
        if (f == null || !f.hasKey("Seen")) {
            return false;
        }
        long ago = entity.world.getTotalWorldTime() - f.getLong("Seen");
        return ago >= 0 && ago <= within;
    }

    /** @return whether powder snow touched the entity on its last move */
    public static boolean inSnow(Entity entity) {
        return seen(entity, peek(entity), 1);
    }

    public static int ticks(Entity entity) {
        return FreezeTicks.ticks(entity);
    }

    /** For tests and commands: sets the freeze ticks outright. */
    public static void setTicks(EntityLivingBase entity, int ticks) {
        FreezeTicks.setTicks(entity, ticks);
    }

    public static float percent(Entity entity) {
        return FreezeTicks.percent(entity);
    }

    public static boolean fullyFrozen(Entity entity) {
        return FreezeTicks.fullyFrozen(entity);
    }

    /** @return whether a skeleton is shaking as it turns into a stray (a frozen mob's shaking is Core's) */
    public static boolean converting(Entity entity) {
        NBTTagCompound f = peek(entity);
        return f != null && (f.hasKey("Convert") || (f.getByte("Flags") & FLAG_SHAKING) != 0);
    }

    /** @return whether the entity shakes: fully frozen, or a skeleton turning into a stray */
    public static boolean shaking(Entity entity) {
        return fullyFrozen(entity) || converting(entity);
    }

    // ---- who ---------------------------------------------------------------------------------

    /** Config id lists, parsed once per array the config holds. */
    private static final class Ids {
        private String[] source;
        private Set<ResourceLocation> ids = new HashSet<ResourceLocation>();

        boolean has(String[] current, @Nullable ResourceLocation id) {
            if (current != this.source) {
                Set<ResourceLocation> parsed = new HashSet<ResourceLocation>();
                for (String s : current) {
                    if (!s.trim().isEmpty()) {
                        parsed.add(new ResourceLocation(s.trim()));
                    }
                }
                this.ids = parsed;
                this.source = current;
            }
            return id != null && !this.ids.isEmpty() && this.ids.contains(id);
        }
    }

    /**
     * @return whether a mob may spawn inside powder snow: 1.17's entity types immune to it (strays, polar bears, snow
     * golems, the wither), whatever freezing's own immunity list says
     */
    public static boolean spawnsInSnow(Entity entity) {
        return entity instanceof net.minecraft.entity.monster.EntityStray || entity instanceof net.minecraft.entity.monster.EntityPolarBear
                || entity instanceof net.minecraft.entity.monster.EntitySnowman || entity instanceof net.minecraft.entity.boss.EntityWither;
    }

    /** Core's {@code #immune_to_freezing}. */
    public static boolean freezeImmune(Entity entity) {
        return FreezeTicks.immune(entity);
    }

    public static boolean hurtsExtra(Entity entity) {
        return FreezeTicks.hurtsExtra(entity);
    }

    public static boolean walkableMob(Entity entity) {
        if (entity instanceof EntityRabbit || entity instanceof EntityEndermite || entity instanceof EntitySilverfish) {
            return true;
        }
        if (entity instanceof EntityPlayer) {
            return false;
        }
        ResourceLocation id = EntityList.getKey(entity);
        return id != null && (id.equals(new ResourceLocation(Owners.VILLAGE_AND_PILLAGE, "fox"))
                || WALKERS.has(ModConfig.powderSnow.walkableMobs, id));
    }

    /** @return whether powder snow holds this entity up: a mob that walks on it, or anything in warm boots (Core's list) */
    public static boolean canWalkOn(Entity entity) {
        if (walkableMob(entity)) {
            return true;
        }
        return entity instanceof EntityLivingBase && FreezeTicks.warm(((EntityLivingBase) entity).getItemStackFromSlot(EntityEquipmentSlot.FEET));
    }

    /** Core's {@code canFreeze}. */
    public static boolean canFreeze(EntityLivingBase e) {
        return FreezeTicks.canFreeze(e);
    }

    static SoundEvent smallFall(Entity e) {
        return e instanceof EntityPlayer ? SoundEvents.ENTITY_PLAYER_SMALL_FALL
                : e instanceof EntityMob ? SoundEvents.ENTITY_HOSTILE_SMALL_FALL : SoundEvents.ENTITY_GENERIC_SMALL_FALL;
    }

    static SoundEvent bigFall(Entity e) {
        return e instanceof EntityPlayer ? SoundEvents.ENTITY_PLAYER_BIG_FALL
                : e instanceof EntityMob ? SoundEvents.ENTITY_HOSTILE_BIG_FALL : SoundEvents.ENTITY_GENERIC_BIG_FALL;
    }

    // ---- the tick ----------------------------------------------------------------------------

    /** Every living entity, every tick, both sides. Does nothing for one powder snow never touched. */
    static void tick(EntityLivingBase e) {
        NBTTagCompound f = peek(e);
        if (f == null) {
            return;
        }
        World world = e.world;
        boolean in = seen(e, f, 1);
        if (in) {
            climb(e);
        }
        if (world.isRemote) {
            if (f.getByte("Flags") == 0 && !seen(e, f, 2)) {
                e.getEntityData().removeTag(TAG);
            }
            return;
        }
        // 1.1.7 and older kept the freeze count here.
        f.removeTag("Ticks");
        if (e.isEntityAlive() && in && e instanceof EntityLiving && walkableMob(e)) {
            hop((EntityLiving) e);
        }
        boolean shaking = e.getClass() == EntitySkeleton.class && StrayConversion.tick((EntitySkeleton) e, f, in);
        if (e.isDead) {
            return;
        }
        int flags = shaking ? FLAG_SHAKING : 0;
        if (!f.hasKey("Sent") || f.getByte("Sent") != flags) {
            f.setByte("Sent", (byte) flags);
            send(e, flags);
        }
        if (flags == 0 && !f.hasKey("SnowTime") && !f.hasKey("Convert") && !seen(e, f, 2)) {
            e.getEntityData().removeTag(TAG);
        }
    }

    /**
     * Anything that walks on powder snow climbs out of it as up a ladder: pushing against it or jumping
     * while its feet are in it. Java sets 0.2 after the move, which gravity and the 1.5 make 0.1764 on the
     * next; this runs before the move, so it sets that.
     */
    private static void climb(EntityLivingBase e) {
        boolean jumping;
        try {
            jumping = JUMPING.getBoolean(e);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
        if ((e.collidedHorizontally || jumping) && BlockPowderSnow.feetIn(e) && canWalkOn(e)) {
            e.motionY = 0.1764D;
        }
    }

    /** Java's climb-on-top goal: a mob that walks on powder snow jumps while it is in it and has room above. */
    private static void hop(EntityLiving e) {
        BlockPos above = new BlockPos(e).up();
        net.minecraft.block.state.IBlockState state = e.world.getBlockState(above);
        if (state.getBlock() instanceof BlockPowderSnow || state.getCollisionBoundingBox(e.world, above) == net.minecraft.block.Block.NULL_AABB) {
            e.getJumpHelper().setJumping();
        }
    }

    // ---- sync (a converting skeleton's shaking) -----------------------------------------------

    static void send(EntityLivingBase e, int flags) {
        PacketFreeze msg = new PacketFreeze(e.getEntityId(), 0, flags);
        NetworkHandler.channel.sendToAllTracking(msg, e);
        if (e instanceof EntityPlayerMP) {
            sendTo((EntityPlayerMP) e, msg);
        }
    }

    static void sendTo(EntityPlayerMP player, PacketFreeze msg) {
        // Fake players, and players whose connection goes nowhere, have no channel to write to.
        if (player instanceof FakePlayer || player.connection == null || player.connection.netManager.channel() == null) {
            return;
        }
        NetworkHandler.channel.sendTo(msg, player);
    }

    /** A player starts seeing an entity: tell them if it is a skeleton shaking into a stray. */
    static void startTracking(EntityPlayerMP watcher, Entity target) {
        NBTTagCompound f = peek(target);
        if (f != null && f.getByte("Sent") != 0) {
            sendTo(watcher, new PacketFreeze(target.getEntityId(), 0, f.getByte("Sent")));
        }
    }

    /** An entity (re)enters a world: its watchers are new, so what was sent no longer counts. */
    static void joined(Entity entity) {
        NBTTagCompound f = peek(entity);
        if (f != null) {
            f.removeTag("Sent");
        }
    }

    /** Client side, from {@link PacketFreeze}. */
    public static void received(Entity entity, int ticks, int flags) {
        if (flags == 0 && peek(entity) == null) {
            return;
        }
        state(entity).setByte("Flags", (byte) flags);
    }

    static void converted(World world, BlockPos pos) {
        world.playSound(null, pos, PowderSnowContent.convertedToStray, SoundCategory.HOSTILE, 2.0F,
                (world.rand.nextFloat() - world.rand.nextFloat()) * 0.2F + 1.0F);
    }
}
