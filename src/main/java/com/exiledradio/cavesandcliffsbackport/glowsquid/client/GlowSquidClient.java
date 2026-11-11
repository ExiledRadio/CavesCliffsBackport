package com.exiledradio.cavesandcliffsbackport.glowsquid.client;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.Content;
import com.exiledradio.cavesandcliffsbackport.glowsquid.EntityGlowItemFrame;
import com.exiledradio.cavesandcliffsbackport.glowsquid.EntityGlowSquid;
import com.exiledradio.cavesandcliffsbackport.glowsquid.GlowSquidContent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Random;

/** Stream Gs on the client: the two entity renderers, the glowing sign renderer, the glow and ink particles. */
public final class GlowSquidClient {

    /** The frame's two looks, loaded as item models so the game loads and stitches them like any other. */
    static final ResourceLocation FRAME = Content.id("glow_item_frame_entity"), FRAME_MAP = Content.id("glow_item_frame_entity_map");
    static final ModelResourceLocation FRAME_MODEL = new ModelResourceLocation(FRAME, "inventory");
    static final ModelResourceLocation FRAME_MAP_MODEL = new ModelResourceLocation(FRAME_MAP, "inventory");

    private static final ResourceLocation GLOW_SPRITE = new ResourceLocation(CavesAndCliffsBackport.MOD_ID, "particle/glow");
    private static TextureAtlasSprite glowSprite;
    private static final Random RANDOM = new Random();

    private GlowSquidClient() {
    }

    /** From {@code ClientSetup.init}, in pre-init, when the stream is on. */
    public static void init() {
        MinecraftForge.EVENT_BUS.register(new GlowSquidClient());
        RenderingRegistry.registerEntityRenderingHandler(EntityGlowSquid.class, new IRenderFactory<EntityGlowSquid>() {
            @Override
            public Render<? super EntityGlowSquid> createRenderFor(RenderManager manager) {
                return new RenderGlowSquid(manager);
            }
        });
        RenderingRegistry.registerEntityRenderingHandler(EntityGlowItemFrame.class, new IRenderFactory<EntityGlowItemFrame>() {
            @Override
            public Render<? super EntityGlowItemFrame> createRenderFor(RenderManager manager) {
                // Called while the game builds its render manager, after the item renderer exists.
                return new RenderGlowItemFrame(manager, Minecraft.getMinecraft().getRenderItem());
            }
        });
        if (ModConfig.glowSquid.glowingSigns) {
            // Replaces vanilla's sign renderer with one that does the same and then adds the glow.
            ClientRegistry.bindTileEntitySpecialRenderer(TileEntitySign.class, new RenderGlowingSign());
        }
    }

    @SubscribeEvent
    public void models(ModelRegistryEvent event) {
        if (GlowSquidContent.glowItemFrame != null) {
            ModelLoader.registerItemVariants(GlowSquidContent.glowItemFrame, FRAME, FRAME_MAP);
        }
    }

    @SubscribeEvent
    public void stitch(TextureStitchEvent.Pre event) {
        if (event.getMap() == Minecraft.getMinecraft().getTextureMapBlocks()) {
            glowSprite = event.getMap().registerSprite(GLOW_SPRITE);
        }
    }

    /** One glow particle; called by the glow squid every client tick. */
    public static void glowParticle(World world, double x, double y, double z) {
        Minecraft mc = Minecraft.getMinecraft();
        if (glowSprite == null || mc.world != world || mc.gameSettings.particleSetting == 2 && RANDOM.nextInt(10) != 0
                || mc.gameSettings.particleSetting == 1 && RANDOM.nextInt(3) == 0) {
            return;
        }
        mc.effectRenderer.addEffect(new ParticleGlow(world, x, y, z, glowSprite));
    }

    /** A squirt of glowing ink, as current Java's squid: thirty particles thrown out behind the squid. */
    public static void ink(final double x, final double y, final double z, final float pitch, final float yaw) {
        final Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                if (mc.world == null) {
                    return;
                }
                Vec3d from = rotate(new Vec3d(0.0, -1.0, 0.0), pitch, yaw).add(x, y, z);
                for (int i = 0; i < 30; i++) {
                    Vec3d dir = rotate(new Vec3d(RANDOM.nextFloat() * 0.6 - 0.3, -1.0, RANDOM.nextFloat() * 0.6 - 0.3), pitch, yaw);
                    Vec3d speed = dir.scale((0.3 + RANDOM.nextFloat() * 2.0) * 0.1);
                    mc.effectRenderer.addEffect(new ParticleGlowInk(mc.world, from.x, from.y + 0.5, from.z, speed.x, speed.y, speed.z));
                }
            }
        });
    }

    private static Vec3d rotate(Vec3d v, float pitch, float yaw) {
        return v.rotatePitch(pitch * 0.017453292F).rotateYaw(-yaw * 0.017453292F);
    }
}
