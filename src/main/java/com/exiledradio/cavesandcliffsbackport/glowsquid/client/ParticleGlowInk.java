package com.exiledradio.cavesandcliffsbackport.glowsquid.client;

import net.minecraft.block.material.Material;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 1.17's glow squid ink: a large full-bright teal puff that runs through the eight generic particle pictures,
 * fades over the second half of its life and sinks once it is out of the water.
 */
public class ParticleGlowInk extends Particle {

    ParticleGlowInk(World world, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        super(world, x, y, z);
        this.motionX = xSpeed;
        this.motionY = ySpeed;
        this.motionZ = zSpeed;
        // Half a block across (1.12 multiplies the scale by a tenth when drawing).
        this.particleScale = 5.0F;
        // The color the game shows for current Java's glow ink (204, 31, 102 after its byte overflow).
        this.setRBGColorF(52 / 255.0F, 225 / 255.0F, 154 / 255.0F);
        this.particleMaxAge = (int) (6.0 / (Math.random() * 0.8 + 0.2));
        this.canCollide = false;
        this.setParticleTextureIndex(7);
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
        this.motionX *= 0.92;
        this.motionY *= 0.92;
        this.motionZ *= 0.92;
        this.setParticleTextureIndex(7 - this.particleAge * 7 / this.particleMaxAge);
        if (this.particleAge > this.particleMaxAge / 2) {
            this.setAlphaF(1.0F - (float) (this.particleAge - this.particleMaxAge / 2) / this.particleMaxAge);
        }
        if (this.world.getBlockState(new BlockPos(this.posX, this.posY, this.posZ)).getMaterial() == Material.AIR) {
            this.motionY -= 0.0074;
        }
    }

    @Override
    public int getBrightnessForRender(float partialTick) {
        return 0xF000F0;
    }
}
