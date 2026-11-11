package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.candle.BlockAbstractCandle;
import com.exiledradio.cavesandcliffsbackport.candle.BlockCandle;
import com.exiledradio.cavesandcliffsbackport.candle.BlockCandleCake;
import com.exiledradio.cavesandcliffsbackport.candle.CandleContent;
import com.exiledradio.cavesandcliffsbackport.candle.ItemCandle;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCake;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.PotionTypes;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.oredict.OreDictionary;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

/** Stream Ca: candles (stacking, lighting, putting out, drops, support), candle cakes, recipes. */
public final class CandleSelfTest implements SelfTest {

    @Override
    public String name() {
        return "candle";
    }

    @Override
    public void run(Check c) {
        registration(c);
        stacking(c);
        lighting(c);
        projectiles(c);
        support(c);
        cakes(c);
        recipes(c);
    }

    private static IBlockState candle(int index, int count, boolean lit) {
        return CandleContent.candles[index].getDefaultState().withProperty(BlockCandle.CANDLES, count)
                .withProperty(BlockAbstractCandle.LIT, lit);
    }

    private static EntityPlayerMP player(Check c, String name, BlockPos near) {
        EntityPlayerMP p = Players.real(c.world, name);
        p.interactionManager.setGameType(GameType.SURVIVAL);
        p.setPosition(near.getX() + 8.5, near.getY(), near.getZ() + 8.5);
        return p;
    }

    /** @return whether the click was taken by this stream (cancelled) */
    private static boolean click(EntityPlayerMP p, BlockPos pos) {
        PlayerInteractEvent.RightClickBlock e = new PlayerInteractEvent.RightClickBlock(p, EnumHand.MAIN_HAND, pos, EnumFacing.UP, new Vec3d(pos));
        MinecraftForge.EVENT_BUS.post(e);
        return e.isCanceled();
    }

    private static EnumActionResult use(EntityPlayerMP p, BlockPos pos, EnumFacing face) {
        ItemStack held = p.getHeldItem(EnumHand.MAIN_HAND);
        return held.getItem().onItemUse(p, p.world, pos, EnumHand.MAIN_HAND, face, 0.5F, 1.0F, 0.5F);
    }

    private void registration(Check c) {
        Set<String> registered = new TreeSet<String>();
        for (int i = 0; i < 17; i++) {
            BlockCandle b = CandleContent.candles[i];
            BlockCandleCake cake = CandleContent.cakes[i];
            String n = CandleContent.name(i);
            registered.add(b.getRegistryName().getPath());
            registered.add(cake.getRegistryName().getPath());
            c.eq(n, b.getRegistryName().getPath(), "candle name");
            c.eq(n + "_cake", cake.getRegistryName().getPath(), "cake name");
            c.ok(Item.getItemFromBlock(b) instanceof ItemCandle, n + " has its item");
            c.ok(Item.getItemFromBlock(cake) == Items.AIR, n + "_cake has no item");
            c.ok(cake.candle() == b && CandleContent.cakeFor(b) == cake, n + " and its cake know each other");
            c.eq(8, b.getBlockState().getValidStates().size(), n + " states");
            for (IBlockState s : b.getBlockState().getValidStates()) {
                c.ok(b.getStateFromMeta(b.getMetaFromState(s)) == s, n + " meta " + s);
                int want = s.getValue(BlockAbstractCandle.LIT) ? 3 * s.getValue(BlockCandle.CANDLES) : 0;
                c.eq(want, s.getLightValue(c.world, BlockPos.ORIGIN), n + " light " + s);
            }
            for (IBlockState s : cake.getBlockState().getValidStates()) {
                c.ok(cake.getStateFromMeta(cake.getMetaFromState(s)) == s, n + "_cake meta " + s);
                c.eq(s.getValue(BlockAbstractCandle.LIT) ? 3 : 0, s.getLightValue(c.world, BlockPos.ORIGIN), n + "_cake light " + s);
            }
            c.eq(EnumPushReaction.DESTROY, b.getDefaultState().getPushReaction(), n + " breaks when pushed");
            c.eq(EnumPushReaction.DESTROY, cake.getDefaultState().getPushReaction(), n + "_cake breaks when pushed");
            c.ok(!b.getDefaultState().isOpaqueCube() && !b.getDefaultState().isFullCube(), n + " is not a cube");
        }
        c.eq(registered, new TreeSet<String>(Arrays.asList(CandleContent.BLOCK_NAMES)), "pre-init name list matches registration");
        c.eq(34, registered.size(), "34 blocks");
        c.eq(0.1F, CandleContent.candles[0].getDefaultState().getBlockHardness(c.world, BlockPos.ORIGIN), "candle hardness");
        c.eq(0.5F, CandleContent.cakes[0].getDefaultState().getBlockHardness(c.world, BlockPos.ORIGIN), "candle cake hardness");
        c.eq(64, new ItemStack(CandleContent.candles[0]).getMaxStackSize(), "candles stack to 64");
    }

    private void stacking(Check c) {
        BlockPos floor = c.area().down();
        BlockPos o = floor.up();
        EntityPlayerMP p = player(c, "stack", o);
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(CandleContent.candles[3], 10));
        c.eq(EnumActionResult.SUCCESS, use(p, floor, EnumFacing.UP), "a candle is placed on stone");
        c.eq(candle(3, 1, false), c.world.getBlockState(o), "one candle");
        for (int n = 2; n <= 4; n++) {
            use(p, o, EnumFacing.UP);
            c.eq(candle(3, n, false), c.world.getBlockState(o), n + " candles");
        }
        c.eq(6, p.getHeldItem(EnumHand.MAIN_HAND).getCount(), "four candles used");
        use(p, o, EnumFacing.UP);
        c.eq(candle(3, 4, false), c.world.getBlockState(o), "a fifth does not fit");
        c.ok(c.world.isAirBlock(o.up()), "and is not placed on top of the others");
        c.eq(6, p.getHeldItem(EnumHand.MAIN_HAND).getCount(), "and is not used");

        // Another color never joins; a lit candle stays lit when one is added; sneaking adds nothing.
        c.world.setBlockState(o, candle(3, 1, true), 3);
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(CandleContent.candles[4], 5));
        use(p, o, EnumFacing.UP);
        c.eq(candle(3, 1, true), c.world.getBlockState(o), "another color does not join");
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(CandleContent.candles[3], 5));
        p.setSneaking(true);
        use(p, o, EnumFacing.UP);
        c.eq(candle(3, 1, true), c.world.getBlockState(o), "sneaking adds nothing");
        p.setSneaking(false);
        use(p, o, EnumFacing.UP);
        c.eq(candle(3, 2, true), c.world.getBlockState(o), "a lit candle stays lit when one is added");

        // Clicking the block next to a candle adds to it too (the space the click would place into).
        BlockPos wall = o.east();
        c.world.setBlockState(wall, Blocks.STONE.getDefaultState(), 3);
        use(p, wall, EnumFacing.WEST);
        c.eq(candle(3, 3, true), c.world.getBlockState(o), "clicking the block beside it adds a candle");

        // Mining gives every candle back.
        p.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        p.interactionManager.tryHarvestBlock(o);
        c.eq(3, Drops.count(c.world, o, Item.getItemFromBlock(CandleContent.candles[3])), "three candles drop three");
    }

    private void lighting(Check c) {
        BlockPos o = c.area();
        EntityPlayerMP p = player(c, "light", o);
        c.world.setBlockState(o, candle(0, 4, false), 3);
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.STICK));
        c.ok(!click(p, o), "a stick lights nothing");
        ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
        p.setHeldItem(EnumHand.MAIN_HAND, flint);
        c.ok(click(p, o), "flint and steel is taken");
        c.eq(candle(0, 4, true), c.world.getBlockState(o), "flint and steel lights");
        c.eq(1, flint.getItemDamage(), "one durability");
        c.eq(12, c.world.getLightFor(EnumSkyBlock.BLOCK, o), "four lit candles give light 12");
        c.ok(c.world.isAirBlock(o.up()) && c.world.isAirBlock(o.east()), "no fire started");
        c.ok(!click(p, o), "a lit candle leaves flint and steel to vanilla");

        IBlockState lit = c.world.getBlockState(o);
        c.ok(!lit.getBlock().onBlockActivated(c.world, o, lit, p, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 0.3F, 0.5F),
                "a hand holding something does not put it out");
        p.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        c.ok(lit.getBlock().onBlockActivated(c.world, o, lit, p, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 0.3F, 0.5F),
                "an empty hand is taken");
        c.eq(candle(0, 4, false), c.world.getBlockState(o), "an empty hand puts it out");
        c.eq(0, c.world.getLightFor(EnumSkyBlock.BLOCK, o), "and the light goes");
        IBlockState unlit = c.world.getBlockState(o);
        c.ok(!unlit.getBlock().onBlockActivated(c.world, o, unlit, p, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 0.3F, 0.5F),
                "an unlit candle ignores the hand");

        c.world.setBlockState(o, candle(7, 2, false), 3);
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.FIRE_CHARGE, 3));
        c.ok(click(p, o), "a fire charge is taken");
        c.eq(candle(7, 2, true), c.world.getBlockState(o), "a fire charge lights");
        c.eq(2, p.getHeldItem(EnumHand.MAIN_HAND).getCount(), "one charge used");
        c.eq(6, c.world.getLightFor(EnumSkyBlock.BLOCK, o), "two lit candles give light 6");
    }

    private void projectiles(Check c) {
        BlockPos o = c.area();
        c.world.setBlockState(o, candle(0, 1, false), 3);
        EntityTippedArrow arrow = new EntityTippedArrow(c.world, o.getX() + 0.5, o.getY() + 3, o.getZ() + 0.5);
        RayTraceResult hit = new RayTraceResult(new Vec3d(o), EnumFacing.UP, o);
        MinecraftForge.EVENT_BUS.post(new ProjectileImpactEvent.Arrow(arrow, hit));
        c.eq(candle(0, 1, false), c.world.getBlockState(o), "a plain arrow lights nothing");
        arrow.setFire(100);
        MinecraftForge.EVENT_BUS.post(new ProjectileImpactEvent.Arrow(arrow, hit));
        c.eq(candle(0, 1, true), c.world.getBlockState(o), "a burning arrow lights the candle it hits");

        // A splash water bottle landing on the floor beside two lit candles and a lit candle cake.
        BlockPos west = o, front = o.east(), east = front.east(), far = east.east().east();
        c.world.setBlockState(east, CandleContent.cakes[2].getDefaultState().withProperty(BlockAbstractCandle.LIT, true), 3);
        c.world.setBlockState(far, candle(1, 1, true), 3);
        RayTraceResult splash = new RayTraceResult(new Vec3d(front), EnumFacing.UP, front.down());
        EntityPotion poison = new EntityPotion(c.world, front.getX(), front.getY() + 2, front.getZ(),
                PotionUtils.addPotionToItemStack(new ItemStack(Items.SPLASH_POTION), PotionTypes.POISON));
        MinecraftForge.EVENT_BUS.post(new ProjectileImpactEvent.Throwable(poison, splash));
        c.eq(candle(0, 1, true), c.world.getBlockState(west), "another potion puts nothing out");
        EntityPotion water = new EntityPotion(c.world, front.getX(), front.getY() + 2, front.getZ(),
                PotionUtils.addPotionToItemStack(new ItemStack(Items.SPLASH_POTION), PotionTypes.WATER));
        MinecraftForge.EVENT_BUS.post(new ProjectileImpactEvent.Throwable(water, splash));
        c.eq(candle(0, 1, false), c.world.getBlockState(west), "a splash water bottle puts the candle out");
        c.eq(CandleContent.cakes[2].getDefaultState(), c.world.getBlockState(east), "and the candle cake");
        c.eq(candle(1, 1, true), c.world.getBlockState(far), "but not one three blocks away");
    }

    private void support(Check c) {
        BlockPos floor = c.area().down();
        BlockPos o = floor.up();
        EntityPlayerMP p = player(c, "support", o);
        Block candle = CandleContent.candles[5];
        c.ok(candle.canPlaceBlockAt(c.world, o), "stone holds a candle");
        c.ok(!candle.canPlaceBlockAt(c.world, o.up(3)), "air does not");
        Object[][] below = {
                {Blocks.OAK_FENCE.getDefaultState(), true, "a fence"},
                {Blocks.COBBLESTONE_WALL.getDefaultState(), true, "a wall"},
                {Blocks.GLASS.getDefaultState(), true, "glass"},
                {Blocks.STONE_SLAB.getDefaultState(), false, "a bottom slab"},
                {Blocks.HOPPER.getDefaultState(), false, "a hopper"},
                {candle.getDefaultState(), false, "another candle"},
        };
        BlockPos post = o.east(4);
        for (Object[] b : below) {
            c.world.setBlockState(post, (IBlockState) b[0], 2 | 16);
            c.eq(b[1], candle.canPlaceBlockAt(c.world, post.up()), b[2] + " under a candle");
        }
        c.world.setBlockState(post, Blocks.AIR.getDefaultState(), 2 | 16);

        // Placing in mid-air by hand fails; taking the floor away drops every candle.
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(candle, 5));
        BlockPos wall = o.up(4);
        c.world.setBlockState(wall, Blocks.STONE.getDefaultState(), 2 | 16);
        use(p, wall, EnumFacing.EAST);
        c.ok(c.world.isAirBlock(wall.east()), "a candle is not placed against a wall in mid-air");
        c.world.setBlockState(wall, Blocks.AIR.getDefaultState(), 2 | 16);

        c.world.setBlockState(o, candle(5, 3, true), 3);
        c.world.setBlockState(floor, Blocks.AIR.getDefaultState(), 3);
        c.ok(c.world.isAirBlock(o), "a candle with nothing under it breaks");
        c.eq(3, Drops.count(c.world, o, Item.getItemFromBlock(candle)), "and drops its candles");
        c.world.setBlockState(floor, Blocks.STONE.getDefaultState(), 3);
    }

    private void cakes(Check c) {
        BlockPos floor = c.area().down();
        BlockPos o = floor.up();
        EntityPlayerMP p = player(c, "cake", o);
        BlockCandleCake red = CandleContent.cakes[15];
        Item redCandle = Item.getItemFromBlock(CandleContent.candles[15]);

        c.world.setBlockState(o, Blocks.CAKE.getDefaultState().withProperty(BlockCake.BITES, 1), 3);
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(redCandle, 3));
        c.ok(!click(p, o), "a bitten cake takes no candle");
        c.world.setBlockState(o, Blocks.CAKE.getDefaultState(), 3);
        p.setSneaking(true);
        c.ok(!click(p, o), "sneaking puts no candle on a cake");
        p.setSneaking(false);
        c.ok(click(p, o), "a candle on a whole cake is taken");
        c.eq(red.getDefaultState(), c.world.getBlockState(o), "the cake gets the red candle");
        c.eq(2, p.getHeldItem(EnumHand.MAIN_HAND).getCount(), "one candle used");
        c.eq(14, red.getComparatorInputOverride(red.getDefaultState(), c.world, o), "a whole cake's comparator signal");
        c.eq(Items.CAKE, red.getItem(c.world, o, red.getDefaultState()).getItem(), "pick block gives a cake");

        // Light it, put it out by the candle, then eat.
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
        c.ok(click(p, o), "flint and steel on a candle cake is taken");
        IBlockState lit = red.getDefaultState().withProperty(BlockAbstractCandle.LIT, true);
        c.eq(lit, c.world.getBlockState(o), "the candle cake is lit");
        c.eq(3, c.world.getLightFor(EnumSkyBlock.BLOCK, o), "light 3");
        p.getFoodStats().setFoodLevel(20);
        c.ok(!red.onBlockActivated(c.world, o, lit, p, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 0.9F, 0.5F),
                "flint and steel in hand neither eats nor puts out");
        p.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        c.ok(!red.onBlockActivated(c.world, o, lit, p, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 0.3F, 0.5F),
                "a full player clicking the cake does nothing");
        c.eq(lit, c.world.getBlockState(o), "and it stays lit");
        c.ok(red.onBlockActivated(c.world, o, lit, p, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 0.9F, 0.5F),
                "an empty hand on the candle is taken");
        c.eq(red.getDefaultState(), c.world.getBlockState(o), "and puts it out");

        p.getFoodStats().setFoodLevel(10);
        c.ok(red.onBlockActivated(c.world, o, red.getDefaultState(), p, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 0.3F, 0.5F),
                "a hungry player eats");
        c.eq(Blocks.CAKE.getDefaultState().withProperty(BlockCake.BITES, 1), c.world.getBlockState(o), "leaving a cake with one slice gone");
        c.eq(12, p.getFoodStats().getFoodLevel(), "two hunger points");
        c.eq(1, Drops.count(c.world, o, redCandle), "and the candle drops");

        // Breaking gives the candle only; so does losing the block below.
        c.world.setBlockState(o, lit, 3);
        p.interactionManager.tryHarvestBlock(o);
        c.ok(c.world.isAirBlock(o), "a candle cake breaks");
        c.eq(1, Drops.count(c.world, o, redCandle), "and drops its candle");
        c.world.setBlockState(o, red.getDefaultState(), 3);
        c.world.setBlockState(floor, Blocks.AIR.getDefaultState(), 3);
        c.ok(c.world.isAirBlock(o), "a candle cake with nothing under it breaks");
        c.eq(1, Drops.count(c.world, o, redCandle), "and drops its candle too");
        c.eq(0, Drops.count(c.world, o, Items.CAKE), "never a cake");
        c.world.setBlockState(floor, Blocks.STONE.getDefaultState(), 3);
    }

    private void recipes(Check c) {
        // No honeycomb mod in the dev world: give paper the ore name honeycomb for the test.
        OreDictionary.registerOre("honeycomb", Items.PAPER);
        ItemStack plain = new ItemStack(CandleContent.candles[0]);
        Crafting.expect(c, Crafting.grid(1, 2, new ItemStack(Items.STRING), new ItemStack(Items.PAPER)), plain, "string over honeycomb");
        c.ok(CraftingManager.findMatchingResult(Crafting.grid(1, 2, new ItemStack(Items.PAPER), new ItemStack(Items.STRING)), c.world).isEmpty(),
                "honeycomb over string makes nothing");
        c.ok(CraftingManager.findMatchingResult(Crafting.grid(1, 2, new ItemStack(Items.STRING), new ItemStack(Items.STICK)), c.world).isEmpty(),
                "string over a stick makes nothing");
        for (int i = 0; i < 16; i++) {
            ItemStack dye = new ItemStack(Items.DYE, 1, EnumDyeColor.byMetadata(i).getDyeDamage());
            Crafting.expect(c, Crafting.grid(2, 1, plain, dye), new ItemStack(CandleContent.candles[i + 1]),
                    CandleContent.COLORS[i] + " candle from dye");
        }
        ItemStack red = new ItemStack(CandleContent.candles[15]);
        c.ok(CraftingManager.findMatchingResult(Crafting.grid(2, 1, red, new ItemStack(Items.DYE, 1, EnumDyeColor.BLUE.getDyeDamage())), c.world).isEmpty(),
                "a dyed candle is not dyed again");
    }
}
