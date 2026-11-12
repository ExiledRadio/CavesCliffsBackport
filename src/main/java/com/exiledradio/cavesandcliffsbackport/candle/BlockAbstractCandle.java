package com.exiledradio.cavesandcliffsbackport.candle;

import com.exiledradio.backportcore.waterlog.Waterlogging;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Random;

/**
 * What candles and candle cakes share: the {@code lit} state, lighting and putting out, and the flame and
 * smoke over every wick.
 */
public abstract class BlockAbstractCandle extends Block {

    public static final PropertyBool LIT = PropertyBool.create("lit");

    protected BlockAbstractCandle(Material material, MapColor color) {
        super(material, color);
    }

    /** @return where each wick's flame sits, relative to the block's corner */
    protected abstract Vec3d[] wicks(IBlockState state);

    /** @return whether this is an unlit candle or candle cake */
    public static boolean canLight(IBlockState state) {
        return state.getBlock() instanceof BlockAbstractCandle && !state.getValue(LIT);
    }

    /** 1.18.2's {@code canLight}: an unlit candle or candle cake, and never a waterlogged candle. */
    public static boolean canLight(IBlockAccess world, BlockPos pos, IBlockState state) {
        return canLight(state) && !Waterlogging.hasWater(world, pos);
    }

    public static boolean isLit(IBlockState state) {
        return state.getBlock() instanceof BlockAbstractCandle && state.getValue(LIT);
    }

    public static void light(World world, BlockPos pos, IBlockState state) {
        world.setBlockState(pos, state.withProperty(LIT, true), 11);
    }

    /** Puts a lit candle out: a puff of smoke over each wick and the extinguish sound. */
    public static void extinguish(World world, BlockPos pos, IBlockState state) {
        world.setBlockState(pos, state.withProperty(LIT, false), 11);
        if (world instanceof WorldServer) {
            for (Vec3d w : ((BlockAbstractCandle) state.getBlock()).wicks(state)) {
                ((WorldServer) world).spawnParticle(EnumParticleTypes.SMOKE_NORMAL, pos.getX() + w.x, pos.getY() + w.y,
                        pos.getZ() + w.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        world.playSound(null, pos, CandleContent.extinguish, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
        if (!state.getValue(LIT)) {
            return;
        }
        for (Vec3d w : wicks(state)) {
            com.exiledradio.cavesandcliffsbackport.candle.client.CandleParticles.flame(world, pos.getX() + w.x,
                    pos.getY() + w.y, pos.getZ() + w.z, rand);
        }
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }
}
