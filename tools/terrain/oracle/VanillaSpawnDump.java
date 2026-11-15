package net.minecraft.world.level.levelgen;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.Bootstrap;

import java.io.File;
import java.io.PrintWriter;
import java.util.Random;

/**
 * Vanilla 1.18.2's climate spawn position (Climate.Sampler.findSpawnPosition, where MinecraftServer.setInitialSpawn
 * starts) as spawns.txt, one "seed x z" line per seed: the seeds given, then `random` more from a fixed Random, for
 * the Caves and Cliffs Backport's SpawnCheck. Arguments: outDir random [seeds...]
 */
public final class VanillaSpawnDump {
    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        int random = Integer.parseInt(args[1]);
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RegistryAccess access = RegistryAccess.builtinCopy();
        Random rnd = new Random(1182L);
        long[] seeds = new long[args.length - 2 + random];
        for (int i = 2; i < args.length; i++) {
            seeds[i - 2] = Long.parseLong(args[i]);
        }
        for (int i = args.length - 2; i < seeds.length; i++) {
            seeds[i] = i % 2 == 0 ? rnd.nextLong() : rnd.nextInt(100000);
        }
        try (PrintWriter p = new PrintWriter(new File(out, "spawns.txt"), "UTF-8")) {
            for (long seed : seeds) {
                BlockPos pos = WorldGenSettings.makeDefaultOverworld(access, seed).climateSampler().findSpawnPosition();
                p.println(seed + " " + pos.getX() + " " + pos.getZ());
            }
        }
        System.out.println(seeds.length + " seeds");
    }
}
