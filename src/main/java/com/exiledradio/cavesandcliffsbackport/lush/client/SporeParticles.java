package com.exiledradio.cavesandcliffsbackport.lush.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.World;

/**
 * The spore blossom's two particles, drawn from 1.12's own particle sheet (the falling drip and the smallest
 * generic puff) in the green current Java gives them. Client only: reached from the block's display tick.
 */
public final class SporeParticles {

    private static final float R = 0.32F, G = 0.5F, B = 0.22F;

    private SporeParticles() {
    }

    /** A spore falling from the flower. */
    public static void falling(World world, double x, double y, double z) {
        Minecraft.getMinecraft().effectRenderer.addEffect(new Spore(world, x, y, z, true));
    }

    /** A spore hanging in the air nearby. */
    public static void air(World world, double x, double y, double z) {
        Minecraft.getMinecraft().effectRenderer.addEffect(new Spore(world, x, y, z, false));
    }

    private static final class Spore extends Particle {

        private final boolean falling;

        Spore(World world, double x, double y, double z, boolean falling) {
            super(world, x, y, z);
            this.falling = falling;
            this.motionX = this.motionY = this.motionZ = 0.0;
            setRBGColorF(R, G, B);
            if (falling) {
                // The falling drip of the particle sheet; gravity as a slow drip, 3 to 6 seconds.
                setParticleTextureIndex(113);
                this.particleGravity = 0.005F;
                this.particleMaxAge = (int) (64.0F / (0.1F + this.rand.nextFloat() * 0.8F));
                setSize(0.01F, 0.01F);
            } else {
                setParticleTextureIndex(0);
                this.particleScale *= 0.6F + this.rand.nextFloat() * 0.6F;
                this.particleGravity = 0.0F;
                this.motionY = -0.004 - this.rand.nextFloat() * 0.004;
                this.particleMaxAge = (int) (500 + this.rand.nextFloat() * 500);
                this.canCollide = false;
                setSize(0.02F, 0.02F);
            }
        }

        @Override
        public void onUpdate() {
            this.prevPosX = this.posX;
            this.prevPosY = this.posY;
            this.prevPosZ = this.posZ;
            if (this.particleAge++ >= this.particleMaxAge) {
                setExpired();
                return;
            }
            this.motionY -= this.particleGravity;
            move(this.motionX, this.motionY, this.motionZ);
            if (this.falling) {
                this.motionX *= 0.98;
                this.motionY *= 0.98;
                this.motionZ *= 0.98;
                if (this.onGround) {
                    setExpired();
                }
            }
        }
    }
}
