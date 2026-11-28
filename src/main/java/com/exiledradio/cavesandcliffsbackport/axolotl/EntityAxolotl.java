package com.exiledradio.cavesandcliffsbackport.axolotl;

import com.exiledradio.cavesandcliffsbackport.common.Advancements;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIMate;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.ai.EntityLookHelper;
import net.minecraft.entity.ai.EntityMoveHelper;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 1.17's axolotl: an amphibious animal that swims freely, crawls slowly on land and dries out there, hunts
 * fish and squid, fights guardians and drowned beside the player and heals whoever lands the kill, plays dead
 * when hurt, and is carried in a bucket.
 *
 * <h2>From a brain to goals</h2>
 * 1.17's axolotl is driven by a brain (activities, memories, sensors); 1.12.2 has goals. The same behavior
 * is kept as goals ({@link AxolotlGoals}) plus three counters on the entity, counted in
 * {@link #updateAITasks()} so they never depend on which goal happens to be asked:
 * <ul>
 *   <li>{@code playDeadTicks} - the {@code PLAY_DEAD_TICKS} memory;</li>
 *   <li>{@code huntingCooldown} - {@code HAS_HUNTING_COOLDOWN}, two minutes after every fight;</li>
 *   <li>the target scan once a second - the attackables sensor.</li>
 * </ul>
 */
public class EntityAxolotl extends EntityAnimal {

    private static final java.lang.reflect.Field LOOK_HELPER = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(EntityLiving.class, "field_70749_g", "lookHelper");

    private static final DataParameter<Integer> VARIANT = EntityDataManager.createKey(EntityAxolotl.class, DataSerializers.VARINT);
    private static final DataParameter<Boolean> PLAYING_DEAD = EntityDataManager.createKey(EntityAxolotl.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Boolean> FROM_BUCKET = EntityDataManager.createKey(EntityAxolotl.class, DataSerializers.BOOLEAN);

    /** Five minutes out of water before it starts taking damage. */
    public static final int MAX_AIR = 6000;
    /** What a splash of water gives back. */
    private static final int REHYDRATE_AIR = 1800;
    private static final int PLAY_DEAD_TICKS = 200;
    private static final int HUNTING_COOLDOWN = 2400;
    private static final int REGENERATION_MAX = 2400;
    private static final int REGENERATION_STEP = 100;

    /** 1.17's {@code DamageSource.DRY_OUT}; its death message is the vanilla key {@code death.attack.dryout}. */
    public static final DamageSource DRY_OUT = new DamageSource("dryout");

    private int playDeadTicks;
    private int huntingCooldown;

    /**
     * The model's part rotations as last drawn (ten parts, x y z each), which 1.17 keeps on the entity so each
     * frame eases from the last. Plain numbers, so the server loads this class without any client type.
     */
    public final float[] modelRotations = new float[30];
    public boolean modelRotationsSet;

    public EntityAxolotl(World world) {
        super(world);
        this.setSize(0.75F, 0.42F);
        this.stepHeight = 1.0F;
        this.setPathPriority(PathNodeType.WATER, 0.0F);
        this.moveHelper = new MoveHelper(this);
        // 1.12.2 keeps the look helper in a private final field, found once (a lookup per axolotl was slow).
        try {
            LOOK_HELPER.set(this, new LookHelper(this, 20));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(VARIANT, 0);
        this.dataManager.register(PLAYING_DEAD, false);
        this.dataManager.register(FROM_BUCKET, false);
        this.setAir(MAX_AIR);
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(14.0D);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(1.0D);
        this.getAttributeMap().registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(2.0D);
    }

    @Override
    protected void initEntityAI() {
        this.tasks.addTask(0, new AxolotlGoals.PlayDead(this));
        this.tasks.addTask(1, new EntityAIMate(this, 0.2D));
        this.tasks.addTask(2, new AxolotlGoals.Tempt(this));
        this.tasks.addTask(3, new AxolotlGoals.FollowAdult(this));
        this.tasks.addTask(4, new AxolotlGoals.Attack(this));
        this.tasks.addTask(5, new AxolotlGoals.FindWater(this));
        this.tasks.addTask(6, new AxolotlGoals.Wander(this));
        this.tasks.addTask(7, new EntityAIWatchClosest(this, EntityPlayer.class, 6.0F));
    }

    @Override
    protected PathNavigate createNavigator(World world) {
        return new AxolotlNavigator(this, world);
    }

    // --- synced state ----------------------------------------------------------------------------------

    public AxolotlVariant getVariant() {
        return AxolotlVariant.byId(this.dataManager.get(VARIANT));
    }

    public void setVariant(AxolotlVariant variant) {
        this.dataManager.set(VARIANT, variant.id);
    }

    public boolean isPlayingDead() {
        return this.dataManager.get(PLAYING_DEAD);
    }

    public void setPlayingDead(boolean playingDead) {
        this.dataManager.set(PLAYING_DEAD, playingDead);
    }

    public boolean isFromBucket() {
        return this.dataManager.get(FROM_BUCKET);
    }

    public void setFromBucket(boolean fromBucket) {
        this.dataManager.set(FROM_BUCKET, fromBucket);
    }

    public int getPlayDeadTicks() {
        return this.playDeadTicks;
    }

    /** Starts (or, with 0, ends) playing dead; the synced flag follows on the next AI tick. */
    public void setPlayDeadTicks(int ticks) {
        this.playDeadTicks = ticks;
    }

    public int getHuntingCooldown() {
        return this.huntingCooldown;
    }

    public void setHuntingCooldown(int ticks) {
        this.huntingCooldown = ticks;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setInteger("Variant", this.getVariant().id);
        compound.setBoolean("FromBucket", this.isFromBucket());
        compound.setInteger("PlayDeadTicks", this.playDeadTicks);
        compound.setInteger("HuntingCooldown", this.huntingCooldown);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        this.setVariant(AxolotlVariant.byId(compound.getInteger("Variant")));
        this.setFromBucket(compound.getBoolean("FromBucket"));
        this.playDeadTicks = compound.getInteger("PlayDeadTicks");
        this.huntingCooldown = compound.getInteger("HuntingCooldown");
    }

    // --- being amphibious ------------------------------------------------------------------------------

    @Override
    public float getEyeHeight() {
        return this.height * 0.655F;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean isPushedByWater() {
        return false;
    }

    @Override
    public float getBlockPathWeight(BlockPos pos) {
        return 0.0F;
    }

    @Override
    public int getVerticalFaceSpeed() {
        return 1;
    }

    /** Dries out: out of water and rain its air runs down, and at the end it loses a heart a second. */
    @Override
    public void onEntityUpdate() {
        int air = this.getAir();
        super.onEntityUpdate();
        if (!this.isAIDisabled()) {
            if (this.isEntityAlive() && !this.isWet()) {
                this.setAir(air - 1);
                if (this.getAir() == -20) {
                    this.setAir(0);
                    this.attackEntityFrom(DRY_OUT, 2.0F);
                }
            } else {
                this.setAir(MAX_AIR);
            }
        }
    }

    /** A splash of water gives back a minute and a half. */
    public void rehydrate() {
        this.setAir(Math.min(this.getAir() + REHYDRATE_AIR, MAX_AIR));
    }

    /** 1.17's swimming: pushed along its heading, up and down as the move helper asks, 0.9 drag, no sinking. */
    @Override
    public void travel(float strafe, float vertical, float forward) {
        if (this.isServerWorld() && this.isInWater()) {
            this.moveRelative(strafe, vertical, forward, this.getAIMoveSpeed());
            this.move(MoverType.SELF, this.motionX, this.motionY, this.motionZ);
            this.motionX *= 0.9D;
            this.motionY *= 0.9D;
            this.motionZ *= 0.9D;
        } else {
            super.travel(strafe, vertical, forward);
        }
    }

    // --- the brain's counters --------------------------------------------------------------------------

    @Override
    protected void updateAITasks() {
        super.updateAITasks();
        if (this.playDeadTicks > 0) {
            --this.playDeadTicks;
        }
        this.setPlayingDead(this.playDeadTicks > 0);
        if (this.huntingCooldown > 0) {
            --this.huntingCooldown;
        }
        // The attackables sensor: once a second, as 1.17's sensors run.
        if ((this.ticksExisted + this.getEntityId()) % 20 == 0 && !this.isPlayingDead()) {
            EntityLivingBase target = this.getAttackTarget();
            if (target != null && !target.isEntityAlive() && !this.isFighting) {
                this.setAttackTarget(null);
                target = null;
            }
            if (target == null && !this.isInLove()) {
                this.setAttackTarget(this.findTarget());
            }
        }
    }

    /** Set by the attack goal while it runs, so a dead target is only dropped here when no fight will end on it. */
    boolean isFighting;

    public boolean isFighting() {
        return this.isFighting;
    }

    /** @return the nearest mob within 8 blocks, in water and in sight, that axolotls attack right now */
    @Nullable
    public EntityLivingBase findTarget() {
        if (!AxolotlTargets.any()) {
            return null;
        }
        List<EntityLivingBase> near = this.world.getEntitiesWithinAABB(EntityLivingBase.class, this.getEntityBoundingBox().grow(8.0D),
                e -> e != this && e.isEntityAlive() && !(e instanceof EntityPlayer) && !e.getIsInvulnerable());
        EntityLivingBase best = null;
        double bestDistance = 64.0D;
        for (EntityLivingBase e : near) {
            double d = e.getDistanceSq(this);
            if (d > bestDistance || !e.isInWater()) {
                continue;
            }
            if ((AxolotlTargets.isHostile(e) || (this.huntingCooldown <= 0 && AxolotlTargets.isHunted(e)))
                    && this.getEntitySenses().canSee(e)) {
                best = e;
                bestDistance = d;
            }
        }
        return best;
    }

    /** One time in three, a hit taken in water that leaves it alive makes it play dead for ten seconds. */
    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        float health = this.getHealth();
        if (!this.world.isRemote && !this.isAIDisabled() && this.world.rand.nextInt(3) == 0
                && (this.world.rand.nextInt(3) < amount || health / this.getMaxHealth() < 0.5F) && amount < health && this.isInWater()
                && (source.getTrueSource() != null || source.getImmediateSource() != null) && !this.isPlayingDead()) {
            this.playDeadTicks = PLAY_DEAD_TICKS;
        }
        return super.attackEntityFrom(source, amount);
    }

    @Override
    public boolean attackEntityAsMob(Entity target) {
        boolean hit = target.attackEntityFrom(DamageSource.causeMobDamage(this),
                (float) (int) this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue());
        if (hit) {
            this.applyEnchantments(this, target);
            this.playSound(AxolotlContent.attack, 1.0F, 1.0F);
        }
        return hit;
    }

    /** 1.17's reach for its bite, squared. */
    double attackReachSq(EntityLivingBase target) {
        return 1.5D + target.width * 2.0D;
    }

    /**
     * The end of a fight. A target that died to a player still within 20 blocks earns that player the
     * axolotl's help; every fight is followed by two minutes without hunting.
     */
    void onStopAttacking(@Nullable EntityLivingBase target) {
        this.huntingCooldown = HUNTING_COOLDOWN;
        if (target == null || target.isEntityAlive()) {
            return;
        }
        DamageSource last = target.getLastDamageSource();
        Entity killer = last == null ? null : last.getTrueSource();
        if (killer instanceof EntityPlayer && killer.world == this.world
                && killer.getEntityBoundingBox().intersects(this.getEntityBoundingBox().grow(20.0D))) {
            this.applySupportingEffects((EntityPlayer) killer);
        }
    }

    /** Regeneration, five seconds more each time up to two minutes, and Mining Fatigue taken away. */
    public void applySupportingEffects(EntityPlayer player) {
        PotionEffect regeneration = player.getActivePotionEffect(MobEffects.REGENERATION);
        int duration = regeneration != null ? regeneration.getDuration() : 0;
        if (duration < REGENERATION_MAX) {
            duration = Math.min(REGENERATION_MAX, REGENERATION_STEP + duration);
            player.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, duration, 0));
            Advancements.grant(player, "husbandry/kill_axolotl_target");
        }
        player.removePotionEffect(MobEffects.MINING_FATIGUE);
    }

    // --- breeding --------------------------------------------------------------------------------------

    @Override
    public boolean isBreedingItem(ItemStack stack) {
        return AxolotlContent.isBreedingItem(stack);
    }

    /** A bucket fed to it is handed back as a bucket of water (any other item's own container, if it has one). */
    @Override
    protected void consumeItemFromStack(EntityPlayer player, ItemStack stack) {
        if (player.capabilities.isCreativeMode) {
            return;
        }
        ItemStack left = ItemStack.EMPTY;
        if (AxolotlContent.isTropicalFishBucket(stack)) {
            left = new ItemStack(Items.WATER_BUCKET);
        } else if (stack.getItem().hasContainerItem(stack)) {
            left = stack.getItem().getContainerItem(stack);
        }
        EnumHand hand = player.getHeldItemOffhand() == stack ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
        boolean held = player.getHeldItem(hand) == stack;
        stack.shrink(1);
        if (left.isEmpty()) {
            return;
        }
        if (held && stack.isEmpty()) {
            player.setHeldItem(hand, left);
        } else if (!player.inventory.addItemStackToInventory(left)) {
            player.dropItem(left, false);
        }
    }

    /** One time in 1200 the baby is blue; otherwise it takes after one parent. It never despawns. */
    @Override
    public EntityAgeable createChild(EntityAgeable mate) {
        EntityAxolotl baby = new EntityAxolotl(this.world);
        AxolotlVariant variant;
        if (this.rand.nextInt(1200) == 0) {
            variant = AxolotlVariant.BLUE;
        } else {
            variant = this.rand.nextBoolean() || !(mate instanceof EntityAxolotl) ? this.getVariant() : ((EntityAxolotl) mate).getVariant();
        }
        baby.setVariant(variant);
        baby.enablePersistence();
        return baby;
    }

    // --- spawning --------------------------------------------------------------------------------------

    /** What a natural group shares: its two colors, and how many have spawned (from the third on, babies). */
    public static final class GroupData implements IEntityLivingData {
        final AxolotlVariant[] types;
        int size;

        GroupData(AxolotlVariant... types) {
            this.types = types;
        }
    }

    @Override
    @Nullable
    public IEntityLivingData onInitialSpawn(DifficultyInstance difficulty, @Nullable IEntityLivingData data) {
        boolean baby = false;
        if (data instanceof GroupData) {
            baby = ((GroupData) data).size >= 2;
        } else {
            data = new GroupData(AxolotlVariant.randomCommon(this.world.rand), AxolotlVariant.randomCommon(this.world.rand));
        }
        GroupData group = (GroupData) data;
        this.setVariant(group.types[this.world.rand.nextInt(group.types.length)]);
        if (baby) {
            this.setGrowingAge(-24000);
        }
        ++group.size;
        super.onInitialSpawn(difficulty, data);
        return data;
    }

    /** 1.17's rule: in water, with clay right below. Where that may be is {@link AxolotlHabitat}'s. */
    @Override
    public boolean getCanSpawnHere() {
        BlockPos pos = new BlockPos(this.posX, this.getEntityBoundingBox().minY, this.posZ);
        return this.world.getBlockState(pos.down()).getBlock() == Blocks.CLAY && AxolotlSpawning.habitat.canSpawnAt(this.world, pos);
    }

    @Override
    public boolean isNotColliding() {
        return this.world.checkNoEntityCollision(this.getEntityBoundingBox(), this);
    }

    /** As 1.17: a wild axolotl despawns as monsters do; one from a bucket, bred or named stays. */
    @Override
    protected boolean canDespawn() {
        return !this.isFromBucket() && !this.hasCustomName();
    }

    // --- buckets ---------------------------------------------------------------------------------------

    /** A bucket of water on a living axolotl scoops it up; the bucket keeps its color, age, health and name. */
    @Override
    public boolean processInteract(EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (held.getItem() != Items.WATER_BUCKET || !this.isEntityAlive() || AxolotlContent.bucket == null) {
            return super.processInteract(player, hand);
        }
        // The server alone fills the bucket and removes the axolotl; the client only claims the click so the
        // bucket is not also emptied onto the block behind.
        if (this.world.isRemote) {
            return true;
        }
        this.playSound(AxolotlContent.bucketFill, 1.0F, 1.0F);
        ItemStack filled = new ItemStack(AxolotlContent.bucket);
        this.writeBucketData(filled);
        Advancements.grant(player, "husbandry/axolotl_in_a_bucket");
        if (player.capabilities.isCreativeMode) {
            // Current Java: creative keeps the water bucket and always gets the filled one as well.
            if (!player.inventory.addItemStackToInventory(filled)) {
                player.dropItem(filled, false);
            }
        } else {
            held.shrink(1);
            if (held.isEmpty()) {
                player.setHeldItem(hand, filled);
            } else if (!player.inventory.addItemStackToInventory(filled)) {
                player.dropItem(filled, false);
            }
        }
        this.setDead();
        return true;
    }

    /** Current Java's bucket data: name, health, the flags a command can set, then color, age and cooldown. */
    public void writeBucketData(ItemStack bucket) {
        if (this.hasCustomName()) {
            bucket.setStackDisplayName(this.getCustomNameTag());
        }
        NBTTagCompound tag = bucket.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            bucket.setTagCompound(tag);
        }
        if (this.isAIDisabled()) {
            tag.setBoolean("NoAI", true);
        }
        if (this.isSilent()) {
            tag.setBoolean("Silent", true);
        }
        if (this.hasNoGravity()) {
            tag.setBoolean("NoGravity", true);
        }
        if (this.isGlowing()) {
            tag.setBoolean("Glowing", true);
        }
        if (this.getIsInvulnerable()) {
            tag.setBoolean("Invulnerable", true);
        }
        tag.setFloat("Health", this.getHealth());
        tag.setInteger("Variant", this.getVariant().id);
        tag.setInteger("Age", this.getGrowingAge());
        if (this.huntingCooldown > 0) {
            tag.setLong("HuntingCooldown", this.huntingCooldown);
        }
    }

    /** Sets up an axolotl just let out of a bucket, before it joins the world. An empty bucket tag gives lucy. */
    public void readBucketData(ItemStack bucket) {
        NBTTagCompound tag = bucket.getTagCompound();
        if (tag != null) {
            if (tag.hasKey("NoAI")) {
                this.setNoAI(tag.getBoolean("NoAI"));
            }
            if (tag.hasKey("Silent")) {
                this.setSilent(tag.getBoolean("Silent"));
            }
            if (tag.hasKey("NoGravity")) {
                this.setNoGravity(tag.getBoolean("NoGravity"));
            }
            if (tag.hasKey("Glowing")) {
                this.setGlowing(tag.getBoolean("Glowing"));
            }
            if (tag.hasKey("Invulnerable")) {
                this.setEntityInvulnerable(tag.getBoolean("Invulnerable"));
            }
            if (tag.hasKey("Health", 99)) {
                this.setHealth(tag.getFloat("Health"));
            }
            int variant = tag.getInteger("Variant");
            if (AxolotlVariant.valid(variant)) {
                this.setVariant(AxolotlVariant.byId(variant));
            }
            if (tag.hasKey("Age")) {
                this.setGrowingAge(tag.getInteger("Age"));
            }
            if (tag.hasKey("HuntingCooldown")) {
                this.huntingCooldown = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, tag.getLong("HuntingCooldown")));
            }
        }
        if (bucket.hasDisplayName()) {
            this.setCustomNameTag(bucket.getDisplayName());
        }
        this.setFromBucket(true);
    }

    // --- sounds and loot -------------------------------------------------------------------------------

    /** Silent while playing dead. */
    @Override
    public void playLivingSound() {
        if (!this.isPlayingDead()) {
            super.playLivingSound();
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isInWater() ? AxolotlContent.idleWater : AxolotlContent.idleAir;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return AxolotlContent.hurt;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return AxolotlContent.death;
    }

    @Override
    protected SoundEvent getSwimSound() {
        return AxolotlContent.swim;
    }

    @Override
    protected SoundEvent getSplashSound() {
        return AxolotlContent.splash;
    }

    /** Axolotls drop nothing but experience. */
    @Override
    @Nullable
    protected ResourceLocation getLootTable() {
        return null;
    }

    // --- movement and looking --------------------------------------------------------------------------

    /**
     * 1.17's {@code SmoothSwimmingMoveControl(85, 10, 0.1, 0.5, no gravity)}: turns at most 10 degrees a tick
     * toward the next path point; in water it pitches toward it (at most 85 degrees) and swims along its pitch
     * at a tenth of the asked speed, on land it walks at half. Still while playing dead.
     */
    static final class MoveHelper extends EntityMoveHelper {
        private final EntityAxolotl axolotl;

        MoveHelper(EntityAxolotl axolotl) {
            super(axolotl);
            this.axolotl = axolotl;
        }

        @Override
        public void onUpdateMoveHelper() {
            EntityAxolotl mob = this.axolotl;
            if (mob.isPlayingDead()) {
                // Nothing steers it, and nothing keeps pushing it either.
                mob.setAIMoveSpeed(0.0F);
                mob.setMoveStrafing(0.0F);
                mob.setMoveVertical(0.0F);
                mob.setMoveForward(0.0F);
                return;
            }
            if (this.action != EntityMoveHelper.Action.MOVE_TO || mob.getNavigator().noPath()) {
                mob.setAIMoveSpeed(0.0F);
                mob.setMoveStrafing(0.0F);
                mob.setMoveVertical(0.0F);
                mob.setMoveForward(0.0F);
                return;
            }
            double dx = this.posX - mob.posX;
            double dy = this.posY - mob.posY;
            double dz = this.posZ - mob.posZ;
            if (dx * dx + dy * dy + dz * dz < 2.5000003E-7D) {
                mob.setMoveForward(0.0F);
                return;
            }
            float yaw = (float) (MathHelper.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;
            mob.rotationYaw = this.limitAngle(mob.rotationYaw, yaw, 10.0F);
            mob.renderYawOffset = mob.rotationYaw;
            mob.rotationYawHead = mob.rotationYaw;
            float speed = (float) (this.speed * mob.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue());
            if (mob.isInWater()) {
                mob.setAIMoveSpeed(speed * 0.1F);
                double horizontal = Math.sqrt(dx * dx + dz * dz);
                if (Math.abs(dy) > 1.0E-5D || Math.abs(horizontal) > 1.0E-5D) {
                    float pitch = -((float) (MathHelper.atan2(dy, horizontal) * (180D / Math.PI)));
                    pitch = MathHelper.clamp(MathHelper.wrapDegrees(pitch), -85.0F, 85.0F);
                    mob.rotationPitch = this.limitAngle(mob.rotationPitch, pitch, 5.0F);
                }
                mob.setMoveForward(MathHelper.cos(mob.rotationPitch * 0.017453292F) * speed);
                mob.setMoveVertical(-MathHelper.sin(mob.rotationPitch * 0.017453292F) * speed);
            } else {
                mob.setAIMoveSpeed(speed * 0.5F);
            }
        }
    }

    /**
     * 1.17's {@code SmoothSwimmingLookControl}: the head turns toward what it looks at (aimed a little past it,
     * +20 yaw and +10 pitch), pitch eases back to level without a path, and the body follows the head once they
     * are more than {@code maxTurn} apart. Still while playing dead.
     *
     * <p>1.12.2's look helper keeps its state private, so this one keeps its own copy.
     */
    static final class LookHelper extends EntityLookHelper {
        private final EntityAxolotl mob;
        private final int maxTurn;
        private float deltaYaw;
        private float deltaPitch;
        private boolean looking;
        private double x;
        private double y;
        private double z;

        LookHelper(EntityAxolotl mob, int maxTurn) {
            super(mob);
            this.mob = mob;
            this.maxTurn = maxTurn;
        }

        @Override
        public void setLookPositionWithEntity(Entity entity, float deltaYaw, float deltaPitch) {
            double eyes = entity instanceof EntityLivingBase
                    ? entity.posY + entity.getEyeHeight()
                    : (entity.getEntityBoundingBox().minY + entity.getEntityBoundingBox().maxY) / 2.0D;
            this.setLookPosition(entity.posX, eyes, entity.posZ, deltaYaw, deltaPitch);
        }

        @Override
        public void setLookPosition(double x, double y, double z, float deltaYaw, float deltaPitch) {
            super.setLookPosition(x, y, z, deltaYaw, deltaPitch);
            this.x = x;
            this.y = y;
            this.z = z;
            this.deltaYaw = deltaYaw;
            this.deltaPitch = deltaPitch;
            this.looking = true;
        }

        @Override
        public void onUpdateLook() {
            if (this.mob.isPlayingDead()) {
                return;
            }
            if (this.looking) {
                this.looking = false;
                double dx = this.x - this.mob.posX;
                double dy = this.y - (this.mob.posY + this.mob.getEyeHeight());
                double dz = this.z - this.mob.posZ;
                double horizontal = MathHelper.sqrt(dx * dx + dz * dz);
                float yaw = (float) (MathHelper.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;
                float pitch = (float) -(MathHelper.atan2(dy, horizontal) * (180D / Math.PI));
                this.mob.rotationYawHead = rotateTowards(this.mob.rotationYawHead, yaw + 20.0F, this.deltaYaw);
                this.mob.rotationPitch = rotateTowards(this.mob.rotationPitch, pitch + 10.0F, this.deltaPitch);
            } else {
                if (this.mob.getNavigator().noPath()) {
                    this.mob.rotationPitch = rotateTowards(this.mob.rotationPitch, 0.0F, 5.0F);
                }
                this.mob.rotationYawHead = rotateTowards(this.mob.rotationYawHead, this.mob.renderYawOffset, 10.0F);
            }
            float apart = MathHelper.wrapDegrees(this.mob.rotationYawHead - this.mob.renderYawOffset);
            if (apart < -this.maxTurn) {
                this.mob.renderYawOffset -= 4.0F;
            } else if (apart > this.maxTurn) {
                this.mob.renderYawOffset += 4.0F;
            }
        }

        @Override
        public boolean getIsLooking() {
            return this.looking;
        }

        private static float rotateTowards(float from, float to, float maxDelta) {
            return from + MathHelper.clamp(MathHelper.wrapDegrees(to - from), -maxDelta, maxDelta);
        }
    }
}
