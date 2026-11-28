package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.lush.BlockBigDripleaf;
import com.exiledradio.cavesandcliffsbackport.lush.BlockCaveVines;
import com.exiledradio.cavesandcliffsbackport.lush.BlockGlowLichen;
import com.exiledradio.cavesandcliffsbackport.lush.BlockSmallDripleaf;
import com.exiledradio.cavesandcliffsbackport.lush.LushCaveGenerator;
import com.exiledradio.cavesandcliffsbackport.lush.LushContent;
import com.exiledradio.cavesandcliffsbackport.lush.LushRegions;

import net.minecraft.block.Block;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameType;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.ProjectileImpactEvent;

import java.util.Arrays;
import java.util.Random;
import java.util.TreeSet;

/** Stream L: every lush block's rules, the item uses, the region noise and the cave decoration. */
public final class LushSelfTest implements SelfTest {

    private static final IBlockState AIR = Blocks.AIR.getDefaultState();
    private static final IBlockState STONE = Blocks.STONE.getDefaultState();

    @Override
    public String name() {
        return "lush";
    }

    @Override
    public void run(Check c) {
        registration(c);
        moss(c);
        azaleas(c);
        pots(c);
        roots(c);
        vines(c);
        bigDripleaf(c);
        smallDripleaf(c);
        lichen(c);
        recipes(c);
        regions(c);
        caves(c);
        loot(c);
    }

    /** 1.17's chest loot: glow berries in mineshaft carts, moss in shipwreck supply chests (Update Aquatic's table). */
    private void loot(Check c) {
        net.minecraft.world.storage.loot.LootTable mine = c.world.getLootTableManager()
                .getLootTableFromLocation(net.minecraft.world.storage.loot.LootTableList.CHESTS_ABANDONED_MINESHAFT);
        net.minecraft.world.storage.loot.LootPool pool = mine.getPool("pool1");
        c.ok(pool != null && pool.getEntry(com.exiledradio.cavesandcliffsbackport.lush.LushLoot.GLOW_BERRIES_ENTRY) != null,
                "glow berries are in the mineshaft chest's 2-4 roll pool");
        c.ok(mine.getPool("main").getEntry(com.exiledradio.cavesandcliffsbackport.lush.LushLoot.GLOW_BERRIES_ENTRY) == null,
                "and not in its other pools");
        Random rand = new Random(17);
        int chests = 0, withBerries = 0, berries = 0;
        for (int i = 0; i < 400; i++) {
            net.minecraft.world.storage.loot.LootContext ctx = new net.minecraft.world.storage.loot.LootContext.Builder(c.world).build();
            chests++;
            boolean any = false;
            for (ItemStack s : mine.generateLootForPools(rand, ctx)) {
                if (s.getItem() == LushContent.glowBerries) {
                    c.ok(s.getCount() >= 3 && s.getCount() <= 6, "3-6 glow berries a stack: " + s.getCount());
                    berries += s.getCount();
                    any = true;
                }
            }
            withBerries += any ? 1 : 0;
        }
        // Weight 15 of 98 in a pool rolled 2-4 times: about 39% of chests have some.
        log("mineshaft chests with glow berries: {} of {}, {} berries", withBerries, chests, berries);
        c.ok(withBerries > chests * 0.25 && withBerries < chests * 0.5, "about 39% of mineshaft chests have glow berries: " + withBerries);
        // Update Aquatic's shipwreck supply table, as that mod ships it: its moss placeholder becomes moss.
        net.minecraft.world.storage.loot.LootPool ship = new net.minecraft.world.storage.loot.LootPool(
                new net.minecraft.world.storage.loot.LootEntry[] {
                        new net.minecraft.world.storage.loot.LootEntryEmpty(7, 0, new net.minecraft.world.storage.loot.conditions.LootCondition[0], "minecraft:moss_block"),
                        new net.minecraft.world.storage.loot.LootEntryItem(Items.PAPER, 8, 0, new net.minecraft.world.storage.loot.functions.LootFunction[0],
                                new net.minecraft.world.storage.loot.conditions.LootCondition[0], "minecraft:paper")},
                new net.minecraft.world.storage.loot.conditions.LootCondition[0], new net.minecraft.world.storage.loot.RandomValueRange(3, 10),
                new net.minecraft.world.storage.loot.RandomValueRange(0), "pool0");
        net.minecraft.world.storage.loot.LootTable supply = new net.minecraft.world.storage.loot.LootTable(
                new net.minecraft.world.storage.loot.LootPool[] {ship});
        com.exiledradio.cavesandcliffsbackport.lush.LushLoot.lootLoad(new net.minecraftforge.event.LootTableLoadEvent(
                com.exiledradio.cavesandcliffsbackport.lush.LushLoot.SHIPWRECK_SUPPLY, supply, c.world.getLootTableManager()));
        c.ok(ship.getEntry("minecraft:moss_block") == null, "the empty moss placeholder is gone");
        c.ok(ship.getEntry(com.exiledradio.cavesandcliffsbackport.lush.LushLoot.MOSS_ENTRY) != null, "moss blocks are in the shipwreck supply chest");
        boolean moss = false;
        for (int i = 0; i < 50 && !moss; i++) {
            for (ItemStack s : supply.generateLootForPools(rand, new net.minecraft.world.storage.loot.LootContext.Builder(c.world).build())) {
                if (s.getItem() == Item.getItemFromBlock(LushContent.moss)) {
                    c.ok(s.getCount() >= 1 && s.getCount() <= 4, "1-4 moss blocks a stack: " + s.getCount());
                    moss = true;
                }
            }
        }
        c.ok(moss, "a shipwreck supply chest gives moss blocks");
    }

    private static void log(String what, Object... args) {
        CavesAndCliffsBackport.LOGGER.info("SELFTEST lush " + what, args);
    }

    private static EntityPlayerMP player(Check c, BlockPos at, ItemStack held) {
        EntityPlayerMP p = Players.real(c.world, "lush");
        p.setPositionAndRotation(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0F, 0F);
        p.interactionManager.setGameType(GameType.SURVIVAL);
        p.setHeldItem(EnumHand.MAIN_HAND, held);
        return p;
    }

    private static EnumActionResult use(Check c, EntityPlayerMP p, BlockPos pos, EnumFacing side) {
        return p.interactionManager.processRightClickBlock(p, c.world, p.getHeldItemMainhand(), EnumHand.MAIN_HAND, pos, side, 0.5F, 0.5F, 0.5F);
    }

    private static int harvest(Check c, BlockPos pos, ItemStack tool, Item want) {
        EntityPlayerMP p = player(c, pos.up(2), tool.copy());
        p.interactionManager.tryHarvestBlock(pos);
        return Drops.count(c.world, pos, want);
    }

    private static int count(Check c, BlockPos from, BlockPos to, Block block) {
        int n = 0;
        for (BlockPos p : BlockPos.getAllInBox(from, to)) {
            if (c.world.getBlockState(p).getBlock() == block) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------------------------------------

    private void registration(Check c) {
        c.eq(20, LushContent.all().size(), "block count");
        TreeSet<String> registered = new TreeSet<String>();
        for (Block b : LushContent.all()) {
            registered.add(b.getRegistryName().getPath());
            for (IBlockState s : b.getBlockState().getValidStates()) {
                c.ok(b.getStateFromMeta(b.getMetaFromState(s)) == s, b.getRegistryName() + " meta " + s);
            }
        }
        c.eq(registered, new TreeSet<String>(Arrays.asList(LushContent.BLOCK_NAMES)), "pre-init name list matches registration");
        for (int mask = 1; mask < 64; mask++) {
            c.eq(mask, BlockGlowLichen.faces(BlockGlowLichen.of(mask)), "glow lichen face mask " + mask);
        }
        c.eq(AIR, BlockGlowLichen.of(0), "no faces is air");
        c.eq(7, BlockGlowLichen.of(4).getLightValue(c.world, BlockPos.ORIGIN), "glow lichen light");
        c.eq(14, LushContent.caveVines.getDefaultState().withProperty(BlockCaveVines.BERRIES, true).getLightValue(c.world, BlockPos.ORIGIN), "berries light");
        c.eq(0, LushContent.caveVinesPlant.getDefaultState().getLightValue(c.world, BlockPos.ORIGIN), "no berries, no light");
        for (Block b : new Block[] {LushContent.moss, LushContent.mossCarpet, LushContent.azalea, LushContent.sporeBlossom,
                LushContent.bigDripleaf, LushContent.smallDripleaf, LushContent.caveVines, LushContent.hangingRoots}) {
            c.eq(EnumPushReaction.DESTROY, b.getPushReaction(b.getDefaultState()), b.getRegistryName() + " breaks when pushed");
        }
        c.ok(Item.getItemFromBlock(LushContent.caveVines) == Items.AIR && Item.getItemFromBlock(LushContent.bigDripleafStem) == Items.AIR
                && Item.getItemFromBlock(LushContent.glowLichen[2]) == Items.AIR, "vines, stems and the lichen variants have no item");
        c.eq(100, ForgeEventFactory.getItemBurnTime(new ItemStack(LushContent.azalea)), "azalea burn time");
        c.eq(60, Blocks.FIRE.getFlammability(LushContent.leaves), "azalea leaves burn");
        c.eq(0, Blocks.FIRE.getFlammability(LushContent.moss), "moss does not burn");
        c.ok(LushContent.leaves.isLeaves(LushContent.leaves.getDefaultState(), c.world, BlockPos.ORIGIN), "azalea leaves are leaves");
    }

    private void moss(Check c) {
        BlockPos o = c.area().add(10, 0, 10);
        BlockPos ground = o.down();
        c.world.setBlockState(ground, LushContent.moss.getDefaultState(), 2);
        c.ok(LushContent.moss.canGrow(c.world, ground, c.world.getBlockState(ground), false), "moss takes bone meal under air");
        Random r = new Random(3);
        for (int i = 0; i < 6; i++) {
            LushContent.moss.grow(c.world, r, ground, c.world.getBlockState(ground));
        }
        int moss = count(c, ground.add(-3, 0, -3), ground.add(3, 0, 3), LushContent.moss);
        int carpet = count(c, o.add(-3, 0, -3), o.add(3, 0, 3), LushContent.mossCarpet);
        int grass = count(c, o.add(-3, 0, -3), o.add(3, 0, 3), Blocks.TALLGRASS);
        int azalea = count(c, o.add(-3, 0, -3), o.add(3, 0, 3), LushContent.azalea) + count(c, o.add(-3, 0, -3), o.add(3, 0, 3), LushContent.floweringAzalea);
        log("bone meal: {} moss, {} carpet, {} grass, {} azaleas", moss, carpet, grass, azalea);
        c.ok(moss >= 20 && moss <= 45, "bone meal spreads moss over the stone around: " + moss);
        c.ok(carpet + grass + azalea > 5, "with plants on it");
        c.eq(0, count(c, ground.add(-4, 0, -4), ground.add(4, 0, 4), LushContent.moss) - moss, "never past 3 blocks");
        c.eq(STONE, c.world.getBlockState(ground.add(3, 0, 3)), "never the corner");
        // Grass stays on moss when a neighbor changes.
        BlockPos g = null;
        for (BlockPos p : BlockPos.getAllInBox(o.add(-3, 0, -3), o.add(3, 0, 3))) {
            if (c.world.getBlockState(p).getBlock() == Blocks.TALLGRASS) {
                g = p;
                break;
            }
        }
        if (g != null) {
            c.world.getBlockState(g).neighborChanged(c.world, g, Blocks.STONE, g.up());
            c.eq(Blocks.TALLGRASS, c.world.getBlockState(g).getBlock(), "grass stays on moss");
        }
        c.fill(o.add(-4, 0, -4), o.add(4, 2, 4), AIR);
        // Carpet.
        BlockPos p = o.add(8, 0, 0);
        c.world.setBlockState(p.down(), STONE, 2);
        c.ok(LushContent.mossCarpet.canPlaceBlockAt(c.world, p) && !LushContent.mossCarpet.canPlaceBlockAt(c.world, p.up(3)), "carpet needs a block under it");
        c.world.setBlockState(p, LushContent.mossCarpet.getDefaultState(), 2);
        c.world.setBlockState(p.down(), AIR, 3);
        c.eq(Blocks.AIR, c.world.getBlockState(p).getBlock(), "carpet breaks without one");
        c.eq(1, Drops.count(c.world, p, Item.getItemFromBlock(LushContent.mossCarpet)), "and drops itself");
        c.world.setBlockState(p.down(), STONE, 2);
    }

    private void azaleas(Check c) {
        BlockPos o = c.area().add(12, 0, 12);
        c.ok(!LushContent.azalea.canPlaceBlockAt(c.world, o), "no azalea on stone");
        for (IBlockState soil : new IBlockState[] {Blocks.DIRT.getDefaultState(), Blocks.GRASS.getDefaultState(), Blocks.CLAY.getDefaultState(),
                Blocks.FARMLAND.getDefaultState(), LushContent.moss.getDefaultState(), LushContent.rootedDirt.getDefaultState()}) {
            c.world.setBlockState(o.down(), soil, 2);
            c.ok(LushContent.floweringAzalea.canPlaceBlockAt(c.world, o), "azalea on " + soil.getBlock().getRegistryName());
        }
        c.world.setBlockState(o.down(), Blocks.DIRT.getDefaultState(), 2);
        c.world.setBlockState(o, LushContent.azalea.getDefaultState(), 2);
        Random r = new Random(11);
        int uses = 0;
        for (int i = 0; i < 400; i++) {
            if (LushContent.azalea.canUseBonemeal(c.world, r, o, LushContent.azalea.getDefaultState())) {
                uses++;
            }
        }
        c.ok(uses > 140 && uses < 220, "bone meal works 45% of the time: " + uses / 4.0);
        LushContent.azalea.grow(c.world, r, o, c.world.getBlockState(o));
        BlockPos lo = o.add(-6, 0, -6), hi = o.add(6, 9, 6);
        int logs = count(c, lo, hi, Blocks.LOG), leaves = count(c, lo, hi, LushContent.leaves), flowering = count(c, lo, hi, LushContent.floweringLeaves);
        log("azalea tree: {} logs, {} leaves, {} flowering", logs, leaves, flowering);
        c.ok(logs >= 5 && logs <= 9, "a trunk of 5 to 9 logs: " + logs);
        c.ok(leaves > 30 && flowering > 5 && flowering < leaves, "leaves, a quarter of them flowering");
        c.eq(Blocks.LOG, c.world.getBlockState(o).getBlock(), "the azalea became the trunk");
        c.eq(LushContent.rootedDirt, c.world.getBlockState(o.down()).getBlock(), "on rooted dirt");
        c.fill(lo, hi, AIR);
        // A tree with no room gives the azalea back.
        c.world.setBlockState(o, LushContent.azalea.getDefaultState(), 2);
        c.world.setBlockState(o.up(), STONE, 2);
        LushContent.azalea.grow(c.world, r, o, c.world.getBlockState(o));
        c.eq(LushContent.azalea, c.world.getBlockState(o).getBlock(), "no room, no tree");
        c.world.setBlockState(o.up(), AIR, 2);
        c.world.setBlockState(o.down(), STONE, 3);
        c.eq(Blocks.AIR, c.world.getBlockState(o).getBlock(), "an azalea breaks without soil");
        c.eq(1, Drops.count(c.world, o, Item.getItemFromBlock(LushContent.azalea)), "and drops itself");
        // Leaf drops.
        int bushes = 0, sticks = 0;
        net.minecraft.util.NonNullList<ItemStack> drops = net.minecraft.util.NonNullList.create();
        for (int i = 0; i < 4000; i++) {
            drops.clear();
            LushContent.floweringLeaves.getDrops(drops, c.world, o, LushContent.floweringLeaves.getDefaultState(), 0);
            for (ItemStack s : drops) {
                if (s.getItem() == Item.getItemFromBlock(LushContent.floweringAzalea)) {
                    bushes++;
                } else if (s.getItem() == Items.STICK) {
                    sticks++;
                }
            }
        }
        c.ok(bushes > 140 && bushes < 270, "flowering leaves drop a flowering azalea 5% of the time: " + bushes / 40.0);
        c.ok(sticks > 45 && sticks < 125, "and sticks 2%: " + sticks / 40.0);
        c.world.setBlockState(o, LushContent.leaves.getDefaultState(), 2);
        c.eq(1, harvest(c, o, new ItemStack(Items.SHEARS), Item.getItemFromBlock(LushContent.leaves)), "shears take the leaves");
    }

    private void pots(Check c) {
        BlockPos o = c.area().add(4, 0, 4);
        c.world.setBlockState(o, Blocks.FLOWER_POT.getDefaultState(), 3);
        EntityPlayerMP p = player(c, o.south(2), new ItemStack(LushContent.floweringAzalea, 2));
        use(c, p, o, EnumFacing.UP);
        c.eq(LushContent.pottedFloweringAzalea, c.world.getBlockState(o).getBlock(), "a flowering azalea goes into an empty pot");
        c.eq(1, p.getHeldItemMainhand().getCount(), "one is used");
        p.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        use(c, p, o, EnumFacing.UP);
        c.eq(Blocks.FLOWER_POT, c.world.getBlockState(o).getBlock(), "using the pot takes it out again");
        c.ok(p.getHeldItemMainhand().getItem() == Item.getItemFromBlock(LushContent.floweringAzalea), "into the hand");
        c.world.setBlockState(o, LushContent.pottedAzalea.getDefaultState(), 3);
        p.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        p.interactionManager.tryHarvestBlock(o);
        int pot = 0, plant = 0;
        for (EntityItem e : c.world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(o).grow(2))) {
            if (e.getItem().getItem() == Items.FLOWER_POT) {
                pot++;
            } else if (e.getItem().getItem() == Item.getItemFromBlock(LushContent.azalea)) {
                plant++;
            }
            e.setDead();
        }
        c.ok(pot == 1 && plant == 1, "a broken potted azalea drops the pot and the azalea");
    }

    private void roots(Check c) {
        BlockPos o = c.area().add(4, 3, 4);
        c.world.setBlockState(o, LushContent.rootedDirt.getDefaultState(), 2);
        LushContent.rootedDirt.grow(c.world, new Random(1), o, c.world.getBlockState(o));
        c.eq(LushContent.hangingRoots, c.world.getBlockState(o.down()).getBlock(), "bone meal on rooted dirt grows hanging roots under it");
        c.eq(0, harvest(c, o.down(), ItemStack.EMPTY, Item.getItemFromBlock(LushContent.hangingRoots)), "hanging roots drop nothing by hand");
        c.world.setBlockState(o.down(), LushContent.hangingRoots.getDefaultState(), 2);
        c.eq(1, harvest(c, o.down(), new ItemStack(Items.SHEARS), Item.getItemFromBlock(LushContent.hangingRoots)), "shears take them");
        c.world.setBlockState(o.down(), LushContent.sporeBlossom.getDefaultState(), 2);
        c.ok(!LushContent.sporeBlossom.canPlaceBlockAt(c.world, o.add(3, 0, 0)), "a spore blossom needs a ceiling");
        EntityPlayerMP p = player(c, o.south(2), new ItemStack(Items.IRON_HOE));
        c.eq(EnumActionResult.SUCCESS, use(c, p, o, EnumFacing.UP), "a hoe works on rooted dirt");
        c.eq(Blocks.DIRT, c.world.getBlockState(o).getBlock(), "it becomes dirt");
        c.eq(1, Drops.count(c.world, o.up(), Item.getItemFromBlock(LushContent.hangingRoots)), "and gives hanging roots");
        c.eq(1, p.getHeldItemMainhand().getItemDamage(), "for one use of the hoe");
        c.world.setBlockState(o, AIR, 3);
        c.eq(Blocks.AIR, c.world.getBlockState(o.down()).getBlock(), "a spore blossom falls with its ceiling");
        c.eq(1, Drops.count(c.world, o.down(), Item.getItemFromBlock(LushContent.sporeBlossom)), "and drops itself");
    }

    private void vines(Check c) {
        BlockPos top = c.area().add(6, 55, 6);
        c.world.setBlockState(top, STONE, 2);
        EntityPlayerMP p = player(c, top.down(3), new ItemStack(LushContent.glowBerries, 3));
        c.eq(EnumActionResult.SUCCESS, use(c, p, top, EnumFacing.DOWN), "glow berries plant under a block");
        BlockPos v = top.down();
        c.eq(LushContent.caveVines, c.world.getBlockState(v).getBlock(), "a cave vine tip");
        c.ok(c.world.getBlockState(v).getValue(BlockCaveVines.AGE) < BlockCaveVines.MAX_AGE, "that can still grow");
        c.eq(EnumActionResult.SUCCESS, use(c, p, v, EnumFacing.DOWN), "and under a cave vine");
        c.eq(LushContent.caveVinesPlant, c.world.getBlockState(v).getBlock(), "the old tip becomes plant");
        c.eq(LushContent.caveVines, c.world.getBlockState(v.down()).getBlock(), "above the new tip");
        c.world.setBlockState(v.down(), LushContent.caveVines.getDefaultState(), 2);
        c.eq(1, p.getHeldItemMainhand().getCount(), "two berries used");
        c.ok(use(c, p, top.add(5, -6, 0), EnumFacing.UP) != EnumActionResult.SUCCESS, "not in the open");
        // Growth.
        Random r = new Random(9);
        for (int i = 0; i < 30000; i++) {
            for (int d = 1; d < 56; d++) {
                BlockPos q = top.down(d);
                IBlockState s = c.world.getBlockState(q);
                if (s.getBlock() == LushContent.caveVines) {
                    s.getBlock().randomTick(c.world, q, s, r);
                    break;
                }
            }
        }
        int length = 0, berries = 0;
        while (BlockCaveVines.isVine(c.world.getBlockState(top.down(length + 1)))) {
            length++;
            if (c.world.getBlockState(top.down(length)).getValue(BlockCaveVines.BERRIES)) {
                berries++;
            }
        }
        IBlockState tip = c.world.getBlockState(top.down(length));
        log("cave vine grew to {} with {} berry blocks", length, berries);
        c.ok(length > 4 && length < 55, "the vine grows down and stops short of the floor: " + length);
        c.ok(tip.getBlock() == LushContent.caveVines && tip.getValue(BlockCaveVines.AGE) == BlockCaveVines.MAX_AGE, "ending in a tip at its last age");
        c.eq(LushContent.caveVinesPlant, c.world.getBlockState(top.down(length - 1)).getBlock(), "plant above the tip");
        // Berries: bone meal, picking.
        BlockPos mid = top.down(2);
        IBlockState body = c.world.getBlockState(mid).withProperty(BlockCaveVines.BERRIES, false);
        c.world.setBlockState(mid, body, 2);
        c.ok(LushContent.caveVinesPlant.canGrow(c.world, mid, body, false), "bone meal works on a vine without berries");
        LushContent.caveVinesPlant.grow(c.world, r, mid, body);
        c.ok(c.world.getBlockState(mid).getValue(BlockCaveVines.BERRIES), "and gives it berries");
        c.ok(!LushContent.caveVinesPlant.canGrow(c.world, mid, c.world.getBlockState(mid), false), "once");
        Drops.count(c.world, mid, LushContent.glowBerries);
        p.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        use(c, p, mid, EnumFacing.NORTH);
        c.ok(!c.world.getBlockState(mid).getValue(BlockCaveVines.BERRIES), "using it picks the berries");
        c.eq(1, Drops.count(c.world, mid, LushContent.glowBerries), "one glow berry");
        // Shears stop a tip.
        BlockPos other = top.add(4, 0, 0);
        c.world.setBlockState(other, STONE, 2);
        c.world.setBlockState(other.down(), LushContent.caveVines.getDefaultState(), 2);
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.SHEARS));
        use(c, p, other.down(), EnumFacing.NORTH);
        c.eq(BlockCaveVines.MAX_AGE, c.world.getBlockState(other.down()).getValue(BlockCaveVines.AGE), "shears stop a tip growing");
        // Breaking: the part below falls, the part above grows a new tip.
        c.world.setBlockState(top.down(3), c.world.getBlockState(top.down(3)).withProperty(BlockCaveVines.BERRIES, true), 2);
        Drops.count(c.world, top.down(3), LushContent.glowBerries);
        c.world.setBlockState(top.down(3), AIR, 3);
        c.eq(LushContent.caveVines, c.world.getBlockState(top.down(2)).getBlock(), "the vine above a break ends in a tip again");
        c.eq(Blocks.AIR, c.world.getBlockState(top.down(length)).getBlock(), "the vine below a break falls");
        c.world.setBlockState(top, AIR, 3);
        c.eq(Blocks.AIR, c.world.getBlockState(top.down()).getBlock(), "and all of it without its ceiling");
        c.ok(LushContent.caveVines.isLadder(LushContent.caveVines.getDefaultState(), c.world, top, p), "cave vines can be climbed");
        c.eq(2, ((net.minecraft.item.ItemFood) LushContent.glowBerries).getHealAmount(new ItemStack(LushContent.glowBerries)), "glow berries feed 2");
        for (EntityItem e : c.world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(top).grow(8, 40, 8))) {
            e.setDead();
        }
    }

    private void bigDripleaf(Check c) {
        BlockPos o = c.area().add(6, 0, 6);
        BlockBigDripleaf leaf = LushContent.bigDripleaf;
        c.ok(!leaf.canPlaceBlockAt(c.world, o), "no big dripleaf on stone");
        c.world.setBlockState(o.down(), Blocks.CLAY.getDefaultState(), 2);
        EntityPlayerMP p = player(c, o.south(2), new ItemStack(leaf, 3));
        c.eq(EnumActionResult.SUCCESS, use(c, p, o.down(), EnumFacing.UP), "a big dripleaf on clay");
        EnumFacing facing = c.world.getBlockState(o).getValue(BlockBigDripleaf.FACING);
        c.eq(EnumActionResult.SUCCESS, use(c, p, o, EnumFacing.UP), "and another on top of it");
        c.eq(LushContent.bigDripleafStem, c.world.getBlockState(o).getBlock(), "the lower one becomes a stem");
        c.eq(leaf, c.world.getBlockState(o.up()).getBlock(), "under the leaf");
        c.eq(facing, c.world.getBlockState(o.up()).getValue(BlockBigDripleaf.FACING), "facing the same way");
        c.ok(LushContent.bigDripleafStem.canGrow(c.world, o, c.world.getBlockState(o), false), "bone meal works on the stem");
        LushContent.bigDripleafStem.grow(c.world, new Random(1), o, c.world.getBlockState(o));
        c.ok(c.world.getBlockState(o.up()).getBlock() == LushContent.bigDripleafStem && c.world.getBlockState(o.up(2)).getBlock() == leaf, "and adds a stem");
        // Tilting.
        BlockPos top = o.up(2);
        EntityPig pig = new EntityPig(c.world);
        pig.setNoAI(true);
        pig.setPosition(top.getX() + 0.5, top.getY() + 0.9375, top.getZ() + 0.5);
        pig.onGround = true;
        c.world.spawnEntity(pig);
        c.eq(1, c.world.getCollisionBoxes(pig, pig.getEntityBoundingBox().expand(0, -0.1, 0)).size(), "the leaf holds a pig");
        c.ok(c.world.isUpdateScheduled(top, leaf), "and notices it");
        Random r = new Random(2);
        leaf.updateTick(c.world, top, c.world.getBlockState(top), r);
        c.eq(BlockBigDripleaf.Tilt.UNSTABLE, c.world.getBlockState(top).getValue(BlockBigDripleaf.TILT), "unstable under the pig");
        leaf.updateTick(c.world, top, c.world.getBlockState(top), r);
        c.eq(BlockBigDripleaf.Tilt.PARTIAL, c.world.getBlockState(top).getValue(BlockBigDripleaf.TILT), "then partly tilted");
        c.eq(1, c.world.getCollisionBoxes(pig, pig.getEntityBoundingBox().offset(0, -0.2, 0)).size(), "still holding");
        leaf.updateTick(c.world, top, c.world.getBlockState(top), r);
        c.eq(BlockBigDripleaf.Tilt.FULL, c.world.getBlockState(top).getValue(BlockBigDripleaf.TILT), "then all the way");
        c.eq(0, c.world.getCollisionBoxes(pig, pig.getEntityBoundingBox().offset(0, -0.2, 0)).size(), "and the pig falls through");
        leaf.updateTick(c.world, top, c.world.getBlockState(top), r);
        c.eq(BlockBigDripleaf.Tilt.NONE, c.world.getBlockState(top).getValue(BlockBigDripleaf.TILT), "and it springs back");
        // Powered: no tilt.
        c.world.setBlockState(top.north(), Blocks.REDSTONE_BLOCK.getDefaultState(), 3);
        leaf.updateTick(c.world, top, c.world.getBlockState(top), r);
        c.eq(BlockBigDripleaf.Tilt.NONE, c.world.getBlockState(top).getValue(BlockBigDripleaf.TILT), "powered, it never tilts");
        c.world.setBlockState(top.north(), AIR, 3);
        pig.setDead();
        // A projectile.
        EntityTippedArrow arrow = new EntityTippedArrow(c.world, top.getX() + 0.5, top.getY() + 2, top.getZ() + 0.5);
        MinecraftForge.EVENT_BUS.post(new ProjectileImpactEvent.Arrow(arrow,
                new RayTraceResult(new Vec3d(top.getX() + 0.5, top.getY() + 0.9, top.getZ() + 0.5), EnumFacing.UP, top)));
        c.eq(BlockBigDripleaf.Tilt.FULL, c.world.getBlockState(top).getValue(BlockBigDripleaf.TILT), "an arrow tilts it all the way");
        // Breaking a stem takes the plant: three big dripleaves.
        Drops.count(c.world, o.up(), Item.getItemFromBlock(leaf));
        c.world.setBlockState(o, AIR, 3);
        c.ok(c.world.isAirBlock(o.up()) && c.world.isAirBlock(top), "breaking the bottom stem takes the plant");
        c.eq(2, Drops.count(c.world, o.up(), Item.getItemFromBlock(leaf)), "each part above drops a big dripleaf");
    }

    private void smallDripleaf(Check c) {
        BlockPos o = c.area().add(6, 0, 6);
        BlockSmallDripleaf small = LushContent.smallDripleaf;
        c.world.setBlockState(o.down(), Blocks.DIRT.getDefaultState(), 2);
        c.ok(!small.canPlaceBlockAt(c.world, o), "no small dripleaf on dirt out of water");
        c.world.setBlockState(o.down(), Blocks.CLAY.getDefaultState(), 2);
        EntityPlayerMP p = player(c, o.south(2), new ItemStack(small, 2));
        c.eq(EnumActionResult.SUCCESS, use(c, p, o.down(), EnumFacing.UP), "a small dripleaf on clay");
        IBlockState lower = c.world.getBlockState(o), upper = c.world.getBlockState(o.up());
        c.ok(lower.getBlock() == small && !lower.getValue(BlockSmallDripleaf.UPPER) && upper.getBlock() == small
                && upper.getValue(BlockSmallDripleaf.UPPER), "two blocks tall");
        c.eq(0, harvest(c, o.up(), ItemStack.EMPTY, Item.getItemFromBlock(small)), "nothing by hand");
        c.ok(c.world.isAirBlock(o) && c.world.isAirBlock(o.up()), "both halves go");
        small.place(c.world, o, EnumFacing.EAST, 2);
        c.eq(1, harvest(c, o, new ItemStack(Items.SHEARS), Item.getItemFromBlock(small)), "one for shears");
        c.ok(c.world.isAirBlock(o) && c.world.isAirBlock(o.up()), "both halves go again");
        small.place(c.world, o, EnumFacing.EAST, 2);
        small.grow(c.world, new Random(5), o.up(), c.world.getBlockState(o.up()));
        int height = 0;
        while (BlockBigDripleafAccess.isPart(c.world.getBlockState(o.up(height)))) {
            height++;
        }
        c.ok(height >= 2 && height <= 5, "bone meal makes a big dripleaf 2 to 5 tall: " + height);
        c.eq(LushContent.bigDripleaf, c.world.getBlockState(o.up(height - 1)).getBlock(), "a leaf on top");
        c.eq(EnumFacing.EAST, c.world.getBlockState(o).getValue(BlockBigDripleaf.FACING), "facing as the small one did");
        c.fill(o, o.up(6), AIR);
        small.place(c.world, o, EnumFacing.NORTH, 2);
        c.world.setBlockState(o.down(), STONE, 3);
        c.ok(c.world.isAirBlock(o) && c.world.isAirBlock(o.up()), "it goes with its clay");
    }

    /** {@code BlockBigDripleaf.isPart} is package-private; the test asks the same question by class. */
    private static final class BlockBigDripleafAccess {
        static boolean isPart(IBlockState s) {
            return s.getBlock() == LushContent.bigDripleaf || s.getBlock() == LushContent.bigDripleafStem;
        }
    }

    private void lichen(Check c) {
        BlockPos o = c.area().add(8, 2, 8);
        Item item = Item.getItemFromBlock(LushContent.glowLichen[0]);
        // A corner: stone to the north and to the east of the air at o.
        c.world.setBlockState(o.north(), STONE, 2);
        c.world.setBlockState(o.east(), STONE, 2);
        EntityPlayerMP p = player(c, o.south(2), new ItemStack(item, 8));
        c.eq(EnumActionResult.SUCCESS, use(c, p, o.north(), EnumFacing.SOUTH), "glow lichen on a wall");
        c.eq(1 << EnumFacing.NORTH.getIndex(), BlockGlowLichen.faces(c.world.getBlockState(o)), "on the face that was clicked");
        c.eq(EnumActionResult.SUCCESS, use(c, p, o.east(), EnumFacing.WEST), "a second face in the same space");
        int both = (1 << EnumFacing.NORTH.getIndex()) | (1 << EnumFacing.EAST.getIndex());
        c.eq(both, BlockGlowLichen.faces(c.world.getBlockState(o)), "joins the first");
        c.eq(6, p.getHeldItemMainhand().getCount(), "two used");
        c.ok(use(c, p, o.north(), EnumFacing.SOUTH) != EnumActionResult.SUCCESS, "no face twice, and none without support");
        // The floor (stone at y 99 is two below): a ceiling instead.
        c.world.setBlockState(o.up(), STONE, 2);
        c.eq(EnumActionResult.SUCCESS, use(c, p, o.up(), EnumFacing.DOWN), "and the ceiling");
        c.eq(LushContent.glowLichen[2], c.world.getBlockState(o).getBlock(), "which is the up block");
        c.eq(both | 2, BlockGlowLichen.faces(c.world.getBlockState(o)), "with all three faces");
        c.ok(c.world.getBlockState(o).getBlock().isReplaceable(c.world, o), "replaceable");
        // Support.
        c.world.setBlockState(o.east(), AIR, 3);
        c.eq((1 << EnumFacing.NORTH.getIndex()) | 2, BlockGlowLichen.faces(c.world.getBlockState(o)), "a face goes with its wall");
        c.eq(0, harvest(c, o, ItemStack.EMPTY, item), "nothing by hand");
        c.world.setBlockState(o, BlockGlowLichen.of((1 << EnumFacing.NORTH.getIndex()) | 2), 2);
        c.eq(2, harvest(c, o, new ItemStack(Items.SHEARS), item), "shears give one per face");
        // Spreading.
        c.world.setBlockState(o, BlockGlowLichen.of(1 << EnumFacing.NORTH.getIndex()), 2);
        Block lichen = c.world.getBlockState(o).getBlock();
        c.ok(((BlockGlowLichen) lichen).canGrow(c.world, o, c.world.getBlockState(o), false), "bone meal can spread it");
        Random r = new Random(4);
        for (int i = 0; i < 40; i++) {
            IBlockState s = c.world.getBlockState(o);
            if (s.getBlock() instanceof BlockGlowLichen && ((BlockGlowLichen) s.getBlock()).canGrow(c.world, o, s, false)) {
                ((BlockGlowLichen) s.getBlock()).grow(c.world, r, o, s);
            }
        }
        int facesAround = 0;
        for (BlockPos q : BlockPos.getAllInBox(o.add(-2, -2, -2), o.add(2, 2, 2))) {
            int mask = BlockGlowLichen.faces(c.world.getBlockState(q));
            for (EnumFacing f : EnumFacing.values()) {
                if (BlockGlowLichen.has(mask, f)) {
                    facesAround++;
                    c.ok(BlockGlowLichen.supported(c.world, q, f), "a spread face has a block behind it at " + q + " " + f);
                }
            }
        }
        log("lichen spread to {} faces", facesAround);
        // From the north face at o: its own ceiling, and around the stone block's four open sides.
        c.ok(facesAround >= 4, "bone meal spreads to neighboring faces: " + facesAround);
        c.ok(!((BlockGlowLichen) c.world.getBlockState(o).getBlock()).canGrow(c.world, o, c.world.getBlockState(o), false), "until nothing is left in reach");
        c.world.setBlockState(o.north(), AIR, 3);
        c.world.setBlockState(o.up(), AIR, 3);
        c.eq(0, BlockGlowLichen.faces(c.world.getBlockState(o)), "all gone with the blocks");
    }

    private void recipes(Check c) {
        ItemStack m = new ItemStack(LushContent.moss);
        Crafting.expect(c, Crafting.grid(2, 1, m, m), new ItemStack(LushContent.mossCarpet, 3), "moss carpet");
        Crafting.expect(c, Crafting.grid(2, 1, m, new ItemStack(Blocks.COBBLESTONE)), new ItemStack(Blocks.MOSSY_COBBLESTONE), "mossy cobblestone");
        Crafting.expect(c, Crafting.grid(2, 1, new ItemStack(Blocks.STONEBRICK), m), new ItemStack(Blocks.STONEBRICK, 1, 1), "mossy stone bricks");
    }

    private void regions(Check c) {
        long seed = c.world.getSeed();
        int lush = 0, total = 0;
        for (int x = -4000; x < 4000; x += 37) {
            for (int z = -4000; z < 4000; z += 41) {
                for (int y = 8; y < 60; y += 13) {
                    total++;
                    if (LushRegions.isLush(seed, x, y, z)) {
                        lush++;
                    }
                }
            }
        }
        double share = lush * 100.0 / total;
        log("regions: {}% of the underground is lush", String.format("%.1f", share));
        c.ok(share > 4 && share < 25, "lush regions cover a modest share of the underground: " + share);
        c.eq(LushRegions.value(seed, 123, 30, -456), LushRegions.value(seed, 123, 30, -456), "the same answer every time");
        c.ok(LushRegions.value(seed, 123, 30, -456) != LushRegions.value(seed + 1, 123, 30, -456), "another seed, other regions");
        // mayContain never says no where there is lush.
        int wrong = 0;
        for (int x = -2000; x < 2000; x += 16 * 7) {
            for (int z = -2000; z < 2000; z += 16 * 5) {
                if (!LushRegions.mayContain(seed, x, z, 4, 124)) {
                    for (int dx = 0; dx < 16; dx += 3) {
                        for (int dz = 0; dz < 16; dz += 3) {
                            for (int y = 4; y <= 124; y += 3) {
                                if (LushRegions.isLush(seed, x + dx, y, z + dz)) {
                                    wrong++;
                                }
                            }
                        }
                    }
                }
            }
        }
        c.eq(0, wrong, "the chunk pre-check never skips a lush block");
    }

    /** @return the corner of a 16x16 area that is lush from y 10 to 26, near the test areas */
    private static BlockPos lushSpot(long seed) {
        for (int x = 24000; x < 60000; x += 16) {
            for (int z = 24000; z < 26000; z += 16) {
                boolean all = true;
                for (int dx = 0; dx <= 15 && all; dx += 5) {
                    for (int dz = 0; dz <= 15 && all; dz += 5) {
                        for (int y = 10; y <= 26 && all; y += 4) {
                            all = LushRegions.isLush(seed, x + dx, y, z + dz);
                        }
                    }
                }
                if (all) {
                    return new BlockPos(x, 0, z);
                }
            }
        }
        return null;
    }

    private void caves(Check c) {
        long seed = c.world.getSeed();
        BlockPos spot = lushSpot(seed);
        if (!c.ok(spot != null, "a lush spot exists")) {
            return;
        }
        int x0 = spot.getX(), z0 = spot.getZ();
        // Our own cave: stone from y 8 to 60 over the window, a hollow from y 14 to 21.
        // The spot is lush, so its real caves were dressed when these chunks generated: wipe a ring around
        // the window too, or that would be counted as ours.
        c.fill(new BlockPos(x0 - 12, 8, z0 - 12), new BlockPos(x0 + 27, 60, z0 + 27), STONE);
        c.fill(new BlockPos(x0 - 12, 61, z0 - 12), new BlockPos(x0 + 27, 100, z0 + 27), AIR);
        // Grass on top: the root system only plants its tree on azalea_grows_on (dirt, sand, terracotta, snow).
        c.fill(new BlockPos(x0 - 12, 60, z0 - 12), new BlockPos(x0 + 27, 60, z0 + 27), Blocks.GRASS.getDefaultState());
        c.fill(new BlockPos(x0, 14, z0), new BlockPos(x0 + 15, 21, z0 + 15), AIR);
        Chunk chunk = c.world.getChunk(spot);
        chunk.generateSkylightMap();
        c.world.getChunk(spot.add(16, 0, 0)).generateSkylightMap();
        c.world.getChunk(spot.add(0, 0, 16)).generateSkylightMap();
        c.world.getChunk(spot.add(16, 0, 16)).generateSkylightMap();
        c.world.getChunk(spot.add(-8, 0, -8)).generateSkylightMap();
        int dressed = LushCaveGenerator.decorate(c.world, new Random(77), x0, z0, seed);
        // The tree's random height rarely lands in a cave (as in 1.18.2), so plant it from the cave's middle.
        boolean tree = false;
        for (int d = 0; d < 8 && !tree; d++) {
            BlockPos origin = new BlockPos(x0 + 8 + d, 21, z0 + 8);
            tree = c.world.isAirBlock(origin) && LushCaveGenerator.rootedTree(c.world, new Random(77 + d), origin, x0 - 8, z0 - 8);
        }
        c.ok(tree, "a root system from the cave's ceiling grows its azalea tree on the grass");
        BlockPos fl = new BlockPos(x0, 13, z0), fh = new BlockPos(x0 + 15, 13, z0 + 15);
        BlockPos cl = new BlockPos(x0, 22, z0), ch = new BlockPos(x0 + 15, 22, z0 + 15);
        BlockPos al = new BlockPos(x0, 14, z0), ah = new BlockPos(x0 + 15, 21, z0 + 15);
        int mossFloor = count(c, fl, fh, LushContent.moss), clay = count(c, fl, fh, Blocks.CLAY), water = count(c, fl, fh, Blocks.WATER);
        int mossCeiling = count(c, cl, ch, LushContent.moss);
        int vines = count(c, al, ah, LushContent.caveVines), vineBlocks = vines + count(c, al, ah, LushContent.caveVinesPlant);
        int blossoms = count(c, al, ah, LushContent.sporeBlossom);
        int plants = count(c, al, ah, Blocks.TALLGRASS) + count(c, al, ah, LushContent.mossCarpet) + count(c, al, ah, LushContent.azalea)
                + count(c, al, ah, LushContent.floweringAzalea);
        int dripleaf = count(c, al, ah, LushContent.bigDripleaf) + count(c, al, ah, LushContent.smallDripleaf) / 2;
        BlockPos sl = new BlockPos(x0 - 8, 22, z0 - 8), sh = new BlockPos(x0 + 23, 75, z0 + 23);
        int logs = count(c, sl, sh, Blocks.LOG), rooted = count(c, sl, sh, LushContent.rootedDirt);
        int hanging = count(c, new BlockPos(x0 - 8, 14, z0 - 8), new BlockPos(x0 + 23, 22, z0 + 23), LushContent.hangingRoots);
        log("cave: {} dressed; floor {} moss, {} clay, {} water; ceiling {} moss; {} vines ({} blocks), {} blossoms, {} plants, {} dripleaves; "
                + "{} logs, {} rooted dirt, {} hanging roots", dressed, mossFloor, clay, water, mossCeiling, vines, vineBlocks, blossoms, plants,
                dripleaf, logs, rooted, hanging);
        c.ok(dressed > 480 && dressed <= 512, "every lush floor and ceiling block of the cave is looked at: " + dressed);
        c.ok(mossFloor > 60, "most of the floor is moss: " + mossFloor);
        c.ok(mossCeiling > 100, "and most of the ceiling: " + mossCeiling);
        c.ok(plants > mossFloor / 3, "plants on the moss: " + plants);
        c.ok(vines > 5 && vineBlocks > vines, "cave vines hang from the ceiling: " + vines);
        c.ok(logs >= 4 && rooted > 20, "an azalea tree above, rooted down to the cave: " + logs + " logs, " + rooted + " rooted dirt");
        c.ok(count(c, new BlockPos(x0 - 8, 61, z0 - 8), new BlockPos(x0 + 23, 75, z0 + 23), LushContent.leaves) > 20, "with its leaves in the open");
        c.eq(LushContent.rootedDirt, firstNonAirBelowLog(c, sl, sh), "standing on rooted dirt");
        // Water only where it is held in.
        for (BlockPos p : BlockPos.getAllInBox(fl, fh)) {
            if (c.world.getBlockState(p).getBlock() == Blocks.WATER) {
                for (EnumFacing f : EnumFacing.Plane.HORIZONTAL) {
                    IBlockState n = c.world.getBlockState(p.offset(f));
                    // A dripleaf standing in the pool holds the pool's water itself (waterlogged).
                    c.ok(n.isNormalCube() || n.getBlock() == Blocks.WATER
                            || com.exiledradio.backportcore.waterlog.Waterlogging.hasWaterSource(c.world, p.offset(f)), "pool water is held in at " + p);
                }
            }
        }
        // Nothing left the window.
        int outside = 0;
        for (BlockPos p : BlockPos.getAllInBox(new BlockPos(x0 - 12, 8, z0 - 12), new BlockPos(x0 + 27, 90, z0 + 27))) {
            boolean in = p.getX() >= x0 - 8 && p.getX() < x0 + 24 && p.getZ() >= z0 - 8 && p.getZ() < z0 + 24;
            Block b = c.world.getBlockState(p).getBlock();
            if (!in && (b == LushContent.leaves || b == LushContent.floweringLeaves || b == LushContent.rootedDirt || b == LushContent.moss)) {
                outside++;
            }
        }
        c.eq(0, outside, "nothing is placed outside the population window");
        // Glow lichen, in the same cave.
        int placed = LushCaveGenerator.lichen(c.world, new Random(5), x0, z0, 2000);
        int lichen = 0;
        for (BlockPos p : BlockPos.getAllInBox(new BlockPos(x0 - 1, 8, z0 - 1), new BlockPos(x0 + 16, 60, z0 + 16))) {
            int mask = BlockGlowLichen.faces(c.world.getBlockState(p));
            if (mask != 0) {
                lichen++;
                for (EnumFacing f : EnumFacing.values()) {
                    if (BlockGlowLichen.has(mask, f)) {
                        c.ok(BlockGlowLichen.supported(c.world, p, f), "generated lichen holds on at " + p);
                    }
                }
            }
        }
        log("lichen: {} of 2000 tries placed, {} blocks", placed, lichen);
        c.ok(placed > 0 && lichen >= placed / 2, "glow lichen generates in a cave: " + placed);

        // Real chunks around another lush spot.
        int cx = (x0 >> 4) + 2, cz = z0 >> 4;
        for (int x = 0; x <= 9; x++) {
            for (int z = 0; z <= 9; z++) {
                c.world.getChunkProvider().provideChunk(cx + x, cz + z);
            }
        }
        int[] n = new int[8];
        for (int x = 1; x <= 8; x++) {
            for (int z = 1; z <= 8; z++) {
                Chunk real = c.world.getChunk(cx + x, cz + z);
                for (int bx = 0; bx < 16; bx++) {
                    for (int bz = 0; bz < 16; bz++) {
                        for (int y = 1; y < 128; y++) {
                            Block b = real.getBlockState(bx, y, bz).getBlock();
                            if (b instanceof BlockGlowLichen) {
                                n[0]++;
                            } else if (b == LushContent.moss) {
                                n[1]++;
                            } else if (b instanceof BlockCaveVines) {
                                n[2]++;
                            } else if (b == LushContent.rootedDirt) {
                                n[3]++;
                            } else if (b == LushContent.leaves) {
                                n[4]++;
                            } else if (b == Blocks.CLAY && y < 50) {
                                n[5]++;
                            } else if (b == LushContent.sporeBlossom) {
                                n[6]++;
                            } else if (b == LushContent.bigDripleaf || b == LushContent.smallDripleaf) {
                                n[7]++;
                            }
                        }
                    }
                }
            }
        }
        log("64 real chunks: {} lichen, {} moss, {} vine blocks, {} rooted dirt, {} azalea leaves, {} clay, {} blossoms, {} dripleaves",
                n[0], n[1], n[2], n[3], n[4], n[5], n[6], n[7]);
        c.ok(n[0] > 0, "glow lichen in real caves: " + n[0]);
    }

    private static Block firstNonAirBelowLog(Check c, BlockPos lo, BlockPos hi) {
        BlockPos best = null;
        for (BlockPos p : BlockPos.getAllInBox(lo, hi)) {
            if (c.world.getBlockState(p).getBlock() == Blocks.LOG && (best == null || p.getY() < best.getY())) {
                best = p.toImmutable();
            }
        }
        return best == null ? null : c.world.getBlockState(best.down()).getBlock();
    }
}
