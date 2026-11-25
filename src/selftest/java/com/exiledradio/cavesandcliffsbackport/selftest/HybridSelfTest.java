package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.height.HeightContent;
import com.exiledradio.cavesandcliffsbackport.height.Heights;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernGenerator;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernTerrain;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Biomes;
import com.exiledradio.cavesandcliffsbackport.upgrade.OldChunks;
import com.exiledradio.cavesandcliffsbackport.upgrade.StructureGuard;
import com.exiledradio.cavesandcliffsbackport.upgrade.WorldUpgrade;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Another mod's world type (RTG, OTG) from y -64 to 319 ({@code height.otherWorldTypes}): its own generator makes every
 * chunk from y 0 up and each gets 1.18's part below 0 as it is made ({@code terrain.ModernGenerator#hybridChunkMade}).
 * Runs on a real dedicated server with the other mod installed ({@code tools/height/hybrid.sh}):
 *
 * <ul>
 *   <li>{@code hybridnew}, a fresh world of that type: it is extended and stays that type; a 16 x 16 chunk area is made
 *       and every chunk is, from y 0 up, exactly what its own generator made (but the bedrock at y 0..4, now
 *       deepslate) before it populated; below 0 it has deepslate, caves, fluids, deepslate ores and bedrock at -64;
 *       its 3D biomes are its own column biome from y 0 up, and lush or dripstone caves occur below 0; no structure
 *       start was emptied. Writes {@code hybrid-area.bin}.</li>
 *   <li>{@code hybridreload}, the same world after a restart: every chunk of the area loads as it was saved and none
 *       gets its part below 0 again; chunks made since get theirs.</li>
 *   <li>{@code hybridold1}, a fresh world of that type made 0-255 (the type taken out of the config): a 10 x 10 area,
 *       then {@code /cavesandcliffs upgradeworld} asked and confirmed. Writes {@code hybrid-old.bin}.</li>
 *   <li>{@code hybridold2}, that world again with the type back in the config: it is upgraded and still that type;
 *       its old chunks keep every block from y 0 up (but the bedrock) and have 1.18's below 0; new chunks too.</li>
 * </ul>
 */
public final class HybridSelfTest implements SelfTest {

    private static final int SIDE = 16;
    private static final int OLD_SIDE = 10;
    /**
     * The area's corner chunk: away from the spawn chunks, so the test makes it, and where 1.18's noise has lush caves
     * below 0 ({@link #locate}); kept in {@code hybrid-origin.txt} for the restart.
     */
    private static int X0 = 150;
    private static int Z0 = -170;

    /** Moves the area onto lush caves in 1.18's noise (biome3d answers the noise for chunks not loaded). */
    private static void locate(WorldServer world, ModernGenerator host) throws Exception {
        search:
        for (int r = 0; r <= 40; r += 2) {
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int cx = 160 + dx;
                    int cz = -160 + dz;
                    if (world.getChunkProvider().getLoadedChunk(cx, cz) == null
                            && host.biome3d((cx << 4) + 8, -30, (cz << 4) + 8) == Biomes.LUSH_CAVES) {
                        X0 = cx - SIDE / 2;
                        Z0 = cz - SIDE / 2;
                        break search;
                    }
                }
            }
        }
        java.nio.file.Files.write(file(world, "hybrid-origin.txt").toPath(), (X0 + " " + Z0).getBytes("UTF-8"));
        info("area at chunk " + X0 + ", " + Z0);
    }

    static void info(String s) {
        com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport.LOGGER.info("SELFTEST INFO {}", s);
    }
    private final String phase;

    HybridSelfTest(String phase) {
        this.phase = phase;
    }

    @Override
    public String name() {
        return phase;
    }

    @Override
    public void run(Check check) throws Exception {
        switch (phase) {
            case "hybridnew":
                fresh(check);
                break;
            case "hybridreload":
                reload(check);
                break;
            case "hybridold1":
                old1(check);
                break;
            default:
                old2(check);
                break;
        }
    }

    // ---------------------------------------------------------------------------------------------

    /** Each chunk's blocks from y 0 to 255 as its own generator made them, and as they are once the chunk is in. */
    static final class Snapshots {
        final Map<Long, char[]> made = new HashMap<>();
        final Map<Long, char[]> in = new HashMap<>();

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void first(ChunkEvent.Load event) {
            if (!event.getWorld().isRemote && event.getWorld().provider.getDimension() == 0) {
                made.put(key(event.getChunk().x, event.getChunk().z), blocks(event.getChunk()));
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void last(ChunkEvent.Load event) {
            if (!event.getWorld().isRemote && event.getWorld().provider.getDimension() == 0) {
                in.put(key(event.getChunk().x, event.getChunk().z), blocks(event.getChunk()));
            }
        }
    }

    static long key(int cx, int cz) {
        return (long) cx << 32 | (cz & 0xFFFFFFFFL);
    }

    static char[] blocks(Chunk c) {
        char[] out = new char[65536];
        int i = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 256; y++) {
                    out[i++] = (char) OldChunks.stateId(c.getBlockState(x, y, z));
                }
            }
        }
        return out;
    }

    private static boolean deepslateOre(IBlockState s) {
        String n = String.valueOf(s.getBlock().getRegistryName());
        return n.contains("deepslate") && n.contains("ore");
    }

    private static boolean deepslate(IBlockState s) {
        return String.valueOf(s.getBlock().getRegistryName()).contains("deepslate");
    }

    /** What is below y 0 in a set of chunks, and a hash of every block from -64 to 319. */
    static final class Below {
        int chunks;
        int withSections;
        int bedrockFloor;
        int bedrockHigh;
        long deepslate;
        long solid;
        long air;
        long fluids;
        long ores;

        void add(Chunk c) {
            chunks++;
            boolean all = true;
            for (int y = -64; y < 0; y += 16) {
                all &= c.getBlockStorageArray()[Heights.slot(c.getWorld(), y)] != Chunk.NULL_BLOCK_STORAGE;
            }
            if (all) {
                withSections++;
            }
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (c.getBlockState(x, -64, z).getBlock() == Blocks.BEDROCK) {
                        bedrockFloor++;
                    }
                    for (int y = 0; y <= 4; y++) {
                        if (c.getBlockState(x, y, z).getBlock() == Blocks.BEDROCK) {
                            bedrockHigh++;
                        }
                    }
                    for (int y = -60; y < 0; y++) {
                        IBlockState s = c.getBlockState(x, y, z);
                        Material m = s.getMaterial();
                        if (m == Material.AIR) {
                            air++;
                        } else if (m.isLiquid()) {
                            fluids++;
                        } else {
                            solid++;
                            if (deepslate(s)) {
                                deepslate++;
                            }
                            if (deepslateOre(s)) {
                                ores++;
                            }
                        }
                    }
                }
            }
        }

        String summary() {
            return chunks + " chunks, " + withSections + " with all four sections below 0, bedrock at -64 in " + bedrockFloor
                    + " columns, bedrock at y 0..4: " + bedrockHigh + "; below 0: " + deepslate + "/" + solid
                    + " solid blocks deepslate, " + ores + " deepslate ores, " + air + " air, " + fluids + " fluid";
        }

        void check(Check check, String what) {
            info(what + ": " + summary());
            check.eq(chunks, withSections, what + ": every chunk has its four sections below y 0 (" + summary() + ")");
            check.ok(bedrockFloor >= chunks * 256 * 9 / 10, what + ": bedrock at y -64 (" + bedrockFloor + " columns)");
            check.eq(0, bedrockHigh, what + ": no bedrock left at y 0..4");
            check.ok(deepslate > solid * 8 / 10, what + ": below 0 is deepslate (" + deepslate + "/" + solid + ")");
            check.ok(ores > chunks * 3L, what + ": deepslate ores below 0 (" + ores + ")");
            check.ok(air > chunks * 100L, what + ": caves below 0 (" + air + " air)");
            check.ok(fluids > 0, what + ": aquifer fluids below 0 (" + fluids + ")");
        }
    }

    static long hash(Chunk c) {
        long h = 1125899906842597L;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = -64; y < 320; y++) {
                    h = 31 * h + OldChunks.stateId(c.getBlockState(x, y, z));
                }
            }
        }
        return h;
    }

    /** The checks every hybrid world passes: extended, still its own type and generator, the pipeline a hybrid one. */
    private static boolean world(Check check, WorldServer world) {
        WorldType type = world.getWorldInfo().getTerrainType();
        WorldUpgrade up = WorldUpgrade.of(world);
        ModernGenerator host = ModernTerrain.host(world);
        boolean ok = check.ok(Heights.isExtended(world) && Heights.minY(world) == -64 && Heights.maxY(world) == 320,
                "the " + type.getName() + " world runs from y -64 to 319");
        ok &= check.ok(type != HeightContent.worldType() && type != WorldType.DEFAULT,
                "it keeps its own world type (" + type.getName() + ")");
        ok &= check.ok(up != null && up.hybrid(), "it is a hybrid world (every chunk gets its part below 0)");
        ok &= check.ok(host != null && host.isHybrid() && !ModernTerrain.owns(world),
                "the 1.18 pipeline runs beside its own generator ("
                        + world.getChunkProvider().chunkGenerator.getClass().getName() + ")");
        return ok;
    }

    /** Biomes: from y 0 up each column's own (as a 1.18 id; plains for one 1.18 has none of), cave biomes below 0. */
    private static int[] biomes(Check check, WorldServer world, ModernGenerator host, int x0, int z0, int side) {
        int lush = 0;
        int dripstone = 0;
        int deepDark = 0;
        int wrong = 0;
        int samples = 0;
        int answered = 0;
        for (int cx = x0; cx < x0 + side; cx++) {
            for (int cz = z0; cz < z0 + side; cz++) {
                for (int lx = 2; lx < 16; lx += 4) {
                    for (int lz = 2; lz < 16; lz += 4) {
                        int x = (cx << 4) + lx;
                        int z = (cz << 4) + lz;
                        for (int y = -58; y < 0; y += 8) {
                            int b = host.biome3d(x, y, z);
                            lush += b == Biomes.LUSH_CAVES ? 1 : 0;
                            dripstone += b == Biomes.DRIPSTONE_CAVES ? 1 : 0;
                            deepDark += b == Biomes.DEEP_DARK ? 1 : 0;
                        }
                        // Away from a column's edges the zoom keeps the column's biome; at y 100 it is the surface's.
                        if (lx == 6 && lz == 6) {
                            samples++;
                            int up = host.biome3d(x, 100, z);
                            if (up == Biomes.LUSH_CAVES || up == Biomes.DRIPSTONE_CAVES || up == Biomes.DEEP_DARK) {
                                wrong++;
                            }
                        }
                        if (ModernTerrain.biomeAt(world, x, 64, z) != -1) {
                            answered++;
                        }
                    }
                }
            }
        }
        check.eq(0, answered, "no 1.18 biome answers from y 0 up in a hybrid world (the 1.17 cave regions stand)");
        check.eq(0, wrong, "no cave biome from y 0 up (" + samples + " columns at y 100)");
        info("cave biomes below 0 (quart samples): lush " + lush + ", dripstone " + dripstone + ", deep dark " + deepDark);
        check.ok(lush + dripstone > 0, "lush or dripstone caves below 0: lush " + lush + ", dripstone " + dripstone
                + ", deep dark " + deepDark);
        return new int[] {lush, dripstone, deepDark};
    }

    // ---------------------------------------------------------------------------------------------
    // hybridnew

    private void fresh(Check check) throws Exception {
        WorldServer world = check.world;
        if (!world(check, world)) {
            return;
        }
        ModernGenerator host = ModernTerrain.host(world);
        locate(world, host);
        int emptied = StructureGuard.emptied;
        Snapshots snaps = new Snapshots();
        MinecraftForge.EVENT_BUS.register(snaps);
        long t = System.nanoTime();
        int before = host.upgradedChunks;
        try {
            for (int x = X0 - 1; x <= X0 + SIDE; x++) {
                for (int z = Z0 - 1; z <= Z0 + SIDE; z++) {
                    world.getChunkProvider().provideChunk(x, z);
                }
            }
        } finally {
            MinecraftForge.EVENT_BUS.unregister(snaps);
        }
        long ms = (System.nanoTime() - t) / 1000000L;
        int made = host.upgradedChunks - before;
        check.ok(made >= (SIDE + 2) * (SIDE + 2), "every chunk made got its part below 0 (" + made + " in " + ms + " ms, "
                + (made == 0 ? 0 : ms / made) + " ms per chunk with " + world.getWorldInfo().getTerrainType().getName()
                + "'s own generation)");
        // From y 0 up, exactly the generator's chunk (but the bedrock at y 0..4).
        int compared = 0;
        long otherChanges = 0;
        long bedrockToDeepslate = 0;
        for (Map.Entry<Long, char[]> e : snaps.made.entrySet()) {
            char[] after = snaps.in.get(e.getKey());
            if (after == null) {
                continue;
            }
            compared++;
            char[] b = e.getValue();
            for (int i = 0; i < b.length; i++) {
                if (b[i] != after[i]) {
                    int y = i & 255;
                    if (y <= 4 && OldChunks.state(b[i]) != null && OldChunks.state(b[i]).getBlock() == Blocks.BEDROCK
                            && deepslate(OldChunks.state(after[i]))) {
                        bedrockToDeepslate++;
                    } else {
                        otherChanges++;
                    }
                }
            }
        }
        check.ok(compared >= SIDE * SIDE, "chunks compared as made and as they joined the world: " + compared);
        info(compared + " chunks compared, " + bedrockToDeepslate + " bedrock blocks at y 0..4 became deepslate, "
                + otherChanges + " other changes from y 0 up; " + made + " chunks made in " + ms + " ms");
        check.eq(0L, otherChanges, "from y 0 up every block is the generator's (" + bedrockToDeepslate
                + " bedrock blocks at y 0..4 became deepslate)");
        check.ok(bedrockToDeepslate > 0, "its bedrock at y 0..4 became deepslate");
        Below below = new Below();
        for (int x = X0; x < X0 + SIDE; x++) {
            for (int z = Z0; z < Z0 + SIDE; z++) {
                Chunk c = world.getChunk(x, z);
                below.add(c);
                check.ok(!WorldUpgrade.of(world).pending(x, z), "chunk " + x + ", " + z + " is done");
            }
        }
        below.check(check, "new chunks");
        biomes(check, world, host, X0, Z0, SIDE);
        check.eq(emptied, StructureGuard.emptied, "no structure start was emptied (the generator's own structures stand)");
        otgAbove255(check, world);
        // For the restart: every block of the area.
        world.getMinecraftServer().getPlayerList().saveAllPlayerData();
        world.saveAllChunks(true, null);
        world.getChunkProvider().flushToDisk();
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file(world, "hybrid-area.bin")))) {
            for (int x = X0; x < X0 + SIDE; x++) {
                for (int z = Z0; z < Z0 + SIDE; z++) {
                    out.writeLong(hash(world.getChunk(x, z)));
                    out.writeInt(host.biome3d((x << 4) + 6, -30, (z << 4) + 6));
                }
            }
        }
    }

    /**
     * OTG only: a chunk with blocks above y 255 that OTG then populates in a cold biome. Its snow and ice pass walks
     * down each column from the height map; OTG's getMaterial answered null above 255 and the server crashed
     * ({@code height.compat.OtgHooks}).
     */
    private static void otgAbove255(Check check, WorldServer world) {
        if (!net.minecraftforge.fml.common.Loader.isModLoaded("openterraingenerator")) {
            return;
        }
        int found = Integer.MIN_VALUE;
        int fz = 0;
        search:
        for (int r = 30; r < 400; r += 3) {
            for (int a = 0; a < 16; a++) {
                int cx = (int) Math.round(r * Math.cos(a * Math.PI / 8));
                int cz = (int) Math.round(r * Math.sin(a * Math.PI / 8));
                boolean fresh = true;
                for (int dx = -1; dx <= 2 && fresh; dx++) {
                    for (int dz = -1; dz <= 2 && fresh; dz++) {
                        fresh = !world.getChunkProvider().isChunkGeneratedAt(cx + dx, cz + dz);
                    }
                }
                net.minecraft.util.math.BlockPos p = new net.minecraft.util.math.BlockPos((cx << 4) + 8, 64, (cz << 4) + 8);
                if (fresh && world.getBiomeProvider().getBiome(p).getTemperature(p) < 0.15F) {
                    found = cx;
                    fz = cz;
                    break search;
                }
            }
        }
        if (!check.ok(found != Integer.MIN_VALUE, "OTG: a cold biome with fresh land around it, for the test above y 255")) {
            return;
        }
        int cx = found;
        int cz = fz;
        try {
            Chunk c = world.getChunkProvider().provideChunk(cx, cz);
            check.ok(!c.isTerrainPopulated(), "OTG: the chunk is not populated yet");
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    world.setBlockState(new net.minecraft.util.math.BlockPos((cx << 4) + x, 280, (cz << 4) + z),
                            Blocks.STONE.getDefaultState(), 2);
                }
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.getChunkProvider().provideChunk(cx + dx, cz + dz);
                }
            }
            check.ok(world.getChunk(cx, cz).isTerrainPopulated() && world.getChunk(cx - 1, cz - 1).isTerrainPopulated(),
                    "OTG populated chunks under and beside blocks at y 280 (snow and ice pass) without a crash");
            info("OTG above 255: chunk " + cx + ", " + cz + " (" + world.getBiome(new net.minecraft.util.math.BlockPos(cx << 4, 64, cz << 4)).getRegistryName()
                    + "), populated with a platform at y 280");
        } catch (RuntimeException e) {
            com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport.LOGGER.error("OTG above 255 threw", e);
            check.fail("OTG: populating beside blocks above y 255 threw " + e);
        }
    }

    private static File file(WorldServer world, String name) {
        return world.getMinecraftServer().getFile(name);
    }

    // ---------------------------------------------------------------------------------------------
    // hybridreload

    private void reload(Check check) throws Exception {
        WorldServer world = check.world;
        if (!world(check, world)) {
            return;
        }
        ModernGenerator host = ModernTerrain.host(world);
        String[] origin = new String(java.nio.file.Files.readAllBytes(file(world, "hybrid-origin.txt").toPath()), "UTF-8")
                .trim().split(" ");
        X0 = Integer.parseInt(origin[0]);
        Z0 = Integer.parseInt(origin[1]);
        int before = host.upgradedChunks;
        int same = 0;
        int sameBiome = 0;
        try (DataInputStream in = new DataInputStream(new FileInputStream(file(world, "hybrid-area.bin")))) {
            for (int x = X0; x < X0 + SIDE; x++) {
                for (int z = Z0; z < Z0 + SIDE; z++) {
                    long h = in.readLong();
                    int b = in.readInt();
                    Chunk c = world.getChunkProvider().provideChunk(x, z);
                    if (hash(c) == h) {
                        same++;
                    }
                    if (host.biome3d((x << 4) + 6, -30, (z << 4) + 6) == b) {
                        sameBiome++;
                    }
                }
            }
        }
        check.eq(0, host.upgradedChunks - before, "no chunk of the area got its part below 0 again");
        check.eq(SIDE * SIDE, same, "every chunk of the area loads as it was saved, y -64 to 319");
        check.eq(SIDE * SIDE, sameBiome, "the 3D biomes below 0 come back with the chunks");
        // New chunks after the restart.
        Below below = new Below();
        int nx = X0 + SIDE + 4;
        for (int x = nx - 1; x <= nx + 4; x++) {
            for (int z = Z0 - 1; z <= Z0 + 4; z++) {
                world.getChunkProvider().provideChunk(x, z);
            }
        }
        for (int x = nx; x < nx + 4; x++) {
            for (int z = Z0; z < Z0 + 4; z++) {
                below.add(world.getChunk(x, z));
            }
        }
        below.check(check, "chunks made after the restart");
    }

    // ---------------------------------------------------------------------------------------------
    // The client's height on a dedicated server: login, and a respawn from the Nether and the End

    /** What the server did for the test player, in order: "height" (joined the overworld: height.HeightPacket sent), "+x,z"
     * and "-x,z" (a chunk sent and unloaded), "respawned". */
    static final class Order {
        final java.util.List<String> events = new java.util.ArrayList<>();
        final String name;

        Order(String name) {
            this.name = name;
        }

        private boolean ours(net.minecraft.entity.Entity e) {
            return e instanceof net.minecraft.entity.player.EntityPlayerMP && name.equals(((net.minecraft.entity.player.EntityPlayerMP) e).getName());
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void told(net.minecraftforge.fml.common.network.FMLNetworkEvent.ServerConnectionFromClientEvent e) {
            if (e.getHandler() instanceof net.minecraft.network.NetHandlerPlayServer
                    && ours(((net.minecraft.network.NetHandlerPlayServer) e.getHandler()).player)) {
                events.add("told");
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void join(net.minecraftforge.event.entity.EntityJoinWorldEvent e) {
            if (ours(e.getEntity()) && !e.getWorld().isRemote) {
                events.add("join" + e.getWorld().provider.getDimension());
            }
        }

        @SubscribeEvent
        public void watch(net.minecraftforge.event.world.ChunkWatchEvent.Watch e) {
            if (ours(e.getPlayer()) && e.getPlayer().dimension == 0) {
                events.add("+" + e.getChunk().x + "," + e.getChunk().z);
            }
        }

        @SubscribeEvent
        public void unwatch(net.minecraftforge.event.world.ChunkWatchEvent.UnWatch e) {
            if (ours(e.getPlayer()) && e.getPlayer().dimension == 0) {
                events.add("-" + e.getChunk().x + "," + e.getChunk().z);
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void respawned(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent e) {
            if (ours(e.player)) {
                events.add("respawned");
            }
        }

        /**
         * From the last "join0" of the list on: every chunk sent before the client could know its height (between
         * the start and the join) is unloaded and sent again after it; returns the ones that are not.
         */
        java.util.Set<String> stale(int from) {
            java.util.Set<String> before = new java.util.LinkedHashSet<>();
            int join = -1;
            for (int i = from; i < events.size(); i++) {
                String s = events.get(i);
                if (s.equals("join0")) {
                    join = i;
                    break;
                }
                if (s.startsWith("+")) {
                    before.add(s.substring(1));
                }
            }
            if (join < 0) {
                before.add("(no join0)");
                return before;
            }
            java.util.Set<String> unloaded = new java.util.HashSet<>();
            java.util.Set<String> stale = new java.util.LinkedHashSet<>();
            for (int i = join; i < events.size(); i++) {
                String s = events.get(i);
                if (s.startsWith("-")) {
                    unloaded.add(s.substring(1));
                } else if (s.startsWith("+") && unloaded.contains(s.substring(1))) {
                    before.remove(s.substring(1));
                }
            }
            stale.addAll(before);
            return stale;
        }
    }

    /** A player logged in through PlayerList on a connection that drops every packet (FML's too). */
    static net.minecraft.entity.player.EntityPlayerMP connect(net.minecraft.server.MinecraftServer server,
            WorldServer world, String name) {
        net.minecraft.entity.player.EntityPlayerMP player = new net.minecraft.entity.player.EntityPlayerMP(server, world,
                new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(("selftest:" + name).getBytes()), name),
                new net.minecraft.server.management.PlayerInteractionManager(world));
        net.minecraft.network.NetworkManager network = new net.minecraft.network.NetworkManager(
                net.minecraft.network.EnumPacketDirection.CLIENTBOUND) {
            @Override
            public void sendPacket(net.minecraft.network.Packet<?> packet) {
            }

            @Override
            @SuppressWarnings("unchecked")
            public void sendPacket(net.minecraft.network.Packet<?> packet,
                    io.netty.util.concurrent.GenericFutureListener<? extends io.netty.util.concurrent.Future<? super Void>> listener,
                    io.netty.util.concurrent.GenericFutureListener<? extends io.netty.util.concurrent.Future<? super Void>>... more) {
            }
        };
        io.netty.channel.embedded.EmbeddedChannel channel = new io.netty.channel.embedded.EmbeddedChannel();
        net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraft.network.NetworkManager.class,
                network, channel, "channel", "field_150746_k");
        // An FML dispatcher, as a real connection has after the handshake: mods that send their packets to every
        // connecting player (OTG's PlayerTracker) throw without one. What it sends goes to the no-op network above.
        channel.attr(net.minecraftforge.fml.common.network.handshake.NetworkDispatcher.FML_DISPATCHER).set(
                new net.minecraftforge.fml.common.network.handshake.NetworkDispatcher(network, server.getPlayerList()));
        net.minecraft.network.NetHandlerPlayServer handler = new net.minecraft.network.NetHandlerPlayServer(server, network, player);
        server.getPlayerList().initializeConnectionToPlayer(network, player, handler);
        return player;
    }

    private static String first(java.util.Set<String> s) {
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (String x : s) {
            if (n++ == 5) {
                break;
            }
            b.append(x).append(' ');
        }
        return b.toString().trim();
    }

    // ---------------------------------------------------------------------------------------------
    // hybridold1 / hybridold2

    private static final int OX0 = -120;
    private static final int OZ0 = 90;

    private void old1(Check check) throws Exception {
        WorldServer world = check.world;
        WorldType type = world.getWorldInfo().getTerrainType();
        if (!check.ok(!Heights.isExtended(world) && type != WorldType.DEFAULT && type != HeightContent.worldType(),
                "hybridold1 runs in a fresh 0-255 world of another mod's type (" + type.getName() + ")")) {
            return;
        }
        for (int x = OX0 - 1; x <= OX0 + OLD_SIDE; x++) {
            for (int z = OZ0 - 1; z <= OZ0 + OLD_SIDE; z++) {
                world.getChunkProvider().provideChunk(x, z);
            }
        }
        try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(
                new FileOutputStream(file(world, "hybrid-old.bin"))))) {
            for (int x = OX0; x < OX0 + OLD_SIDE; x++) {
                for (int z = OZ0; z < OZ0 + OLD_SIDE; z++) {
                    for (char c : blocks(world.getChunk(x, z))) {
                        out.writeChar(c);
                    }
                }
            }
        }
        // The world was made with the type out of the config (so y 0-255); now it is back in, as a player would add it.
        com.exiledradio.cavesandcliffsbackport.ModConfig.height.otherWorldTypes = new String[] {"RTG", "OTG"};
        UpgradeSelfTest.Sender sender = new UpgradeSelfTest.Sender(world.getMinecraftServer());
        world.getMinecraftServer().getCommandManager().executeCommand(sender, "cavesandcliffs upgradeworld");
        check.ok(sender.all().contains("confirm within 60 seconds") && sender.all().contains(type.getName() + "'s terrain"),
                "the command accepts the " + type.getName() + " world and says what happens: " + sender.all());
        sender.said.clear();
        world.getMinecraftServer().getCommandManager().executeCommand(sender, "cavesandcliffs upgradeworld confirm");
        check.ok(sender.all().contains("upgrades on its next load"), "the confirm marks the world: " + sender.all());
    }

    private void old2(Check check) throws Exception {
        WorldServer world = check.world;
        if (!world(check, world)) {
            return;
        }
        ModernGenerator host = ModernTerrain.host(world);
        int before = host.upgradedChunks;
        long changed = 0;
        long bedrock = 0;
        Below below = new Below();
        try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(
                new FileInputStream(file(world, "hybrid-old.bin"))))) {
            for (int x = OX0; x < OX0 + OLD_SIDE; x++) {
                for (int z = OZ0; z < OZ0 + OLD_SIDE; z++) {
                    Chunk c = world.getChunkProvider().provideChunk(x, z);
                    char[] now = blocks(c);
                    for (int i = 0; i < now.length; i++) {
                        char was = in.readChar();
                        if (was != now[i]) {
                            Block b = OldChunks.state(was) == null ? null : OldChunks.state(was).getBlock();
                            if ((i & 255) <= 4 && b == Blocks.BEDROCK && deepslate(OldChunks.state(now[i]))) {
                                bedrock++;
                            } else {
                                changed++;
                            }
                        }
                    }
                    below.add(c);
                }
            }
        }
        check.ok(host.upgradedChunks - before >= OLD_SIDE * OLD_SIDE, "every old chunk got its part below 0 as it loaded ("
                + (host.upgradedChunks - before) + ")");
        check.eq(0L, changed, "old chunks keep every block from y 0 up (" + bedrock + " bedrock blocks became deepslate)");
        below.check(check, "old chunks");
        Below fresh = new Below();
        int nx = OX0 + OLD_SIDE + 3;
        for (int x = nx - 1; x <= nx + 4; x++) {
            for (int z = OZ0 - 1; z <= OZ0 + 4; z++) {
                world.getChunkProvider().provideChunk(x, z);
            }
        }
        for (int x = nx; x < nx + 4; x++) {
            for (int z = OZ0; z < OZ0 + 4; z++) {
                fresh.add(world.getChunk(x, z));
            }
        }
        fresh.check(check, "new chunks of the upgraded world");
        UpgradeSelfTest.Sender sender = new UpgradeSelfTest.Sender(world.getMinecraftServer());
        world.getMinecraftServer().getCommandManager().executeCommand(sender, "cavesandcliffs upgradeworld");
        check.ok(sender.all().contains("upgraded already"), "a second upgrade is refused: " + sender.all());
    }
}
