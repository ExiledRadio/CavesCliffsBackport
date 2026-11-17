package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.Advancements;
import com.exiledradio.cavesandcliffsbackport.common.Content;
import com.exiledradio.cavesandcliffsbackport.glowsquid.EntityGlowItemFrame;
import com.exiledradio.cavesandcliffsbackport.glowsquid.EntityGlowSquid;
import com.exiledradio.cavesandcliffsbackport.glowsquid.GlowSquidContent;
import com.exiledradio.cavesandcliffsbackport.glowsquid.GlowingText;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.block.BlockStandingSign;
import net.minecraft.block.BlockWallSign;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntitySpawnPlacementRegistry;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.GameType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraftforge.fml.common.registry.EntityRegistry;

import java.util.List;
import java.util.Random;

/** Stream Gs: the glow squid, its drops and spawning, the glow item frame, glowing sign text. */
public final class GlowSquidSelfTest implements SelfTest {

    @Override
    public String name() {
        return "glowsquid";
    }

    @Override
    public void run(Check c) {
        registration(c);
        spawning(c);
        squid(c);
        loot(c);
        frame(c);
        signs(c);
    }

    private static boolean spawns(Biome biome, EnumCreatureType group) {
        for (Biome.SpawnListEntry e : biome.getSpawnableList(group)) {
            if (e.entityClass == EntityGlowSquid.class) {
                return true;
            }
        }
        return false;
    }

    private void registration(Check c) {
        c.ok(GlowSquidContent.glowInkSac != null && GlowSquidContent.glowItemFrame != null, "items registered");
        c.eq(64, new ItemStack(GlowSquidContent.glowInkSac).getMaxStackSize(), "glow ink sacs stack to 64");
        c.eq(Content.id("glow_squid"), EntityList.getKey(EntityGlowSquid.class), "glow squid entity id");
        c.eq(Content.id("glow_item_frame"), EntityList.getKey(EntityGlowItemFrame.class), "glow item frame entity id");
        c.eq(GlowSquidContent.GLOW_SQUID_ID, EntityRegistry.instance().lookupModSpawn(EntityGlowSquid.class, false).getModEntityId(), "glow squid network id");
        c.eq(GlowSquidContent.GLOW_ITEM_FRAME_ID, EntityRegistry.instance().lookupModSpawn(EntityGlowItemFrame.class, false).getModEntityId(), "frame network id");
        c.ok(EntityList.ENTITY_EGGS.containsKey(Content.id("glow_squid")), "glow squid has a spawn egg");
        c.ok(!EntityList.ENTITY_EGGS.containsKey(Content.id("glow_item_frame")), "the frame has none");

        EnumCreatureType group = GlowSquidContent.undergroundWater;
        c.ok(group != null && group != EnumCreatureType.WATER_CREATURE, "the underground water creature group exists");
        if (group != null) {
            c.eq(5, group.getMaxNumberOfCreature(), "its cap is 5");
            c.ok(group.getPeacefulCreature() && !group.getAnimal(), "peaceful, despawning");
            c.ok(spawns(Biomes.PLAINS, group) && spawns(Biomes.OCEAN, group) && spawns(Biomes.MUSHROOM_ISLAND, group), "spawns in overworld biomes");
            c.ok(!spawns(Biomes.HELL, group) && !spawns(Biomes.SKY, group) && !spawns(Biomes.VOID, group), "not in the Nether, the End or the void");
            c.ok(!spawns(Biomes.PLAINS, EnumCreatureType.WATER_CREATURE), "not in the squid's own list");
            // The vanilla lists still answer (the switch in Biome takes its cases and its default).
            c.ok(!Biomes.PLAINS.getSpawnableList(EnumCreatureType.MONSTER).isEmpty() && !Biomes.OCEAN.getSpawnableList(EnumCreatureType.WATER_CREATURE).isEmpty(),
                    "vanilla spawn lists unharmed");
        }
        c.eq(EntityLiving.SpawnPlacementType.IN_WATER, EntitySpawnPlacementRegistry.getPlacementForEntity(EntityGlowSquid.class), "spawns in water");
        Crafting.expect(c, Crafting.grid(2, 1, new ItemStack(Items.ITEM_FRAME), new ItemStack(GlowSquidContent.glowInkSac)),
                new ItemStack(GlowSquidContent.glowItemFrame), "item frame + glow ink sac");
    }

    private EntityGlowSquid squidAt(Check c, BlockPos pos) {
        EntityGlowSquid squid = new EntityGlowSquid(c.world);
        squid.setPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return squid;
    }

    private void spawning(Check c) {
        BlockPos corner = c.area();
        // A sealed pool deep underground, and an open one at y=100.
        BlockPos low = new BlockPos(corner.getX(), 14, corner.getZ());
        c.fill(low, low.add(8, 8, 8), Blocks.STONE.getDefaultState());
        c.fill(low.add(1, 1, 1), low.add(7, 7, 7), Blocks.WATER.getDefaultState());
        BlockPos deep = low.add(4, 4, 4);
        for (BlockPos p : BlockPos.getAllInBox(low, low.add(8, 8, 8))) {
            c.world.checkLight(p);
        }
        c.ok(squidAt(c, deep).getCanSpawnHere(), "spawns in lightless water at y=18");
        // The spawn list as such: 1.18's per-player cap (changes.localMobCap) offers nothing with no player near.
        boolean localCap = ModConfig.changes.localMobCap;
        ModConfig.changes.localMobCap = false;
        try {
            Biome.SpawnListEntry offered = c.world.getSpawnListEntryForTypeAt(GlowSquidContent.spawnGroup(), deep);
            c.ok(offered != null && offered.entityClass == EntityGlowSquid.class && c.world.canCreatureTypeSpawnHere(GlowSquidContent.spawnGroup(), offered, deep),
                    "the world offers glow squid there");
        } finally {
            ModConfig.changes.localMobCap = localCap;
        }
        c.world.setBlockState(low.add(2, 4, 4), Blocks.GLOWSTONE.getDefaultState(), 3);
        c.ok(!squidAt(c, deep).getCanSpawnHere(), "not where a light reaches");
        c.world.setBlockState(low.add(2, 4, 4), Blocks.WATER.getDefaultState(), 3);
        c.ok(squidAt(c, deep).getCanSpawnHere(), "again once the light is gone");
        c.ok(!squidAt(c, low.add(4, 8, 4)).getCanSpawnHere(), "not in stone");

        BlockPos high = corner.add(4, 0, 4);
        c.fill(high.add(-1, 0, -1), high.add(1, 2, 1), Blocks.WATER.getDefaultState());
        c.ok(!squidAt(c, high.up()).getCanSpawnHere(), "not in water at y=101");
        c.fill(high.add(-1, 0, -1), high.add(1, 2, 1), Blocks.AIR.getDefaultState());
        // A pool at y=31 is one block too high (sea level 63 - 33 = 30).
        BlockPos edge = new BlockPos(corner.getX() + 16, 26, corner.getZ());
        c.fill(edge, edge.add(4, 8, 4), Blocks.STONE.getDefaultState());
        c.fill(edge.add(1, 1, 1), edge.add(3, 7, 3), Blocks.WATER.getDefaultState());
        for (BlockPos p : BlockPos.getAllInBox(edge, edge.add(4, 8, 4))) {
            c.world.checkLight(p);
        }
        c.ok(squidAt(c, new BlockPos(edge.getX() + 2, 30, edge.getZ() + 2)).getCanSpawnHere(), "spawns at y=30");
        c.ok(!squidAt(c, new BlockPos(edge.getX() + 2, 31, edge.getZ() + 2)).getCanSpawnHere(), "not at y=31");
    }

    private void squid(Check c) {
        BlockPos at = c.area().add(4, 1, 4);
        EntityPlayerMP player = Players.real(c.world, "glowsquid");
        player.setPosition(at.getX() + 2.5, at.getY(), at.getZ() + 0.5);

        EntityGlowSquid squid = squidAt(c, at);
        c.world.spawnEntity(squid);
        c.ok(squid instanceof EntitySquid, "a glow squid is a squid (guardians hunt it)");
        c.eq(10.0F, squid.getMaxHealth(), "10 health");
        c.ok(squid.isCreatureType(GlowSquidContent.spawnGroup(), true), "counts against its own group");
        c.ok(!squid.isCreatureType(EnumCreatureType.WATER_CREATURE, true), "not against the squid's");
        c.eq(0, squid.getDarkTicks(), "glows at first");

        c.ok(squid.attackEntityFrom(DamageSource.GENERIC, 1.0F), "takes damage");
        c.eq(0, squid.getDarkTicks(), "damage from nobody leaves it glowing");
        squid.hurtResistantTime = 0;
        c.ok(squid.attackEntityFrom(DamageSource.causePlayerDamage(player), 1.0F), "a player's hit lands");
        c.eq(EntityGlowSquid.DARK_TIME, squid.getDarkTicks(), "and darkens it for 100 ticks");
        squid.onLivingUpdate();
        c.eq(EntityGlowSquid.DARK_TIME - 1, squid.getDarkTicks(), "which count down");

        NBTTagCompound tag = new NBTTagCompound();
        squid.writeToNBT(tag);
        c.eq(EntityGlowSquid.DARK_TIME - 1, tag.getInteger("DarkTicksRemaining"), "DarkTicksRemaining is saved");
        EntityGlowSquid copy = new EntityGlowSquid(c.world);
        copy.readFromNBT(tag);
        c.eq(EntityGlowSquid.DARK_TIME - 1, copy.getDarkTicks(), "and read back");
        for (int i = 0; i < 200; i++) {
            squid.setDarkTicks(Math.max(0, squid.getDarkTicks() - 1));
        }
        c.eq(0, squid.getDarkTicks(), "never below zero");

        // Killed by a player, it drops glow ink sacs and no ink sacs.
        squid.hurtResistantTime = 0;
        squid.attackEntityFrom(DamageSource.causePlayerDamage(player), 100.0F);
        c.ok(!squid.isEntityAlive(), "dies");
        int sacs = 0, other = 0;
        for (EntityItem e : c.world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(at).grow(3))) {
            if (e.getItem().getItem() == GlowSquidContent.glowInkSac) {
                sacs += e.getItem().getCount();
            } else {
                other++;
            }
            e.setDead();
        }
        c.ok(sacs >= 1 && sacs <= 3 && other == 0, "drops 1-3 glow ink sacs, got " + sacs + " and " + other + " other stacks");
        squid.setDead();
    }

    private void loot(Check c) {
        LootTable table = c.world.getLootTableManager().getLootTableFromLocation(GlowSquidContent.LOOT);
        c.ok(table != LootTable.EMPTY_LOOT_TABLE, "loot table loads");
        Random r = new Random(17);
        int[] seen = new int[5];
        boolean only = true;
        for (int i = 0; i < 300; i++) {
            int n = 0;
            for (ItemStack s : table.generateLootForPools(r, new LootContext.Builder(c.world).build())) {
                only &= s.getItem() == GlowSquidContent.glowInkSac;
                n += s.getCount();
            }
            seen[Math.min(n, 4)]++;
        }
        c.ok(only && seen[0] == 0 && seen[4] == 0 && seen[1] > 50 && seen[2] > 50 && seen[3] > 50,
                "1, 2 or 3 sacs each time: " + seen[1] + "/" + seen[2] + "/" + seen[3] + ", none " + seen[0] + ", more " + seen[4]);
    }

    private static int count(Check c, BlockPos at, Item item) {
        int n = 0;
        for (EntityItem e : c.world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(at).grow(3))) {
            if (!e.isDead && e.getItem().getItem() == item) {
                n += e.getItem().getCount();
            }
        }
        return n;
    }

    private static void clearItems(Check c, BlockPos at) {
        for (EntityItem e : c.world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(at).grow(3))) {
            e.setDead();
        }
    }

    private static EntityGlowItemFrame frameAt(Check c, BlockPos at) {
        List<EntityGlowItemFrame> frames = c.world.getEntitiesWithinAABB(EntityGlowItemFrame.class, new AxisAlignedBB(at));
        return frames.isEmpty() ? null : frames.get(0);
    }

    private void frame(Check c) {
        BlockPos wall = c.area().add(8, 2, 8);
        c.world.setBlockState(wall, Blocks.STONE.getDefaultState(), 3);
        BlockPos front = wall.north();
        EntityPlayerMP player = Players.real(c.world, "glowframe");
        player.setPosition(front.getX() + 0.5, front.getY() - 1, front.getZ() - 1.5);
        player.interactionManager.setGameType(GameType.SURVIVAL);
        Item frameItem = GlowSquidContent.glowItemFrame;

        player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(frameItem, 3));
        c.eq(EnumActionResult.FAIL, frameItem.onItemUse(player, c.world, wall, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 1.0F, 0.5F), "not on top of a block");
        c.eq(EnumActionResult.SUCCESS, frameItem.onItemUse(player, c.world, wall, EnumHand.MAIN_HAND, EnumFacing.NORTH, 0.5F, 0.5F, 0.0F), "on its side");
        EntityGlowItemFrame frame = frameAt(c, front);
        if (!c.ok(frame != null, "a glow item frame hangs there")) {
            return;
        }
        c.eq(2, player.getHeldItemMainhand().getCount(), "one frame used");
        c.eq(EnumFacing.NORTH, frame.facingDirection, "faces out of the wall");
        c.ok(frame instanceof EntityItemFrame, "it is an item frame to everything else");
        frameItem.onItemUse(player, c.world, wall, EnumHand.MAIN_HAND, EnumFacing.NORTH, 0.5F, 0.5F, 0.0F);
        c.eq(2, player.getHeldItemMainhand().getCount(), "a second frame does not fit in the same place");
        c.eq(frameItem, frame.getPickedResult(null).getItem(), "picked as a glow item frame");

        // What the client is told when it starts seeing the frame.
        ByteBuf buf = Unpooled.buffer();
        frame.writeSpawnData(buf);
        EntityGlowItemFrame seen = new EntityGlowItemFrame(c.world);
        seen.setLocationAndAngles(frame.posX, frame.posY, frame.posZ, frame.rotationYaw, frame.rotationPitch);
        seen.readSpawnData(buf);
        c.eq(front, seen.getHangingPosition(), "spawn data: where it hangs");
        c.eq(EnumFacing.NORTH, seen.facingDirection, "spawn data: its facing");
        c.eq(frame.getEntityBoundingBox(), seen.getEntityBoundingBox(), "spawn data: the same box");

        // Filling, rotating, saving.
        player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 2));
        frame.processInitialInteract(player, EnumHand.MAIN_HAND);
        c.eq(Items.DIAMOND, frame.getDisplayedItem().getItem(), "takes an item");
        c.eq(1, player.getHeldItemMainhand().getCount(), "from the player's hand");
        c.eq(1, frame.getAnalogOutput(), "comparator reads 1");
        frame.processInitialInteract(player, EnumHand.MAIN_HAND);
        c.eq(2, frame.getAnalogOutput(), "and 2 once turned");
        c.eq(Items.DIAMOND, frame.getPickedResult(null).getItem(), "picked as its contents when full");
        NBTTagCompound tag = new NBTTagCompound();
        c.ok(frame.writeToNBTOptional(tag), "saves");
        c.eq(Content.id("glow_item_frame").toString(), tag.getString("id"), "under its own id");
        Entity loaded = EntityList.createEntityFromNBT(tag, c.world);
        c.ok(loaded instanceof EntityGlowItemFrame && ((EntityGlowItemFrame) loaded).getDisplayedItem().getItem() == Items.DIAMOND
                && ((EntityGlowItemFrame) loaded).getRotation() == 1 && loaded.getHorizontalFacing() == EnumFacing.NORTH, "and loads as a glow frame with its item");

        // Hit once: the item comes out. Hit again: the frame comes off, as a glow item frame.
        clearItems(c, front);
        frame.attackEntityFrom(DamageSource.causePlayerDamage(player), 1.0F);
        c.ok(frame.isEntityAlive() && frame.getDisplayedItem().isEmpty(), "a hit empties it");
        c.eq(1, count(c, front, Items.DIAMOND), "the item drops");
        c.eq(0, count(c, front, frameItem), "the frame stays");
        frame.attackEntityFrom(DamageSource.causePlayerDamage(player), 1.0F);
        c.ok(!frame.isEntityAlive(), "a second hit breaks it");
        c.eq(1, count(c, front, frameItem), "it drops a glow item frame");
        c.eq(0, count(c, front, Items.ITEM_FRAME), "not an item frame");
        clearItems(c, front);

        // Creative players get nothing back; a frame whose wall goes falls off with its contents.
        frame = new EntityGlowItemFrame(c.world, front, EnumFacing.NORTH);
        c.world.spawnEntity(frame);
        frame.setDisplayedItem(new ItemStack(Items.EMERALD));
        player.interactionManager.setGameType(GameType.CREATIVE);
        frame.attackEntityFrom(DamageSource.causePlayerDamage(player), 1.0F);
        frame.attackEntityFrom(DamageSource.causePlayerDamage(player), 1.0F);
        c.ok(!frame.isEntityAlive() && count(c, front, frameItem) == 0 && count(c, front, Items.EMERALD) == 0, "nothing drops for a creative player");
        player.interactionManager.setGameType(GameType.SURVIVAL);

        frame = new EntityGlowItemFrame(c.world, front, EnumFacing.NORTH);
        c.world.spawnEntity(frame);
        frame.setDisplayedItem(new ItemStack(Items.EMERALD));
        c.world.setBlockState(wall, Blocks.AIR.getDefaultState(), 3);
        for (int i = 0; i < 101 && frame.isEntityAlive(); i++) {
            frame.onUpdate();
        }
        c.ok(!frame.isEntityAlive(), "falls off when its wall is gone");
        c.ok(count(c, front, frameItem) == 1 && count(c, front, Items.EMERALD) == 1, "dropping itself and its item");
        clearItems(c, front);
    }

    private static EnumActionResult click(Check c, EntityPlayerMP player, BlockPos pos) {
        return player.interactionManager.processRightClickBlock(player, c.world, player.getHeldItemMainhand(), EnumHand.MAIN_HAND,
                pos, EnumFacing.UP, 0.5F, 0.5F, 0.5F);
    }

    private void signs(Check c) {
        BlockPos pos = c.area().add(8, 1, 8);
        c.world.setBlockState(pos, Blocks.STANDING_SIGN.getDefaultState().withProperty(BlockStandingSign.ROTATION, 4), 3);
        TileEntitySign sign = (TileEntitySign) c.world.getTileEntity(pos);
        sign.signText[0] = new TextComponentString("Glow");
        EntityPlayerMP player = Players.real(c.world, "glowsign");
        player.setPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() - 1.5);
        player.interactionManager.setGameType(GameType.SURVIVAL);
        ItemStack glowSacs = new ItemStack(GlowSquidContent.glowInkSac, 3);
        ItemStack inkSacs = new ItemStack(Items.DYE, 3, 0);

        c.ok(!GlowingText.get(sign), "a new sign does not glow");
        player.setHeldItem(EnumHand.MAIN_HAND, inkSacs.copy());
        click(c, player, pos);
        c.ok(!GlowingText.get(sign) && player.getHeldItemMainhand().getCount() == 3, "an ink sac does nothing to it");

        player.setHeldItem(EnumHand.MAIN_HAND, glowSacs.copy());
        player.setSneaking(true);
        click(c, player, pos);
        c.ok(!GlowingText.get(sign) && player.getHeldItemMainhand().getCount() == 3, "nor a glow ink sac while sneaking");
        player.setSneaking(false);
        ModConfig.glowSquid.glowingSigns = false;
        click(c, player, pos);
        c.ok(!GlowingText.get(sign), "nor with glowing signs switched off");
        ModConfig.glowSquid.glowingSigns = true;

        boolean had = Advancements.has(player, "husbandry/make_a_sign_glow");
        c.eq(EnumActionResult.SUCCESS, click(c, player, pos), "a glow ink sac is used on it");
        c.ok(GlowingText.get(sign), "and the sign glows");
        c.eq(2, player.getHeldItemMainhand().getCount(), "one sac used up");
        c.ok(had || Advancements.has(player, "husbandry/make_a_sign_glow"), "Glow and Behold!");
        click(c, player, pos);
        c.eq(2, player.getHeldItemMainhand().getCount(), "a second sac is not wasted");

        // The glow is in the sign's own NBT, and so in what the server sends about the sign.
        NBTTagCompound saved = sign.writeToNBT(new NBTTagCompound());
        c.ok(saved.getCompoundTag("ForgeData").getBoolean("GlowingText"), "saved as ForgeData.GlowingText");
        c.eq("minecraft:sign", saved.getString("id"), "the sign is still a vanilla sign");
        c.ok(sign.getUpdateTag().getCompoundTag("ForgeData").getBoolean("GlowingText"), "in the chunk's copy of the sign");
        c.ok(sign.getUpdatePacket() != null, "and its update packet is made from the same tag");
        TileEntitySign client = new TileEntitySign();
        client.readFromNBT(sign.getUpdateTag());
        c.ok(GlowingText.get(client), "a sign read from that packet glows");
        c.eq("Glow", client.signText[0].getUnformattedText(), "with its text");

        player.setHeldItem(EnumHand.MAIN_HAND, inkSacs.copy());
        c.eq(EnumActionResult.SUCCESS, click(c, player, pos), "an ink sac is used on a glowing sign");
        c.ok(!GlowingText.get(sign) && player.getHeldItemMainhand().getCount() == 2, "and the glow is gone");
        c.ok(!sign.writeToNBT(new NBTTagCompound()).getCompoundTag("ForgeData").hasKey("GlowingText"), "from its NBT too");
        client.readFromNBT(sign.getUpdateTag());
        c.ok(!GlowingText.get(client), "and from the client's sign");
        c.eq("Glow", sign.signText[0].getUnformattedText(), "the text is untouched");

        // Creative mode uses nothing up; wall signs work the same; a bone meal (another dye) does nothing.
        player.interactionManager.setGameType(GameType.CREATIVE);
        player.setHeldItem(EnumHand.MAIN_HAND, glowSacs.copy());
        click(c, player, pos);
        c.ok(GlowingText.get(sign) && player.getHeldItemMainhand().getCount() == 3, "creative players keep the sac");
        player.interactionManager.setGameType(GameType.SURVIVAL);
        player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.DYE, 3, 15));
        click(c, player, pos);
        c.ok(GlowingText.get(sign), "another dye leaves the glow");

        BlockPos support = pos.add(3, 0, 0);
        c.world.setBlockState(support, Blocks.STONE.getDefaultState(), 3);
        BlockPos wallPos = support.north();
        c.world.setBlockState(wallPos, Blocks.WALL_SIGN.getDefaultState().withProperty(BlockWallSign.FACING, EnumFacing.NORTH), 3);
        TileEntitySign wallSign = (TileEntitySign) c.world.getTileEntity(wallPos);
        player.setHeldItem(EnumHand.MAIN_HAND, glowSacs.copy());
        click(c, player, wallPos);
        c.ok(GlowingText.get(wallSign), "wall signs glow too");
        // A broken sign takes its glow with it.
        c.world.setBlockToAir(wallPos);
        c.world.setBlockState(wallPos, Blocks.WALL_SIGN.getDefaultState().withProperty(BlockWallSign.FACING, EnumFacing.NORTH), 3);
        c.ok(!GlowingText.get((TileEntitySign) c.world.getTileEntity(wallPos)), "a new sign in its place does not");
    }
}
