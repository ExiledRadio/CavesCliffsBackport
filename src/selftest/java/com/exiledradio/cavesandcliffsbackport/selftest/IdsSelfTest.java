package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.height.HeightHooks;
import com.exiledradio.cavesandcliffsbackport.height.HeightLayout;
import com.exiledradio.cavesandcliffsbackport.height.Heights;
import com.exiledradio.cavesandcliffsbackport.height.IdsCompat;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Extended height with Roughly Enough IDs / Just Enough IDs ({@code height.IdsCompat}); passes without them too.
 *
 * <ul>
 *   <li>{@code ids}, a fresh Caves &amp; Cliffs world: generates 2x2 chunks at 4000, 4000; fills a section below 0
 *       (y -48..-33) and one above 255 (y 288..303) with more than 256 different states (two-byte palette indices);
 *       checks each chunk's NBT round trip (every block -64..319, the biomes); checks every column has a real biome
 *       (not REID's error biome); checks a section saved without its palette by C&amp;C 1.1.5 under REID is left empty
 *       and kept aside. Writes {@code ids-dump.txt} (the natural chunks' blocks and biomes, for comparing a run without
 *       REID) and {@code ids-markers.txt}; the server then stops and saves.</li>
 *   <li>{@code ids2}, the same world: the filled sections and the dump read back from the region files.</li>
 * </ul>
 * {@code sh tools/height/ids-selftest.sh} runs both on a real server with each REID/JEID jar and once without.
 */
public final class IdsSelfTest implements SelfTest {

    private static final int CX = 250;
    private static final int CZ = 250;
    private static final int LOW = -48;
    private static final int HIGH = 288;

    private final String phase;

    IdsSelfTest(String phase) {
        this.phase = phase;
    }

    @Override
    public String name() {
        return phase;
    }

    @Override
    public void run(Check check) throws Exception {
        WorldServer world = check.world;
        boolean ids = Loader.isModLoaded("jeid");
        CavesAndCliffsBackport.LOGGER.info("SELFTEST ids: REID/JEID {}, palette handled {}, extended {}", ids ? "installed" : "absent",
                IdsCompat.present(), Heights.isExtended(world));
        check.eq(ids, IdsCompat.present(), "REID/JEID's palette is handled exactly when it is installed");
        if (!check.ok(Heights.isExtended(world), "the world is a Caves & Cliffs (extended) world")) {
            return;
        }
        File dump = world.getMinecraftServer().getFile("ids-dump.txt");
        File markers = world.getMinecraftServer().getFile("ids-markers.txt");
        if ("ids2".equals(phase)) {
            List<String> before = Files.readAllLines(dump.toPath(), StandardCharsets.UTF_8);
            List<String> after = dump(world);
            List<String> differ = new ArrayList<String>();
            for (int i = 0; i < Math.max(before.size(), after.size()); i++) {
                String a = i < before.size() ? before.get(i) : "-";
                String b = i < after.size() ? after.get(i) : "-";
                if (!a.equals(b)) {
                    differ.add(b.length() > 60 ? b.substring(0, 60) : b);
                }
            }
            check.eq("[]", differ.toString(), "the 2x2 chunks' sections and biomes that changed across the restart");
            List<String> marks = Files.readAllLines(markers.toPath(), StandardCharsets.UTF_8);
            int wrong = 0;
            String first = null;
            for (String line : marks) {
                String[] f = line.split("\t");
                BlockPos p = new BlockPos(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]));
                String got = String.valueOf(world.getBlockState(p));
                if (!got.equals(f[3])) {
                    wrong++;
                    if (first == null) {
                        first = p + ": " + got + " for " + f[3];
                    }
                }
            }
            check.eq(0, wrong, "the filled sections below 0 and above 255 after the restart" + (first == null ? "" : " (" + first + ")"));
            check.ok(marks.size() == 8192, "both filled sections were recorded (" + marks.size() + " blocks)");
            return;
        }

        // 4x4 chunks, so the 2x2 inside are populated and every neighbor that decorates into them has been too.
        for (int dx = -1; dx < 3; dx++) {
            for (int dz = -1; dz < 3; dz++) {
                world.getChunkProvider().provideChunk(CX + dx, CZ + dz);
            }
        }
        biomes(check, world);
        // The dump is of the natural chunks, before the fill.
        Files.write(dump.toPath(), dump(world), StandardCharsets.UTF_8);

        Chunk chunk = world.getChunk(CX, CZ);
        List<IBlockState> states = new ArrayList<IBlockState>();
        for (Block block : Block.REGISTRY) {
            // Blocks that stay put untouched: full cubes, no tile entity, no random ticks, nothing that falls.
            IBlockState def = block.getDefaultState();
            if (block.hasTileEntity(def) || block.getTickRandomly() || block instanceof net.minecraft.block.BlockFalling
                    || !def.isFullCube() || def.getMaterial().isLiquid()) {
                continue;
            }
            for (IBlockState s : block.getBlockState().getValidStates()) {
                // Only states 1.12 saves as they are (not ones whose properties come from the neighbors).
                if (Block.BLOCK_STATE_IDS.get(s) >= 0 && block.getStateFromMeta(block.getMetaFromState(s)) == s) {
                    states.add(s);
                }
            }
        }
        check.ok(states.size() > 256, "more than 256 states to fill with (" + states.size() + ")");
        List<String> lines = new ArrayList<String>();
        for (int base : new int[] {LOW, HIGH}) {
            int slot = Heights.slot(world, base);
            ExtendedBlockStorage section = chunk.getBlockStorageArray()[slot];
            if (section == Chunk.NULL_BLOCK_STORAGE) {
                // Above 255 the natural chunk is air: no section yet.
                section = new ExtendedBlockStorage(base, world.provider.hasSkyLight());
                chunk.getBlockStorageArray()[slot] = section;
            }
            check.eq(base, section.getYLocation(), "the section at y " + base + " sits at its height");
            for (int i = 0; i < 4096; i++) {
                int x = i & 15, y = i >> 8 & 15, z = i >> 4 & 15;
                IBlockState s = i % 5 == 0 ? Blocks.AIR.getDefaultState() : states.get(Math.floorMod(i * 37 + base, states.size()));
                section.set(x, y, z, s);
                lines.add(((CX << 4) + x) + "\t" + (base + y) + "\t" + ((CZ << 4) + z) + "\t" + s);
            }
            section.recalculateRefCounts();
        }
        chunk.markDirty();
        Files.write(markers.toPath(), lines, StandardCharsets.UTF_8);

        AnvilChunkLoader loader = (AnvilChunkLoader) world.getChunkProvider().chunkLoader;
        Method write = ObfuscationReflectionHelper.findMethod(AnvilChunkLoader.class, "func_75820_a", void.class,
                Chunk.class, World.class, NBTTagCompound.class);
        Method read = ObfuscationReflectionHelper.findMethod(AnvilChunkLoader.class, "func_75823_a", Chunk.class,
                World.class, NBTTagCompound.class);
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                Chunk c = world.getChunk(CX + dx, CZ + dz);
                NBTTagCompound level = new NBTTagCompound();
                write.invoke(loader, c, world, level);
                if (ids) {
                    NBTTagList ours = level.getTagList(HeightHooks.SECTIONS, 10);
                    int palettes = 0;
                    for (int i = 0; i < ours.tagCount(); i++) {
                        palettes += ours.getCompoundTagAt(i).hasKey("Palette", 11) ? 1 : 0;
                    }
                    check.eq(ours.tagCount(), palettes, "chunk " + c.x + "," + c.z + ": every section of our list has REID's Palette");
                    check.eq(11, (int) level.getTag("Biomes").getId(), "chunk " + c.x + "," + c.z + ": REID saves the biomes as ints");
                }
                Chunk back = (Chunk) read.invoke(loader, world, level);
                int wrong = 0;
                String first = null;
                for (int y = -64; y < 320; y++) {
                    for (int i = 0; i < 256; i++) {
                        IBlockState a = c.getBlockState(i & 15, y, i >> 4);
                        IBlockState b = back.getBlockState(i & 15, y, i >> 4);
                        if (a != b) {
                            wrong++;
                            if (first == null) {
                                first = (i & 15) + "," + y + "," + (i >> 4) + ": " + b + " for " + a;
                            }
                        }
                    }
                }
                check.eq(0, wrong, "chunk " + c.x + "," + c.z + ": blocks -64..319 after the NBT round trip"
                        + (first == null ? "" : " (" + first + ")"));
                check.eq(join(IdsCompat.biomeBytes(c)), join(IdsCompat.biomeBytes(back)),
                        "chunk " + c.x + "," + c.z + ": biomes after the NBT round trip");
                if (ids && dx == 1 && dz == 1) {
                    lostPalette(check, world, loader, read, level, back);
                }
            }
        }
    }

    /** A save of C&amp;C 1.1.5 under REID: our list without Palette. Left empty and kept aside, never decoded. */
    private static void lostPalette(Check check, WorldServer world, AnvilChunkLoader loader, Method read, NBTTagCompound level,
            Chunk good) throws Exception {
        NBTTagCompound old = level.copy();
        NBTTagList ours = old.getTagList(HeightHooks.SECTIONS, 10);
        for (int i = 0; i < ours.tagCount(); i++) {
            ours.getCompoundTagAt(i).removeTag("Palette");
        }
        int cx = old.getInteger("xPos");
        int cz = old.getInteger("zPos");
        File lost = new File(new File(world.getSaveHandler().getWorldDirectory(), CavesAndCliffsBackport.MOD_ID + "_lost_sections"),
                "c." + cx + "." + cz + ".dat");
        lost.delete();
        Chunk back = (Chunk) read.invoke(loader, world, old);
        int filled = 0;
        for (int slot = HeightLayout.VANILLA_SECTIONS; slot < back.getBlockStorageArray().length; slot++) {
            filled += back.getBlockStorageArray()[slot] != Chunk.NULL_BLOCK_STORAGE ? 1 : 0;
        }
        check.eq(0, filled, "an old REID save's sections without palette are left empty, not decoded");
        int same = 0;
        for (int y = 0; y < 256; y += 5) {
            same += back.getBlockState(3, y, 7) == good.getBlockState(3, y, 7) ? 1 : 0;
        }
        check.eq(52, same, "its sections 0-255 read as usual");
        check.ok(lost.isFile(), "its unreadable sections are kept in " + lost);
        lost.delete();
    }

    /** Every column of the 2x2 chunks has a real 1.12 biome (REID's error biome or none means the ids were lost). */
    private static void biomes(Check check, WorldServer world) {
        int bad = 0;
        String first = null;
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                Chunk c = world.getChunk(CX + dx, CZ + dz);
                int[] ints = IdsCompat.intBiomes(c);
                for (int i = 0; i < 256; i++) {
                    Biome b = Biome.getBiome(ints[i]);
                    String ns = b == null ? null : b.getRegistryName().getNamespace();
                    if (b == null || !"minecraft".equals(ns) && !CavesAndCliffsBackport.MOD_ID.equals(ns)) {
                        bad++;
                        if (first == null) {
                            first = "chunk " + c.x + "," + c.z + " column " + i + ": " + ints[i] + " " + (b == null ? null : b.getRegistryName());
                        }
                    }
                }
            }
        }
        check.eq(0, bad, "every column has a vanilla or Caves & Cliffs biome" + (first == null ? "" : " (" + first + ")"));
    }

    /** Per chunk: a hash of every block -64..319 (except the filled sections) and the biome bytes. */
    private static List<String> dump(WorldServer world) {
        List<String> out = new ArrayList<String>();
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                Chunk c = world.getChunk(CX + dx, CZ + dz);
                out.add(c.x + "," + c.z + " biomes " + join(IdsCompat.biomeBytes(c)));
                for (int base = -64; base < 320; base += 16) {
                    if (dx == 0 && dz == 0 && (base == LOW || base == HIGH)) {
                        continue;
                    }
                    long h = 1125899906842597L;
                    for (int y = base; y < base + 16; y++) {
                        for (int i = 0; i < 256; i++) {
                            h = 31 * h + c.getBlockState(i & 15, y, i >> 4).toString().hashCode();
                        }
                    }
                    out.add(c.x + "," + c.z + " y " + base + " blocks " + Long.toHexString(h));
                }
            }
        }
        return out;
    }

    private static String join(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) {
            s.append(x & 255).append(' ');
        }
        return s.toString();
    }
}
