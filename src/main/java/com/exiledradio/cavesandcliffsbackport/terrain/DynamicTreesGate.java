package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.Loader;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * Dynamic Trees grows its own trees in a 1.12 world and cancels vanilla's: its {@code TreeGenCancelEventHandler} denies
 * 1.12's TREE and BIG_SHROOM decoration events (and CACTUS, unless {@code vanillaCactusWorldGen}) in every biome its
 * biome database says to. The 1.18 terrain places its trees itself, so this asks the same question of Dynamic Trees
 * directly, with that handler's logic, and the decorator leaves the 1.18 trees (or cacti) out of those chunks; Dynamic
 * Trees' own world generator then grows its trees there as in a Default world.
 *
 * <p>Asked directly, not through the decoration event: the 1.18 trees are placed while a chunk is generated, before it
 * is in the world, and other mods generate in that event (Biomes O' Plenty places its trees in it, which reads the
 * world, asks for the chunk being generated, and recurses until the stack overflows).
 */
final class DynamicTreesGate implements Decorator.TreeGate {

    private final World world;
    private final Object database;
    private final Method getEntry;
    private final Method cancels;
    private final boolean vanillaCactus;

    private DynamicTreesGate(World world, Object database, Method getEntry, Method cancels, boolean vanillaCactus) {
        this.world = world;
        this.database = database;
        this.getEntry = getEntry;
        this.cancels = cancels;
        this.vanillaCactus = vanillaCactus;
    }

    /** The gate for a world, or null when Dynamic Trees is not installed or leaves this dimension alone. */
    @Nullable
    static Decorator.TreeGate of(World world) {
        if (!Loader.isModLoaded("dynamictrees")) {
            return null;
        }
        try {
            Class<?> generator = Class.forName("com.ferreusveritas.dynamictrees.worldgen.TreeGenerator");
            Class<?> configs = Class.forName("com.ferreusveritas.dynamictrees.ModConfigs");
            Class<?> databaseClass = Class.forName("com.ferreusveritas.dynamictrees.worldgen.BiomeDataBase");
            int dim = world.provider.getDimension();
            Object instance = generator.getMethod("getTreeGenerator").invoke(null);
            Object database = generator.getMethod("getBiomeDataBase", int.class).invoke(instance, dim);
            Object blacklisted = generator.getField("DIMENSIONBLACKLISTED").get(null);
            Set<?> dims = (Set<?>) configs.getField("dimensionBlacklist").get(null);
            if (database == null || database == blacklisted || dims.contains(dim)) {
                return null;
            }
            Field cactus = configs.getField("vanillaCactusWorldGen");
            Method getEntry = databaseClass.getMethod("getEntry", Biome.class);
            Method cancels = getEntry.getReturnType().getMethod("shouldCancelVanillaTreeGen");
            CavesAndCliffsBackport.LOGGER.info("Dynamic Trees is installed: the 1.18 trees step aside in the biomes where it "
                    + "grows its own.");
            return new DynamicTreesGate(world, database, getEntry, cancels, cactus.getBoolean(null));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CavesAndCliffsBackport.LOGGER.warn("Could not ask Dynamic Trees which biomes it grows trees in; the 1.18 trees "
                    + "are placed everywhere, beside its own.", e);
            return null;
        }
    }

    @Override
    public boolean allows(int cx, int cz, int kind) {
        if (kind != TREES && kind != CACTUS || kind == CACTUS && this.vanillaCactus) {
            return true;
        }
        try {
            // The handler reads the biome at the event's position, the chunk's corner. The chunk being generated is not
            // in the world yet, so this is the biome provider's answer, which is the chunk's own.
            Biome biome = this.world.getBiome(new BlockPos(cx << 4, 0, cz << 4));
            Object entry = this.getEntry.invoke(this.database, biome);
            return entry == null || !(Boolean) this.cancels.invoke(entry);
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }
}
