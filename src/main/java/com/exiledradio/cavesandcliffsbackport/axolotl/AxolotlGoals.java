package com.exiledradio.cavesandcliffsbackport.axolotl;

import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The axolotl's goals: 1.17's brain activities (play dead, fight, idle) as 1.12.2 goals, in the brain's order
 * of precedence. Speeds are 1.17's: 0.5 idling in water, 0.6 chasing or following in water, 0.15 on land.
 */
final class AxolotlGoals {

    private AxolotlGoals() {
    }

    static double idleSpeed(EntityAxolotl axolotl) {
        return axolotl.isInWater() ? 0.5D : 0.15D;
    }

    static double chaseSpeed(EntityAxolotl axolotl) {
        return axolotl.isInWater() ? 0.6D : 0.15D;
    }

    /**
     * Playing dead: holds every other goal off for as long as the axolotl's counter runs, and gives it
     * Regeneration for ten seconds when it starts. The counter itself is the entity's.
     */
    static final class PlayDead extends EntityAIBase {
        private final EntityAxolotl axolotl;

        PlayDead(EntityAxolotl axolotl) {
            this.axolotl = axolotl;
            this.setMutexBits(7);
        }

        @Override
        public boolean shouldExecute() {
            return this.axolotl.getPlayDeadTicks() > 0 && this.axolotl.isInWater();
        }

        @Override
        public boolean shouldContinueExecuting() {
            return this.axolotl.getPlayDeadTicks() > 0;
        }

        @Override
        public void startExecuting() {
            this.axolotl.getNavigator().clearPath();
            this.axolotl.setAttackTarget(null);
            this.axolotl.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 200, 0));
        }
    }

    /** Follows a player within 10 blocks who holds what axolotls breed with. */
    static final class Tempt extends EntityAIBase {
        private final EntityAxolotl axolotl;
        @Nullable
        private EntityPlayer player;
        private int cooldown;

        Tempt(EntityAxolotl axolotl) {
            this.axolotl = axolotl;
            this.setMutexBits(3);
        }

        @Override
        public boolean shouldExecute() {
            if (this.cooldown > 0) {
                --this.cooldown;
                return false;
            }
            this.player = this.axolotl.world.getClosestPlayerToEntity(this.axolotl, 10.0D);
            return this.player != null && this.holdsFood(this.player);
        }

        private boolean holdsFood(EntityPlayer p) {
            return this.axolotl.isBreedingItem(p.getHeldItemMainhand()) || this.axolotl.isBreedingItem(p.getHeldItemOffhand());
        }

        @Override
        public boolean shouldContinueExecuting() {
            return this.player != null && this.player.isEntityAlive() && this.axolotl.getDistanceSq(this.player) < 100.0D
                    && this.holdsFood(this.player);
        }

        @Override
        public void resetTask() {
            this.player = null;
            this.axolotl.getNavigator().clearPath();
            this.cooldown = 100;
        }

        @Override
        public void updateTask() {
            this.axolotl.getLookHelper().setLookPositionWithEntity(this.player, 10.0F, this.axolotl.getVerticalFaceSpeed());
            if (this.axolotl.getDistanceSq(this.player) < 6.25D) {
                this.axolotl.getNavigator().clearPath();
            } else {
                this.axolotl.getNavigator().tryMoveToEntityLiving(this.player, idleSpeed(this.axolotl));
            }
        }
    }

    /** A baby keeps near the closest adult. */
    static final class FollowAdult extends EntityAIBase {
        private final EntityAxolotl axolotl;
        @Nullable
        private EntityAxolotl adult;
        private int delay;

        FollowAdult(EntityAxolotl axolotl) {
            this.axolotl = axolotl;
            this.setMutexBits(3);
        }

        @Override
        public boolean shouldExecute() {
            if (!this.axolotl.isChild()) {
                return false;
            }
            List<EntityAxolotl> near = this.axolotl.world.getEntitiesWithinAABB(EntityAxolotl.class,
                    this.axolotl.getEntityBoundingBox().grow(16.0D, 8.0D, 16.0D), e -> !e.isChild() && e.isEntityAlive());
            EntityAxolotl best = null;
            double bestDistance = Double.MAX_VALUE;
            for (EntityAxolotl e : near) {
                double d = this.axolotl.getDistanceSq(e);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = e;
                }
            }
            if (best == null || bestDistance < 25.0D) {
                return false;
            }
            this.adult = best;
            return true;
        }

        @Override
        public boolean shouldContinueExecuting() {
            if (!this.axolotl.isChild() || this.adult == null || !this.adult.isEntityAlive()) {
                return false;
            }
            double d = this.axolotl.getDistanceSq(this.adult);
            return d >= 9.0D && d <= 256.0D;
        }

        @Override
        public void startExecuting() {
            this.delay = 0;
        }

        @Override
        public void resetTask() {
            this.adult = null;
        }

        @Override
        public void updateTask() {
            if (--this.delay <= 0) {
                this.delay = 10;
                this.axolotl.getNavigator().tryMoveToEntityLiving(this.adult, chaseSpeed(this.axolotl));
            }
        }
    }

    /**
     * The fight: chases the target and bites it once a second when in reach. It ends when the target is dead
     * or gone, out of range, or the axolotl is ready to breed - and every ending goes through
     * {@link EntityAxolotl#onStopAttacking}.
     */
    static final class Attack extends EntityAIBase {
        private final EntityAxolotl axolotl;
        @Nullable
        private EntityLivingBase target;
        private int attackCooldown;
        private int pathDelay;

        Attack(EntityAxolotl axolotl) {
            this.axolotl = axolotl;
            this.setMutexBits(3);
        }

        @Override
        public boolean shouldExecute() {
            EntityLivingBase t = this.axolotl.getAttackTarget();
            return t != null && t.isEntityAlive() && !this.axolotl.isInLove();
        }

        @Override
        public boolean shouldContinueExecuting() {
            EntityLivingBase t = this.target;
            return t != null && t.isEntityAlive() && t.world == this.axolotl.world && this.axolotl.getAttackTarget() == t
                    && !this.axolotl.isInLove() && this.axolotl.getDistanceSq(t) <= 256.0D;
        }

        @Override
        public void startExecuting() {
            this.target = this.axolotl.getAttackTarget();
            this.axolotl.isFighting = true;
            this.pathDelay = 0;
        }

        @Override
        public void resetTask() {
            EntityLivingBase t = this.target;
            this.target = null;
            this.axolotl.isFighting = false;
            this.axolotl.setAttackTarget(null);
            this.axolotl.getNavigator().clearPath();
            this.axolotl.onStopAttacking(t);
        }

        @Override
        public void updateTask() {
            EntityLivingBase t = this.target;
            if (t == null) {
                return;
            }
            this.axolotl.getLookHelper().setLookPositionWithEntity(t, 30.0F, 30.0F);
            double reach = this.axolotl.attackReachSq(t);
            double distance = this.axolotl.getDistanceSq(t.posX, t.getEntityBoundingBox().minY, t.posZ);
            if (distance > reach || !this.axolotl.getEntitySenses().canSee(t)) {
                if (--this.pathDelay <= 0) {
                    this.pathDelay = 4 + this.axolotl.getRNG().nextInt(7);
                    this.axolotl.getNavigator().tryMoveToEntityLiving(t, chaseSpeed(this.axolotl));
                }
            } else {
                this.axolotl.getNavigator().clearPath();
            }
            if (this.attackCooldown > 0) {
                --this.attackCooldown;
            }
            if (this.attackCooldown <= 0 && distance <= reach && this.axolotl.getEntitySenses().canSee(t)) {
                this.attackCooldown = 20;
                this.axolotl.attackEntityAsMob(t);
            }
        }
    }

    /** On land, heads for the nearest water within 6 blocks. */
    static final class FindWater extends EntityAIBase {
        private final EntityAxolotl axolotl;
        private int cooldown;

        FindWater(EntityAxolotl axolotl) {
            this.axolotl = axolotl;
            this.setMutexBits(1);
        }

        @Override
        public boolean shouldExecute() {
            if (this.axolotl.isInWater() || !this.axolotl.onGround) {
                return false;
            }
            if (this.cooldown > 0) {
                --this.cooldown;
                return false;
            }
            this.cooldown = 20;
            BlockPos water = this.nearestWater();
            return water != null && this.axolotl.getNavigator().tryMoveToXYZ(water.getX() + 0.5D, water.getY(), water.getZ() + 0.5D, 0.15D);
        }

        @Override
        public boolean shouldContinueExecuting() {
            return !this.axolotl.isInWater() && !this.axolotl.getNavigator().noPath();
        }

        @Nullable
        private BlockPos nearestWater() {
            BlockPos origin = new BlockPos(this.axolotl);
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (BlockPos.MutableBlockPos p : BlockPos.getAllInBoxMutable(origin.add(-6, -6, -6), origin.add(6, 6, 6))) {
                double d = p.distanceSq(origin);
                if (d < bestDistance && this.axolotl.world.isBlockLoaded(p)
                        && this.axolotl.world.getBlockState(p).getMaterial() == Material.WATER) {
                    bestDistance = d;
                    best = p.toImmutable();
                }
            }
            return best;
        }
    }

    /**
     * Idling: swims to random water within 10 blocks, or strolls on land, with a short rest between legs
     * (and a longer one after finding nowhere to go, so a walled-in axolotl does not search every tick).
     */
    static final class Wander extends EntityAIBase {
        private final EntityAxolotl axolotl;
        private int rest;

        Wander(EntityAxolotl axolotl) {
            this.axolotl = axolotl;
            this.setMutexBits(1);
        }

        @Override
        public boolean shouldExecute() {
            if (this.rest > 0) {
                --this.rest;
                return false;
            }
            if (!this.axolotl.getNavigator().noPath()) {
                return false;
            }
            Vec3d target = this.pick();
            if (target == null || !this.axolotl.getNavigator().tryMoveToXYZ(target.x, target.y, target.z, idleSpeed(this.axolotl))) {
                this.rest = 20 + this.axolotl.getRNG().nextInt(20);
                return false;
            }
            return true;
        }

        @Nullable
        private Vec3d pick() {
            boolean swimming = this.axolotl.isInWater();
            for (int i = 0; i < 10; i++) {
                Vec3d target = RandomPositionGenerator.findRandomTarget(this.axolotl, 10, 7);
                if (target == null) {
                    continue;
                }
                boolean water = this.axolotl.world.getBlockState(new BlockPos(target)).getMaterial() == Material.WATER;
                if (water == swimming) {
                    return target;
                }
            }
            return null;
        }

        @Override
        public boolean shouldContinueExecuting() {
            return !this.axolotl.getNavigator().noPath();
        }

        @Override
        public void resetTask() {
            this.rest = 10 + this.axolotl.getRNG().nextInt(30);
        }
    }
}
