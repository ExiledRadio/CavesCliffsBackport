package com.exiledradio.cavesandcliffsbackport.candle;

import net.minecraft.block.BlockDispenser;
import net.minecraft.block.state.IBlockState;
import net.minecraft.dispenser.IBehaviorDispenseItem;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 1.17: a dispenser with flint and steel lights the unlit candle or candle cake in front of it (not a waterlogged
 * candle), using one durability, as 1.18.2's flint-and-steel dispense behavior. Anything else goes to the behavior
 * that was registered before (vanilla's fire and TNT, or another mod's), so this only adds to it.
 */
public final class CandleDispense implements IBehaviorDispenseItem {

    private final IBehaviorDispenseItem previous;

    private CandleDispense(IBehaviorDispenseItem previous) {
        this.previous = previous;
    }

    /** From the mod's init, after vanilla's behaviors exist. */
    public static void register() {
        IBehaviorDispenseItem before = BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.getObject(Items.FLINT_AND_STEEL);
        if (!(before instanceof CandleDispense)) {
            BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.putObject(Items.FLINT_AND_STEEL, new CandleDispense(before));
        }
    }

    @Override
    public ItemStack dispense(IBlockSource source, ItemStack stack) {
        World world = source.getWorld();
        EnumFacing facing = source.getBlockState().getValue(BlockDispenser.FACING);
        BlockPos pos = source.getBlockPos().offset(facing);
        IBlockState state = world.getBlockState(pos);
        if (!BlockAbstractCandle.canLight(world, pos, state)) {
            return this.previous.dispense(source, stack);
        }
        BlockAbstractCandle.light(world, pos, state);
        if (stack.attemptDamageItem(1, world.rand, null)) {
            stack.setCount(0);
        }
        // The dispense click and smoke, as any dispense that worked.
        world.playEvent(1000, source.getBlockPos(), 0);
        world.playEvent(2000, source.getBlockPos(), facing.getXOffset() + 1 + (facing.getZOffset() + 1) * 3);
        return stack;
    }
}
