package com.exiledradio.cavesandcliffsbackport.copper;

import com.exiledradio.backportcore.copper.Oxidation;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDispenser;
import net.minecraft.block.state.IBlockState;
import net.minecraft.dispenser.IBehaviorDispenseItem;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 1.17: a dispenser with honeycomb waxes the copper block in front of it (any mod's copper in Backport Core's table),
 * using one honeycomb, with the wax particles, as 1.18.2's honeycomb dispense behavior. Anything else goes to the
 * behavior registered before (by default, dropping the honeycomb). Every wax item is covered: those with the ore name
 * honeycomb and those in {@code copper.waxItems}.
 */
public final class CopperDispense implements IBehaviorDispenseItem {

    private final IBehaviorDispenseItem previous;

    private CopperDispense(IBehaviorDispenseItem previous) {
        this.previous = previous;
    }

    /** From the mod's post-init, once every mod's honeycomb and ore names exist. */
    public static void register() {
        Set<Item> wax = new LinkedHashSet<Item>();
        for (ItemStack s : OreDictionary.getOres("honeycomb", false)) {
            wax.add(s.getItem());
        }
        for (String name : com.exiledradio.cavesandcliffsbackport.ModConfig.copper.waxItems) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(name));
            if (item != null && item != net.minecraft.init.Items.AIR) {
                wax.add(item);
            }
        }
        for (Item item : wax) {
            IBehaviorDispenseItem before = BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.getObject(item);
            if (!(before instanceof CopperDispense)) {
                BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.putObject(item, new CopperDispense(before));
            }
        }
    }

    @Override
    public ItemStack dispense(IBlockSource source, ItemStack stack) {
        World world = source.getWorld();
        EnumFacing facing = source.getBlockState().getValue(BlockDispenser.FACING);
        BlockPos pos = source.getBlockPos().offset(facing);
        IBlockState state = world.getBlockState(pos);
        Block waxed = Weathering.isWax(stack) ? Weathering.waxedOf(state.getBlock()) : null;
        if (waxed == null) {
            return this.previous.dispense(source, stack);
        }
        Oxidation.change(world, pos, state, waxed);
        PacketCopperParticles.send(world, pos, PacketCopperParticles.WAX_ON);
        stack.shrink(1);
        world.playEvent(1000, source.getBlockPos(), 0);
        world.playEvent(2000, source.getBlockPos(), facing.getXOffset() + 1 + (facing.getZOffset() + 1) * 3);
        return stack;
    }
}
