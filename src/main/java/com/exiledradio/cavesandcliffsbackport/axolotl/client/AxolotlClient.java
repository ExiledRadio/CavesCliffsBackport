package com.exiledradio.cavesandcliffsbackport.axolotl.client;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.axolotl.AxolotlVariant;
import com.exiledradio.cavesandcliffsbackport.axolotl.EntityAxolotl;

import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

/**
 * The axolotl's renderer, 1.17's: one texture per color, shadow 0.5. Reached only from {@code ClientSetup},
 * during pre-init, so a dedicated server never loads it.
 */
public final class AxolotlClient {

    private AxolotlClient() {
    }

    public static void init() {
        RenderingRegistry.registerEntityRenderingHandler(EntityAxolotl.class, Render::new);
    }

    static final class Render extends RenderLiving<EntityAxolotl> {

        private static final ResourceLocation[] TEXTURES = new ResourceLocation[AxolotlVariant.values().length];

        static {
            for (AxolotlVariant v : AxolotlVariant.values()) {
                TEXTURES[v.id] = new ResourceLocation(CavesAndCliffsBackport.MOD_ID, "textures/entity/axolotl/axolotl_" + v.name + ".png");
            }
        }

        Render(RenderManager manager) {
            super(manager, new ModelAxolotl(), 0.5F);
        }

        @Override
        protected ResourceLocation getEntityTexture(EntityAxolotl entity) {
            return TEXTURES[entity.getVariant().id];
        }
    }
}
