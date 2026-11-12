package com.exiledradio.cavesandcliffsbackport.glowsquid.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ModelManager;
import net.minecraft.client.renderer.entity.RenderItemFrame;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.MapData;
import net.minecraftforge.client.event.RenderItemInFrameEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * Vanilla's item frame renderer with the glow frame's models, and with the item or map in it drawn at
 * current Java's glow-frame light (block 13, sky 15) whatever the light around it.
 *
 * <p>Vanilla's renderer keeps its model names and its item drawing private, so both methods are repeated
 * here; they differ from vanilla's only in the two models and the light.
 */
public class RenderGlowItemFrame extends RenderItemFrame {

    private static final ResourceLocation MAP_BACKGROUND = new ResourceLocation("textures/map/map_background.png");
    /** 1.17's 15728850: block light 13, sky light 15. */
    private static final float GLOW_BLOCK = 210.0F, GLOW_SKY = 240.0F;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final RenderItem items;

    public RenderGlowItemFrame(RenderManager manager, RenderItem items) {
        super(manager, items);
        this.items = items;
    }

    @Override
    public void doRender(EntityItemFrame entity, double x, double y, double z, float entityYaw, float partialTicks) {
        GlStateManager.pushMatrix();
        BlockPos pos = entity.getHangingPosition();
        GlStateManager.translate(pos.getX() - entity.posX + x + 0.5, pos.getY() - entity.posY + y + 0.5, pos.getZ() - entity.posZ + z + 0.5);
        GlStateManager.rotate(180.0F - entity.rotationYaw, 0.0F, 1.0F, 0.0F);
        this.renderManager.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        BlockRendererDispatcher blocks = this.mc.getBlockRendererDispatcher();
        ModelManager models = blocks.getBlockModelShapes().getModelManager();
        IBakedModel model = models.getModel(entity.getDisplayedItem().getItem() instanceof ItemMap
                ? GlowSquidClient.FRAME_MAP_MODEL : GlowSquidClient.FRAME_MODEL);

        GlStateManager.pushMatrix();
        GlStateManager.translate(-0.5F, -0.5F, -0.5F);
        if (this.renderOutlines) {
            GlStateManager.enableColorMaterial();
            GlStateManager.enableOutlineMode(this.getTeamColor(entity));
        }
        blocks.getBlockModelRenderer().renderModelBrightnessColor(model, 1.0F, 1.0F, 1.0F, 1.0F);
        if (this.renderOutlines) {
            GlStateManager.disableOutlineMode();
            GlStateManager.disableColorMaterial();
        }
        GlStateManager.popMatrix();

        GlStateManager.translate(0.0F, 0.0F, 0.4375F);
        float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, GLOW_BLOCK, GLOW_SKY);
        this.renderContents(entity);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
        GlStateManager.popMatrix();
        this.renderName(entity, x + entity.facingDirection.getXOffset() * 0.3F, y - 0.25, z + entity.facingDirection.getZOffset() * 0.3F);
    }

    private void renderContents(EntityItemFrame frame) {
        ItemStack stack = frame.getDisplayedItem();
        if (stack.isEmpty()) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        boolean map = stack.getItem() instanceof ItemMap;
        int turns = map ? frame.getRotation() % 4 * 2 : frame.getRotation();
        GlStateManager.rotate(turns * 360.0F / 8.0F, 0.0F, 0.0F, 1.0F);

        if (!MinecraftForge.EVENT_BUS.post(new RenderItemInFrameEvent(frame, this))) {
            if (map) {
                this.renderManager.renderEngine.bindTexture(MAP_BACKGROUND);
                GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
                GlStateManager.scale(0.0078125F, 0.0078125F, 0.0078125F);
                GlStateManager.translate(-64.0F, -64.0F, 0.0F);
                MapData data = ((ItemMap) stack.getItem()).getMapData(stack, frame.world);
                GlStateManager.translate(0.0F, 0.0F, -1.0F);
                if (data != null) {
                    this.mc.entityRenderer.getMapItemRenderer().renderMap(data, true);
                }
            } else {
                GlStateManager.scale(0.5F, 0.5F, 0.5F);
                GlStateManager.pushAttrib();
                RenderHelper.enableStandardItemLighting();
                this.items.renderItem(stack, ItemCameraTransforms.TransformType.FIXED);
                RenderHelper.disableStandardItemLighting();
                GlStateManager.popAttrib();
            }
        }
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }
}
