package com.exiledradio.cavesandcliffsbackport.upgrade;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.height.HeightContent;
import com.exiledradio.cavesandcliffsbackport.height.HeightHooks;
import com.exiledradio.cavesandcliffsbackport.height.HeightLayout;
import com.exiledradio.cavesandcliffsbackport.height.Heights;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.fml.common.Loader;

import javax.annotation.Nullable;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Upgrading an existing 0-255 world to y -64..319, the way 1.18 upgraded old worlds: {@code /cavesandcliffs
 * upgradeworld} marks the world, and on its next load it switches. Every chunk the world had then is an old chunk; each
 * gets its part below y 0 when it first loads ({@code terrain.ModernGenerator#upgradeChunk}), and new chunks next to
 * old ones blend toward them.
 *
 * <p>The state is the world's {@code data/cavesandcliffsbackport_upgrade.dat}: {@code State} (pending or upgraded),
 * then for an upgraded world which chunks are old and which of those are upgraded, one bit per chunk per region file.
 * It is written in place of nothing else, so stopping the game at any point leaves either the old world or the
 * upgraded one.
 */
public final class WorldUpgrade {

    public static final String FILE = CavesAndCliffsBackport.MOD_ID + "_upgrade.dat";
    static final String PENDING = "pending";
    static final String UPGRADED = "upgraded";
    private static final Pattern REGION = Pattern.compile("^r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.mca$");

    private static final Map<World, WorldUpgrade> WORLDS = new WeakHashMap<>();

    private final File file;
    /** Per region (x << 32 | z): 1024 bits, chunk (x & 31) + (z & 31) * 32. */
    private final Map<Long, long[]> old = new HashMap<>();
    private final Map<Long, long[]> done = new HashMap<>();
    private int oldCount;
    private int doneCount;
    private boolean dirty;
    /**
     * Another mod's world type (RTG, OTG: {@code height.otherWorldTypes}) with y -64..319: its own generator makes every
     * chunk from y 0 up, so every chunk is old and gets 1.18's part below 0 as it is made or first loads.
     */
    private boolean hybrid;

    private WorldUpgrade(File file) {
        this.file = file;
    }

    /** The upgraded world's state, or null for a world that is not an upgraded one. Server side. */
    @Nullable
    public static synchronized WorldUpgrade of(World world) {
        return world == null || world.isRemote ? null : WORLDS.get(world);
    }

    // ---------------------------------------------------------------------------------------------
    // Old chunks

    private static long region(int cx, int cz) {
        return (long) (cx >> 5) << 32 | ((cz >> 5) & 0xFFFFFFFFL);
    }

    private static int bit(int cx, int cz) {
        return (cx & 31) + (cz & 31) * 32;
    }

    private static boolean get(Map<Long, long[]> map, int cx, int cz) {
        long[] bits = map.get(region(cx, cz));
        int b = bit(cx, cz);
        return bits != null && (bits[b >> 6] & 1L << b) != 0;
    }

    /** Whether the chunk existed when the world was upgraded (BlendingData's old noise). */
    public boolean isOld(int cx, int cz) {
        return hybrid || get(old, cx, cz);
    }

    /**
     * Whether this world keeps another mod's generator (RTG, OTG) from y 0 up: every chunk gets 1.18's part below 0,
     * and that generator places its structures as before (no structure guard).
     */
    public boolean hybrid() {
        return hybrid;
    }

    /** Whether new worlds of this type get y -64..319 under their own terrain ({@code height.otherWorldTypes}). */
    public static boolean hybridType(@Nullable WorldType type) {
        if (type == null || type == WorldType.DEFAULT || type == HeightContent.worldType()) {
            return false;
        }
        for (String name : com.exiledradio.cavesandcliffsbackport.ModConfig.height.otherWorldTypes) {
            if (name != null && name.trim().equalsIgnoreCase(type.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * A new world of a {@link #hybridType} (no region files yet), from {@code HeightHooks.worldConstructed}: it runs from
     * y -64 to 319 under its own terrain from the start.
     */
    public static void startHybrid(World world, File dir) {
        // Its part below 0 is 1.18's terrain, with the newer biomes the installed backports have, as a new world's.
        com.exiledradio.cavesandcliffsbackport.terrain.ModernTerrain.chooseModern(dir);
        WorldUpgrade up = new WorldUpgrade(new File(new File(dir, "data"), FILE));
        up.hybrid = true;
        up.dirty = true;
        up.save();
        synchronized (WorldUpgrade.class) {
            WORLDS.put(world, up);
        }
        CavesAndCliffsBackport.LOGGER.info("New {} world: y -64 to 319. Its own terrain from y 0 up; below 0 each chunk "
                + "gets 1.18's as it is made.", world.getWorldInfo().getTerrainType().getName());
    }

    /** Whether the chunk is an old one that has not had its part below y 0 yet. */
    public boolean pending(int cx, int cz) {
        return (hybrid || get(old, cx, cz)) && !get(done, cx, cz);
    }

    /** The chunk got its part below y 0 (or never will: no bedrock at y 0); saved with the world. */
    public void markDone(int cx, int cz) {
        long r = region(cx, cz);
        long[] bits = done.get(r);
        if (bits == null) {
            bits = new long[16];
            done.put(r, bits);
        }
        int b = bit(cx, cz);
        if ((bits[b >> 6] & 1L << b) == 0) {
            bits[b >> 6] |= 1L << b;
            doneCount++;
            dirty = true;
        }
    }

    /** Whether any old chunk lies within r chunks of cx, cz. */
    public boolean oldWithin(int cx, int cz, int r) {
        if (hybrid) {
            return true;
        }
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (isOld(cx + dx, cz + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    public int oldCount() {
        return oldCount;
    }

    public int doneCount() {
        return doneCount;
    }

    // ---------------------------------------------------------------------------------------------
    // The switch, at load

    /**
     * From the end of the overworld's constructor ({@code HeightHooks.worldConstructed}), before its height is read:
     * a world marked by the command switches to y -64..319 now; an upgraded world gets its state.
     */
    public static void worldConstructed(World world, File dir, WorldInfo info) {
        File file = new File(new File(dir, "data"), FILE);
        NBTTagCompound tag = read(file);
        if (tag == null) {
            return;
        }
        String state = tag.getString("State");
        if (PENDING.equals(state)) {
            String why = refusalAtLoad(dir, info);
            if (why != null) {
                CavesAndCliffsBackport.LOGGER.error("World upgrade to y -64..319 NOT done: {}. The world keeps y 0-255; "
                        + "/cavesandcliffs upgradeworld cancel clears the request.", why);
                return;
            }
            WorldUpgrade up = new WorldUpgrade(file);
            up.scan(new File(dir, "region"));
            up.hybrid = hybridType(info.getTerrainType());
            NBTTagCompound out = up.write();
            out.setString("State", UPGRADED);
            out.setLong("Upgraded", System.currentTimeMillis());
            // The order makes a crash anywhere safe: until the state file says upgraded, the world is the old one.
            com.exiledradio.cavesandcliffsbackport.terrain.ModernTerrain.chooseModern(dir);
            writeHeight(dir);
            if (!writeAtomic(file, out)) {
                CavesAndCliffsBackport.LOGGER.error("World upgrade: could not write {}; the world keeps y 0-255.", file);
                return;
            }
            tag = out;
            if (up.hybrid) {
                CavesAndCliffsBackport.LOGGER.info("World upgraded to y -64..319: {} old chunks get their part below y 0 as "
                        + "they load, and so does every new {} chunk as it is made. level.dat was backed up as {}.",
                        up.oldCount, info.getTerrainType().getName(), UpgradeCommand.BACKUP);
            } else {
                CavesAndCliffsBackport.LOGGER.info("World upgraded to y -64..319: {} old chunks get their part below y 0 as "
                        + "they load; new chunks blend toward them. level.dat was backed up as {}.", up.oldCount,
                        UpgradeCommand.BACKUP);
            }
            state = UPGRADED;
        }
        if (!UPGRADED.equals(state)) {
            return;
        }
        WorldType ours = HeightContent.worldType();
        if (ours == null) {
            CavesAndCliffsBackport.LOGGER.error("This world was upgraded to y -64..319, but extended height is off ({}). "
                    + "It loads as y 0-255: blocks below 0 and above 255 are not loaded. Turn extended height back on "
                    + "before playing it.", HeightContent.refusal());
            return;
        }
        boolean hybrid = tag.getBoolean("Hybrid");
        if (!hybrid && info.getTerrainType() != ours) {
            // level.dat is written by the game, at its next save; until then it may still name the old type.
            info.setTerrainType(ours);
        }
        WorldUpgrade up = new WorldUpgrade(file);
        up.read(tag);
        up.hybrid = hybrid;
        synchronized (WorldUpgrade.class) {
            WORLDS.put(world, up);
        }
    }

    /** Why a marked world cannot switch now (the command checked the same when it was asked), or null. */
    @Nullable
    private static String refusalAtLoad(File dir, WorldInfo info) {
        String why = refusal();
        if (why != null) {
            return why;
        }
        if (info.getTerrainType() != WorldType.DEFAULT && !hybridType(info.getTerrainType())) {
            return "the world type is " + info.getTerrainType().getName() + ", not default (or one of height.otherWorldTypes)";
        }
        File height = new File(new File(dir, "data"), HeightHooks.DATA_FILE);
        NBTTagCompound saved = read(height);
        if (saved != null && saved.getInteger("MinY") != 0) {
            return "the world is extended already";
        }
        return null;
    }

    /**
     * Why no world can be upgraded in this game, or null: extended height must be on (no Cubic Chunks, Caves Not
     * Cliffs, Farmer's Future Delight, other Alfheim or Phosphor versions; see {@code docs/EXTENDED_HEIGHT.md}), and
     * the structure guard patched.
     */
    @Nullable
    public static String refusal() {
        if (Loader.isModLoaded("cubicchunks")) {
            return "Cubic Chunks is installed (it stores its own heights; this mod's extended height is off with it)";
        }
        if (!HeightContent.allowed()) {
            return "extended height is off: " + HeightContent.refusal();
        }
        if (HeightHooks.overridden() != null) {
            return "extended height is off: " + HeightHooks.overridden();
        }
        String stellar = com.exiledradio.cavesandcliffsbackport.height.asm.StellarCoreCompat.version();
        String adapted = com.exiledradio.cavesandcliffsbackport.height.asm.StellarCoreCompat.result();
        if (stellar != null && adapted != null && !"adapted".equals(adapted) && !"not overwritten".equals(adapted)) {
            return "StellarCore " + stellar + " keeps World.isOutsideBuildHeight at 0-255 (" + adapted
                    + "); set B:WorldBlockPosJudgement=false in config/stellar_core.cfg";
        }
        if (!StructureGuard.patched()) {
            return "the structure guard (MapGenStructure.recursiveGenerate) could not be patched, so structures could "
                    + "start in old chunks";
        }
        return null;
    }

    private static void writeHeight(File dir) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("MinY", HeightLayout.EXTENDED_MIN_Y);
        tag.setInteger("MaxY", HeightLayout.EXTENDED_MAX_Y);
        writeAtomic(new File(new File(dir, "data"), HeightHooks.DATA_FILE), tag);
    }

    /** Every chunk in the region files: the region header's location table. */
    private void scan(File regions) {
        String[] names = regions.list();
        if (names == null) {
            return;
        }
        for (String name : names) {
            Matcher m = REGION.matcher(name);
            if (!m.matches()) {
                continue;
            }
            int rx = Integer.parseInt(m.group(1));
            int rz = Integer.parseInt(m.group(2));
            File f = new File(regions, name);
            if (f.length() < 4096) {
                continue;
            }
            long[] bits = new long[16];
            int n = 0;
            try (DataInputStream in = new DataInputStream(new FileInputStream(f))) {
                for (int i = 0; i < 1024; i++) {
                    if (in.readInt() != 0) {
                        bits[i >> 6] |= 1L << i;
                        n++;
                    }
                }
            } catch (IOException e) {
                CavesAndCliffsBackport.LOGGER.warn("World upgrade: could not read {} ({}); its chunks count as new.", f, e.toString());
                continue;
            }
            if (n > 0) {
                old.put((long) rx << 32 | (rz & 0xFFFFFFFFL), bits);
                oldCount += n;
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Saving

    /** From WorldEvent.Save and Unload: the upgraded chunks, when any changed. */
    public synchronized void save() {
        if (!dirty) {
            return;
        }
        NBTTagCompound tag = write();
        tag.setString("State", UPGRADED);
        if (writeAtomic(file, tag)) {
            dirty = false;
        }
    }

    private NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        if (hybrid) {
            tag.setBoolean("Hybrid", true);
        }
        tag.setTag("Old", list(old));
        tag.setTag("Done", list(done));
        return tag;
    }

    private void read(NBTTagCompound tag) {
        oldCount = unlist(tag.getTagList("Old", 10), old);
        doneCount = unlist(tag.getTagList("Done", 10), done);
    }

    private static NBTTagList list(Map<Long, long[]> map) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<Long, long[]> e : map.entrySet()) {
            NBTTagCompound r = new NBTTagCompound();
            r.setInteger("X", (int) (e.getKey() >> 32));
            r.setInteger("Z", (int) (long) e.getKey());
            int[] ints = new int[32];
            long[] bits = e.getValue();
            for (int i = 0; i < 16; i++) {
                ints[2 * i] = (int) bits[i];
                ints[2 * i + 1] = (int) (bits[i] >>> 32);
            }
            r.setIntArray("Chunks", ints);
            list.appendTag(r);
        }
        return list;
    }

    private static int unlist(NBTTagList list, Map<Long, long[]> map) {
        int n = 0;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound r = list.getCompoundTagAt(i);
            int[] ints = r.getIntArray("Chunks");
            if (ints.length != 32) {
                continue;
            }
            long[] bits = new long[16];
            for (int j = 0; j < 16; j++) {
                bits[j] = ints[2 * j] & 0xFFFFFFFFL | (long) ints[2 * j + 1] << 32;
                n += Long.bitCount(bits[j]);
            }
            map.put((long) r.getInteger("X") << 32 | (r.getInteger("Z") & 0xFFFFFFFFL), bits);
        }
        return n;
    }

    @Nullable
    static NBTTagCompound read(File file) {
        if (!file.isFile()) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            return CompressedStreamTools.readCompressed(in);
        } catch (Exception e) {
            CavesAndCliffsBackport.LOGGER.error("World upgrade: could not read {}.", file, e);
            return null;
        }
    }

    /** Writes a temporary file and moves it over the old one, so a crash leaves one or the other whole. */
    static boolean writeAtomic(File file, NBTTagCompound tag) {
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            file.getParentFile().mkdirs();
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                CompressedStreamTools.writeCompressed(tag, out);
            }
            java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            CavesAndCliffsBackport.LOGGER.error("World upgrade: could not write {}.", file, e);
            return false;
        }
    }

    /** Whether the world is marked for the switch at its next load. */
    static boolean isPending(File dir) {
        NBTTagCompound tag = read(new File(new File(dir, "data"), FILE));
        return tag != null && PENDING.equals(tag.getString("State"));
    }

    /** Marks the world for the switch at its next load. */
    static boolean markPending(File dir) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("State", PENDING);
        tag.setLong("Requested", System.currentTimeMillis());
        return writeAtomic(new File(new File(dir, "data"), FILE), tag);
    }

    /** Clears a pending request. */
    static boolean cancel(File dir) {
        File f = new File(new File(dir, "data"), FILE);
        return isPending(dir) && f.delete();
    }

    /** Whether the overworld is extended now (a Caves &amp; Cliffs world, or one upgraded earlier). */
    static boolean extendedNow(World overworld) {
        return Heights.isExtended(overworld);
    }
}
