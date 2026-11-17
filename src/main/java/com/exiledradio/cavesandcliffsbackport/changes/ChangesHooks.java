package com.exiledradio.cavesandcliffsbackport.changes;

import com.exiledradio.cavesandcliffsbackport.ModConfig;

import net.minecraft.block.Block;
import net.minecraft.block.BlockShulkerBox;
import net.minecraft.block.BlockStem;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.IPlantable;

/**
 * What the "Other Changes" patches ({@code height.asm.ChangesTransformer}) call. Each gives back 1.12.2's value with
 * its setting off.
 */
public final class ChangesHooks {

    /** 1.12.2's minecart gravity per tick. */
    public static final double CART_GRAVITY = 0.03999999910593033D;
    /** 1.17's minecart gravity in water ({@code AbstractMinecart.tick}: -0.005). */
    public static final double CART_WATER_GRAVITY = 0.005D;

    private ChangesHooks() {
    }

    // ------------------------------------------------------------------------------------------
    // Minecarts in water: 1.18.2's AbstractMinecart. getMaxSpeed is (in water ? 4 : 8) / 20, the slope push is
    // 0.0078125 * 0.2 in water, gravity 0.005 instead of 0.04, and applyNaturalSlowdown multiplies by 0.95F more.

    private static boolean wet(EntityMinecart cart) {
        return ModConfig.changes.minecartsInWater && cart.isInWater();
    }

    public static double cartGravity(EntityMinecart cart) {
        return wet(cart) ? CART_WATER_GRAVITY : CART_GRAVITY;
    }

    public static double cartSpeed(double speed, EntityMinecart cart) {
        return wet(cart) ? speed / 2.0D : speed;
    }

    public static double cartSlope(double slope, EntityMinecart cart) {
        return wet(cart) ? slope * 0.2D : slope;
    }

    public static void cartDrag(EntityMinecart cart) {
        if (wet(cart)) {
            cart.motionX *= (double) 0.95F;
            cart.motionZ *= (double) 0.95F;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Shulker box items: 1.18.2's BlockItem.onDestroyed (ItemUtils.onContainerDestroyed): the stacks of the item's
    // BlockEntityTag.Items each become an item where the destroyed item was.

    public static void itemDestroyed(EntityItem item) {
        if (!ModConfig.changes.shulkerBoxContents || item.world.isRemote || item.isDead) {
            return;
        }
        ItemStack stack = item.getItem();
        if (stack.isEmpty() || !(Block.getBlockFromItem(stack.getItem()) instanceof BlockShulkerBox)) {
            return;
        }
        NBTTagCompound tag = stack.getSubCompound("BlockEntityTag");
        if (tag == null || !tag.hasKey("Items", 9)) {
            return;
        }
        NonNullList<ItemStack> contents = NonNullList.withSize(27, ItemStack.EMPTY);
        ItemStackHelper.loadAllItems(tag, contents);
        tag.removeTag("Items");
        for (ItemStack s : contents) {
            if (!s.isEmpty()) {
                item.world.spawnEntity(new EntityItem(item.world, item.posX, item.posY, item.posZ, s));
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Mycelium: in 1.17 it joined the dirt tag, which BushBlock.mayPlaceOn (flowers, saplings, grass, ferns, berry
    // bushes), DeadBushBlock, SugarCaneBlock and the stems' fruit (AttachedStemBlock's placement) all accept. So
    // mycelium answers as dirt does. Stems themselves still need farmland: for a stem the answer changes only where the
    // block above is not that stem (the fruit's place, which BlockStem.updateTick asks about).

    public static boolean myceliumSustains(boolean original, IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing facing,
                                           IPlantable plantable) {
        if (original || !ModConfig.changes.myceliumPlants || facing != EnumFacing.UP) {
            return original;
        }
        if (plantable instanceof BlockStem) {
            return world.getBlockState(pos.up()).getBlock() != plantable;
        }
        return Blocks.DIRT.canSustainPlant(Blocks.DIRT.getDefaultState(), world, pos, facing, plantable);
    }

    // ------------------------------------------------------------------------------------------

    /** The longest name the server takes from an anvil: 50 from 1.17 (ServerGamePacketListenerImpl), 35 before. */
    public static int anvilNameLimit() {
        return ModConfig.changes.anvilNames ? 50 : 35;
    }
    // ------------------------------------------------------------------------------------------
    // Absorption hearts (1.17, 20w49a, MC-18880: they "now show the textures for the effect instead of looking blank").
    // 1.18.2's Gui.renderHearts draws them as WITHERED under Wither and as ABSORBING otherwise (Poison too); Forge's
    // 1.12.2 renderHealth adds Poison's and Wither's offsets to ABSORBING's, where icons.png is empty.

    /** icons.png's heart columns ({@code Gui.HeartType} of 1.18.2: x = 16 + index * 18, half hearts 9 further). */
    public static final int ABSORBING = 16 + 8 * 18;
    public static final int WITHERED = 16 + 6 * 18;
    /** Where Forge's renderHealth draws absorption under Poison (MARGIN 16 + 36) and Wither (16 + 72). */
    public static final int POISONED_ABSORPTION = 16 + 36 + 144;
    public static final int WITHERED_ABSORPTION = 16 + 72 + 144;

    /** The texture x of an absorption heart (full or half) as 1.17 draws it, from the one 1.12.2's Forge computed. */
    public static int absorptionU(int u) {
        if (u == POISONED_ABSORPTION || u == POISONED_ABSORPTION + 9) {
            return ABSORBING + (u - POISONED_ABSORPTION);
        }
        if (u == WITHERED_ABSORPTION || u == WITHERED_ABSORPTION + 9) {
            return WITHERED + (u - WITHERED_ABSORPTION);
        }
        return u;
    }

    // ------------------------------------------------------------------------------------------
    // Sprinting (1.18, 21w38a/21w41a): a horizontal collision stops sprinting only when it is not minor.

    /** 1.18.2's LocalPlayer.isHorizontalCollisionMinor: under 8 degrees (0.13962634F radians). */
    public static final double MINOR_ANGLE = 0.13962634F;

    /** 1.18.2's isHorizontalCollisionMinor, from the input (strafe, forward, yaw) and the movement that resulted. */
    public static boolean isMinor(float strafe, float forward, float yaw, double dx, double dz) {
        double f = yaw * ((float) Math.PI / 180F);
        double sin = Math.sin(f);
        double cos = Math.cos(f);
        double wantX = strafe * cos - forward * sin;
        double wantZ = forward * cos + strafe * sin;
        double want = wantX * wantX + wantZ * wantZ;
        double went = dx * dx + dz * dz;
        if (want < 1.0E-5F || went < 1.0E-5F) {
            return false;
        }
        double dot = wantX * dx + wantZ * dz;
        return Math.acos(dot / Math.sqrt(want * went)) < MINOR_ANGLE;
    }
}
