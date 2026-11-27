package com.exiledradio.cavesandcliffsbackport.dripstone.client;

import com.exiledradio.cavesandcliffsbackport.dripstone.DripstoneContent;

import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleDrip;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;

/** Stream Dr's client side: the drip particle with its landing sound. Core draws the falling segments. */
public final class DripstoneClient {

    private DripstoneClient() {
    }

    /** A drop that hangs under a stalactite's tip, falls, and is heard when it lands. */
    public static void drip(World world, double x, double y, double z, boolean lava) {
        Minecraft.getMinecraft().effectRenderer.addEffect(new Drip(world, x, y, z, lava));
    }

    private static final class Drip extends ParticleDrip {
        private final boolean lava;
        private boolean landed;

        Drip(World world, double x, double y, double z, boolean lava) {
            super(world, x, y, z, lava ? Material.LAVA : Material.WATER);
            this.lava = lava;
        }

        @Override
        public void onUpdate() {
            super.onUpdate();
            if (this.onGround && !this.landed) {
                this.landed = true;
                float volume = 0.3F + this.rand.nextFloat() * 0.7F;
                this.world.playSound(this.posX, this.posY, this.posZ, this.lava ? DripstoneContent.dripLava : DripstoneContent.dripWater,
                        SoundCategory.BLOCKS, volume, 1.0F, false);
            }
        }
    }
}
