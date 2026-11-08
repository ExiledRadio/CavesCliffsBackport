package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;

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
        // No tab of its own: its blocks are in vanilla's tabs (Backport Core's creative placement).
        check.ok(com.exiledradio.backportcore.creative.VanillaOrder.tab("minecraft:deepslate")
                == net.minecraft.creativetab.CreativeTabs.BUILDING_BLOCKS, "Core's vanilla creative order is readable");
        check.ok(com.exiledradio.backportcore.ownership.BlockOwnership.table() != null, "Core's ownership table is reachable");

        // Ownership, per stream, as a later update asks it during its own block registration (Rule 1).
        String later = "trickytrialsbackport";
        Object[][] samples = {{ModConfig.deepslate.enabled, "tuff"}, {ModConfig.deepslate.enabled, "cobbled_deepslate_double_slab"},
                {ModConfig.copper.enabled, "copper_block"}, {ModConfig.copper.enabled, "waxed_oxidized_cut_copper_double_slab"},
                {ModConfig.copper.enabled, "lightning_rod"}, {ModConfig.amethyst.enabled, "amethyst_block"}};
        for (Object[] sample : samples) {
            boolean on = (Boolean) sample[0];
            String name = (String) sample[1];
            check.eq(!on, com.exiledradio.backportcore.ownership.BlockOwnership.shouldRegister(later, name),
                    "a later update registers its own " + name + " exactly when our stream is off");
            check.eq(on ? CavesAndCliffsBackport.MOD_ID : com.exiledradio.cavesandcliffsbackport.common.Owners.SWITCHED_OFF,
                    com.exiledradio.backportcore.ownership.BlockOwnership.owner(name), "owner of " + name);
        }
    }
}
