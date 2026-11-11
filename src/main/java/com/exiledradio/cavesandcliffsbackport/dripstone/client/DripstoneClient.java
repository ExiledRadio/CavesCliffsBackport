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

/** Stream Dr's client side: the falling stalactite's renderer and the drip particle with its landing sound. */
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

    /** Draws the segment's block model at the entity, as vanilla draws a falling block. */
    private static final class RenderFalling extends Render<EntityFallingStalactite> {

        RenderFalling(RenderManager manager) {
            super(manager);
            this.shadowSize = 0.5F;
        }

        @Override
        public void doRender(EntityFallingStalactite entity, double x, double y, double z, float yaw, float partialTicks) {
            IBlockState state = entity.getState();
            World world = entity.world;
            if (state.getRenderType() != EnumBlockRenderType.MODEL || state == world.getBlockState(new BlockPos(entity))) {
                return;
            }
            bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.pushMatrix();
            GlStateManager.disableLighting();
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            if (this.renderOutlines) {
                GlStateManager.enableColorMaterial();
                GlStateManager.enableOutlineMode(getTeamColor(entity));
            }
            buffer.begin(7, DefaultVertexFormats.BLOCK);
            BlockPos pos = new BlockPos(entity.posX, entity.getEntityBoundingBox().maxY, entity.posZ);
            GlStateManager.translate((float) (x - pos.getX() - 0.5), (float) (y - pos.getY()), (float) (z - pos.getZ() - 0.5));
            BlockRendererDispatcher dispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();
            dispatcher.getBlockModelRenderer().renderModel(world, dispatcher.getModelForState(state), state, pos, buffer, false,
                    MathHelper.getPositionRandom(pos));
            tessellator.draw();
            if (this.renderOutlines) {
                GlStateManager.disableOutlineMode();
                GlStateManager.disableColorMaterial();
            }
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
            super.doRender(entity, x, y, z, yaw, partialTicks);
        }

        @Override
        protected ResourceLocation getEntityTexture(EntityFallingStalactite entity) {
            return TextureMap.LOCATION_BLOCKS_TEXTURE;
        }
    }
}
