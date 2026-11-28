package com.exiledradio.cavesandcliffsbackport.powdersnow.client;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.powdersnow.BlockPowderSnow;
import com.exiledradio.cavesandcliffsbackport.powdersnow.Freezing;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Powder snow on the client: the white fog with the camera inside the block, and skeletons turning into strays
 * shaking (the frost overlay and frozen mobs shaking are Backport Core's). Registered from {@code ClientSetup} only when
 * the stream is on.
 */
public final class PowderSnowClient {


    /** What the shake added to the body yaw in Pre, taken back in Post. */
    private float shake;

    private PowderSnowClient() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new PowderSnowClient());
    }

    /** From the network thread: applied on the client thread, to the entity if the client has it. */
    public static void receive(final int entityId, final int ticks, final int flags) {
        final Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                if (mc.world != null) {
                    Entity entity = mc.world.getEntityByID(entityId);
                    if (entity != null) {
                        Freezing.received(entity, ticks, flags);
                    }
                }
            }
        });
    }

    /** The powder snow fog color of Java. */
    @SubscribeEvent
    public void fogColor(EntityViewRenderEvent.FogColors event) {
        if (event.getState().getBlock() instanceof BlockPowderSnow) {
            event.setRed(0.623F);
            event.setGreen(0.734F);
            event.setBlue(0.785F);
        }
    }

    /** The powder snow fog of Java: linear, from the eye to 2 blocks. */
    @SubscribeEvent
    public void fogDensity(EntityViewRenderEvent.FogDensity event) {
        if (event.getState().getBlock() instanceof BlockPowderSnow) {
            GlStateManager.setFog(GlStateManager.FogMode.LINEAR);
            GlStateManager.setFogStart(0.0F);
            GlStateManager.setFogEnd(2.0F);
            event.setDensity(1.0F);
            event.setCanceled(true);
        }
    }

    /** The shake of Java: the body swings by cos(age * 3.25) * pi * 0.4 degrees. */
    @SubscribeEvent
    public void shakeStart(RenderLivingEvent.Pre<EntityLivingBase> event) {
        EntityLivingBase e = event.getEntity();
        this.shake = 0.0F;
        // A frozen mob shakes through Backport Core (freeze.client.FreezeClient); a skeleton turning into a stray here.
        if (Freezing.converting(e) && !Freezing.fullyFrozen(e)) {
            this.shake = (float) (MathHelper.cos(e.ticksExisted * 3.25F) * Math.PI * 0.4D);
            e.renderYawOffset += this.shake;
            e.prevRenderYawOffset += this.shake;
        }
    }

    @SubscribeEvent
    public void shakeEnd(RenderLivingEvent.Post<EntityLivingBase> event) {
        if (this.shake != 0.0F) {
            EntityLivingBase e = event.getEntity();
            e.renderYawOffset -= this.shake;
            e.prevRenderYawOffset -= this.shake;
            this.shake = 0.0F;
        }
    }
}
