package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.height.AlfheimHooks;
import com.exiledradio.cavesandcliffsbackport.height.HeightContent;
import com.exiledradio.cavesandcliffsbackport.height.PhosphorHooks;
import com.exiledradio.cavesandcliffsbackport.height.Heights;
import com.exiledradio.cavesandcliffsbackport.height.asm.PatchReport;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.Loader;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Extended height with a replaced lighting engine, on a dedicated server with that mod installed and a Caves &amp;
 * Cliffs world: Alfheim 1.6 (phases {@code alfheim*}; plan {@code docs/streams/optifine-alfheim.md}, section 2, test 3)
 * or Phosphor/Hesperus (phases {@code phosphor*}; {@code docs/streams/height.md}, "Phosphor"). Both engines share the
 * structures checked here (Alfheim is a Phosphor descendant):
 *
 * <ol>
 *   <li>{@code alfheimlight1} / {@code phosphorlight1}, fresh world: the engine's classes patched and the wide key in use; a torch at y -30 by a
 *       chunk border lights across it, torches at y -60 and y 300 light their surroundings; a shaft dug from the surface to -60 gets sky light (and a corridor off its
 *       bottom), loses it when capped and gets it back when opened; sky light under a roof at y 310 (sections that
 *       were empty); no chunk's boundary flags use a bit above 11. Writes what it built and the light it expects to
 *       {@code alfheim-light.txt} / {@code phosphor-light.txt}; the server then stops and saves.</li>
 *   <li>{@code alfheimlight2} / {@code phosphorlight2}, the same world on a new server: every light of the file read back the same, then the
 *       open shaft capped and the capped one opened, relit from the saved state; the flags read from the save.</li>
 * </ol>
 *
 * {@code alfheimdump} / {@code phosphordump} (any world) writes a hash of the sky and block light of a fixed 9x9 chunks
 * around 0, 0 to {@code alfheim-dump.txt} / {@code phosphor-dump.txt}: the same Default world with and without extended
 * height on must give the same file.
 */
public final class LightEngineSelfTest implements SelfTest {

    /** A chunk border (x 44000 is chunk 2750's first block), far from every other test. */
    private static final int BX = 44000;
    private static final int Z0 = 40008;

    private final String phase;
    /** "alfheim" or "phosphor": the phase's prefix and its files' names. */
    private final String engine;

    LightEngineSelfTest(String phase) {
        this.phase = phase;
        this.engine = phase.startsWith("phosphor") ? "phosphor" : "alfheim";
    }

    private boolean phosphor() {
        return "phosphor".equals(this.engine);
    }

    @Override
    public String name() {
        return this.phase;
    }

    @Override
    public void run(Check check) throws Exception {
        WorldServer world = check.world;
        if (this.phase.endsWith("dump")) {
            dump(check, world, this.engine + "-dump.txt");
            return;
        }
        String mod = phosphor() ? "phosphor-lighting" : "alfheim";
        if (!check.ok(Loader.isModLoaded(mod), this.phase + " runs with " + mod + " installed")
                || !check.ok(Heights.isExtended(world), this.phase + " runs in an extended world (refusal: "
                + HeightContent.refusal() + ")")) {
            return;
        }
        check.ok(phosphor() ? PhosphorHooks.wide() : AlfheimHooks.wide(), this.engine + "'s engine uses the wide position key");
        String[] labels = phosphor()
                ? new String[] {"PhosphorLightingEngine", "PhosphorLightingHooks", "PhosphorLightingEngineHelpers", "PhosphorMixinChunk"}
                : new String[] {"AlfheimLightingEngine", "AlfheimChunkMixin", "AlfheimChunkCacheMixin"};
        for (String label : labels) {
            check.ok(PatchReport.classPatched(label), label + " patched");
        }
        prepare(world, BX, Z0, 3);
        File file = world.getMinecraftServer().getFile(this.engine + "-light.txt");
        if (this.phase.endsWith("light1")) {
            build(check, world, file);
        } else {
            reread(check, world, file);
        }
        flags(check, world, phosphor() ? "getNeighborLightChecks" : "alfheim$getNeighborLightChecks");
    }

    private static void build(Check check, WorldServer world, File file) throws Exception {
        StringBuilder expect = new StringBuilder();

        // A torch at y -30 in the last column of one chunk, a tunnel through the next one.
        // Stone around it first, so a natural cave (or lava) beside it changes nothing.
        for (int x = BX - 5; x <= BX + 7; x++) {
            for (int y = -31; y <= -28; y++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.setBlockState(new BlockPos(x, y, Z0 + dz), Blocks.STONE.getDefaultState(), 2);
                }
            }
        }
        for (int x = BX - 4; x <= BX + 6; x++) {
            for (int y = -30; y <= -29; y++) {
                world.setBlockState(new BlockPos(x, y, Z0), Blocks.AIR.getDefaultState(), 3);
            }
        }
        BlockPos torch = new BlockPos(BX - 1, -30, Z0);
        world.setBlockState(torch, Blocks.TORCH.getDefaultState(), 3);
        check.eq(14, block(world, torch), "block light at a torch at y -30");
        check.eq(13, block(world, torch.east()), "block light across the chunk border at y -30");
        check.eq(8, block(world, torch.east(6)), "block light 6 blocks into the next chunk at y -30");
        check.eq(10, block(world, torch.west(3).up()), "block light 3 back and 1 up at y -29");
        expect.append(line("block", torch.east(), 13)).append(line("block", torch.east(6), 8));

        // A torch at y -60, four above the world's bottom, in a sealed tunnel; and one at y 300 in open air (sections
        // above 255, empty until the torch makes one).
        for (int x = BX - 4; x <= BX + 4; x++) {
            for (int y = -61; y <= -58; y++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.setBlockState(new BlockPos(x, y, Z0 - 10 + dz), Blocks.STONE.getDefaultState(), 2);
                }
            }
        }
        for (int x = BX - 3; x <= BX + 3; x++) {
            world.setBlockState(new BlockPos(x, -60, Z0 - 10), Blocks.AIR.getDefaultState(), 3);
        }
        BlockPos deep = new BlockPos(BX - 3, -60, Z0 - 10);
        world.setBlockState(deep, Blocks.TORCH.getDefaultState(), 3);
        check.eq(14, block(world, deep), "block light at a torch at y -60");
        check.eq(13, block(world, deep.east()), "block light 1 from a torch at y -60");
        check.eq(8, block(world, deep.east(6)), "block light 6 along the tunnel at y -60 (across the chunk border)");
        check.eq(0, block(world, deep.down()), "no block light in the stone under the torch at y -61");
        BlockPos high = new BlockPos(BX - 2, 300, Z0 - 10);
        world.setBlockState(high.down(), Blocks.STONE.getDefaultState(), 3);
        world.setBlockState(high, Blocks.TORCH.getDefaultState(), 3);
        check.eq(14, block(world, high), "block light at a torch at y 300");
        check.eq(13, block(world, high.up()), "block light above a torch at y 300");
        check.eq(9, block(world, high.up(5)), "block light 5 above a torch at y 300");
        check.eq(10, block(world, high.east(4)), "block light 4 east of a torch at y 300 (across the chunk border)");
        check.eq(15, sky(world, high.up(3)), "sky light beside a torch at y 303");
        expect.append(line("block", deep.east(), 13)).append(line("block", deep.east(6), 8))
                .append(line("block", high.up(5), 9)).append(line("block", high.east(4), 10));

        // Shafts from the surface to -60, a corridor east off the bottom of each.
        BlockPos open = shaft(world, BX - 8, Z0 + 20);
        BlockPos capped = shaft(world, BX + 8, Z0 + 20);
        for (BlockPos bottom : new BlockPos[] {open, capped}) {
            check.eq(15, sky(world, bottom), "sky light at the bottom of a shaft at y -60");
            check.eq(15, sky(world, new BlockPos(bottom.getX(), -1, bottom.getZ())), "sky light in the shaft at y -1");
            check.eq(12, sky(world, bottom.east(3)), "sky light 3 into a corridor at y -60");
            check.eq(0, sky(world, bottom.down()), "no sky light in the stone under the shaft");
            check.eq(0, sky(world, bottom.west()), "no sky light in the stone beside the shaft");
        }
        BlockPos lid = lid(world, capped);
        world.setBlockState(lid, Blocks.STONE.getDefaultState(), 3);
        check.eq(0, sky(world, capped), "no sky light at the bottom of a capped shaft");
        check.eq(0, sky(world, capped.east(3)), "no sky light in a capped shaft's corridor");
        world.setBlockState(lid, Blocks.AIR.getDefaultState(), 3);
        check.eq(15, sky(world, capped), "sky light back at the bottom when the shaft is opened again");
        check.eq(12, sky(world, capped.east(3)), "sky light back in the corridor");
        world.setBlockState(lid, Blocks.STONE.getDefaultState(), 3);
        check.eq(0, sky(world, capped), "capped again");
        expect.append(line("sky", open, 15)).append(line("sky", open.east(3), 12))
                .append(line("sky", capped, 0)).append(line("sky", capped.east(3), 0))
                .append(line("lid", lid(world, open), 0)).append(line("lid", lid, 0));

        // A 5x5 roof at y 310 in the empty sections above the terrain: sky light under its middle comes from its sides.
        BlockPos roof = new BlockPos(BX - 20, 310, Z0 - 20);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.setBlockState(roof.add(dx, 0, dz), Blocks.STONE.getDefaultState(), 3);
            }
        }
        check.eq(12, sky(world, roof.down()), "sky light under the middle of a roof at y 310");
        check.eq(15, sky(world, roof.up()), "sky light on the roof");
        expect.append(line("sky", roof.down(), 12));
        Files.write(file.toPath(), expect.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void reread(Check check, WorldServer world, File file) throws Exception {
        if (!check.ok(file.isFile(), file.getName() + " from the first phase")) {
            return;
        }
        BlockPos openLid = null;
        BlockPos cappedLid = null;
        for (String raw : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            String[] p = raw.trim().split(" ");
            if (p.length != 5) {
                continue;
            }
            BlockPos pos = new BlockPos(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
            int want = Integer.parseInt(p[4]);
            if ("lid".equals(p[0])) {
                if (openLid == null) {
                    openLid = pos;
                } else {
                    cappedLid = pos;
                }
                continue;
            }
            int got = "sky".equals(p[0]) ? sky(world, pos) : block(world, pos);
            check.eq(want, got, p[0] + " light at " + pos + " after a save and reload");
        }
        if (!check.ok(openLid != null && cappedLid != null, "both shafts in the file")) {
            return;
        }
        BlockPos open = bottomUnder(openLid);
        BlockPos capped = bottomUnder(cappedLid);
        world.setBlockState(openLid, Blocks.STONE.getDefaultState(), 3);
        check.eq(0, sky(world, open), "the open shaft capped after the reload: dark at y -60");
        check.eq(0, sky(world, open.east(3)), "and in its corridor");
        world.setBlockState(cappedLid, Blocks.AIR.getDefaultState(), 3);
        check.eq(15, sky(world, capped), "the capped shaft opened after the reload: sky light at y -60");
        check.eq(12, sky(world, capped.east(3)), "and in its corridor");
    }

    /** No boundary flag of a loaded chunk uses a bit an extended world's 12 do not have. */
    private static void flags(Check check, WorldServer world, String getter) throws Exception {
        Method get = null;
        int chunks = 0;
        int set = 0;
        int high = 0;
        for (Chunk chunk : world.getChunkProvider().getLoadedChunks()) {
            if (get == null) {
                get = chunk.getClass().getMethod(getter);
            }
            short[] flags = (short[]) get.invoke(chunk);
            chunks++;
            if (flags == null) {
                continue;
            }
            for (short f : flags) {
                set += Integer.bitCount(f & 0xFFFF);
                high += Integer.bitCount(f & 0xF000);
            }
        }
        check.ok(chunks > 0, "loaded chunks to read flags from");
        check.eq(0, high, "boundary flag bits above 11 (" + set + " set in " + chunks + " chunks)");
    }

    /**
     * Digs a 1x1 shaft from the top of the terrain to y -60 inside a stone sleeve (no cave, water or lava reaches it),
     * and a 3-block corridor east at the bottom, sealed too.
     */
    private static BlockPos shaft(WorldServer world, int x, int z) {
        int top = world.getChunk(new BlockPos(x, 0, z)).getHeightValue(x & 15, z & 15);
        for (int y = top + 1; y >= -61; y--) {
            for (int dx = -1; dx <= 4; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx <= 1 || y <= -59) {
                        world.setBlockState(new BlockPos(x + dx, y, z + dz), Blocks.STONE.getDefaultState(), 2);
                    }
                }
            }
        }
        for (int y = top + 1; y >= -60; y--) {
            world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3);
        }
        BlockPos bottom = new BlockPos(x, -60, z);
        for (int i = 1; i <= 3; i++) {
            world.setBlockState(bottom.east(i), Blocks.AIR.getDefaultState(), 3);
        }
        return bottom;
    }

    /** Where a shaft is capped: y -1, always inside it. */
    private static BlockPos lid(WorldServer world, BlockPos bottom) {
        return new BlockPos(bottom.getX(), -1, bottom.getZ());
    }

    private static BlockPos bottomUnder(BlockPos lid) {
        return new BlockPos(lid.getX(), -60, lid.getZ());
    }

    private static String line(String kind, BlockPos p, int light) {
        return kind + " " + p.getX() + " " + p.getY() + " " + p.getZ() + " " + light + "\n";
    }

    private static int sky(WorldServer world, BlockPos pos) {
        return world.getLightFor(EnumSkyBlock.SKY, pos);
    }

    private static int block(WorldServer world, BlockPos pos) {
        return world.getLightFor(EnumSkyBlock.BLOCK, pos);
    }

    /** A hash per chunk of sky and block light at every block of 9x9 chunks around 0, 0. */
    private static void dump(Check check, WorldServer world, String name) throws Exception {
        prepare(world, 0, 0, 5);
        StringBuilder out = new StringBuilder();
        long all = 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int cx = -4; cx <= 4; cx++) {
            for (int cz = -4; cz <= 4; cz++) {
                long h = 1125899906842597L;
                for (int y = Heights.minY(world); y < Heights.maxY(world); y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            p.setPos((cx << 4) + x, y, (cz << 4) + z);
                            h = 31 * h + world.getLightFor(EnumSkyBlock.SKY, p);
                            h = 31 * h + world.getLightFor(EnumSkyBlock.BLOCK, p);
                        }
                    }
                }
                out.append(cx).append(' ').append(cz).append(' ').append(Long.toHexString(h)).append('\n');
                all = 31 * all + h;
            }
        }
        out.append("all ").append(Long.toHexString(all)).append('\n');
        Files.write(world.getMinecraftServer().getFile(name).toPath(),
                out.toString().getBytes(StandardCharsets.UTF_8));
        check.ok(true, "light dump written (" + Long.toHexString(all) + ")");
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
}
