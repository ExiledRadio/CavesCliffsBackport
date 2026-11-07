package com.exiledradio.cavesandcliffsbackport.common;

import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;

/**
 * Stairs of one of our blocks. Vanilla's {@link BlockStairs} does everything (shapes, corners, half,
 * hardness, resistance and sound copied from the model block); only its constructor is protected.
 */
public class BlockCCStairs extends BlockStairs {

    public BlockCCStairs(IBlockState model) {
        super(model);
        this.useNeighborBrightness = true;
    }
}
