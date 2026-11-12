package com.exiledradio.cavesandcliffsbackport.common;

import net.minecraft.world.IBlockAccess;
import com.exiledradio.backportcore.waterlog.Waterloggable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraft.entity.player.EntityPlayer;

import java.util.Random;

/**
 * A slab of one of our blocks, as 1.12 builds slabs: a half block ({@link Half}, top or bottom) and a
 * separate double block ({@link Double}) that a second slab turns it into; vanilla's {@code ItemSlab}
 * does the placing. Both copy hardness, resistance and sound from the full block.
 *
 * <p>1.12's slab code wants a "variant" property for blocks that hold several slab kinds; ours hold
 * one, so the property has one value and never shows ({@code variant=default} in blockstate files).
 *
 * <p>Registry names: {@code <name>_slab} and {@code <name>_double_slab}, as the family's released mods name them.
 */
public abstract class BlockCCSlab extends BlockSlab implements Waterloggable {

    public enum Variant implements IStringSerializable {
        DEFAULT;

        @Override
        public String getName() {
            return "default";
        }
    }

    public static final PropertyEnum<Variant> VARIANT = PropertyEnum.create("variant", Variant.class);

    protected BlockCCSlab(IBlockState full) {
        super(full.getMaterial(), full.getBlock().getMapColor(full, null, null));
        Block b = full.getBlock();
        setSoundType(b.getSoundType());
        setHardness(b.getBlockHardness(full, null, null));
        // Stored as 3x what setResistance is given: pass a third of the full block's stored value.
        setResistance(b.getExplosionResistance(null) * 5.0F / 3.0F);
        this.useNeighborBrightness = !isDouble();
        IBlockState state = this.blockState.getBaseState().withProperty(VARIANT, Variant.DEFAULT);
        if (!isDouble()) {
            state = state.withProperty(HALF, EnumBlockHalf.BOTTOM);
        }
        setDefaultState(state);
    }

    /** A half slab holds water; a double slab is a full block and holds none (1.18.2's {@code SlabBlock}). */
    @Override
    public boolean canHoldWater(IBlockState state, IBlockAccess world, BlockPos pos) {
        return !isDouble();
    }

    /** The half slab: the one with an item. */
    public static class Half extends BlockCCSlab {
        public Half(IBlockState full) {
            super(full);
        }

        @Override
        public boolean isDouble() {
            return false;
        }
    }

    /** The double slab: two halves in one block, dropping two half slabs. */
    public static class Double extends BlockCCSlab {
        private final Half half;

        public Double(IBlockState full, Half half) {
            super(full);
            this.half = half;
            setCreativeTab(null);
        }

        @Override
        public boolean isDouble() {
            return true;
        }

        @Override
        public Item getItemDropped(IBlockState state, Random rand, int fortune) {
            return Item.getItemFromBlock(this.half);
        }

        @Override
        public ItemStack getItem(World world, BlockPos pos, IBlockState state) {
            return new ItemStack(this.half);
        }

        @Override
        public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
            return new ItemStack(this.half);
        }
    }

    @Override
    public String getTranslationKey(int meta) {
        return super.getTranslationKey();
    }

    @Override
    public IProperty<?> getVariantProperty() {
        return VARIANT;
    }

    @Override
    public Comparable<?> getTypeForItem(ItemStack stack) {
        return Variant.DEFAULT;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return isDouble() ? new BlockStateContainer(this, VARIANT) : new BlockStateContainer(this, HALF, VARIANT);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        IBlockState state = getDefaultState();
        if (!isDouble()) {
            state = state.withProperty(HALF, (meta & 8) != 0 ? EnumBlockHalf.TOP : EnumBlockHalf.BOTTOM);
        }
        return state;
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return !isDouble() && state.getValue(HALF) == EnumBlockHalf.TOP ? 8 : 0;
    }
}
