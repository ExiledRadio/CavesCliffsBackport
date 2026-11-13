package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;

import net.minecraft.world.WorldServer;

/**
 * Generates and populates a square of chunks for an offline scan of the region files (generation parity: azalea
 * trees, lush caves, geodes against 1.18.2). Runs only when named ({@code -Pselftest=genscan}); the square's
 * center and side, in chunks, come from {@code genscan.txt} in the run directory, {@code x,z,side} (default 0,0,48). The
 * server's stop saves the chunks.
 */
public final class GenScanSelfTest implements SelfTest {

    @Override
    public String name() {
        return "genscan";
    }

    @Override
    public void run(Check check) throws Exception {
        String asked = System.getProperty(CavesAndCliffsBackport.MOD_ID + ".selftest", "");
        if (!java.util.Arrays.asList(asked.split(",")).contains(name())) {
            return;
        }
        java.io.File f = check.world.getMinecraftServer().getFile("genscan.txt");
        String spec = f.exists() ? new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8").trim() : "0,0,48";
        String[] a = spec.split(",");
        int cx = Integer.parseInt(a[0].trim()), cz = Integer.parseInt(a[1].trim()), side = Integer.parseInt(a[2].trim());
        WorldServer world = check.world;
        long t0 = System.nanoTime();
        int n = 0;
        for (int x = cx - side / 2; x <= cx + side / 2; x++) {
            for (int z = cz - side / 2; z <= cz + side / 2; z++) {
                world.getChunkProvider().provideChunk(x, z);
                n++;
            }
        }
        CavesAndCliffsBackport.LOGGER.info(String.format(java.util.Locale.ROOT, "GENSCAN %d chunks around %d,%d in %.1f s",
                n, cx, cz, (System.nanoTime() - t0) / 1e9));
        check.ok(n > 0, "chunks generated");
    }
}
