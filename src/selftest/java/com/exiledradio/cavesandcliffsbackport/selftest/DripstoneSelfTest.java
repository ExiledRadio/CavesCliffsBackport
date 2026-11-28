package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.dripstone.BlockPointedDripstone;
import com.exiledradio.cavesandcliffsbackport.dripstone.DripstoneCaveGenerator;
import com.exiledradio.cavesandcliffsbackport.dripstone.DripstoneContent;
import com.exiledradio.cavesandcliffsbackport.dripstone.DripstoneEvents;
import com.exiledradio.backportcore.speleothem.EntityFallingSpeleothem;
import com.exiledradio.backportcore.speleothem.SpeleothemEvents;
import com.exiledradio.cavesandcliffsbackport.common.CaveRegions;
import com.exiledradio.backportcore.speleothem.Thickness;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCauldron;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;

/**
 * Stream Dr: thickness and merging, support (breaking and falling), both kinds of damage, growth, drips
 * into cauldrons, the lava cauldron, the recipe, regions, and the cave features placed by hand and generated.
 */
public final class DripstoneSelfTest implements SelfTest {

    private static final EnumFacing UP = EnumFacing.UP, DOWN = EnumFacing.DOWN;

    @Override
    public String name() {
        return "dripstone";
    }

    @Override
    public void run(Check c) throws Exception {
        registration(c);
        thickness(c);
        support(c);
        damage(c);
        growth(c);
        drips(c);
        lavaCauldron(c);
        recipe(c);
        regions(c);
        features(c);
        generated(c);
    }

    private static BlockPointedDripstone pd() {
        return DripstoneContent.pointed;
    }

    /** Places a segment as a player would, with neighbor updates. */
    private static void place(Check c, BlockPos pos, EnumFacing preferred, boolean merge) {
        c.world.setBlockState(pos, pd().placementState(c.world, pos, preferred, merge), 3);
    }

    private static Thickness thick(Check c, BlockPos pos) {
        IBlockState s = c.world.getBlockState(pos);
        return BlockPointedDripstone.isPointed(s) ? s.getValue(BlockPointedDripstone.THICKNESS) : null;
    }

    private static EnumFacing dir(Check c, BlockPos pos) {
        IBlockState s = c.world.getBlockState(pos);
        return BlockPointedDripstone.isPointed(s) ? s.getValue(BlockPointedDripstone.VERTICAL_DIRECTION) : null;
    }

    private static int count(WorldServer world, BlockPos from, BlockPos to, Block block) {
        int n = 0;
        for (BlockPos p : BlockPos.getAllInBox(from, to)) {
            if (world.getBlockState(p).getBlock() == block) {
                n++;
            }
        }
        return n;
    }

    private void registration(Check c) {
        c.ok(DripstoneContent.dripstone != null && pd() != null && DripstoneContent.lavaCauldron != null, "three blocks registered");
        for (String n : DripstoneContent.BLOCK_NAMES) {
            c.ok(com.exiledradio.cavesandcliffsbackport.common.Owners.ourBlock(n) != null, n + " is in the registry");
        }
        c.eq(10, pd().getBlockState().getValidStates().size(), "pointed dripstone has ten states");
        for (IBlockState s : pd().getBlockState().getValidStates()) {
            c.ok(pd().getStateFromMeta(pd().getMetaFromState(s)) == s, "meta " + s);
        }
        c.eq(EnumPushReaction.DESTROY, pd().getPushReaction(pd().getDefaultState()), "pointed dripstone breaks when pushed");
        c.ok(pd().getTickRandomly(), "pointed dripstone ticks randomly");
        c.eq(1.5F, pd().getDefaultState().getBlockHardness(c.world, BlockPos.ORIGIN), "pointed dripstone hardness");
        c.eq(1.5F, DripstoneContent.dripstone.getDefaultState().getBlockHardness(c.world, BlockPos.ORIGIN), "dripstone block hardness");
        c.eq(15, DripstoneContent.lavaCauldron.getDefaultState().getLightValue(c.world, BlockPos.ORIGIN), "lava cauldron light");
        c.eq(3, DripstoneContent.lavaCauldron.getComparatorInputOverride(DripstoneContent.lavaCauldron.getDefaultState(), c.world, BlockPos.ORIGIN), "lava cauldron comparator");
        c.ok(Item.getItemFromBlock(DripstoneContent.lavaCauldron) == Items.AIR, "the lava cauldron has no item");
        c.eq(Items.CAULDRON, DripstoneContent.lavaCauldron.getItemDropped(DripstoneContent.lavaCauldron.getDefaultState(), new Random(), 0), "it drops a cauldron");
        AxisAlignedBB tip = pd().state(UP, Thickness.TIP).getBoundingBox(c.world, BlockPos.ORIGIN);
        c.ok(Math.abs(tip.maxY - 11 / 16.0) < 1e-6 && Math.abs((tip.maxX - tip.minX) - 6 / 16.0) < 1e-6, "tip shape " + tip);
        double worst = 0;
        for (int i = 0; i < 200; i++) {
            net.minecraft.util.math.Vec3d off = pd().getDefaultState().getOffset(c.world, new BlockPos(i * 7, 0, i * 13));
            worst = Math.max(worst, Math.max(Math.abs(off.x), Math.abs(off.z)));
        }
        c.ok(worst > 0.05 && worst <= 0.125 + 1e-9, "offset is within an eighth of a block: " + worst);
    }

    private void thickness(Check c) {
        BlockPos o = c.area().add(4, 0, 4);
        c.ok(!pd().canPlaceBlockAt(c.world, o.up(5)), "cannot be placed in mid-air");
        c.ok(pd().canPlaceBlockAt(c.world, o), "can be placed on a floor");
        // A stalagmite of four, placed from the floor up.
        for (int i = 0; i < 4; i++) {
            place(c, o.up(i), UP, true);
        }
        c.eq(Thickness.BASE, thick(c, o), "stalagmite 1/4");
        c.eq(Thickness.MIDDLE, thick(c, o.up(1)), "stalagmite 2/4");
        c.eq(Thickness.FRUSTUM, thick(c, o.up(2)), "stalagmite 3/4");
        c.eq(Thickness.TIP, thick(c, o.up(3)), "stalagmite 4/4");
        c.eq(UP, dir(c, o.up(3)), "it points up");
        // A ceiling right over it: a stalactite preferred there has nothing above... so put a ceiling two up
        // and hang one segment, merging with the stalagmite's tip.
        c.world.setBlockState(o.up(5), Blocks.STONE.getDefaultState(), 3);
        place(c, o.up(4), DOWN, true);
        c.eq(DOWN, dir(c, o.up(4)), "placed under a ceiling it points down");
        c.eq(Thickness.TIP_MERGE, thick(c, o.up(4)), "the stalactite's tip merges");
        c.eq(Thickness.TIP_MERGE, thick(c, o.up(3)), "and so does the stalagmite's");
        // Breaking the stalactite un-merges the stalagmite.
        c.world.setBlockToAir(o.up(4));
        c.eq(Thickness.TIP, thick(c, o.up(3)), "alone again it is a plain tip");
        // Sneaking: the two tips stay apart.
        place(c, o.up(4), DOWN, false);
        c.eq(Thickness.TIP, thick(c, o.up(4)), "placed sneaking, the stalactite does not merge");
        c.eq(Thickness.TIP, thick(c, o.up(3)), "nor the stalagmite");
        // Looking down at a floor with a ceiling right above: the preferred direction wins when both hold.
        BlockPos q = o.add(4, 0, 0);
        c.world.setBlockState(q.up(1), Blocks.STONE.getDefaultState(), 3);
        c.eq(UP, pd().placementState(c.world, q, UP, true).getValue(BlockPointedDripstone.VERTICAL_DIRECTION), "between floor and ceiling, looking down gives a stalagmite");
        c.eq(DOWN, pd().placementState(c.world, q, DOWN, true).getValue(BlockPointedDripstone.VERTICAL_DIRECTION), "looking up a stalactite");
        c.eq(UP, pd().placementState(c.world, o.add(8, 0, 0), DOWN, true).getValue(BlockPointedDripstone.VERTICAL_DIRECTION), "with only a floor it is a stalagmite whichever way one looks");
    }

    private void support(Check c) {
        BlockPos o = c.area().add(4, 1, 4);
        Item item = Item.getItemFromBlock(pd());
        // A stalagmite on a block that is then removed breaks from the bottom up.
        c.world.setBlockState(o, Blocks.STONE.getDefaultState(), 3);
        for (int i = 1; i <= 3; i++) {
            place(c, o.up(i), UP, true);
        }
        c.world.setBlockToAir(o);
        Pools.settle(c.world);
        c.eq(0, count(c.world, o, o.up(4), pd()), "a stalagmite without its floor breaks");
        c.eq(3, Drops.count(c.world, o.up(), item), "and drops its three segments");

        // A stalactite whose ceiling goes falls: every segment a falling entity, landing as items.
        BlockPos top = o.add(6, 12, 0);
        c.world.setBlockState(top, Blocks.STONE.getDefaultState(), 3);
        for (int i = 1; i <= 3; i++) {
            place(c, top.down(i), DOWN, true);
        }
        c.eq(Thickness.BASE, thick(c, top.down(1)), "stalactite base");
        c.eq(Thickness.TIP, thick(c, top.down(3)), "stalactite tip");
        c.world.setBlockToAir(top);
        c.ok(c.world.isUpdateScheduled(top.down(1), pd()), "losing its ceiling schedules the fall");
        Pools.settle(c.world);
        c.eq(0, count(c.world, top.down(4), top, pd()), "the stalactite has left");
        List<EntityFallingSpeleothem> falling = c.world.getEntitiesWithinAABB(EntityFallingSpeleothem.class, new AxisAlignedBB(top).grow(1, 16, 1));
        c.eq(3, falling.size(), "three falling segments");
        int hurting = 0;
        for (EntityFallingSpeleothem e : falling) {
            if (e.hurtPerBlock() > 0) {
                hurting++;
                c.eq(6.0F, e.hurtPerBlock(), "a short stalactite's tip does 6 per block");
            }
        }
        c.eq(1, hurting, "only the tip hurts");
        fall(falling);
        BlockPos ground = new BlockPos(top.getX(), 100, top.getZ());
        c.eq(3, Drops.count(c.world, ground, item), "the segments land as items");
        c.eq(0, count(c.world, ground, ground.up(3), pd()), "and leave no block on the floor");

        // A long one: the damage per block is its length.
        for (int i = 1; i <= 9; i++) {
            c.world.setBlockState(top.down(i), pd().state(DOWN, i == 9 ? Thickness.TIP : i == 8 ? Thickness.FRUSTUM : i == 1 ? Thickness.BASE : Thickness.MIDDLE), 2);
        }
        c.world.scheduleUpdate(top.down(1), pd(), 1);
        Pools.settle(c.world);
        falling = c.world.getEntitiesWithinAABB(EntityFallingSpeleothem.class, new AxisAlignedBB(top).grow(1, 16, 1));
        float most = 0;
        int alive = 0;
        for (EntityFallingSpeleothem e : falling) {
            if (!e.isDead) {
                alive++;
                most = Math.max(most, e.hurtPerBlock());
                e.setDead();
            }
        }
        c.eq(9, alive, "nine falling segments");
        c.eq(9.0F, most, "a stalactite of nine does 9 per block");
    }

    private static void fall(List<EntityFallingSpeleothem> falling) {
        for (int t = 0; t < 300; t++) {
            for (EntityFallingSpeleothem e : falling) {
                if (!e.isDead) {
                    e.onUpdate();
                }
            }
        }
    }

    private void damage(Check c) {
        BlockPos o = c.area().add(4, 0, 4);
        // A stalactite of one, eight blocks over an iron golem's feet.
        EntityIronGolem golem = new EntityIronGolem(c.world);
        golem.setPosition(o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
        c.world.spawnEntity(golem);
        float before = golem.getHealth();
        BlockPos top = o.up(9);
        c.world.setBlockState(top, Blocks.STONE.getDefaultState(), 3);
        place(c, top.down(), DOWN, true);
        c.world.setBlockToAir(top);
        Pools.settle(c.world);
        List<EntityFallingSpeleothem> falling = c.world.getEntitiesWithinAABB(EntityFallingSpeleothem.class, new AxisAlignedBB(top).grow(1, 12, 1));
        c.eq(1, falling.size(), "one falling tip");
        fall(falling);
        float taken = before - golem.getHealth();
        // It falls about 8 blocks: 6 a block after the first, 40 at most.
        c.ok(taken >= 36 && taken <= 40, "a falling stalactite hurts what it lands on, 6 per block up to 40: " + taken);
        c.ok(golem.getLastDamageSource() == SpeleothemEvents.FALLING_STALACTITE, "with its own damage source");
        golem.setDead();
        Drops.count(c.world, o, Item.getItemFromBlock(pd()));

        // With a helmet: a quarter less, and the helmet wears.
        EntityZombie guard = new EntityZombie(c.world);
        guard.setPosition(o.getX() + 4.5, o.getY(), o.getZ() + 0.5);
        guard.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        c.world.spawnEntity(guard);
        EntityFallingSpeleothem one = new EntityFallingSpeleothem(c.world, o.getX() + 4.5, o.getY() + 0.5, o.getZ() + 0.5, pd().state(DOWN, Thickness.TIP));
        one.setHurts(1.0F, 40);
        c.world.spawnEntity(one);
        float hp = guard.getHealth();
        one.fall(5.0F, 1.0F);
        // 4 blocks x 1 = 4, x 0.75 for the helmet = 3, then the diamond helmet's 3 armor points.
        float lost = hp - guard.getHealth();
        c.ok(lost > 2.0F && lost < 3.0F, "a helmet takes a quarter of it: " + lost);
        c.ok(guard.getItemStackFromSlot(EntityEquipmentSlot.HEAD).getItemDamage() >= 16, "and wears for it: " + guard.getItemStackFromSlot(EntityEquipmentSlot.HEAD).getItemDamage());
        one.setDead();
        guard.setDead();

        // Landing on a stalagmite tip: a longer fall, twice the damage.
        BlockPos s = o.add(8, 0, 0);
        place(c, s, UP, true);
        EntityZombie zombie = new EntityZombie(c.world);
        zombie.setPosition(s.getX() + 0.5, s.getY() + 1, s.getZ() + 0.5);
        c.world.spawnEntity(zombie);
        pd().onFallenUpon(c.world, s, zombie, 5.0F);
        // 1.18.2's 2 blocks longer (Core uses 26.4's 2.5 only once a 26.4 backport is installed).
        c.eq(12.0F, zombie.getHealth(), "a 5-block fall onto a stalagmite does 8");
        c.ok(zombie.getLastDamageSource() == SpeleothemEvents.STALAGMITE, "as a stalagmite's damage");
        zombie.setDead();
        // On its base instead it is an ordinary fall.
        place(c, s.up(), UP, true);
        EntityZombie second = new EntityZombie(c.world);
        second.setPosition(s.getX() + 0.5, s.getY() + 1, s.getZ() + 0.5);
        c.world.spawnEntity(second);
        pd().onFallenUpon(c.world, s, second, 5.0F);
        c.eq(18.0F, second.getHealth(), "the same fall onto a stalagmite's side is an ordinary fall of 2");
        second.setDead();
        // Feather Falling IV: 48% less.
        EntityZombie third = new EntityZombie(c.world);
        third.setPosition(s.getX() + 0.5, s.getY() + 2, s.getZ() + 0.5);
        ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
        boots.addEnchantment(Enchantments.FEATHER_FALLING, 4);
        third.setItemStackToSlot(EntityEquipmentSlot.FEET, boots);
        c.world.spawnEntity(third);
        pd().onFallenUpon(c.world, s.up(), third, 5.0F);
        c.ok(Math.abs(third.getHealth() - (20 - 8 * 0.52F)) < 0.01F, "Feather Falling IV takes 48% off: " + third.getHealth());
        third.setDead();
    }

    private void growth(Check c) throws Exception {
        BlockPos floor = c.area().add(4, -1, 4);
        // floor at y; a dripstone ceiling 13 above it with a water source on top, and one stalactite segment.
        BlockPos root = floor.up(13);
        c.world.setBlockState(root, DripstoneContent.dripstone.getDefaultState(), 3);
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            c.world.setBlockState(root.up().offset(side), Blocks.STONE.getDefaultState(), 2);
        }
        BlockPos start = root.down();
        place(c, start, DOWN, true);
        Random r = new Random(17);
        for (int i = 0; i < 50; i++) {
            pd().growIfPossible(c.world.getBlockState(start), c.world, start, r);
        }
        c.eq(1, count(c.world, floor.up(), start, pd()), "no growth without water above the dripstone block");
        c.world.setBlockState(root.up(), Blocks.WATER.getDefaultState(), 2);
        for (int i = 0; i < 400; i++) {
            pd().growIfPossible(c.world.getBlockState(start), c.world, start, r);
        }
        int down = 0;
        while (dir(c, start.down(down)) == DOWN) {
            down++;
        }
        int up = 0;
        while (dir(c, floor.up(1 + up)) == UP) {
            up++;
        }
        // 12 open blocks: the stalactite stops at 8, the stalagmite under it grows while its floor is within
        // ten of the tip, and they merge if they meet first.
        boolean merged = thick(c, start.down(down - 1)) == Thickness.TIP_MERGE;
        c.ok(down == 8 || merged, "the stalactite grows to eight, or until it meets the stalagmite: " + down + (merged ? " merged" : ""));
        c.ok(up >= 1, "a stalagmite grows under it: " + up);
        c.ok(down + up <= 12, "they never overlap: " + down + " + " + up);
        c.eq(Thickness.BASE, thick(c, start), "the grown stalactite has a base");
        if (up >= 2) {
            c.eq(Thickness.BASE, thick(c, floor.up()), "and the stalagmite too");
        }
        // Under a plain stone ceiling nothing grows, water or not.
        BlockPos stone = root.add(5, 0, 0);
        c.world.setBlockState(stone, Blocks.STONE.getDefaultState(), 3);
        c.world.setBlockState(stone.up(), Blocks.WATER.getDefaultState(), 2);
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            c.world.setBlockState(stone.up().offset(side), Blocks.STONE.getDefaultState(), 2);
        }
        place(c, stone.down(), DOWN, true);
        for (int i = 0; i < 100; i++) {
            pd().growIfPossible(c.world.getBlockState(stone.down()), c.world, stone.down(), r);
        }
        c.eq(1, count(c.world, stone.down(12), stone.down(), pd()), "no growth from stone");
        c.world.setBlockToAir(root.up());
        c.world.setBlockToAir(stone.up());
    }

    private static boolean transfer(Check c, BlockPos start, float roll) throws Exception {
        Method m = BlockPointedDripstone.class.getDeclaredMethod("maybeTransferFluid", IBlockState.class, net.minecraft.world.World.class, BlockPos.class, float.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, c.world.getBlockState(start), c.world, start, roll);
    }

    /** A stone block with a contained source on top and a stalactite of {@code length} under it; @return the root */
    private static BlockPos dripper(Check c, BlockPos root, Block liquid, int length) {
        c.world.setBlockState(root, Blocks.STONE.getDefaultState(), 3);
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            c.world.setBlockState(root.up().offset(side), Blocks.STONE.getDefaultState(), 2);
        }
        if (liquid != null) {
            c.world.setBlockState(root.up(), liquid.getDefaultState(), 2);
        }
        for (int i = 1; i <= length; i++) {
            place(c, root.down(i), DOWN, true);
        }
        return root;
    }

    private void drips(Check c) throws Exception {
        BlockPos floor = c.area().add(4, 0, 4);
        // Water: cauldron on the floor, tip five above it.
        BlockPos root = dripper(c, floor.up(8), Blocks.WATER, 2);
        c.world.setBlockState(floor, Blocks.CAULDRON.getDefaultState(), 3);
        c.ok(!transfer(c, root.down(), 0.18F), "a roll over 45/256 sends nothing");
        c.ok(!transfer(c, root.down(2), 0.01F), "only the topmost segment sends drips");
        c.ok(transfer(c, root.down(), 0.10F), "water drips at a roll under 45/256");
        c.eq(0, DripstoneEvents.landDrips(c.world, false), "the drip takes time to land");
        c.eq(0, (int) c.world.getBlockState(floor).getValue(BlockCauldron.LEVEL), "the cauldron is still empty");
        c.eq(1, DripstoneEvents.landDrips(c.world, true), "then it lands");
        c.eq(1, (int) c.world.getBlockState(floor).getValue(BlockCauldron.LEVEL), "and the cauldron has one level");
        for (int i = 0; i < 5; i++) {
            transfer(c, root.down(), 0.0F);
            DripstoneEvents.landDrips(c.world, true);
        }
        c.eq(3, (int) c.world.getBlockState(floor).getValue(BlockCauldron.LEVEL), "it fills to three and no further");
        // Something in the way stops it; a block that leaves the middle open (a torch) does not.
        c.world.setBlockState(floor, Blocks.CAULDRON.getDefaultState(), 3);
        c.world.setBlockState(floor.up(2), Blocks.GLASS.getDefaultState(), 3);
        c.ok(!transfer(c, root.down(), 0.0F), "a block in the way stops the drip");
        c.world.setBlockState(floor.up(2), Blocks.AIR.getDefaultState(), 3);
        c.world.setBlockState(floor.up(2).west(), Blocks.STONE.getDefaultState(), 3);
        c.world.setBlockState(floor.up(2), Blocks.TORCH.getDefaultState().withProperty(net.minecraft.block.BlockTorch.FACING, EnumFacing.EAST), 2);
        c.ok(transfer(c, root.down(), 0.0F), "a wall torch does not");
        DripstoneEvents.landDrips(c.world, true);
        c.world.setBlockToAir(floor.up(2));

        // Lava: rarer, and only into an empty cauldron.
        BlockPos f2 = floor.add(6, 0, 0);
        BlockPos lava = dripper(c, f2.up(8), Blocks.LAVA, 1);
        c.world.setBlockState(f2, Blocks.CAULDRON.getDefaultState(), 3);
        c.ok(!transfer(c, lava.down(), 0.10F), "lava does not drip at a roll over 15/256");
        c.ok(transfer(c, lava.down(), 0.05F), "it does under it");
        DripstoneEvents.landDrips(c.world, true);
        c.eq(DripstoneContent.lavaCauldron, c.world.getBlockState(f2).getBlock(), "dripping lava makes a lava cauldron");
        c.ok(!transfer(c, lava.down(), 0.0F), "a lava cauldron takes no more");
        c.world.setBlockState(f2, Blocks.CAULDRON.getDefaultState().withProperty(BlockCauldron.LEVEL, 1), 3);
        c.ok(!transfer(c, lava.down(), 0.0F), "nor does lava drip into water");
        c.world.setBlockToAir(lava.up());

        // Nothing above: no drips into the cauldron. Eleven blocks down: too far.
        BlockPos f3 = floor.add(12, 0, 0);
        BlockPos dry = dripper(c, f3.up(8), null, 1);
        c.world.setBlockState(f3, Blocks.CAULDRON.getDefaultState(), 3);
        c.ok(!transfer(c, dry.down(), 0.0F), "a dry stalactite fills nothing");
        BlockPos f4 = floor.add(18, 0, 0);
        BlockPos high = dripper(c, f4.up(13), Blocks.WATER, 1);
        c.world.setBlockState(f4, Blocks.CAULDRON.getDefaultState(), 3);
        c.ok(!transfer(c, high.down(), 0.0F), "a cauldron eleven blocks under the tip is too far");
        c.world.setBlockState(f4.up(2), Blocks.CAULDRON.getDefaultState(), 3);
        c.ok(transfer(c, high.down(), 0.0F), "ten is not");
        DripstoneEvents.landDrips(c.world, true);
        c.world.setBlockToAir(root.up());
        c.world.setBlockToAir(high.up());
    }

    private void lavaCauldron(Check c) {
        BlockPos at = c.area().add(4, 0, 4);
        EntityPlayerMP p = Players.real(c.world, "dripstone");
        p.setPosition(at.getX() + 0.5, at.getY(), at.getZ() + 2.5);
        p.interactionManager.setGameType(GameType.SURVIVAL);
        c.world.setBlockState(at, Blocks.CAULDRON.getDefaultState().withProperty(BlockCauldron.LEVEL, 2), 3);
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
        p.interactionManager.processRightClickBlock(p, c.world, p.getHeldItemMainhand(), EnumHand.MAIN_HAND, at, EnumFacing.UP, 0.5F, 1.0F, 0.5F);
        c.eq(DripstoneContent.lavaCauldron, c.world.getBlockState(at).getBlock(), "a lava bucket on a cauldron makes a lava cauldron, whatever was in it");
        c.eq(Items.BUCKET, p.getHeldItemMainhand().getItem(), "and leaves the bucket");
        c.eq(0, count(c.world, at.add(-2, 0, -2), at.add(2, 2, 2), Blocks.FLOWING_LAVA) + count(c.world, at.add(-2, 0, -2), at.add(2, 2, 2), Blocks.LAVA), "no lava was poured beside it");

        // Burning: a zombie standing in it.
        EntityZombie zombie = new EntityZombie(c.world);
        zombie.setPosition(at.getX() + 0.5, at.getY() + 0.3125, at.getZ() + 0.5);
        c.world.spawnEntity(zombie);
        DripstoneContent.lavaCauldron.onEntityCollision(c.world, at, c.world.getBlockState(at), zombie);
        c.ok(zombie.getHealth() < 20 && zombie.isBurning(), "standing in it burns: health " + zombie.getHealth());
        zombie.setDead();
        EntityZombie above = new EntityZombie(c.world);
        above.setPosition(at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5);
        DripstoneContent.lavaCauldron.onEntityCollision(c.world, at, c.world.getBlockState(at), above);
        c.ok(above.getHealth() == 20 && !above.isBurning(), "standing on its rim does not");

        // An empty bucket takes the lava back and leaves vanilla's empty cauldron.
        p.interactionManager.processRightClickBlock(p, c.world, p.getHeldItemMainhand(), EnumHand.MAIN_HAND, at, EnumFacing.UP, 0.5F, 1.0F, 0.5F);
        c.eq(Blocks.CAULDRON, c.world.getBlockState(at).getBlock(), "an empty bucket empties it to a vanilla cauldron");
        c.eq(0, (int) c.world.getBlockState(at).getValue(BlockCauldron.LEVEL), "which is empty");
        c.eq(Items.LAVA_BUCKET, p.getHeldItemMainhand().getItem(), "and the bucket is full of lava");
        // Water over lava.
        c.world.setBlockState(at, DripstoneContent.lavaCauldron.getDefaultState(), 3);
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        p.interactionManager.processRightClickBlock(p, c.world, p.getHeldItemMainhand(), EnumHand.MAIN_HAND, at, EnumFacing.UP, 0.5F, 1.0F, 0.5F);
        c.ok(c.world.getBlockState(at).getBlock() == Blocks.CAULDRON && c.world.getBlockState(at).getValue(BlockCauldron.LEVEL) == 3, "a water bucket replaces the lava with water");
        // Mined, it is a cauldron.
        c.world.setBlockState(at, DripstoneContent.lavaCauldron.getDefaultState(), 3);
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        p.interactionManager.tryHarvestBlock(at);
        c.eq(1, Drops.count(c.world, at, Items.CAULDRON), "mined, it drops a cauldron");

        // Pointed dripstone drops itself whatever breaks it; the dripstone block needs a pickaxe.
        c.world.setBlockState(at, pd().getDefaultState(), 2);
        p.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        p.interactionManager.tryHarvestBlock(at);
        c.eq(1, Drops.count(c.world, at, Item.getItemFromBlock(pd())), "pointed dripstone drops by hand");
        c.world.setBlockState(at, DripstoneContent.dripstone.getDefaultState(), 2);
        p.interactionManager.tryHarvestBlock(at);
        c.eq(0, Drops.count(c.world, at, Item.getItemFromBlock(DripstoneContent.dripstone)), "the dripstone block does not");
        c.world.setBlockState(at, DripstoneContent.dripstone.getDefaultState(), 2);
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.WOODEN_PICKAXE));
        p.interactionManager.tryHarvestBlock(at);
        c.eq(1, Drops.count(c.world, at, Item.getItemFromBlock(DripstoneContent.dripstone)), "with a pickaxe it does");
    }

    private void recipe(Check c) {
        ItemStack p = new ItemStack(pd());
        Crafting.expect(c, Crafting.grid(2, 2, p, p, p, p), new ItemStack(DripstoneContent.dripstone), "four pointed dripstone make a dripstone block");
        boolean sold = false;
        for (com.exiledradio.backportcore.trade.TradeRegistry.TraderTrade t : com.exiledradio.backportcore.trade.TradeRegistry.traderTrades(
                com.exiledradio.backportcore.trade.TradeRegistry.TraderPool.ORDINARY)) {
            net.minecraft.village.MerchantRecipe offer = t.offer(new Random(1));
            if (offer != null && offer.getItemToSell().getItem() == Item.getItemFromBlock(pd())) {
                sold = offer.getItemToSell().getCount() == 2 && offer.getItemToBuy().getItem() == Items.EMERALD
                        && offer.getItemToBuy().getCount() == 1 && offer.getMaxTradeUses() == 5;
            }
        }
        c.ok(sold, "the wandering trader's list has two pointed dripstone for an emerald, five times");
    }

    private void regions(Check c) {
        long seed = c.world.getSeed();
        Random r = new Random(3);
        int dripstone = 0, lush = 0, n = 40000;
        for (int i = 0; i < n; i++) {
            CaveRegions.Region at = CaveRegions.at(seed, r.nextInt(100000) - 50000, 6 + r.nextInt(58), r.nextInt(100000) - 50000);
            if (at == CaveRegions.Region.DRIPSTONE) {
                dripstone++;
            } else if (at == CaveRegions.Region.LUSH) {
                lush++;
            }
        }
        c.ok(dripstone > n * 0.05 && dripstone < n * 0.14, "dripstone regions are about 9% of the underground: " + 100.0 * dripstone / n);
        c.ok(lush > n * 0.05 && lush < n * 0.14, "lush regions too: " + 100.0 * lush / n);
        c.eq(CaveRegions.at(seed, 123, 30, -456), CaveRegions.at(seed, 123, 30, -456), "the same answer twice");
        // Coherent: a block's neighbor is nearly always in the same region.
        int same = 0;
        for (int i = 0; i < 2000; i++) {
            int x = r.nextInt(20000), y = 10 + r.nextInt(40), z = r.nextInt(20000);
            if (CaveRegions.at(seed, x, y, z) == CaveRegions.at(seed, x + 1, y, z)) {
                same++;
            }
        }
        c.ok(same > 1960, "regions are blobs, not speckle: " + same + "/2000");
    }

    /** A hollow in solid stone at an area: stone from y to y+height+7 over 32x32, air inside. */
    private static BlockPos hollow(Check c, int height) {
        BlockPos o = c.area();
        c.fill(o, o.add(31, height + 7, 31), Blocks.STONE.getDefaultState());
        c.fill(o.add(3, 4, 3), o.add(28, 3 + height, 28), Blocks.AIR.getDefaultState());
        return o;
    }

    private void features(Check c) {
        Random r = new Random(11);
        // Clusters in a cave eight high.
        BlockPos o = hollow(c, 8);
        DripstoneCaveGenerator.Window w = new DripstoneCaveGenerator.Window(c.world, o.getX(), o.getZ());
        for (int i = 0; i < 6; i++) {
            c.ok(DripstoneCaveGenerator.cluster(w, r, o.add(10 + r.nextInt(12), 6, 10 + r.nextInt(12))), "a cluster starts in the open");
        }
        BlockPos lo = o, hi = o.add(31, 20, 31);
        int blocks = count(c.world, lo, hi, DripstoneContent.dripstone), pointed = count(c.world, lo, hi, pd());
        int water = count(c.world, lo, hi, Blocks.WATER);
        c.ok(blocks > 100, "clusters lay dripstone block: " + blocks);
        c.ok(pointed > 40, "and pointed dripstone: " + pointed);
        c.ok(water > 0, "and pools: " + water);
        checkColumns(c, lo, hi, "cluster");
        // Every pool block is held in by rock or water.
        int leaks = 0;
        for (BlockPos p : BlockPos.getAllInBox(lo, hi)) {
            if (c.world.getBlockState(p).getBlock() == Blocks.WATER) {
                for (EnumFacing f : new EnumFacing[] {EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST, DOWN}) {
                    IBlockState s = c.world.getBlockState(p.offset(f));
                    if (!s.getMaterial().isSolid() && s.getBlock() != Blocks.WATER) {
                        leaks++;
                    }
                }
            }
        }
        c.eq(0, leaks, "pools do not leak");
        c.eq(0, count(c.world, o.add(-8, 0, -8), o.add(-1, 20, 39), DripstoneContent.dripstone)
                + count(c.world, o.add(32, 0, -8), o.add(39, 20, 39), DripstoneContent.dripstone), "nothing is written outside the window");

        // Large dripstone in a cave eighteen high.
        BlockPos big = hollow(c, 18);
        w = new DripstoneCaveGenerator.Window(c.world, big.getX(), big.getZ());
        int built = 0;
        for (int i = 0; i < 6; i++) {
            if (DripstoneCaveGenerator.largeDripstone(w, r, big.add(10 + r.nextInt(12), 12, 10 + r.nextInt(12)))) {
                built++;
            }
        }
        int inCave = count(c.world, big.add(3, 4, 3), big.add(28, 21, 28), DripstoneContent.dripstone);
        c.ok(built >= 3, "large dripstone builds in a tall cave: " + built + "/6");
        c.ok(inCave > 150, "its columns fill part of the cave: " + inCave);
        c.ok(!DripstoneCaveGenerator.largeDripstone(w, r, big.add(1, 12, 1)), "it does not start in rock");

        // Single pointed dripstones.
        BlockPos small = hollow(c, 5);
        w = new DripstoneCaveGenerator.Window(c.world, small.getX(), small.getZ());
        int placed = 0;
        for (int i = 0; i < 40; i++) {
            if (DripstoneCaveGenerator.pointed(w, r, small.add(5 + r.nextInt(22), 6, 5 + r.nextInt(22)))) {
                placed++;
            }
        }
        int singles = count(c.world, small, small.add(31, 14, 31), pd());
        c.ok(placed == 40 && singles >= 35 && singles <= 80, "single pointed dripstones: " + singles + " blocks from " + placed + " tries");
        checkColumns(c, small, small.add(31, 14, 31), "single");
    }

    /** Every pointed dripstone in a box holds on to something and has the thickness its neighbors call for. */
    private static int checkColumns(Check c, BlockPos lo, BlockPos hi, String what) {
        int bad = 0, n = 0;
        for (BlockPos p : BlockPos.getAllInBox(lo, hi)) {
            IBlockState s = c.world.getBlockState(p);
            if (!BlockPointedDripstone.isPointed(s)) {
                continue;
            }
            n++;
            EnumFacing d = s.getValue(BlockPointedDripstone.VERTICAL_DIRECTION);
            Thickness want = pd().placementState(c.world, p, d, true).getValue(BlockPointedDripstone.THICKNESS);
            Thickness have = s.getValue(BlockPointedDripstone.THICKNESS);
            boolean thicknessOk = want == have || have == Thickness.TIP && want == Thickness.TIP_MERGE;
            if (!pd().canSurvive(c.world, p, s) || !thicknessOk || pd().placementState(c.world, p, d, true).getValue(BlockPointedDripstone.VERTICAL_DIRECTION) != d) {
                if (bad++ < 3) {
                    c.fail(what + ": bad pointed dripstone at " + p + " " + s + ", expected " + want);
                }
            }
        }
        c.ok(bad == 0, what + ": all " + n + " pointed dripstones are sound (" + bad + " bad)");
        return n;
    }

    /** Real chunks: finds a dripstone region, generates 9x9 chunks in it and looks at what is there. */
    private void generated(Check c) {
        long seed = c.world.getSeed();
        int fx = 0, fz = 0;
        boolean found = false;
        search:
        for (int x = 40000; x < 60000; x += 64) {
            for (int z = 40000; z < 44000; z += 64) {
                int inside = 0;
                for (int y = 12; y <= 52; y += 8) {
                    for (int d = -48; d <= 48; d += 48) {
                        if (CaveRegions.at(seed, x + d, y, z + d) == CaveRegions.Region.DRIPSTONE) {
                            inside++;
                        }
                    }
                }
                if (inside >= 9) {
                    fx = x;
                    fz = z;
                    found = true;
                    break search;
                }
            }
        }
        if (!c.ok(found, "a dripstone region within reach")) {
            return;
        }
        int cx = fx >> 4, cz = fz >> 4, radius = 4;
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                c.world.getChunk(cx + dx, cz + dz);
            }
        }
        BlockPos lo = new BlockPos((cx - radius) * 16, 1, (cz - radius) * 16), hi = new BlockPos((cx + radius) * 16 + 15, 80, (cz + radius) * 16 + 15);
        int blocks = 0, pointed = 0, inRegion = 0, outside = 0, copper = 0;
        Block ore = com.exiledradio.cavesandcliffsbackport.common.Owners.ourBlock("copper_ore");
        Block deepOre = com.exiledradio.cavesandcliffsbackport.common.Owners.ourBlock("deepslate_copper_ore");
        for (BlockPos p : BlockPos.getAllInBox(lo, hi)) {
            Block b = c.world.getBlockState(p).getBlock();
            if (b == DripstoneContent.dripstone) {
                blocks++;
            } else if (b == pd()) {
                pointed++;
                if (nearRegion(seed, p)) {
                    inRegion++;
                } else {
                    outside++;
                }
            } else if (b == ore || b == deepOre) {
                copper++;
            }
        }
        com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport.LOGGER.info(
                "SELFTEST dripstone caves at chunk {},{}: {} dripstone blocks, {} pointed ({} outside), {} copper ore in 81 chunks",
                cx, cz, blocks, pointed, outside, copper);
        c.ok(blocks > 200, "generated dripstone caves have dripstone block: " + blocks);
        c.ok(pointed > 50, "and pointed dripstone: " + pointed);
        c.ok(outside * 50 <= pointed, "hardly any of it outside the region: " + outside + " of " + pointed);
        checkColumns(c, lo, hi, "generated");
        if (ore != null) {
            // 1.17's own copper is about 6 veins of ~7 a chunk: ~3400 in 81 chunks. The region adds to that.
            c.ok(copper > 4200, "dripstone caves have extra copper: " + copper);
        }
        // Far from any region there is none.
        int px = 0, pz = 0;
        boolean plain = false;
        plainSearch:
        for (int x = 60000; x < 80000; x += 64) {
            for (int z = 40000; z < 44000; z += 64) {
                boolean any = false;
                for (int y = 6; y <= 70 && !any; y += 4) {
                    for (int dx = -64; dx <= 64 && !any; dx += 16) {
                        for (int dz = -64; dz <= 64 && !any; dz += 16) {
                            any = CaveRegions.noise(seed, x + dx, y, z + dz) > CaveRegions.THRESHOLD - 0.05;
                        }
                    }
                }
                if (!any) {
                    px = x;
                    pz = z;
                    plain = true;
                    break plainSearch;
                }
            }
        }
        if (c.ok(plain, "a place with no dripstone region within reach")) {
            int pcx = px >> 4, pcz = pz >> 4;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    c.world.getChunk(pcx + dx, pcz + dz);
                }
            }
            BlockPos a = new BlockPos((pcx - 1) * 16, 1, (pcz - 1) * 16), b = new BlockPos((pcx + 1) * 16 + 15, 80, (pcz + 1) * 16 + 15);
            c.eq(0, count(c.world, a, b, DripstoneContent.dripstone) + count(c.world, a, b, pd()), "no dripstone outside the regions");
        }
    }

    /** Within a cluster's reach (8 blocks, plus a layer) of a dripstone region. */
    private static boolean nearRegion(long seed, BlockPos p) {
        for (int dx = -12; dx <= 12; dx += 4) {
            for (int dz = -12; dz <= 12; dz += 4) {
                for (int dy = -12; dy <= 12; dy += 4) {
                    if (CaveRegions.at(seed, p.getX() + dx, p.getY() + dy, p.getZ() + dz) == CaveRegions.Region.DRIPSTONE) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
