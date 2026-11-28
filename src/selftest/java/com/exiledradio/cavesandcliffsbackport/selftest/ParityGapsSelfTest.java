package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.backportcore.trade.TradeRegistry;
import com.exiledradio.cavesandcliffsbackport.common.Advancements;
import com.exiledradio.cavesandcliffsbackport.common.VanillaAdvancements;
import com.exiledradio.cavesandcliffsbackport.copper.CopperContent;
import com.exiledradio.cavesandcliffsbackport.copper.CopperDispense;
import com.exiledradio.cavesandcliffsbackport.copper.PacketCopperParticles;
import com.exiledradio.cavesandcliffsbackport.lush.BlockCaveVines;
import com.exiledradio.cavesandcliffsbackport.lush.LushCaveGenerator;
import com.exiledradio.cavesandcliffsbackport.lush.LushContent;
import com.exiledradio.cavesandcliffsbackport.powdersnow.PowderSnowContent;
import com.exiledradio.cavesandcliffsbackport.powdersnow.PowderSnowEvents;
import com.exiledradio.cavesandcliffsbackport.powdersnow.Freezing;
import com.exiledradio.cavesandcliffsbackport.spawning.SquidSpawns;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.advancements.Advancement;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDispenser;
import net.minecraft.block.BlockSourceImpl;
import net.minecraft.block.state.IBlockState;
import net.minecraft.dispenser.IBehaviorDispenseItem;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntitySmallFireball;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerMerchant;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.oredict.OreDictionary;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * The xisumavoid audit's medium Caves &amp; Cliffs gaps (1.1.8): vanilla advancements of 1.17 and 1.18, the wandering
 * trader's lush caves items, azaleas as saplings and bee flowers, endermen and moss, the small dripleaf's offset,
 * generated cave vines' age, parrots and fireballs in powder snow, squid spawn heights, dispensers waxing copper and the
 * wax particles' packet, Channeling tridents on lightning rods. Last in the list, so no earlier test moves.
 */
public final class ParityGapsSelfTest implements SelfTest {

    @Override
    public String name() {
        return "paritygaps";
    }

    @Override
    public void run(Check c) throws Exception {
        advancements(c);
        starTrader(c);
        lush(c);
        powderSnow(c);
        squid(c);
        dispenserWax(c);
        channeling(c);
    }

    private static Advancement adv(Check c, String ns, String path) {
        return c.world.getMinecraftServer().getAdvancementManager().getAdvancement(new net.minecraft.util.ResourceLocation(ns, path));
    }

    private void advancements(Check c) {
        VanillaAdvancements.check(c.world.getMinecraftServer());
        Advancement two = adv(c, "minecraft", "husbandry/bred_all_animals");
        c.ok(two != null && two.getCriteria().containsKey(VanillaAdvancements.AXOLOTL_CRITERION)
                && two.getCriteria().containsKey(VanillaAdvancements.GOAT_CRITERION), "Two by Two needs an axolotl and a goat (1.17)");
        boolean required = false;
        for (String[] r : two.getRequirements()) {
            required |= r.length == 1 && r[0].equals(VanillaAdvancements.GOAT_CRITERION);
        }
        c.ok(required, "each as a requirement of its own");
        Advancement diet = adv(c, "minecraft", "husbandry/balanced_diet");
        c.ok(diet != null && diet.getCriteria().containsKey(VanillaAdvancements.GLOW_BERRIES_CRITERION), "A Balanced Diet needs glow berries (1.17)");
        int before = two.getCriteria().size();
        VanillaAdvancements.check(c.world.getMinecraftServer());
        c.eq(before, two.getCriteria().size(), "a second check adds nothing");
        Advancement music = adv(c, "cavesandcliffsbackport", VanillaAdvancements.SOUND_OF_MUSIC);
        c.ok(music != null && music.getParent() != null && "minecraft:adventure/sleep_in_bed".equals(music.getParent().getId().toString()),
                "Sound of Music is under Sweet Dreams");
        Advancement star = adv(c, "cavesandcliffsbackport", VanillaAdvancements.STAR_TRADER);
        c.ok(star != null && star.getParent() != null && "minecraft:adventure/trade".equals(star.getParent().getId().toString()),
                "Star Trader is under What a Deal!");
        // Sound of Music: only in 1.18's meadow; a Default world has none.
        BlockPos at = c.area().up();
        c.world.setBlockState(at, Blocks.JUKEBOX.getDefaultState(), 2);
        EntityPlayerMP p = Players.real(c.world, "music");
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.RECORD_CAT));
        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(p, EnumHand.MAIN_HAND, at, EnumFacing.UP, new Vec3d(at)));
        boolean meadow = VanillaAdvancements.inMeadow(c.world, at);
        c.eq(meadow, Advancements.has(p, VanillaAdvancements.SOUND_OF_MUSIC), "a disc in a jukebox grants Sound of Music only in a meadow");
        c.world.setBlockState(at, Blocks.AIR.getDefaultState(), 2);
    }

    /** A trade seen as a rise in the open screen's uses; granted only at y 319 or above. */
    private void starTrader(Check c) {
        BlockPos at = c.area().up();
        EntityPlayerMP p = Players.real(c.world, "startrader");
        EntityVillager villager = new EntityVillager(c.world, 0);
        villager.setPosition(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        MerchantRecipeList list = villager.getRecipes(p);
        c.ok(list != null && !list.isEmpty(), "the villager has offers");
        for (double y : new double[] {at.getY(), VanillaAdvancements.STAR_TRADER_Y}) {
            p.setPosition(at.getX() + 0.5, y, at.getZ() + 0.5);
            p.openContainer = new ContainerMerchant(p.inventory, villager, c.world);
            tick(p);
            tick(p);
            c.ok(!Advancements.has(p, VanillaAdvancements.STAR_TRADER), "no trade, no Star Trader (y " + y + ")");
            MerchantRecipe r = list.get(0);
            r.incrementToolUses();
            tick(p);
            boolean high = y >= VanillaAdvancements.STAR_TRADER_Y;
            c.eq(high, Advancements.has(p, VanillaAdvancements.STAR_TRADER), "a trade at y " + y + (high ? " grants" : " does not grant") + " Star Trader");
        }
        p.openContainer = p.inventoryContainer;
    }

    private static void tick(EntityPlayerMP p) {
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, p));
    }

    private void lush(Check c) throws Exception {
        java.util.Set<Item> sold = new java.util.HashSet<Item>();
        for (TradeRegistry.TraderTrade t : TradeRegistry.traderTrades(TradeRegistry.TraderPool.ORDINARY)) {
            MerchantRecipe r = t.offer(new Random(1));
            if (r != null && r.getItemToBuy().getItem() == Items.EMERALD && r.getItemToSell().getCount() == 2 && r.getMaxTradeUses() == 5) {
                sold.add(r.getItemToSell().getItem());
            }
        }
        for (Block b : new Block[] {LushContent.smallDripleaf, LushContent.rootedDirt, LushContent.moss}) {
            c.ok(sold.contains(Item.getItemFromBlock(b)), "the wandering trader sells 2 " + b.getRegistryName() + " for an emerald, 5 times");
        }
        int sapling = OreDictionary.getOreID("treeSapling");
        for (Block b : new Block[] {LushContent.azalea, LushContent.floweringAzalea}) {
            boolean has = false;
            for (int id : OreDictionary.getOreIDs(new ItemStack(b))) {
                has |= id == sapling;
            }
            c.ok(has, b.getRegistryName() + " is a treeSapling (1.17's saplings tag)");
        }
        // EntityEnderman's carriable set, through a fresh enderman: it would pick up what is in the set.
        java.lang.reflect.Field carriable = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(EntityEnderman.class, "CARRIABLE_BLOCKS", "field_70827_d");
        @SuppressWarnings("unchecked")
        java.util.Set<Block> set = (java.util.Set<Block>) carriable.get(null);
        c.ok(set.contains(LushContent.moss) && set.contains(LushContent.rootedDirt), "endermen carry moss blocks and rooted dirt");
        c.eq(Block.EnumOffsetType.XYZ, LushContent.smallDripleaf.getOffsetType(), "small dripleaves are offset as 1.17's (XYZ)");
        BlockPos o = c.area().up(10);
        IBlockState lower = LushContent.smallDripleaf.getDefaultState();
        Vec3d off = lower.getOffset(c.world, o);
        c.ok(off.y <= 0 && off.y >= -0.2 && Math.abs(off.x) <= 0.25, "an offset of at most a quarter sideways, a fifth down: " + off);
        c.eq(off, lower.getOffset(c.world, o.up()), "the same for the upper half");
        // Generated cave vines: tips at age 5-7 of 7, 1.18.2's 23-25 of 25.
        c.fill(o.add(-1, 0, -1), o.add(1, 20, 1), Blocks.AIR.getDefaultState());
        c.world.setBlockState(o.up(20), Blocks.STONE.getDefaultState(), 2);
        Random rand = new Random(4);
        int tips = 0, low = 0;
        for (int i = 0; i < 60; i++) {
            c.fill(o, o.up(19), Blocks.AIR.getDefaultState());
            LushCaveGenerator.vines(c.world, rand, o.up(19));
            for (int y = 0; y < 20; y++) {
                IBlockState s = c.world.getBlockState(o.up(y));
                if (s.getBlock() == LushContent.caveVines) {
                    tips++;
                    low += s.getValue(BlockCaveVines.AGE) < BlockCaveVines.MAX_AGE - 2 ? 1 : 0;
                }
            }
        }
        c.ok(tips > 0 && low == 0, "generated vine tips are 5-7 of 7: " + tips + " tips, " + low + " younger");
        java.lang.reflect.Method special = Class.forName("com.exiledradio.cavesandcliffsbackport.terrain.StateMap")
                .getDeclaredMethod("special", String.class, Map.class);
        special.setAccessible(true);
        for (int age = 23; age <= 25; age++) {
            Map<String, String> props = new HashMap<String, String>();
            props.put("age", String.valueOf(age));
            props.put("berries", "true");
            IBlockState s = (IBlockState) special.invoke(null, "cave_vines", props);
            c.eq(BlockCaveVines.MAX_AGE - (25 - age), s == null ? -1 : s.getValue(BlockCaveVines.AGE), "1.18 terrain's cave_vines age " + age);
            c.ok(s != null && s.getValue(BlockCaveVines.BERRIES), "keeps its berries");
        }
        if (Loader.isModLoaded("updateaquaticbackport")) {
            net.minecraft.world.storage.loot.LootPool ship = c.world.getLootTableManager()
                    .getLootTableFromLocation(com.exiledradio.cavesandcliffsbackport.lush.LushLoot.SHIPWRECK_SUPPLY).getPool("pool0");
            c.ok(ship != null && ship.getEntry(com.exiledradio.cavesandcliffsbackport.lush.LushLoot.MOSS_ENTRY) != null
                    && ship.getEntry("minecraft:moss_block") == null, "Update Aquatic: its shipwreck supply chest has moss, not the placeholder");
        }
        if (Loader.isModLoaded("buzzybeesbackport")) {
            Class<?> api = Class.forName("com.exiledradio.buzzybeesbackport.api.BeeFlowers");
            c.ok((Boolean) api.getMethod("isFlower", IBlockState.class).invoke(null, LushContent.floweringAzalea.getDefaultState()),
                    "Buzzy Bees: a flowering azalea is a flower");
            c.ok((Boolean) api.getMethod("isFlowerItem", ItemStack.class).invoke(null, new ItemStack(LushContent.floweringLeaves)),
                    "Buzzy Bees: flowering azalea leaves breed bees");
            IBlockState vine = LushContent.caveVinesPlant.getDefaultState().withProperty(BlockCaveVines.BERRIES, false);
            IBlockState grown = (IBlockState) api.getMethod("grow", IBlockState.class).invoke(null, vine);
            c.ok(grown != null && grown.getValue(BlockCaveVines.BERRIES), "Buzzy Bees: a bee gives cave vines berries");
        }
    }

    private void powderSnow(Check c) {
        BlockPos o = c.area().up();
        // Parrots leave the shoulders of a player in powder snow.
        EntityPlayerMP p = Players.real(c.world, "parrots");
        p.setPosition(o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
        NBTTagCompound parrot = new NBTTagCompound();
        parrot.setString("id", "minecraft:parrot");
        java.lang.reflect.Method shoulder = net.minecraftforge.fml.common.ObfuscationReflectionHelper.findMethod(
                net.minecraft.entity.player.EntityPlayer.class, "func_192029_h", void.class, NBTTagCompound.class);
        try {
            shoulder.invoke(p, parrot);
        } catch (ReflectiveOperationException e) {
            c.fail("could not put a parrot on the shoulder: " + e);
            return;
        }
        c.ok(!p.getLeftShoulderEntity().isEmpty(), "a parrot on the shoulder");
        PowderSnowEvents.dropShoulderParrots(p);
        c.ok(!p.getLeftShoulderEntity().isEmpty(), "out of powder snow it stays");
        Freezing.markInSnow(p);
        PowderSnowEvents.dropShoulderParrots(p);
        c.ok(p.getLeftShoulderEntity().isEmpty(), "in powder snow it leaves the shoulder (1.17)");
        // A fire charge flies through powder snow and melts it.
        BlockPos snow = o.add(4, 0, 4);
        c.world.setBlockState(snow, PowderSnowContent.powderSnow.getDefaultState(), 2);
        EntitySmallFireball ball = new EntitySmallFireball(c.world, snow.getX() + 0.5, snow.getY() + 2.0, snow.getZ() + 0.5, 0, -1, 0);
        RayTraceResult hit = new RayTraceResult(new Vec3d(snow.getX() + 0.5, snow.getY() + 1.0, snow.getZ() + 0.5), EnumFacing.UP, snow);
        ProjectileImpactEvent.Fireball impact = new ProjectileImpactEvent.Fireball(ball, hit);
        MinecraftForge.EVENT_BUS.post(impact);
        c.ok(impact.isCanceled(), "the fireball flies on through powder snow");
        c.eq(Blocks.AIR, c.world.getBlockState(snow).getBlock(), "and melts it (mobGriefing)");
        c.world.setBlockState(snow, Blocks.STONE.getDefaultState(), 2);
        impact = new ProjectileImpactEvent.Fireball(ball, hit);
        MinecraftForge.EVENT_BUS.post(impact);
        c.ok(!impact.isCanceled(), "on stone it hits as before");
        c.world.setBlockState(snow, Blocks.AIR.getDefaultState(), 2);
    }

    private void squid(Check c) {
        BlockPos o = new BlockPos(c.area().getX() + 8, 0, c.area().getZ() + 8);
        // A column of water from y 40 to 63 with air above, like an ocean.
        for (int y = 40; y <= 63; y++) {
            c.world.setBlockState(o.up(y), Blocks.WATER.getDefaultState(), 2);
        }
        c.world.setBlockState(o.up(64), Blocks.AIR.getDefaultState(), 2);
        c.ok(!SquidSpawns.canSpawnAt(c.world, o.up(48)), "no squid at y 48 (1.12.2 allowed 46-62)");
        c.ok(SquidSpawns.canSpawnAt(c.world, o.up(50)), "squid from y 50");
        c.ok(SquidSpawns.canSpawnAt(c.world, o.up(62)), "to y 62 with water above");
        c.ok(!SquidSpawns.canSpawnAt(c.world, o.up(63)), "not at y 63, with air above");
        for (int y = 40; y <= 64; y++) {
            c.world.setBlockState(o.up(y), Blocks.AIR.getDefaultState(), 2);
        }
    }

    private void dispenserWax(Check c) {
        OreDictionary.registerOre("honeycomb", Items.PAPER);
        CopperDispense.register();
        CopperDispense.register();
        BlockPos d = c.area().up();
        BlockPos front = d.east();
        c.world.setBlockState(d, Blocks.DISPENSER.getDefaultState().withProperty(BlockDispenser.FACING, EnumFacing.EAST), 2);
        IBehaviorDispenseItem wax = BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.getObject(Items.PAPER);
        c.ok(wax instanceof CopperDispense, "honeycomb (here paper with the ore name) has the waxing behavior, once");
        BlockSourceImpl source = new BlockSourceImpl(c.world, d);
        ItemStack stack = new ItemStack(Items.PAPER, 3);
        c.world.setBlockState(front, CopperContent.BLOCKS.get("weathered_cut_copper_stairs").getDefaultState(), 2);
        wax.dispense(source, stack);
        c.eq(CopperContent.BLOCKS.get("waxed_weathered_cut_copper_stairs"), c.world.getBlockState(front).getBlock(), "a dispenser waxes the copper in front");
        c.eq(2, stack.getCount(), "using one honeycomb");
        c.world.setBlockState(front, CopperContent.BLOCKS.get("exposed_lightning_rod").getDefaultState(), 2);
        wax.dispense(source, stack);
        c.eq(CopperContent.BLOCKS.get("waxed_exposed_lightning_rod"), c.world.getBlockState(front).getBlock(), "and lightning rods");
        ItemStack before = stack.copy();
        wax.dispense(source, stack);
        c.eq(before.getCount() - 1, stack.getCount(), "on waxed copper it is dispensed as an item instead");
        c.world.setBlockState(front, Blocks.AIR.getDefaultState(), 2);
        c.world.setBlockState(d, Blocks.AIR.getDefaultState(), 2);
        // The particles' packet carries its block and kind.
        ByteBuf buf = Unpooled.buffer();
        new PacketCopperParticles(new BlockPos(-30000, -64, 29999), PacketCopperParticles.SCRAPE).toBytes(buf);
        PacketCopperParticles back = new PacketCopperParticles();
        back.fromBytes(buf);
        c.ok(back.pos.equals(new BlockPos(-30000, -64, 29999)) && back.kind == PacketCopperParticles.SCRAPE, "the particle packet round-trips");
    }

    /** A Channeling trident on a rod in a thunderstorm calls lightning; anything else does not. */
    private void channeling(Check c) throws Exception {
        WorldServer world = c.world;
        BlockPos rod = c.area().up();
        world.setBlockState(rod, CopperContent.BLOCKS.get("lightning_rod").getDefaultState(), 2);
        boolean thunder = world.getWorldInfo().isThundering();
        boolean rain = world.getWorldInfo().isRaining();
        world.getWorldInfo().setRaining(true);
        world.getWorldInfo().setThundering(true);
        for (int i = 0; i < 400; i++) {
            world.updateWeatherBody();
        }
        try {
            RayTraceResult hit = new RayTraceResult(new Vec3d(rod.getX() + 0.5, rod.getY() + 1.0, rod.getZ() + 0.5), EnumFacing.UP, rod);
            int bolts = world.weatherEffects.size();
            EntityTippedArrow arrow = new EntityTippedArrow(world, rod.getX() + 0.5, rod.getY() + 3, rod.getZ() + 0.5);
            MinecraftForge.EVENT_BUS.post(new ProjectileImpactEvent.Arrow(arrow, hit));
            c.eq(bolts, world.weatherEffects.size(), "an arrow on a rod in a thunderstorm calls nothing");
            net.minecraft.entity.Entity trident = net.minecraft.entity.EntityList.createEntityByIDFromName(
                    new net.minecraft.util.ResourceLocation("updateaquaticbackport", "trident"), world);
            if (trident instanceof net.minecraft.entity.projectile.EntityArrow) {
                ItemStack stack = new ItemStack(Item.getByNameOrId("updateaquaticbackport:trident"));
                stack.addEnchantment(net.minecraftforge.fml.common.registry.ForgeRegistries.ENCHANTMENTS.getValue(
                        new net.minecraft.util.ResourceLocation("updateaquaticbackport", "channeling")), 1);
                java.lang.reflect.Field f = trident.getClass().getDeclaredField("thrownStack");
                f.setAccessible(true);
                f.set(trident, stack);
                trident.setPosition(rod.getX() + 0.5, rod.getY() + 3, rod.getZ() + 0.5);
                MinecraftForge.EVENT_BUS.post(new ProjectileImpactEvent.Arrow((net.minecraft.entity.projectile.EntityArrow) trident, hit));
                boolean bolt = false;
                for (net.minecraft.entity.Entity e : world.weatherEffects) {
                    bolt |= e instanceof net.minecraft.entity.effect.EntityLightningBolt && Math.abs(e.posX - (rod.getX() + 0.5)) < 0.01
                            && Math.abs(e.posY - (rod.getY() + 1)) < 0.01;
                }
                c.ok(bolt, "Update Aquatic: a Channeling trident on a rod calls lightning onto it");
                c.eq(true, world.getBlockState(rod).getValue(com.exiledradio.cavesandcliffsbackport.copper.BlockLightningRod.POWERED), "and the rod powers");
            }
        } finally {
            world.getWorldInfo().setRaining(rain);
            world.getWorldInfo().setThundering(thunder);
            for (net.minecraft.entity.Entity e : new java.util.ArrayList<net.minecraft.entity.Entity>(world.weatherEffects)) {
                e.setDead();
            }
            world.setBlockState(rod, Blocks.AIR.getDefaultState(), 2);
        }
    }
}
