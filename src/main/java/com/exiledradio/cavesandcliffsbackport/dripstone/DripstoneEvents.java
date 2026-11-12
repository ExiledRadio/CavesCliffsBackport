package com.exiledradio.cavesandcliffsbackport.dripstone;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.dripstone.BlockPointedDripstone.Fluid;

import net.minecraft.block.BlockCauldron;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What dripstone does outside its own blocks: the two kinds of damage, tridents, drips landing in cauldrons
 * and lava buckets on vanilla's cauldron. Registered on the event bus only when the stream is on.
 */
public final class DripstoneEvents {

    /** Landing on a stalagmite. A fall in everything but its death message: it ignores armor, and Feather Falling counts. */
    public static final DamageSource STALAGMITE =
            new DamageSource(CavesAndCliffsBackport.MOD_ID + ".stalagmite").setDamageBypassesArmor();

    /** A falling stalactite. As an anvil: a helmet takes a quarter of it, and wears for it. */
    public static final DamageSource FALLING_STALACTITE = new DamageSource(CavesAndCliffsBackport.MOD_ID + ".fallingStalactite");

    private static boolean impaling;

    /** Drips on their way down, per world: {cauldron position, world time they land}. Server thread only; lost on a restart. */
    private static final Map<World, List<long[]>> DRIPS = new WeakHashMap<World, List<long[]>>();

    // ------------------------------------------------------------------------------- stalagmites

    /**
     * Makes an entity fall on a stalagmite: its usual fall (events, Jump Boost, flying players, sounds), but
     * the damage arrives as {@link #STALAGMITE}.
     */
    static void impale(Entity entity, float distance, float multiplier) {
        boolean was = impaling;
        impaling = true;
        try {
            entity.fall(distance, multiplier);
        } finally {
            impaling = was;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(LivingAttackEvent event) {
        if (impaling && event.getSource() == DamageSource.FALL) {
            event.setCanceled(true);
            impaling = false;
            try {
                event.getEntityLiving().attackEntityFrom(STALAGMITE, event.getAmount());
            } finally {
                impaling = true;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onHurt(LivingHurtEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (event.getSource() == STALAGMITE) {
            // 1.12 gives Feather Falling its effect only for DamageSource.FALL itself, so add it here: the
            // damage is scaled so that vanilla's own enchantment step ends where it would with it.
            int featherFalling = 0;
            for (ItemStack armor : entity.getArmorInventoryList()) {
                featherFalling += 3 * EnchantmentHelper.getEnchantmentLevel(Enchantments.FEATHER_FALLING, armor);
            }
            if (featherFalling > 0) {
                int have = Math.min(20, Math.max(0, EnchantmentHelper.getEnchantmentModifierDamage(entity.getArmorInventoryList(), STALAGMITE)));
                int want = Math.min(20, have + featherFalling);
                event.setAmount(event.getAmount() * (1 - want / 25.0F) / (1 - have / 25.0F));
            }
        } else if (event.getSource() == FALLING_STALACTITE) {
            ItemStack helmet = entity.getItemStackFromSlot(EntityEquipmentSlot.HEAD);
            if (!helmet.isEmpty()) {
                float amount = event.getAmount();
                helmet.damageItem((int) (amount * 4.0F + entity.getRNG().nextFloat() * amount * 2.0F), entity);
                event.setAmount(amount * 0.75F);
            }
        }
    }

    // ------------------------------------------------------------------------------- tridents

    /** A thrown trident moving faster than 0.6 breaks the pointed dripstone it hits. Any mod's trident, by its entity name. */
    @SubscribeEvent
    public void onProjectile(ProjectileImpactEvent event) {
        RayTraceResult hit = event.getRayTraceResult();
        Entity projectile = event.getEntity();
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || projectile == null || projectile.world.isRemote) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        if (!BlockPointedDripstone.isPointed(projectile.world.getBlockState(pos))) {
            return;
        }
        ResourceLocation id = EntityList.getKey(projectile);
        double speed = Math.sqrt(projectile.motionX * projectile.motionX + projectile.motionY * projectile.motionY + projectile.motionZ * projectile.motionZ);
        if (id != null && id.getPath().contains("trident") && speed > 0.6) {
            projectile.world.destroyBlock(pos, true);
        }
    }

    // ------------------------------------------------------------------------------- drips

    static void scheduleDrip(World world, BlockPos cauldron, int delay) {
        List<long[]> drips = DRIPS.get(world);
        if (drips == null) {
            drips = new ArrayList<long[]>();
            DRIPS.put(world, drips);
        }
        long at = cauldron.toLong();
        for (long[] d : drips) {
            if (d[0] == at) {
                return;
            }
        }
        drips.add(new long[] {at, world.getTotalWorldTime() + delay});
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.world.isRemote) {
            landDrips(event.world, false);
        }
    }

    @SubscribeEvent
    public void onUnload(WorldEvent.Unload event) {
        DRIPS.remove(event.getWorld());
    }

    /**
     * Lands every drip that is due (or every one, for tests): the cauldron looks up for its stalactite again
     * and takes what it drips now.
     *
     * @return how many cauldrons changed
     */
    public static int landDrips(World world, boolean all) {
        List<long[]> drips = DRIPS.get(world);
        if (drips == null || drips.isEmpty()) {
            return 0;
        }
        int changed = 0;
        long now = world.getTotalWorldTime();
        for (Iterator<long[]> it = drips.iterator(); it.hasNext(); ) {
            long[] d = it.next();
            if (!all && d[1] > now) {
                continue;
            }
            it.remove();
            BlockPos cauldron = BlockPos.fromLong(d[0]);
            if (world.isBlockLoaded(cauldron) && receiveDrip(world, cauldron)) {
                changed++;
            }
        }
        return changed;
    }

    private static boolean receiveDrip(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() != Blocks.CAULDRON) {
            return false;
        }
        BlockPos tip = BlockPointedDripstone.findTipAboveCauldron(world, pos);
        if (tip == null) {
            return false;
        }
        Fluid fluid = BlockPointedDripstone.fluidAbove(world, tip, world.getBlockState(tip));
        int level = state.getValue(BlockCauldron.LEVEL);
        if (fluid == Fluid.WATER && level < 3) {
            Blocks.CAULDRON.setWaterLevel(world, pos, state, level + 1);
            world.playSound(null, pos, DripstoneContent.dripWaterIntoCauldron, SoundCategory.BLOCKS, 2.0F, world.rand.nextFloat() * 0.1F + 0.9F);
            return true;
        }
        if (fluid == Fluid.LAVA && level == 0) {
            world.setBlockState(pos, DripstoneContent.lavaCauldron.getDefaultState(), 3);
            world.playSound(null, pos, DripstoneContent.dripLavaIntoCauldron, SoundCategory.BLOCKS, 2.0F, world.rand.nextFloat() * 0.1F + 0.9F);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------- lava into a cauldron

    /** A bucket of lava on vanilla's cauldron, whatever is in it, makes a lava cauldron. */
    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        if (held.getItem() != Items.LAVA_BUCKET || world.getBlockState(pos).getBlock() != Blocks.CAULDRON) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
        if (world.isRemote) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
        if (!player.capabilities.isCreativeMode) {
            player.setHeldItem(event.getHand(), new ItemStack(Items.BUCKET));
        }
        player.addStat(StatList.CAULDRON_FILLED);
        world.setBlockState(pos, DripstoneContent.lavaCauldron.getDefaultState(), 3);
        world.playSound(null, pos, SoundEvents.ITEM_BUCKET_EMPTY_LAVA, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }
}
