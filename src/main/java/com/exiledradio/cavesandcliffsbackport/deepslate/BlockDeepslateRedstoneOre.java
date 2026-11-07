package com.exiledradio.cavesandcliffsbackport.deepslate;

import com.exiledradio.cavesandcliffsbackport.common.BlockCCOre;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Random;

/**
 * Deepslate redstone ore: one block with a {@code lit} property, as current Java has it (1.12 uses two
 * blocks). Touching, walking on or right-clicking it lights it (light 9, red particles); a random tick
 * puts it out. Drops 4-5 redstone, Fortune adding 0 to its level, 1-5 experience.
 */
public class BlockDeepslateRedstoneOre extends BlockCCOre {

    public static final PropertyBool LIT = PropertyBool.create("lit");

    public BlockDeepslateRedstoneOre(MapColor color, SoundType sound) {
        super(color, sound, () -> Items.REDSTONE, 0, 4, 5, Bonus.ADD, 1, 5);
        setTickRandomly(true);
        setDefaultState(this.blockState.getBaseState().withProperty(LIT, false));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, LIT);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(LIT, (meta & 1) != 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(LIT) ? 1 : 0;
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getValue(LIT) ? 9 : 0;
    }

    private void activate(World world, BlockPos pos) {
        particles(world, pos);
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() == this && !state.getValue(LIT)) {
            world.setBlockState(pos, state.withProperty(LIT, true), 3);
        }
    }

    @Override
    public void onBlockClicked(World world, BlockPos pos, EntityPlayer player) {
        activate(world, pos);
        super.onBlockClicked(world, pos, player);
    }

    @Override
    public void onEntityWalk(World world, BlockPos pos, Entity entity) {
        activate(world, pos);
        super.onEntityWalk(world, pos, entity);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        activate(world, pos);
        return super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ);
    }

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random rand) {
        if (state.getValue(LIT)) {
            world.setBlockState(pos, state.withProperty(LIT, false), 3);
        }
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Items.REDSTONE;
    }

    @Override
    protected ItemStack getSilkTouchDrop(IBlockState state) {
        return new ItemStack(this);
    }

    @Override
    public ItemStack getItem(World world, BlockPos pos, IBlockState state) {
        return new ItemStack(this);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
        if (state.getValue(LIT)) {
            particles(world, pos);
        }
    }

    /** A dust particle just outside each face that is not covered. */
    private static void particles(World world, BlockPos pos) {
        if (!world.isRemote) {
            return;
        }
        Random r = world.rand;
        for (EnumFacing face : EnumFacing.values()) {
            if (world.getBlockState(pos.offset(face)).isOpaqueCube()) {
                continue;
            }
            double x = pos.getX() + (face.getXOffset() == 0 ? r.nextDouble() : 0.5 + face.getXOffset() * 0.5625);
            double y = pos.getY() + (face.getYOffset() == 0 ? r.nextDouble() : 0.5 + face.getYOffset() * 0.5625);
            double z = pos.getZ() + (face.getZOffset() == 0 ? r.nextDouble() : 0.5 + face.getZOffset() * 0.5625);
            world.spawnParticle(EnumParticleTypes.REDSTONE, x, y, z, 0, 0, 0);
        }
    }
}
