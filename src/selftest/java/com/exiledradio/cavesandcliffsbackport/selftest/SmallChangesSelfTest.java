package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.backportcore.waterlog.Waterlogging;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.changes.ChangesHooks;
import com.exiledradio.cavesandcliffsbackport.changes.SmallChanges;
import com.exiledradio.cavesandcliffsbackport.changes.StrongholdBurial;
import com.exiledradio.cavesandcliffsbackport.height.asm.ChangesTransformer;

import net.minecraft.block.BlockRailBase;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.ai.EntityAITempt;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.item.EntityMinecartEmpty;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.entity.passive.EntityDonkey;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityLlama;
import net.minecraft.entity.passive.EntitySkeletonHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.MapGenBase;
import net.minecraft.world.gen.structure.MapGenStronghold;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.terraingen.InitMapGenEvent;
import net.minecraftforge.event.terraingen.TerrainGen;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.lang.reflect.Method;
import java.util.Random;

/**
 * The low-impact 1.17 and 1.18 changes of the "Other Changes" page, each also with its setting off: minecarts in
 * water, shulker box items, mycelium, anvil names, absorption hearts, sleeping and the weather, horse and llama
 * tempting, the enchanting table's light, sprinting past walls and buried strongholds; and that rails take water (1.17)
 * with Fluidlogged API, which waterlogs them by its own rules (no setting of ours).
 */
public final class SmallChangesSelfTest implements SelfTest {

    @Override
    public String name() {
        return "smallchanges";
    }

    @Override
    public void run(Check c) throws Exception {
        ModConfig.Changes s = ModConfig.changes;
        boolean[] saved = {s.minecartsInWater, s.shulkerBoxContents, s.myceliumPlants, s.anvilNames, s.absorptionHearts,
                s.sleepWeather, s.horseTempting, s.enchantingTableLight, s.sprintCollisions, s.strongholdBurial};
        try {
            patches(c);
            minecarts(c);
            shulkerItems(c);
            mycelium(c);
            anvil(c);
            hearts(c);
            sleep(c);
            sleepPercentage(c);
            tempting(c);
            tableLight(c);
            sprint(c);
            rails(c);
            strongholds(c);
        } finally {
            s.minecartsInWater = saved[0];
            s.shulkerBoxContents = saved[1];
            s.myceliumPlants = saved[2];
            s.anvilNames = saved[3];
            s.absorptionHearts = saved[4];
            s.sleepWeather = saved[5];
            s.horseTempting = saved[6];
            s.enchantingTableLight = saved[7];
            s.sprintCollisions = saved[8];
            s.strongholdBurial = saved[9];
            SmallChanges.applyTableLight();
        }
    }

    private static void patches(Check c) throws java.io.IOException {
        // Loading each target makes sure the transformer saw it.
        Object[] load = {EntityMinecart.class, EntityItem.class, net.minecraft.block.BlockMycelium.class,
                net.minecraft.network.NetHandlerPlayServer.class, net.minecraft.entity.EntityLiving.class};
        c.ok(load.length == ChangesTransformer.COMMON.length, "every server target loaded");
        for (String target : ChangesTransformer.COMMON) {
            c.ok(ChangesTransformer.APPLIED.containsKey(target), "patched: " + target);
        }
        // Every target, client ones too, on Forge's SRG-named jar (a real instance's names) and ASM's stack check.
        java.io.File srg = new java.io.File(System.getProperty("user.home"),
                ".gradle/caches/minecraft/net/minecraftforge/forge/1.12.2-14.23.5.2847/stable/39/forge-1.12.2-14.23.5.2847-srgBin.jar");
        if (!c.ok(srg.isFile(), "Forge's SRG jar is in the Gradle cache")) {
            return;
        }
        java.util.zip.ZipFile zip = new java.util.zip.ZipFile(srg);
        try {
            for (String target : ChangesTransformer.targets(true)) {
                // The SRG jar is vanilla's classes without Forge's patches or Forge's own classes. The minecart's
                // sites include two methods Forge adds (getMaxSpeed, getSlopeAdjustment, unobfuscated in an instance too),
                // and GuiIngameForge is Forge's: those are checked on the dev classes (MCP names, Forge's patches) only.
                java.util.zip.ZipEntry e = target.startsWith("net.minecraftforge.") || target.equals(EntityMinecart.class.getName())
                        ? null : zip.getEntry(target.replace('.', '/') + ".class");
                java.io.InputStream in = e != null ? zip.getInputStream(e)
                        : SmallChangesSelfTest.class.getResourceAsStream("/" + target.replace('.', '/') + ".class");
                String which = e != null ? "SRG " : "dev ";
                byte[] bytes = in == null ? null : org.apache.commons.io.IOUtils.toByteArray(in);
                byte[] out = bytes == null ? null : ChangesTransformer.patchForTest(target, bytes);
                if (!c.ok(out != null, which + target + " patched")) {
                    continue;
                }
                if (e != null) {
                    // The JVM's own verifier, with Mojang's ProGuard frames (ASM's check below computes its own).
                    String error = FrameCheck.verify(srg, target, out);
                    c.ok(error == null, "SRG " + target + " passes the JVM's verifier with Mojang's frames"
                            + (error == null ? "" : ": " + error));
                }
                org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
                new org.objectweb.asm.ClassReader(out).accept(node, 0);
                for (org.objectweb.asm.tree.MethodNode m : node.methods) {
                    try {
                        new org.objectweb.asm.tree.analysis.Analyzer<org.objectweb.asm.tree.analysis.BasicValue>(
                                new org.objectweb.asm.tree.analysis.BasicVerifier()).analyze(node.name, m);
                    } catch (Exception ex) {
                        c.fail(which + target + "." + m.name + " fails ASM's stack check: " + ex);
                    }
                }
            }
        } finally {
            zip.close();
        }
    }

    // ------------------------------------------------------------------------------------------

    private static double invokeD(Object o, String mcp, String srg) throws Exception {
        Method m = ObfuscationReflectionHelper.findMethod(EntityMinecart.class, srg, double.class);
        return (Double) m.invoke(o);
    }

    private static void minecarts(Check c) throws Exception {
        WorldServer world = c.world;
        BlockPos corner = c.area();
        c.fill(corner.add(2, 0, 2), corner.add(6, 3, 6), Blocks.WATER.getDefaultState());
        EntityMinecartEmpty wet = new EntityMinecartEmpty(world, corner.getX() + 4.5, corner.getY() + 1, corner.getZ() + 4.5);
        EntityMinecartEmpty dry = new EntityMinecartEmpty(world, corner.getX() + 20.5, corner.getY(), corner.getZ() + 20.5);
        world.spawnEntity(wet);
        world.spawnEntity(dry);
        wet.onUpdate();
        dry.onUpdate();
        c.ok(wet.isInWater() && !dry.isInWater(), "one cart in water, one on dry ground");

        ModConfig.changes.minecartsInWater = true;
        c.eq(0.2D, invokeD(wet, "getMaximumSpeed", "func_174898_m"), "in water: half the top speed (4 / 20)");
        c.eq(0.4D, invokeD(dry, "getMaximumSpeed", "func_174898_m"), "dry: 8 / 20");
        c.eq(0.0078125D * 0.2D, wet.getSlopeAdjustment(), "in water: a fifth of the slope's push");
        c.eq(0.0078125D, dry.getSlopeAdjustment(), "dry: the whole push");
        c.eq(ChangesHooks.CART_WATER_GRAVITY, ChangesHooks.cartGravity(wet), "in water: sinks at 0.005 a tick");
        Method drag = ObfuscationReflectionHelper.findMethod(EntityMinecart.class, "func_94101_h", void.class);
        ModConfig.changes.minecartsInWater = false;
        wet.motionX = 0.1D;
        drag.invoke(wet);
        double before = wet.motionX;
        ModConfig.changes.minecartsInWater = true;
        wet.motionX = 0.1D;
        drag.invoke(wet);
        c.ok(Math.abs(wet.motionX - before * 0.95F) < 1e-9, "in water: slowed by 0.95 more, " + wet.motionX + " against " + before);
        // A real tick: the cart falls slowly in water.
        wet.motionY = 0;
        wet.setPosition(wet.posX, corner.getY() + 3, wet.posZ);
        double y = wet.posY;
        wet.onUpdate();
        c.ok(wet.posY < y && y - wet.posY < 0.03D, "a tick in water sinks the cart by less than in air, " + (y - wet.posY));

        ModConfig.changes.minecartsInWater = false;
        c.eq(0.4D, invokeD(wet, "getMaximumSpeed", "func_174898_m"), "setting off: full speed in water");
        c.eq(0.0078125D, wet.getSlopeAdjustment(), "setting off: the whole push in water");
        c.eq(ChangesHooks.CART_GRAVITY, ChangesHooks.cartGravity(wet), "setting off: 1.12.2's gravity");
        wet.setDead();
        dry.setDead();
    }

    // ------------------------------------------------------------------------------------------

    private static ItemStack shulkerWith(ItemStack... contents) {
        ItemStack box = new ItemStack(Blocks.PURPLE_SHULKER_BOX);
        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        for (int i = 0; i < contents.length; i++) {
            items.set(i * 3, contents[i]);
        }
        NBTTagCompound tag = new NBTTagCompound();
        ItemStackHelper.saveAllItems(tag, items);
        box.setTagInfo("BlockEntityTag", tag);
        return box;
    }

    private static int count(WorldServer world, BlockPos at, net.minecraft.item.Item item) {
        int n = 0;
        for (EntityItem e : world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(at).grow(3))) {
            if (!e.isDead && e.getItem().getItem() == item) {
                n += e.getItem().getCount();
            }
        }
        return n;
    }

    private static void shulkerItems(Check c) {
        WorldServer world = c.world;
        BlockPos at = c.area().add(8, 1, 8);
        ModConfig.changes.shulkerBoxContents = true;
        EntityItem box = new EntityItem(world, at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
                shulkerWith(new ItemStack(Items.DIAMOND, 5), new ItemStack(Items.STICK, 2)));
        world.spawnEntity(box);
        box.attackEntityFrom(DamageSource.CACTUS, 10.0F);
        c.ok(box.isDead, "the shulker box item is destroyed");
        c.eq(5, count(world, at, Items.DIAMOND), "its diamonds spill");
        c.eq(2, count(world, at, Items.STICK), "its sticks spill");
        box.attackEntityFrom(DamageSource.CACTUS, 10.0F);
        c.eq(5, count(world, at, Items.DIAMOND), "hit again once dead: nothing more spills");

        BlockPos at2 = c.area().add(8, 1, 8);
        ModConfig.changes.shulkerBoxContents = false;
        EntityItem old = new EntityItem(world, at2.getX() + 0.5, at2.getY(), at2.getZ() + 0.5,
                shulkerWith(new ItemStack(Items.EMERALD, 3)));
        world.spawnEntity(old);
        old.attackEntityFrom(DamageSource.CACTUS, 10.0F);
        c.ok(old.isDead, "setting off: destroyed too");
        c.eq(0, count(world, at2, Items.EMERALD), "setting off: its contents are lost");
    }

    // ------------------------------------------------------------------------------------------

    private static void mycelium(Check c) {
        WorldServer world = c.world;
        BlockPos corner = c.area();
        BlockPos soil = corner.add(4, 0, 4);
        BlockPos above = soil.up();
        world.setBlockState(soil, Blocks.MYCELIUM.getDefaultState(), 2);
        BlockPos cane = corner.add(10, 0, 4);
        world.setBlockState(cane, Blocks.MYCELIUM.getDefaultState(), 2);
        world.setBlockState(cane.east(), Blocks.WATER.getDefaultState(), 2);

        ModConfig.changes.myceliumPlants = true;
        c.ok(Blocks.YELLOW_FLOWER.canPlaceBlockAt(world, above), "a dandelion on mycelium");
        c.ok(Blocks.RED_FLOWER.canPlaceBlockAt(world, above), "a poppy on mycelium");
        c.ok(Blocks.SAPLING.canPlaceBlockAt(world, above), "a sapling on mycelium");
        c.ok(Blocks.TALLGRASS.canPlaceBlockAt(world, above), "grass on mycelium");
        c.ok(Blocks.DOUBLE_PLANT.canPlaceBlockAt(world, above), "a tall plant on mycelium");
        c.ok(Blocks.DEADBUSH.canPlaceBlockAt(world, above), "a dead bush on mycelium");
        c.ok(Blocks.REEDS.canPlaceBlockAt(world, cane.up()), "sugar cane on mycelium by water");
        c.ok(!Blocks.REEDS.canPlaceBlockAt(world, above), "... not away from water");
        c.ok(Blocks.BROWN_MUSHROOM.canPlaceBlockAt(world, above), "mushrooms still");
        c.ok(!Blocks.WHEAT.canPlaceBlockAt(world, above), "no wheat: crops need farmland");
        c.ok(!Blocks.MYCELIUM.canSustainPlant(Blocks.MYCELIUM.getDefaultState(), world, soil, EnumFacing.UP,
                (net.minecraftforge.common.IPlantable) Items.PUMPKIN_SEEDS), "no pumpkin seeds: stems need farmland");
        world.setBlockState(above, Blocks.PUMPKIN_STEM.getDefaultState(), 2);
        c.ok(!((net.minecraft.block.BlockBush) Blocks.PUMPKIN_STEM).canBlockStay(world, above, Blocks.PUMPKIN_STEM.getDefaultState()), "a stem on mycelium does not stay");
        world.setBlockToAir(above);
        c.ok(Blocks.MYCELIUM.canSustainPlant(Blocks.MYCELIUM.getDefaultState(), world, soil, EnumFacing.UP,
                (net.minecraftforge.common.IPlantable) Blocks.PUMPKIN_STEM), "a pumpkin may grow onto mycelium");
        c.ok(!Blocks.CACTUS.canPlaceBlockAt(world, above), "no cactus");

        ModConfig.changes.myceliumPlants = false;
        c.ok(!Blocks.YELLOW_FLOWER.canPlaceBlockAt(world, above), "setting off: no dandelion");
        c.ok(!Blocks.SAPLING.canPlaceBlockAt(world, above), "setting off: no sapling");
        c.ok(Blocks.BROWN_MUSHROOM.canPlaceBlockAt(world, above), "setting off: mushrooms still");
        c.ok(!Blocks.MYCELIUM.canSustainPlant(Blocks.MYCELIUM.getDefaultState(), world, soil, EnumFacing.UP,
                (net.minecraftforge.common.IPlantable) Blocks.PUMPKIN_STEM), "setting off: no pumpkin onto mycelium");
    }

    private static void anvil(Check c) {
        ModConfig.changes.anvilNames = true;
        c.eq(50, ChangesHooks.anvilNameLimit(), "the server takes anvil names of 50 characters");
        ModConfig.changes.anvilNames = false;
        c.eq(35, ChangesHooks.anvilNameLimit(), "setting off: 35");
    }

    private static void hearts(Check c) {
        c.eq(160, ChangesHooks.absorptionU(16 + 36 + 144), "Poison: a golden absorption heart, not a blank one");
        c.eq(169, ChangesHooks.absorptionU(16 + 36 + 153), "Poison: a golden half heart");
        c.eq(124, ChangesHooks.absorptionU(16 + 72 + 144), "Wither: a withered absorption heart");
        c.eq(133, ChangesHooks.absorptionU(16 + 72 + 153), "Wither: a withered half heart");
        c.eq(160, ChangesHooks.absorptionU(16 + 144), "no effect: golden as before");
    }

    // ------------------------------------------------------------------------------------------

    /** The players wake from a night slept through: the wake events, 1.12.2's reset, the end of the tick. */
    private static void wake(WorldServer world, EntityPlayer player) {
        MinecraftForge.EVENT_BUS.post(new PlayerWakeUpEvent(player, false, false, true));
        if (world.getGameRules().getBoolean("doWeatherCycle")) {
            world.provider.resetRainAndThunder();
        }
        MinecraftForge.EVENT_BUS.post(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, world));
    }

    private static void rain(WorldServer world, float strength) {
        ObfuscationReflectionHelper.setPrivateValue(net.minecraft.world.World.class, world, strength, "field_73003_n");
        ObfuscationReflectionHelper.setPrivateValue(net.minecraft.world.World.class, world, strength, "field_73004_o");
    }

    /**
     * 1.17's playersSleepingPercentage is Backport Core 1.5.0's ({@code mechanics.SleepRules}, patches P86 and P87): with
     * this mod installed (a 1.17 backport) every world has the rule, and 1.12.2's all-asleep test follows it.
     */
    private static void sleepPercentage(Check c) {
        WorldServer world = c.world;
        String rule = com.exiledradio.backportcore.mechanics.SleepRules.GAME_RULE;
        c.ok(com.exiledradio.backportcore.mechanics.SleepRules.installed(), "Core counts this mod as a 1.17 backport");
        c.ok(world.getGameRules().hasRule(rule), "the playersSleepingPercentage game rule exists (Core's)");
        c.eq(100, world.getGameRules().getInt(rule), "it defaults to 100");
        c.eq(2, com.exiledradio.backportcore.mechanics.SleepRules.needed(3, 50), "3 players at 50%: 2");
        c.eq(1, com.exiledradio.backportcore.mechanics.SleepRules.needed(3, 0), "at 0%: still 1");
        EntityPlayerMP a = Players.real(world, "sleepA");
        EntityPlayerMP b = Players.real(world, "sleepB");
        BlockPos spawn = world.getSpawnPoint();
        a.setPosition(spawn.getX(), spawn.getY() + 1, spawn.getZ());
        b.setPosition(spawn.getX() + 2, spawn.getY() + 1, spawn.getZ());
        world.spawnEntity(a);
        world.spawnEntity(b);
        try {
            world.getGameRules().setOrCreateGameRule(rule, "50");
            asleep(a, true, 40);
            world.updateAllPlayersSleepingFlag();
            c.ok(!world.areAllPlayersAsleep(), "one of two in bed, not yet long enough: the night stays");
            asleep(a, true, 100);
            world.updateAllPlayersSleepingFlag();
            c.ok(world.areAllPlayersAsleep(), "one of two asleep at 50%: the night is skipped");
            world.getGameRules().setOrCreateGameRule(rule, "101");
            asleep(b, true, 100);
            world.updateAllPlayersSleepingFlag();
            c.ok(!world.areAllPlayersAsleep(), "above 100%: never, even with everyone asleep");
            world.getGameRules().setOrCreateGameRule(rule, "100");
            c.ok(world.areAllPlayersAsleep(), "at 100%: with everyone asleep, as 1.12.2");
        } finally {
            asleep(a, false, 0);
            asleep(b, false, 0);
            world.removeEntity(a);
            world.removeEntity(b);
            world.getGameRules().setOrCreateGameRule(rule, "100");
            world.updateAllPlayersSleepingFlag();
        }
    }

    private static void asleep(EntityPlayer player, boolean sleeping, int ticks) {
        ObfuscationReflectionHelper.setPrivateValue(EntityPlayer.class, player, sleeping, "field_71083_bS");
        ObfuscationReflectionHelper.setPrivateValue(EntityPlayer.class, player, ticks, "field_71076_b");
    }

    private static void sleep(Check c) {
        WorldServer world = c.world;
        WorldInfo info = world.getWorldInfo();
        int rainTime = info.getRainTime();
        boolean raining = info.isRaining();
        int thunderTime = info.getThunderTime();
        boolean thundering = info.isThundering();
        float rain = world.getRainStrength(1.0F);
        EntityPlayer player = c.player("sleeper", c.area());
        try {
            ModConfig.changes.sleepWeather = true;
            rain(world, 0.0F);
            info.setRaining(false);
            info.setRainTime(12345);
            info.setThunderTime(54321);
            wake(world, player);
            c.eq(12345, info.getRainTime(), "a clear night slept through keeps the rain timer");
            c.eq(54321, info.getThunderTime(), "... and the thunder timer");

            rain(world, 1.0F);
            info.setRaining(true);
            info.setRainTime(12345);
            wake(world, player);
            c.ok(!info.isRaining() && info.getRainTime() == 0, "a rainy night slept through clears the rain");

            // A player who just leaves the bed changes nothing either way.
            rain(world, 0.0F);
            info.setRaining(false);
            info.setRainTime(777);
            MinecraftForge.EVENT_BUS.post(new PlayerWakeUpEvent(player, false, true, true));
            MinecraftForge.EVENT_BUS.post(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, world));
            c.eq(777, info.getRainTime(), "leaving the bed: the weather untouched");

            ModConfig.changes.sleepWeather = false;
            info.setRainTime(12345);
            wake(world, player);
            c.eq(0, info.getRainTime(), "setting off: the clear night's timer reset as in 1.12.2");
        } finally {
            info.setRainTime(rainTime);
            info.setRaining(raining);
            info.setThunderTime(thunderTime);
            info.setThundering(thundering);
            rain(world, rain);
        }
    }

    // ------------------------------------------------------------------------------------------

    private static boolean tempted(AbstractHorse horse, net.minecraft.item.Item food) {
        for (EntityAITasks.EntityAITaskEntry e : horse.tasks.taskEntries) {
            if (e.action instanceof EntityAITempt) {
                java.util.Set<?> items = ObfuscationReflectionHelper.getPrivateValue(EntityAITempt.class, (EntityAITempt) e.action, "field_151484_k");
                return items.contains(food);
            }
        }
        return false;
    }

    private static <T extends AbstractHorse> T spawn(WorldServer world, T horse, BlockPos at) {
        horse.setPosition(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        world.spawnEntity(horse);
        return horse;
    }

    private static void tempting(Check c) {
        WorldServer world = c.world;
        BlockPos at = c.area();
        ModConfig.changes.horseTempting = true;
        c.ok(tempted(spawn(world, new EntityHorse(world), at), Items.GOLDEN_CARROT), "a horse follows a golden carrot");
        c.ok(tempted(spawn(world, new EntityDonkey(world), at), Items.GOLDEN_APPLE), "a donkey follows a golden apple");
        c.ok(tempted(spawn(world, new EntityLlama(world), at), net.minecraft.item.Item.getItemFromBlock(Blocks.HAY_BLOCK)),
                "a llama follows a hay bale");
        c.ok(!tempted(spawn(world, new EntitySkeletonHorse(world), at), Items.GOLDEN_CARROT), "a skeleton horse follows nothing");
        ModConfig.changes.horseTempting = false;
        c.ok(!tempted(spawn(world, new EntityHorse(world), at), Items.GOLDEN_CARROT), "setting off: a horse follows nothing");
        for (Entity e : world.getEntitiesWithinAABB(AbstractHorse.class, new AxisAlignedBB(at).grow(4))) {
            e.setDead();
        }
    }

    private static void tableLight(Check c) {
        IBlockState table = Blocks.ENCHANTING_TABLE.getDefaultState();
        ModConfig.changes.enchantingTableLight = true;
        SmallChanges.applyTableLight();
        c.eq(7, table.getLightValue(), "an enchanting table gives light 7");
        ModConfig.changes.enchantingTableLight = false;
        SmallChanges.applyTableLight();
        c.eq(0, table.getLightValue(), "setting off: no light");
    }

    private static void sprint(Check c) {
        // Running north (yaw 180: forward is -z) and moving along the wall's face.
        c.ok(ChangesHooks.isMinor(0.0F, 0.98F, 180.0F, 0.0D, -0.28D), "straight ahead: minor");
        double a = Math.toRadians(7.0D);
        c.ok(ChangesHooks.isMinor(0.0F, 0.98F, 180.0F, 0.28D * Math.sin(a), -0.28D * Math.cos(a)), "7 degrees off: minor");
        double b = Math.toRadians(9.0D);
        c.ok(!ChangesHooks.isMinor(0.0F, 0.98F, 180.0F, 0.28D * Math.sin(b), -0.28D * Math.cos(b)), "9 degrees off: stops the sprint");
        c.ok(!ChangesHooks.isMinor(0.0F, 0.98F, 180.0F, 0.0D, 0.0D), "stopped dead: stops the sprint");
        c.ok(!ChangesHooks.isMinor(0.98F, 0.98F, 180.0F, 0.0D, -0.28D), "a diagonal run along a wall (45 degrees): stops");
    }

    // ------------------------------------------------------------------------------------------

    private static void rails(Check c) {
        if (!Waterlogging.isAvailable()) {
            c.ok(true, "waterlogged rails: Fluidlogged API not installed, no block holds water");
            return;
        }
        WorldServer world = c.world;
        BlockPos at = c.area().add(4, 0, 4);
        IBlockState[] rails = {Blocks.RAIL.getDefaultState(), Blocks.GOLDEN_RAIL.getDefaultState(),
                Blocks.DETECTOR_RAIL.getDefaultState(), Blocks.ACTIVATOR_RAIL.getDefaultState()};
        for (int i = 0; i < rails.length; i++) {
            BlockPos p = at.add(i * 2, 0, 0);
            world.setBlockState(p, rails[i], 2);
            c.ok(Waterlogging.canHoldWater(rails[i], world, p), "holds water: " + rails[i].getBlock().getRegistryName());
            c.ok(Waterlogging.setWater(world, p, true, 2) && Waterlogging.hasWaterSource(world, p)
                    && world.getBlockState(p).getBlock() instanceof BlockRailBase, "waterlogged: " + rails[i].getBlock().getRegistryName());
            if (i == 0) {
                EntityMinecartEmpty cart = new EntityMinecartEmpty(world, p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                world.spawnEntity(cart);
                cart.onUpdate();
                c.ok(cart.isInWater(), "a minecart on a waterlogged rail is in water");
                cart.setDead();
            }
            Waterlogging.setWater(world, p, false, 2);
        }
    }

    private static void strongholds(Check c) {
        MapGenBase gen = TerrainGen.getModdedMapGen(new MapGenStronghold(), InitMapGenEvent.EventType.STRONGHOLD);
        c.ok(gen instanceof StrongholdBurial.Generator, "the Default world's stronghold generator buries");

        // A stronghold laid out by 1.12.2's own code, in a chunk of ocean.
        WorldServer world = c.world;
        MapGenStronghold.Start start = new MapGenStronghold.Start(world, new Random(42L), 100, 100);
        StructureComponent first = start.getComponents().get(0);
        StructureBoundingBox b = first.getBoundingBox();
        int cx = b.minX >> 4;
        int cz = b.minZ >> 4;
        ChunkPrimer primer = new ChunkPrimer();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 63; y++) {
                    primer.setBlockState(x, y, z, y < 1 ? Blocks.STONE.getDefaultState() : Blocks.WATER.getDefaultState());
                }
            }
        }
        ModConfig.changes.strongholdBurial = true;
        int buried = StrongholdBurial.bury(start, cx, cz, primer);
        c.ok(buried > 0, "water around the stronghold's pieces made stone: " + buried);
        int lx = Math.max(b.minX, cx << 4) - (cx << 4);
        int lz = Math.max(b.minZ, cz << 4) - (cz << 4);
        c.ok(b.minY >= 1 && b.maxY < 62, "the stronghold is in the water, from y " + b.minY);
        c.eq(Blocks.STONE, primer.getBlockState(lx, b.minY, lz).getBlock(), "inside its first piece: stone");
        c.eq(Blocks.WATER, primer.getBlockState(lx, 62, lz).getBlock(), "the sea above it stays");
    }
}
