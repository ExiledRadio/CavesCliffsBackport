package com.exiledradio.cavesandcliffsbackport.glowsquid.client;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;

import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderSquid;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.util.ResourceLocation;

/**
 * The squid's renderer with the glow squid's texture (the same layout). The brightness is the entity's own
 * ({@code EntityGlowSquid.getBrightnessForRender}), which the render manager applies before drawing.
 */
public class RenderGlowSquid extends RenderSquid {

    private static final ResourceLocation TEXTURE = new ResourceLocation(CavesAndCliffsBackport.MOD_ID, "textures/entity/squid/glow_squid.png");

    public RenderGlowSquid(RenderManager manager) {
        super(manager);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntitySquid entity) {
        return TEXTURE;
    }
}
