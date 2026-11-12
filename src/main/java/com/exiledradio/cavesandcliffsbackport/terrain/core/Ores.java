package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.util.Arrays;
import java.util.List;

import static com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes.*;
import static com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator.*;

/**
 * The overworld's Feature.ORE placed features, from data/worldgen/placement/OrePlacements and
 * features/OreFeatures: commonOrePlacement (count, in square, height, biome), rareOrePlacement (rarity, in square,
 * height, biome), orePlacement with a uniform count (ore_gold_lower). Heights are VerticalAnchors resolved for the
 * overworld (bottom -64, top 319, aboveBottom(n) -64 + n); triangle() is a trapezoid with plateau 0.
 */
final class Ores {
    private Ores() {
    }

    private static final int N = RULE_NATURAL_STONE;
    private static final int S = RULE_STONE_ORE_REPLACEABLES;
    private static final int D = RULE_DEEPSLATE_ORE_REPLACEABLES;
    private static final int BOTTOM = -64;
    private static final int TOP = 319;

    private static int[] common(int count, int... height) {
        return concat(new int[]{Decorator.COUNT, count, IN_SQUARE}, height, new int[]{BIOME});
    }

    private static int[] rare(int chance, int... height) {
        return concat(new int[]{RARITY, chance, IN_SQUARE}, height, new int[]{BIOME});
    }

    private static int[] uniform(int min, int max) {
        return new int[]{HEIGHT_UNIFORM, min, max};
    }

    private static int[] triangle(int min, int max) {
        return new int[]{HEIGHT_TRAPEZOID, min, max, 0};
    }

    private static int[] concat(int[]... parts) {
        int n = 0;
        for (int[] p : parts) {
            n += p.length;
        }
        int[] out = new int[n];
        int at = 0;
        for (int[] p : parts) {
            System.arraycopy(p, 0, out, at, p.length);
            at += p.length;
        }
        return out;
    }

    private static Placed placed(String name, Feature f, int[] ops) {
        return new Placed(name, f, ops);
    }

    static List<Placed> placed() {
        Ore dirt = new Ore(33, 0.0F, N, DIRT);
        Ore gravel = new Ore(33, 0.0F, N, GRAVEL);
        Ore granite = new Ore(64, 0.0F, N, GRANITE);
        Ore diorite = new Ore(64, 0.0F, N, DIORITE);
        Ore andesite = new Ore(64, 0.0F, N, ANDESITE);
        Ore tuff = new Ore(64, 0.0F, N, TUFF);
        Ore coal = new Ore(17, 0.0F, S, COAL_ORE, D, DEEPSLATE_COAL_ORE);
        Ore coalBuried = new Ore(17, 0.5F, S, COAL_ORE, D, DEEPSLATE_COAL_ORE);
        Ore iron = new Ore(9, 0.0F, S, IRON_ORE, D, DEEPSLATE_IRON_ORE);
        Ore ironSmall = new Ore(4, 0.0F, S, IRON_ORE, D, DEEPSLATE_IRON_ORE);
        Ore gold = new Ore(9, 0.0F, S, GOLD_ORE, D, DEEPSLATE_GOLD_ORE);
        Ore goldBuried = new Ore(9, 0.5F, S, GOLD_ORE, D, DEEPSLATE_GOLD_ORE);
        Ore redstone = new Ore(8, 0.0F, S, REDSTONE_ORE, D, DEEPSLATE_REDSTONE_ORE);
        Ore diamondSmall = new Ore(4, 0.5F, S, DIAMOND_ORE, D, DEEPSLATE_DIAMOND_ORE);
        Ore diamondLarge = new Ore(12, 0.7F, S, DIAMOND_ORE, D, DEEPSLATE_DIAMOND_ORE);
        Ore diamondBuried = new Ore(8, 1.0F, S, DIAMOND_ORE, D, DEEPSLATE_DIAMOND_ORE);
        Ore lapis = new Ore(7, 0.0F, S, LAPIS_ORE, D, DEEPSLATE_LAPIS_ORE);
        Ore lapisBuried = new Ore(7, 1.0F, S, LAPIS_ORE, D, DEEPSLATE_LAPIS_ORE);
        Ore infested = new Ore(9, 0.0F, S, INFESTED_STONE, D, INFESTED_DEEPSLATE);
        Ore emerald = new Ore(3, 0.0F, S, EMERALD_ORE, D, DEEPSLATE_EMERALD_ORE);
        Ore copperSmall = new Ore(10, 0.0F, S, COPPER_ORE, D, DEEPSLATE_COPPER_ORE);
        Ore copperLarge = new Ore(20, 0.0F, S, COPPER_ORE, D, DEEPSLATE_COPPER_ORE);
        Ore clay = new Ore(33, 0.0F, N, CLAY);
        return Arrays.asList(
            placed("ore_dirt", dirt, common(7, uniform(0, 160))),
            placed("ore_gravel", gravel, common(14, uniform(BOTTOM, TOP))),
            placed("ore_granite_upper", granite, rare(6, uniform(64, 128))),
            placed("ore_granite_lower", granite, common(2, uniform(0, 60))),
            placed("ore_diorite_upper", diorite, rare(6, uniform(64, 128))),
            placed("ore_diorite_lower", diorite, common(2, uniform(0, 60))),
            placed("ore_andesite_upper", andesite, rare(6, uniform(64, 128))),
            placed("ore_andesite_lower", andesite, common(2, uniform(0, 60))),
            placed("ore_tuff", tuff, common(2, uniform(BOTTOM, 0))),
            placed("ore_coal_upper", coal, common(30, uniform(136, TOP))),
            placed("ore_coal_lower", coalBuried, common(20, triangle(0, 192))),
            placed("ore_iron_upper", iron, common(90, triangle(80, 384))),
            placed("ore_iron_middle", iron, common(10, triangle(-24, 56))),
            placed("ore_iron_small", ironSmall, common(10, uniform(BOTTOM, 72))),
            placed("ore_gold_extra", gold, common(50, uniform(32, 256))),
            placed("ore_gold", goldBuried, common(4, triangle(-64, 32))),
            placed("ore_gold_lower", goldBuried, concat(new int[]{COUNT_UNIFORM, 0, 1, IN_SQUARE}, uniform(-64, -48),
                new int[]{BIOME})),
            placed("ore_redstone", redstone, common(4, uniform(BOTTOM, 15))),
            placed("ore_redstone_lower", redstone, common(8, triangle(BOTTOM - 32, BOTTOM + 32))),
            placed("ore_diamond", diamondSmall, common(7, triangle(BOTTOM - 80, BOTTOM + 80))),
            placed("ore_diamond_large", diamondLarge, rare(9, triangle(BOTTOM - 80, BOTTOM + 80))),
            placed("ore_diamond_buried", diamondBuried, common(4, triangle(BOTTOM - 80, BOTTOM + 80))),
            placed("ore_lapis", lapis, common(2, triangle(-32, 32))),
            placed("ore_lapis_buried", lapisBuried, common(4, uniform(BOTTOM, 64))),
            placed("ore_infested", infested, common(14, uniform(BOTTOM, 63))),
            placed("ore_emerald", emerald, common(100, triangle(-16, 480))),
            placed("ore_copper", copperSmall, common(16, triangle(-16, 112))),
            placed("ore_copper_large", copperLarge, common(16, triangle(-16, 112))),
            placed("ore_clay", clay, common(46, uniform(BOTTOM, 256))));
    }
}
