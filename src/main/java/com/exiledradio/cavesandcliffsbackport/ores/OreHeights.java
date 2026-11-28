package com.exiledradio.cavesandcliffsbackport.ores;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.Owners;
import com.exiledradio.cavesandcliffsbackport.height.Heights;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernGenerator;
import com.google.common.base.Predicate;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Stream O2: other mods' ores in the lower half of a Caves &amp; Cliffs world, as 1.18 moved vanilla's.
 *
 * <p>{@code Chunk.populate} calls {@link #generateWorld} instead of Forge's {@code GameRegistry.generateWorld} (the
 * height coremod's Chunk patch). In a Caves &amp; Cliffs overworld it runs every mod's {@code IWorldGenerator} as Forge
 * does, same order, same random, and while one runs:
 * <ul>
 *   <li>an ore ({@link ModdedOres}) the generator sets at y 0 to 63 is set at {@code 2y - 64} instead (the World patch's
 *       {@code setBlockState} asks {@link #setBlockPos}); 64 and above stay;</li>
 *   <li>its stone check ({@code Block.isReplaceableOreGen}, which {@code WorldGenMinable} and most mods' vein code ask;
 *       patched to call {@link #isReplaceableOreGen}) is answered at that target height too, where deepslate and tuff
 *       count as stone. An ore whose target fails the check is not placed; a non-ore placement still needs the check
 *       to pass where it is, at every position the run checked (not only the last), and is never moved;</li>
 *   <li>only positions in the four chunks population may write (the chunk and its +x, +z neighbors, vanilla's +8
 *       offset) move; anything a generator places beyond them is left as the mod placed it.</li>
 * </ul>
 * Then, so each block of rock keeps the mod's own density in a band twice as tall, every generator that has placed a
 * moved ore runs a second time for the chunk with a random of its own, and from that run only the moved ores are kept:
 * everything else it would set, and every entity it would spawn, is dropped. A generator that throws in that second
 * run is left out of it from then on (logged once); the first run is Forge's own and throws as it would.
 *
 * <p>Not moved: Nether and End ores, every non-ore block, generators of this mod family ({@code com.exiledradio}),
 * other dimensions, Default and other worlds (Forge's own method runs), and worlds in Cubic Chunks (no coremod).
 */
public final class OreHeights {

    /** The ore band's top (exclusive): y 0 to 63 stretches to -64 to 63. */
    public static final int SEAM = 64;
    /** Added to the chunk seed of the second run. */
    private static final long SECOND_RUN_SALT = 0x5DEECE66DL * 0x9E3779B97F4A7C15L;
    /** {@code World.setBlockState} returns false at once for a position outside the world. */
    private static final BlockPos DROP = new BlockPos(0, -1 << 20, 0);

    /** The world whose generators run under the remap, else null (read by every patched setBlockState). */
    @Nullable
    private static World active;
    /** 1 for the generators' own run, 2 for the second run. */
    private static int pass;
    private static int minX, minZ;
    @Nullable
    private static IWorldGenerator running;
    private static boolean moved;
    /** The last stone check's position (as a long) and what it found where it was and at the target. */
    private static long checkedPos;
    private static boolean checked, checkedHere, checkedThere;
    /**
     * Every position of this generator run whose stone check passed only for its target, not for its own block. A
     * generator may check a list of positions and set them later (Bedrock Ores: the bedrock at y 0-2, whose targets
     * at -64 to -60 are the real bedrock), so the last check alone can't tell; anything but a moved ore set at one of
     * these is dropped.
     */
    private static LongOpenHashSet onlyThere = new LongOpenHashSet();
    /** The last {@link #setBlockPos} moved an ore: {@link #placedState} picks the block. */
    private static boolean placing;

    /** Generators that have placed an ore the remap moved: they run again for the doubling. */
    private static final Set<IWorldGenerator> ORE_GENERATORS = Collections.newSetFromMap(new IdentityHashMap<IWorldGenerator, Boolean>());
    /** Generators that threw in a second run: never run twice again. */
    private static final Set<IWorldGenerator> BROKEN = Collections.newSetFromMap(new IdentityHashMap<IWorldGenerator, Boolean>());
    private static final Map<IWorldGenerator, Boolean> FAMILY = new IdentityHashMap<IWorldGenerator, Boolean>();

    @Nullable
    private static MethodHandle sortedList;
    @Nullable
    private static Method computeSorted;
    private static boolean reflectionFailed, registered;
    @Nullable
    private static Block deepslate, tuff;
    private static boolean stonesLooked;

    /** For the self-test and the census: ores moved and placed by the second run since start. */
    public static long movedOres, secondRunOres, droppedOres;

    private OreHeights() {
    }

    // ---------------------------------------------------------------------------------------------
    // Running the generators

    /** In place of {@code GameRegistry.generateWorld}, from the patched {@code Chunk.populate}. */
    public static void generateWorld(int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        List<IWorldGenerator> generators = applies(world, chunkGenerator) ? generators() : null;
        if (generators == null) {
            GameRegistry.generateWorld(chunkX, chunkZ, world, chunkGenerator, chunkProvider);
            return;
        }
        if (!registered) {
            registered = true;
            MinecraftForge.EVENT_BUS.register(new Entities());
        }
        // Forge 14.23.5.2847's GameRegistry.generateWorld, line for line.
        long worldSeed = world.getSeed();
        Random fmlRandom = new Random(worldSeed);
        long xSeed = fmlRandom.nextLong() >> 2 + 1L;
        long zSeed = fmlRandom.nextLong() >> 2 + 1L;
        long chunkSeed = (xSeed * chunkX + zSeed * chunkZ) ^ worldSeed;
        World before = active;
        int passBefore = pass;
        IWorldGenerator runningBefore = running;
        int minXBefore = minX, minZBefore = minZ;
        boolean movedBefore = moved;
        // A generator may populate another chunk (a nested run): that run gets a set of its own.
        LongOpenHashSet onlyThereBefore = onlyThere;
        onlyThere = new LongOpenHashSet();
        try {
            for (IWorldGenerator generator : generators) {
                fmlRandom.setSeed(chunkSeed);
                if (family(generator)) {
                    active = null;
                    generator.generate(fmlRandom, chunkX, chunkZ, world, chunkGenerator, chunkProvider);
                    continue;
                }
                begin(world, 1, generator, chunkX, chunkZ);
                generator.generate(fmlRandom, chunkX, chunkZ, world, chunkGenerator, chunkProvider);
                if (moved) {
                    ORE_GENERATORS.add(generator);
                }
            }
            // The doubling: a second run of every ore generator, only its moved ores kept.
            for (IWorldGenerator generator : generators) {
                if (!ORE_GENERATORS.contains(generator) || BROKEN.contains(generator)) {
                    continue;
                }
                fmlRandom.setSeed(chunkSeed + SECOND_RUN_SALT);
                begin(world, 2, generator, chunkX, chunkZ);
                try {
                    generator.generate(fmlRandom, chunkX, chunkZ, world, chunkGenerator, chunkProvider);
                } catch (RuntimeException | LinkageError e) {
                    BROKEN.add(generator);
                    CavesAndCliffsBackport.LOGGER.warn("Modded ore heights: {} threw when run a second time for chunk {}, {}; "
                            + "its ores are no longer doubled.", generator.getClass().getName(), chunkX, chunkZ, e);
                }
            }
        } finally {
            active = before;
            pass = passBefore;
            running = runningBefore;
            minX = minXBefore;
            minZ = minZBefore;
            moved = movedBefore;
            onlyThere = onlyThereBefore;
            checked = false;
            placing = false;
        }
    }

    private static void begin(World world, int which, IWorldGenerator generator, int chunkX, int chunkZ) {
        active = world;
        pass = which;
        running = generator;
        moved = false;
        checked = false;
        onlyThere.clear();
        minX = chunkX << 4;
        minZ = chunkZ << 4;
    }

    /** A Caves &amp; Cliffs overworld with 1.18 terrain, extended to -64, with the setting on. */
    public static boolean applies(World world, @Nullable IChunkGenerator generator) {
        return ModConfig.height.moddedOreHeights && generator instanceof ModernGenerator && !world.isRemote
                && world.provider.getDimension() == 0 && Heights.minY(world) <= -SEAM;
    }

    private static boolean family(IWorldGenerator generator) {
        Boolean f = FAMILY.get(generator);
        if (f == null) {
            // The self-test's stand-in mods are "other mods".
            String name = generator.getClass().getName();
            f = name.startsWith("com.exiledradio.") && !name.contains(".selftest.");
            FAMILY.put(generator, f);
        }
        return f;
    }

    /** Forge's sorted generator list, or null when it cannot be read (then Forge's own method runs). */
    @Nullable
    @SuppressWarnings("unchecked")
    private static List<IWorldGenerator> generators() {
        if (reflectionFailed) {
            return null;
        }
        try {
            if (sortedList == null) {
                Field f = GameRegistry.class.getDeclaredField("sortedGeneratorList");
                f.setAccessible(true);
                sortedList = MethodHandles.lookup().unreflectGetter(f);
                computeSorted = GameRegistry.class.getDeclaredMethod("computeSortedGeneratorList");
                computeSorted.setAccessible(true);
            }
            List<IWorldGenerator> list = (List<IWorldGenerator>) sortedList.invoke();
            if (list == null) {
                computeSorted.invoke(null);
                list = (List<IWorldGenerator>) sortedList.invoke();
            }
            return list;
        } catch (Throwable t) {
            reflectionFailed = true;
            CavesAndCliffsBackport.LOGGER.error("Modded ore heights are off: Forge's world generator list could not be read.", t);
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The patched sites

    /**
     * {@code World.setBlockState(pos, state, flags)}, first thing: the position to set instead. Outside a remapped run
     * this returns {@code pos} at once. {@link #DROP} (far below the world) makes vanilla's own range check return
     * false.
     */
    public static BlockPos setBlockPos(World world, BlockPos pos, IBlockState state) {
        if (active != world) {
            return pos;
        }
        boolean memo = checked && checkedPos == pos.toLong();
        checked = false;
        placing = false;
        int y = pos.getY();
        boolean inside = y >= 0 && y < SEAM && inside(pos);
        if (inside && ModdedOres.isOre(state)) {
            boolean there = memo ? checkedThere : stoneAt(world, target(pos), null);
            moved = true;
            if (!there) {
                droppedOres++;
                return DROP;
            }
            if (pass == 2) {
                secondRunOres++;
            } else {
                movedOres++;
            }
            placing = true;
            return target(pos);
        }
        if (pass == 2) {
            return DROP;
        }
        // A check said yes only for the target: a non-ore placement needs it to pass where it is. Never moved either.
        return inside && (memo && !checkedHere || onlyThere.contains(pos.toLong())) ? DROP : pos;
    }

    /**
     * {@code World.setBlockState}, right after {@link #setBlockPos}: the state to set, at the position it returned.
     */
    public static IBlockState placedState(World world, BlockPos at, IBlockState state) {
        if (!placing || active != world) {
            return state;
        }
        placing = false;
        World was = active;
        active = null;
        try {
            return placed(state, world.getBlockState(at));
        } finally {
            active = was;
        }
    }

    /**
     * The one place that chooses the block of an ore set at its remapped height, over the block it replaces: its
     * deepslate form (stream O1, {@link com.exiledradio.cavesandcliffsbackport.deepslate.ModdedOres#deepslateForm})
     * where it replaces deepslate or tuff, as 1.18's ores do; otherwise, or when it has none, the ore itself.
     */
    public static IBlockState placed(IBlockState ore, IBlockState replaced) {
        if (!stonesLooked) {
            stonesLooked = true;
            deepslate = Owners.ourBlock("deepslate");
            tuff = Owners.ourBlock("tuff");
        }
        Block block = replaced.getBlock();
        if (block == null || block != deepslate && block != tuff) {
            return ore;
        }
        IBlockState form = com.exiledradio.cavesandcliffsbackport.deepslate.ModdedOres.deepslateForm(ore);
        return form != null ? form : ore;
    }

    /** {@code Block.isReplaceableOreGen}'s body ({@code target.apply(state)}), answered for the remap too. */
    public static boolean isReplaceableOreGen(IBlockState state, IBlockAccess access, BlockPos pos, Predicate<IBlockState> target) {
        boolean here = target.apply(state);
        if (active == null || access != active) {
            return here;
        }
        int y = pos.getY();
        if (y < 0 || y >= SEAM || !inside(pos)) {
            return here;
        }
        boolean there = stoneAt(active, target(pos), target);
        checked = true;
        checkedPos = pos.toLong();
        checkedHere = here;
        checkedThere = there;
        if (there && !here) {
            onlyThere.add(checkedPos);
        } else if (here) {
            onlyThere.remove(checkedPos);
        }
        return pass == 2 ? there : here || there;
    }

    /** Whether an ore may replace the block at {@code at}: the generator's own test, or a deep stone. */
    private static boolean stoneAt(World world, BlockPos at, @Nullable Predicate<IBlockState> test) {
        World was = active;
        active = null;
        try {
            IBlockState state = world.getBlockState(at);
            Block block = state.getBlock();
            if (test != null ? state.getBlock().isReplaceableOreGen(state, world, at, test) : block == Blocks.STONE) {
                return true;
            }
            if (!stonesLooked) {
                stonesLooked = true;
                deepslate = Owners.ourBlock("deepslate");
                tuff = Owners.ourBlock("tuff");
            }
            if (block != deepslate && block != tuff || block == null) {
                return false;
            }
            // A deep stone counts as stone where the generator's test takes stone.
            return test == null || test.apply(Blocks.STONE.getDefaultState());
        } finally {
            active = was;
        }
    }

    private static boolean inside(BlockPos pos) {
        int dx = pos.getX() - minX;
        int dz = pos.getZ() - minZ;
        return dx >= 0 && dx < 32 && dz >= 0 && dz < 32;
    }

    /** y 0 to 63 to y -64 to 62. */
    public static int targetY(int y) {
        return 2 * y - SEAM;
    }

    private static BlockPos target(BlockPos pos) {
        return new BlockPos(pos.getX(), targetY(pos.getY()), pos.getZ());
    }

    /** Whether a remapped run is in progress (for tests). */
    public static boolean running() {
        return active != null;
    }

    /** Entities a generator spawns in the second run are not added. */
    public static final class Entities {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void join(EntityJoinWorldEvent event) {
            if (pass == 2 && active != null && event.getWorld() == active) {
                event.setCanceled(true);
            }
        }
    }
}
