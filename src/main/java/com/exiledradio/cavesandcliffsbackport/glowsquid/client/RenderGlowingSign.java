package com.exiledradio.cavesandcliffsbackport.glowsquid.client;

import com.exiledradio.cavesandcliffsbackport.glowsquid.GlowingText;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiUtilRenderComponents;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySignRenderer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

/**
 * The sign renderer: vanilla's, and for a sign whose text glows a second pass over the text at full
 * brightness with current Java's outline (the text once more at each of the eight pixels around it, in the
 * outline color, then the text itself on top).
 *
 * <p>1.12 signs have black text only, and current Java outlines glowing black text in pale yellow
 * ({@code 0xF0EBCC}) and leaves the letters black.
 */
public class RenderGlowingSign extends TileEntitySignRenderer {

    private static final int OUTLINE = 0xF0EBCC;
    private static final float TEXT_SCALE = 0.010416667F;

    @Override
    public void render(TileEntitySign te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        super.render(te, x, y, z, partialTicks, destroyStage, alpha);
        if (destroyStage >= 0 || !GlowingText.get(te)) {
            return;
        }
        // The same place vanilla has just drawn the text.
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x + 0.5F, (float) y + 0.5F, (float) z + 0.5F);
        if (te.getBlockType() == Blocks.STANDING_SIGN) {
            GlStateManager.rotate(-(te.getBlockMetadata() * 360) / 16.0F, 0.0F, 1.0F, 0.0F);
        } else {
            int meta = te.getBlockMetadata();
            float angle = meta == 2 ? 180.0F : meta == 4 ? 90.0F : meta == 5 ? -90.0F : 0.0F;
            GlStateManager.rotate(-angle, 0.0F, 1.0F, 0.0F);
            GlStateManager.translate(0.0F, -0.3125F, -0.4375F);
        }
        GlStateManager.translate(0.0F, 0.33333334F, 0.046666667F);
        GlStateManager.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
        GlStateManager.glNormal3f(0.0F, 0.0F, -TEXT_SCALE);
        GlStateManager.depthMask(false);
        float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);

        FontRenderer font = this.getFontRenderer();
        String[] lines = new String[te.signText.length];
        for (int i = 0; i < lines.length; i++) {
            ITextComponent text = te.signText[i];
            if (text != null) {
                List<ITextComponent> split = GuiUtilRenderComponents.splitText(text, 90, font, false, true);
                lines[i] = split != null && !split.isEmpty() ? split.get(0).getFormattedText() : "";
            }
        }
        for (int i = 0; i < lines.length; i++) {
            if (lines[i] == null || lines[i].isEmpty()) {
                continue;
            }
            // Without its formatting codes, so the outline is one color whatever the text's is.
            String plain = TextFormatting.getTextWithoutFormattingCodes(lines[i]);
            int left = -font.getStringWidth(lines[i]) / 2, top = i * 10 - lines.length * 5;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx != 0 || dy != 0) {
                        font.drawString(plain, left + dx, top + dy, OUTLINE);
                    }
                }
            }
        }
        for (int i = 0; i < lines.length; i++) {
            if (lines[i] != null && !lines[i].isEmpty()) {
                font.drawString(lines[i], -font.getStringWidth(lines[i]) / 2, i * 10 - lines.length * 5, 0);
            }
        }

        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
        GlStateManager.depthMask(true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }
}
