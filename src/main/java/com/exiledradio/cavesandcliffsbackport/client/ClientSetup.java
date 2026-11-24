package com.exiledradio.cavesandcliffsbackport.client;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.common.Content;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Map;

/**
 * Client-only setup. A dedicated server never loads this class: the mod class reaches it through a
 * no-argument call, which names no client type.
 *
 * <ul>
 *   <li>A model for every item model request in {@link Content#itemModels()} - by exact name, never by
 *       walking the model registry's keys, which some performance mods bake lazily.</li>
 *   <li>One model name per multipart block ({@link Content#multipart}).</li>
 * </ul>
 */
public final class ClientSetup {

    private ClientSetup() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new ClientSetup());
        MinecraftForge.EVENT_BUS.register(new com.exiledradio.cavesandcliffsbackport.height.client.CreateWorldDefault());
        if (com.exiledradio.cavesandcliffsbackport.ModConfig.amethyst.enabled) {
            com.exiledradio.cavesandcliffsbackport.amethyst.client.SpyglassClient.init();
        }
        if (com.exiledradio.cavesandcliffsbackport.ModConfig.dripstone.enabled) {
            com.exiledradio.cavesandcliffsbackport.dripstone.client.DripstoneClient.init();
        }
        if (com.exiledradio.cavesandcliffsbackport.ModConfig.powderSnow.enabled) {
            com.exiledradio.cavesandcliffsbackport.powdersnow.client.PowderSnowClient.init();
        }
        if (com.exiledradio.cavesandcliffsbackport.ModConfig.glowSquid.enabled) {
            com.exiledradio.cavesandcliffsbackport.glowsquid.client.GlowSquidClient.init();
        }
        if (com.exiledradio.cavesandcliffsbackport.ModConfig.axolotls.enabled) {
            com.exiledradio.cavesandcliffsbackport.axolotl.client.AxolotlClient.init();
        }
        if (com.exiledradio.cavesandcliffsbackport.ModConfig.goats.enabled) {
            com.exiledradio.cavesandcliffsbackport.goat.client.GoatClient.init();
        }
        if (com.exiledradio.cavesandcliffsbackport.ModConfig.blocks.enabled) {
            com.exiledradio.cavesandcliffsbackport.blocks.client.LightClient.init();
        }
        if (com.exiledradio.cavesandcliffsbackport.deepslate.ModdedOres.enabled()) {
            com.exiledradio.cavesandcliffsbackport.deepslate.client.ModdedOreClient.init();
        }
        com.exiledradio.cavesandcliffsbackport.music.client.MusicClient.init();
        if ("lookfeel".equals(System.getProperty(CavesAndCliffsBackport.MOD_ID + ".clienttest"))) {
            // A test build's client test (runClient -Pclienttest=lookfeel), reached by name: a release has no test classes.
            // 1.17's and 1.18's look and feel is Backport Core's since 1.1.5 (versions.Look118); the test checks it with this mod.
            try {
                Class.forName("com.exiledradio.cavesandcliffsbackport.selftest.LookFeelClientSelfTest")
                        .getMethod("start", String.class).invoke(null, "lookfeel");
            } catch (ReflectiveOperationException e) {
                CavesAndCliffsBackport.LOGGER.error("CLIENT SELFTEST DONE pass=0 fail=1: the test classes are not in this build.", e);
            }
        }
        if ("particles".equals(System.getProperty(CavesAndCliffsBackport.MOD_ID + ".clienttest"))) {
            // A test build's client test (runClient -Pclienttest=particles), reached by name: a release has no test classes.
            try {
                Class.forName("com.exiledradio.cavesandcliffsbackport.selftest.ParticleClientSelfTest")
                        .getMethod("start", String.class).invoke(null, "particles");
            } catch (ReflectiveOperationException e) {
                CavesAndCliffsBackport.LOGGER.error("CLIENT SELFTEST DONE pass=0 fail=1: the test classes are not in this build.", e);
            }
        }
        if ("babyrender".equals(System.getProperty(CavesAndCliffsBackport.MOD_ID + ".clienttest"))) {
            // runClient -Pclienttest=babyrender: 26.1's baby models drawn in a flat world, with screenshots.
            try {
                Class.forName("com.exiledradio.cavesandcliffsbackport.selftest.ClientBabyRenderTest").getMethod("register").invoke(null);
            } catch (ReflectiveOperationException e) {
                CavesAndCliffsBackport.LOGGER.error("BABYRENDER DONE pass=0 fail=1: the test classes are not in this build.", e);
            }
        }
    }

    @SubscribeEvent
    public void registerModels(ModelRegistryEvent event) {
        for (Map.Entry<Item, Map<Integer, String>> entry : Content.itemModels().entrySet()) {
            for (Map.Entry<Integer, String> meta : entry.getValue().entrySet()) {
                ModelLoader.setCustomModelResourceLocation(entry.getKey(), meta.getKey(),
                        new ModelResourceLocation(CavesAndCliffsBackport.MOD_ID + ":" + meta.getValue(), "inventory"));
            }
        }
        for (final Block block : Content.multipartBlocks()) {
            final ModelResourceLocation one = new ModelResourceLocation(block.getRegistryName(), "multipart");
            ModelLoader.setCustomStateMapper(block, new StateMapperBase() {
                @Override
                protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                    return one;
                }
            });
        }
    }

}
