package com.exiledradio.cavesandcliffsbackport.deepslate;

import com.exiledradio.cavesandcliffsbackport.common.BlockBasic;

import net.minecraft.block.BlockRotatedPillar;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * A block with an axis, as logs have: deepslate. Without Silk Touch it may drop another block
 * (deepslate drops cobbled deepslate); with it, or when no other drop is set, it drops itself.
 */
public class BlockCCPillar extends BlockRotatedPillar {

    @Nullable
    private IBlockState drop;

    public BlockCCPillar(Material material, MapColor color, SoundType sound, float hardness, float resistance) {
        super(material, color);
        setSoundType(sound);
        setHardness(hardness);
        BlockBasic.resistance(this, resistance);
    }

    /** What breaking it without Silk Touch gives, set once the other block exists. */
    public void dropsWithoutSilkTouch(IBlockState state) {
        this.drop = state;
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return this.drop != null ? Item.getItemFromBlock(this.drop.getBlock()) : Item.getItemFromBlock(this);
    }

    @Override
    public int damageDropped(IBlockState state) {
        return this.drop != null ? this.drop.getBlock().getMetaFromState(this.drop) : 0;
    }

    @Override
    protected ItemStack getSilkTouchDrop(IBlockState state) {
        return new ItemStack(this);
    }
}
