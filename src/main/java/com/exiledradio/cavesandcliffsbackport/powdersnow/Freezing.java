package com.exiledradio.cavesandcliffsbackport.powdersnow;

import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.Owners;
import com.exiledradio.cavesandcliffsbackport.network.NetworkHandler;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityEndermite;
import net.minecraft.entity.monster.EntityMagmaCube;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntityPolarBear;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.entity.monster.EntityStray;
import net.minecraft.entity.passive.EntityRabbit;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
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
 * Freezing, kept on our own entity data so it works for every mob.
 *
 * <h2>The rules (current Java)</h2>
 * A living entity touching powder snow gains one freeze tick a tick, up to 140; outside it loses two. At 140
 * it takes 1 damage every 40 ticks (5 for blazes and magma cubes). Wearing any piece of leather armor,
 * being a stray, polar bear, snow golem or wither, or being a spectator prevents all of it. Frozen entities
 * are slowed by up to 0.05 while they stand on something. Skeletons do not freeze: after 140 ticks in
 * powder snow they shake for 300 more and become strays.
 *
 * <h2>Where the state lives</h2>
 * In the compound {@value #TAG} of the entity's Forge data: {@code Ticks} (freeze ticks), {@code Seen} (the
 * world time powder snow last touched it), and for skeletons {@code SnowTime} and {@code Convert}. The
 * compound exists only while one of them matters, and an entity that never met powder snow is never given
 * Forge data: the tick handler looks without creating it.
 *
 * <h2>The client</h2>
 * Gets {@link PacketFreeze} when an entity starts or stops freezing or shaking, and counts the ticks up and
 * down itself by the same rule, for the frost overlay and the shaking.
 */
public final class Freezing {

    public static final String TAG = "CCBFreeze";
    public static final int FREEZE_TICKS = 140;
    public static final int HURT_EVERY = 40;
    public static final int SKELETON_SNOW_TICKS = 140;
    public static final int SKELETON_CONVERT_TICKS = 300;
    public static final String GAME_RULE = "freezeDamage";

    /** Java's {@code freeze} damage: ignores armor. Its death messages are {@code death.attack.freeze}. */
    public static final DamageSource FREEZE = new DamageSource("freeze").setDamageBypassesArmor();

    static final int FLAG_FREEZING = 1, FLAG_SHAKING = 2;

    public static final UUID SLOW = UUID.fromString("1eaf83ff-7207-4596-b37a-d7a07b3ec4ce");
    /** Forge's per-entity data, null until something asks for it. */
    private static final Field DATA = ReflectionHelper.findField(Entity.class, "customEntityData");
    private static final Field JUMPING = ReflectionHelper.findField(EntityLivingBase.class, "isJumping", "field_70703_bu");

    private static final Ids WEARABLES = new Ids(), IMMUNE = new Ids(), EXTRA = new Ids(), WALKERS = new Ids();

    private Freezing() {
    }

    // ---- state -------------------------------------------------------------------------------

    /** @return the entity's freeze data, or null if it has none; never creates any */
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

    /** Powder snow touched this entity this tick. */
    public static void markInSnow(EntityLivingBase entity) {
        state(entity).setLong("Seen", entity.world.getTotalWorldTime());
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
        NBTTagCompound f = peek(entity);
        return f == null ? 0 : f.getInteger("Ticks");
    }

    /** For tests and commands: sets the freeze ticks outright. */
    public static void setTicks(EntityLivingBase entity, int ticks) {
        state(entity).setInteger("Ticks", Math.max(0, Math.min(FREEZE_TICKS, ticks)));
    }

    public static float percent(Entity entity) {
        return Math.min(ticks(entity), FREEZE_TICKS) / (float) FREEZE_TICKS;
    }

    public static boolean fullyFrozen(Entity entity) {
        return ticks(entity) >= FREEZE_TICKS;
    }

    /** @return whether the entity shakes: fully frozen, or a skeleton turning into a stray */
    public static boolean shaking(Entity entity) {
        NBTTagCompound f = peek(entity);
        if (f == null) {
            return false;
        }
        return f.getInteger("Ticks") >= FREEZE_TICKS || f.hasKey("Convert") || (f.getByte("Flags") & FLAG_SHAKING) != 0;
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

    private static boolean warm(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if (item instanceof ItemArmor && ((ItemArmor) item).getArmorMaterial() == ItemArmor.ArmorMaterial.LEATHER) {
            return true;
        }
        return WEARABLES.has(ModConfig.powderSnow.freezeImmuneWearables, item.getRegistryName());
    }

    public static boolean freezeImmune(Entity entity) {
        return entity instanceof EntityStray || entity instanceof EntityPolarBear || entity instanceof EntitySnowman
                || entity instanceof EntityWither
                || IMMUNE.has(ModConfig.powderSnow.freezeImmuneMobs, EntityList.getKey(entity));
    }

    public static boolean hurtsExtra(Entity entity) {
        if (entity instanceof EntityBlaze || entity instanceof EntityMagmaCube) {
            return true;
        }
        ResourceLocation id = EntityList.getKey(entity);
        // The strider is another update's mob; it is known here only by its name.
        return id != null && (id.equals(new ResourceLocation(Owners.NETHER_UPDATE, "strider"))
                || EXTRA.has(ModConfig.powderSnow.freezeHurtsExtraMobs, id));
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

    /** @return whether powder snow holds this entity up: a mob that walks on it, or anything in leather boots */
    public static boolean canWalkOn(Entity entity) {
        if (walkableMob(entity)) {
            return true;
        }
        return entity instanceof EntityLivingBase && warm(((EntityLivingBase) entity).getItemStackFromSlot(EntityEquipmentSlot.FEET));
    }

    public static boolean canFreeze(EntityLivingBase e) {
        if (e instanceof EntityPlayer && ((EntityPlayer) e).isSpectator()) {
            return false;
        }
        // Skeletons turn into strays instead.
        if (e.getClass() == EntitySkeleton.class || freezeImmune(e)) {
            return false;
        }
        return !warm(e.getItemStackFromSlot(EntityEquipmentSlot.HEAD)) && !warm(e.getItemStackFromSlot(EntityEquipmentSlot.CHEST))
                && !warm(e.getItemStackFromSlot(EntityEquipmentSlot.LEGS)) && !warm(e.getItemStackFromSlot(EntityEquipmentSlot.FEET));
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
            int flags = f.getByte("Flags");
            int t = f.getInteger("Ticks");
            int n = (flags & FLAG_FREEZING) != 0 ? Math.min(FREEZE_TICKS, t + 1) : Math.max(0, t - 2);
            if (n != t) {
                f.setInteger("Ticks", n);
            }
            if (n == 0 && flags == 0 && !seen(e, f, 2)) {
                e.getEntityData().removeTag(TAG);
            }
            return;
        }

        int t = f.getInteger("Ticks");
        boolean freezing = false;
        if (e.isEntityAlive()) {
            freezing = in && ModConfig.powderSnow.freezing && canFreeze(e);
            int n = freezing ? Math.min(FREEZE_TICKS, t + 1) : Math.max(0, t - 2);
            if (n != t) {
                f.setInteger("Ticks", n);
                t = n;
            }
            if (in && e instanceof EntityLiving && walkableMob(e)) {
                hop((EntityLiving) e);
            }
        }
        slow(e, t);
        if (t >= FREEZE_TICKS && e.ticksExisted % HURT_EVERY == 0 && canFreeze(e)
                && (!(e instanceof EntityPlayer) || freezeDamage(world))) {
            e.attackEntityFrom(FREEZE, hurtsExtra(e) ? 5.0F : 1.0F);
        }
        boolean shaking = e.getClass() == EntitySkeleton.class && StrayConversion.tick((EntitySkeleton) e, f, in);
        if (e.isDead) {
            return;
        }
        int flags = (freezing ? FLAG_FREEZING : 0) | (shaking ? FLAG_SHAKING : 0);
        if (!f.hasKey("Sent") || f.getByte("Sent") != flags) {
            f.setByte("Sent", (byte) flags);
            send(e, t, flags);
        }
        if (t == 0 && flags == 0 && !f.hasKey("SnowTime") && !seen(e, f, 2)) {
            e.getEntityData().removeTag(TAG);
        }
    }

    /** The freezeDamage game rule (added by us to worlds that lack it): off, players take no freeze damage. */
    private static boolean freezeDamage(World world) {
        return !world.getGameRules().hasRule(GAME_RULE) || world.getGameRules().getBoolean(GAME_RULE);
    }

    /** Java slows a frozen entity by up to 0.05 while it stands on something. */
    private static void slow(EntityLivingBase e, int t) {
        IAttributeInstance speed = e.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        double want = 0.0D;
        if (t > 0 && !e.world.isAirBlock(new BlockPos(e.posX, e.posY - 0.2D, e.posZ))) {
            want = -0.05F * (Math.min(t, FREEZE_TICKS) / (float) FREEZE_TICKS);
        }
        AttributeModifier has = speed.getModifier(SLOW);
        if (has != null) {
            if (want != 0.0D && has.getAmount() == want) {
                return;
            }
            speed.removeModifier(has);
        }
        if (want != 0.0D) {
            speed.applyModifier(new AttributeModifier(SLOW, "Powder snow slow", want, 0).setSaved(false));
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

    // ---- sync --------------------------------------------------------------------------------

    static void send(EntityLivingBase e, int ticks, int flags) {
        PacketFreeze msg = new PacketFreeze(e.getEntityId(), ticks, flags);
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

    /** A player starts seeing an entity: tell them if it is frozen or shaking. */
    static void startTracking(EntityPlayerMP watcher, Entity target) {
        NBTTagCompound f = peek(target);
        if (f != null && (f.getInteger("Ticks") > 0 || f.getByte("Sent") != 0)) {
            sendTo(watcher, new PacketFreeze(target.getEntityId(), f.getInteger("Ticks"), f.getByte("Sent")));
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
        if (ticks == 0 && flags == 0 && peek(entity) == null) {
            return;
        }
        NBTTagCompound f = state(entity);
        f.setInteger("Ticks", ticks);
        f.setByte("Flags", (byte) flags);
    }

    static void converted(World world, BlockPos pos) {
        world.playSound(null, pos, PowderSnowContent.convertedToStray, SoundCategory.HOSTILE, 2.0F,
                (world.rand.nextFloat() - world.rand.nextFloat()) * 0.2F + 1.0F);
    }
}
