package com.exiledradio.cavesandcliffsbackport.copper;

import com.exiledradio.cavesandcliffsbackport.common.BlockBasic;
import com.exiledradio.cavesandcliffsbackport.common.BlockCCSlab;
import com.exiledradio.cavesandcliffsbackport.common.BlockCCStairs;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Random;

/**
 * The four copper shapes - full and cut blocks, stairs, slabs - each oxidizing on random ticks through
 * {@link Weathering} unless waxed or fully oxidized. Hardness 3, blast resistance 6, a stone pickaxe.
 */
public final class CopperBlocks {

    private CopperBlocks() {
    }

    /** A block of copper or cut copper. */
    public static class Full extends BlockBasic {
        public Full(CopperStage stage, SoundType sound, boolean ages) {
            super(Material.IRON, stage.color, sound, 3.0F, 6.0F);
            setTickRandomly(ages);
        }

        @Override
        public void randomTick(World world, BlockPos pos, IBlockState state, Random rand) {
            Weathering.randomTick(world, pos, state, rand);
        }
    }

    /**
     * Cut copper stairs. Vanilla stairs pass random ticks to their model block, which would turn the stairs
     * into that block's next stage as a full block, so stairs tick for themselves.
     */
    public static class Stairs extends BlockCCStairs {
        public Stairs(IBlockState model, boolean ages) {
            super(model);
            setTickRandomly(ages);
        }

        @Override
        public void randomTick(World world, BlockPos pos, IBlockState state, Random rand) {
            Weathering.randomTick(world, pos, state, rand);
        }

        @Override
        public void updateTick(World world, BlockPos pos, IBlockState state, Random rand) {
        }
    }

    public static class Slab extends BlockCCSlab.Half {
        public Slab(IBlockState full, boolean ages) {
            super(full);
            setTickRandomly(ages);
        }

        @Override
        public void randomTick(World world, BlockPos pos, IBlockState state, Random rand) {
            Weathering.randomTick(world, pos, state, rand);
        }
    }

    public static class DoubleSlab extends BlockCCSlab.Double {
        public DoubleSlab(IBlockState full, BlockCCSlab.Half half, boolean ages) {
            super(full, half);
            setTickRandomly(ages);
        }

        @Override
        public void randomTick(World world, BlockPos pos, IBlockState state, Random rand) {
            Weathering.randomTick(world, pos, state, rand);
        }
    }
}
