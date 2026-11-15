package com.exiledradio.cavesandcliffsbackport.terrain;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Random;

/**
 * The bees of a bee nest 1.18's trees put down (BeehiveDecorator), in the nest a backport registers under
 * {@code bee_nest} (Buzzy Bees Backport's). Without one the nest is air ({@link StateMap}).
 *
 * <p>Vanilla stores 2 or 3 bees, each a compound holding only the bee's entity id, {@code random.nextInt(599)}
 * ticks into its stay and not carrying nectar (600 ticks' minimum stay): {@code BeehiveBlockEntity.storeBee}'s
 * format, which the backport's hive reads. The terrain core draws those numbers from the feature's random as
 * vanilla does and keeps its own sequence, but does not hand them over; these come from a random seeded by the
 * nest's position (vanilla's nest position itself is not reproducible, see the core's Trees).
 */
final class Nests {

    private Nests() {
    }

    /** The nest's tile entity with its bees, or null when the block has none. */
    @javax.annotation.Nullable
    static TileEntity create(World world, BlockPos pos, IBlockState state) {
        ResourceLocation block = state.getBlock().getRegistryName();
        if (block == null) {
            return null;
        }
        TileEntity te = state.getBlock().createTileEntity(world, state);
        if (te == null) {
            return null;
        }
        Random random = new Random(world.getSeed() ^ pos.toLong() * 0x9E3779B97F4A7C15L);
        String bee = new ResourceLocation(block.getNamespace(), "bee").toString();
        NBTTagList bees = new NBTTagList();
        int n = 2 + random.nextInt(2);
        for (int i = 0; i < n; i++) {
            NBTTagCompound data = new NBTTagCompound();
            data.setString("id", bee);
            NBTTagCompound entry = new NBTTagCompound();
            entry.setTag("EntityData", data);
            entry.setInteger("TicksInHive", random.nextInt(599));
            entry.setInteger("MinOccupationTicks", 600);
            bees.appendTag(entry);
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("x", pos.getX());
        tag.setInteger("y", pos.getY());
        tag.setInteger("z", pos.getZ());
        tag.setTag("Bees", bees);
        te.readFromNBT(tag);
        return te;
    }
}
