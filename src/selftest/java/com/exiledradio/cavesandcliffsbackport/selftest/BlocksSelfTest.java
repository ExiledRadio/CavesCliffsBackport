package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.backportcore.terrain.WorldHeights;
import com.exiledradio.cavesandcliffsbackport.blocks.BlockLight;
import com.exiledradio.cavesandcliffsbackport.blocks.BlocksContent;
import com.exiledradio.cavesandcliffsbackport.blocks.ItemLight;
import com.exiledradio.cavesandcliffsbackport.blocks.VanillaChanges;
import com.exiledradio.cavesandcliffsbackport.blocks.WorldFall;
import com.exiledradio.cavesandcliffsbackport.common.Advancements;

import net.minecraft.block.BlockDirt;
import net.minecraft.block.BlockSilverfish;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.state.BlockPistonStructureHelper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityPolarBear;
import net.minecraft.entity.monster.EntityShulker;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityLlama;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityShulkerBullet;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Stream X: the light block and its item, the Caves &amp; Cliffs advancement, 1.17's changes to vanilla things. */
public final class BlocksSelfTest implements SelfTest {

    @Override
    public String name() {
        return "blocks";
    }

    @Override
    public void run(Check c) {
        registration(c);
        itemLevels(c);
        placing(c);
        cycling(c);
        world(c);
        falling(c);
        advancement(c);
        paths(c);
        cookedDrops(c);
        pufferfish(c);
        llamas(c);
        infested(c);
        shulkers(c);
    }

    private static BlockLight light() {
        return BlocksContent.light;
    }

    private static IBlockState light(int level) {
        return light().getDefaultState().withProperty(BlockLight.LEVEL, level);
    }

    private void registration(Check c) {
        BlockLight b = light();
        c.ok(b != null && BlocksContent.lightItem != null, "light block and item registered");
        c.eq(Arrays.asList("light"), Arrays.asList(BlocksContent.BLOCK_NAMES), "pre-init name list");
        c.eq(16, b.getBlockState().getValidStates().size(), "16 states");
        for (int level = 0; level < 16; level++) {
            IBlockState s = light(level);
            c.ok(b.getStateFromMeta(b.getMetaFromState(s)) == s, "meta round trip " + level);
            c.eq(level, s.getLightValue(c.world, BlockPos.ORIGIN), "light value " + level);
        }
        IBlockState s = b.getDefaultState();
        c.eq(15, s.getValue(BlockLight.LEVEL), "default level");
        c.eq(-1.0F, s.getBlockHardness(c.world, BlockPos.ORIGIN), "cannot be mined");
        c.ok(b.getExplosionResistance(null) >= 3600000.0F, "cannot be blown up");
        c.eq(EnumPushReaction.BLOCK, s.getPushReaction(), "blocks pistons");
        c.ok(s.getCollisionBoundingBox(c.world, BlockPos.ORIGIN) == null, "no collision");
        c.eq(0, s.getLightOpacity(c.world, BlockPos.ORIGIN), "lets light through");
        c.ok(!s.isOpaqueCube() && !s.isFullCube() && !s.getMaterial().blocksMovement(), "not solid");
        c.ok(!b.canCollideCheck(s, false), "a server cannot point at it");
        c.eq(Items.AIR, b.getItemDropped(s, new Random(), 0), "drops nothing");
        c.ok(BlocksContent.lightItem.getCreativeTab() == null, "in no creative tab");
        c.eq(EnumRarity.EPIC, new ItemStack(BlocksContent.lightItem).getRarity(), "epic rarity");
        c.eq(64, new ItemStack(BlocksContent.lightItem).getMaxStackSize(), "stacks to 64");
    }

    private static ItemStack lightStack(Object level) {
        ItemStack stack = new ItemStack(BlocksContent.lightItem);
        if (level instanceof String) {
            stack.getOrCreateSubCompound("BlockStateTag").setString("level", (String) level);
        } else if (level instanceof Integer) {
            stack.getOrCreateSubCompound("BlockStateTag").setInteger("level", (Integer) level);
        }
        return stack;
    }

    private void itemLevels(Check c) {
        c.eq(15, ItemLight.level(lightStack(null)), "no tag is level 15");
        c.eq(7, ItemLight.level(lightStack("7")), "string level");
        c.eq(0, ItemLight.level(lightStack("0")), "level 0");
        c.eq(3, ItemLight.level(lightStack(3)), "integer level");
        c.eq(15, ItemLight.level(lightStack("bright")), "not a number is level 15");
        c.eq(15, ItemLight.level(lightStack(16)), "out of range is level 15");
        c.eq(15, ItemLight.level(lightStack("-1")), "negative is level 15");
        c.ok(!ItemLight.withLevel(new ItemStack(BlocksContent.lightItem), 15).hasTagCompound(), "level 15 writes no tag");
        ItemStack four = ItemLight.withLevel(new ItemStack(BlocksContent.lightItem), 4);
        NBTTagCompound tag = four.getSubCompound("BlockStateTag");
        c.ok(tag != null && "4".equals(tag.getString("level")), "level 4 written as Java Edition writes it");
        ItemStack picked = light().getPickBlock(light(9), null, c.world, BlockPos.ORIGIN, null);
        c.eq(9, ItemLight.level(picked), "pick block keeps the level");
    }

    private static EnumActionResult use(EntityPlayerMP p, BlockPos pos, EnumFacing face) {
        return p.getHeldItemMainhand().onItemUse(p, p.world, pos, EnumHand.MAIN_HAND, face, 0.5F, 1.0F, 0.5F);
    }

    private void placing(Check c) {
        BlockPos floor = c.area().add(8, -1, 8);
        BlockPos at = floor.up();
        EntityPlayerMP p = Players.real(c.world, "light");
        p.setPosition(at.getX() + 3.5, at.getY(), at.getZ() + 0.5);
        p.interactionManager.setGameType(GameType.SURVIVAL);
        // An earlier run left a level 15 light above; the area was cleared before its surroundings were loaded,
        // and 1.12.2 skips relighting then. Relight from where that light was.
        c.world.checkLightFor(EnumSkyBlock.BLOCK, at.up());

        p.setHeldItem(EnumHand.MAIN_HAND, lightStack("7"));
        c.eq(EnumActionResult.SUCCESS, use(p, floor, EnumFacing.UP), "placing a light");
        c.eq(light(7), c.world.getBlockState(at), "placed at the item's level");
        c.ok(p.getHeldItemMainhand().isEmpty(), "survival uses the item up");
        c.eq(7, c.world.getLightFor(EnumSkyBlock.BLOCK, at), "the world is lit to 7 there");
        c.eq(6, c.world.getLightFor(EnumSkyBlock.BLOCK, at.east()), "and to 6 beside it");

        p.setHeldItem(EnumHand.MAIN_HAND, lightStack(null));
        c.eq(EnumActionResult.FAIL, use(p, floor, EnumFacing.UP), "a light does not replace a light");
        c.eq(light(7), c.world.getBlockState(at), "the first light is unchanged");
        c.eq(EnumActionResult.SUCCESS, use(p, at, EnumFacing.UP), "a light used on a light goes beside it");
        c.eq(light(15), c.world.getBlockState(at.up()), "above, at level 15");
        c.eq(light(7), c.world.getBlockState(at), "the clicked light is unchanged");

        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Blocks.COBBLESTONE));
        c.eq(EnumActionResult.SUCCESS, use(p, floor, EnumFacing.UP), "another block is placed into a light");
        c.eq(Blocks.COBBLESTONE, c.world.getBlockState(at).getBlock(), "and replaces it");
        c.ok(Drops.count(c.world, at, BlocksContent.lightItem) == 0, "the replaced light drops nothing");

        p.interactionManager.setGameType(GameType.ADVENTURE);
        p.setHeldItem(EnumHand.MAIN_HAND, lightStack(null));
        BlockPos other = floor.add(4, 0, 4);
        use(p, other, EnumFacing.UP);
        c.eq(Blocks.AIR, c.world.getBlockState(other.up()).getBlock(), "adventure mode cannot place one");
    }

    private boolean activate(EntityPlayerMP p, BlockPos at) {
        return light().onBlockActivated(p.world, at, p.world.getBlockState(at), p, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 0.5F, 0.5F);
    }

    private void cycling(Check c) {
        BlockPos at = c.area().add(8, 0, 8);
        EntityPlayerMP p = Players.real(c.world, "cycle");
        p.setPosition(at.getX() + 2.5, at.getY(), at.getZ() + 0.5);
        c.world.setBlockState(at, light(7), 3);

        p.interactionManager.setGameType(GameType.CREATIVE);
        p.setHeldItem(EnumHand.MAIN_HAND, lightStack(null));
        c.ok(activate(p, at), "using a light block is always used up");
        c.eq(light(7), c.world.getBlockState(at), "a creative player who is not an operator cannot change it");

        c.world.getMinecraftServer().getPlayerList().addOp(p.getGameProfile());
        try {
            activate(p, at);
            c.eq(light(8), c.world.getBlockState(at), "a creative operator raises it by one");
            c.eq(8, c.world.getLightFor(EnumSkyBlock.BLOCK, at), "and the world's light follows");
            c.world.setBlockState(at, light(15), 3);
            activate(p, at);
            c.eq(light(0), c.world.getBlockState(at), "15 wraps to 0");
            c.eq(0, c.world.getLightFor(EnumSkyBlock.BLOCK, at), "level 0 is dark");

            p.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
            activate(p, at);
            c.eq(light(0), c.world.getBlockState(at), "not without a light in the main hand");

            p.setHeldItem(EnumHand.MAIN_HAND, lightStack(null));
            p.interactionManager.setGameType(GameType.SURVIVAL);
            activate(p, at);
            c.eq(light(0), c.world.getBlockState(at), "not in survival, operator or not");
        } finally {
            c.world.getMinecraftServer().getPlayerList().removeOp(p.getGameProfile());
        }
    }

    /** Pistons and flowing water. */
    private void world(Check c) {
        BlockPos o = c.area().add(8, 0, 8);
        c.world.setBlockState(o, light(15), 3);
        c.world.setBlockState(o.west(), Blocks.PISTON.getDefaultState(), 2);
        c.ok(!new BlockPistonStructureHelper(c.world, o.west(), EnumFacing.EAST, true).canMove(), "a piston cannot push into a light");
        c.world.setBlockState(o.west(), Blocks.STONE.getDefaultState(), 2);
        c.world.setBlockState(o.west(2), Blocks.PISTON.getDefaultState(), 2);
        c.ok(!new BlockPistonStructureHelper(c.world, o.west(2), EnumFacing.EAST, true).canMove(), "nor push a block into one");
        c.eq(light(15), c.world.getBlockState(o), "the light is still there");

        // A water source two blocks away, on the floor: its flow reaches the light's side and stops.
        BlockPos src = o.add(0, 0, 6);
        BlockPos lit = src.east();
        c.world.setBlockState(lit, light(3), 3);
        c.world.setBlockState(src, Blocks.FLOWING_WATER.getDefaultState(), 2);
        Blocks.FLOWING_WATER.updateTick(c.world, src, c.world.getBlockState(src), c.world.rand);
        c.eq(Blocks.FLOWING_WATER, c.world.getBlockState(src.west()).getBlock(), "water flows the other way");
        c.eq(light(3), c.world.getBlockState(lit), "and not through the light");
        for (BlockPos p : BlockPos.getAllInBox(src.add(-8, 0, -8), src.add(8, 0, 8))) {
            if (c.world.getBlockState(p).getMaterial().isLiquid()) {
                c.world.setBlockState(p, Blocks.AIR.getDefaultState(), 2 | 16);
            }
        }
    }

    /** Runs every falling block near a position until it lands. */
    private static void settle(Check c, BlockPos near) {
        for (int i = 0; i < 200; i++) {
            List<EntityFallingBlock> list = c.world.getEntitiesWithinAABB(EntityFallingBlock.class, new AxisAlignedBB(near).grow(12));
            boolean any = false;
            for (EntityFallingBlock e : list) {
                if (!e.isDead) {
                    e.onUpdate();
                    any = true;
                }
            }
            if (!any) {
                return;
            }
        }
    }

    private void falling(Check c) {
        BlockPos o = c.area().add(8, 0, 8);
        // Sand on a light that sits on the floor: it sinks one block and takes the light's place.
        c.world.setBlockState(o, light(15), 3);
        c.world.setBlockState(o.up(), Blocks.SAND.getDefaultState(), 3);
        settle(c, o);
        c.eq(Blocks.SAND, c.world.getBlockState(o).getBlock(), "sand falls into a light on the ground");
        c.eq(Blocks.AIR, c.world.getBlockState(o.up()).getBlock(), "and leaves where it was");
        Drops.count(c.world, o, Item.getItemFromBlock(Blocks.SAND));

        // Sand on a light in mid-air: it falls through, and the light stays.
        BlockPos p = o.add(6, 0, 0);
        c.world.setBlockState(p.up(3), light(4), 3);
        c.world.setBlockState(p.up(4), Blocks.GRAVEL.getDefaultState(), 3);
        settle(c, p);
        c.eq(Blocks.GRAVEL, c.world.getBlockState(p).getBlock(), "gravel falls through a light in the air");
        c.eq(light(4), c.world.getBlockState(p.up(3)), "and the light is still there");
        c.eq(Blocks.AIR, c.world.getBlockState(p.up(4)).getBlock(), "nothing is left above it");

        // A light put under sand that was resting on nothing it could fall through.
        BlockPos q = o.add(0, 0, 6);
        c.world.setBlockState(q.up(), Blocks.STONE.getDefaultState(), 2 | 16);
        c.world.setBlockState(q.up(2), Blocks.SAND.getDefaultState(), 2 | 16);
        c.world.setBlockState(q.up(), light(15), 3);
        settle(c, q);
        c.eq(Blocks.SAND, c.world.getBlockState(q).getBlock(), "a light put under sand lets it fall");
    }

    private void advancement(Check c) {
        int min = WorldHeights.minY(c.world);
        int max = WorldHeights.maxYExclusive(c.world);
        c.ok(max - min >= 256, "the world reports its heights (" + min + " to " + max + ")");
        c.ok(WorldFall.qualifies(c.world, max, min + 1), "top to bottom qualifies");
        c.ok(WorldFall.qualifies(c.world, max - 1, min + 4), "from one under the limit to four above the floor qualifies");
        c.ok(WorldFall.qualifies(c.world, max, min + 5), "from the limit to five above the floor qualifies");
        c.ok(!WorldFall.qualifies(c.world, max - 1, min + 5), "both at once is a block short, as in 1.18 (319 to -59 is 378)");
        c.ok(!WorldFall.qualifies(c.world, max - 1.5, min + 1), "starting lower does not");
        c.ok(!WorldFall.qualifies(c.world, max, min + 5.5), "ending higher does not");

        WorldFall fall = BlocksContent.worldFall;
        BlockPos o = c.area();
        EntityPlayerMP dead = Players.real(c.world, "falldead");
        dead.setPosition(o.getX(), max, o.getZ());
        dead.fallDistance = 0.5F;
        fall.update(dead);
        dead.setPosition(o.getX(), min + 1, o.getZ());
        dead.fallDistance = 0.0F;
        dead.setHealth(0.0F);
        fall.update(dead);
        c.ok(!Advancements.has(dead, WorldFall.ADVANCEMENT), "a fall that kills does not count");

        EntityPlayerMP p = Players.real(c.world, "fall");
        p.setPosition(o.getX(), max - 40, o.getZ());
        p.fallDistance = 0.5F;
        fall.update(p);
        p.setPosition(o.getX(), min + 1, o.getZ());
        p.fallDistance = 200.0F;
        fall.update(p);
        p.fallDistance = 0.0F;
        fall.update(p);
        c.ok(!Advancements.has(p, WorldFall.ADVANCEMENT), "a fall that started lower does not count");

        // Still on the ground: nothing is being tracked, so nothing happens.
        fall.update(p);
        p.setPosition(o.getX(), max, o.getZ());
        p.fallDistance = 0.1F;
        fall.update(p);
        p.setPosition(o.getX(), (max + min) / 2.0, o.getZ());
        p.fallDistance = 120.0F;
        fall.update(p);
        c.ok(!Advancements.has(p, WorldFall.ADVANCEMENT), "nothing is granted in mid-fall");
        p.setPosition(o.getX(), min + 1, o.getZ());
        p.fallDistance = 0.0F;
        fall.update(p);
        c.ok(Advancements.has(p, WorldFall.ADVANCEMENT), "Caves & Cliffs granted for the whole fall, survived");
    }

    private static boolean shovel(EntityPlayerMP p, BlockPos pos, EnumFacing face) {
        PlayerInteractEvent.RightClickBlock e = new PlayerInteractEvent.RightClickBlock(p, EnumHand.MAIN_HAND, pos, face, new Vec3d(pos));
        MinecraftForge.EVENT_BUS.post(e);
        return e.isCanceled();
    }

    private void paths(Check c) {
        BlockPos o = c.area().add(4, 0, 4);
        EntityPlayerMP p = Players.real(c.world, "path");
        p.setPosition(o.getX() + 0.5, o.getY() + 1, o.getZ() - 2.5);
        p.interactionManager.setGameType(GameType.SURVIVAL);
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.IRON_SHOVEL));
        IBlockState dirt = Blocks.DIRT.getDefaultState();
        IBlockState[] from = {dirt, dirt.withProperty(BlockDirt.VARIANT, BlockDirt.DirtType.COARSE_DIRT),
                dirt.withProperty(BlockDirt.VARIANT, BlockDirt.DirtType.PODZOL), Blocks.MYCELIUM.getDefaultState()};
        for (int i = 0; i < from.length; i++) {
            BlockPos at = o.add(i * 2, 0, 0);
            c.world.setBlockState(at, from[i], 2);
            c.ok(shovel(p, at, EnumFacing.UP), from[i] + " takes the shovel");
            c.eq(Blocks.GRASS_PATH, c.world.getBlockState(at).getBlock(), from[i] + " becomes a path");
        }
        c.eq(from.length, p.getHeldItemMainhand().getItemDamage(), "one durability each");

        BlockPos at = o.add(0, 0, 4);
        c.world.setBlockState(at, dirt, 2);
        c.ok(!shovel(p, at, EnumFacing.DOWN), "not from below");
        c.world.setBlockState(at.up(), Blocks.STONE.getDefaultState(), 2);
        c.ok(!shovel(p, at, EnumFacing.NORTH), "not under a block");
        c.eq(dirt, c.world.getBlockState(at), "the dirt is unchanged");
        c.world.setBlockState(at.east(2), Blocks.STONE.getDefaultState(), 2);
        c.ok(!shovel(p, at.east(2), EnumFacing.UP), "stone is left alone");
        c.world.setBlockState(at.east(4), Blocks.GRASS.getDefaultState(), 2);
        c.ok(!shovel(p, at.east(4), EnumFacing.UP), "grass is left to the vanilla shovel");
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        c.world.setBlockState(at.east(6), dirt, 2);
        c.ok(!shovel(p, at.east(6), EnumFacing.UP), "only a shovel does it");
        c.ok(!VanillaChanges.flattens(Blocks.STONE.getDefaultState()) && !VanillaChanges.flattens(Blocks.AIR.getDefaultState()),
                "nothing else flattens");
    }

    private ItemStack dropAfterDeath(Check c, EntityLivingBase mob, boolean burning, ItemStack drop) {
        if (burning) {
            mob.setFire(10);
        }
        List<EntityItem> drops = new ArrayList<EntityItem>();
        drops.add(new EntityItem(c.world, 0, 0, 0, drop));
        MinecraftForge.EVENT_BUS.post(new LivingDropsEvent(mob, DamageSource.ON_FIRE, drops, 0, false));
        return drops.get(0).getItem();
    }

    private void cookedDrops(Check c) {
        c.eq(Items.BAKED_POTATO, dropAfterDeath(c, new EntityZombie(c.world), true, new ItemStack(Items.POTATO)).getItem(), "a burning zombie's potato is baked");
        c.eq(Items.POTATO, dropAfterDeath(c, new EntityZombie(c.world), false, new ItemStack(Items.POTATO)).getItem(), "a zombie's potato otherwise is not");
        c.eq(Items.ROTTEN_FLESH, dropAfterDeath(c, new EntityZombie(c.world), true, new ItemStack(Items.ROTTEN_FLESH)).getItem(), "rotten flesh stays");
        ItemStack salmon = dropAfterDeath(c, new EntityPolarBear(c.world), true, new ItemStack(Items.FISH, 2, 1));
        c.ok(salmon.getItem() == Items.COOKED_FISH && salmon.getMetadata() == 1 && salmon.getCount() == 2, "a burning polar bear's salmon is cooked: " + salmon);
        ItemStack cod = dropAfterDeath(c, new EntityPolarBear(c.world), false, new ItemStack(Items.FISH, 1, 0));
        c.eq(Items.FISH, cod.getItem(), "a polar bear's fish otherwise is raw");
        ItemStack puffer = dropAfterDeath(c, new EntityPolarBear(c.world), true, new ItemStack(Items.FISH, 1, 3));
        c.eq(Items.FISH, puffer.getItem(), "pufferfish cannot be cooked");
        c.eq(Items.POTATO, dropAfterDeath(c, new EntityPolarBear(c.world), true, new ItemStack(Items.POTATO)).getItem(), "only zombies bake potatoes");
    }

    private void pufferfish(Check c) {
        EntityPlayerMP p = Players.real(c.world, "puffer");
        ItemStack fish = new ItemStack(Items.FISH, 1, 3);
        // What eating does, then the event the game posts after it.
        p.addPotionEffect(new PotionEffect(MobEffects.POISON, 1200, 3));
        ForgeEventFactory.onItemUseFinish(p, fish, 0, ItemStack.EMPTY);
        PotionEffect poison = p.getActivePotionEffect(MobEffects.POISON);
        c.ok(poison != null && poison.getAmplifier() == 1 && poison.getDuration() == 1200, "pufferfish gives Poison II for a minute: " + poison);
        p.clearActivePotions();
        p.addPotionEffect(new PotionEffect(MobEffects.POISON, 1200, 3));
        ForgeEventFactory.onItemUseFinish(p, new ItemStack(Items.FISH, 1, 0), 0, ItemStack.EMPTY);
        c.eq(3, p.getActivePotionEffect(MobEffects.POISON).getAmplifier(), "another food leaves a poison alone");
        p.clearActivePotions();
    }

    private void llamas(Check c) {
        EntityPlayerMP p = Players.real(c.world, "llama");
        EntityLlama llama = new EntityLlama(c.world);
        EnumDifficulty was = c.world.getWorldInfo().getDifficulty();
        try {
            c.world.getWorldInfo().setDifficulty(EnumDifficulty.PEACEFUL);
            llama.setAttackTarget(p);
            c.ok(llama.getAttackTarget() == null, "a llama does not target a player in Peaceful");
            c.world.getWorldInfo().setDifficulty(EnumDifficulty.NORMAL);
            llama.setAttackTarget(p);
            c.ok(llama.getAttackTarget() == p, "it does otherwise");
        } finally {
            c.world.getWorldInfo().setDifficulty(was);
        }
    }

    private void infested(Check c) {
        IBlockState stone = Blocks.MONSTER_EGG.getDefaultState();
        IBlockState cobble = stone.withProperty(BlockSilverfish.VARIANT, BlockSilverfish.EnumType.COBBLESTONE);
        c.eq("pickaxe", Blocks.MONSTER_EGG.getHarvestTool(stone), "a pickaxe is the tool for infested blocks");
        c.eq(6.0F, new ItemStack(Items.IRON_PICKAXE).getDestroySpeed(stone), "and mines them at its speed");
        EntityPlayerMP p = Players.real(c.world, "infested");
        c.eq(1.0F, ForgeEventFactory.getBreakSpeed(p, stone, 1.0F, BlockPos.ORIGIN), "infested stone at its own hardness");
        c.eq(0.75F, ForgeEventFactory.getBreakSpeed(p, cobble, 1.0F, BlockPos.ORIGIN), "infested cobblestone a third slower (hardness 1)");
    }

    private static int shulkersNear(Check c, BlockPos at) {
        int n = 0;
        for (EntityShulker s : c.world.getEntitiesWithinAABB(EntityShulker.class, new AxisAlignedBB(at).grow(20))) {
            if (s.isEntityAlive()) {
                n++;
            }
        }
        return n;
    }

    private static EntityShulker shulker(Check c, BlockPos at) {
        EntityShulker s = new EntityShulker(c.world);
        s.setPosition(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        s.setAttachmentPos(at);
        c.world.spawnEntity(s);
        return s;
    }

    private static void bullet(Check c, EntityShulker target) {
        EntityShulkerBullet bullet = new EntityShulkerBullet(c.world);
        target.hurtResistantTime = 0;
        target.attackEntityFrom(DamageSource.causeIndirectDamage(bullet, (EntityLivingBase) null).setProjectile(), 4.0F);
        BlocksContent.changes.settleHits();
    }

    private void shulkers(Check c) {
        BlockPos o = c.area().add(16, 1, 16);
        // A shulker teleports only to an air block that touches a full block. Stone in a 3D checkerboard
        // (the shulkers stand in its gaps) makes every gap such a place, so a teleport never fails for want of one.
        for (BlockPos p : BlockPos.getAllInBox(o.add(-10, -1, -10), o.add(10, 10, 10))) {
            boolean gap = ((p.getX() - o.getX()) + (p.getY() - o.getY()) + (p.getZ() - o.getZ())) % 2 == 0;
            c.world.setBlockState(p, gap ? Blocks.AIR.getDefaultState() : Blocks.STONE.getDefaultState(), 2 | 16);
        }
        for (Entity e : c.world.getEntitiesWithinAABB(EntityShulker.class, new AxisAlignedBB(o).grow(30))) {
            e.setDead();
        }
        EntityShulker closed = shulker(c, o);
        bullet(c, closed);
        c.eq(1, shulkersNear(c, o), "a closed shulker hit by a bullet stays one");
        c.eq(o, closed.getAttachmentPos(), "and stays where it is");
        c.ok(closed.getHealth() < closed.getMaxHealth(), "though it is hurt");

        closed.updateArmorModifier(100);
        closed.setHealth(closed.getMaxHealth());
        bullet(c, closed);
        c.eq(2, shulkersNear(c, o), "an open shulker hit by a bullet leaves a second one");
        c.ok(!o.equals(closed.getAttachmentPos()), "the first has teleported away");
        boolean atOld = false;
        for (EntityShulker s : c.world.getEntitiesWithinAABB(EntityShulker.class, new AxisAlignedBB(o).grow(0.5))) {
            atOld |= s != closed && s.isEntityAlive();
        }
        c.ok(atOld, "the new one is where the first was");

        // With five or more others within eight blocks the chance is nil.
        for (int i = 0; i < 6; i++) {
            shulker(c, o.add(2 + (i % 3) * 2, 0, 2 + (i / 3) * 2));
        }
        int before = shulkersNear(c, o);
        EntityShulker crowded = shulker(c, o.add(0, 0, -2));
        for (int i = 0; i < 3; i++) {
            crowded.updateArmorModifier(100);
            crowded.setHealth(crowded.getMaxHealth());
            bullet(c, crowded);
        }
        c.eq(before + 1, shulkersNear(c, o), "a crowd of shulkers does not grow");

        EntityShulker arrowed = shulker(c, o.add(-6, 0, -6));
        arrowed.updateArmorModifier(100);
        int had = shulkersNear(c, o);
        arrowed.attackEntityFrom(DamageSource.GENERIC, 4.0F);
        BlocksContent.changes.settleHits();
        c.eq(had, shulkersNear(c, o), "other damage makes no shulker");
        for (Entity e : c.world.getEntitiesWithinAABB(EntityShulker.class, new AxisAlignedBB(o).grow(40))) {
            e.setDead();
        }
    }
}
