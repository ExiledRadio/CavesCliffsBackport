package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.height.HeightContent;
import com.exiledradio.cavesandcliffsbackport.height.HeightHooks;
import com.exiledradio.cavesandcliffsbackport.height.HeightLayout;
import com.exiledradio.cavesandcliffsbackport.height.Heights;
import com.exiledradio.cavesandcliffsbackport.height.asm.PatchReport;
import io.netty.buffer.Unpooled;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.network.play.server.SPacketMultiBlockChange;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Stream H (EXTENDED_HEIGHT section 5.2): the patched paths in a Caves & Cliffs overworld, and the Nether as a world
 * that must behave as vanilla. Run on a server whose {@code level-type} is {@code cavesandcliffs} with a fresh world
 * ({@code tools/height/selftest.sh}).
 */
public final class HeightSelfTest implements SelfTest {

    /** Far from every other test's areas, in chunks nothing else touches. */
    private static final int X = 40000;
    private static final int Z = 40000;

    @Override
    public String name() {
        return "height";
    }

    @Override
    public void run(Check check) throws Exception {
        WorldServer world = check.world;
        report(check);
        String asked = System.getProperty(CavesAndCliffsBackport.MOD_ID + ".selftest", "");
        boolean demanded = java.util.Arrays.asList(asked.split(",")).contains("height");
        if (!Heights.isExtended(world)) {
            // In the whole suite's normal world: the overworld too must be vanilla's 0-255.
            check.ok(!demanded, "the overworld has extended height (run tools/height/selftest.sh: level-type "
                    + "cavesandcliffs on a fresh world; refusal: " + HeightContent.refusal() + ")");
            check.eq(0, Heights.minY(world), "a Default overworld's min y");
            check.eq(256, world.getHeight(), "a Default overworld's World.getHeight()");
            check.eq(16, world.getChunk(new BlockPos(X, 0, Z)).getBlockStorageArray().length, "a Default overworld's sections");
            // Portals as vanilla's: one lit from its middle row at y 70 lights.
            prepare(world, X + 400, Z, 2);
            BlockPos portal = frame(world, X + 400, 70, Z);
            world.setBlockState(portal.up(), Blocks.FIRE.getDefaultState(), 3);
            check.eq(6, portalBlocks(world, portal), "portal blocks in a frame lit at y 70 in a Default world");
            vanillaWorld(check);
            return;
        }
        // Light updates need the chunks 17 blocks around loaded, and a chunk is sent to players only once it has
        // been populated and ticked: load (and tick) the areas the checks use.
        prepare(world, X, Z, 6);
        prepare(world, X + 200, Z, 5);
        ranges(check, world);
        generated(check, world);
        everyHeight(check, world);
        light(check, world);
        nbt(check, world);
        packet(check, world);
        packets(check, world);
        voidFall(check, world);
        entities(check, world);
        removalMarkers(check, world);
        portals(check, world);
        vanillaWorld(check);
    }

    // ---------------------------------------------------------------------------------------------
    // Nether portals at every height (1.18.2): lit anywhere from -64 to 319, found over the world's whole height,
    // and built within the world's bounds.
    // ---------------------------------------------------------------------------------------------

    private static void portals(Check check, WorldServer world) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        int x = X + 400;
        int lowZ = Z;
        int highZ = Z + 600;
        int roomZ = Z - 600;
        prepare(world, x, lowZ, 3);
        prepare(world, x, highZ, 3);
        prepare(world, x, roomZ, 3);

        // Lit from the frame's middle row: the lighter walks down to the frame's bottom (vanilla stops at y 0).
        BlockPos low = frame(world, x, -60, lowZ);
        world.setBlockState(low.up(), Blocks.FIRE.getDefaultState(), 3);
        check.eq(6, portalBlocks(world, low), "portal blocks in a frame lit at y -60 (fire in its middle row)");
        BlockPos high = frame(world, x, 300, highZ);
        world.setBlockState(high.up(), Blocks.FIRE.getDefaultState(), 3);
        check.eq(6, portalBlocks(world, high), "portal blocks in a frame lit at y 300");

        // Zombie pigmen come out of a portal below 0 as anywhere (vanilla required the portal above 0).
        int pigmen = world.getEntitiesWithinAABB(net.minecraft.entity.monster.EntityPigZombie.class,
                new net.minecraft.util.math.AxisAlignedBB(low).grow(4)).size();
        Blocks.PORTAL.updateTick(world, low.up(), world.getBlockState(low.up()), new Random() {
            @Override
            public int nextInt(int bound) {
                return 0;
            }
        });
        check.eq(pigmen + 1, world.getEntitiesWithinAABB(net.minecraft.entity.monster.EntityPigZombie.class,
                new net.minecraft.util.math.AxisAlignedBB(low).grow(4)).size(), "a zombie pigman came out of the portal at y -60");

        // To the Nether and back: the return trip finds the portal below 0.
        net.minecraft.entity.passive.EntityPig pig = new net.minecraft.entity.passive.EntityPig(world);
        pig.setLocationAndAngles(low.getX() + 0.5D, low.getY(), low.getZ() + 0.5D, 0F, 0F);
        world.spawnEntity(pig);
        pig.setPortal(low);
        net.minecraft.entity.Entity there = pig.changeDimension(-1);
        if (!check.ok(there != null && there.dimension == -1, "the pig went through the portal at y -60 to the Nether")) {
            return;
        }
        WorldServer nether = server.getWorld(-1);
        net.minecraft.world.Teleporter netherPortals = nether.getDefaultTeleporter();
        check.ok(netherPortals.makePortal(there), "a Nether portal was made for it");
        netherPortals.placeInExistingPortal(there, there.rotationYaw);
        BlockPos inNether = new BlockPos(there);
        if (!check.eq(Blocks.PORTAL, nether.getBlockState(inNether).getBlock(), "the pig stands in the Nether portal")) {
            return;
        }
        there.setPortal(inNether);
        net.minecraft.entity.Entity back = there.changeDimension(0);
        check.ok(back != null && back.dimension == 0 && Math.abs(back.posX - (low.getX() + 1)) < 2
                        && Math.abs(back.posZ - (low.getZ() + 0.5D)) < 2 && back.posY >= -60 && back.posY < -57,
                "back in the overworld at the portal below 0 (arrived at " + (back == null ? "nowhere" : back.getPosition()) + ")");
        CavesAndCliffsBackport.LOGGER.info("SELFTEST height: portal trip y -60 -> Nether {} -> {}", inNether,
                back == null ? null : back.getPosition());
        if (back != null) {
            back.setDead();
        }

        // A portal made for an arrival can be built below 0: in a room at y -50, around the arriving entity.
        BlockPos room = new BlockPos(x, -50, roomZ);
        for (BlockPos p : BlockPos.getAllInBox(room.add(-6, 0, -6), room.add(6, 5, 6))) {
            world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
        }
        for (BlockPos p : BlockPos.getAllInBox(room.add(-6, -1, -6), room.add(6, -1, 6))) {
            world.setBlockState(p, Blocks.STONE.getDefaultState(), 2);
        }
        net.minecraft.entity.passive.EntityPig arriving = new net.minecraft.entity.passive.EntityPig(world);
        arriving.setLocationAndAngles(room.getX() + 0.5D, room.getY(), room.getZ() + 0.5D, 0F, 0F);
        check.ok(world.getDefaultTeleporter().makePortal(arriving), "a portal was made for an arrival at y -50");
        int lowest = Integer.MAX_VALUE;
        int count = 0;
        for (BlockPos p : BlockPos.getAllInBox(room.add(-16, -14, -16), room.add(16, 20, 16))) {
            if (world.getBlockState(p).getBlock() == Blocks.PORTAL) {
                count++;
                lowest = Math.min(lowest, p.getY());
            }
        }
        check.ok(count == 6 && lowest >= -50 && lowest < -44, "the arrival's portal is built in the room at y -50 ("
                + count + " portal blocks, lowest y " + (count == 0 ? "none" : lowest) + ")");
    }

    /**
     * An obsidian frame along x, 2 wide and 3 high inside, its inside's bottom left at (x, y, z), cleared around;
     * answers that corner.
     */
    private static BlockPos frame(WorldServer world, int x, int y, int z) {
        for (BlockPos p : BlockPos.getAllInBox(new BlockPos(x - 2, y - 1, z - 2), new BlockPos(x + 3, y + 4, z + 2))) {
            world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
        }
        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = -1; dy <= 3; dy++) {
                if (dx == -1 || dx == 2 || dy == -1 || dy == 3) {
                    world.setBlockState(new BlockPos(x + dx, y + dy, z), Blocks.OBSIDIAN.getDefaultState(), 2);
                }
            }
        }
        return new BlockPos(x, y, z);
    }

    private static int portalBlocks(WorldServer world, BlockPos corner) {
        int n = 0;
        for (BlockPos p : BlockPos.getAllInBox(corner, corner.add(1, 2, 0))) {
            if (world.getBlockState(p).getBlock() == Blocks.PORTAL) {
                n++;
            }
        }
        return n;
    }

    /**
     * Blocks by the spawn (whose chunks a server keeps loaded) for the removal test
     * ({@code tools/height/removal.sh}): the ones inside 0-255 must still be there on a server without this mod, which
     * also shows the chunk was read and not regenerated; the ones outside are gone with it.
     */
    private static void removalMarkers(Check check, WorldServer world) throws Exception {
        BlockPos spawn = world.getSpawnPoint();
        int x = spawn.getX() + 2;
        int z = spawn.getZ() + 2;
        Object[][] marks = {{0, Blocks.GOLD_BLOCK, "minecraft:gold_block"}, {200, Blocks.GLASS, "minecraft:glass"},
                {255, Blocks.COBBLESTONE, "minecraft:cobblestone"}, {-10, Blocks.DIAMOND_BLOCK, "minecraft:diamond_block"},
                {300, Blocks.IRON_BLOCK, "minecraft:iron_block"}};
        java.io.PrintWriter out = new java.io.PrintWriter(world.getMinecraftServer().getFile("height-markers.txt"), "UTF-8");
        for (Object[] m : marks) {
            BlockPos pos = new BlockPos(x, (Integer) m[0], z);
            world.setBlockState(pos, ((net.minecraft.block.Block) m[1]).getDefaultState(), 2);
            check.eq(m[1], world.getBlockState(pos).getBlock(), "removal marker at y " + m[0]);
            out.println(x + " " + m[0] + " " + z + " " + m[2]);
        }
        out.close();
    }

    private static void prepare(WorldServer world, int x, int z, int radius) {
        int cx = x >> 4;
        int cz = z >> 4;
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                world.getChunk(cx + dx, cz + dz);
            }
        }
        for (int pass = 0; pass < 2; pass++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    world.getChunk(cx + dx, cz + dz).onTick(false);
                }
            }
        }
    }

    /** The pattern-drift report: every site found. */
    private static void report(Check check) {
        check.ok(PatchReport.size() > 40, "the drift report lists the patch sites (" + PatchReport.size() + ")");
        check.eq(new ArrayList<String>(), PatchReport.missed(), "patch sites not found");
        check.ok(HeightContent.allowed(), "extended height is on (refusal: " + HeightContent.refusal() + ")");
    }

    private static void ranges(Check check, WorldServer world) {
        check.eq(-64, Heights.minY(world), "overworld min y");
        check.eq(320, Heights.maxY(world), "overworld max y");
        check.eq(24, Heights.sections(world), "overworld sections");
        check.eq(320, world.getHeight(), "World.getHeight()");
        check.eq(320, world.getActualHeight(), "World.getActualHeight()");
        check.ok(!world.isOutsideBuildHeight(new BlockPos(0, -64, 0)), "y -64 is inside");
        check.ok(world.isOutsideBuildHeight(new BlockPos(0, -65, 0)), "y -65 is outside");
        check.ok(!world.isOutsideBuildHeight(new BlockPos(0, 319, 0)), "y 319 is inside");
        check.ok(world.isOutsideBuildHeight(new BlockPos(0, 320, 0)), "y 320 is outside");
        check.eq(-1, Heights.slot(world, -65), "slot of y -65");
        check.eq(20, Heights.slot(world, -64), "slot of y -64");
        check.eq(19, Heights.slot(world, 319), "slot of y 319");
        File data = new File(new File(world.getSaveHandler().getWorldDirectory(), "data"), HeightHooks.DATA_FILE);
        check.ok(data.isFile(), "the world's height is saved in " + data);
        EntityPlayerMP player = check.player("height", new BlockPos(X, 100, Z));
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        check.eq(server.getBuildLimit() >= 256 ? 320 : server.getBuildLimit(), Heights.buildLimit(server, player),
                "the build limit in the extended overworld");
        Random random = new Random(42L);
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int i = 0; i < 2000; i++) {
            int y = Heights.spawnY(random, 80, world);
            low = Math.min(low, y);
            high = Math.max(high, y);
        }
        check.ok(low == -64 && high == 79, "spawn attempts draw y from -64 to 79 (got " + low + ".." + high + ")");
    }

    /** A chunk the Caves & Cliffs type generated: 24 sections, bedrock at -64, stone above it. */
    private static void generated(Check check, WorldServer world) {
        Chunk chunk = world.getChunk(new BlockPos(X + 64, 0, Z));
        check.eq(24, chunk.getBlockStorageArray().length, "a generated chunk's section count");
        int bedrock = 0;
        int stone = 0;
        int solid = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                bedrock += chunk.getBlockState(x, -64, z).getBlock() == Blocks.BEDROCK ? 1 : 0;
                stone += chunk.getBlockState(x, -32, z).getBlock() == Blocks.STONE ? 1 : 0;
                // Vanilla population reaches a little below 0 here (ore veins and dirt or gravel blobs around low
                // centres, a dungeon's floor at y -1), as it would in any world that holds those heights.
                solid += chunk.getBlockState(x, -1, z).getMaterial().blocksMovement() ? 1 : 0;
            }
        }
        check.eq(256, bedrock, "bedrock columns at y -64");
        if (com.exiledradio.cavesandcliffsbackport.terrain.ModernTerrain.owns(world)) {
            // 1.18 terrain: deepslate, caves and aquifers below 0 (TerrainSelfTest checks it block for block).
            int deep = 0;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    deep += chunk.getBlockState(x, -32, z).getMaterial().blocksMovement() ? 1 : 0;
                }
            }
            check.ok(deep > 0, "rock at y -32 (" + deep + " columns)");
        } else {
            check.eq(256, stone, "stone columns at y -32");
            check.eq(256, solid, "solid columns at y -1");
        }
        check.eq(Blocks.AIR.getDefaultState(), chunk.getBlockState(0, -65, 0), "below the world is air");
        for (int slot = HeightLayout.FIRST_LOW_SLOT; slot < 24; slot++) {
            ExtendedBlockStorage section = chunk.getBlockStorageArray()[slot];
            check.ok(section != null && section.getYLocation() == HeightLayout.slotBase(slot),
                    "slot " + slot + " holds the section at y " + HeightLayout.slotBase(slot));
        }
    }

    /** Set and read a block at every height, and refuse one outside. */
    private static void everyHeight(Check check, WorldServer world) {
        int wrong = 0;
        for (int y = -64; y < 320; y++) {
            BlockPos pos = new BlockPos(X, y, Z);
            IBlockState want = (y & 1) == 0 ? Blocks.GLASS.getDefaultState() : Blocks.COBBLESTONE.getDefaultState();
            boolean set = world.setBlockState(pos, want, 2);
            if (!set || world.getBlockState(pos) != want || world.getChunk(pos).getBlockState(pos) != want) {
                wrong++;
                if (wrong < 5) {
                    check.fail("block at y " + y + ": set " + set + ", read " + world.getBlockState(pos));
                }
            }
        }
        check.eq(0, wrong, "heights where a block did not stick (of 384)");
        check.ok(!world.setBlockState(new BlockPos(X, -65, Z), Blocks.STONE.getDefaultState(), 2), "no block at y -65");
        check.ok(!world.setBlockState(new BlockPos(X, 320, Z), Blocks.STONE.getDefaultState(), 2), "no block at y 320");
        check.eq(Blocks.AIR.getDefaultState(), world.getBlockState(new BlockPos(X, 320, Z)), "y 320 reads air");
        Chunk chunk = world.getChunk(new BlockPos(X, 0, Z));
        check.eq(320, chunk.getHeightValue(X & 15, Z & 15), "height map over a column built to y 319");
        check.eq(304, chunk.getTopFilledSegment(), "top filled segment is the section at y 304");
        check.ok(!chunk.isEmptyBetween(-64, -49), "the section at y -64 is not empty");
    }

    /** Sky light at -64 under open sky; block light at 319 and in a pocket below 0. */
    private static void light(Check check, WorldServer world) {
        int x = X + 3;
        for (int y = 319; y >= -64; y--) {
            world.setBlockState(new BlockPos(x, y, Z), Blocks.AIR.getDefaultState(), 3);
        }
        BlockPos bottom = new BlockPos(x, -64, Z);
        Chunk chunk = world.getChunk(bottom);
        check.eq(-64, chunk.getHeightValue(x & 15, Z & 15), "height map of an open column");
        check.eq(0, world.getHeight(x, Z), "World.getHeight of a column open below 0 is 1.12's 0, never negative");
        check.eq(0, world.getHeight(bottom).getY(), "World.getHeight(BlockPos) of that column too");
        check.eq(320, world.getHeight(X, Z), "World.getHeight of a column built to y 319");
        check.ok(world.canSeeSky(bottom), "y -64 sees the sky up an open column");
        check.eq(15, world.getLightFor(EnumSkyBlock.SKY, bottom), "sky light at y -64 under open sky");
        check.eq(15, world.getLightFor(EnumSkyBlock.SKY, new BlockPos(x, 0, Z)), "sky light at y 0 in the open column");
        check.eq(0, world.getLightFor(EnumSkyBlock.SKY, new BlockPos(X + 64, -40, Z)), "no sky light inside the deep stone");

        BlockPos top = new BlockPos(X + 6, 319, Z);
        world.setBlockState(top, Blocks.GLOWSTONE.getDefaultState(), 3);
        check.eq(15, world.getLightFor(EnumSkyBlock.BLOCK, top), "block light at y 319");
        check.eq(14, world.getLightFor(EnumSkyBlock.BLOCK, top.down()), "block light below a light at y 319");
        check.eq(15, world.getLightFromNeighbors(top.up()), "light above the world is full");

        BlockPos lamp = new BlockPos(X + 64 + 4, -40, Z + 4);
        BlockPos next = lamp.east();
        world.setBlockState(next, Blocks.AIR.getDefaultState(), 3);
        world.setBlockState(lamp, Blocks.GLOWSTONE.getDefaultState(), 3);
        check.eq(15, world.getLightFor(EnumSkyBlock.BLOCK, lamp), "block light at a light at y -40");
        check.eq(14, world.getLightFor(EnumSkyBlock.BLOCK, next), "block light next to a light at y -40");
    }

    /** Both section lists in the save, with signed Y, and back. */
    private static void nbt(Check check, WorldServer world) throws Exception {
        Chunk chunk = world.getChunk(new BlockPos(X, 0, Z));
        AnvilChunkLoader loader = (AnvilChunkLoader) world.getChunkProvider().chunkLoader;
        Method write = ObfuscationReflectionHelper.findMethod(AnvilChunkLoader.class, "func_75820_a", void.class,
                Chunk.class, World.class, NBTTagCompound.class);
        Method read = ObfuscationReflectionHelper.findMethod(AnvilChunkLoader.class, "func_75823_a", Chunk.class,
                World.class, NBTTagCompound.class);
        NBTTagCompound level = new NBTTagCompound();
        write.invoke(loader, chunk, world, level);

        NBTTagList vanilla = level.getTagList("Sections", 10);
        NBTTagList ours = level.getTagList(HeightHooks.SECTIONS, 10);
        check.eq(16, vanilla.tagCount(), "vanilla's Sections list holds the 16 sections of y 0-255");
        check.eq(8, ours.tagCount(), "our list holds the 8 sections below 0 and above 255");
        boolean vanillaYs = true;
        for (int i = 0; i < vanilla.tagCount(); i++) {
            int y = vanilla.getCompoundTagAt(i).getByte("Y");
            vanillaYs &= y >= 0 && y < 16;
        }
        check.ok(vanillaYs, "vanilla's list has only Y 0-15");
        List<Integer> ys = new ArrayList<Integer>();
        for (int i = 0; i < ours.tagCount(); i++) {
            ys.add((int) ours.getCompoundTagAt(i).getByte("Y"));
        }
        check.eq("[16, 17, 18, 19, -4, -3, -2, -1]", ys.toString(), "our list's signed Y");

        Chunk back = (Chunk) read.invoke(loader, world, level);
        check.eq(24, back.getBlockStorageArray().length, "the read chunk's section count");
        int wrong = 0;
        for (int y = -64; y < 320; y++) {
            for (int dx = 0; dx < 16; dx += 3) {
                BlockPos p = new BlockPos((X & ~15) + dx, y, Z);
                if (back.getBlockState(p) != chunk.getBlockState(p)
                        || back.getLightFor(EnumSkyBlock.SKY, p) != chunk.getLightFor(EnumSkyBlock.SKY, p)
                        || back.getLightFor(EnumSkyBlock.BLOCK, p) != chunk.getLightFor(EnumSkyBlock.BLOCK, p)) {
                    wrong++;
                }
            }
        }
        check.eq(0, wrong, "blocks or light that changed in the NBT round trip");
        for (int slot = 0; slot < 24; slot++) {
            ExtendedBlockStorage s = back.getBlockStorageArray()[slot];
            check.ok(s != null && s.getYLocation() == HeightLayout.slotBase(slot), "read slot " + slot + " at its height");
        }
    }

    /** The chunk packet: all 24 sections in the mask, and its size. */
    private static void packet(Check check, WorldServer world) throws Exception {
        Chunk chunk = world.getChunk(new BlockPos(X, 0, Z));
        SPacketChunkData packet = new SPacketChunkData(chunk, 65535);
        check.ok(packet.isFullChunk(), "65535 still means a full chunk");
        check.eq(0xFFFFFF, mask(packet), "the full chunk's section mask covers all 24 sections");
        int size = 256;
        for (ExtendedBlockStorage s : chunk.getBlockStorageArray()) {
            size += s.getData().getSerializedSize() + s.getBlockLight().getData().length + s.getSkyLight().getData().length;
        }
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        packet.writePacketData(buf);
        SPacketChunkData copy = new SPacketChunkData();
        copy.readPacketData(buf);
        check.eq(size, data(copy).length, "the packet's data size (24 sections and the biomes)");
        check.eq(0xFFFFFF, mask(copy), "the mask survives the wire");
        SPacketChunkData partial = new SPacketChunkData(chunk, 1 << 21);
        check.ok(!partial.isFullChunk(), "a one-section update is not a full chunk");
        check.eq(1 << 21, mask(partial), "a one-section update of the section at y -48");
    }

    /** 20 changes below 0 in one tick reach a watching player as 20 single-block packets. */
    private static void packets(Check check, WorldServer world) throws Exception {
        final List<Packet<?>> sent = new ArrayList<Packet<?>>();
        NetworkManager network = new NetworkManager(EnumPacketDirection.CLIENTBOUND) {
            @Override
            public void sendPacket(Packet<?> packet) {
                sent.add(packet);
            }
        };
        int x = X + 200;
        EntityPlayerMP player = check.player("height_watcher", new BlockPos(x, 100, Z));
        new NetHandlerPlayServer(world.getMinecraftServer(), network, player);
        PlayerChunkMap map = world.getPlayerChunkMap();
        map.addPlayer(player);
        try {
            // Until every chunk around the player has been sent (a few quiet ticks in a row).
            int quiet = 0;
            int size = -1;
            for (int i = 0; i < 400 && quiet < 5; i++) {
                map.tick();
                quiet = sent.size() == size ? quiet + 1 : 0;
                size = sent.size();
            }
            int chunkPackets = 0;
            boolean fullMask = false;
            for (Packet<?> p : sent) {
                if (p instanceof SPacketChunkData) {
                    chunkPackets++;
                    fullMask |= (mask((SPacketChunkData) p) & 0xF00000) == 0xF00000;
                }
            }
            check.ok(chunkPackets > 0, "a watching player is sent chunks (" + chunkPackets + ")");
            check.ok(fullMask, "the chunks sent include the sections below 0");
            sent.clear();
            long before = HeightHooks.sentOutside;
            for (int i = 0; i < 20; i++) {
                world.setBlockState(new BlockPos(x + (i % 4), -10 - i, Z + (i / 4)), Blocks.GOLD_BLOCK.getDefaultState(), 3);
            }
            map.tick();
            int single = 0;
            int other = 0;
            for (Packet<?> p : sent) {
                if (p instanceof SPacketBlockChange && ((SPacketBlockChange) p).blockState.getBlock() == Blocks.GOLD_BLOCK) {
                    single++;
                } else if (p instanceof SPacketMultiBlockChange || p instanceof SPacketChunkData) {
                    other++;
                }
            }
            check.eq(20, single, "single-block packets for 20 changes below y 0");
            check.eq(0, other, "multi-block or chunk packets for them");
            check.eq(20L, HeightHooks.sentOutside - before, "changes sent through the side list");
            sent.clear();
            for (int i = 0; i < 20; i++) {
                world.setBlockState(new BlockPos(x + (i % 4), 280 + i, Z + (i / 4)), Blocks.GOLD_BLOCK.getDefaultState(), 3);
            }
            map.tick();
            int high = 0;
            for (Packet<?> p : sent) {
                high += p instanceof SPacketBlockChange && ((SPacketBlockChange) p).blockState.getBlock() == Blocks.GOLD_BLOCK ? 1 : 0;
            }
            check.eq(20, high, "single-block packets for 20 changes above y 255");
        } finally {
            map.removePlayer(player);
        }
    }

    /** Entities below 0 and above 255 sit in their own section's list, and area queries find them. */
    private static void entities(Check check, WorldServer world) {
        Chunk chunk = world.getChunk(new BlockPos(X, 0, Z));
        check.eq(24, chunk.getEntityLists().length, "an extended chunk's entity lists");
        int[][] cases = {{-30, -2}, {-64, -4}, {300, 18}, {64, 4}};
        for (int[] c : cases) {
            EntityItem item = item(world, c[0] + 0.5D);
            world.spawnEntity(item);
            check.eq(c[1], item.chunkCoordY, "chunkCoordY of an entity at y " + c[0]);
            int index = c[1] - Heights.minSection(world);
            check.ok(chunk.getEntityLists()[index].contains(item), "the entity at y " + c[0] + " is in list " + index);
            List<EntityItem> near = world.getEntitiesWithinAABB(EntityItem.class,
                    new net.minecraft.util.math.AxisAlignedBB(X, c[0] - 1, Z, X + 1, c[0] + 2, Z + 1));
            check.ok(near.contains(item), "an area query at y " + c[0] + " finds the entity");
            List<EntityItem> away = world.getEntitiesWithinAABB(EntityItem.class,
                    new net.minecraft.util.math.AxisAlignedBB(X, c[0] + 40, Z, X + 1, c[0] + 42, Z + 1));
            check.ok(!away.contains(item), "an area query 40 blocks above y " + c[0] + " does not");
            world.removeEntityDangerously(item);
            check.ok(!chunk.getEntityLists()[index].contains(item), "the entity at y " + c[0] + " is removed from its list");
        }
    }

    /** The packet's section mask (its getter is client-only). */
    private static int mask(SPacketChunkData packet) {
        return ObfuscationReflectionHelper.<Integer, SPacketChunkData>getPrivateValue(SPacketChunkData.class, packet, "field_186948_c");
    }

    private static byte[] data(SPacketChunkData packet) {
        return ObfuscationReflectionHelper.<byte[], SPacketChunkData>getPrivateValue(SPacketChunkData.class, packet, "field_186949_d");
    }

    /** The void starts at y -128 here (vanilla: -64). */
    private static void voidFall(Check check, WorldServer world) {
        EntityItem alive = item(world, -127.5D);
        EntityItem gone = item(world, -128.5D);
        alive.onEntityUpdate();
        gone.onEntityUpdate();
        check.ok(!alive.isDead, "an item at y -127.5 is not in the void");
        check.ok(gone.isDead, "an item at y -128.5 is in the void");
    }

    private static EntityItem item(World world, double y) {
        EntityItem item = new EntityItem(world, X + 0.5D, y, Z + 0.5D, new ItemStack(Items.STICK));
        item.setNoGravity(true);
        return item;
    }

    /** The Nether: vanilla's 0-255 everywhere. */
    private static void vanillaWorld(Check check) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        WorldServer nether = server.getWorld(-1);
        check.ok(!Heights.isExtended(nether), "the Nether is not extended");
        check.eq(0, Heights.minY(nether), "Nether min y");
        check.eq(256, Heights.maxY(nether), "Nether max y");
        check.eq(256, nether.getHeight(), "Nether World.getHeight()");
        check.eq(128, nether.getActualHeight(), "Nether World.getActualHeight()");
        BlockPos at = new BlockPos(X, 64, Z);
        Chunk chunk = nether.getChunk(at);
        check.eq(16, chunk.getBlockStorageArray().length, "a Nether chunk's section count");
        check.ok(nether.isOutsideBuildHeight(at.down(65)), "Nether y -1 is outside");
        check.ok(nether.isOutsideBuildHeight(new BlockPos(X, 256, Z)), "Nether y 256 is outside");
        check.ok(!nether.setBlockState(new BlockPos(X, -5, Z), Blocks.STONE.getDefaultState(), 2), "no block at Nether y -5");
        check.eq(Blocks.AIR.getDefaultState(), chunk.getBlockState(X & 15, -5, Z & 15), "Nether y -5 reads air");
        check.eq(Blocks.AIR.getDefaultState(), chunk.getBlockState(X & 15, 300, Z & 15), "Nether y 300 reads air");
        check.eq(15, nether.getLightFor(EnumSkyBlock.SKY, new BlockPos(X, 300, Z)), "light above the Nether's top");
        EntityItem alive = item(nether, -63.5D);
        EntityItem gone = item(nether, -64.5D);
        alive.onEntityUpdate();
        gone.onEntityUpdate();
        check.ok(!alive.isDead && gone.isDead, "the Nether's void starts at vanilla's y -64");
        SPacketChunkData packet = new SPacketChunkData(chunk, 65535);
        check.ok(packet.isFullChunk() && (mask(packet) & ~0xFFFF) == 0, "a Nether chunk packet has 16 sections at most");
        CavesAndCliffsBackport.LOGGER.info("SELFTEST height: {} patch sites; Nether checked as vanilla.", PatchReport.size());
    }
}
