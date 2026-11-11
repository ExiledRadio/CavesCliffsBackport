package com.exiledradio.cavesandcliffsbackport.glowsquid;

import com.exiledradio.cavesandcliffsbackport.network.NetworkHandler;

import net.minecraft.block.material.Material;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The glow squid: a squid that is drawn at full brightness, sheds glow particles, spawns only in lightless
 * water deep underground, and goes dark for five seconds (and squirts glowing ink) when something hurts it.
 */
public class EntityGlowSquid extends EntitySquid {

    private static final DataParameter<Integer> DARK_TICKS = EntityDataManager.createKey(EntityGlowSquid.class, DataSerializers.VARINT);

    /** How long a hurt glow squid stays dark. */
    public static final int DARK_TIME = 100;

    public EntityGlowSquid(World world) {
        super(world);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(DARK_TICKS, 0);
    }

    public int getDarkTicks() {
        return this.dataManager.get(DARK_TICKS);
    }

    public void setDarkTicks(int ticks) {
        this.dataManager.set(DARK_TICKS, ticks);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return GlowSquidContent.squidAmbient;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return GlowSquidContent.squidHurt;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return GlowSquidContent.squidDeath;
    }

    @Override
    protected ResourceLocation getLootTable() {
        return GlowSquidContent.LOOT;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        int dark = this.getDarkTicks();
        if (dark > 0) {
            this.setDarkTicks(dark - 1);
        }
        if (this.world.isRemote) {
            // Named only here and reached only on a client, so a server never loads the class.
            com.exiledradio.cavesandcliffsbackport.glowsquid.client.GlowSquidClient.glowParticle(this.world,
                    this.posX + this.width * (2.0 * this.rand.nextDouble() - 1.0) * 0.6,
                    this.posY + this.height * this.rand.nextDouble(),
                    this.posZ + this.width * (2.0 * this.rand.nextDouble() - 1.0) * 0.6);
        }
    }

    /** As current Java: only a hit that has an attacker behind it makes the squid squirt and go dark. */
    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        boolean hurt = super.attackEntityFrom(source, amount);
        if (hurt && !this.world.isRemote && this.getRevengeTarget() != null) {
            this.squirt();
            this.setDarkTicks(DARK_TIME);
        }
        return hurt;
    }

    /** The squirt sound, and thirty ink particles on every client that sees this squid. */
    private void squirt() {
        this.playSound(GlowSquidContent.squidSquirt, this.getSoundVolume(), this.getSoundPitch());
        NetworkHandler.channel.sendToAllTracking(
                new PacketGlowInk(this.posX, this.posY, this.posZ, this.prevSquidPitch, this.prevRenderYawOffset), this);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setInteger("DarkTicksRemaining", this.getDarkTicks());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        this.setDarkTicks(compound.getInteger("DarkTicksRemaining"));
    }

    /** Glow squid have their own spawn group, so they neither fill the squid's cap nor are held back by it. */
    @Override
    public boolean isCreatureType(EnumCreatureType type, boolean forSpawnCount) {
        if (forSpawnCount && this.isNoDespawnRequired()) {
            return false;
        }
        return type == GlowSquidContent.spawnGroup();
    }

    /** Current Java: in water, at least 33 blocks below sea level, where no light at all reaches. */
    @Override
    public boolean getCanSpawnHere() {
        BlockPos pos = new BlockPos(this);
        return pos.getY() <= this.world.getSeaLevel() - 33
                && this.world.getBlockState(pos).getMaterial() == Material.WATER
                && this.world.getLightFor(EnumSkyBlock.SKY, pos) == 0
                && this.world.getLightFor(EnumSkyBlock.BLOCK, pos) == 0;
    }

    /** Full block light, fading to the world's light over the last ten dark ticks' worth; sky light as it is. */
    @SideOnly(Side.CLIENT)
    @Override
    public int getBrightnessForRender() {
        int sky = super.getBrightnessForRender() & 0xFFFF0000;
        int block = (int) MathHelper.clampedLerp(0.0, 15.0, 1.0 - this.getDarkTicks() / 10.0);
        return sky | block << 4;
    }
}
