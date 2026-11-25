package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.height.HeightContent;
import com.exiledradio.cavesandcliffsbackport.height.Heights;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernGenerator;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernTerrain;
import com.exiledradio.cavesandcliffsbackport.upgrade.OldChunks;
import com.exiledradio.cavesandcliffsbackport.upgrade.StructureGuard;
import com.exiledradio.cavesandcliffsbackport.upgrade.WorldUpgrade;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.ICommandSender;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Upgrading a 0-255 world to y -64..319 ({@code upgrade.WorldUpgrade}): {@code sh tools/height/upgrade.sh} runs three
 * servers on one world.
 *
 * <ol>
 *   <li>{@code upgrade1}, a fresh Default world: generates a 12 x 12 chunk area around a village, adds known blocks
 *       (a hole in the bedrock, diamond blocks, a chest, glass at the top of the world), writes every block from
 *       y 0 to 255 of the populated chunks and the saved structure starts to {@code upgrade-old.bin}, and asks for the
 *       upgrade with the command (asked, confirmed).</li>
 *   <li>{@code upgrade2}, the same world: it is now y -64..319. Every old chunk loaded is byte-identical from y 0 up
 *       (but the bedrock at y 0..4, now deepslate), has deepslate, caves and deepslate ores below 0 and bedrock at
 *       -64; the hole's column is air below 0. New chunks east of the area are full height and meet the old terrain
 *       closer than the unblended 1.18 terrain would. No structure started since the upgrade lies in or reaches into an
 *       old chunk, and the saved village goes on. The command refuses a second upgrade. Writes {@code
 *       upgrade-after.bin}.</li>
 *   <li>{@code upgrade3}, the same world again: every chunk is as {@code upgrade2} left it, and no old chunk is
 *       upgraded a second time.</li>
 * </ol>
 */
public final class UpgradeSelfTest implements SelfTest {

    private static final int SIDE = 12;
    private final String phase;

    UpgradeSelfTest(String phase) {
        this.phase = phase;
    }

    @Override
    public String name() {
        return phase;
    }

    @Override
    public void run(Check check) throws Exception {
        switch (phase) {
            case "upgrade1":
                before(check);
                break;
            case "upgrade2":
                after(check);
                break;
            default:
                reload(check);
                break;
        }
    }

    /** A command sender that keeps what it is told. */
    static final class Sender implements ICommandSender {
        final MinecraftServer server;
        final List<String> said = new ArrayList<>();

        Sender(MinecraftServer server) {
            this.server = server;
        }

        @Override
        public String getName() {
            return "UpgradeSelfTest";
        }

        @Override
        public void sendMessage(ITextComponent component) {
            said.add(component.getUnformattedText());
        }

        @Override
        public boolean canUseCommand(int permLevel, String commandName) {
            return true;
        }

        @Override
        public World getEntityWorld() {
            return server.getWorld(0);
        }

        @Override
        public MinecraftServer getServer() {
            return server;
        }

        String all() {
            return String.join(" | ", said);
        }
    }

    private static File file(WorldServer world, String name) {
        return world.getMinecraftServer().getFile(name);
    }

    // ---------------------------------------------------------------------------------------------
    // Phase 1

    private void before(Check check) throws Exception {
        WorldServer world = check.world;
        if (!check.ok(!Heights.isExtended(world) && world.getWorldInfo().getTerrainType() == WorldType.DEFAULT,
                "upgrade1 runs in a fresh Default world of y 0-255")) {
            return;
        }
        BlockPos village = world.findNearestStructure("Village", new BlockPos(3200, 64, 3200), false);
        check.ok(village != null, "a village to center the old area on");
        int cx0 = (village == null ? 3200 : village.getX()) >> 4;
        int cz0 = (village == null ? 3200 : village.getZ()) >> 4;
        int x0 = cx0 - SIDE / 2;
        int z0 = cz0 - SIDE / 2;
        // One more chunk around, so the area's chunks are all populated (1.12 populates a chunk once its +x, +z
        // neighbors exist); the outer ring stays unpopulated.
        for (int x = x0 - 1; x <= x0 + SIDE; x++) {
            for (int z = z0 - 1; z <= z0 + SIDE; z++) {
                world.getChunkProvider().provideChunk(x, z);
            }
        }
        // Known blocks, in a chunk in the middle.
        int bx = (x0 + 3) << 4;
        int bz = (z0 + 3) << 4;
        for (int y = 0; y <= 4; y++) {
            world.setBlockState(new BlockPos(bx + 5, y, bz + 5), Blocks.AIR.getDefaultState(), 2);
        }
        for (int y = 6; y <= 10; y++) {
            world.setBlockState(new BlockPos(bx + 8, y, bz + 8), Blocks.DIAMOND_BLOCK.getDefaultState(), 2);
        }
        BlockPos chest = new BlockPos(bx + 9, 12, bz + 9);
        world.setBlockState(chest, Blocks.CHEST.getDefaultState(), 2);
        TileEntity te = world.getTileEntity(chest);
        if (te instanceof TileEntityChest) {
            ((TileEntityChest) te).setInventorySlotContents(0, new ItemStack(Items.APPLE, 7));
        }
        for (int y = 250; y <= 255; y++) {
            world.setBlockState(new BlockPos(bx + 1, y, bz + 1), Blocks.GLASS.getDefaultState(), 2);
        }
        int populated = 0;
        try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(
                new FileOutputStream(file(world, "upgrade-old.bin"))))) {
            out.writeInt(x0);
            out.writeInt(z0);
            out.writeInt(bx);
            out.writeInt(bz);
            for (int x = x0; x < x0 + SIDE; x++) {
                for (int z = z0; z < z0 + SIDE; z++) {
                    Chunk c = world.getChunk(x, z);
                    out.writeBoolean(c.isTerrainPopulated());
                    if (c.isTerrainPopulated()) {
                        populated++;
                    }
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            for (int y = 0; y < 256; y++) {
                                out.writeChar(OldChunks.stateId(c.getBlockState(lx, y, lz)));
                            }
                        }
                    }
                }
            }
            world.getPerWorldStorage().saveAllData();
            Map<String, Set<String>> starts = starts(world);
            out.writeInt(starts.size());
            for (Map.Entry<String, Set<String>> e : starts.entrySet()) {
                out.writeUTF(e.getKey());
                out.writeInt(e.getValue().size());
                for (String s : e.getValue()) {
                    out.writeUTF(s);
                }
            }
        }
        check.eq(SIDE * SIDE, populated, "every chunk of the old area is populated");
        Map<String, Set<String>> starts = starts(world);
        check.ok(starts.containsKey("Village") && starts.get("Village").contains("[" + cx0 + "," + cz0 + "]"),
                "the village's start is saved (" + starts.get("Village") + ")");

        Sender sender = new Sender(world.getMinecraftServer());
        world.getMinecraftServer().getCommandManager().executeCommand(sender, "cavesandcliffs upgradeworld confirm");
        check.ok(sender.all().contains("first"), "confirm without asking first is refused: " + sender.all());
        sender.said.clear();
        world.getMinecraftServer().getCommandManager().executeCommand(sender, "cavesandcliffs upgradeworld");
        check.ok(sender.all().contains("confirm within 60 seconds"), "the command explains and asks: " + sender.all());
        sender.said.clear();
        world.getMinecraftServer().getCommandManager().executeCommand(sender, "cavesandcliffs upgradeworld confirm");
        check.ok(sender.all().contains("upgrades on its next load"), "the confirm marks the world: " + sender.all());
        File dir = world.getSaveHandler().getWorldDirectory();
        check.ok(new File(dir, "level.dat.before-extended-height").isFile(), "level.dat is backed up");
        check.ok(new File(new File(dir, "data"), WorldUpgrade.FILE).isFile(), "the upgrade request is saved");
        check.ok(!Heights.isExtended(world), "the running world stays y 0-255 until it is loaded again");
        sender.said.clear();
        world.getMinecraftServer().getCommandManager().executeCommand(sender, "cavesandcliffs upgradeworld");
        check.ok(sender.all().contains("next load already"), "asking again says it is pending: " + sender.all());
    }

    /** Every saved structure start: per structure data name, its start keys "[x,z]" and, for each, if it has pieces. */
    static Map<String, Set<String>> starts(WorldServer world) throws Exception {
        Map<String, Set<String>> out = new HashMap<>();
        File data = new File(world.getSaveHandler().getWorldDirectory(), "data");
        File[] files = data.listFiles();
        if (files == null) {
            return out;
        }
        for (File f : files) {
            if (!f.getName().endsWith(".dat")) {
                continue;
            }
            NBTTagCompound tag;
            try (FileInputStream in = new FileInputStream(f)) {
                tag = CompressedStreamTools.readCompressed(in);
            } catch (Exception e) {
                continue;
            }
            NBTTagCompound features = tag.getCompoundTag("data").getCompoundTag("Features");
            if (features.getKeySet().isEmpty()) {
                continue;
            }
            Set<String> keys = new HashSet<>(features.getKeySet());
            out.put(f.getName().substring(0, f.getName().length() - 4), keys);
        }
        return out;
    }

    /** One structure data file's starts as tags. */
    static NBTTagCompound features(WorldServer world, String name) throws Exception {
        File f = new File(new File(world.getSaveHandler().getWorldDirectory(), "data"), name + ".dat");
        try (FileInputStream in = new FileInputStream(f)) {
            return CompressedStreamTools.readCompressed(in).getCompoundTag("data").getCompoundTag("Features");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Phase 2

    private static final class Old {
        int x0;
        int z0;
        int bx;
        int bz;
        final boolean[] populated = new boolean[SIDE * SIDE];
        final char[][] ids = new char[SIDE * SIDE][];
        final Map<String, Set<String>> starts = new HashMap<>();
    }

    private static Old readOld(WorldServer world) throws Exception {
        Old o = new Old();
        try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(
                new FileInputStream(file(world, "upgrade-old.bin"))))) {
            o.x0 = in.readInt();
            o.z0 = in.readInt();
            o.bx = in.readInt();
            o.bz = in.readInt();
            for (int i = 0; i < SIDE * SIDE; i++) {
                o.populated[i] = in.readBoolean();
                char[] ids = new char[65536];
                for (int j = 0; j < ids.length; j++) {
                    ids[j] = in.readChar();
                }
                o.ids[i] = ids;
            }
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                String name = in.readUTF();
                int m = in.readInt();
                Set<String> keys = new HashSet<>();
                for (int j = 0; j < m; j++) {
                    keys.add(in.readUTF());
                }
                o.starts.put(name, keys);
            }
        }
        return o;
    }

    private static boolean deepslate(IBlockState s) {
        return s.getBlock().getRegistryName() != null && s.getBlock().getRegistryName().getPath().equals("deepslate");
    }

    private void after(Check check) throws Exception {
        WorldServer world = check.world;
        WorldUpgrade up = WorldUpgrade.of(world);
        if (!check.ok(up != null && Heights.isExtended(world) && Heights.minY(world) == -64 && Heights.maxY(world) == 320,
                "the world loaded as an upgraded one, y -64..319")) {
            return;
        }
        check.ok(world.getWorldInfo().getTerrainType() == HeightContent.worldType(), "its world type is Caves & Cliffs now");
        check.ok(ModernTerrain.owns(world), "new chunks come from the 1.18 generator");
        ModernGenerator gen = (ModernGenerator) world.getChunkProvider().chunkGenerator;
        check.ok(gen.upgraded(), "the generator knows the world is upgraded");
        Old o = readOld(world);
        check.ok(up.oldCount() >= SIDE * SIDE, "the old chunks were counted (" + up.oldCount() + ")");
        int before = gen.upgradedChunks;
        OldChunks disk = new OldChunks(world);
        long t = System.nanoTime();
        int same = 0;
        int differ = 0;
        int bedrockReplaced = 0;
        int bedrockLeft = 0;
        long deep = 0;
        long air = 0;
        long ores = 0;
        long stoneBelow = 0;
        int floor = 0;
        int floorMissing = 0;
        String firstDiff = null;
        Map<String, Integer> diffs = new java.util.TreeMap<>();
        for (int i = 0; i < SIDE * SIDE; i++) {
            int cx = o.x0 + i / SIDE;
            int cz = o.z0 + i % SIDE;
            check.ok(up.isOld(cx, cz), "chunk " + cx + "," + cz + " is old");
            // The chunk as the 0-255 world saved it (blocks moved in the ticks after upgrade1 looked), read from its
            // region file before it loads.
            OldChunks.OldChunk saved = world.getChunkProvider().getLoadedChunk(cx, cz) == null ? disk.get(cx, cz) : null;
            check.ok(saved != null, "chunk " + cx + "," + cz + " is read from its save before it loads");
            char[] ids = saved != null ? saved.ids().clone() : o.ids[i];
            Chunk c = world.getChunk(cx, cz);
            boolean hole = (cx << 4) == o.bx && (cz << 4) == o.bz;
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int at = (lx * 16 + lz) * 256;
                    for (int y = 0; y < 256; y++) {
                        int was = OldChunks.stateId(OldChunks.state(ids[at + y]));
                        IBlockState now = c.getBlockState(lx, y, lz);
                        int id = OldChunks.stateId(now);
                        if (id == was) {
                            same++;
                        } else if (y <= 4 && was == OldChunks.stateId(Blocks.BEDROCK.getDefaultState()) && deepslate(now)) {
                            bedrockReplaced++;
                        } else {
                            differ++;
                            String k = (i / SIDE) + "," + (i % SIDE) + " " + OldChunks.state(was).getBlock().getRegistryName().getPath()
                                    + "->" + now.getBlock().getRegistryName().getPath();
                            Integer v = diffs.get(k);
                            diffs.put(k, v == null ? 1 : v + 1);
                            if (firstDiff == null) {
                                firstDiff = (cx * 16 + lx) + "," + y + "," + (cz * 16 + lz) + ": " + OldChunks.state(was) + " -> " + now;
                            }
                        }
                        if (y <= 4 && now.getBlock() == Blocks.BEDROCK) {
                            bedrockLeft++;
                        }
                    }
                    boolean holeColumn = hole && lx == 5 && lz == 5;
                    for (int y = -64; y < 0; y++) {
                        IBlockState s = c.getBlockState(lx, y, lz);
                        if (holeColumn) {
                            if (s.getMaterial() != Material.AIR) {
                                check.fail("the hole's column is air below 0, not " + s + " at y " + y);
                                holeColumn = false;
                            }
                            continue;
                        }
                        if (deepslate(s)) {
                            deep++;
                        } else if (s.getMaterial() == Material.AIR) {
                            air++;
                        } else if (s.getBlock() == Blocks.STONE) {
                            stoneBelow++;
                        }
                        String path = s.getBlock().getRegistryName().getPath();
                        if (path.startsWith("deepslate_") && path.endsWith("_ore")) {
                            ores++;
                        }
                    }
                    if (!(hole && lx == 5 && lz == 5)) {
                        if (c.getBlockState(lx, -64, lz).getBlock() == Blocks.BEDROCK) {
                            floor++;
                        } else {
                            floorMissing++;
                        }
                    }
                }
            }
        }
        long ms = (System.nanoTime() - t) / 1000000L;
        com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport.LOGGER.info("UPGRADE OLD {} chunks in {} ms; "
                + "same {} bedrock->deepslate {} differ {}; below 0: deepslate {} air {} ores {} floor {}; diffs {}",
                SIDE * SIDE, ms, same, bedrockReplaced, differ, deep, air, ores, floor, diffs);
        check.ok(gen.upgradedChunks - before >= SIDE * SIDE, "every old chunk got its part below 0 as it loaded ("
                + (gen.upgradedChunks - before) + " in " + ms + " ms)");
        check.eq(0, differ, "from y 0 up the old chunks are unchanged but the bedrock (" + same + " blocks the same, "
                + bedrockReplaced + " bedrock now deepslate; first difference: " + firstDiff + ")");
        check.ok(bedrockReplaced > 0, "the old bedrock at y 0..4 became deepslate (" + bedrockReplaced + ")");
        check.eq(0, bedrockLeft, "no bedrock left at y 0..4");
        long below = deep + air + stoneBelow;
        check.ok(deep > below / 2, "deepslate fills most of y -64..-1 (" + deep + " of " + below + ")");
        check.ok(air > 1000, "caves below 0 (" + air + " air)");
        check.ok(ores > 50, "deepslate ores below 0 (" + ores + ")");
        check.eq(0, floorMissing, "bedrock at y -64 in every column but the hole's (" + floor + ")");
        Chunk mid = world.getChunk(o.bx >> 4, o.bz >> 4);
        check.ok(mid.getBlockState(8, 8, 8).getBlock() == Blocks.DIAMOND_BLOCK, "the diamond blocks are still there");
        TileEntity te = world.getTileEntity(new BlockPos(o.bx + 9, 12, o.bz + 9));
        check.ok(te instanceof TileEntityChest && ((TileEntityChest) te).getStackInSlot(0).getCount() == 7,
                "the chest still holds its 7 apples");
        check.ok(mid.getBlockState(1, 255, 1).getBlock() == Blocks.GLASS, "the glass at y 255 is still there");

        // New chunks east of the old area (past its unpopulated ring): full height, blended at the border.
        int nx0 = o.x0 + SIDE + 1;
        int deepNew = 0;
        int floorNew = 0;
        for (int x = nx0; x < nx0 + 6; x++) {
            for (int z = o.z0; z < o.z0 + SIDE; z++) {
                Chunk c = world.getChunk(x, z);
                check.ok(!up.isOld(x, z), "chunk " + x + "," + z + " is new");
                deepNew += deepBelow(c);
                if (c.getBlockState(3, -64, 3).getBlock() == Blocks.BEDROCK) {
                    floorNew++;
                }
            }
        }
        check.eq(6 * SIDE, floorNew, "the new chunks have bedrock at -64");
        // The same count 30 chunks further east, past any blending.
        int deepFar = 0;
        for (int x = nx0 + 30; x < nx0 + 36; x++) {
            for (int z = o.z0; z < o.z0 + SIDE; z++) {
                deepFar += deepBelow(world.getChunk(x, z));
            }
        }
        com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport.LOGGER.info("UPGRADE DEEP near {} far {} of {}",
                deepNew, deepFar, 6 * SIDE * 256 * 64);
        check.ok(deepNew > 6 * SIDE * 256 * 64 / 3, "the new chunks are mostly deepslate below 0 (" + deepNew
                + "; past the blending: " + deepFar + ")");
        double blended = 0;
        double unblended = 0;
        int n = 0;
        int oldX = (nx0 << 4) - 1;
        for (int z = o.z0 << 4; z < (o.z0 + SIDE) << 4; z++) {
            int hOld = ground(world, oldX, z);
            int hNew = ground(world, oldX + 1, z);
            int hNoise = gen.floor(oldX + 1, z) - 1;
            blended += Math.abs(hNew - hOld);
            unblended += Math.abs(hNoise - hOld);
            n++;
        }
        blended /= n;
        unblended /= n;
        com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport.LOGGER.info(
                "UPGRADE BORDER blended {} unblended {}", blended, unblended);
        check.ok(blended <= Math.max(3.0D, unblended * 0.6D), String.format(
                "new terrain meets the old at the border: %.1f blocks apart on average, the unblended 1.18 terrain %.1f",
                blended, unblended));

        // Structures: nothing new in or reaching into an old chunk; the saved village goes on.
        world.getPerWorldStorage().saveAllData();
        Map<String, Set<String>> now = starts(world);
        int newStarts = 0;
        int built = 0;
        for (Map.Entry<String, Set<String>> e : now.entrySet()) {
            Set<String> had = o.starts.containsKey(e.getKey()) ? o.starts.get(e.getKey()) : new HashSet<String>();
            NBTTagCompound features = features(world, e.getKey());
            for (String key : e.getValue()) {
                if (had.contains(key)) {
                    continue;
                }
                newStarts++;
                NBTTagCompound s = features.getCompoundTag(key);
                if (s.getTagList("Children", 10).tagCount() == 0) {
                    continue;
                }
                built++;
                int[] bb = s.getIntArray("BB");
                boolean touches = up.isOld(s.getInteger("ChunkX"), s.getInteger("ChunkZ"));
                if (bb.length == 6) {
                    for (int cx = bb[0] >> 4; cx <= bb[3] >> 4 && !touches; cx++) {
                        for (int cz = bb[2] >> 4; cz <= bb[5] >> 4 && !touches; cz++) {
                            touches = up.isOld(cx, cz);
                        }
                    }
                }
                check.ok(!touches, e.getKey() + " " + key + " started since the upgrade stays out of the old chunks");
            }
        }
        com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport.LOGGER.info("UPGRADE STRUCTURES {} new starts, {} "
                + "built, {} emptied; old chunks {}, upgraded {}", newStarts, built, StructureGuard.emptied, up.oldCount(),
                gen.upgradedChunks);
        NBTTagCompound village = features(world, "Village").getCompoundTag("[" + (o.x0 + SIDE / 2) + "," + (o.z0 + SIDE / 2) + "]");
        check.ok(village.getTagList("Children", 10).tagCount() > 0, "the village saved before the upgrade keeps its pieces");

        Sender sender = new Sender(world.getMinecraftServer());
        world.getMinecraftServer().getCommandManager().executeCommand(sender, "cavesandcliffs upgradeworld");
        check.ok(sender.all().contains("upgraded already"), "a second upgrade is refused: " + sender.all());
        sender.said.clear();
        world.getMinecraftServer().getCommandManager().executeCommand(sender, "cavesandcliffs upgradeworld status");
        check.ok(sender.all().contains("old chunks"), "status: " + sender.all());

        // Everything as it is now, for the reload.
        writeAll(world, o, nx0, file(world, "upgrade-after.bin"));
    }

    private static int deepBelow(Chunk c) {
        int n = 0;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = -64; y < 0; y++) {
                    IBlockState s = c.getBlockState(lx, y, lz);
                    if (deepslate(s)) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    /** The highest rock, dirt, sand or grass block at x, z (no trees, plants or water). */
    private static int ground(WorldServer world, int x, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, 255, z);
        for (int y = 319; y > -64; y--) {
            p.setY(y);
            Material m = world.getBlockState(p).getMaterial();
            if (m == Material.ROCK || m == Material.GROUND || m == Material.GRASS || m == Material.SAND
                    || m == Material.CLAY || m == Material.PACKED_ICE || m == Material.CRAFTED_SNOW) {
                return y;
            }
        }
        return -64;
    }

    private static void writeAll(WorldServer world, Old o, int nx0, File f) throws Exception {
        try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(new FileOutputStream(f)))) {
            for (int x = o.x0; x < nx0 + 6; x++) {
                for (int z = o.z0; z < o.z0 + SIDE; z++) {
                    Chunk c = world.getChunk(x, z);
                    long h = 1125899906842597L;
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            for (int y = -64; y < 320; y++) {
                                h = 31 * h + OldChunks.stateId(c.getBlockState(lx, y, lz));
                            }
                        }
                    }
                    out.writeInt(x);
                    out.writeInt(z);
                    out.writeLong(h);
                }
            }
            out.writeInt(Integer.MIN_VALUE);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Phase 3

    /** A chunk's blocks from y -64 to 319 as its region file has them, hashed as the loaded chunk is. */
    private static long savedHash(WorldServer world, int cx, int cz) throws Exception {
        NBTTagCompound level;
        try (DataInputStream in = net.minecraft.world.chunk.storage.RegionFileCache.getChunkInputStream(
                world.getChunkSaveLocation(), cx, cz)) {
            level = CompressedStreamTools.read(in).getCompoundTag("Level");
        }
        char[] ids = new char[256 * 384];
        for (String list : new String[] {"Sections", com.exiledradio.cavesandcliffsbackport.height.HeightHooks.SECTIONS}) {
            net.minecraft.nbt.NBTTagList sections = level.getTagList(list, 10);
            for (int i = 0; i < sections.tagCount(); i++) {
                NBTTagCompound s = sections.getCompoundTagAt(i);
                int sy = s.getByte("Y");
                byte[] blocks = s.getByteArray("Blocks");
                net.minecraft.world.chunk.NibbleArray data = new net.minecraft.world.chunk.NibbleArray(s.getByteArray("Data"));
                net.minecraft.world.chunk.NibbleArray add = s.hasKey("Add", 7)
                        ? new net.minecraft.world.chunk.NibbleArray(s.getByteArray("Add")) : null;
                // Roughly Enough IDs / Just Enough IDs: palette indices, and the palette's state ids.
                int[] palette = s.hasKey("Palette", 11) ? s.getIntArray("Palette") : null;
                for (int j = 0; j < 4096; j++) {
                    int y = (sy << 4) + (j >> 8) + 64;
                    if (palette != null) {
                        IBlockState state = Block.BLOCK_STATE_IDS.getByValue(palette[(blocks[j] & 255) << 4 | data.getFromIndex(j)]);
                        ids[((j & 15) * 16 + (j >> 4 & 15)) * 384 + y] = (char) (state == null ? 0 : OldChunks.stateId(state));
                        continue;
                    }
                    int id = (add == null ? 0 : add.getFromIndex(j)) << 8 | blocks[j] & 255;
                    ids[((j & 15) * 16 + (j >> 4 & 15)) * 384 + y] = (char) (id << 4 | data.getFromIndex(j));
                }
            }
        }
        long h = 1125899906842597L;
        for (int i = 0; i < ids.length; i++) {
            h = 31 * h + OldChunks.stateId(OldChunks.state(ids[i]));
        }
        return h;
    }

    private void reload(Check check) throws Exception {
        WorldServer world = check.world;
        WorldUpgrade up = WorldUpgrade.of(world);
        if (!check.ok(up != null && Heights.isExtended(world), "the upgraded world loads again as y -64..319")) {
            return;
        }
        ModernGenerator gen = (ModernGenerator) world.getChunkProvider().chunkGenerator;
        int before = gen.upgradedChunks;
        int chunks = 0;
        int changed = 0;
        try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(
                new FileInputStream(file(world, "upgrade-after.bin"))))) {
            while (true) {
                int x = in.readInt();
                if (x == Integer.MIN_VALUE) {
                    break;
                }
                int z = in.readInt();
                in.readLong();
                // The chunk as saved (both section lists), then as loaded: a save and a load change nothing, and an
                // upgraded chunk is not upgraded again. (Chunks go on ticking and populating after upgrade2 looked, so
                // the save, not upgrade2's view, is what the load must give back.)
                if (world.getChunkProvider().getLoadedChunk(x, z) != null) {
                    continue;
                }
                long h = savedHash(world, x, z);
                Chunk c = world.getChunk(x, z);
                long g = 1125899906842597L;
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = -64; y < 320; y++) {
                            g = 31 * g + OldChunks.stateId(c.getBlockState(lx, y, lz));
                        }
                    }
                }
                chunks++;
                if (g != h) {
                    changed++;
                }
            }
        }
        check.ok(chunks > 200, "chunks checked after the restart (" + chunks + ")");
        check.eq(0, changed, "after a restart every chunk loads as it was saved (" + chunks + " chunks)");
        check.eq(0, gen.upgradedChunks - before, "no old chunk is upgraded a second time");
        check.ok(up.doneCount() >= SIDE * SIDE, "the upgraded chunks are remembered (" + up.doneCount() + ")");
    }
}
