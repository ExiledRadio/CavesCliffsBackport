package com.exiledradio.cavesandcliffsbackport.goat.client;

import com.exiledradio.cavesandcliffsbackport.goat.EntityGoat;

import net.minecraftforge.fml.client.registry.RenderingRegistry;

/** Client half of the goat stream: the renderer. Reached from {@code ClientSetup} only when goats are on. */
public final class GoatClient {

    private GoatClient() {
    }

    public static void init() {
        RenderingRegistry.registerEntityRenderingHandler(EntityGoat.class, RenderGoat::new);
    }
}
