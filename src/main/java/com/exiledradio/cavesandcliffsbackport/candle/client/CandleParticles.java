package com.exiledradio.cavesandcliffsbackport.candle.client;

import com.exiledradio.cavesandcliffsbackport.candle.CandleContent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.Random;

/**
 * A lit wick, client side (1.17's numbers): always a small flame, smoke 30% of the time and the candle's
 * crackle 17% of the time. Reached only from the candle's client-only display tick.
 */
public final class CandleParticles {

    private CandleParticles() {
    }

    public static void flame(World world, double x, double y, double z, Random rand) {
        float f = rand.nextFloat();
        if (f < 0.3F) {
            world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, x, y, z, 0.0, 0.0, 0.0);
            if (f < 0.17F) {
                world.playSound(x + 0.5, y + 0.5, z + 0.5, CandleContent.ambient, SoundCategory.BLOCKS,
                        1.0F + rand.nextFloat(), rand.nextFloat() * 0.7F + 0.3F, false);
            }
        }
        Minecraft.getMinecraft().effectRenderer.addEffect(new SmallFlame(world, x, y, z));
    }

    /** 1.17's small flame: 1.12's flame particle at half its size. 1.12's keeps its size private, so it is rewritten here. */
    private static final class SmallFlame extends Particle {

        private final float size;

        SmallFlame(World world, double x, double y, double z) {
            super(world, x, y, z, 0.0, 0.0, 0.0);
            this.motionX *= 0.01;
            this.motionY *= 0.01;
            this.motionZ *= 0.01;
            this.posX += (this.rand.nextFloat() - this.rand.nextFloat()) * 0.05F;
            this.posY += (this.rand.nextFloat() - this.rand.nextFloat()) * 0.05F;
            this.posZ += (this.rand.nextFloat() - this.rand.nextFloat()) * 0.05F;
            this.size = this.particleScale * 0.5F;
            this.particleRed = 1.0F;
            this.particleGreen = 1.0F;
            this.particleBlue = 1.0F;
            this.particleMaxAge = (int) (8.0 / (Math.random() * 0.8 + 0.2)) + 4;
            this.canCollide = false;
            setParticleTextureIndex(48);
        }

        @Override
        public void renderParticle(BufferBuilder buffer, Entity entity, float partialTicks, float rotationX, float rotationZ,
                                   float rotationYZ, float rotationXY, float rotationXZ) {
            float f = (this.particleAge + partialTicks) / this.particleMaxAge;
            this.particleScale = this.size * (1.0F - f * f * 0.5F);
            super.renderParticle(buffer, entity, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
        }

        @Override
        public int getBrightnessForRender(float partialTick) {
            float f = MathHelper.clamp((this.particleAge + partialTick) / this.particleMaxAge, 0.0F, 1.0F);
            int i = super.getBrightnessForRender(partialTick);
            int j = i & 255;
            int k = i >> 16 & 255;
            j = Math.min(j + (int) (f * 15.0F * 16.0F), 240);
            return j | k << 16;
        }

        @Override
        public void onUpdate() {
            this.prevPosX = this.posX;
            this.prevPosY = this.posY;
            this.prevPosZ = this.posZ;
            if (this.particleAge++ >= this.particleMaxAge) {
                setExpired();
            }
            move(this.motionX, this.motionY, this.motionZ);
            this.motionX *= 0.96;
            this.motionY *= 0.96;
            this.motionZ *= 0.96;
        }
    }
}
