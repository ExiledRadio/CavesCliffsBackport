package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Climate;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;
import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

/**
 * The 1.12 biome provider of a Caves &amp; Cliffs world: 1.18.2's biome source, read at each column's surface (the
 * noise's preliminary surface level, as 1.18 structures read it), mapped to 1.12 biomes ({@link BiomeMap}). It answers
 * for places no chunk exists yet: 1.12 structures' biome checks, the spawn search, other mods. Generated chunks keep
 * their own column biomes (the zoomed biome at each column's top block), which {@code World.getBiome} reads first.
 *
 * <p>Thread safe: every thread gets its own biome source over the shared noise and climate.
 */
public final class ModernBiomeProvider extends BiomeProvider {

    private final OverworldNoise noise;
    private final Climate climate;
    private final BiomeMap map;
    private final ThreadLocal<Kit> kits = new ThreadLocal<Kit>() {
        @Override
        protected Kit initialValue() {
            return new Kit();
        }
    };

    private final class Kit {
        final ColumnCache columns = new ColumnCache(noise);
        final BiomeSource biomes = new BiomeSource(noise, columns, climate);

        /** The 1.18 biome at the quart holding block x, z, at the column's surface. */
        int modern(int x, int z) {
            int y = columns.preliminarySurfaceLevel(x, z);
            return biomes.biome(x >> 2, y >> 2, z >> 2);
        }
    }

    ModernBiomeProvider(OverworldNoise noise, Climate climate, BiomeMap map) {
        this.noise = noise;
        this.climate = climate;
        this.map = map;
    }

    /** The 1.18 biome id at block x, z (the quart at its surface). */
    public int modernBiome(int x, int z) {
        return kits.get().modern(x, z);
    }

    private Biome at(int x, int z) {
        return map.biome(kits.get().modern(x, z), x, z);
    }

    @Override
    public Biome getBiome(BlockPos pos) {
        return at(pos.getX(), pos.getZ());
    }

    @Override
    public Biome getBiome(BlockPos pos, Biome defaultBiome) {
        return at(pos.getX(), pos.getZ());
    }

    @Override
    public float getTemperatureAtHeight(float temperature, int height) {
        return temperature;
    }

    @Override
    public Biome[] getBiomesForGeneration(Biome[] biomes, int x, int z, int width, int height) {
        if (biomes == null || biomes.length < width * height) {
            biomes = new Biome[width * height];
        }
        Kit k = kits.get();
        for (int dz = 0; dz < height; dz++) {
            for (int dx = 0; dx < width; dx++) {
                int bx = (x + dx) << 2;
                int bz = (z + dz) << 2;
                biomes[dz * width + dx] = map.biome(k.modern(bx, bz), bx, bz);
            }
        }
        return biomes;
    }

    @Override
    public Biome[] getBiomes(@Nullable Biome[] oldBiomeList, int x, int z, int width, int depth) {
        return getBiomes(oldBiomeList, x, z, width, depth, true);
    }

    @Override
    public Biome[] getBiomes(@Nullable Biome[] list, int x, int z, int width, int length, boolean cacheFlag) {
        if (list == null || list.length < width * length) {
            list = new Biome[width * length];
        }
        Kit k = kits.get();
        for (int dz = 0; dz < length; dz++) {
            for (int dx = 0; dx < width; dx++) {
                list[dz * width + dx] = map.biome(k.modern(x + dx, z + dz), x + dx, z + dz);
            }
        }
        return list;
    }

    @Override
    public boolean areBiomesViable(int x, int z, int radius, List<Biome> allowed) {
        Kit k = kits.get();
        int x0 = x - radius >> 2;
        int z0 = z - radius >> 2;
        int x1 = x + radius >> 2;
        int z1 = z + radius >> 2;
        for (int qx = x0; qx <= x1; qx++) {
            for (int qz = z0; qz <= z1; qz++) {
                if (!allowed.contains(map.biome(k.modern(qx << 2, qz << 2), qx << 2, qz << 2))) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    @Nullable
    public BlockPos findBiomePosition(int x, int z, int range, List<Biome> biomes, Random random) {
        Kit k = kits.get();
        int x0 = x - range >> 2;
        int z0 = z - range >> 2;
        int x1 = x + range >> 2;
        int z1 = z + range >> 2;
        int w = x1 - x0 + 1;
        int n = w * (z1 - z0 + 1);
        BlockPos found = null;
        int count = 0;
        for (int i = 0; i < n; i++) {
            int qx = x0 + i % w;
            int qz = z0 + i / w;
            if (biomes.contains(map.biome(k.modern(qx << 2, qz << 2), qx << 2, qz << 2)) && (found == null || random.nextInt(count + 1) == 0)) {
                found = new BlockPos(qx << 2, 0, qz << 2);
                count++;
            }
        }
        return found;
    }

    @Override
    public void cleanupCache() {
    }

    @Override
    public boolean isFixedBiome() {
        return false;
    }

    @Override
    public Biome getFixedBiome() {
        return Biomes.PLAINS;
    }
}
