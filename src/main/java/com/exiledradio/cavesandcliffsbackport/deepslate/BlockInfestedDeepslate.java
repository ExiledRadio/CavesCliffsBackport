package com.exiledradio.cavesandcliffsbackport.deepslate;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Infested deepslate: looks like deepslate (with its axis), breaks in half the time, drops nothing and
 * lets a silverfish out; with Silk Touch it drops plain deepslate and no silverfish.
 */
public class BlockInfestedDeepslate extends BlockCCPillar {

    private ItemStack silkDrop = ItemStack.EMPTY;

    public BlockInfestedDeepslate(MapColor color, SoundType sound) {
        super(Material.CLAY, color, sound, 1.5F, 0.75F);
    }

    public void silkTouchGives(ItemStack stack) {
        this.silkDrop = stack;
    }

    @Override
    public int quantityDropped(Random random) {
        return 0;
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return net.minecraft.init.Items.AIR;
    }

    @Override
    protected ItemStack getSilkTouchDrop(IBlockState state) {
        return this.silkDrop.copy();
    }

    @Override
    public void dropBlockAsItemWithChance(World world, BlockPos pos, IBlockState state, float chance, int fortune) {
        if (!world.isRemote && world.getGameRules().getBoolean("doTileDrops")) {
            EntitySilverfish silverfish = new EntitySilverfish(world);
            silverfish.setLocationAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            world.spawnEntity(silverfish);
            silverfish.spawnExplosionParticle();
        }
    }
}
