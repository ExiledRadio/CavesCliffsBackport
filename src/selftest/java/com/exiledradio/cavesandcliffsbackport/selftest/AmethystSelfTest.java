package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.amethyst.AmethystContent;
import com.exiledradio.cavesandcliffsbackport.amethyst.BlockAmethystCluster;
import com.exiledradio.cavesandcliffsbackport.amethyst.GeodeGenerator;
import com.exiledradio.cavesandcliffsbackport.amethyst.ItemSpyglass;
import com.exiledradio.cavesandcliffsbackport.common.Advancements;
import com.exiledradio.cavesandcliffsbackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.passive.EntityParrot;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.oredict.OreDictionary;

import java.util.Random;

/** Stream Am: blocks, growth, drops, tinted glass, recipes, the spyglass's looking, geodes. */
public final class AmethystSelfTest implements SelfTest {

    @Override
    public String name() {
        return "amethyst";
    }

    @Override
    public void run(Check c) {
        registration(c);
        growth(c);
        drops(c);
        recipes(c);
        spyglass(c);
        geodes(c);
    }

    private void registration(Check c) {
        c.eq(7, AmethystContent.all().size(), "block count");
        int[] light = {1, 2, 4, 5};
        for (int i = 0; i < 4; i++) {
            BlockAmethystCluster b = AmethystContent.stages[i];
            c.eq(light[i], b.getDefaultState().getLightValue(c.world, BlockPos.ORIGIN), b.getRegistryName() + " light");
            for (IBlockState s : b.getBlockState().getValidStates()) {
                c.ok(b.getStateFromMeta(b.getMetaFromState(s)) == s, b.getRegistryName() + " meta " + s);
            }
            c.eq(EnumPushReaction.DESTROY, b.getPushReaction(b.getDefaultState()), b.getRegistryName() + " breaks when pushed");
        }
        c.eq(EnumPushReaction.DESTROY, AmethystContent.budding.getPushReaction(AmethystContent.budding.getDefaultState()), "budding breaks when pushed");
        c.eq(255, AmethystContent.tintedGlass.getDefaultState().getLightOpacity(), "tinted glass blocks light");
        c.ok(AmethystContent.budding.getTickRandomly() && !AmethystContent.amethyst.getTickRandomly(), "only budding amethyst ticks");
        c.ok(OreDictionary.getOres("gemAmethyst").stream().anyMatch(s -> s.getItem() == AmethystContent.shard), "gemAmethyst");
        c.eq(1, new ItemStack(AmethystContent.spyglass).getMaxStackSize(), "spyglass stacks to 1");
    }

    private void growth(Check c) {
        BlockPos o = c.area().up(5);
        c.world.setBlockState(o, AmethystContent.budding.getDefaultState(), 2);
        Random r = new Random(5);
        for (int i = 0; i < 5000; i++) {
            AmethystContent.budding.randomTick(c.world, o, c.world.getBlockState(o), r);
        }
        int clusters = 0;
        for (EnumFacing f : EnumFacing.values()) {
            IBlockState s = c.world.getBlockState(o.offset(f));
            if (s.getBlock() instanceof BlockAmethystCluster) {
                c.eq(f, s.getValue(BlockAmethystCluster.FACING), "a bud points away from the budding block");
                if (((BlockAmethystCluster) s.getBlock()).isCluster()) {
                    clusters++;
                }
            }
        }
        c.eq(6, clusters, "all six faces grow to clusters in time");
        // A bud whose support goes drops off.
        BlockPos p = o.add(5, 0, 0);
        c.world.setBlockState(p, Blocks.STONE.getDefaultState(), 2);
        c.world.setBlockState(p.up(), AmethystContent.stages[2].getDefaultState().withProperty(BlockAmethystCluster.FACING, EnumFacing.UP), 2);
        c.world.setBlockState(p, Blocks.AIR.getDefaultState(), 3);
        c.eq(Blocks.AIR, c.world.getBlockState(p.up()).getBlock(), "an unsupported bud breaks");
        c.ok(!AmethystContent.stages[0].canPlaceBlockOnSide(c.world, p.up(3), EnumFacing.UP), "a bud cannot be placed in mid-air");
        // Clear the six clusters.
        for (EnumFacing f : EnumFacing.values()) {
            c.world.setBlockState(o.offset(f), Blocks.AIR.getDefaultState(), 2);
        }
    }

    private static int harvest(Check c, BlockPos pos, IBlockState state, ItemStack tool, Item want) {
        c.world.setBlockState(pos.down(), Blocks.STONE.getDefaultState(), 2);
        c.world.setBlockState(pos, state, 2);
        EntityPlayerMP p = Players.real(c.world, "amethyst");
        p.setPosition(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        p.interactionManager.setGameType(GameType.SURVIVAL);
        p.setHeldItem(EnumHand.MAIN_HAND, tool.copy());
        p.interactionManager.tryHarvestBlock(pos);
        return Drops.count(c.world, pos, want);
    }

    private void drops(Check c) {
        BlockPos at = c.area().up();
        IBlockState cluster = AmethystContent.stages[3].getDefaultState();
        Item shard = AmethystContent.shard;
        c.eq(4, harvest(c, at, cluster, new ItemStack(Items.IRON_PICKAXE), shard), "a cluster mined with a pickaxe gives 4 shards");
        c.eq(2, harvest(c, at, cluster, ItemStack.EMPTY, shard), "by hand 2");
        ItemStack fortune = new ItemStack(Items.DIAMOND_PICKAXE);
        fortune.addEnchantment(Enchantments.FORTUNE, 3);
        int total = 0;
        for (int i = 0; i < 40; i++) {
            total += harvest(c, at, cluster, fortune, shard);
        }
        c.ok(total > 40 * 6.5 && total < 40 * 11, "Fortune III averages about 8.8: " + total / 40.0);
        ItemStack silk = new ItemStack(Items.IRON_PICKAXE);
        silk.addEnchantment(Enchantments.SILK_TOUCH, 1);
        c.eq(1, harvest(c, at, cluster, silk, Item.getItemFromBlock(AmethystContent.stages[3])), "silk touch cluster");
        c.eq(0, harvest(c, at, AmethystContent.stages[1].getDefaultState(), new ItemStack(Items.IRON_PICKAXE), Item.getItemFromBlock(AmethystContent.stages[1])), "a bud drops nothing");
        c.eq(1, harvest(c, at, AmethystContent.stages[1].getDefaultState(), silk, Item.getItemFromBlock(AmethystContent.stages[1])), "silk touch bud");
        c.eq(0, harvest(c, at, AmethystContent.budding.getDefaultState(), silk, Item.getItemFromBlock(AmethystContent.budding)), "budding amethyst drops nothing even with silk touch");
        c.eq(0, harvest(c, at, AmethystContent.amethyst.getDefaultState(), ItemStack.EMPTY, Item.getItemFromBlock(AmethystContent.amethyst)), "amethyst by hand drops nothing");
        c.eq(1, harvest(c, at, AmethystContent.amethyst.getDefaultState(), new ItemStack(Items.WOODEN_PICKAXE), Item.getItemFromBlock(AmethystContent.amethyst)), "amethyst block with a pickaxe");
        c.eq(1, harvest(c, at, AmethystContent.tintedGlass.getDefaultState(), ItemStack.EMPTY, Item.getItemFromBlock(AmethystContent.tintedGlass)), "tinted glass drops itself");
    }

    private void recipes(Check c) {
        ItemStack s = new ItemStack(AmethystContent.shard);
        ItemStack e = ItemStack.EMPTY;
        Crafting.expect(c, Crafting.grid(2, 2, s, s, s, s), new ItemStack(AmethystContent.amethyst), "amethyst block");
        Crafting.expect(c, Crafting.grid(3, 3, e, s, e, s, new ItemStack(Blocks.GLASS), s, e, s, e), new ItemStack(AmethystContent.tintedGlass, 2), "tinted glass");
        Item copper = Owners.ours("copper_ingot");
        if (copper != null) {
            ItemStack cu = new ItemStack(copper);
            Crafting.expect(c, Crafting.grid(1, 3, s, cu, cu), new ItemStack(AmethystContent.spyglass), "spyglass from our copper");
        } else {
            c.fail("copper stream off in this test run");
        }
    }

    private void spyglass(Check c) {
        BlockPos o = c.area().up();
        EntityPlayerMP p = Players.real(c.world, "spyglass");
        p.setPositionAndRotation(o.getX() + 0.5, o.getY(), o.getZ() + 0.5, -90F, 0F); // yaw -90: looking east (+x)
        p.rotationYawHead = -90F; // getLook(1) reads the head yaw
        EntityParrot parrot = new EntityParrot(c.world);
        parrot.setPosition(o.getX() + 20.5, o.getY() + p.getEyeHeight() - 0.4, o.getZ() + 0.5);
        parrot.setNoAI(true);
        c.world.spawnEntity(parrot);
        Entity seen = ItemSpyglass.lookedAt(p);
        c.eq(parrot, seen, "looking east at a parrot 20 blocks away");
        c.ok(Advancements.has(p, "adventure/spyglass_at_parrot"), "Is It a Bird? granted");
        c.world.setBlockState(o.add(10, 0, 0), Blocks.STONE.getDefaultState(), 2);
        c.world.setBlockState(o.add(10, 1, 0), Blocks.STONE.getDefaultState(), 2);
        c.eq(null, ItemSpyglass.lookedAt(p), "not through a wall");
        parrot.setDead();
        c.world.setBlockState(o.add(10, 0, 0), Blocks.AIR.getDefaultState(), 2);
        c.world.setBlockState(o.add(10, 1, 0), Blocks.AIR.getDefaultState(), 2);
        EntityGhast ghast = new EntityGhast(c.world);
        ghast.setPosition(o.getX() + 30.5, o.getY() - 1, o.getZ() + 0.5); // inside the loaded, cleared test cell
        ghast.setNoAI(true);
        c.world.spawnEntity(ghast);
        c.ok(ItemSpyglass.lookedAt(p) == ghast, "a ghast 30 blocks off (the parrot, dead but not yet removed, is skipped)");
        c.ok(Advancements.has(p, "adventure/spyglass_at_ghast"), "Is It a Balloon? granted");
        ghast.setDead();
    }

    private void geodes(Check c) {
        BlockPos area = c.area();
        int minX = area.getX(), minZ = area.getZ();
        BlockPos corner = new BlockPos(minX + 9, 20, minZ + 9);
        c.fill(new BlockPos(minX, 5, minZ), new BlockPos(minX + 31, 45, minZ + 31), Blocks.STONE.getDefaultState());
        Block calcite = Owners.ourBlock("calcite"), basalt = Owners.ourBlock("smooth_basalt");
        boolean placed = GeodeGenerator.place(c.world, new Random(42), corner, minX, minZ);
        c.ok(placed, "a geode is placed in stone");
        int[] n = new int[6];
        for (BlockPos q : BlockPos.getAllInBox(new BlockPos(minX, 5, minZ), new BlockPos(minX + 31, 45, minZ + 31))) {
            Block b = c.world.getBlockState(q).getBlock();
            if (b == AmethystContent.amethyst) {
                n[0]++;
            } else if (b == AmethystContent.budding) {
                n[1]++;
            } else if (b == calcite) {
                n[2]++;
            } else if (b == basalt) {
                n[3]++;
            } else if (b instanceof BlockAmethystCluster) {
                n[4]++;
            } else if (b == Blocks.AIR) {
                n[5]++;
            }
        }
        com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport.LOGGER.info(
                "SELFTEST geode: {} amethyst, {} budding, {} calcite, {} smooth basalt, {} buds, {} air", n[0], n[1], n[2], n[3], n[4], n[5]);
        c.ok(n[0] > 50 && n[2] > 50 && n[3] > 50, "the three layers are there");
        c.ok(n[1] > 0 && n[1] < n[0], "some budding amethyst, less than plain");
        c.ok(n[5] > 20, "hollow inside");
        c.ok(n[3] > n[2] && n[2] > n[0] / 3, "outer layer the biggest");
        // Real chunks.
        int base = 14000 + (int) (System.currentTimeMillis() / 1000 % 3000) * 16;
        for (int x = 0; x <= 11; x++) {
            for (int z = 0; z <= 11; z++) {
                c.world.getChunkProvider().provideChunk(base + x, base + z);
            }
        }
        int budding = 0, calc = 0;
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                Chunk ch = c.world.getChunk(base + x, base + z);
                for (int bx = 0; bx < 16; bx++) {
                    for (int bz = 0; bz < 16; bz++) {
                        for (int y = 1; y < 64; y++) {
                            Block b = ch.getBlockState(bx, y, bz).getBlock();
                            if (b == AmethystContent.budding) {
                                budding++;
                            } else if (b == calcite) {
                                calc++;
                            }
                        }
                    }
                }
            }
        }
        com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport.LOGGER.info("SELFTEST geodes in 100 generated chunks: {} budding, {} calcite", budding, calc);
        c.ok(calc > 100, "geodes generate in real chunks (about 4 in 100 chunks): " + calc + " calcite");
        c.ok(budding > 0, "with budding amethyst: " + budding);
    }
}
