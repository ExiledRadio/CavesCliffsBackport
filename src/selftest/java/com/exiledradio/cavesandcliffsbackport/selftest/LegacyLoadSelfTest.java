package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.backportcore.speleothem.EntityFallingSpeleothem;
import com.exiledradio.backportcore.speleothem.Thickness;
import com.exiledradio.cavesandcliffsbackport.dripstone.BlockPointedDripstone;
import com.exiledradio.cavesandcliffsbackport.dripstone.DripstoneContent;
import com.exiledradio.cavesandcliffsbackport.dripstone.EntityFallingStalactite;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.util.List;

/**
 * 1.2.0's move of pointed dripstone onto Backport Core's speleothem, against worlds saved by 1.1.7.
 *
 * <ul>
 *   <li>Always: a falling stalactite as 1.1.7 saved it ({@code cavesandcliffsbackport:falling_stalactite} with
 *       {@code Thickness}, {@code Time}, {@code FallHurtAmount}, {@code FallHurtMax}) loads and turns into Core's
 *       falling segment with its state, place, speed and damage.</li>
 *   <li>In a world saved by the 1.1.7 release with its scratch {@code legacysave} test (marked by the file
 *       {@code legacy117.txt} in the world folder): the stalactite, stalagmite and merged pair it left at the spawn
 *       point, at y 200, load with their states, and the falling stalactite it left in the air is Core's.</li>
 * </ul>
 */
public final class LegacyLoadSelfTest implements SelfTest {

    @Override
    public String name() {
        return "legacyload";
    }

    @Override
    public void run(Check c) {
        BlockPointedDripstone p = DripstoneContent.pointed;
        // A 1.1.7 falling stalactite's NBT, read back now.
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("id", "cavesandcliffsbackport:falling_stalactite");
        NBTTagList pos = new NBTTagList();
        BlockPos spawn = c.world.getSpawnPoint();
        for (double d : new double[] {spawn.getX() + 0.5, 240, spawn.getZ() + 0.5}) {
            pos.appendTag(new NBTTagDouble(d));
        }
        tag.setTag("Pos", pos);
        NBTTagList motion = new NBTTagList();
        for (double d : new double[] {0, -0.5, 0}) {
            motion.appendTag(new NBTTagDouble(d));
        }
        tag.setTag("Motion", motion);
        tag.setInteger("Thickness", p.getMetaFromState(p.state(EnumFacing.DOWN, Thickness.TIP)));
        tag.setInteger("Time", 12);
        tag.setFloat("FallHurtAmount", 7.0F);
        tag.setInteger("FallHurtMax", 40);
        Entity old = EntityList.createEntityFromNBT(tag, c.world);
        c.ok(old instanceof EntityFallingStalactite, "1.1.7's falling stalactite still loads");
        if (old instanceof EntityFallingStalactite) {
            EntityFallingSpeleothem now = ((EntityFallingStalactite) old).replacement();
            c.eq(p.state(EnumFacing.DOWN, Thickness.TIP), now.getState(), "as Core's falling segment, with its segment");
            c.ok(Math.abs(now.posY - old.posY) < 1.0E-6 && Math.abs(now.motionY + 0.5) < 1.0E-6, "its place and speed");
            c.eq(7.0F, now.hurtPerBlock(), "and its damage");
            NBTTagCompound saved = now.writeToNBT(new NBTTagCompound());
            c.eq(12, saved.getInteger("Time"), "and its fall time");
            c.world.spawnEntity(old);
            old.onUpdate();
            c.ok(old.isDead, "the old one is gone after its first tick");
        }

        File marker = new File(c.world.getSaveHandler().getWorldDirectory(), "legacy117.txt");
        if (!marker.isFile()) {
            c.ok(true, "no 1.1.7 world here (only the NBT check)");
            return;
        }
        BlockPos base = new BlockPos(spawn.getX(), 200, spawn.getZ());
        c.eq(p.state(EnumFacing.DOWN, Thickness.BASE), c.world.getBlockState(base.down(1)), "1.1.7 world: the stalactite's base");
        c.eq(p.state(EnumFacing.DOWN, Thickness.FRUSTUM), c.world.getBlockState(base.down(2)), "its frustum");
        c.eq(p.state(EnumFacing.DOWN, Thickness.TIP), c.world.getBlockState(base.down(3)), "its tip");
        BlockPos floor = base.add(2, -10, 0);
        c.eq(p.state(EnumFacing.UP, Thickness.FRUSTUM), c.world.getBlockState(floor.up(1)), "the stalagmite's frustum");
        c.eq(p.state(EnumFacing.UP, Thickness.TIP), c.world.getBlockState(floor.up(2)), "its tip");
        BlockPos top = base.add(4, 0, 0);
        c.eq(p.state(EnumFacing.DOWN, Thickness.TIP_MERGE), c.world.getBlockState(top.down(1)), "the merged pair's stalactite");
        c.eq(p.state(EnumFacing.UP, Thickness.TIP_MERGE), c.world.getBlockState(top.down(2)), "and stalagmite");
        // The falling one left in the air: by now Core's (or landed: then a dripstone item or nothing).
        AxisAlignedBB column = new AxisAlignedBB(base.getX() + 5, 0, base.getZ() - 1, base.getX() + 8, 256, base.getZ() + 2);
        List<EntityFallingStalactite> stale = c.world.getEntitiesWithinAABB(EntityFallingStalactite.class, column);
        List<EntityFallingSpeleothem> core = c.world.getEntitiesWithinAABB(EntityFallingSpeleothem.class, column);
        c.ok(stale.isEmpty(), "no 1.1.7 falling stalactite is left: " + stale.size());
        com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport.LOGGER.info("SELFTEST legacyload: {} Core falling segments in the column", core.size());
        IBlockState tip = c.world.getBlockState(base.down(3));
        c.ok(tip.getBlock() != Blocks.AIR, "nothing fell that should not have");
    }
}
