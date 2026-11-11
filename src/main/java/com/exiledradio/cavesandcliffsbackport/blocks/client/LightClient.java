package com.exiledradio.cavesandcliffsbackport.blocks.client;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.blocks.BlockLight;
import com.exiledradio.cavesandcliffsbackport.blocks.ItemLight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.World;

/**
 * The client's side of the light block: who may point at one, and the marker that shows its level.
 *
 * <p>The marker is 1.12.2's barrier marker with the light's own picture: shown to a creative-mode player
 * whose main hand holds a light, from the block's random display tick, thinned by the particles option
 * the way every vanilla particle is.
 */
public final class LightClient implements BlockLight.ClientHook {

    private LightClient() {
    }

    public static void init() {
        BlockLight.client = new LightClient();
    }

    @Override
    public boolean holding() {
        Minecraft mc = Minecraft.getMinecraft();
        // An integrated server shares this class: its thread must not see lights through the client's hand.
        return mc.isCallingFromMinecraftThread() && mc.player != null
                && mc.player.getHeldItemMainhand().getItem() instanceof ItemLight;
    }

    @Override
    public void display(World world, BlockPos pos, int level) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.playerController == null || mc.playerController.getCurrentGameType() != GameType.CREATIVE || !holding()) {
            return;
        }
        int setting = mc.gameSettings.particleSetting;
        if (setting == 1 && world.rand.nextInt(3) == 0) {
            setting = 2;
        }
        if (setting > 1) {
            return;
        }
        String name = CavesAndCliffsBackport.MOD_ID + ":item/light_" + (level < 10 ? "0" : "") + level;
        mc.effectRenderer.addEffect(new Marker(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                mc.getTextureMapBlocks().getAtlasSprite(name)));
    }

    /** A still picture half a block across that faces the camera for four seconds. */
    private static final class Marker extends Particle {

        Marker(World world, double x, double y, double z, TextureAtlasSprite sprite) {
            super(world, x, y, z, 0.0, 0.0, 0.0);
            setParticleTexture(sprite);
            this.particleRed = 1.0F;
            this.particleGreen = 1.0F;
            this.particleBlue = 1.0F;
            this.motionX = 0.0;
            this.motionY = 0.0;
            this.motionZ = 0.0;
            this.particleGravity = 0.0F;
            this.particleMaxAge = 80;
            this.canCollide = false;
        }

        @Override
        public int getFXLayer() {
            return 1;
        }

        @Override
        public void renderParticle(BufferBuilder buffer, Entity entity, float partialTicks, float rotationX, float rotationZ,
                                   float rotationYZ, float rotationXY, float rotationXZ) {
            float u0 = this.particleTexture.getMinU();
            float u1 = this.particleTexture.getMaxU();
            float v0 = this.particleTexture.getMinV();
            float v1 = this.particleTexture.getMaxV();
            float x = (float) (this.prevPosX + (this.posX - this.prevPosX) * partialTicks - interpPosX);
            float y = (float) (this.prevPosY + (this.posY - this.prevPosY) * partialTicks - interpPosY);
            float z = (float) (this.prevPosZ + (this.posZ - this.prevPosZ) * partialTicks - interpPosZ);
            int light = getBrightnessForRender(partialTicks);
            int sky = light >> 16 & 65535;
            int block = light & 65535;
            float s = 0.5F;
            buffer.pos(x - rotationX * s - rotationXY * s, y - rotationZ * s, z - rotationYZ * s - rotationXZ * s)
                    .tex(u1, v1).color(this.particleRed, this.particleGreen, this.particleBlue, 1.0F).lightmap(sky, block).endVertex();
            buffer.pos(x - rotationX * s + rotationXY * s, y + rotationZ * s, z - rotationYZ * s + rotationXZ * s)
                    .tex(u1, v0).color(this.particleRed, this.particleGreen, this.particleBlue, 1.0F).lightmap(sky, block).endVertex();
            buffer.pos(x + rotationX * s + rotationXY * s, y + rotationZ * s, z + rotationYZ * s + rotationXZ * s)
                    .tex(u0, v0).color(this.particleRed, this.particleGreen, this.particleBlue, 1.0F).lightmap(sky, block).endVertex();
            buffer.pos(x + rotationX * s - rotationXY * s, y - rotationZ * s, z + rotationYZ * s - rotationXZ * s)
                    .tex(u0, v1).color(this.particleRed, this.particleGreen, this.particleBlue, 1.0F).lightmap(sky, block).endVertex();
        }
    }
}
