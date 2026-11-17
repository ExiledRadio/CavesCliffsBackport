package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** {@link StandInOres} draw as vanilla's ores: iron (copper and the left-out two), then diamond, emerald and redstone. */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID, value = Side.CLIENT)
public final class StandInOresClient {

    private static final String[] GEMS = {"diamond_ore", "emerald_ore", "redstone_ore"};

    private StandInOresClient() {
    }

    @SubscribeEvent
    public static void models(ModelRegistryEvent event) {
        final ModelResourceLocation iron = new ModelResourceLocation("minecraft:iron_ore", "normal");
        StateMapperBase asIron = new StateMapperBase() {
            @Override
            protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                return iron;
            }
        };
        ModelLoader.setCustomStateMapper(StandInOres.copperOre, asIron);
        ModelLoader.setCustomStateMapper(StandInOres.netherOre, asIron);
        ModelLoader.setCustomStateMapper(StandInOres.excludedOre, asIron);
        ModelLoader.setCustomStateMapper(StandInOres.sandOre, asIron);
        ModelLoader.setCustomStateMapper(StandInOres.clathrateOre, asIron);
        ModelLoader.setCustomStateMapper(StandInOres.deepslateOre, asIron);
        ModelLoader.setCustomStateMapper(StandInOres.enderKeyOre, asIron);
        ModelLoader.setCustomStateMapper(StandInOres.gemOre, new StateMapperBase() {
            @Override
            protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                return new ModelResourceLocation("minecraft:" + GEMS[state.getValue(StandInOres.GemOre.VARIANT)], "normal");
            }
        });
        for (Item i : StandInOres.ITEMS) {
            if (i.getHasSubtypes()) {
                for (int m = 0; m < 3; m++) {
                    ModelLoader.setCustomModelResourceLocation(i, m, new ModelResourceLocation("minecraft:" + GEMS[m], "inventory"));
                }
            } else {
                ModelLoader.setCustomModelResourceLocation(i, 0, new ModelResourceLocation("minecraft:iron_ore", "inventory"));
            }
        }
    }
}
