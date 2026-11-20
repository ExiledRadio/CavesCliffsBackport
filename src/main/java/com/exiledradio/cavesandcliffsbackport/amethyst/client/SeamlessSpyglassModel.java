package com.exiledradio.cavesandcliffsbackport.amethyst.client;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The spyglass's in-hand model without seams.
 *
 * <p>1.12.2 bakes each box face to end exactly where its neighbor starts, and samples its UVs right up to the edge of
 * the texture's 2-pixel column, so held close to the camera a thin gap shows along the edges where the transparent
 * texels next to the column are hit. Every face here is pushed out by {@link #EPS} in its own plane, so neighbors
 * overlap, and its UVs are pulled in by {@link #UV_SHRINK} of a texel toward the face's center, as 1.14+ does.</p>
 */
final class SeamlessSpyglassModel implements IBakedModel {

    /** Model units (a block is 1, a texture pixel 1/16): about a thirtieth of a pixel. */
    static final float EPS = 0.002F;

    /** Texels each UV moves toward the face's center. */
    static final float UV_SHRINK = 0.02F;

    private final IBakedModel inner;
    private final List<BakedQuad> general;
    private final Map<EnumFacing, List<BakedQuad>> sided = new EnumMap<>(EnumFacing.class);

    SeamlessSpyglassModel(IBakedModel inner) {
        this.inner = inner;
        this.general = widen(inner.getQuads(null, null, 0L));
        for (EnumFacing side : EnumFacing.values()) {
            this.sided.put(side, widen(inner.getQuads(null, side, 0L)));
        }
    }

    private static List<BakedQuad> widen(List<BakedQuad> quads) {
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            out.add(widen(quad));
        }
        return out;
    }

    /** Pushes a face's corners away from its center along the two axes in its plane. */
    static BakedQuad widen(BakedQuad quad) {
        EnumFacing face = quad.getFace();
        if (face == null) {
            return quad;
        }
        VertexFormat format = quad.getFormat();
        int stride = format.getIntegerSize();
        int[] data = quad.getVertexData().clone();
        if (data.length < stride * 4) {
            return quad;
        }
        int normal = face.getAxis().ordinal();
        float[] center = new float[3];
        for (int v = 0; v < 4; v++) {
            for (int a = 0; a < 3; a++) {
                center[a] += Float.intBitsToFloat(data[v * stride + a]) / 4.0F;
            }
        }
        for (int v = 0; v < 4; v++) {
            for (int a = 0; a < 3; a++) {
                if (a == normal) {
                    continue;
                }
                float p = Float.intBitsToFloat(data[v * stride + a]);
                if (p > center[a] + 1.0E-5F) {
                    p += EPS;
                } else if (p < center[a] - 1.0E-5F) {
                    p -= EPS;
                }
                data[v * stride + a] = Float.floatToRawIntBits(p);
            }
        }
        TextureAtlasSprite sprite = quad.getSprite();
        if (format.hasUvOffset(0) && sprite != null) {
            int uv = format.getUvOffsetById(0) / 4;
            float du = (sprite.getMaxU() - sprite.getMinU()) / sprite.getIconWidth() * UV_SHRINK;
            float dv = (sprite.getMaxV() - sprite.getMinV()) / sprite.getIconHeight() * UV_SHRINK;
            float cu = 0.0F, cv = 0.0F;
            for (int v = 0; v < 4; v++) {
                cu += Float.intBitsToFloat(data[v * stride + uv]) / 4.0F;
                cv += Float.intBitsToFloat(data[v * stride + uv + 1]) / 4.0F;
            }
            for (int v = 0; v < 4; v++) {
                data[v * stride + uv] = Float.floatToRawIntBits(toward(Float.intBitsToFloat(data[v * stride + uv]), cu, du));
                data[v * stride + uv + 1] = Float.floatToRawIntBits(toward(Float.intBitsToFloat(data[v * stride + uv + 1]), cv, dv));
            }
        }
        return new BakedQuad(data, quad.getTintIndex(), face, quad.getSprite(), quad.shouldApplyDiffuseLighting(), format);
    }

    private static float toward(float p, float center, float d) {
        if (p > center + d) {
            return p - d;
        }
        return p < center - d ? p + d : p;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        return side == null ? this.general : this.sided.get(side);
    }

    @Override
    public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
        Pair<? extends IBakedModel, Matrix4f> p = this.inner.handlePerspective(type);
        return p.getLeft() == this.inner ? Pair.of(this, p.getRight()) : p;
    }

    @Override
    public boolean isAmbientOcclusion() {
        return this.inner.isAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return this.inner.isGui3d();
    }

    @Override
    public boolean isBuiltInRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return this.inner.getParticleTexture();
    }

    @SuppressWarnings("deprecation")
    @Override
    public ItemCameraTransforms getItemCameraTransforms() {
        return this.inner.getItemCameraTransforms();
    }

    @Override
    public ItemOverrideList getOverrides() {
        return this.inner.getOverrides();
    }
}
