package com.exiledradio.cavesandcliffsbackport.glowsquid.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/** 1.17's glow particle as a glow squid sheds it: small, slow, pale or dark teal, brightening as it ages. */
public class ParticleGlow extends Particle {

    ParticleGlow(World world, double x, double y, double z, TextureAtlasSprite sprite) {
        super(world, x, y, z, 0.5 - Math.random(), 0.0, 0.5 - Math.random());
        this.setParticleTexture(sprite);
        this.particleScale *= 0.75F;
        this.canCollide = false;
        if (this.rand.nextBoolean()) {
            this.setRBGColorF(0.6F, 1.0F, 0.8F);
        } else {
            this.setRBGColorF(0.08F, 0.4F, 0.4F);
        }
        this.motionY *= 0.2;
        this.motionX *= 0.1;
        this.motionZ *= 0.1;
        this.particleMaxAge = (int) (8.0 / (Math.random() * 0.8 + 0.2));
    }

    /** Drawn from the block texture sheet, where its sprite was stitched. */
    @Override
    public int getFXLayer() {
        return 1;
    }

    @Override
    public void onUpdate() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        if (this.particleAge++ >= this.particleMaxAge) {
            this.setExpired();
            return;
        }
        this.move(this.motionX, this.motionY, this.motionZ);
        if (this.posY == this.prevPosY) {
            this.motionX *= 1.1;
            this.motionZ *= 1.1;
        }
        this.motionX *= 0.96;
        this.motionY *= 0.96;
        this.motionZ *= 0.96;
    }

    @Override
    public int getBrightnessForRender(float partialTick) {
        float f = MathHelper.clamp((this.particleAge + partialTick) / this.particleMaxAge, 0.0F, 1.0F);
        int light = super.getBrightnessForRender(partialTick);
        int block = Math.min(240, (light & 255) + (int) (f * 15.0F * 16.0F));
        return block | (light >> 16 & 255) << 16;
    }
}
