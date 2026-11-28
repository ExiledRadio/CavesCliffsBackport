package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.backportcore.waterlog.Waterloggable;
import com.exiledradio.backportcore.waterlog.Waterlogging;
import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.blocks.BlocksContent;
import com.exiledradio.cavesandcliffsbackport.candle.BlockAbstractCandle;
import com.exiledradio.cavesandcliffsbackport.candle.CandleContent;
import com.exiledradio.cavesandcliffsbackport.common.Owners;
import com.exiledradio.cavesandcliffsbackport.dripstone.BlockPointedDripstone;
import com.exiledradio.cavesandcliffsbackport.dripstone.DripstoneContent;
import com.exiledradio.cavesandcliffsbackport.dripstone.Thickness;
import com.exiledradio.cavesandcliffsbackport.lush.BlockBigDripleaf;
import com.exiledradio.cavesandcliffsbackport.lush.BlockGlowLichen;
import com.exiledradio.cavesandcliffsbackport.lush.LushContent;
import com.exiledradio.cavesandcliffsbackport.lush.Soil;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.Random;

/**
 * Waterlogging, 1.18.2's rules through Backport Core's waterlog API: which blocks hold water, placing in water and
 * flowing water, and the candle, dripstone, lichen, dripleaf and light rules. With {@code -PnoFluidlogged} it checks
 * the dry path instead: nothing holds water and the same actions do what they did before waterlogging.
 */
public final class WaterlogSelfTest implements SelfTest {

    @Override
    public String name() {
        return "waterlog";
    }

    /** @return whether 1.18.2 waterlogs this block of ours (any state; a double slab never) */
    static boolean waterloggable(String path) {
        if (path.endsWith("_double_slab") || path.endsWith("_candle_cake") || path.equals("candle_cake")) {
            return false;
        }
        return path.equals("candle") || path.endsWith("_candle") || path.equals("pointed_dripstone")
                || path.startsWith("glow_lichen") || path.endsWith("lightning_rod") || path.equals("amethyst_cluster")
                || path.endsWith("_amethyst_bud") || path.equals("hanging_roots") || path.equals("small_dripleaf")
                || path.equals("big_dripleaf") || path.equals("big_dripleaf_stem") || path.equals("light")
                || path.endsWith("_stairs") || path.endsWith("_slab") || path.endsWith("_wall");
    }

    @Override
    public void run(Check c) throws Exception {
        WorldServer world = c.world;
        // Which classes say so: the same list whether or not Fluidlogged API is installed.
        int marked = 0;
        StringBuilder wrong = new StringBuilder();
        for (Block block : ForgeRegistries.BLOCKS) {
            ResourceLocation name = block.getRegistryName();
            if (name == null || !CavesAndCliffsBackport.MOD_ID.equals(name.getNamespace())) {
                continue;
            }
            boolean is = block instanceof Waterloggable;
            if (is) {
                marked++;
            }
            if (is != waterloggable(name.getPath()) && !name.getPath().endsWith("_double_slab")) {
                wrong.append(' ').append(name.getPath());
            }
        }
        c.ok(wrong.length() == 0, "Waterloggable exactly where 1.18.2 waterlogs; wrong:" + wrong);
        c.ok(marked > 40, "a fair number of our blocks are Waterloggable: " + marked);

        if (!Waterlogging.isAvailable()) {
            dry(c);
            return;
        }
        WaterlogFluidChecks.states(c, c.area().add(16, 1, 16));
        placing(c);
        candles(c);
        dripstone(c);
        lichen(c);
        dripleaf(c);
        light(c);
        flowing(c);
    }

    // --------------------------------------------------------------------------------------------- with water

    private void placing(Check c) {
        WorldServer world = c.world;
        BlockPos pool = pool(c);
        Block slab = Owners.ourBlock("cobbled_deepslate_slab");
        Block doubleSlab = Owners.ourBlock("cobbled_deepslate_double_slab");
        Block stairs = Owners.ourBlock("cut_copper_stairs");
        Block wall = Owners.ourBlock("deepslate_brick_wall");
        Block cluster = Owners.ourBlock("amethyst_cluster");
        if (!c.ok(slab != null && doubleSlab != null && stairs != null && wall != null && cluster != null, "the blocks this test uses exist")) {
            return;
        }
        IBlockState[] wet = {
                CandleContent.candles[0].getDefaultState(),
                DripstoneContent.pointed.state(EnumFacing.UP, Thickness.TIP),
                cluster.getDefaultState(),
                Owners.ourBlock("lightning_rod").getDefaultState(),
                BlocksContent.light.getDefaultState(),
                stairs.getDefaultState(),
                slab.getDefaultState().withProperty(BlockSlab.HALF, BlockSlab.EnumBlockHalf.TOP),
                wall.getDefaultState()};
        for (int i = 0; i < wet.length; i++) {
            BlockPos p = pool.add(1 + i, 1, 1);
            world.setBlockState(p, wet[i], 2);
            c.ok(world.getBlockState(p).getBlock() == wet[i].getBlock() && Waterlogging.hasWaterSource(world, p),
                    wet[i].getBlock().getRegistryName() + " placed into a water source keeps it");
        }
        world.setBlockState(pool.add(1, 1, 3), doubleSlab.getDefaultState(), 2);
        c.ok(!Waterlogging.hasWater(world, pool.add(1, 1, 3)), "a double slab placed into water replaces it");
        world.setBlockState(pool.add(2, 1, 3), CandleContent.cakes[0].getDefaultState(), 2);
        c.ok(!Waterlogging.hasWater(world, pool.add(2, 1, 3)), "a candle cake holds no water");
        // Hanging roots under a ceiling in the water.
        BlockPos roots = pool.add(5, 1, 5);
        world.setBlockState(roots.up(), Blocks.STONE.getDefaultState(), 2);
        world.setBlockState(roots, LushContent.hangingRoots.getDefaultState(), 2);
        c.ok(world.getBlockState(roots).getBlock() == LushContent.hangingRoots && Waterlogging.hasWaterSource(world, roots),
                "hanging roots in a water source keep it");
    }

    private void candles(Check c) {
        WorldServer world = c.world;
        BlockPos p = cup(world, c.area().add(4, 0, 4));
        IBlockState lit = CandleContent.candles[0].getDefaultState().withProperty(BlockAbstractCandle.LIT, true);
        world.setBlockState(p, lit, 3);
        c.ok(BlockAbstractCandle.isLit(world.getBlockState(p)), "a lit candle, dry");
        Waterlogging.setWater(world, p, true, 3);
        IBlockState after = world.getBlockState(p);
        c.ok(after.getBlock() == CandleContent.candles[0] && Waterlogging.hasWaterSource(world, p), "water comes into the candle");
        c.ok(!BlockAbstractCandle.isLit(after), "which puts it out");
        c.ok(!BlockAbstractCandle.canLight(world, p, after), "a waterlogged candle cannot be lit");
        Waterlogging.setWater(world, p, false, 3);
        c.ok(!Waterlogging.hasWater(world, p) && BlockAbstractCandle.canLight(world, p, world.getBlockState(p)), "drained, it can be lit again");
        // Placed into water: unlit (candles are always placed unlit).
        BlockPos q = pool(c).add(3, 1, 3);
        world.setBlockState(q, CandleContent.candles[3].getDefaultState(), 2);
        c.ok(Waterlogging.hasWaterSource(world, q) && !BlockAbstractCandle.isLit(world.getBlockState(q)), "a candle placed into water is unlit and waterlogged");
    }

    private void dripstone(Check c) {
        WorldServer world = c.world;
        BlockPointedDripstone pointed = DripstoneContent.pointed;
        BlockPos o = c.area().add(8, 0, 8);
        // A stone root at y+6, a stalactite tip under it.
        BlockPos root = o.up(6);
        world.setBlockState(root, Blocks.STONE.getDefaultState(), 2);
        BlockPos tip = root.down();
        world.setBlockState(tip, pointed.state(EnumFacing.DOWN, Thickness.TIP), 2);
        c.ok(BlockPointedDripstone.canDrip(world, tip, world.getBlockState(tip)), "a dry tip drips");
        c.eq(BlockPointedDripstone.Fluid.NONE, BlockPointedDripstone.fluidAbove(world, tip, world.getBlockState(tip)), "nothing above the root");
        // Water kept in a slab above the root fills cauldrons, as a water block there would.
        Block slab = Owners.ourBlock("cobbled_deepslate_slab");
        world.setBlockState(root.up(), slab.getDefaultState(), 2);
        Waterlogging.setWater(world, root.up(), true, 2);
        c.eq(BlockPointedDripstone.Fluid.WATER, BlockPointedDripstone.fluidAbove(world, tip, world.getBlockState(tip)),
                "water kept in a slab above the root is the drip's water");
        // A waterlogged tip does not drip.
        Waterlogging.setWater(world, tip, true, 2);
        c.ok(Waterlogging.hasWaterSource(world, tip), "the tip takes water");
        c.ok(!BlockPointedDripstone.canDrip(world, tip, world.getBlockState(tip)), "and a waterlogged tip does not drip");
    }

    private void lichen(Check c) {
        WorldServer world = c.world;
        BlockPos pool = pool(c);
        BlockPos p = pool.add(6, 1, 6);  // a water source on the stone floor
        c.ok(BlockGlowLichen.canAdd(world, p, EnumFacing.DOWN), "glow lichen can grow into a water source, on the floor below it");
        BlockGlowLichen.add(world, p, EnumFacing.DOWN, 2);
        c.ok(world.getBlockState(p).getBlock() instanceof BlockGlowLichen && Waterlogging.hasWaterSource(world, p), "and keeps the water");
        BlockPos flow = c.area().add(6, 0, 6);
        world.setBlockState(flow, Blocks.FLOWING_WATER.getDefaultState().withProperty(BlockLiquid.LEVEL, 3), 2);
        c.ok(!BlockGlowLichen.canAdd(world, flow, EnumFacing.DOWN), "but not into flowing water");
        world.setBlockState(flow, Blocks.AIR.getDefaultState(), 2);
    }

    private void dripleaf(Check c) {
        WorldServer world = c.world;
        BlockPos pool = pool(c);
        // Small dripleaf on dirt: only in a water source.
        BlockPos onDirt = pool.add(3, 1, 8);
        world.setBlockState(onDirt.down(), Blocks.DIRT.getDefaultState(), 2);
        c.ok(Soil.smallDripleaf(world, onDirt), "a small dripleaf may stand on dirt in a water source");
        BlockPos dry = c.area().add(3, 0, 3);
        world.setBlockState(dry.down(), Blocks.DIRT.getDefaultState(), 2);
        c.ok(!Soil.smallDripleaf(world, dry), "but not on dry dirt");
        LushContent.smallDripleaf.place(world, onDirt, EnumFacing.NORTH, 2);
        c.ok(world.getBlockState(onDirt).getBlock() == LushContent.smallDripleaf && Waterlogging.hasWaterSource(world, onDirt),
                "placed there, its lower half is waterlogged");
        Waterlogging.setWater(world, onDirt, false, 3);
        c.ok(world.getBlockState(onDirt).getBlock() != LushContent.smallDripleaf, "drained, it breaks");
        // Big dripleaf grows up through water, each part keeping it.
        BlockPos deep = c.area().add(10, 0, 10);
        world.setBlockState(deep.down(), Blocks.CLAY.getDefaultState(), 2);
        for (int y = 0; y < 6; y++) {
            world.setBlockState(deep.up(y), Blocks.WATER.getDefaultState(), 2);
        }
        int n = BlockBigDripleaf.placeTall(world, new Random(5), deep, EnumFacing.EAST, 2);
        boolean allWet = n >= 2;
        for (int i = 0; i < n; i++) {
            allWet &= Waterlogging.hasWaterSource(world, deep.up(i));
        }
        c.ok(allWet, "a big dripleaf of " + n + " grown through water keeps the water in every part");
    }

    private void light(Check c) {
        WorldServer world = c.world;
        BlockPos p = pool(c).add(9, 1, 9);
        EntityPlayerMP player = Players.real(world, "waterloglight");
        ItemStack stack = new ItemStack(BlocksContent.lightItem);
        boolean placed = BlocksContent.lightItem.placeBlockAt(stack, player, world, p, EnumFacing.UP, 0.5F, 0.5F, 0.5F,
                BlocksContent.light.getDefaultState());
        c.ok(placed && world.getBlockState(p).getBlock() == BlocksContent.light, "a light placed from its item into water");
        c.ok(!Waterlogging.hasWater(world, p), "takes the water's place (1.18.2: only a bucket waterlogs a light)");
    }

    /** Flowing water neither enters nor washes away a dry block of ours that could hold water. */
    private void flowing(Check c) {
        WorldServer world = c.world;
        BlockPos spring = c.area().add(12, 0, 12);
        world.setBlockState(spring.east(), CandleContent.candles[0].getDefaultState(), 3);
        world.setBlockState(spring.west(), DripstoneContent.pointed.state(EnumFacing.UP, Thickness.TIP), 3);
        world.setBlockState(spring.north(), BlockGlowLichen.of(1), 3);  // on the floor
        world.setBlockState(spring, Blocks.FLOWING_WATER.getDefaultState(), 3);
        settle(world);
        c.ok(Waterlogging.hasWater(world, spring.south()), "the spring's water spreads");
        c.ok(world.getBlockState(spring.east()).getBlock() == CandleContent.candles[0] && !Waterlogging.hasWater(world, spring.east()),
                "a dry candle beside it stays, dry");
        c.ok(world.getBlockState(spring.west()).getBlock() == DripstoneContent.pointed && !Waterlogging.hasWater(world, spring.west()),
                "so does a dry stalagmite");
        c.ok(world.getBlockState(spring.north()).getBlock() instanceof BlockGlowLichen && !Waterlogging.hasWater(world, spring.north()),
                "and dry glow lichen");
    }

    // --------------------------------------------------------------------------------------------- without

    private void dry(Check c) {
        WorldServer world = c.world;
        c.ok(true, "Fluidlogged API is not installed: the dry path");
        BlockPos pool = pool(c);
        BlockPos p = pool.add(2, 1, 2);
        world.setBlockState(p, CandleContent.candles[0].getDefaultState(), 2);
        c.ok(world.getBlockState(p).getBlock() == CandleContent.candles[0] && !Waterlogging.hasWater(world, p), "a candle placed into water replaces it");
        c.ok(BlockAbstractCandle.canLight(world, p, world.getBlockState(p)), "and can be lit");
        BlockPos q = pool.add(4, 1, 4);
        world.setBlockState(q.down(), Blocks.DIRT.getDefaultState(), 2);
        c.ok(!Soil.smallDripleaf(world, q), "no small dripleaf on dirt in water (it could not keep the water)");
        c.ok(Waterlogging.setWater(world, p, true, 3) == false && !Waterlogging.hasWater(world, p), "setWater adds nothing");
        BlockPos tip = c.area().add(6, 3, 6);
        world.setBlockState(tip.up(), Blocks.STONE.getDefaultState(), 2);
        world.setBlockState(tip, DripstoneContent.pointed.state(EnumFacing.DOWN, Thickness.TIP), 2);
        c.ok(BlockPointedDripstone.canDrip(world, tip, world.getBlockState(tip)), "a tip drips");
    }

    // --------------------------------------------------------------------------------------------- helpers

    /** A stone cup: stone below and on the four sides of pos, air above. @return pos */
    private static BlockPos cup(WorldServer world, BlockPos pos) {
        world.setBlockState(pos.down(), Blocks.STONE.getDefaultState(), 2);
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            world.setBlockState(pos.offset(side), Blocks.STONE.getDefaultState(), 2);
        }
        return pos;
    }

    /** A 12x12 pool of still water one deep, walled with stone, on a fresh area's floor. @return its floor corner */
    private static BlockPos pool(Check c) {
        BlockPos corner = c.area();
        for (int x = 0; x < 14; x++) {
            for (int z = 0; z < 14; z++) {
                boolean edge = x == 0 || z == 0 || x == 13 || z == 13;
                c.world.setBlockState(corner.add(x, 0, z), (edge ? Blocks.STONE : Blocks.WATER).getDefaultState(), 2 | 16);
            }
        }
        return corner.add(0, -1, 0);
    }

    /** Runs every pending block tick now, a few rounds, so water finishes flowing. */
    private static void settle(WorldServer world) {
        for (int i = 0; i < 40; i++) {
            world.tickUpdates(true);
        }
    }
}
