package com.exiledradio.cavesandcliffsbackport.blocks;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSilverfish;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityGuardian;
import net.minecraft.entity.monster.EntityPolarBear;
import net.minecraft.entity.monster.EntityShulker;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityLlama;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityShulkerBullet;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemFishFood;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 1.17's changes to things 1.12.2 already has, each behind its own switch in the {@code blocks} category:
 *
 * <ul>
 *   <li>a shovel makes a path from dirt, coarse dirt, podzol, mycelium and rooted dirt, not only grass;</li>
 *   <li>a shulker hit by another shulker's bullet may leave a new shulker behind;</li>
 *   <li>zombies, husks, zombie villagers, polar bears and guardians that die burning drop their food cooked;</li>
 *   <li>pufferfish gives Poison II instead of Poison IV;</li>
 *   <li>llamas leave players alone in Peaceful;</li>
 *   <li>infested blocks mine faster with a pickaxe, and infested cobblestone takes half as long as
 *       cobblestone (the others already do);</li>
 *   <li>endermen can pick up rooted dirt.</li>
 * </ul>
 *
 * All of it is events and public vanilla calls; nothing here replaces a vanilla block or patches a class.
 */
public final class VanillaChanges {

    /** {@code EntityShulker.tryTeleportToNewPosition}, protected. */
    private static Method shulkerTeleport;

    /** Set if that method cannot be reached, so the failure is logged once. */
    private static boolean teleportBroken;

    /** Shulkers hit by a bullet this tick, settled when the tick ends. */
    private final List<BulletHit> hits = new ArrayList<BulletHit>();

    /** From the mod's init: the changes that are settings on vanilla objects rather than events. */
    static void init() {
        if (ModConfig.blocks.infestedBlocks) {
            // Makes a pickaxe the tool that speeds this up; the block still needs no tool to break.
            Blocks.MONSTER_EGG.setHarvestLevel("pickaxe", 0);
        }
        Block rootedDirt = Owners.ourBlock("rooted_dirt");
        if (rootedDirt != null) {
            EntityEnderman.setCarriable(rootedDirt, true);
        }
    }

    // ---- dirt paths ----

    /** @return whether 1.17 lets a shovel flatten this block and 1.12.2 does not */
    public static boolean flattens(IBlockState state) {
        Block block = state.getBlock();
        // Blocks.DIRT is dirt, coarse dirt and podzol.
        return block == Blocks.DIRT || block == Blocks.MYCELIUM || (block == Owners.ourBlock("rooted_dirt") && block != null);
    }

    @SubscribeEvent
    public void flatten(PlayerInteractEvent.RightClickBlock event) {
        if (!ModConfig.blocks.dirtPaths) {
            return;
        }
        ItemStack stack = event.getItemStack();
        EnumFacing face = event.getFace();
        if (!(stack.getItem() instanceof ItemSpade) || face == null || face == EnumFacing.DOWN) {
            return;
        }
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        EntityPlayer player = event.getEntityPlayer();
        if (!flattens(world.getBlockState(pos)) || world.getBlockState(pos.up()).getMaterial() != Material.AIR
                || !player.canPlayerEdit(pos.offset(face), face, stack)) {
            return;
        }
        // From here on this is the vanilla shovel's own code for grass.
        world.playSound(player, pos, SoundEvents.ITEM_SHOVEL_FLATTEN, SoundCategory.BLOCKS, 1.0F, 1.0F);
        if (!world.isRemote) {
            world.setBlockState(pos, Blocks.GRASS_PATH.getDefaultState(), 11);
            stack.damageItem(1, player);
        }
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
    }

    // ---- shulkers ----

    private static final class BulletHit {
        final EntityShulker shulker;
        final double x, y, z;
        final AxisAlignedBB box;
        final BlockPos attachedTo;
        final boolean open;

        BulletHit(EntityShulker s) {
            this.shulker = s;
            this.x = s.posX;
            this.y = s.posY;
            this.z = s.posZ;
            this.box = s.getEntityBoundingBox();
            this.attachedTo = s.getAttachmentPos();
            this.open = s.getPeekTick() > 0;
        }
    }

    @SubscribeEvent
    public void shulkerHit(LivingDamageEvent event) {
        EntityLivingBase hit = event.getEntityLiving();
        if (ModConfig.blocks.shulkerDuplication && !teleportBroken && hit instanceof EntityShulker && !hit.world.isRemote
                && event.getAmount() > 0.0F && event.getSource().getImmediateSource() instanceof EntityShulkerBullet) {
            this.hits.add(new BulletHit((EntityShulker) hit));
        }
    }

    /**
     * Settles the bullet hits of the tick. It waits for the tick's end because the damage event comes before
     * the shulker's own reaction: one at low health may teleport away by itself, and then, as in current Java,
     * the bullet does nothing more.
     */
    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            settleHits();
        }
    }

    /** Public for the self-test, which has no tick between a hit and its checks. */
    public void settleHits() {
        if (this.hits.isEmpty()) {
            return;
        }
        List<BulletHit> now = new ArrayList<BulletHit>(this.hits);
        this.hits.clear();
        for (BulletHit hit : now) {
            duplicate(hit);
        }
    }

    /** Current Java's {@code Shulker.hitByShulkerBullet}. */
    private static void duplicate(BulletHit hit) {
        EntityShulker shulker = hit.shulker;
        BlockPos attached = shulker.getAttachmentPos();
        boolean moved = attached == null ? hit.attachedTo != null : !attached.equals(hit.attachedTo);
        if (!shulker.isEntityAlive() || !hit.open || moved || !teleport(shulker)) {
            return;
        }
        World world = shulker.world;
        int near = 0;
        for (EntityShulker other : world.getEntitiesWithinAABB(EntityShulker.class, hit.box.grow(8.0))) {
            if (other.isEntityAlive()) {
                near++;
            }
        }
        // Certain with no other shulker around, 20% less likely for each one, never with five.
        if (world.rand.nextFloat() < (near - 1) / 5.0F) {
            return;
        }
        EntityShulker born = new EntityShulker(world);
        NBTTagCompound from = new NBTTagCompound();
        shulker.writeEntityToNBT(from);
        NBTTagCompound to = new NBTTagCompound();
        born.writeEntityToNBT(to);
        to.setByte("Color", from.getByte("Color"));
        born.readEntityFromNBT(to);
        born.setPosition(hit.x, hit.y, hit.z);
        world.spawnEntity(born);
    }

    /** @return whether the shulker found a new place and went there */
    private static boolean teleport(EntityShulker shulker) {
        BlockPos before = shulker.getAttachmentPos();
        try {
            if (shulkerTeleport == null) {
                shulkerTeleport = ObfuscationReflectionHelper.findMethod(EntityShulker.class, "func_184689_o", boolean.class);
            }
            // Vanilla's method also answers true for a shulker that cannot move at all (no AI), so the
            // attachment is compared as well.
            boolean went = (Boolean) shulkerTeleport.invoke(shulker);
            BlockPos after = shulker.getAttachmentPos();
            return went && after != null && !after.equals(before);
        } catch (Exception e) {
            CavesAndCliffsBackport.LOGGER.error("Could not teleport a shulker; shulkers will not multiply.", e);
            teleportBroken = true;
            return false;
        }
    }

    // ---- cooked drops ----

    @SubscribeEvent
    public void drops(LivingDropsEvent event) {
        EntityLivingBase mob = event.getEntityLiving();
        if (!ModConfig.blocks.cookedMobDrops || !mob.isBurning()) {
            return;
        }
        boolean fish = mob instanceof EntityGuardian || mob instanceof EntityPolarBear;
        boolean potato = mob instanceof EntityZombie;
        if (!fish && !potato) {
            return;
        }
        for (EntityItem drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            // Fish metadata 0 and 1 are cod and salmon, raw and cooked alike; the other two fish cannot be cooked.
            if (fish && stack.getItem() == Items.FISH && stack.getMetadata() <= 1) {
                drop.setItem(new ItemStack(Items.COOKED_FISH, stack.getCount(), stack.getMetadata()));
            } else if (potato && stack.getItem() == Items.POTATO) {
                drop.setItem(new ItemStack(Items.BAKED_POTATO, stack.getCount()));
            }
        }
    }

    // ---- pufferfish ----

    @SubscribeEvent
    public void eaten(LivingEntityUseItemEvent.Finish event) {
        EntityLivingBase eater = event.getEntityLiving();
        ItemStack food = event.getItem();
        if (!ModConfig.blocks.pufferfishPoison || eater.world.isRemote || food.getItem() != Items.FISH
                || ItemFishFood.FishType.byItemStack(food) != ItemFishFood.FishType.PUFFERFISH) {
            return;
        }
        // The fish has just given Poison IV for a minute; anything else is another poison and is left alone.
        PotionEffect poison = eater.getActivePotionEffect(MobEffects.POISON);
        if (poison != null && poison.getAmplifier() == 3 && poison.getDuration() >= 1190 && poison.getDuration() <= 1200) {
            eater.removePotionEffect(MobEffects.POISON);
            eater.addPotionEffect(new PotionEffect(MobEffects.POISON, 1200, 1));
        }
    }

    // ---- llamas ----

    @SubscribeEvent
    public void llamaTarget(LivingSetAttackTargetEvent event) {
        EntityLivingBase mob = event.getEntityLiving();
        if (ModConfig.blocks.peacefulLlamas && mob instanceof EntityLlama && event.getTarget() instanceof EntityPlayer
                && !mob.world.isRemote && mob.world.getDifficulty() == EnumDifficulty.PEACEFUL) {
            ((EntityLiving) mob).setAttackTarget(null);
        }
    }

    // ---- infested blocks ----

    /** Infested cobblestone shares the others' hardness (0.75) in 1.12.2; 1.17 gives it 1, half of cobblestone's 2. */
    @SubscribeEvent
    public void breakSpeed(PlayerEvent.BreakSpeed event) {
        IBlockState state = event.getState();
        if (ModConfig.blocks.infestedBlocks && state.getBlock() == Blocks.MONSTER_EGG
                && state.getValue(BlockSilverfish.VARIANT) == BlockSilverfish.EnumType.COBBLESTONE) {
            event.setNewSpeed(event.getNewSpeed() * 0.75F);
        }
    }
}
