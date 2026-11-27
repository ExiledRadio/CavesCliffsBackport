package com.exiledradio.cavesandcliffsbackport.dripstone;

import com.exiledradio.backportcore.speleothem.EntityFallingSpeleothem;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * {@code cavesandcliffsbackport:falling_stalactite}, the falling segment of 1.1.7 and older, kept only so worlds saved
 * mid-fall load: on its first server tick it becomes Backport Core's {@link EntityFallingSpeleothem} where it is,
 * with its motion, fall time and damage. Registered without tracking, so clients never see one.
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
        if (this.world.isRemote || this.isDead) {
            return;
        }
        setDead();
        if (DripstoneContent.pointed == null) {
            return;
        }
        EntityFallingSpeleothem core = convert();
        if (core != null) {
            this.world.spawnEntity(core);
        }
    }

    /** @return Core's segment for this one, not yet in the world (a new UUID: this one's is still taken) */
    public EntityFallingSpeleothem convert() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        tag.removeTag("UUIDMost");
        tag.removeTag("UUIDLeast");
        tag.setString("Block", String.valueOf(DripstoneContent.pointed.getRegistryName()));
        tag.setInteger("Meta", this.meta);
        EntityFallingSpeleothem core = new EntityFallingSpeleothem(this.world);
        core.readFromNBT(tag);
        return core;
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setInteger("Thickness", this.meta);
        tag.setInteger("Time", this.fallTime);
        tag.setFloat("FallHurtAmount", this.hurtPerBlock);
        tag.setInteger("FallHurtMax", this.hurtMax);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        this.meta = tag.getInteger("Thickness");
        this.fallTime = tag.getInteger("Time");
        this.hurtPerBlock = tag.getFloat("FallHurtAmount");
        this.hurtMax = tag.getInteger("FallHurtMax");
    }
}
