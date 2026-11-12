package com.exiledradio.cavesandcliffsbackport.lush;

import com.exiledradio.cavesandcliffsbackport.ModConfig;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
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
 * Lush caves and glow lichen in newly generated chunks.
 *
 * <h2>Lush caves</h2>
 * Runs when a chunk populates, over the 16x16 columns of its population window, after the caves are carved.
 * Every cave air block inside a {@link LushRegions lush region} that has a floor or a ceiling of stone, dirt,
 * gravel or sand is dressed, by 1.18's proportions:
 * <ul>
 *   <li><b>Floors</b>: moss over most of them, 70% of it under moss vegetation (grass, moss carpet, tall grass,
 *       azaleas); patches of clay two deep, their middles sunk into pools of water wherever the water is held
 *       in on all sides; a dripleaf on one clay block in ten (four big to one small).</li>
 *   <li><b>Ceilings</b>: moss over most of them; cave vines under 12% of the moss, up to 20 long, a fifth of
 *       their blocks with berries; a spore blossom under 1.5%.</li>
 *   <li><b>Azalea trees</b>: from one lush ceiling per chunk (and half the time a second) the column is
 *       followed up, at most 100 blocks, to the first open ground. An azalea tree goes there, and rooted dirt
 *       runs from it back down to the cave (20 tries a layer within 2 blocks), ending in hanging roots.</li>
 * </ul>
 * Underground means at least 4 below the column's highest block, so an open ravine floor is not a cave.
 *
 * <h2>Glow lichen</h2>
 * Everywhere underground, lush or not: {@code lichenTries} tries a chunk (40) between y 5 and 54, each
 * putting lichen on one stone face next to an air block and half the time spreading it once.
 *
 * <p>Nothing is written outside the population window, so no other chunk is ever loaded.
 */
public final class LushCaveGenerator implements IWorldGenerator {

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator generator, IChunkProvider provider) {
        if (world.provider.getDimension() != 0 || world.getWorldType() == WorldType.FLAT) {
            return;
        }
        int x0 = chunkX * 16 + 8, z0 = chunkZ * 16 + 8;
        if (ModConfig.caves.lushRegions) {
            decorate(world, random, x0, z0, world.getSeed());
        }
        if (ModConfig.lush.glowLichen) {
            lichen(world, random, x0, z0, ModConfig.lush.lichenTries);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lush caves

    /**
     * Dresses the lush cave air of the 16x16 columns from {@code x0, z0}.
     *
     * @return how many floor and ceiling blocks were dressed
     */
    public static int decorate(World world, Random random, int x0, int z0, long seed) {
        if (!LushRegions.mayContain(seed, x0, z0, 4, 124)) {
            return 0;
        }
        List<BlockPos> floors = new ArrayList<BlockPos>(), ceilings = new ArrayList<BlockPos>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                int top = Math.min(world.getHeight(x, z) - 4, 124);
                boolean wasAir = false;
                IBlockState below = world.getBlockState(m.setPos(x, 3, z));
                for (int y = 4; y <= top; y++) {
                    IBlockState here = world.getBlockState(m.setPos(x, y, z));
                    boolean air = here.getBlock() == Blocks.AIR;
                    if (air && !wasAir && Soil.lushReplaceable(below) && LushRegions.isLush(seed, x, y, z)) {
                        floors.add(new BlockPos(x, y, z));
                    } else if (!air && wasAir && Soil.lushReplaceable(here) && LushRegions.isLush(seed, x, y - 1, z)) {
                        ceilings.add(new BlockPos(x, y - 1, z));
                    }
                    wasAir = air;
                    below = here;
                }
            }
        }
        for (BlockPos air : floors) {
            floor(world, random, air, seed);
        }
        List<BlockPos> mossy = new ArrayList<BlockPos>();
        for (BlockPos air : ceilings) {
            if (ceiling(world, random, air, seed)) {
                mossy.add(air);
            }
        }
        if (ModConfig.lush.azaleaTrees && !mossy.isEmpty()) {
            int trees = 1 + random.nextInt(2);
            for (int i = 0; i < trees; i++) {
                rootedTree(world, random, mossy.get(random.nextInt(mossy.size())), x0 - 8, z0 - 8);
            }
        }
        return floors.size() + ceilings.size();
    }

    private static void floor(World world, Random random, BlockPos air, long seed) {
        BlockPos ground = air.down();
        int x = air.getX(), z = air.getZ();
        double clay = LushRegions.patch(seed, 1, x, z, 9.0);
        if (clay > 0.60) {
            world.setBlockState(ground, Blocks.CLAY.getDefaultState(), 2);
            BlockPos under = ground.down();
            if (Soil.lushReplaceable(world.getBlockState(under))) {
                world.setBlockState(under, Blocks.CLAY.getDefaultState(), 2);
            }
            if (clay > 0.68 && holdsWater(world, ground)) {
                world.setBlockState(ground, Blocks.WATER.getDefaultState(), 2);
                // 1.18's clay pool with dripleaves: the dripleaf stands in the pool's water, which it keeps. Without
                // Fluidlogged API it could not keep it, and the pool would drain through it: no dripleaf then.
                if (random.nextFloat() < 0.1F && com.exiledradio.backportcore.waterlog.Waterlogging.isAvailable()) {
                    dripleaf(world, random, ground);
                }
            } else if (random.nextFloat() < 0.1F) {
                dripleaf(world, random, air);
            }
        } else if (LushRegions.patch(seed, 2, x, z, 6.0) > 0.22) {
            world.setBlockState(ground, LushContent.moss.getDefaultState(), 2);
            if (random.nextFloat() < 0.7F) {
                BlockMoss.vegetation(world, random, air);
            }
        }
    }

    /** One small (1 in 5) or big dripleaf standing at {@code at}, in air or in the water there. */
    private static void dripleaf(World world, Random random, BlockPos at) {
        EnumFacing facing = EnumFacing.Plane.HORIZONTAL.random(random);
        if (random.nextInt(5) == 0) {
            if (BlockBigDripleaf.canReplace(world, at.up())) {
                LushContent.smallDripleaf.place(world, at, facing, 2);
            }
        } else {
            BlockBigDripleaf.placeTall(world, random, at, facing, 2);
        }
    }

    /** Air or a water source: where 1.18 places glow lichen and hanging roots (waterlogged in the water). */
    private static boolean airOrWater(World world, BlockPos pos) {
        IBlockState s = world.getBlockState(pos);
        return s.getBlock().isAir(s, world, pos)
                || (s.getBlock() == Blocks.WATER || s.getBlock() == Blocks.FLOWING_WATER) && s.getValue(net.minecraft.block.BlockLiquid.LEVEL) == 0;
    }

    /** @return true if water put in place of this floor block would stay: solid under it and on all four sides */
    private static boolean holdsWater(World world, BlockPos ground) {
        if (!solidOrWater(world, ground.down(), false)) {
            return false;
        }
        for (EnumFacing f : EnumFacing.Plane.HORIZONTAL) {
            if (!solidOrWater(world, ground.offset(f), true)) {
                return false;
            }
        }
        return true;
    }

    private static boolean solidOrWater(World world, BlockPos pos, boolean water) {
        IBlockState s = world.getBlockState(pos);
        return s.isNormalCube() || (water && s.getBlock() == Blocks.WATER);
    }

    /** @return true if the ceiling above this air block is moss now */
    private static boolean ceiling(World world, Random random, BlockPos air, long seed) {
        if (LushRegions.patch(seed, 3, air.getX(), air.getZ(), 6.0) <= 0.25) {
            return false;
        }
        world.setBlockState(air.up(), LushContent.moss.getDefaultState(), 2);
        if (!world.isAirBlock(air)) {
            return true;
        }
        float r = random.nextFloat();
        if (r < 0.12F) {
            vines(world, random, air);
        } else if (r < 0.135F) {
            world.setBlockState(air, LushContent.sporeBlossom.getDefaultState(), 2);
        }
        return true;
    }

    /** Cave vines hanging from the block above {@code top}: 1.18's lengths (most under 8, a few up to 20). */
    public static void vines(World world, Random random, BlockPos top) {
        int w = random.nextInt(15);
        int body = w < 2 ? random.nextInt(20) : w < 5 ? random.nextInt(3) : random.nextInt(7);
        int length = 0;
        // Leave the last air block above a floor free, so a vine never stands on the ground.
        while (length <= body && top.getY() - length > 1 && world.isAirBlock(top.down(length)) && world.isAirBlock(top.down(length + 1))) {
            length++;
        }
        for (int i = 0; i < length; i++) {
            boolean berries = random.nextInt(5) == 0;
            IBlockState s = i == length - 1
                    ? LushContent.caveVines.planted(random).withProperty(BlockCaveVines.BERRIES, berries)
                    : LushContent.caveVinesPlant.getDefaultState().withProperty(BlockCaveVines.BERRIES, berries);
            world.setBlockState(top.down(i), s, 2);
        }
    }

    /**
     * 1.17's root system: an azalea tree on the first open ground above a cave, rooted dirt from it down to the
     * cave and hanging roots in the cave.
     *
     * @param origin an air block under the cave's ceiling
     * @param minX   and minZ: the 32x32 window everything stays in
     */
    public static boolean rootedTree(World world, Random random, BlockPos origin, int minX, int minZ) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(origin);
        int x = origin.getX(), z = origin.getZ();
        for (int i = 0; i < 100 && m.getY() < 240; i++) {
            m.move(EnumFacing.UP);
            if (!world.isAirBlock(m) || !world.isAirBlock(m.up()) || !world.isAirBlock(m.up(2))) {
                continue;
            }
            IBlockState ground = world.getBlockState(m.down());
            if (!ground.getMaterial().isSolid() || ground.getMaterial() == Material.LEAVES || ground.getMaterial() == Material.WOOD) {
                return false;
            }
            BlockPos base = m.toImmutable();
            if (!AzaleaTree.grow(world, random, base, minX, minZ, 2, true)) {
                continue;
            }
            for (int y = origin.getY(); y < base.getY(); y++) {
                for (int t = 0; t < 20; t++) {
                    m.setPos(x + random.nextInt(3) - random.nextInt(3), y, z + random.nextInt(3) - random.nextInt(3));
                    if (inside(m, minX, minZ) && rootReplaceable(world.getBlockState(m))) {
                        world.setBlockState(m, LushContent.rootedDirt.getDefaultState(), 2);
                    }
                }
            }
            for (int t = 0; t < 20; t++) {
                m.setPos(x + random.nextInt(3) - random.nextInt(3), origin.getY() + random.nextInt(2) - random.nextInt(2),
                        z + random.nextInt(3) - random.nextInt(3));
                BlockPos up = m.up();
                if (inside(m, minX, minZ) && airOrWater(world, m)
                        && world.getBlockState(up).getBlockFaceShape(world, up, EnumFacing.DOWN) == BlockFaceShape.SOLID) {
                    world.setBlockState(m, LushContent.hangingRoots.getDefaultState(), 2);
                }
            }
            return true;
        }
        return false;
    }

    /** {@code azalea_root_replaceable}: base stone, dirt, clay, gravel, sand, hardened clay, snow blocks. */
    private static boolean rootReplaceable(IBlockState s) {
        return Soil.lushReplaceable(s) || s.getBlock() == Blocks.HARDENED_CLAY || s.getBlock() == Blocks.STAINED_HARDENED_CLAY
                || s.getBlock() == Blocks.SNOW;
    }

    private static boolean inside(BlockPos p, int minX, int minZ) {
        return p.getX() >= minX && p.getX() < minX + 32 && p.getZ() >= minZ && p.getZ() < minZ + 32;
    }

    // ---------------------------------------------------------------------------------------------
    // Glow lichen

    /** @return how many tries placed lichen */
    public static int lichen(World world, Random random, int x0, int z0, int tries) {
        int placed = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        List<EnumFacing> faces = new ArrayList<EnumFacing>(6);
        for (int t = 0; t < tries; t++) {
            int x = x0 + random.nextInt(16), z = z0 + random.nextInt(16);
            int top = Math.min(world.getHeight(x, z) - 4, 54);
            if (top <= 5) {
                continue;
            }
            int y = 5 + random.nextInt(top - 5);
            // A random block is usually stone: look a few blocks down for the cave it may be the roof of.
            for (int i = 0; i < 8 && y > 4 && !airOrWater(world, m.setPos(x, y, z)); i++) {
                y--;
            }
            if (!airOrWater(world, m.setPos(x, y, z))) {
                continue;
            }
            faces.clear();
            for (EnumFacing f : EnumFacing.values()) {
                BlockPos behind = m.offset(f);
                IBlockState s = world.getBlockState(behind);
                if (Soil.mossReplaceable(s) && s.getBlockFaceShape(world, behind, f.getOpposite()) == BlockFaceShape.SOLID) {
                    faces.add(f);
                }
            }
            if (faces.isEmpty()) {
                continue;
            }
            BlockPos at = m.toImmutable();
            BlockGlowLichen.add(world, at, faces.get(random.nextInt(faces.size())), 2);
            placed++;
            if (random.nextBoolean()) {
                List<Object[]> options = BlockGlowLichen.spreads(world, at, BlockGlowLichen.faces(world.getBlockState(at)));
                if (!options.isEmpty()) {
                    Object[] pick = options.get(random.nextInt(options.size()));
                    BlockGlowLichen.add(world, (BlockPos) pick[0], (EnumFacing) pick[1], 2);
                }
            }
        }
        return placed;
    }
}
