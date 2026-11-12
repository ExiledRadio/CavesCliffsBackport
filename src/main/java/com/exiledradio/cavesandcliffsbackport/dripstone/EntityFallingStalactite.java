package com.exiledradio.cavesandcliffsbackport.dripstone;

import io.netty.buffer.ByteBuf;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;

/**
 * One falling segment of a stalactite.
 *
 * <p>Our own entity rather than 1.12's falling block, whose damage is fixed to the anvil's and which insists
 * on finding its block at its starting place. It falls as a falling block does. Where it lands it stays a
 * block only if a stalactite could hang there (something solid right above); otherwise - nearly always - it
 * breaks with a crash and drops its item. The tip hurts whatever it lands on: its damage per block fallen
 * (after the first) and its cap are set by {@link BlockPointedDripstone#spawnFallingStalactite}.
 */
public class EntityFallingStalactite extends Entity implements IEntityAdditionalSpawnData {

    private IBlockState state = Blocks.AIR.getDefaultState();
    private int fallTime;
    private float hurtPerBlock;
    private int hurtMax;

    public EntityFallingStalactite(World world) {
        super(world);
        this.preventEntitySpawning = true;
        setSize(0.98F, 0.98F);
    }

    public EntityFallingStalactite(World world, double x, double y, double z, IBlockState state) {
        this(world);
        this.state = state;
        setPosition(x, y + (1.0F - this.height) / 2.0F, z);
        this.motionX = 0;
        this.motionY = 0;
        this.motionZ = 0;
        this.prevPosX = x;
        this.prevPosY = y;
        this.prevPosZ = z;
    }

    public IBlockState getState() {
        return this.state;
    }

    /** Makes this segment hurt what it lands on: {@code perBlock} per block fallen after the first, {@code max} at most. */
    public void setHurts(float perBlock, int max) {
        this.hurtPerBlock = perBlock;
        this.hurtMax = max;
    }

    public float hurtPerBlock() {
        return this.hurtPerBlock;
    }

    @Override
    protected void entityInit() {
    }

    @Override
    protected boolean canTriggerWalking() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return !this.isDead;
    }

    @Override
    public boolean canBeAttackedWithItem() {
        return false;
    }

    @Override
    public void onUpdate() {
        if (!BlockPointedDripstone.isPointed(this.state)) {
            setDead();
            return;
        }
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.fallTime++;
        if (!hasNoGravity()) {
            this.motionY -= 0.04;
        }
        move(MoverType.SELF, this.motionX, this.motionY, this.motionZ);
        if (!this.world.isRemote) {
            BlockPos here = new BlockPos(this);
            if (this.onGround) {
                land(here);
            } else if (this.fallTime > 100 && (here.getY() < 1 || here.getY() > 256) || this.fallTime > 600) {
                dropAsItem();
                setDead();
            }
        }
        this.motionX *= 0.98;
        this.motionY *= 0.98;
        this.motionZ *= 0.98;
    }

    private void land(BlockPos here) {
        this.motionX *= 0.7;
        this.motionZ *= 0.7;
        this.motionY *= -0.5;
        IBlockState at = this.world.getBlockState(here);
        if (at.getBlock() == Blocks.PISTON_EXTENSION) {
            return;
        }
        setDead();
        Block block = this.state.getBlock();
        if (at.getBlock().isReplaceable(this.world, here) && !at.getMaterial().isLiquid()
                && BlockPointedDripstone.isValidPlacement(this.world, here, EnumFacing.DOWN)) {
            this.world.setBlockState(here, ((BlockPointedDripstone) block).placementState(this.world, here, EnumFacing.DOWN, true), 3);
            return;
        }
        if (!isSilent()) {
            this.world.playSound(null, here, DripstoneContent.land, SoundCategory.BLOCKS, 2.0F, this.rand.nextFloat() * 0.1F + 0.9F);
        }
        dropAsItem();
    }

    private void dropAsItem() {
        if (this.world.getGameRules().getBoolean("doEntityDrops")) {
            entityDropItem(new ItemStack(this.state.getBlock()), 0.0F);
        }
    }

    /** Landing: the damage, as a falling block's but with this stalactite's own numbers and death message. */
    @Override
    public void fall(float distance, float damageMultiplier) {
        if (this.hurtPerBlock <= 0) {
            return;
        }
        int blocks = MathHelper.ceil(distance - 1.0F);
        if (blocks <= 0) {
            return;
        }
        float damage = Math.min(MathHelper.floor(blocks * this.hurtPerBlock), this.hurtMax);
        for (Entity e : this.world.getEntitiesWithinAABBExcludingEntity(this, getEntityBoundingBox())) {
            e.attackEntityFrom(DripstoneEvents.FALLING_STALACTITE, damage);
        }
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setInteger("Thickness", this.state.getBlock().getMetaFromState(this.state));
        tag.setInteger("Time", this.fallTime);
        tag.setFloat("FallHurtAmount", this.hurtPerBlock);
        tag.setInteger("FallHurtMax", this.hurtMax);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        if (DripstoneContent.pointed != null) {
            this.state = DripstoneContent.pointed.getStateFromMeta(tag.getInteger("Thickness"));
        }
        this.fallTime = tag.getInteger("Time");
        this.hurtPerBlock = tag.getFloat("FallHurtAmount");
        this.hurtMax = tag.getInteger("FallHurtMax");
    }

    @Override
    public void writeSpawnData(ByteBuf buffer) {
        buffer.writeInt(Block.getStateId(this.state));
    }

    @Override
    public void readSpawnData(ByteBuf buffer) {
        this.state = Block.getStateById(buffer.readInt());
    }

    @Override
    public boolean canRenderOnFire() {
        return false;
    }

    @Override
    public boolean ignoreItemEntityData() {
        return true;
    }
}
