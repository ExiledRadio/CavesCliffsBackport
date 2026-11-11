package com.exiledradio.cavesandcliffsbackport.powdersnow;

import com.exiledradio.cavesandcliffsbackport.ModConfig;

import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityStray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/**
 * Skeletons turning into strays (current Java): 140 ticks in powder snow start it, 300 ticks of shaking
 * finish it, and once started it finishes wherever the skeleton goes. Only the plain skeleton converts, not
 * wither skeletons or another mod's.
 */
final class StrayConversion {

    /** What Java's conversion carries over; the stray is otherwise new, at full health. */
    private static final String[] KEPT = {"HandItems", "ArmorItems", "HandDropChances", "ArmorDropChances", "CanPickUpLoot",
            "PersistenceRequired", "CustomName", "CustomNameVisible", "NoAI", "Invulnerable", "LeftHanded", "Silent"};

    private StrayConversion() {
    }

    /** @return whether the skeleton is shaking (converting) */
    static boolean tick(EntitySkeleton skeleton, NBTTagCompound f, boolean inSnow) {
        if (!ModConfig.powderSnow.strayConversion) {
            f.removeTag("SnowTime");
            f.removeTag("Convert");
            return false;
        }
        if (!skeleton.isEntityAlive() || skeleton.isAIDisabled()) {
            return f.hasKey("Convert");
        }
        if (f.hasKey("Convert")) {
            int left = f.getInteger("Convert") - 1;
            if (left < 0) {
                convert(skeleton);
                return false;
            }
            f.setInteger("Convert", left);
            return true;
        }
        if (!inSnow) {
            f.removeTag("SnowTime");
            return false;
        }
        int time = f.getInteger("SnowTime") + 1;
        if (time >= Freezing.SKELETON_SNOW_TICKS) {
            f.removeTag("SnowTime");
            f.setInteger("Convert", Freezing.SKELETON_CONVERT_TICKS);
            return true;
        }
        f.setInteger("SnowTime", time);
        return false;
    }

    static EntityStray convert(EntitySkeleton skeleton) {
        EntityStray stray = new EntityStray(skeleton.world);
        stray.copyLocationAndAnglesFrom(skeleton);
        NBTTagCompound from = skeleton.writeToNBT(new NBTTagCompound());
        NBTTagCompound to = stray.writeToNBT(new NBTTagCompound());
        for (String key : KEPT) {
            if (from.hasKey(key)) {
                to.setTag(key, from.getTag(key));
            }
        }
        stray.readFromNBT(to);
        boolean silent = skeleton.isSilent();
        BlockPos pos = new BlockPos(skeleton);
        skeleton.world.removeEntity(skeleton);
        skeleton.world.spawnEntity(stray);
        if (!silent) {
            Freezing.converted(stray.world, pos);
        }
        return stray;
    }
}
