package com.exiledradio.cavesandcliffsbackport.glowsquid;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

/**
 * The glow item frame: an item frame of its own entity type whose contents are drawn at full brightness.
 *
 * <p>It is a subclass of the vanilla frame, so everything that works with item frames (comparators, maps,
 * other mods looking for {@code EntityItemFrame}) works with it. What differs is the item it drops and is
 * picked as, its sounds and its look.
 */
public class EntityGlowItemFrame extends EntityItemFrame implements IEntityAdditionalSpawnData {

    public EntityGlowItemFrame(World world) {
        super(world);
    }

    public EntityGlowItemFrame(World world, BlockPos pos, EnumFacing facing) {
        super(world, pos, facing);
    }

    /** Every sound the vanilla frame's code plays, as the glow frame's own. */
    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        if (sound == SoundEvents.ENTITY_ITEMFRAME_ADD_ITEM) {
            sound = GlowSquidContent.frameAddItem;
        } else if (sound == SoundEvents.ENTITY_ITEMFRAME_BREAK) {
            sound = GlowSquidContent.frameBreak;
        } else if (sound == SoundEvents.ENTITY_ITEMFRAME_PLACE) {
            sound = GlowSquidContent.framePlace;
        } else if (sound == SoundEvents.ENTITY_ITEMFRAME_REMOVE_ITEM) {
            sound = GlowSquidContent.frameRemoveItem;
        } else if (sound == SoundEvents.ENTITY_ITEMFRAME_ROTATE_ITEM) {
            sound = GlowSquidContent.frameRotateItem;
        }
        super.playSound(sound, volume, pitch);
    }

    /** Drops the glow frame itself where vanilla would drop an item frame; the contents are vanilla's business. */
    @Override
    public void dropItemOrSelf(@Nullable Entity breaker, boolean dropSelf) {
        boolean creative = breaker instanceof EntityPlayer && ((EntityPlayer) breaker).capabilities.isCreativeMode;
        if (dropSelf && !creative && this.world.getGameRules().getBoolean("doEntityDrops")) {
            this.entityDropItem(new ItemStack(GlowSquidContent.glowItemFrame), 0.0F);
        }
        super.dropItemOrSelf(breaker, false);
    }

    @Override
    public ItemStack getPickedResult(RayTraceResult target) {
        ItemStack held = this.getDisplayedItem();
        return held.isEmpty() ? new ItemStack(GlowSquidContent.glowItemFrame) : held.copy();
    }

    /** A mod entity's spawn packet carries a position but not what a hanging entity hangs on. */
    @Override
    public void writeSpawnData(ByteBuf buffer) {
        buffer.writeLong(this.hangingPosition.toLong());
        buffer.writeByte(this.facingDirection == null ? 0 : this.facingDirection.getHorizontalIndex());
    }

    @Override
    public void readSpawnData(ByteBuf buffer) {
        this.hangingPosition = BlockPos.fromLong(buffer.readLong());
        this.updateFacingWithBoundingBox(EnumFacing.byHorizontalIndex(buffer.readByte()));
    }

    /** Current Java: the frame is drawn with block light 5 at least. */
    @SideOnly(Side.CLIENT)
    @Override
    public int getBrightnessForRender() {
        int light = super.getBrightnessForRender();
        int block = Math.max(light >> 4 & 15, 5);
        return light & 0xFFFF0000 | block << 4;
    }
}
