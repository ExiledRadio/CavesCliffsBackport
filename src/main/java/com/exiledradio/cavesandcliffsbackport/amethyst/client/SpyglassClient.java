package com.exiledradio.cavesandcliffsbackport.amethyst.client;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.amethyst.ItemSpyglass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Looking through the spyglass, client side: the field of view drops to a tenth (as Java's), the held
 * item is hidden, and the scope texture is drawn over the screen with black around it; the crosshair and
 * the rest of the HUD stay hidden while scoping in first person.
 */
public final class SpyglassClient {

    private static final ResourceLocation SCOPE = new ResourceLocation(CavesAndCliffsBackport.MOD_ID, "textures/misc/spyglass_scope.png");

    private SpyglassClient() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new SpyglassClient());
    }

    static boolean scoping() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.player;
        return p != null && mc.gameSettings.thirdPersonView == 0 && p.isHandActive()
                && p.getActiveItemStack().getItem() instanceof ItemSpyglass;
    }

    @SubscribeEvent
    public void fov(FOVUpdateEvent event) {
        if (scoping()) {
            event.setNewfov(0.1F);
        }
    }

    @SubscribeEvent
    public void hand(RenderHandEvent event) {
        if (scoping()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void overlay(RenderGameOverlayEvent.Pre event) {
        if (!scoping()) {
            return;
        }
        if (event.getType() == RenderGameOverlayEvent.ElementType.CROSSHAIRS) {
            event.setCanceled(true);
            return;
        }
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        ScaledResolution res = event.getResolution();
        int w = res.getScaledWidth(), h = res.getScaledHeight();
        int size = Math.min(w, h);
        int x = (w - size) / 2, y = (h - size) / 2;
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.color(1, 1, 1, 1);
        Minecraft.getMinecraft().getTextureManager().bindTexture(SCOPE);
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, size, size, size, size);
        Gui.drawRect(0, 0, w, y, 0xFF000000);
        Gui.drawRect(0, y + size, w, h, 0xFF000000);
        Gui.drawRect(0, y, x, y + size, 0xFF000000);
        Gui.drawRect(x + size, y, w, y + size, 0xFF000000);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.color(1, 1, 1, 1);
    }
}
