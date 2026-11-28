package com.exiledradio.cavesandcliffsbackport.dripstone;

import com.exiledradio.backportcore.speleothem.EntityFallingSpeleothem;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * A falling stalactite segment saved by 1.1.7 or older ({@code cavesandcliffsbackport:falling_stalactite}). Since 1.1.8
 * stalactites fall as Backport Core's {@link EntityFallingSpeleothem}; one of these loaded from an old world turns into
 * that on its first tick, with its segment, place, speed, fall time and damage, and nothing else ever makes one.
 */
public class EntityFallingStalactite extends Entity {

    private int meta;
    private int fallTime;
    private float hurtPerBlock;
    private int hurtMax;

    public EntityFallingStalactite(World world) {
        super(world);
        setSize(0.98F, 0.98F);
    }

    @Override
    protected void entityInit() {
    }

    @Override
    public void onUpdate() {
        if (this.world.isRemote) {
            return;
        }
        setDead();
        if (DripstoneContent.pointed == null) {
            return;
        }
        EntityFallingSpeleothem now = replacement();
        this.world.spawnEntity(now);
    }

    /** @return Core's falling segment carrying everything this one had */
    public EntityFallingSpeleothem replacement() {
        IBlockState state = DripstoneContent.pointed.getStateFromMeta(this.meta);
        EntityFallingSpeleothem now = new EntityFallingSpeleothem(this.world, this.posX, this.posY, this.posZ, state);
        NBTTagCompound tag = now.writeToNBT(new NBTTagCompound());
        tag.setInteger("Time", this.fallTime);
        tag.setFloat("FallHurtAmount", this.hurtPerBlock);
        tag.setInteger("FallHurtMax", this.hurtMax);
        now.readFromNBT(tag);
        now.setPosition(this.posX, this.posY, this.posZ);
        now.motionX = this.motionX;
        now.motionY = this.motionY;
        now.motionZ = this.motionZ;
        return now;
    }

    /** 1.1.7's fields: {@code Thickness} (the segment's metadata), {@code Time}, {@code FallHurtAmount}, {@code FallHurtMax}. */
    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        this.meta = tag.getInteger("Thickness");
        this.fallTime = tag.getInteger("Time");
        this.hurtPerBlock = tag.getFloat("FallHurtAmount");
        this.hurtMax = tag.getInteger("FallHurtMax");
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setInteger("Thickness", this.meta);
        tag.setInteger("Time", this.fallTime);
        tag.setFloat("FallHurtAmount", this.hurtPerBlock);
        tag.setInteger("FallHurtMax", this.hurtMax);
    }
}
