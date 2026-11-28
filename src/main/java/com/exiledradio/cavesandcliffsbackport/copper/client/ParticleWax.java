package com.exiledradio.cavesandcliffsbackport.copper.client;

import com.exiledradio.cavesandcliffsbackport.copper.PacketCopperParticles;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * 1.17's wax on, wax off and scrape particles: glow particles (1.18.2's {@code GlowParticle} with its WaxOn, WaxOff and
 * Scrape providers) in honey orange, pale pink or copper green, drifting slowly off the face they came from and
 * brightening as they age, for 10 to 39 ticks.
 */
public class ParticleWax extends Particle {

    ParticleWax(World world, double x, double y, double z, double vx, double vy, double vz, int kind, TextureAtlasSprite sprite) {
        super(world, x, y, z, 0.0, 0.0, 0.0);
        this.setParticleTexture(sprite);
        this.particleScale *= 0.75F;
        this.canCollide = false;
        if (kind == PacketCopperParticles.WAX_ON) {
            this.setRBGColorF(0.91F, 0.55F, 0.08F);
        } else if (kind == PacketCopperParticles.WAX_OFF) {
            this.setRBGColorF(1.0F, 0.9F, 1.0F);
        } else if (this.rand.nextBoolean()) {
            this.setRBGColorF(0.29F, 0.58F, 0.51F);
        } else {
            this.setRBGColorF(0.43F, 0.77F, 0.62F);
        }
        double sideways = kind == PacketCopperParticles.SCRAPE ? 1.0 : 0.5;
        this.motionX = vx * 0.01 * sideways;
        this.motionY = vy * 0.01;
        this.motionZ = vz * 0.01 * sideways;
        this.particleMaxAge = this.rand.nextInt(30) + 10;
    }

    /** Drawn from the block texture sheet, where the glow sprite is stitched. */
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
