package com.exiledradio.cavesandcliffsbackport.babies.client;

import com.exiledradio.cavesandcliffsbackport.axolotl.AxolotlVariant;
import com.exiledradio.cavesandcliffsbackport.axolotl.EntityAxolotl;
import com.exiledradio.cavesandcliffsbackport.babies.client.model.BabyAxolotlModel;
import com.exiledradio.cavesandcliffsbackport.babies.client.model.BabyGoatModel;
import com.exiledradio.cavesandcliffsbackport.babies.client.render.BabyRender;
import com.exiledradio.cavesandcliffsbackport.babies.client.render.BabyWrapper;
import com.exiledradio.cavesandcliffsbackport.goat.EntityGoat;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.ResourceLocation;

/**
 * 26.1's babies for this mod's goat and axolotl (Game Drops Backport's request 1): each 1.17 renderer is wrapped by
 * a {@link BabyWrapper} that sends babies to a 26.3 baby renderer while {@link BabyModels#active}. Shadows are 26.3's
 * adult radii, which 1.12 halves for children as 26.3 does. The glow squid has no babies here (1.17 had none), so it
 * keeps its renderer.
 */
public final class CaveBabies {

    private CaveBabies() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends EntityLiving> Render<T> wrap(Render<T> adult, BabyRender<T> baby) {
        return new BabyWrapper(adult, baby);
    }

    /** {@code GoatRenderer}: {@code goat_baby}, shadow 0.7. */
    public static Render<EntityGoat> goat(RenderManager manager, Render<EntityGoat> adult) {
        final ResourceLocation texture = BabyRender.tex("goat/goat_baby");
        return wrap(adult, new BabyRender<EntityGoat>(manager, new BabyGoatModel(), 0.7F, new BabyRender.Texture<EntityGoat>() {
            @Override
            public ResourceLocation get(EntityGoat goat) {
                return texture;
            }
        }));
    }

    /** {@code AxolotlRenderer}: {@code axolotl_<variant>_baby}, shadow 0.5. */
    public static Render<EntityAxolotl> axolotl(RenderManager manager, Render<EntityAxolotl> adult) {
        final ResourceLocation[] textures = new ResourceLocation[AxolotlVariant.values().length];
        for (AxolotlVariant v : AxolotlVariant.values()) {
            textures[v.id] = BabyRender.tex("axolotl/axolotl_" + v.name + "_baby");
        }
        return wrap(adult, new BabyRender<EntityAxolotl>(manager, new BabyAxolotlModel(), 0.5F, new BabyRender.Texture<EntityAxolotl>() {
            @Override
            public ResourceLocation get(EntityAxolotl axolotl) {
                return textures[axolotl.getVariant().id];
            }
        }));
    }
}
