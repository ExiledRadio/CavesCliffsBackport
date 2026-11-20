package com.exiledradio.cavesandcliffsbackport.amethyst.client;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.amethyst.AmethystContent;
import com.exiledradio.cavesandcliffsbackport.amethyst.ItemSpyglass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import java.util.List;

/**
 * Looking through the spyglass, client side: the field of view drops to a tenth (as Java's), the held
 * item is hidden, and the scope texture is drawn over the screen with black around it; the crosshair and
 * the rest of the HUD stay hidden while scoping in first person. As Java's, it is held as the 3D
 * {@code spyglass_in_hand} model and shown as the flat icon in the GUI, on the ground and in frames.
 */
public final class SpyglassClient {

    private static final ResourceLocation SCOPE = new ResourceLocation(CavesAndCliffsBackport.MOD_ID, "textures/misc/spyglass_scope.png");

    private SpyglassClient() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new SpyglassClient());
    }

    private static ModelResourceLocation inventory(String name) {
        return new ModelResourceLocation(CavesAndCliffsBackport.MOD_ID + ":" + name, "inventory");
    }

    @SubscribeEvent
    public void registerModels(ModelRegistryEvent event) {
        if (AmethystContent.spyglass != null) {
            ModelLoader.registerItemVariants(AmethystContent.spyglass,
                    new ResourceLocation(CavesAndCliffsBackport.MOD_ID, "spyglass"),
                    new ResourceLocation(CavesAndCliffsBackport.MOD_ID, "spyglass_in_hand"));
        }
    }

    @SubscribeEvent
    public void bakeModels(ModelBakeEvent event) {
        IBakedModel icon = event.getModelRegistry().getObject(inventory("spyglass"));
        IBakedModel held = event.getModelRegistry().getObject(inventory("spyglass_in_hand"));
        if (icon == null || held == null) {
            CavesAndCliffsBackport.LOGGER.warn("Spyglass model is incomplete; it is held as its icon.");
            return;
        }
        event.getModelRegistry().putObject(inventory("spyglass"), new ByPerspective(icon, held));
    }

    /** The icon in the GUI, on the ground and in frames; the in-hand model held, worn and in first person. */
    static final class ByPerspective implements IBakedModel {
        final IBakedModel icon;
        final IBakedModel held;

        ByPerspective(IBakedModel icon, IBakedModel held) {
            this.icon = icon;
            this.held = held;
        }

        static boolean flat(ItemCameraTransforms.TransformType type) {
            return type == ItemCameraTransforms.TransformType.GUI || type == ItemCameraTransforms.TransformType.GROUND
                    || type == ItemCameraTransforms.TransformType.FIXED;
        }

        @Override
        public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
            return (flat(type) ? this.icon : this.held).handlePerspective(type);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
            return this.icon.getQuads(state, side, rand);
        }

        @Override
        public boolean isAmbientOcclusion() {
            return this.icon.isAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return this.icon.isGui3d();
        }

        @Override
        public boolean isBuiltInRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleTexture() {
            return this.icon.getParticleTexture();
        }

        @SuppressWarnings("deprecation")
        @Override
        public ItemCameraTransforms getItemCameraTransforms() {
            return this.icon.getItemCameraTransforms();
        }

        @Override
        public ItemOverrideList getOverrides() {
            return this.icon.getOverrides();
        }
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
