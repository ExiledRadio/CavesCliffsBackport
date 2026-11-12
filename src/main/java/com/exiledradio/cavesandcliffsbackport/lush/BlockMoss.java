package com.exiledradio.cavesandcliffsbackport.lush;

import com.exiledradio.cavesandcliffsbackport.common.BlockBasic;

import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockTallGrass;
import net.minecraft.block.IGrowable;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.EnumPlantType;
import net.minecraftforge.common.IPlantable;

import java.util.Random;

/**
 * Moss block. Bone meal spreads moss over the stone and dirt around it and grows plants on it: the moss
 * patch of 1.17 (a 3x3 to 5x5 square without its corners, each edge column 75% of the time, following the
 * ground up or down by up to 5; 60% of the new moss gets a plant).
 */
public class BlockMoss extends BlockBasic implements IGrowable {

    public BlockMoss(SoundType sound) {
        super(Material.GRASS, MapColor.GREEN, sound, 0.1F, 0.1F);
    }

    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.DESTROY;
    }

    /** Moss counts as dirt for everything that grows on dirt. */
    @Override
    public boolean canSustainPlant(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing direction, IPlantable plantable) {
        EnumPlantType type = plantable.getPlantType(world, pos.offset(direction));
        return type == EnumPlantType.Plains || type == EnumPlantType.Cave;
    }

    @Override
    public boolean canGrow(World world, BlockPos pos, IBlockState state, boolean isClient) {
        return world.isAirBlock(pos.up());
    }

    @Override
    public boolean canUseBonemeal(World world, Random rand, BlockPos pos, IBlockState state) {
        return true;
    }

    @Override
    public void grow(World world, Random rand, BlockPos pos, IBlockState state) {
        patch(world, rand, pos.up(), 1 + rand.nextInt(2), 1 + rand.nextInt(2), 0.6F, 0.75F);
    }

    /**
     * The floor vegetation patch of 1.17 with moss as its ground.
     *
     * @param origin an air block above the ground
     * @param rx     and rz the radii before the edge ring
     */
    public static void patch(World world, Random rand, BlockPos origin, int rx, int rz, float vegetation, float edge) {
        rx++;
        rz++;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                boolean ex = Math.abs(dx) == rx, ez = Math.abs(dz) == rz;
                if ((ex && ez) || ((ex || ez) && rand.nextFloat() > edge)) {
                    continue;
                }
                m.setPos(origin.getX() + dx, origin.getY(), origin.getZ() + dz);
                for (int i = 0; i < 5 && world.isAirBlock(m) && m.getY() > 1; i++) {
                    m.move(EnumFacing.DOWN);
                }
                for (int i = 0; i < 5 && !world.isAirBlock(m) && m.getY() < 255; i++) {
                    m.move(EnumFacing.UP);
                }
                BlockPos air = m.toImmutable(), ground = air.down();
                IBlockState g = world.getBlockState(ground);
                if (!world.isAirBlock(air) || g.getBlockFaceShape(world, ground, EnumFacing.UP) != BlockFaceShape.SOLID
                        || !Soil.mossReplaceable(g)) {
                    continue;
                }
                world.setBlockState(ground, LushContent.moss.getDefaultState(), 2);
                if (rand.nextFloat() < vegetation) {
                    vegetation(world, rand, air);
                }
            }
        }
    }

    /** The moss vegetation of 1.17: grass 50, moss carpet 25, tall grass 10, azalea 7, flowering azalea 4. */
    public static void vegetation(World world, Random rand, BlockPos air) {
        int r = rand.nextInt(96);
        if (r < 4) {
            world.setBlockState(air, LushContent.floweringAzalea.getDefaultState(), 2);
        } else if (r < 11) {
            world.setBlockState(air, LushContent.azalea.getDefaultState(), 2);
        } else if (r < 36) {
            world.setBlockState(air, LushContent.mossCarpet.getDefaultState(), 2);
        } else if (r < 86) {
            world.setBlockState(air, Blocks.TALLGRASS.getDefaultState().withProperty(BlockTallGrass.TYPE, BlockTallGrass.EnumType.GRASS), 2);
        } else if (world.isAirBlock(air.up())) {
            Blocks.DOUBLE_PLANT.placeAt(world, air, BlockDoublePlant.EnumPlantType.GRASS, 2);
        }
    }

    /** Moss carpet: a sixteenth of a block, on anything but air. */
    public static class Carpet extends BlockLushPlant {

        private static final AxisAlignedBB BOX = new AxisAlignedBB(0, 0, 0, 1, 0.0625, 1);

        public Carpet(SoundType sound) {
            super(Material.CARPET, MapColor.GREEN, sound, 0.1F);
            BlockBasic.resistance(this, 0.1F);
        }

        @Override
        public boolean canStay(World world, BlockPos pos, IBlockState state) {
            return !world.isAirBlock(pos.down());
        }

        @Override
        public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
            return BOX;
        }

        @Override
        public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
            return BOX;
        }

        @Override
        public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
            return face == EnumFacing.DOWN ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
        }

        @Override
        @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
        public net.minecraft.util.BlockRenderLayer getRenderLayer() {
            return net.minecraft.util.BlockRenderLayer.SOLID;
        }

        @Override
        @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
        public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
            return side == EnumFacing.UP || super.shouldSideBeRendered(state, world, pos, side);
        }
    }
}
