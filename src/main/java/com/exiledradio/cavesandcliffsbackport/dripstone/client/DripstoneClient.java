package com.exiledradio.cavesandcliffsbackport.dripstone.client;

import com.exiledradio.cavesandcliffsbackport.dripstone.DripstoneContent;
import com.exiledradio.cavesandcliffsbackport.dripstone.EntityFallingStalactite;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleDrip;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

/** Stream Dr's client side: the drip particle with its landing sound (Backport Core draws falling stalactites). */
public final class DripstoneClient {

    private DripstoneClient() {
    }

    public static void init() {
        RenderingRegistry.registerEntityRenderingHandler(EntityFallingStalactite.class, new IRenderFactory<EntityFallingStalactite>() {
            @Override
            public Render<? super EntityFallingStalactite> createRenderFor(RenderManager manager) {
                return new RenderFalling(manager);
            }
        });
    }

    /** A drop that hangs under a stalactite's tip, falls, and is heard when it lands. */
    public static void drip(World world, double x, double y, double z, boolean lava) {
        Minecraft.getMinecraft().effectRenderer.addEffect(new Drip(world, x, y, z, lava));
    }

    private static final class Drip extends ParticleDrip {
        private final boolean lava;
        private boolean landed;

        Drip(World world, double x, double y, double z, boolean lava) {
            super(world, x, y, z, lava ? Material.LAVA : Material.WATER);
            this.lava = lava;
        }

        @Override
        public void onUpdate() {
            super.onUpdate();
            if (this.onGround && !this.landed) {
                this.landed = true;
                float volume = 0.3F + this.rand.nextFloat() * 0.7F;
                this.world.playSound(this.posX, this.posY, this.posZ, this.lava ? DripstoneContent.dripLava : DripstoneContent.dripWater,
                        SoundCategory.BLOCKS, volume, 1.0F, false);
            }
        }
    }

    /** 1.1.7's falling stalactite, which turns into Core's on its first server tick: nothing to draw. */
    private static final class RenderFalling extends Render<EntityFallingStalactite> {

        RenderFalling(RenderManager manager) {
            super(manager);
        }

        @Override
        public void doRender(EntityFallingStalactite entity, double x, double y, double z, float yaw, float partialTicks) {
        }

        @Override
        protected ResourceLocation getEntityTexture(EntityFallingStalactite entity) {
            return TextureMap.LOCATION_BLOCKS_TEXTURE;
        }
    }
}
