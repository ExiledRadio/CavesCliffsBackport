package com.exiledradio.cavesandcliffsbackport.lush;

import com.exiledradio.cavesandcliffsbackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

/** Current Java's block tags the lush blocks ask about, as plain checks. */
public final class Soil {

    private Soil() {
    }

    /** The {@code dirt} tag: dirt of every kind, grass, mycelium, rooted dirt, moss. */
    public static boolean dirt(IBlockState s) {
        Block b = s.getBlock();
        return b == Blocks.DIRT || b == Blocks.GRASS || b == Blocks.MYCELIUM || b == LushContent.rootedDirt || b == LushContent.moss;
    }

    /** Azaleas: dirt, farmland and clay. */
    public static boolean azalea(IBlockState s) {
        return dirt(s) || s.getBlock() == Blocks.FARMLAND || s.getBlock() == Blocks.CLAY;
    }

    /** Big dripleaf: clay, moss, dirt and farmland. */
    public static boolean bigDripleaf(IBlockState s) {
        return azalea(s);
    }

    /** Small dripleaf out of water: clay and moss only. */
    public static boolean smallDripleaf(IBlockState s) {
        return s.getBlock() == Blocks.CLAY || s.getBlock() == LushContent.moss;
    }

    /** The overworld base stone: stone of every kind, deepslate and tuff (stream D, by name). */
    public static boolean baseStone(IBlockState s) {
        Block b = s.getBlock();
        if (b == Blocks.STONE) {
            return true;
        }
        look();
        return b != Blocks.AIR && (b == deepslate || b == tuff);
    }

    /** {@code moss_replaceable}: what moss spreads over - base stone and dirt. */
    public static boolean mossReplaceable(IBlockState s) {
        return baseStone(s) || dirt(s);
    }

    /** {@code lush_ground_replaceable}: what a lush cave floor or ceiling patch replaces. */
    public static boolean lushReplaceable(IBlockState s) {
        Block b = s.getBlock();
        return mossReplaceable(s) || b == Blocks.CLAY || b == Blocks.GRAVEL || b == Blocks.SAND;
    }

    private static Block deepslate, tuff;
    private static boolean looked;

    private static void look() {
        if (!looked) {
            looked = true;
            deepslate = Owners.ourBlock("deepslate");
            tuff = Owners.ourBlock("tuff");
        }
    }
}
