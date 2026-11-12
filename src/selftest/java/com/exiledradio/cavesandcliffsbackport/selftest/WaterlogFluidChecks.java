package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;

import git.jbredwards.fluidlogged_api.api.util.FluidState;
import git.jbredwards.fluidlogged_api.api.util.FluidloggedUtils;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * The part of {@link WaterlogSelfTest} that names Fluidlogged API's own types: only loaded when it is installed, so
 * the suite still runs with {@code -PnoFluidlogged}.
 */
final class WaterlogFluidChecks {

    private WaterlogFluidChecks() {
    }

    /** Every state of every block of ours against 1.18.2: which take a water source; none take flowing water or lava. */
    static void states(Check c, BlockPos probe) {
        WorldServer world = c.world;
        FluidState source = FluidState.of(FluidRegistry.WATER);
        FluidState flowing = source.withLevel(3);
        FluidState lava = FluidState.of(FluidRegistry.LAVA);
        int states = 0, holding = 0;
        List<String> wrong = new ArrayList<String>();
        for (Block block : ForgeRegistries.BLOCKS) {
            ResourceLocation name = block.getRegistryName();
            if (name == null || !CavesAndCliffsBackport.MOD_ID.equals(name.getNamespace())) {
                continue;
            }
            boolean expected = WaterlogSelfTest.waterloggable(name.getPath());
            for (IBlockState state : block.getBlockState().getValidStates()) {
                states++;
                boolean takes = FluidloggedUtils.isStateFluidloggable(state, world, probe, source);
                if (takes) {
                    holding++;
                }
                if (takes != expected) {
                    wrong.add(state + (expected ? " holds no water" : " takes water"));
                }
                if (FluidloggedUtils.isStateFluidloggable(state, world, probe, flowing)) {
                    wrong.add(state + " takes flowing water");
                }
                if (FluidloggedUtils.isStateFluidloggable(state, world, probe, lava)) {
                    wrong.add(state + " takes lava");
                }
            }
        }
        c.ok(wrong.isEmpty(), states + " states of ours checked against 1.18.2 (" + holding + " hold water), wrong: "
                + (wrong.size() > 12 ? wrong.subList(0, 12) + " and " + (wrong.size() - 12) + " more" : wrong));
        c.ok(holding > 200, "a fair number of states hold water: " + holding);
        CavesAndCliffsBackport.LOGGER.info("SELFTEST waterlog: {} states of ours, {} hold a water source, none flowing water or lava",
                states, holding);
    }
}
