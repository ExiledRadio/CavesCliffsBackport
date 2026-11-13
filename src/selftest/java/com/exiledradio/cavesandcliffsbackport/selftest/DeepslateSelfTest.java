package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.BlockCCSlab;
import com.exiledradio.backportcore.block.ModernWall;
import com.exiledradio.cavesandcliffsbackport.deepslate.BlockDeepslateRedstoneOre;
import com.exiledradio.cavesandcliffsbackport.deepslate.DeepslateContent;
import com.exiledradio.cavesandcliffsbackport.deepslate.DeepslateLayer;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRotatedPillar;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.oredict.OreDictionary;

import java.util.Random;

/** Stream D: blocks, drops, recipes, walls, slabs and the deepslate layer in real generated chunks. */
public final class DeepslateSelfTest implements SelfTest {

    @Override
    public String name() {
        return "deepslate";
    }

    private static Block b(String name) {
        return DeepslateContent.BLOCKS.get(name);
    }

    @Override
    public void run(Check c) throws Exception {
        registration(c);
        drops(c);
        redstone(c);
        recipes(c);
        walls(c);
        slabs(c);
        layer(c);
        generated(c);
    }

    private void registration(Check c) {
        c.eq(37, DeepslateContent.all().size(), "block count");
        c.eq(new java.util.TreeSet<String>(DeepslateContent.BLOCKS.keySet()),
                new java.util.TreeSet<String>(java.util.Arrays.asList(DeepslateContent.blockNames())), "pre-init name list matches registration");
        for (Block block : DeepslateContent.all()) {
            for (IBlockState s : block.getBlockState().getValidStates()) {
                int meta = block.getMetaFromState(s);
                // Stair shapes and wall sides are actual state: compare what metadata stores.
                IBlockState back = block.getStateFromMeta(meta);
                c.ok(block.getMetaFromState(back) == meta, block.getRegistryName() + " meta round trip " + s);
            }
            boolean hasItem = Item.getItemFromBlock(block) != Items.AIR;
            c.eq(!(block instanceof BlockCCSlab.Double), hasItem, block.getRegistryName() + " has an item");
        }
        WorldServer w = c.world;
        c.eq(3.0F, b("deepslate").getBlockHardness(b("deepslate").getDefaultState(), w, BlockPos.ORIGIN), "deepslate hardness");
        c.ok(Math.abs(b("deepslate").getExplosionResistance(null) - 6.0F) < 1e-4, "deepslate blast resistance 6: " + b("deepslate").getExplosionResistance(null));
        c.ok(Math.abs(b("deepslate_coal_ore").getExplosionResistance(null) - 3.0F) < 1e-4, "ore blast resistance 3");
        c.ok(Math.abs(b("cobbled_deepslate_stairs").getExplosionResistance(null) - 6.0F) < 1e-4, "stairs resistance 6: " + b("cobbled_deepslate_stairs").getExplosionResistance(null));
        c.ok(Math.abs(b("cobbled_deepslate_slab").getExplosionResistance(null) - 6.0F) < 1e-4, "slab resistance 6");
        c.ok(Math.abs(b("cobbled_deepslate_wall").getExplosionResistance(null) - 6.0F) < 1e-4, "wall resistance 6");
        c.eq(3.5F, b("deepslate_tile_slab").getBlockHardness(b("deepslate_tile_slab").getDefaultState(), w, BlockPos.ORIGIN), "tile slab hardness");
        c.eq(0.75F, b("calcite").getBlockHardness(b("calcite").getDefaultState(), w, BlockPos.ORIGIN), "calcite hardness");
        c.eq(2, b("deepslate_diamond_ore").getHarvestLevel(b("deepslate_diamond_ore").getDefaultState()), "diamond ore needs iron");
        c.eq(1, b("deepslate_iron_ore").getHarvestLevel(b("deepslate_iron_ore").getDefaultState()), "iron ore needs stone");
        c.eq(0, b("deepslate").getHarvestLevel(b("deepslate").getDefaultState()), "deepslate needs a wooden pickaxe");
        c.ok(OreDictionary.getOres("oreDiamond").stream().anyMatch(s -> s.getItem() == Item.getItemFromBlock(b("deepslate_diamond_ore"))), "oreDiamond");
        c.ok(OreDictionary.getOres("cobblestone").stream().anyMatch(s -> s.getItem() == Item.getItemFromBlock(b("cobbled_deepslate"))), "cobblestone ore name");
        c.ok(com.exiledradio.backportcore.ownership.BlockOwnership.table().containsKey("tuff"), "tuff declared to Core");
    }

    /** Breaks a block as a survival player holding a tool; returns how many of an item dropped. */
    private static int harvest(Check c, BlockPos pos, IBlockState state, ItemStack tool, Item want) {
        c.world.setBlockState(pos, state, 2);
        EntityPlayerMP p = Players.real(c.world, "deepslate");
        p.setPosition(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        p.interactionManager.setGameType(GameType.SURVIVAL);
        p.setHeldItem(EnumHand.MAIN_HAND, tool.copy());
        p.interactionManager.tryHarvestBlock(pos);
        return Drops.count(c.world, pos, want);
    }

    private static ItemStack silk(Item tool) {
        ItemStack s = new ItemStack(tool);
        s.addEnchantment(Enchantments.SILK_TOUCH, 1);
        return s;
    }

    private static ItemStack fortune(Item tool, int level) {
        ItemStack s = new ItemStack(tool);
        s.addEnchantment(Enchantments.FORTUNE, level);
        return s;
    }

    private void drops(Check c) {
        BlockPos at = c.area().up();
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        Item cobbled = Item.getItemFromBlock(b("cobbled_deepslate"));
        Item deep = Item.getItemFromBlock(b("deepslate"));
        c.eq(1, harvest(c, at, b("deepslate").getDefaultState(), pick, cobbled), "deepslate drops cobbled deepslate");
        c.eq(1, harvest(c, at, b("deepslate").getDefaultState(), silk(Items.DIAMOND_PICKAXE), deep), "silk touch deepslate drops deepslate");
        c.eq(0, harvest(c, at, b("deepslate").getDefaultState(), ItemStack.EMPTY, cobbled), "deepslate by hand drops nothing");
        c.eq(0, harvest(c, at, b("deepslate_diamond_ore").getDefaultState(), new ItemStack(Items.STONE_PICKAXE), Items.DIAMOND), "diamond ore with stone pickaxe drops nothing");
        c.eq(1, harvest(c, at, b("deepslate_diamond_ore").getDefaultState(), pick, Items.DIAMOND), "deepslate diamond ore drops a diamond");
        c.eq(1, harvest(c, at, b("deepslate_coal_ore").getDefaultState(), pick, Items.COAL), "deepslate coal ore drops coal");
        c.eq(1, harvest(c, at, b("deepslate_iron_ore").getDefaultState(), pick, DeepslateContent.rawIron), "deepslate iron ore drops raw iron");
        c.eq(1, harvest(c, at, b("deepslate_gold_ore").getDefaultState(), pick, DeepslateContent.rawGold), "deepslate gold ore drops raw gold");
        c.eq(1, harvest(c, at, b("deepslate_iron_ore").getDefaultState(), silk(Items.DIAMOND_PICKAXE), Item.getItemFromBlock(b("deepslate_iron_ore"))), "silk touch iron ore drops itself");
        c.eq(1, harvest(c, at, Blocks.IRON_ORE.getDefaultState(), pick, DeepslateContent.rawIron), "vanilla iron ore drops raw iron");
        c.eq(1, harvest(c, at, Blocks.GOLD_ORE.getDefaultState(), pick, DeepslateContent.rawGold), "vanilla gold ore drops raw gold");
        c.eq(1, harvest(c, at, Blocks.IRON_ORE.getDefaultState(), silk(Items.DIAMOND_PICKAXE), Item.getItemFromBlock(Blocks.IRON_ORE)), "silk touch vanilla iron ore drops the ore");
        int lapis = 0, n = 40;
        for (int i = 0; i < n; i++) {
            c.world.setBlockState(at, b("deepslate_lapis_ore").getDefaultState(), 2);
            NonNullList<ItemStack> list = NonNullList.create();
            b("deepslate_lapis_ore").getDrops(list, c.world, at, b("deepslate_lapis_ore").getDefaultState(), 0);
            int one = 0; // 1.12 drops one stack per item
            for (ItemStack s : list) {
                c.ok(s.getItem() == Items.DYE && s.getMetadata() == EnumDyeColor.BLUE.getDyeDamage(), "lapis drops lapis");
                one += s.getCount();
            }
            c.ok(one >= 4 && one <= 9, "lapis count 4-9: " + one);
            lapis += one;
        }
        c.ok(lapis > n * 5 && lapis < n * 8, "lapis average about 6.5: " + lapis / (double) n);
        // Fortune III on raw iron: 1 to 4, average 2.2.
        int raw = 0;
        for (int i = 0; i < n; i++) {
            raw += harvest(c, at, Blocks.IRON_ORE.getDefaultState(), fortune(Items.DIAMOND_PICKAXE, 3), DeepslateContent.rawIron);
        }
        c.ok(raw > n * 1.5 && raw < n * 3.0, "fortune III raw iron average about 2.2: " + raw / (double) n);
        ModConfig.deepslate.rawOreDrops = false;
        c.eq(1, harvest(c, at, Blocks.IRON_ORE.getDefaultState(), pick, Item.getItemFromBlock(Blocks.IRON_ORE)), "raw ore drops off: vanilla iron ore drops itself");
        c.eq(1, harvest(c, at, b("deepslate_iron_ore").getDefaultState(), pick, Item.getItemFromBlock(b("deepslate_iron_ore"))), "raw ore drops off: deepslate iron ore drops itself");
        ModConfig.deepslate.rawOreDrops = true;

        // Infested deepslate: a silverfish, no item; silk touch gives deepslate.
        c.world.setBlockState(at, b("infested_deepslate").getDefaultState(), 2);
        EntityPlayerMP p = Players.real(c.world, "infest");
        p.interactionManager.setGameType(GameType.SURVIVAL);
        p.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        p.interactionManager.tryHarvestBlock(at);
        int fish = c.world.getEntitiesWithinAABB(EntitySilverfish.class, new AxisAlignedBB(at).grow(2)).size();
        c.eq(1, fish, "breaking infested deepslate lets a silverfish out");
        for (EntitySilverfish s : c.world.getEntitiesWithinAABB(EntitySilverfish.class, new AxisAlignedBB(at).grow(2))) {
            s.setDead();
        }
        c.eq(0, Drops.count(c.world, at, deep), "infested deepslate drops nothing");
        c.eq(1, harvest(c, at, b("infested_deepslate").getDefaultState(), silk(Items.DIAMOND_PICKAXE), deep), "silk touch infested deepslate drops deepslate");
        for (EntitySilverfish s : c.world.getEntitiesWithinAABB(EntitySilverfish.class, new AxisAlignedBB(at).grow(2))) {
            if (!s.isDead) {
                c.fail("silk touch let a silverfish out");
                s.setDead();
            }
        }
    }

    private void redstone(Check c) {
        BlockPos at = c.area().up();
        Block ore = b("deepslate_redstone_ore");
        c.world.setBlockState(at, ore.getDefaultState(), 3);
        c.eq(0, c.world.getBlockState(at).getLightValue(c.world, at), "unlit redstone ore gives no light");
        ore.onBlockClicked(c.world, at, Players.real(c.world, "redstone"));
        c.eq(true, c.world.getBlockState(at).getValue(BlockDeepslateRedstoneOre.LIT), "clicking lights it");
        c.eq(9, c.world.getBlockState(at).getLightValue(c.world, at), "lit light 9");
        ore.updateTick(c.world, at, c.world.getBlockState(at), new Random(1));
        c.eq(false, c.world.getBlockState(at).getValue(BlockDeepslateRedstoneOre.LIT), "a random tick puts it out");
        int total = 0;
        for (int i = 0; i < 40; i++) {
            NonNullList<ItemStack> list = NonNullList.create();
            ore.getDrops(list, c.world, at, ore.getDefaultState(), 0);
            int one = 0;
            for (ItemStack s : list) {
                c.ok(s.getItem() == Items.REDSTONE, "redstone ore drops redstone: " + s);
                one += s.getCount();
            }
            c.ok(one >= 4 && one <= 5, "redstone 4-5: " + one);
            total += one;
        }
        c.ok(total >= 160 && total <= 200, "redstone total");
    }

    private void recipes(Check c) {
        ItemStack cobbled = new ItemStack(b("cobbled_deepslate"));
        ItemStack e = ItemStack.EMPTY;
        Crafting.expect(c, Crafting.grid(2, 2, cobbled, cobbled, cobbled, cobbled), new ItemStack(b("polished_deepslate"), 4), "polished deepslate");
        ItemStack pol = new ItemStack(b("polished_deepslate"));
        Crafting.expect(c, Crafting.grid(2, 2, pol, pol, pol, pol), new ItemStack(b("deepslate_bricks"), 4), "deepslate bricks");
        ItemStack bricks = new ItemStack(b("deepslate_bricks"));
        Crafting.expect(c, Crafting.grid(2, 2, bricks, bricks, bricks, bricks), new ItemStack(b("deepslate_tiles"), 4), "deepslate tiles");
        Crafting.expect(c, Crafting.grid(3, 3, cobbled, e, e, cobbled, cobbled, e, cobbled, cobbled, cobbled), new ItemStack(b("cobbled_deepslate_stairs"), 4), "stairs");
        Crafting.expect(c, Crafting.grid(3, 1, cobbled, cobbled, cobbled), new ItemStack(b("cobbled_deepslate_slab"), 6), "slabs");
        Crafting.expect(c, Crafting.grid(3, 2, cobbled, cobbled, cobbled, cobbled, cobbled, cobbled), new ItemStack(b("cobbled_deepslate_wall"), 6), "walls");
        ItemStack slab = new ItemStack(b("cobbled_deepslate_slab"));
        Crafting.expect(c, Crafting.grid(1, 2, slab, slab), new ItemStack(b("chiseled_deepslate"), 1), "chiseled deepslate");
        ItemStack ri = new ItemStack(DeepslateContent.rawIron);
        Crafting.expect(c, Crafting.grid(3, 3, ri, ri, ri, ri, ri, ri, ri, ri, ri), new ItemStack(b("raw_iron_block")), "raw iron block");
        Crafting.expect(c, Crafting.grid(1, 1, new ItemStack(b("raw_gold_block"))), new ItemStack(DeepslateContent.rawGold, 9), "raw gold from block");
        // Stone tools take cobbled deepslate through the cobblestone ore name, as in current Java.
        ItemStack stick = new ItemStack(Items.STICK);
        Crafting.expect(c, Crafting.grid(3, 3, cobbled, cobbled, cobbled, e, stick, e, e, stick, e), new ItemStack(Items.STONE_PICKAXE), "stone pickaxe from cobbled deepslate");

        FurnaceRecipes f = FurnaceRecipes.instance();
        c.ok(Crafting.same(f.getSmeltingResult(cobbled), new ItemStack(b("deepslate"))), "smelt cobbled to deepslate");
        c.ok(Crafting.same(f.getSmeltingResult(ri), new ItemStack(Items.IRON_INGOT)), "smelt raw iron");
        // 1.12 keys smelting experience by the result, which iron ore shares.
        c.ok(Math.abs(f.getSmeltingExperience(new ItemStack(Items.IRON_INGOT)) - 0.7F) < 1e-4, "iron ingot experience 0.7");
        c.ok(Crafting.same(f.getSmeltingResult(new ItemStack(DeepslateContent.rawGold)), new ItemStack(Items.GOLD_INGOT)), "smelt raw gold");
        c.ok(Crafting.same(f.getSmeltingResult(new ItemStack(b("deepslate_lapis_ore"))), new ItemStack(Items.DYE, 1, 4)), "smelt lapis ore");
        c.ok(Crafting.same(f.getSmeltingResult(bricks), new ItemStack(b("cracked_deepslate_bricks"))), "smelt cracked bricks");
    }

    private IBlockState actual(Check c, BlockPos p) {
        return c.world.getBlockState(p).getActualState(c.world, p);
    }

    private void walls(Check c) {
        BlockPos o = c.area().up();
        IBlockState wall = b("cobbled_deepslate_wall").getDefaultState();
        c.world.setBlockState(o, wall, 2);
        IBlockState lone = actual(c, o);
        c.eq(true, lone.getValue(ModernWall.UP), "a lone wall has a post");
        c.world.setBlockState(o.north(), wall, 2);
        c.world.setBlockState(o.south(), wall, 2);
        IBlockState mid = actual(c, o);
        c.eq(ModernWall.Side.LOW, mid.getValue(ModernWall.NORTH), "north side low");
        c.eq(ModernWall.Side.LOW, mid.getValue(ModernWall.SOUTH), "south side low");
        c.eq(false, mid.getValue(ModernWall.UP), "a straight run has no post");
        c.world.setBlockState(o.up(), Blocks.STONE.getDefaultState(), 2);
        mid = actual(c, o);
        c.eq(ModernWall.Side.TALL, mid.getValue(ModernWall.NORTH), "a block above makes the sides tall");
        c.eq(false, mid.getValue(ModernWall.UP), "tall straight run: still no post");
        c.world.setBlockState(o.up(), Blocks.TORCH.getDefaultState(), 2);
        c.eq(true, actual(c, o).getValue(ModernWall.UP), "a torch above needs the post");
        c.world.setBlockState(o.up(), Blocks.AIR.getDefaultState(), 2);
        c.world.setBlockState(o.east(), Blocks.COBBLESTONE_WALL.getDefaultState(), 2);
        mid = actual(c, o);
        c.eq(ModernWall.Side.LOW, mid.getValue(ModernWall.EAST), "joins a cobblestone wall");
        c.eq(true, mid.getValue(ModernWall.UP), "a junction has a post");
        IBlockState cobbleWall = c.world.getBlockState(o.east()).getActualState(c.world, o.east());
        c.eq(true, cobbleWall.getValue(net.minecraft.block.BlockWall.WEST), "the cobblestone wall joins ours");
        c.world.setBlockState(o.west(), Blocks.STONE.getDefaultState(), 2);
        c.eq(ModernWall.Side.LOW, actual(c, o).getValue(ModernWall.WEST), "joins a solid block");
        c.world.setBlockState(o.west(), Blocks.GLASS.getDefaultState(), 2);
        c.eq(ModernWall.Side.NONE, actual(c, o).getValue(ModernWall.WEST), "does not join glass");
        // Collision: a block and a half tall, so it cannot be jumped.
        java.util.List<AxisAlignedBB> boxes = new java.util.ArrayList<AxisAlignedBB>();
        c.world.getBlockState(o).addCollisionBoxToList(c.world, o, new AxisAlignedBB(o).grow(1), boxes, null, false);
        double top = 0;
        for (AxisAlignedBB bb : boxes) {
            top = Math.max(top, bb.maxY - o.getY());
        }
        c.ok(Math.abs(top - 1.5) < 1e-6, "wall collision 1.5 high: " + top);
    }

    private void slabs(Check c) {
        BlockPos o = c.area(); // y 100, on the stone floor at 99
        BlockCCSlab.Half slab = DeepslateContent.SLABS.get("deepslate_tile");
        EntityPlayerMP p = Players.real(c.world, "slab");
        p.interactionManager.setGameType(GameType.SURVIVAL);
        p.setPosition(o.getX() + 0.5, o.getY() + 2, o.getZ() + 3.5);
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(slab, 2));
        p.getHeldItem(EnumHand.MAIN_HAND).onItemUse(p, c.world, o.down(), EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 1F, 0.5F);
        IBlockState placed = c.world.getBlockState(o);
        c.ok(placed.getBlock() == slab && placed.getValue(BlockSlab.HALF) == BlockSlab.EnumBlockHalf.BOTTOM, "a slab placed on the floor is a bottom slab: " + placed);
        p.getHeldItem(EnumHand.MAIN_HAND).onItemUse(p, c.world, o, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 0.5F, 0.5F);
        c.eq(DeepslateContent.DOUBLE_SLABS.get("deepslate_tile"), c.world.getBlockState(o).getBlock(), "a second slab makes a double slab");
        c.eq(2, harvest(c, o, c.world.getBlockState(o), new ItemStack(Items.IRON_PICKAXE), Item.getItemFromBlock(slab)), "a double slab drops two slabs");
        IBlockState stairs = b("polished_deepslate_stairs").getDefaultState();
        c.ok(stairs.getPropertyKeys().contains(BlockStairs.SHAPE), "stairs have shapes");
    }

    /** The layer swap on a hand-built column, in a chunk of the test area. */
    private void layer(Check c) {
        BlockPos o = c.area();
        Chunk chunk = c.world.getChunk(o);
        int bx = chunk.x << 4, bz = chunk.z << 4;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 24; y++) {
                    IBlockState s = y == 0 ? Blocks.BEDROCK.getDefaultState()
                            : y == 3 && x == 3 && z == 3 ? Blocks.DIAMOND_ORE.getDefaultState()
                            : y == 4 && x == 4 && z == 4 ? Blocks.LIT_REDSTONE_ORE.getDefaultState()
                            : y == 5 && x == 5 && z == 5 ? Blocks.MONSTER_EGG.getDefaultState()
                            : y == 20 && x == 6 && z == 6 ? Blocks.DIAMOND_ORE.getDefaultState()
                            : y == 2 && x == 7 && z == 7 ? Blocks.STONE.getStateFromMeta(1) // granite
                            : Blocks.STONE.getDefaultState();
                    c.world.setBlockState(new BlockPos(bx + x, y, bz + z), s, 2 | 16);
                }
            }
        }
        DeepslateLayer.convert(c.world, chunk);
        int stoneLow = 0, deepHigh = 0, blend = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 1; y < 24; y++) {
                    Block got = c.world.getBlockState(new BlockPos(bx + x, y, bz + z)).getBlock();
                    if (y < 8 && got == Blocks.STONE) {
                        stoneLow++;
                    }
                    if (y >= 16 && got == DeepslateContent.deepslate) {
                        deepHigh++;
                    }
                    if (y >= 8 && y < 16 && got == DeepslateContent.deepslate) {
                        blend++;
                    }
                }
            }
        }
        c.eq(0, stoneLow, "no stone left below y 8");
        c.eq(0, deepHigh, "no deepslate at or above y 16");
        // Expected share between 8 and 15: average of (16-y)/8 over 8 layers = 4.5/8.
        double share = blend / (256.0 * 8);
        c.ok(share > 0.45 && share < 0.68, "blend share about 0.56: " + share);
        c.eq(Blocks.BEDROCK, c.world.getBlockState(new BlockPos(bx, 0, bz)).getBlock(), "bedrock untouched");
        c.eq(b("deepslate_diamond_ore"), c.world.getBlockState(new BlockPos(bx + 3, 3, bz + 3)).getBlock(), "diamond ore became deepslate diamond ore");
        IBlockState red = c.world.getBlockState(new BlockPos(bx + 4, 4, bz + 4));
        c.ok(red.getBlock() == b("deepslate_redstone_ore") && red.getValue(BlockDeepslateRedstoneOre.LIT), "lit redstone ore became lit deepslate redstone ore");
        c.eq(b("infested_deepslate"), c.world.getBlockState(new BlockPos(bx + 5, 5, bz + 5)).getBlock(), "infested stone became infested deepslate");
        c.eq(Blocks.DIAMOND_ORE, c.world.getBlockState(new BlockPos(bx + 6, 20, bz + 6)).getBlock(), "ore above the layer untouched");
        c.eq(DeepslateContent.deepslate, c.world.getBlockState(new BlockPos(bx + 7, 2, bz + 7)).getBlock(), "granite in the layer became deepslate");
        c.eq(EnumFacing.Axis.Y, c.world.getBlockState(new BlockPos(bx + 1, 1, bz + 1)).getValue(BlockRotatedPillar.AXIS), "deepslate stands upright");
        c.world.setBlockState(new BlockPos(bx, 0, bz), Blocks.BEDROCK.getDefaultState(), 2);
    }

    /** Real chunks from the world generator, far from anything loaded. */
    private void generated(Check c) {
        WorldServer w = c.world;
        // Chunk coordinates far out, fresh each run: the test world keeps what earlier runs generated.
        int base = 4000 + (int) (System.currentTimeMillis() / 1000 % 5000) * 8;
        for (int x = -1; x <= 4; x++) {
            for (int z = -1; z <= 4; z++) {
                w.getChunkProvider().provideChunk(base + x, base + z);
            }
        }
        DeepslateLayer.sweep(w);
        int done = 0, pending = 0, deep = 0, stoneLow = 0, deepOres = 0, tuff = 0, stoneOresLow = 0;
        for (int x = 0; x <= 3; x++) {
            for (int z = 0; z <= 3; z++) {
                Chunk chunk = w.getChunk(base + x, base + z);
                int st = DeepslateLayer.status(chunk);
                if (st == 2) {
                    done++;
                } else {
                    pending++;
                    continue;
                }
                int before = stoneLow;
                StringBuilder where = new StringBuilder();
                for (int bx = 0; bx < 16; bx++) {
                    for (int bz = 0; bz < 16; bz++) {
                        for (int y = 1; y < 16; y++) {
                            Block got = chunk.getBlockState(bx, y, bz).getBlock();
                            if (got == DeepslateContent.deepslate) {
                                deep++;
                            } else if (got == Blocks.STONE && y < 8) {
                                stoneLow++;
                                if (where.length() < 200) {
                                    where.append(' ').append(bx).append(',').append(y).append(',').append(bz);
                                }
                            } else if (got == DeepslateContent.BLOCKS.get("tuff")) {
                                tuff++;
                            } else if (got.getRegistryName().getPath().startsWith("deepslate_") && got.getRegistryName().getPath().endsWith("_ore")) {
                                deepOres++;
                            } else if (y < 8 && (got == Blocks.DIAMOND_ORE || got == Blocks.IRON_ORE || got == Blocks.REDSTONE_ORE)) {
                                stoneOresLow++;
                            }
                        }
                    }
                }
                if (stoneLow > before) {
                    com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport.LOGGER.info("SELFTEST stone below 8 in chunk {},{}: {} at{}",
                            x, z, stoneLow - before, where);
                }
            }
        }
        com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport.LOGGER.info(
                "SELFTEST deepslate generation: {} chunks done, {} pending, {} deepslate, {} deepslate ores, {} tuff, {} stone below 8",
                done, pending, deep, deepOres, tuff, stoneLow);
        c.ok(done >= 9, "the inner chunks of a generated area are converted: " + done);
        c.ok(deep > done * 1000, "lots of deepslate: " + deep);
        c.eq(0, stoneLow, "no stone below y 8 in converted chunks");
        c.eq(0, stoneOresLow, "no stone ores below y 8 in converted chunks");
        c.ok(deepOres > done * 2, "deepslate ores generated: " + deepOres);
        c.ok(tuff > 0, "tuff blobs: " + tuff);
        // The edge chunks (only some neighbors populated) wait.
        Chunk edge = w.getChunk(base + 4, base + 4);
        c.ok(DeepslateLayer.status(edge) == 1, "an unpopulated edge chunk is pending: " + DeepslateLayer.status(edge));
        // Saved status: done chunks write 2.
        NBTTagCompound root = new NBTTagCompound();
        MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Save(w.getChunk(base + 1, base + 1), root));
        c.eq((byte) 2, root.getCompoundTag("cavesandcliffsbackport").getByte("deepslate"), "saved status 2");
        // A chunk loaded from a world without the mod has no status and is never converted.
        NBTTagCompound none = new NBTTagCompound();
        Chunk old = w.getChunk(base + 2, base + 2);
        MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Load(old, none));
        c.eq(0, DeepslateLayer.status(old), "a chunk without saved status is never touched");
    }
}
