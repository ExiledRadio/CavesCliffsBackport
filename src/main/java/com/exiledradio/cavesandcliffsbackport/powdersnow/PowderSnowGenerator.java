package com.exiledradio.cavesandcliffsbackport.powdersnow;

import com.exiledradio.cavesandcliffsbackport.ModConfig;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.IWorldGenerator;

import java.util.Random;

/**
 * Patches of powder snow in snowy high ground.
 *
 * <p>1.18 puts powder snow in its groves and snowy slopes, as noise-shaped patches of the surface a few
 * blocks deep, level with the ground. 1.12's terrain has neither biome, so a patch goes wherever the ground
 * is under snow, cold enough to snow at its height, and at or above {@code patchMinY}: the snowy tops of
 * mountains and hills, not the flat snowfields.
 *
 * <p>A patch is a rough disc 2 to 4 blocks across its radius, three deep in the middle and one at the rim.
 * It replaces the snow layer and the ground under it, so its top is flush with the ground around it.
 * It stays inside the four chunks that are loaded while a chunk is populated.
 */
public final class PowderSnowGenerator implements IWorldGenerator {

    private static final int TRIES = 6;

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator generator, IChunkProvider provider) {
        if (com.exiledradio.cavesandcliffsbackport.terrain.ModernTerrain.owns(world)) {
            return; // the 1.18 terrain places its own
        }
        ModConfig.PowderSnow config = ModConfig.powderSnow;
        if (!config.patches || !world.provider.isSurfaceWorld() || world.getWorldType() == WorldType.FLAT
                || random.nextInt(config.patchRarity) != 0) {
            return;
        }
        // Snowy heights are a small part of most chunks that have any, so a chunk looks at a few columns
        // and takes the first that will do.
        for (int i = 0; i < TRIES; i++) {
            int x = (chunkX << 4) + 8 + random.nextInt(16);
            int z = (chunkZ << 4) + 8 + random.nextInt(16);
            if (patch(world, random, x, z, config.patchMinY, true) > 0) {
                return;
            }
        }
    }

    /**
     * @param climate false places the patch on any ground at any height (for tests)
     * @return how many blocks became powder snow
     */
    public static int patch(World world, Random random, int centerX, int centerZ, int minY, boolean climate) {
        if (climate && surface(world, centerX, centerZ, minY, true) == null) {
            return 0;
        }
        int radius = 2 + random.nextInt(3);
        int placed = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz) / (radius + 0.5D);
                if (d > 0.75D + random.nextDouble() * 0.25D) {
                    continue;
                }
                BlockPos top = surface(world, centerX + dx, centerZ + dz, minY, climate);
                if (top == null) {
                    continue;
                }
                int depth = 1 + (int) (2.0D * (1.0D - d) + random.nextDouble());
                if (world.getBlockState(top.up()).getBlock() == Blocks.SNOW_LAYER) {
                    world.setBlockState(top.up(), Blocks.AIR.getDefaultState(), 2 | 16);
                }
                for (int i = 0; i < depth; i++) {
                    BlockPos p = top.down(i);
                    if (!ground(world.getBlockState(p))) {
                        break;
                    }
                    world.setBlockState(p, PowderSnowContent.powderSnow.getDefaultState(), 2 | 16);
                    placed++;
                }
            }
        }
        return placed;
    }

    /** @return the top ground block of a column if a patch may take it, else null */
    private static BlockPos surface(World world, int x, int z, int minY, boolean climate) {
        // The height map counts only blocks that dim light, so a snow layer sits at that height, on the ground.
        BlockPos top = new BlockPos(x, world.getHeight(x, z) - 1, z);
        boolean snowy = world.getBlockState(top.up()).getBlock() == Blocks.SNOW_LAYER;
        IBlockState state = world.getBlockState(top);
        if (!ground(state)) {
            return null;
        }
        if (climate && (!(snowy || state.getBlock() == Blocks.SNOW) || top.getY() < minY
                || world.getBiome(top).getTemperature(top) >= 0.15F)) {
            return null;
        }
        return top;
    }

    /** What a patch replaces: the natural ground of snowy heights, never ores, ice, water or anything built. */
    private static boolean ground(IBlockState state) {
        Block b = state.getBlock();
        return b == Blocks.GRASS || b == Blocks.DIRT || b == Blocks.STONE || b == Blocks.SNOW || b == Blocks.GRAVEL;
    }
}
