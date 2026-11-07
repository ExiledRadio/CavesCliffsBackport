package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.ModCreativeTab;

import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Loader;

/** The shared code every stream builds on. */
public final class ScaffoldSelfTest implements SelfTest {

    @Override
    public String name() {
        return "scaffold";
    }

    @Override
    public void run(Check check) {
        BlockPos area = check.area();
        check.ok(check.world.isAirBlock(area.up()), "test area is clear");
        check.ok(Loader.isModLoaded("backportcore"), "Backport Core is loaded");
        check.ok(Loader.isModLoaded(CavesAndCliffsBackport.MOD_ID), "this mod is loaded");
        check.ok(ModConfig.deepslate != null && ModConfig.caves != null, "config categories exist");
        // getTabLabel() is client-only; the tab array is not. Touch the tab first: building it grows the array.
        ModCreativeTab tab = ModCreativeTab.TAB;
        check.ok(java.util.Arrays.asList(net.minecraft.creativetab.CreativeTabs.CREATIVE_TAB_ARRAY).contains(tab), "creative tab registered");
        check.ok(com.exiledradio.backportcore.ownership.BlockOwnership.table() != null, "Core's ownership table is reachable");
    }
}
