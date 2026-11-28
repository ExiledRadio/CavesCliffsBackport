package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.common.Advancements;
import com.exiledradio.cavesandcliffsbackport.copper.BlockLightningRod;
import com.exiledradio.cavesandcliffsbackport.copper.CopperContent;
import com.exiledradio.cavesandcliffsbackport.copper.CopperOreGenerator;
import com.exiledradio.cavesandcliffsbackport.copper.CopperStage;
import com.exiledradio.cavesandcliffsbackport.copper.Lightning;
import com.exiledradio.cavesandcliffsbackport.copper.Weathering;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDirectional;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.oredict.OreDictionary;

import java.lang.reflect.Field;
import java.util.Random;

/** Stream Cu: blocks, drops, oxidation, waxing, scraping, lightning, rods, recipes, ore generation. */
public final class CopperSelfTest implements SelfTest {

    private BlockPos lightningArea;

    @Override
    public String name() {
        return "copper";
    }

    private static Block b(String name) {
        return CopperContent.BLOCKS.get(name);
    }

    @Override
    public void run(Check c) throws Exception {
        registration(c);
        drops(c);
        oxidation(c);
        waxAndScrape(c);
        lightning(c);
        rods(c);
        stonecutting(c);
        recipes(c);
        generation(c);
    }

    private void registration(Check c) {
        // 3 ores/raw + 32 + 8 double slabs + 8 rods (1.21.9)
        c.eq(51, CopperContent.all().size(), "block count");
        c.eq(new java.util.TreeSet<String>(CopperContent.BLOCKS.keySet()),
                new java.util.TreeSet<String>(java.util.Arrays.asList(CopperContent.blockNames())), "pre-init name list matches registration");
        String[] expect = {"copper_block", "exposed_copper", "weathered_copper", "oxidized_copper", "cut_copper",
                "exposed_cut_copper", "oxidized_cut_copper_stairs", "weathered_cut_copper_slab", "waxed_copper_block",
                "waxed_oxidized_copper", "waxed_exposed_cut_copper_stairs", "waxed_cut_copper_slab",
                "cut_copper_double_slab", "waxed_oxidized_cut_copper_double_slab", "lightning_rod", "raw_copper_block"};
        for (String n : expect) {
            c.ok(b(n) != null, "registered " + n);
        }
        for (Block block : CopperContent.all()) {
            for (IBlockState s : block.getBlockState().getValidStates()) {
                int meta = block.getMetaFromState(s);
                c.ok(block.getMetaFromState(block.getStateFromMeta(meta)) == meta, block.getRegistryName() + " meta " + s);
            }
        }
        c.eq(true, b("copper_block").getTickRandomly(), "unwaxed copper ages");
        c.eq(false, b("oxidized_copper").getTickRandomly(), "oxidized copper does not tick");
        c.eq(false, b("waxed_copper_block").getTickRandomly(), "waxed copper does not tick");
        c.eq(b("exposed_cut_copper_stairs"), Weathering.next(b("cut_copper_stairs")), "stairs chain");
        c.eq(b("waxed_weathered_cut_copper_slab"), Weathering.waxedOf(b("weathered_cut_copper_slab")), "slab wax pair");
        c.eq(CopperContent.DOUBLE_SLABS.get("exposed_cut_copper_slab"), Weathering.next(CopperContent.DOUBLE_SLABS.get("cut_copper_slab")), "double slab chain");
        c.ok(Math.abs(b("copper_block").getExplosionResistance(null) - 6F) < 1e-4, "copper resistance 6");
        c.eq(1, b("copper_block").getHarvestLevel(b("copper_block").getDefaultState()), "copper needs a stone pickaxe");
        c.ok(OreDictionary.getOres("ingotCopper").stream().anyMatch(s -> s.getItem() == CopperContent.copperIngot), "ingotCopper");
        c.ok(com.exiledradio.backportcore.ownership.BlockOwnership.table().containsKey("cut_copper_double_slab"), "declared to Core");
    }

    private static int harvest(Check c, BlockPos pos, IBlockState state, ItemStack tool, Item want) {
        c.world.setBlockState(pos, state, 2);
        EntityPlayerMP p = Players.real(c.world, "copper");
        p.setPosition(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        p.interactionManager.setGameType(GameType.SURVIVAL);
        p.setHeldItem(EnumHand.MAIN_HAND, tool.copy());
        p.interactionManager.tryHarvestBlock(pos);
        return Drops.count(c.world, pos, want);
    }

    private void drops(Check c) {
        BlockPos at = c.area().up();
        ItemStack stone = new ItemStack(Items.STONE_PICKAXE);
        int total = 0, n = 40;
        for (int i = 0; i < n; i++) {
            int got = harvest(c, at, b("copper_ore").getDefaultState(), stone, CopperContent.rawCopper);
            c.ok(got >= 2 && got <= 5, "copper ore drops 2-5 raw copper: " + got);
            total += got;
        }
        c.ok(total > n * 2.8 && total < n * 4.2, "average about 3.5: " + total / (double) n);
        c.eq(0, harvest(c, at, b("copper_ore").getDefaultState(), new ItemStack(Items.WOODEN_PICKAXE), CopperContent.rawCopper), "wooden pickaxe drops nothing");
        ItemStack silk = new ItemStack(Items.IRON_PICKAXE);
        silk.addEnchantment(Enchantments.SILK_TOUCH, 1);
        c.eq(1, harvest(c, at, b("deepslate_copper_ore").getDefaultState(), silk, Item.getItemFromBlock(b("deepslate_copper_ore"))), "silk touch deepslate copper ore");
        c.eq(1, harvest(c, at, b("exposed_cut_copper").getDefaultState(), stone, Item.getItemFromBlock(b("exposed_cut_copper"))), "copper drops itself");
        c.eq(1, harvest(c, at, b("lightning_rod").getDefaultState(), stone, Item.getItemFromBlock(b("lightning_rod"))), "rod drops itself");
    }

    private void oxidation(Check c) {
        BlockPos o = c.area().up();
        c.world.setBlockState(o, b("copper_block").getDefaultState(), 2);
        c.ok(Math.abs(Weathering.ageChance(c.world, o, 0) - 0.75F) < 1e-6, "a lone unaffected block: 0.75");
        c.world.setBlockState(o.east(), b("copper_block").getDefaultState(), 2);
        c.ok(Math.abs(Weathering.ageChance(c.world, o, 0) - 0.75F / 4) < 1e-6, "next to another: (1/2)^2 * 0.75");
        c.world.setBlockState(o.west(), b("exposed_copper").getDefaultState(), 2);
        c.ok(Math.abs(Weathering.ageChance(c.world, o, 0) - 0.75F * 4 / 9) < 1e-6, "one same, one older: (2/3)^2 * 0.75");
        c.eq(0F, Weathering.ageChance(c.world, o.west(), 1), "an exposed block next to unaffected ones waits");
        c.world.setBlockState(o.west(), b("waxed_copper_block").getDefaultState(), 2);
        c.ok(Math.abs(Weathering.ageChance(c.world, o, 0) - 0.75F / 4) < 1e-6, "waxed copper does not count");
        c.world.setBlockState(o.east(), Blocks.AIR.getDefaultState(), 2);
        c.world.setBlockState(o.west(), Blocks.AIR.getDefaultState(), 2);
        // A lone block ages through all stages in a reasonable number of random ticks (expected ~1/(0.0569*0.75)).
        Random rand = new Random(7);
        int ticks = 0;
        while (c.world.getBlockState(o).getBlock() != b("oxidized_copper") && ticks < 20000) {
            IBlockState s = c.world.getBlockState(o);
            s.getBlock().randomTick(c.world, o, s, rand);
            ticks++;
        }
        c.eq(b("oxidized_copper"), c.world.getBlockState(o).getBlock(), "a lone block oxidizes fully");
        c.ok(ticks > 20 && ticks < 200, "in about 70 random ticks: " + ticks);
        // Stairs keep their shape and do not turn into a full block.
        IBlockState stairs = b("cut_copper_stairs").getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.EAST)
                .withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.TOP);
        c.world.setBlockState(o, stairs, 2);
        for (int i = 0; i < 2000 && c.world.getBlockState(o).getBlock() == b("cut_copper_stairs"); i++) {
            c.world.getBlockState(o).getBlock().randomTick(c.world, o, c.world.getBlockState(o), rand);
        }
        IBlockState aged = c.world.getBlockState(o);
        c.eq(b("exposed_cut_copper_stairs"), aged.getBlock(), "stairs age into exposed stairs");
        c.eq(EnumFacing.EAST, aged.getValue(BlockStairs.FACING), "facing kept");
        c.eq(BlockStairs.EnumHalf.TOP, aged.getValue(BlockStairs.HALF), "half kept");
        IBlockState slab = b("cut_copper_slab").getDefaultState().withProperty(BlockSlab.HALF, BlockSlab.EnumBlockHalf.TOP);
        c.world.setBlockState(o, slab, 2);
        for (int i = 0; i < 2000 && c.world.getBlockState(o).getBlock() == b("cut_copper_slab"); i++) {
            c.world.getBlockState(o).getBlock().randomTick(c.world, o, c.world.getBlockState(o), rand);
        }
        c.eq(BlockSlab.EnumBlockHalf.TOP, c.world.getBlockState(o).getValue(BlockSlab.HALF), "a top slab stays a top slab");
        c.world.setBlockState(o, b("waxed_copper_block").getDefaultState(), 2);
        for (int i = 0; i < 2000; i++) {
            c.world.getBlockState(o).getBlock().randomTick(c.world, o, c.world.getBlockState(o), rand);
        }
        c.eq(b("waxed_copper_block"), c.world.getBlockState(o).getBlock(), "waxed copper never ages");
    }

    private static void click(Check c, EntityPlayerMP p, BlockPos pos) {
        PlayerInteractEvent.RightClickBlock e = new PlayerInteractEvent.RightClickBlock(p, EnumHand.MAIN_HAND, pos, EnumFacing.UP, new Vec3d(pos));
        MinecraftForge.EVENT_BUS.post(e);
    }

    private void waxAndScrape(Check c) throws Exception {
        BlockPos o = c.area().up();
        EntityPlayerMP p = Players.real(c.world, "wax");
        p.interactionManager.setGameType(GameType.SURVIVAL);
        // No honeycomb mod in the dev world: give paper the ore name honeycomb for the test.
        OreDictionary.registerOre("honeycomb", Items.PAPER);
        c.world.setBlockState(o, b("weathered_cut_copper").getDefaultState(), 2);
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.PAPER, 2));
        click(c, p, o);
        c.eq(b("waxed_weathered_cut_copper"), c.world.getBlockState(o).getBlock(), "honeycomb waxes");
        c.eq(1, p.getHeldItem(EnumHand.MAIN_HAND).getCount(), "one honeycomb used");
        c.ok(Advancements.has(p, "husbandry/wax_on"), "Wax On granted");
        click(c, p, o);
        c.eq(b("waxed_weathered_cut_copper"), c.world.getBlockState(o).getBlock(), "waxed copper takes no more wax");
        c.eq(1, p.getHeldItem(EnumHand.MAIN_HAND).getCount(), "and uses none");
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        click(c, p, o);
        c.eq(b("weathered_cut_copper"), c.world.getBlockState(o).getBlock(), "an axe takes the wax off");
        c.ok(Advancements.has(p, "husbandry/wax_off"), "Wax Off granted");
        click(c, p, o);
        c.eq(b("exposed_cut_copper"), c.world.getBlockState(o).getBlock(), "then scrapes a stage");
        click(c, p, o);
        click(c, p, o);
        c.eq(b("cut_copper"), c.world.getBlockState(o).getBlock(), "unaffected copper cannot be scraped further");
        c.eq(3, p.getHeldItem(EnumHand.MAIN_HAND).getItemDamage(), "the axe lost one durability per use");
        IBlockState stairs = b("oxidized_cut_copper_stairs").getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.WEST);
        c.world.setBlockState(o, stairs, 2);
        click(c, p, o);
        c.eq(EnumFacing.WEST, c.world.getBlockState(o).getValue(BlockStairs.FACING), "scraped stairs keep their facing");
        c.eq(b("weathered_cut_copper_stairs"), c.world.getBlockState(o).getBlock(), "stairs scrape");
        c.world.setBlockState(o, Blocks.STONE.getDefaultState(), 2);
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.PAPER));
        click(c, p, o);
        c.eq(1, p.getHeldItem(EnumHand.MAIN_HAND).getCount(), "honeycomb on stone does nothing");
        c.ok(Weathering.isWax(new ItemStack(Items.PAPER)) && !Weathering.isWax(new ItemStack(Items.STICK)), "wax items by ore name");
    }

    private void lightning(Check c) throws Exception {
        this.lightningArea = c.area();
        BlockPos o = this.lightningArea.add(8, 1, 8);
        // A rod on a weathered copper block with a patch of copper around.
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                c.world.setBlockState(o.add(dx, 0, dz), b("oxidized_copper").getDefaultState(), 2);
            }
        }
        BlockPos rod = o.up();
        c.world.setBlockState(rod, b("lightning_rod").getDefaultState(), 3);
        c.ok(c.world.getTileEntity(rod) instanceof BlockLightningRod.TileEntityLightningRod, "the rod has its marker");
        // Rain over the test area: the rod must have rain on it, so its column is made plains.
        java.util.Arrays.fill(c.world.getChunk(rod).getBiomeArray(), (byte) 1);
        // With Roughly Enough IDs the line above fills a throwaway copy; this fills its live int array.
        java.util.Arrays.fill(com.exiledradio.cavesandcliffsbackport.height.IdsCompat.intBiomes(c.world.getChunk(rod)), 1);
        c.world.getWorldInfo().setRaining(true);
        c.world.getWorldInfo().setThundering(true);
        for (int i = 0; i < 400; i++) {
            c.world.updateWeatherBody();
        }
        boolean rain = c.world.isRainingAt(rod.up());
        c.ok(rain, "it rains on the rod (plains-like biome at the test area)");
        BlockPos strike = o.add(40, 0, 10);
        c.eq(rod, Lightning.findRod(c.world, strike), "the nearest rod is found");
        c.eq(null, Lightning.findRod(c.world, o.add(200, 0, 0)), "no rod beyond 128 blocks");
        EntityVillager villager = new EntityVillager(c.world);
        villager.setPosition(o.getX() + 10, o.getY() + 1, o.getZ());
        c.world.spawnEntity(villager);
        EntityPlayerMP watcher = Players.real(c.world, "surge");
        watcher.setPosition(o.getX() + 5, o.getY() + 1, o.getZ());
        c.world.spawnEntity(watcher);
        Field natural = Lightning.class.getDeclaredField("forceNatural");
        natural.setAccessible(true);
        natural.setBoolean(null, true);
        try {
            c.world.addWeatherEffect(new EntityLightningBolt(c.world, strike.getX(), strike.getY() + 1, strike.getZ(), false));
        } finally {
            natural.setBoolean(null, false);
        }
        IBlockState r = c.world.getBlockState(rod);
        c.eq(true, r.getValue(BlockLightningRod.POWERED), "the struck rod is powered");
        c.eq(15, c.world.getRedstonePower(rod, EnumFacing.EAST), "full signal to the side");
        c.eq(15, c.world.getStrongPower(rod, EnumFacing.UP), "strong power into the block it stands on");
        c.eq(0, c.world.getStrongPower(rod, EnumFacing.EAST), "no strong power sideways");
        c.ok(c.world.isBlockPowered(o), "the copper under the rod is powered");
        // 1.21.9: the bolt cleans the rod it struck (copper now), and its walks reach the copper under it.
        c.eq(b("lightning_rod"), c.world.getBlockState(rod).getBlock(), "the struck rod is unaffected");
        int cleaned = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (c.world.getBlockState(o.add(dx, 0, dz)).getBlock() != b("oxidized_copper")) {
                    cleaned++;
                }
            }
        }
        c.ok(cleaned > 0, "the random walks from the rod scraped the copper around it: " + cleaned);
        boolean bolt = false;
        for (net.minecraft.entity.Entity e : c.world.weatherEffects) {
            if (e instanceof EntityLightningBolt && Math.abs(e.posX - (rod.getX() + 0.5)) < 0.01 && Math.abs(e.posZ - (rod.getZ() + 0.5)) < 0.01) {
                bolt = true;
            }
            if (e instanceof EntityLightningBolt && Math.abs(e.posX - strike.getX()) < 0.01 && Math.abs(e.posZ - strike.getZ()) < 0.01) {
                c.fail("the original bolt was not cancelled");
            }
        }
        c.ok(bolt, "a bolt struck above the rod");
        c.ok(Advancements.has(watcher, "adventure/lightning_rod_with_villager_no_fire"), "Surge Protector granted");
        // The signal ends after 8 ticks.
        r.getBlock().updateTick(c.world, rod, c.world.getBlockState(rod), new Random());
        c.eq(false, c.world.getBlockState(rod).getValue(BlockLightningRod.POWERED), "the rod switches off");
        // A command bolt is not redirected, but still cleans what it hits.
        c.world.setBlockState(rod, Blocks.AIR.getDefaultState(), 3);
        c.world.setBlockState(o, b("weathered_copper").getDefaultState(), 2);
        c.world.addWeatherEffect(new EntityLightningBolt(c.world, o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5, false));
        c.eq(b("copper_block"), c.world.getBlockState(o).getBlock(), "a bolt straight onto copper cleans it");
        villager.setDead();
        c.world.removeEntity(watcher);
        c.world.getWorldInfo().setRaining(false);
        c.world.getWorldInfo().setThundering(false);
        // Rod placement against a rod pointing the same way continues the other way.
        IBlockState placed = b("lightning_rod").getStateForPlacement(c.world, o.up(2), EnumFacing.UP, 0, 0, 0, 0, null);
        c.eq(EnumFacing.UP, placed.getValue(BlockDirectional.FACING), "placed on a floor it points up");
    }

    /** 1.21.9: lightning rods oxidize, take wax, scrape, and a bolt cleans an unwaxed one. */
    private void rods(Check c) {
        String[] stages = {"lightning_rod", "exposed_lightning_rod", "weathered_lightning_rod", "oxidized_lightning_rod"};
        for (int i = 0; i < 4; i++) {
            Block rod = b(stages[i]);
            Block waxed = b("waxed_" + stages[i]);
            c.ok(rod instanceof BlockLightningRod && waxed instanceof BlockLightningRod, "rods registered: " + stages[i]);
            c.ok(Item.getItemFromBlock(rod) != Items.AIR && Item.getItemFromBlock(waxed) != Items.AIR, "rod items: " + stages[i]);
            c.eq(waxed, Weathering.waxedOf(rod), "rod wax pair " + stages[i]);
            c.eq(i, Weathering.stage(rod), "rod stage " + stages[i]);
            c.eq(-1, Weathering.stage(waxed), "a waxed rod is not aging copper");
            c.eq(i < 3 ? b(stages[i + 1]) : null, Weathering.next(rod), "rod chain after " + stages[i]);
            c.eq(i < 3, rod.getTickRandomly(), "rod ticks while it can age: " + stages[i]);
            c.eq(false, waxed.getTickRandomly(), "waxed rod does not tick");
            c.eq(1, rod.getHarvestLevel(rod.getDefaultState()), "rods need a stone pickaxe");
        }
        // The lightning test's area, far side: a new area would move every later test's place.
        BlockPos o = this.lightningArea.add(24, 1, 24);
        ItemStack stone = new ItemStack(Items.STONE_PICKAXE);
        c.eq(1, harvest(c, o, b("waxed_weathered_lightning_rod").getDefaultState(), stone,
                Item.getItemFromBlock(b("waxed_weathered_lightning_rod"))), "a waxed weathered rod drops itself");
        // A lone rod ages through its stages on random ticks, keeping its facing.
        c.world.setBlockState(o, b("lightning_rod").getDefaultState().withProperty(BlockDirectional.FACING, EnumFacing.NORTH), 2);
        Random rand = new Random(5);
        for (int i = 0; i < 20000 && c.world.getBlockState(o).getBlock() != b("oxidized_lightning_rod"); i++) {
            IBlockState s = c.world.getBlockState(o);
            s.getBlock().randomTick(c.world, o, s, rand);
        }
        c.eq(b("oxidized_lightning_rod"), c.world.getBlockState(o).getBlock(), "a lone rod oxidizes fully");
        c.eq(EnumFacing.NORTH, c.world.getBlockState(o).getValue(BlockDirectional.FACING), "an oxidized rod keeps its facing");
        c.ok(c.world.getTileEntity(o) instanceof BlockLightningRod.TileEntityLightningRod, "an oxidized rod keeps a marker");
        // A random tick never switches a powered rod off early (1.12's default random tick runs updateTick).
        c.world.setBlockState(o, b("waxed_lightning_rod").getDefaultState().withProperty(BlockLightningRod.POWERED, true), 2);
        b("waxed_lightning_rod").randomTick(c.world, o, c.world.getBlockState(o), rand);
        c.eq(true, c.world.getBlockState(o).getValue(BlockLightningRod.POWERED), "a random tick leaves a powered rod on");
        // Honeycomb and axes, as on other copper.
        EntityPlayerMP p = Players.real(c.world, "rodwax");
        p.interactionManager.setGameType(GameType.SURVIVAL);
        OreDictionary.registerOre("honeycomb", Items.PAPER);
        c.world.setBlockState(o, b("exposed_lightning_rod").getDefaultState().withProperty(BlockDirectional.FACING, EnumFacing.EAST), 2);
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.PAPER, 2));
        click(c, p, o);
        c.eq(b("waxed_exposed_lightning_rod"), c.world.getBlockState(o).getBlock(), "honeycomb waxes a rod");
        c.eq(EnumFacing.EAST, c.world.getBlockState(o).getValue(BlockDirectional.FACING), "a waxed rod keeps its facing");
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        click(c, p, o);
        c.eq(b("exposed_lightning_rod"), c.world.getBlockState(o).getBlock(), "an axe takes a rod's wax off");
        click(c, p, o);
        c.eq(b("lightning_rod"), c.world.getBlockState(o).getBlock(), "then scrapes the rod a stage");
        // A rod lit when it changes keeps its unpowering tick.
        c.world.setBlockState(o, b("exposed_lightning_rod").getDefaultState().withProperty(BlockLightningRod.POWERED, true), 3);
        c.ok(c.world.isUpdateScheduled(o, b("exposed_lightning_rod")), "a lit rod placed schedules its switch-off");
        c.world.setBlockState(o, Weathering.copy(c.world.getBlockState(o), b("weathered_lightning_rod")), 3);
        c.ok(c.world.isUpdateScheduled(o, b("weathered_lightning_rod")), "a lit rod that ages keeps its switch-off");
        // Lightning: an unwaxed rod goes back to unaffected; a waxed one stays, but still powers.
        c.fill(o.add(-2, -1, -2), o.add(2, -1, 2), Blocks.STONE.getDefaultState());
        c.world.setBlockState(o, b("oxidized_lightning_rod").getDefaultState(), 3);
        c.world.addWeatherEffect(new EntityLightningBolt(c.world, o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5, false));
        c.eq(b("lightning_rod"), c.world.getBlockState(o).getBlock(), "a bolt cleans an oxidized rod to unaffected");
        c.eq(true, c.world.getBlockState(o).getValue(BlockLightningRod.POWERED), "and powers it");
        c.world.setBlockState(o, b("waxed_oxidized_lightning_rod").getDefaultState(), 3);
        c.world.addWeatherEffect(new EntityLightningBolt(c.world, o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5, false));
        c.eq(b("waxed_oxidized_lightning_rod"), c.world.getBlockState(o).getBlock(), "a bolt leaves a waxed rod as it is");
        c.eq(true, c.world.getBlockState(o).getValue(BlockLightningRod.POWERED), "a waxed oxidized rod still powers");
        for (net.minecraft.entity.Entity e : new java.util.ArrayList<net.minecraft.entity.Entity>(c.world.weatherEffects)) {
            e.setDead();
        }
        c.fill(o.add(-2, -1, -2), o.add(2, 2, 2), Blocks.AIR.getDefaultState());
        // Waxing a rod in the grid.
        Crafting.expect(c, Crafting.grid(2, 1, new ItemStack(b("weathered_lightning_rod")), new ItemStack(Items.PAPER)),
                new ItemStack(b("waxed_weathered_lightning_rod")), "waxing a rod in the grid");
    }

    /** 1.17's stonecutting: every stage, waxed and not, to its own cuts; through V&P's stonecutter when installed. */
    private void stonecutting(Check c) throws Exception {
        java.util.List<String> cuts = com.exiledradio.cavesandcliffsbackport.common.Compat.stonecutting();
        String m = "cavesandcliffsbackport:";
        c.eq(40, new java.util.HashSet<String>(cuts).size(), "5 cuts for each of 8 copper blocks");
        for (String want : new String[] {"copper_block=" + m + "cut_copper*4", "copper_block=" + m + "cut_copper_slab*8",
                "exposed_copper=" + m + "exposed_cut_copper_stairs*4", "waxed_oxidized_copper=" + m + "waxed_oxidized_cut_copper*4",
                "weathered_cut_copper=" + m + "weathered_cut_copper_stairs", "waxed_exposed_cut_copper=" + m + "waxed_exposed_cut_copper_slab*2"}) {
            c.ok(cuts.contains(m + want), "stonecutting " + want);
        }
        for (String cut : cuts) {
            String[] io = cut.split("=");
            c.ok(io[0].contains("waxed_") == io[1].contains("waxed_"), "waxed copper cuts to waxed copper: " + cut);
            for (String s : new String[] {"exposed_", "weathered_", "oxidized_"}) {
                c.ok(io[0].contains(s) == io[1].contains(s), "a stage cuts to the same stage: " + cut);
            }
            for (String side : io) {
                String path = side.substring(m.length()).replaceAll("[*][0-9]+$", "");
                c.ok(b(path) != null, "a cut names a block of ours: " + path);
            }
        }
        if (net.minecraftforge.fml.common.Loader.isModLoaded("villagerbackport")) {
            Class<?> kind = Class.forName("com.exiledradio.villagerbackport.block.CutterKind");
            Object table = Class.forName("com.exiledradio.villagerbackport.block.StonecutterRecipes").getMethod("of", kind)
                    .invoke(null, kind.getField("STONE").get(null));
            java.lang.reflect.Method results = table.getClass().getMethod("resultsFor", ItemStack.class);
            @SuppressWarnings("unchecked")
            java.util.List<ItemStack> fromBlock = (java.util.List<ItemStack>) results.invoke(table, new ItemStack(b("waxed_weathered_copper")));
            boolean four = false, eight = false;
            for (ItemStack s : fromBlock) {
                four |= s.getItem() == Item.getItemFromBlock(b("waxed_weathered_cut_copper")) && s.getCount() == 4;
                eight |= s.getItem() == Item.getItemFromBlock(b("waxed_weathered_cut_copper_slab")) && s.getCount() == 8;
            }
            c.ok(four && eight, "V&P's stonecutter cuts waxed weathered copper into 4 cut copper and 8 slabs: " + fromBlock);
        }
    }

    private void recipes(Check c) {
        ItemStack ingot = new ItemStack(CopperContent.copperIngot);
        Crafting.expect(c, Crafting.grid(3, 3, ingot, ingot, ingot, ingot, ingot, ingot, ingot, ingot, ingot), new ItemStack(b("copper_block")), "copper block");
        Crafting.expect(c, Crafting.grid(1, 1, new ItemStack(b("waxed_copper_block"))), new ItemStack(CopperContent.copperIngot, 9), "ingots from waxed block");
        ItemStack ex = new ItemStack(b("exposed_copper"));
        Crafting.expect(c, Crafting.grid(2, 2, ex, ex, ex, ex), new ItemStack(b("exposed_cut_copper"), 4), "exposed cut copper");
        ItemStack cut = new ItemStack(b("waxed_cut_copper"));
        Crafting.expect(c, Crafting.grid(3, 1, cut, cut, cut), new ItemStack(b("waxed_cut_copper_slab"), 6), "waxed slabs");
        Crafting.expect(c, Crafting.grid(1, 3, ingot, ingot, ingot), new ItemStack(b("lightning_rod")), "lightning rod");
        Crafting.expect(c, Crafting.grid(2, 1, new ItemStack(b("oxidized_cut_copper_stairs")), new ItemStack(Items.PAPER)),
                new ItemStack(b("waxed_oxidized_cut_copper_stairs")), "waxing in the grid");
        FurnaceRecipes f = FurnaceRecipes.instance();
        c.ok(Crafting.same(f.getSmeltingResult(new ItemStack(CopperContent.rawCopper)), ingot), "smelt raw copper");
        c.ok(Crafting.same(f.getSmeltingResult(new ItemStack(b("copper_ore"))), ingot), "smelt copper ore");
    }

    private void generation(Check c) {
        BlockPos o = c.area();
        c.fill(o.add(0, -40, 0), o.add(15, -20, 15), Blocks.STONE.getDefaultState());
        int placed = CopperOreGenerator.vein(c.world, new Random(3), o.add(8, -30, 8), 10);
        c.ok(placed >= 1 && placed <= 20, "a vein of about 10: " + placed);
        // Same size as vanilla's own ore veins of that size, in solid stone.
        int ours = 0, theirs = 0;
        Random rand = new Random(11);
        for (int i = 0; i < 60; i++) {
            c.fill(o.add(0, -40, 0), o.add(15, -20, 15), Blocks.STONE.getDefaultState());
            ours += CopperOreGenerator.vein(c.world, rand, o.add(8, -30, 8), 10);
            c.fill(o.add(0, -40, 0), o.add(15, -20, 15), Blocks.STONE.getDefaultState());
            // vanilla centers its vein 8 blocks east and south of the position it is given
            new net.minecraft.world.gen.feature.WorldGenMinable(Blocks.GOLD_ORE.getDefaultState(), 10).generate(c.world, rand, o.add(0, -29, 0));
            for (BlockPos q : BlockPos.getAllInBox(o.add(0, -40, 0), o.add(15, -20, 15))) {
                if (c.world.getBlockState(q).getBlock() == Blocks.GOLD_ORE) {
                    theirs++;
                }
            }
        }
        com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport.LOGGER.info("SELFTEST vein size: ours {} vanilla {} per vein", ours / 60.0, theirs / 60.0);
        c.ok(Math.abs(ours - theirs) < theirs * 0.3, "our veins are vanilla-sized: " + ours / 60.0 + " vs " + theirs / 60.0);
        // Real chunks: count copper ore in a fresh area.
        // A fixed place, so a run repeats; 25 chunks, so the count is not at the mercy of one cave system.
        int base = 9000;
        // Without the dripstone caves' extra copper, which a third of all places would otherwise add to the count.
        int extra = com.exiledradio.cavesandcliffsbackport.ModConfig.dripstone.extraCopperVeins;
        com.exiledradio.cavesandcliffsbackport.ModConfig.dripstone.extraCopperVeins = 0;
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                c.world.getChunkProvider().provideChunk(base + x, base + z);
            }
        }
        com.exiledradio.cavesandcliffsbackport.ModConfig.dripstone.extraCopperVeins = extra;
        int ore = 0, deepOre = 0;
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                net.minecraft.world.chunk.Chunk ch = c.world.getChunk(base + x, base + z);
                for (int bx = 0; bx < 16; bx++) {
                    for (int bz = 0; bz < 16; bz++) {
                        for (int y = 1; y < 100; y++) {
                            Block blk = ch.getBlockState(bx, y, bz).getBlock();
                            if (blk == b("copper_ore")) {
                                ore++;
                            } else if (blk == b("deepslate_copper_ore")) {
                                deepOre++;
                            }
                        }
                    }
                }
            }
        }
        com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport.LOGGER.info("SELFTEST copper generation: {} copper ore, {} deepslate copper ore in 25 chunks", ore, deepOre);
        // 6 veins of about 7 blocks, between y 1 and 96; those above the ground or in caves place nothing.
        c.ok(ore + deepOre > 25 * 15 && ore + deepOre < 25 * 60, "about 6 veins per chunk: " + (ore + deepOre));
        c.ok(deepOre > 0, "some veins in the deepslate layer became deepslate copper ore: " + deepOre);
        c.ok(CopperStage.values().length == 4, "four stages");
    }
}
