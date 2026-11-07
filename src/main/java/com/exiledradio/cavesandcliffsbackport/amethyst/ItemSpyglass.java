package com.exiledradio.cavesandcliffsbackport.amethyst;

import com.exiledradio.cavesandcliffsbackport.common.Advancements;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.MultiPartEntityPart;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.passive.EntityParrot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * The spyglass: hold use to look through it (the zoom and the scope are drawn by the client,
 * {@code amethyst.client.SpyglassClient}). While looking, the server checks every few ticks what the player
 * looks at within 100 blocks, for <i>Is It a Bird?</i>, <i>Is It a Balloon?</i> and <i>Is It a Plane?</i>.
 */
public class ItemSpyglass extends Item {

    public static final int USE_TICKS = 1200;
    public static final double LOOK_RANGE = 100;

    public ItemSpyglass() {
        setMaxStackSize(1);
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return USE_TICKS;
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.NONE;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        player.setActiveHand(hand);
        world.playSound(null, player.posX, player.posY, player.posZ, AmethystContent.spyglassUse, SoundCategory.PLAYERS, 1.0F, 1.0F);
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    @Override
    public void onPlayerStoppedUsing(ItemStack stack, World world, EntityLivingBase entity, int timeLeft) {
        world.playSound(null, entity.posX, entity.posY, entity.posZ, AmethystContent.spyglassStop, SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    public ItemStack onItemUseFinish(ItemStack stack, World world, EntityLivingBase entity) {
        onPlayerStoppedUsing(stack, world, entity, 0);
        return stack;
    }

    @Override
    public void onUsingTick(ItemStack stack, EntityLivingBase entity, int count) {
        if (entity.world.isRemote || !(entity instanceof EntityPlayer) || count % 5 != 0) {
            return;
        }
        lookedAt((EntityPlayer) entity);
    }

    /** Grants the advancement for what the player is looking at, if any; @return that entity, for tests */
    @Nullable
    public static Entity lookedAt(EntityPlayer player) {
        Entity seen = lookingAt(player);
        if (seen instanceof EntityParrot) {
            Advancements.grant(player, "adventure/spyglass_at_parrot");
        } else if (seen instanceof EntityGhast) {
            Advancements.grant(player, "adventure/spyglass_at_ghast");
        } else if (seen instanceof EntityDragon || seen instanceof MultiPartEntityPart && ((MultiPartEntityPart) seen).parent instanceof EntityDragon) {
            Advancements.grant(player, "adventure/spyglass_at_dragon");
        }
        return seen;
    }

    /** The nearest entity the look ray hits within range, not behind a block. Server-safe (no client ray trace). */
    @Nullable
    static Entity lookingAt(EntityPlayer player) {
        Vec3d eye = new Vec3d(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3d look = player.getLook(1.0F);
        Vec3d end = eye.add(look.scale(LOOK_RANGE));
        RayTraceResult block = player.world.rayTraceBlocks(eye, end, false, true, false);
        double limit = block != null ? eye.distanceTo(block.hitVec) : LOOK_RANGE;
        Entity best = null;
        double bestDist = limit;
        AxisAlignedBB sweep = player.getEntityBoundingBox().expand(look.x * LOOK_RANGE, look.y * LOOK_RANGE, look.z * LOOK_RANGE).grow(1);
        for (Entity e : player.world.getEntitiesWithinAABBExcludingEntity(player, sweep)) {
            if (e.isDead || e.isInvisible()) {
                continue;
            }
            AxisAlignedBB box = e.getEntityBoundingBox().grow(e.getCollisionBorderSize());
            RayTraceResult hit = box.calculateIntercept(eye, end);
            if (hit != null) {
                double d = eye.distanceTo(hit.hitVec);
                if (d < bestDist) {
                    best = e;
                    bestDist = d;
                }
            }
        }
        return best;
    }
}
