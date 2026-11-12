package com.exiledradio.cavesandcliffsbackport.lush;

import com.exiledradio.backportcore.waterlog.Waterloggable;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.IShearable;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The two plants that hang from a solid ceiling: hanging roots (replaceable like tall grass; dropped only
 * for shears) and the spore blossom (drops itself; green spores drift down from it and hang in the air
 * around it, drawn by the client).
 */
public class BlockCeilingPlant extends BlockLushPlant {

    private final AxisAlignedBB box;

    protected BlockCeilingPlant(Material material, MapColor color, SoundType sound, AxisAlignedBB box) {
        super(material, color, sound, 0.0F);
        this.box = box;
    }

    @Override
    public boolean canStay(World world, BlockPos pos, IBlockState state) {
        return solidAbove(world, pos);
    }

    @Override
    public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side) {
        return canPlaceBlockAt(world, pos);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return this.box;
    }

    /** Hanging roots. */
    public static class Roots extends BlockCeilingPlant implements IShearable, Waterloggable {

        public Roots(SoundType sound) {
            super(Material.VINE, MapColor.DIRT, sound, new AxisAlignedBB(0.125, 0.625, 0.125, 0.875, 1, 0.875));
        }

        @Override
        public boolean isReplaceable(IBlockAccess world, BlockPos pos) {
            return true;
        }

        @Override
        public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        }

        @Override
        public Item getItemDropped(IBlockState state, Random rand, int fortune) {
            return net.minecraft.init.Items.AIR;
        }

        @Override
        public boolean isShearable(ItemStack item, IBlockAccess world, BlockPos pos) {
            return true;
        }

        @Override
        public List<ItemStack> onSheared(ItemStack item, IBlockAccess world, BlockPos pos, int fortune) {
            return Collections.singletonList(new ItemStack(this));
        }
    }

    /** Spore blossom. */
    public static class SporeBlossom extends BlockCeilingPlant {

        public SporeBlossom(SoundType sound) {
            super(Material.PLANTS, MapColor.FOLIAGE, sound, new AxisAlignedBB(0.125, 0.8125, 0.125, 0.875, 1, 0.875));
        }

        /**
         * As current Java: one falling spore from under the flower each display tick, and 14 tries at a
         * drifting spore in open air within 10 blocks sideways and 10 up or down.
         */
        @Override
        @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
        public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
            com.exiledradio.cavesandcliffsbackport.lush.client.SporeParticles.falling(world,
                    pos.getX() + rand.nextDouble(), pos.getY() + 0.7, pos.getZ() + rand.nextDouble());
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int i = 0; i < 14; i++) {
                m.setPos(pos.getX() + rand.nextInt(10) - rand.nextInt(10), pos.getY() - rand.nextInt(10),
                        pos.getZ() + rand.nextInt(10) - rand.nextInt(10));
                if (!world.getBlockState(m).isFullCube()) {
                    com.exiledradio.cavesandcliffsbackport.lush.client.SporeParticles.air(world,
                            m.getX() + rand.nextDouble(), m.getY() + rand.nextDouble(), m.getZ() + rand.nextDouble());
                }
            }
        }
    }
}
