package com.exiledradio.cavesandcliffsbackport.deepslate;

import com.exiledradio.cavesandcliffsbackport.common.BlockBasic;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;

import java.util.Random;
import java.util.function.Supplier;

/**
 * An ore block dropping an item, with 1.12's (and current Java's) two Fortune rules:
 * {@link Bonus#MULTIPLY} (coal, lapis, diamond, emerald, the raw ores: the count is multiplied by a random
 * 1 to fortune + 1, weighted toward 1) and {@link Bonus#ADD} (redstone: 0 to fortune extra).
 * Experience is dropped only when the drop is not the block itself.
 */
public class BlockCCOre extends BlockBasic {

    public enum Bonus {
        MULTIPLY, ADD
    }

    private final Supplier<Item> drop;
    private final int meta;
    private final int min, max;
    private final Bonus bonus;
    private final int xpMin, xpMax;

    /**
     * @param drop what it drops, or a supplier returning null to drop itself (raw ore drops switched off)
     */
    public BlockCCOre(MapColor color, SoundType sound, Supplier<Item> drop, int meta, int min, int max, Bonus bonus,
                      int xpMin, int xpMax) {
        super(Material.ROCK, color, sound, 4.5F, 3.0F);
        this.drop = drop;
        this.meta = meta;
        this.min = min;
        this.max = max;
        this.bonus = bonus;
        this.xpMin = xpMin;
        this.xpMax = xpMax;
    }

    private boolean dropsItself() {
        return this.drop.get() == null;
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        Item item = this.drop.get();
        return item != null ? item : Item.getItemFromBlock(this);
    }

    @Override
    public int damageDropped(IBlockState state) {
        return dropsItself() ? 0 : this.meta;
    }

    @Override
    public int quantityDropped(Random random) {
        return dropsItself() ? 1 : this.min + (this.max > this.min ? random.nextInt(this.max - this.min + 1) : 0);
    }

    @Override
    public int quantityDroppedWithBonus(int fortune, Random random) {
        int base = quantityDropped(random);
        if (fortune <= 0 || dropsItself()) {
            return base;
        }
        if (this.bonus == Bonus.ADD) {
            return base + random.nextInt(fortune + 1);
        }
        int i = random.nextInt(fortune + 2) - 1;
        return base * (Math.max(i, 0) + 1);
    }

    @Override
    public int getExpDrop(IBlockState state, IBlockAccess world, BlockPos pos, int fortune) {
        if (dropsItself() || this.xpMax <= 0) {
            return 0;
        }
        Random rand = world instanceof net.minecraft.world.World ? ((net.minecraft.world.World) world).rand : new Random();
        return MathHelper.getInt(rand, this.xpMin, this.xpMax);
    }
}
