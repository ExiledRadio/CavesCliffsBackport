package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.lush.BlockGlowLichen;
import com.exiledradio.cavesandcliffsbackport.lush.LushCaveGenerator;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Glow lichen density in this world, measured the way {@code LichenStats} measures 1.18.2's feature dumps: lichen
 * blocks a chunk, underground air (air or water at most 13 below the column's ocean floor) and eligible air (underground
 * air with lichen-able stone above it or beside it). Runs only when named ({@code -Pselftest=lichencensus}); the
 * square's center and side, in chunks, come from {@code lichencensus.txt} in the run directory, {@code x,z,side}
 * (default 0,0,24). Only the chunks whose whole population window is inside the square are counted.
 */
public final class LichenCensusSelfTest implements SelfTest {

    @Override
    public String name() {
        return "lichencensus";
    }

    @Override
    public void run(Check check) throws Exception {
        String asked = System.getProperty(CavesAndCliffsBackport.MOD_ID + ".selftest", "");
        if (!java.util.Arrays.asList(asked.split(",")).contains(name())) {
            return;
        }
        java.io.File f = check.world.getMinecraftServer().getFile("lichencensus.txt");
        String spec = f.exists() ? new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8").trim() : "0,0,24";
        String[] a = spec.split(",");
        int cx = Integer.parseInt(a[0].trim()), cz = Integer.parseInt(a[1].trim()), side = Integer.parseInt(a[2].trim());
        WorldServer world = check.world;
        int x0 = cx - side / 2, z0 = cz - side / 2;
        for (int x = x0; x < x0 + side; x++) {
            for (int z = z0; z < z0 + side; z++) {
                world.getChunkProvider().provideChunk(x, z);
            }
        }
        long lichen = 0, faces = 0, floors = 0, air = 0, eligible = 0;
        List<Integer> perChunk = new ArrayList<Integer>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        // A chunk is fully populated once it and its +x, +z and +x+z neighbors were generated: skip the last row.
        for (int x = x0 + 1; x < x0 + side - 1; x++) {
            for (int z = z0 + 1; z < z0 + side - 1; z++) {
                int cl = 0;
                for (int bx = x << 4; bx < (x << 4) + 16; bx++) {
                    for (int bz = z << 4; bz < (z << 4) + 16; bz++) {
                        int limit = LushCaveGenerator.oceanFloor(world, bx, bz) - 13;
                        for (int y = 1; y < 256; y++) {
                            IBlockState s = world.getBlockState(m.setPos(bx, y, bz));
                            int mask = BlockGlowLichen.faces(s);
                            if (mask != 0) {
                                lichen++;
                                cl++;
                                faces += Integer.bitCount(mask);
                                if ((mask & 1) != 0) {
                                    floors++;
                                }
                            }
                            if (y > limit) {
                                continue;
                            }
                            boolean open = mask != 0 || s.getBlock() == Blocks.AIR || s.getMaterial() == Material.WATER;
                            if (open) {
                                air++;
                                if (LushCaveGenerator.lichenBase(world.getBlockState(m.setPos(bx, y + 1, bz)))
                                        || LushCaveGenerator.lichenBase(world.getBlockState(m.setPos(bx - 1, y, bz)))
                                        || LushCaveGenerator.lichenBase(world.getBlockState(m.setPos(bx + 1, y, bz)))
                                        || LushCaveGenerator.lichenBase(world.getBlockState(m.setPos(bx, y, bz - 1)))
                                        || LushCaveGenerator.lichenBase(world.getBlockState(m.setPos(bx, y, bz + 1)))) {
                                    eligible++;
                                }
                            }
                        }
                    }
                }
                perChunk.add(cl);
            }
        }
        Collections.sort(perChunk);
        int n = perChunk.size();
        CavesAndCliffsBackport.LOGGER.info(String.format(Locale.ROOT,
            "LICHENCENSUS chunks %d, lichen blocks %d (%.2f/chunk, median %d, p90 %d, max %d), faces %d, with floor face %d",
            n, lichen, lichen / (double) n, perChunk.get(n / 2), perChunk.get(n * 9 / 10), perChunk.get(n - 1), faces, floors));
        CavesAndCliffsBackport.LOGGER.info(String.format(Locale.ROOT,
            "LICHENCENSUS underground air %d (%.0f/chunk): lichen per 1000 air %.3f; eligible air %d (%.0f/chunk): lichen per 1000 eligible %.3f",
            air, air / (double) n, 1000.0 * lichen / air, eligible, eligible / (double) n, 1000.0 * lichen / eligible));
        check.ok(n > 0, "chunks counted");
    }
}
