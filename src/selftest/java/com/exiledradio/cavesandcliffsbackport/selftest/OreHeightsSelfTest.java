package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.Owners;
import com.exiledradio.cavesandcliffsbackport.ores.ModdedOres;
import com.exiledradio.cavesandcliffsbackport.ores.OreHeights;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.state.pattern.BlockMatcher;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.OreDictionary;

import java.util.Random;

/**
 * Stream O2: other mods' ores in the lower half of a Caves &amp; Cliffs world. Stand-in "mods": ore dictionary names on
 * blocks no world uses (purpur as {@code oreSelftestium}, purpur pillars as {@code oreSelftestiumVein}, red nether
 * bricks as {@code oreNetherSelftestium}, end bricks as nothing) and two {@code IWorldGenerator}s of this test's
 * package (the remap skips the family's own generators, but not the self-test's), each acting only in its own chunks:
 * <ul>
 *   <li>{@link Fixed}: one chunk, fixed placements through the usual stone check
 *       ({@code isReplaceableOreGen} with stone), each prepared first: ore at y 10 lands at -44 (deepslate there, air
 *       at 10: the check is answered at the target), 40 at 16, 100 at 100; an end brick and a Nether ore at 20 stay;
 *       an ore whose target is air is not placed anywhere; an ore set without a check lands in tuff.</li>
 *   <li>{@link Veins}: an 8 by 8 chunk area, four one-block "veins" per chunk at random y 0-63 through the stone
 *       check: in a Caves &amp; Cliffs world the area holds about twice as many in y -64 to 63 (the second run), none
 *       left in 0-63 above their target; in a Default world as many as were placed, at their own heights.</li>
 * </ul>
 * Runs in the whole suite (a Default world: nothing moves) and with {@code tools/height/selftest.sh oreheights}.
 */
public final class OreHeightsSelfTest implements SelfTest {

    private static final int AX = -6000, AZ = 7000;
    private static final int BX = -6100, BZ = 7100, N = 8;
    private static final int VEINS = 4;

    static IBlockState ore, veinOre, netherOre, notOre, deepslate, tuff;
    static volatile boolean on;
    static int veinAttempts;

    @Override
    public String name() {
        return "oreheights";
    }

    @Override
    public void run(Check check) throws Exception {
        WorldServer world = check.world;
        ore = Blocks.PURPUR_BLOCK.getDefaultState();
        veinOre = Blocks.PURPUR_PILLAR.getDefaultState();
        netherOre = Blocks.RED_NETHER_BRICK.getDefaultState();
        notOre = Blocks.END_BRICKS.getDefaultState();
        Block d = Owners.ourBlock("deepslate");
        Block t = Owners.ourBlock("tuff");
        check.ok(d != null && t != null, "deepslate and tuff are registered");
        if (d == null || t == null) {
            return;
        }
        deepslate = d.getDefaultState();
        tuff = t.getDefaultState();
        OreDictionary.registerOre("oreSelftestium", new ItemStack(Blocks.PURPUR_BLOCK));
        OreDictionary.registerOre("oreSelftestiumVein", new ItemStack(Blocks.PURPUR_PILLAR, 1, OreDictionary.WILDCARD_VALUE));
        OreDictionary.registerOre("oreNetherSelftestium", new ItemStack(Blocks.RED_NETHER_BRICK));
        OreDictionary.registerOre("oreSelftestiumNether", new ItemStack(Blocks.RED_NETHER_BRICK));
        check.ok(ModdedOres.isOre(ore), "the stand-in ore is an overworld ore");
        check.ok(ModdedOres.isOre(veinOre), "the stand-in vein ore (wildcard metadata) is an overworld ore");
        check.ok(!ModdedOres.isOre(netherOre), "an oreNether* block is not, though also under an overworld name");
        check.ok(!ModdedOres.isOre(notOre), "a block under no ore name is not");
        check.ok(!ModdedOres.isOre(Blocks.QUARTZ_ORE.getDefaultState()), "Nether quartz ore is not");
        check.ok(ModdedOres.isOre(Blocks.IRON_ORE.getDefaultState()), "vanilla iron ore is (a mod's extra iron moves too)");
        check.ok(ModdedOres.blockCount() > 5, "overworld ore blocks found: " + ModdedOres.blockCount());

        GameRegistry.registerWorldGenerator(new Fixed(), 1000);
        GameRegistry.registerWorldGenerator(new Veins(), 1001);
        on = true;
        boolean remap = ModConfig.height.moddedOreHeights && OreHeights.applies(world, generatorOf(world));
        CavesAndCliffsBackport.LOGGER.info("SELFTEST oreheights: remap applies here: {}", remap);
        try {
            fixed(check, world, remap);
            veins(check, world, remap);
        } finally {
            on = false;
        }
    }

    private static IChunkGenerator generatorOf(WorldServer world) {
        return world.getChunkProvider().chunkGenerator;
    }

    private static void populate(WorldServer world, int x0, int z0, int n) {
        for (int x = x0; x <= x0 + n; x++) {
            for (int z = z0; z <= z0 + n; z++) {
                world.getChunkProvider().provideChunk(x, z);
            }
        }
    }

    private static BlockPos at(int dx, int y, int dz) {
        return new BlockPos(AX * 16 + 8 + dx, y, AZ * 16 + 8 + dz);
    }

    private static void fixed(Check check, WorldServer world, boolean remap) {
        check.ok(!world.getChunkProvider().isChunkGeneratedAt(AX, AZ), "the fixed chunk is new");
        populate(world, AX, AZ, 1);
        check.ok(world.getChunk(AX, AZ).isTerrainPopulated(), "the fixed chunk is populated");
        if (remap) {
            check.eq(ore, world.getBlockState(at(1, -44, 1)), "ore aimed at y 10 is at -44 (deepslate there)");
            check.eq(Blocks.AIR.getDefaultState(), world.getBlockState(at(1, 10, 1)), "y 10 itself is left as it was (air)");
            check.eq(ore, world.getBlockState(at(3, 16, 3)), "ore aimed at y 40 is at 16");
            check.ok(world.getBlockState(at(3, 40, 3)) != ore, "and not at 40");
            check.eq(ore, world.getBlockState(at(5, 100, 5)), "ore at y 100 stays at 100");
            check.eq(notOre, world.getBlockState(at(7, 20, 7)), "a non-ore at y 20 stays at 20");
            check.eq(Blocks.STONE.getDefaultState(), world.getBlockState(at(7, -24, 7)), "and nothing is set at -24");
            check.eq(netherOre, world.getBlockState(at(9, 20, 9)), "a Nether ore at y 20 stays at 20");
            check.eq(Blocks.STONE.getDefaultState(), world.getBlockState(at(9, -24, 9)), "and nothing is set at -24");
            check.eq(Blocks.AIR.getDefaultState(), world.getBlockState(at(11, -4, 11)), "ore aimed at 30 over an air target: not placed there");
            check.eq(Blocks.STONE.getDefaultState(), world.getBlockState(at(11, 30, 11)), "nor at 30");
            check.eq(ore, world.getBlockState(at(13, 36, 13)), "ore set at 50 without a check lands in tuff at 36");
        } else {
            check.eq(Blocks.AIR.getDefaultState(), world.getBlockState(at(1, 10, 1)), "Default: ore at y 10 over air is not placed");
            check.eq(ore, world.getBlockState(at(3, 40, 3)), "Default: ore at y 40 stays at 40");
            check.eq(ore, world.getBlockState(at(5, 100, 5)), "Default: ore at y 100 stays at 100");
            check.eq(notOre, world.getBlockState(at(7, 20, 7)), "Default: a non-ore at y 20 stays");
            check.eq(ore, world.getBlockState(at(11, 30, 11)), "Default: ore at 30 is placed at 30");
            check.eq(ore, world.getBlockState(at(13, 50, 13)), "Default: ore set at 50 stays at 50");
        }
    }

    private static void veins(Check check, WorldServer world, boolean remap) {
        long moved = OreHeights.movedOres, second = OreHeights.secondRunOres;
        veinAttempts = 0;
        long t = System.nanoTime();
        populate(world, BX, BZ, N);
        long ms = (System.nanoTime() - t) / 1000000;
        long m1 = OreHeights.movedOres - moved, m2 = OreHeights.secondRunOres - second;
        int low = 0, band = 0, high = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int minY = remap ? -64 : 0;
        for (int x = BX * 16 + 8; x < (BX + N) * 16 + 8; x++) {
            for (int z = BZ * 16 + 8; z < (BZ + N) * 16 + 8; z++) {
                for (int y = minY; y < 128; y++) {
                    if (world.getBlockState(p.setPos(x, y, z)).getBlock() == Blocks.PURPUR_PILLAR) {
                        if (y < 0) {
                            low++;
                        } else if (y < 64) {
                            band++;
                        } else {
                            high++;
                        }
                    }
                }
            }
        }
        int chunks = N * N;
        CavesAndCliffsBackport.LOGGER.info("SELFTEST oreheights veins: {} chunks in {} ms, attempts {} (pass 1 + 2), moved {}, "
                + "second run {}, found below 0: {}, 0-63: {}, 64+: {}", chunks, ms, veinAttempts, m1, m2, low, band, high);
        check.eq(0, high, "no vein ore above y 63");
        if (remap) {
            int found = low + band;
            // Targets cover -64 to 62, an even y only: half of them below 0.
            check.ok(m1 > chunks * VEINS * 0.6, "first run: most of " + chunks * VEINS + " veins placed (" + m1 + ")");
            check.ok(m2 > m1 * 0.8 && m2 < m1 * 1.25, "second run places about as many (" + m2 + " vs " + m1 + ")");
            check.ok(Math.abs(found - (m1 + m2)) <= 3, "every placed vein is in the world: " + found + " vs " + (m1 + m2));
            // A vein only lands in rock: the share of rock at the targets' heights (even y, -64 to 62) bounds it.
            int rock = 0, all = 0;
            Block deep = deepslate.getBlock(), tuffBlock = tuff.getBlock();
            for (int x = BX * 16 + 8; x < (BX + N) * 16 + 8; x++) {
                for (int z = BZ * 16 + 8; z < (BZ + N) * 16 + 8; z++) {
                    for (int y = -64; y < 64; y += 2) {
                        Block b = world.getBlockState(p.setPos(x, y, z)).getBlock();
                        all++;
                        if (b == Blocks.STONE || b == deep || b == tuffBlock || b == Blocks.PURPUR_PILLAR) {
                            rock++;
                        }
                    }
                }
            }
            double share = rock / (double) all;
            double ratio = found / (double) (chunks * VEINS);
            CavesAndCliffsBackport.LOGGER.info("SELFTEST oreheights veins: rock share at the targets {}, veins found per vein aimed {}",
                    String.format("%.3f", share), String.format("%.3f", ratio));
            check.ok(Math.abs(m1 / (double) (chunks * VEINS) - share) < 0.1, "first run lands where there is rock: "
                    + m1 + " of " + chunks * VEINS + " vs rock share " + String.format("%.3f", share));
            check.ok(Math.abs(ratio - 2 * share) < 0.2, "veins in the low band doubled: " + found + " for " + chunks * VEINS
                    + " aimed (x" + String.format("%.2f", ratio) + ", rock share " + String.format("%.3f", share) + ")");
            check.ok(low > found / 3 && band > found / 3, "spread over -64..63: " + low + " below 0, " + band + " at 0-63");
            for (int x = BX * 16 + 8; x < (BX + N) * 16 + 8; x++) {
                for (int z = BZ * 16 + 8; z < (BZ + N) * 16 + 8; z++) {
                    for (int y = -64; y < 64; y++) {
                        if (world.getBlockState(p.setPos(x, y, z)).getBlock() == Blocks.PURPUR_PILLAR && (y & 1) != 0) {
                            check.fail("vein ore at odd y " + y + " (" + x + ", " + z + "): not a remapped height");
                            return;
                        }
                    }
                }
            }
        } else {
            check.eq(0L, m1 + m2, "Default: nothing moved");
            check.ok(band > chunks * VEINS * 0.4 && band <= chunks * VEINS, "Default: veins at their own heights, not doubled: "
                    + band + " for " + chunks * VEINS);
            check.eq(0, low, "Default: none below 0");
        }
    }

    private static final BlockMatcher STONE = BlockMatcher.forBlock(Blocks.STONE);

    private static void place(World world, BlockPos pos, IBlockState state) {
        IBlockState here = world.getBlockState(pos);
        if (here.getBlock().isReplaceableOreGen(here, world, pos, STONE)) {
            world.setBlockState(pos, state, 2);
        }
    }

    /** The fixed placements, in chunk AX, AZ only. */
    static final class Fixed implements IWorldGenerator {
        @Override
        public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator gen, IChunkProvider provider) {
            if (!on || chunkX != AX || chunkZ != AZ || world.provider.getDimension() != 0) {
                return;
            }
            IBlockState stone = Blocks.STONE.getDefaultState(), air = Blocks.AIR.getDefaultState();
            // y 10: deepslate at its target, air where it is aimed.
            world.setBlockState(at(1, -44, 1), deepslate, 2);
            world.setBlockState(at(1, 10, 1), air, 2);
            place(world, at(1, 10, 1), ore);
            // y 40 -> 16.
            world.setBlockState(at(3, 16, 3), stone, 2);
            world.setBlockState(at(3, 40, 3), stone, 2);
            place(world, at(3, 40, 3), ore);
            // y 100 stays.
            world.setBlockState(at(5, 100, 5), stone, 2);
            place(world, at(5, 100, 5), ore);
            // A non-ore and a Nether ore at 20 stay.
            world.setBlockState(at(7, 20, 7), stone, 2);
            world.setBlockState(at(7, -24, 7), stone, 2);
            place(world, at(7, 20, 7), notOre);
            world.setBlockState(at(9, 20, 9), stone, 2);
            world.setBlockState(at(9, -24, 9), stone, 2);
            place(world, at(9, 20, 9), netherOre);
            // y 30 over an air target: nowhere.
            world.setBlockState(at(11, -4, 11), air, 2);
            world.setBlockState(at(11, 30, 11), stone, 2);
            place(world, at(11, 30, 11), ore);
            // Set at 50 without a check: tuff at 36 takes it.
            world.setBlockState(at(13, 36, 13), tuff, 2);
            world.setBlockState(at(13, 50, 13), ore, 2);
        }
    }

    /** Four one-block veins per chunk at random y 0-63, in the vein area only. */
    static final class Veins implements IWorldGenerator {
        @Override
        public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator gen, IChunkProvider provider) {
            if (!on || chunkX < BX || chunkX >= BX + N || chunkZ < BZ || chunkZ >= BZ + N || world.provider.getDimension() != 0) {
                return;
            }
            for (int i = 0; i < VEINS; i++) {
                BlockPos pos = new BlockPos(chunkX * 16 + 8 + random.nextInt(16), random.nextInt(64), chunkZ * 16 + 8 + random.nextInt(16));
                veinAttempts++;
                place(world, pos, veinOre);
            }
        }
    }
}
