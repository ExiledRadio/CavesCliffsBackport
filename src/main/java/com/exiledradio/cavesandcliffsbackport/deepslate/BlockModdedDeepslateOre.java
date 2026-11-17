package com.exiledradio.cavesandcliffsbackport.deepslate;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * The deepslate form of another mod's stone ore ({@link ModdedOres}), by 1.18's rules for deepslate ores: 1.5 times as
 * hard (at least 4.5), deepslate's sound, and everything else - harvest tool and level, drops, experience, Fortune,
 * Silk Touch, light, render layers - handed to the original ore with its own state. Wherever the original drops itself,
 * this drops itself instead, as a deepslate ore does.
 */
public class BlockModdedDeepslateOre extends Block {

    final ModdedOres.Form form;

    BlockModdedDeepslateOre(ModdedOres.Form form, SoundType sound, float hardness, float resistance) {
        super(Material.ROCK, MapColor.STONE);
        this.form = form;
        setSoundType(sound);
        setHardness(hardness);
        setResistance(resistance);
    }

    public IBlockState original() {
        return this.form.original;
    }

    // ---- Harvesting: the original's tool and level, asked each time (some mods set them in init). ----

    @Nullable
    @Override
    public String getHarvestTool(IBlockState state) {
        return this.form.original.getBlock().getHarvestTool(this.form.original);
    }

    @Override
    public int getHarvestLevel(IBlockState state) {
        return this.form.original.getBlock().getHarvestLevel(this.form.original);
    }

    @Override
    public boolean isToolEffective(String type, IBlockState state) {
        return this.form.original.getBlock().isToolEffective(type, this.form.original);
    }

    // ---- Drops, experience, Fortune and Silk Touch: the original's. ----

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        NonNullList<ItemStack> theirs = NonNullList.create();
        this.form.original.getBlock().getDrops(theirs, world, pos, this.form.original, fortune);
        for (ItemStack s : theirs) {
            drops.add(this.form.swapSelf(s));
        }
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        Item theirs = this.form.original.getBlock().getItemDropped(this.form.original, rand, fortune);
        return theirs == this.form.originalItem() ? Item.getItemFromBlock(this) : theirs;
    }

    @Override
    public int getExpDrop(IBlockState state, IBlockAccess world, BlockPos pos, int fortune) {
        return this.form.original.getBlock().getExpDrop(this.form.original, world, pos, fortune);
    }

    @Override
    public boolean canSilkHarvest(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        return this.form.original.getBlock().canSilkHarvest(world, pos, this.form.original, player);
    }

    // ---- Light and drawing. ----

    @SuppressWarnings("deprecation")
    @Override
    public int getLightValue(IBlockState state) {
        return this.form.original.getLightValue();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public BlockRenderLayer getRenderLayer() {
        return this.form.original.getBlock().getRenderLayer();
    }

    @Override
    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return this.form.original.getBlock().canRenderInLayer(this.form.original, layer);
    }

    @Override
    public String getLocalizedName() {
        return this.form.displayName();
    }
}
