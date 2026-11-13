package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.feature.WorldGenMinable;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent;
import net.minecraftforge.event.terraingen.OreGenEvent;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.event.terraingen.TerrainGen;
import net.minecraftforge.fml.common.Loader;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Other mods' world generation that hangs on 1.12's decoration, in a world with 1.18 terrain.
 *
 * <p>The 1.18 features replace 1.12's biome decorator and population features, so their Forge events never fired
 * and what other mods do in them never ran: Quark's stone revamp (marble, limestone, jasper, slate and its granite,
 * diorite and andesite clusters) and underground biomes run in the decorator's DIRT ore event, its varied dungeons in
 * the DUNGEON population event. {@link #populate} fires the events of 1.12's population that the 1.18 terrain has a
 * counterpart for, in 1.12's order, once per chunk, from the generator's populate (the chunk and its neighbors are in
 * the world, as in a Default world):
 * <ul>
 *   <li>{@code PopulateChunkEvent.Populate} DUNGEON (1.18 places its own monster rooms; the 1.12 dungeon is not
 *       placed whatever the answer);</li>
 *   <li>{@code DecorateBiomeEvent.Pre}, {@code OreGenEvent.Pre}, {@code OreGenEvent.GenerateMinable} DIRT (1.18
 *       places its own dirt, so the answer is not used), {@code OreGenEvent.Post}, {@code DecorateBiomeEvent.Post},
 *       at the chunk's corner as BiomeDecorator gives them, with the biome's decorator at that chunk.</li>
 * </ul>
 * Not fired: the other ore types (a mod that denies one to place its own would place it beside 1.18's ores) and
 * {@code DecorateBiomeEvent.Decorate} (Biomes O' Plenty grows its trees in TREE, beside the 1.18 trees; see
 * {@link DynamicTreesGate}). The events get a random of their own, seeded from the chunk, so the animals that follow
 * are the same with or without the mods that draw from it.
 *
 * <p>Quark's stone revamp denies 1.12's granite, diorite and andesite in a Default world and places its own clusters
 * instead; {@link #quarkStones} asks it the same question, and the 1.18 granite, diorite and andesite blobs step
 * aside where it says, so they do not generate twice.
 */
final class ModDecoration {

    private static final WorldGenMinable DIRT = new WorldGenMinable(Blocks.DIRT.getDefaultState(), 33);

    private ModDecoration() {
    }

    /** The events of 1.12's population for chunk x, z (see the class comment). */
    static void populate(IChunkGenerator generator, World world, int x, int z, boolean village,
                         @Nullable ModernFeatures modern) {
        Random rand = new Random(world.getSeed() ^ ((long) x * 341873128712L + (long) z * 132897987541L) ^ 0x5DEECE66DL);
        TerrainGen.populate(generator, world, rand, x, z, village, PopulateChunkEvent.Populate.EventType.DUNGEON);
        BlockPos pos = new BlockPos(x << 4, 0, z << 4);
        ChunkPos chunk = new ChunkPos(x, z);
        Biome biome = world.getBiome(pos.add(16, 0, 16));
        net.minecraft.world.biome.BiomeDecorator decorator = biome.decorator;
        BlockPos before = decorator == null ? null : decorator.chunkPos;
        // BiomeDecorator.decorate sets it before its events; some handlers read it.
        if (decorator != null) {
            decorator.chunkPos = pos;
        }
        try {
            MinecraftForge.EVENT_BUS.post(new DecorateBiomeEvent.Pre(world, rand, chunk));
            MinecraftForge.ORE_GEN_BUS.post(new OreGenEvent.Pre(world, rand, pos));
            TerrainGen.generateOre(world, rand, DIRT, pos, OreGenEvent.GenerateMinable.EventType.DIRT);
            MinecraftForge.ORE_GEN_BUS.post(new OreGenEvent.Post(world, rand, pos));
            DecorateBiomeEvent.Post post = new DecorateBiomeEvent.Post(world, rand, chunk);
            if (modern != null) {
                // Tricky Trials' 1.12.2 plants step aside: its 1.21.5 ones are placed in the terrain's own steps.
                modern.postDecorated(post);
            } else {
                MinecraftForge.EVENT_BUS.post(post);
            }
        } finally {
            if (decorator != null) {
                decorator.chunkPos = before;
            }
        }
    }

    /** Both gates, either may leave a kind out (null for none). */
    @Nullable
    static Decorator.TreeGate gate(@Nullable final Decorator.TreeGate a, @Nullable final Decorator.TreeGate b) {
        if (a == null || b == null) {
            return a == null ? b : a;
        }
        return (cx, cz, kind) -> a.allows(cx, cz, kind) && b.allows(cx, cz, kind);
    }

    /**
     * Quark's stone revamp: RevampStoneGen denies 1.12's GRANITE, DIORITE and ANDESITE ore events for each stone it
     * generates itself ({@code StoneInfo.enabled}), in every dimension; null when Quark or the feature is absent.
     */
    @Nullable
    static Decorator.TreeGate quarkStones() {
        if (!Loader.isModLoaded("quark")) {
            return null;
        }
        try {
            Class<?> loader = Class.forName("vazkii.quark.base.module.ModuleLoader");
            Class<?> revamp = Class.forName("vazkii.quark.world.feature.RevampStoneGen");
            Method enabled = loader.getMethod("isFeatureEnabled", Class.class);
            if (!(Boolean) enabled.invoke(null, revamp)) {
                return null;
            }
            final boolean granite = stone(revamp, "graniteInfo");
            final boolean diorite = stone(revamp, "dioriteInfo");
            final boolean andesite = stone(revamp, "andesiteInfo");
            if (!granite && !diorite && !andesite) {
                return null;
            }
            CavesAndCliffsBackport.LOGGER.info("Quark's stone revamp places its own{}{}{}: the 1.18 blobs step aside.",
                    granite ? " granite" : "", diorite ? " diorite" : "", andesite ? " andesite" : "");
            return (cx, cz, kind) -> !(kind == Decorator.TreeGate.GRANITE && granite
                    || kind == Decorator.TreeGate.DIORITE && diorite || kind == Decorator.TreeGate.ANDESITE && andesite);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CavesAndCliffsBackport.LOGGER.warn("Could not ask Quark which stones it generates; the 1.18 granite, diorite "
                    + "and andesite are placed beside its own.", e);
            return null;
        }
    }

    private static boolean stone(Class<?> revamp, String field) throws ReflectiveOperationException {
        Object info = revamp.getField(field).get(null);
        return info != null && info.getClass().getField("enabled").getBoolean(info);
    }
}
