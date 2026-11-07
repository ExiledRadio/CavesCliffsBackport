package com.exiledradio.cavesandcliffsbackport.amethyst;

import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.IWorldGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Amethyst geodes, 1.17's settings within 1.12's height: one in {@code geodeRarity} chunks (24), between
 * {@code geodeMinY} and {@code geodeMaxY} (6-46).
 *
 * <h2>Shape (our own implementation of 1.17's layered geode)</h2>
 * Three or four points are scattered 4-6 blocks from a corner, each with an offset of 1-2. For every
 * block in reach, {@code s} is the sum over the points of {@code 1 / sqrt(distance^2 + offset)}, plus a
 * little noise. Thresholds from 1.17's layer sizes (filling 1.7, inner 2.2, middle 3.2, outer 4.2, each
 * as {@code 1 / sqrt(size)}, the last three widened by points / 6) pick air, amethyst (budding 8.3% of the
 * time), calcite or smooth basalt. 95% of geodes get a crack: a few points in a line out one side, whose
 * own sum opens the shell to the air inside. Then 35% of the budding blocks get a random bud on their first
 * open face.
 *
 * <p>A geode aborts if more than one of its points lies in bedrock, water, lava or ice. It never replaces
 * bedrock, spawners or chests, and it stays inside the population window, so it never loads another
 * chunk. Calcite and smooth basalt are stream D's; without them (stream off) no geodes generate.
 */
public final class GeodeGenerator implements IWorldGenerator {

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator generator, IChunkProvider provider) {
        if (!ModConfig.amethyst.geodes || world.provider.getDimension() != 0 || world.getWorldType() == WorldType.FLAT
                || ModConfig.amethyst.geodeRarity <= 0 || random.nextInt(ModConfig.amethyst.geodeRarity) != 0) {
            return;
        }
        int lo = Math.max(1, ModConfig.amethyst.geodeMinY);
        int hi = Math.max(lo + 1, ModConfig.amethyst.geodeMaxY);
        // The points sit 4-6 blocks from the corner, so the geode's middle is about 5 past it; keep the
        // whole thing inside the 32x32 population window [chunk*16, chunk*16+32).
        BlockPos corner = new BlockPos(chunkX * 16 + 7 + random.nextInt(8), lo + random.nextInt(hi - lo), chunkZ * 16 + 7 + random.nextInt(8));
        place(world, random, corner, chunkX * 16, chunkZ * 16);
    }

    /**
     * Places a geode at a corner, writing only inside {@code [minX, minX+32) x [minZ, minZ+32)}.
     *
     * @return true if it was placed
     */
    public static boolean place(World world, Random random, BlockPos corner, int minX, int minZ) {
        Block calcite = Owners.ourBlock("calcite");
        Block basalt = Owners.ourBlock("smooth_basalt");
        if (calcite == null || basalt == null || AmethystContent.amethyst == null) {
            return false;
        }
        int points = 3 + random.nextInt(2);
        List<int[]> pts = new ArrayList<int[]>();
        int invalid = 0;
        for (int i = 0; i < points; i++) {
            int[] p = {corner.getX() + 4 + random.nextInt(3), corner.getY() + 4 + random.nextInt(3),
                    corner.getZ() + 4 + random.nextInt(3), 1 + random.nextInt(2)};
            pts.add(p);
            if (invalidHere(world.getBlockState(new BlockPos(p[0], p[1], p[2]))) && ++invalid > 1) {
                return false;
            }
        }
        double widen = points / 6.0;
        double filling = 1 / Math.sqrt(1.7), inner = 1 / Math.sqrt(2.2 + widen),
                middle = 1 / Math.sqrt(3.2 + widen), outer = 1 / Math.sqrt(4.2 + widen);
        double crackLimit = 1 / Math.sqrt(2.0 + random.nextDouble() / 2 + (points > 3 ? widen : 0));
        List<int[]> crack = new ArrayList<int[]>();
        if (random.nextFloat() < 0.95F) {
            int side = random.nextInt(4), reach = points * 2 + 1;
            for (int dy : new int[] {7, 5, 1}) {
                int cx = corner.getX(), cz = corner.getZ();
                if (side == 0) {
                    cx += reach;
                } else if (side == 1) {
                    cz += reach;
                } else if (side == 2) {
                    cx += reach;
                    cz += 7;
                } else {
                    cx += 7;
                    cz += reach;
                }
                crack.add(new int[] {cx, corner.getY() + dy, cz});
            }
        }
        IBlockState amethyst = AmethystContent.amethyst.getDefaultState();
        IBlockState budding = AmethystContent.budding.getDefaultState();
        List<BlockPos> buddingPlaced = new ArrayList<BlockPos>();
        Random noise = new Random(world.getSeed() ^ corner.toLong());
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = corner.getX() - 4; x <= corner.getX() + 14; x++) {
            if (x < minX || x >= minX + 32) {
                continue;
            }
            for (int z = corner.getZ() - 4; z <= corner.getZ() + 14; z++) {
                if (z < minZ || z >= minZ + 32) {
                    continue;
                }
                for (int y = Math.max(1, corner.getY() - 4); y <= Math.min(254, corner.getY() + 14); y++) {
                    double s = 0;
                    for (int[] p : pts) {
                        double dx = x - p[0], dy = y - p[1], dz = z - p[2];
                        s += 1 / Math.sqrt(dx * dx + dy * dy + dz * dz + p[3]);
                    }
                    s += (noise.nextDouble() - 0.5) * 0.1;
                    if (s < outer) {
                        continue;
                    }
                    double c = 0;
                    for (int[] p : crack) {
                        double dx = x - p[0], dy = y - p[1], dz = z - p[2];
                        c += 1 / Math.sqrt(dx * dx + dy * dy + dz * dz + 2);
                    }
                    m.setPos(x, y, z);
                    if (cannotReplace(world.getBlockState(m))) {
                        continue;
                    }
                    IBlockState put;
                    if (!crack.isEmpty() && c >= crackLimit && s < filling) {
                        put = Blocks.AIR.getDefaultState();
                    } else if (s >= filling) {
                        put = Blocks.AIR.getDefaultState();
                    } else if (s >= inner) {
                        boolean bud = random.nextFloat() < 0.083F;
                        put = bud ? budding : amethyst;
                        if (bud && random.nextFloat() < 0.35F) {
                            buddingPlaced.add(m.toImmutable());
                        }
                    } else if (s >= middle) {
                        put = calcite.getDefaultState();
                    } else {
                        put = basalt.getDefaultState();
                    }
                    world.setBlockState(m, put, 2);
                }
            }
        }
        BlockAmethystCluster[] buds = AmethystContent.stages;
        for (BlockPos b : buddingPlaced) {
            BlockAmethystCluster bud = buds[random.nextInt(buds.length)];
            for (EnumFacing f : EnumFacing.values()) {
                BlockPos at = b.offset(f);
                if (at.getX() < minX || at.getX() >= minX + 32 || at.getZ() < minZ || at.getZ() >= minZ + 32) {
                    continue;
                }
                if (BlockAmethyst.Budding.canGrowInto(world.getBlockState(at))) {
                    world.setBlockState(at, bud.getDefaultState().withProperty(BlockAmethystCluster.FACING, f), 2);
                    break;
                }
            }
        }
        return true;
    }

    private static boolean invalidHere(IBlockState s) {
        Block b = s.getBlock();
        return b == Blocks.BEDROCK || s.getMaterial() == Material.WATER || s.getMaterial() == Material.LAVA
                || b == Blocks.ICE || b == Blocks.PACKED_ICE;
    }

    private static boolean cannotReplace(IBlockState s) {
        Block b = s.getBlock();
        return b == Blocks.BEDROCK || b == Blocks.MOB_SPAWNER || b == Blocks.CHEST || b == Blocks.END_PORTAL_FRAME
                || b.hasTileEntity(s);
    }
}
