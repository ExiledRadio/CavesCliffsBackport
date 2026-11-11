package com.exiledradio.cavesandcliffsbackport.axolotl;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.monster.EntityGuardian;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.init.PotionTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionUtils;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import com.google.common.base.Predicate;

/**
 * What the rest of the world does about axolotls: natural spawning each world tick, the mobs they fight
 * fighting back, nobody attacking one that plays dead, and splashes of water keeping them wet.
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class AxolotlEvents {

    private AxolotlEvents() {
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END && AxolotlContent.enabled() && event.world instanceof WorldServer) {
            AxolotlSpawning.tick((WorldServer) event.world);
        }
    }

    /** No mob keeps (or takes) a target that is playing dead. */
    @SubscribeEvent
    public static void onSetAttackTarget(LivingSetAttackTargetEvent event) {
        EntityLivingBase target = event.getTarget();
        if (target instanceof EntityAxolotl && ((EntityAxolotl) target).isPlayingDead() && event.getEntityLiving() instanceof EntityLiving) {
            EntityLiving attacker = (EntityLiving) event.getEntityLiving();
            // Clearing fires this event again, with no target, which ends here.
            attacker.setAttackTarget(null);
            attacker.setRevengeTarget(null);
        }
    }

    /**
     * Current Java: guardians, elder guardians and drowned attack axolotls. The mobs in {@code hostileTargets}
     * get one more target goal as they join the world (not while any goal runs, so no task list is edited
     * under its own loop).
     */
    @SubscribeEvent
    public static void onJoin(EntityJoinWorldEvent event) {
        Entity entity = event.getEntity();
        if (event.getWorld().isRemote || !(entity instanceof EntityCreature) || entity instanceof EntityAxolotl
                || !AxolotlContent.enabled() || !ModConfig.axolotls.hostilesAttackAxolotls || !AxolotlTargets.isHostile(entity)) {
            return;
        }
        final EntityCreature mob = (EntityCreature) entity;
        // A guardian only goes for what is more than 3 blocks away, as it does with players and squid.
        final boolean guardian = mob instanceof EntityGuardian;
        Predicate<EntityAxolotl> awake = new Predicate<EntityAxolotl>() {
            @Override
            public boolean apply(EntityAxolotl axolotl) {
                return axolotl != null && !axolotl.isPlayingDead() && (!guardian || axolotl.getDistanceSq(mob) > 9.0D);
            }
        };
        mob.targetTasks.addTask(3, new EntityAINearestAttackableTarget<EntityAxolotl>(mob, EntityAxolotl.class, 10, true, false, awake));
    }

    /** A splash water bottle wets every axolotl within its reach. */
    @SubscribeEvent
    public static void onPotionImpact(ProjectileImpactEvent.Throwable event) {
        if (!AxolotlContent.enabled() || !(event.getThrowable() instanceof EntityPotion) || event.getThrowable().world.isRemote) {
            return;
        }
        EntityPotion potion = (EntityPotion) event.getThrowable();
        ItemStack stack = potion.getPotion();
        if (PotionUtils.getPotionFromItem(stack) != PotionTypes.WATER || !PotionUtils.getEffectsFromStack(stack).isEmpty()) {
            return;
        }
        for (EntityAxolotl axolotl : potion.world.getEntitiesWithinAABB(EntityAxolotl.class, potion.getEntityBoundingBox().grow(4.0D, 2.0D, 4.0D))) {
            axolotl.rehydrate();
        }
    }
}
