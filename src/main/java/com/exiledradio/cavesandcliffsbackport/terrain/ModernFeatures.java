package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Marks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.eventhandler.IEventListener;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * Other backports' placed features in the 1.18 terrain: Tricky Trials' 1.21.5 spring plants (its stream 15,
 * {@code docs/CORE_REQUESTS.md} #11 there), so that with Tricky Trials installed the newest version's plants grow
 * here too. Without it nothing here runs and decoration is exactly 1.18.2's.
 *
 * <p>Tricky Trials' features take a {@link World}, and the terrain core decorates held chunks before they are in the
 * world, so the core leaves requests ({@link Marks} kinds from {@link Marks#REQUEST}) where they belong and this
 * runs them once the chunk and its eight neighbors are in the world ({@link FluidUpdates}, which keeps them with the
 * chunk across saves):
 * <ul>
 *   <li>The tree selectors get 1.21.5's entries at Tricky Trials' chances ({@code FallenTrees.SELECTORS}; the
 *       forest's {@code trees_birch_and_oak} is 1.21.5's {@code trees_birch_and_oak_leaf_litter}). A fallen tree the
 *       selector picks is a {@link Marks#FALLEN} request ({@code FallenTrees.place}); a forest or dark forest tree,
 *       once it stands, a {@link Marks#LITTER} request for its leaf litter ({@code FallenTrees.leafLitter}).</li>
 *   <li>The cactus patches are left out of the core's decoration (its tree gate's CACTUS) and placed here as 1.21.5's,
 *       flowers included ({@code FloraFeatures.place(..., "patch_cactus_desert" / "patch_cactus_decorated")}).</li>
 *   <li>The rest of the vegetal step's new patches (bush, firefly bush, dry grass, wildflowers, leaf litter), after the
 *       chunk's trees: one {@link Marks#VEGETATION} request per chunk. Vanilla decorates the union of the biomes'
 *       features, each filtered by the biome at its position, so every patch of every 1.18 biome the chunk's surface
 *       has is placed once, in 1.21.5's order (Tricky Trials' per-biome lists merged); for a chunk of one biome that
 *       is {@code FloraFeatures.vegetation} itself.</li>
 * </ul>
 * Positions are drawn as 1.21.5 draws them from the chunk corner, with a random of the world seed and the chunk.
 * Tricky Trials' own 1.12.2 path ({@code FloraGen}, on {@code DecorateBiomeEvent.Post}, which {@link ModDecoration}
 * fires for other mods) is skipped in these chunks, so nothing grows twice.
 */
final class ModernFeatures {

    private static final String FLORA = "com.exiledradio.trickytrialsbackport.flora.";

    private final Method place;
    private final Method fallenPlace;
    private final Method leafLitter;
    private final Object[] fallen;
    private final List<String> order;
    private final Map<String, List<String>> features;
    /** Tricky Trials' FloraGen listeners on the event bus, left out of the decoration event here. */
    private final Set<IEventListener> skipped;
    private final int busID;
    /** Dynamic Trees' gate, asked again before the cactus patches (null: none). */
    @Nullable
    private final Decorator.TreeGate gate;
    final Map<String, Decorator.ModernTrees> selectors;

    private ModernFeatures(Method place, Method fallenPlace, Method leafLitter, Object[] fallen, List<String> order,
                           Map<String, List<String>> features, Set<IEventListener> skipped, int busID,
                           Map<String, Decorator.ModernTrees> selectors, @Nullable Decorator.TreeGate gate) {
        this.place = place;
        this.fallenPlace = fallenPlace;
        this.leafLitter = leafLitter;
        this.fallen = fallen;
        this.order = order;
        this.features = features;
        this.skipped = skipped;
        this.busID = busID;
        this.selectors = selectors;
        this.gate = gate;
    }

    /** Tricky Trials' spring plants, or null when it is not installed (or its plants are turned off). */
    @Nullable
    static ModernFeatures of(@Nullable Decorator.TreeGate gate) {
        if (!Loader.isModLoaded("trickytrialsbackport")) {
            return null;
        }
        try {
            Class<?> content = Class.forName(FLORA + "FloraContent");
            if (!(Boolean) content.getMethod("enabled").invoke(null) || content.getField("bush").get(null) == null) {
                return null;
            }
            Class<?> flora = Class.forName(FLORA + "FloraFeatures");
            Class<?> trees = Class.forName(FLORA + "FallenTrees");
            Class<?> biomes = Class.forName(FLORA + "FloraBiomes");
            Class<?> kind = Class.forName(FLORA + "FallenTrees$Fallen");
            Method place = flora.getMethod("place", World.class, Random.class, BlockPos.class, String.class);
            Method fallenPlace = trees.getMethod("place", World.class, Random.class, BlockPos.class, kind);
            Method leafLitter = trees.getMethod("leafLitter", World.class, Random.class, Collection.class);
            Object[] fallen = kind.getEnumConstants();

            Field featuresField = biomes.getDeclaredField("FEATURES");
            featuresField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, List<String>> features = new HashMap<>((Map<String, List<String>>) featuresField.get(null));

            Method dropsLitter = trees.getDeclaredMethod("dropsLitter", String.class);
            dropsLitter.setAccessible(true);
            Field selectorsField = trees.getDeclaredField("SELECTORS");
            selectorsField.setAccessible(true);
            Map<String, Decorator.ModernTrees> selectors = new HashMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) selectorsField.get(null)).entrySet()) {
                String name = (String) e.getKey();
                Object sel = e.getValue();
                Field chancesField = sel.getClass().getDeclaredField("chances");
                Field kindsField = sel.getClass().getDeclaredField("kinds");
                chancesField.setAccessible(true);
                kindsField.setAccessible(true);
                float[] chances = (float[]) chancesField.get(sel);
                Object[] kinds = (Object[]) kindsField.get(sel);
                int[] index = new int[kinds.length];
                for (int i = 0; i < kinds.length; i++) {
                    index[i] = kinds[i] == null ? Decorator.ModernTrees.TERRAIN : ((Enum<?>) kinds[i]).ordinal();
                }
                // 1.21.5 gave the forest a selector of its own; 1.18.2's forest has trees_birch_and_oak.
                String own = "trees_birch_and_oak_leaf_litter".equals(name) ? "trees_birch_and_oak" : name;
                selectors.put(own, new Decorator.ModernTrees(chances, index, (Boolean) dropsLitter.invoke(null, name)));
            }

            // FloraGen's listeners, to leave out of the decoration event in these chunks.
            Field listenersField = EventBus.class.getDeclaredField("listeners");
            listenersField.setAccessible(true);
            Field busField = EventBus.class.getDeclaredField("busID");
            busField.setAccessible(true);
            Map<?, ?> listeners = (Map<?, ?>) listenersField.get(MinecraftForge.EVENT_BUS);
            Set<IEventListener> skipped = Collections.newSetFromMap(new IdentityHashMap<IEventListener, Boolean>());
            Object own = listeners.get(Class.forName(FLORA + "FloraGen"));
            if (own instanceof Collection) {
                for (Object l : (Collection<?>) own) {
                    skipped.add((IEventListener) l);
                }
            }
            return new ModernFeatures(place, fallenPlace, leafLitter, fallen, order(features), features, skipped,
                    busField.getInt(MinecraftForge.EVENT_BUS), selectors, gate);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CavesAndCliffsBackport.LOGGER.warn("Could not reach Tricky Trials' spring plants; the 1.18 terrain grows "
                    + "1.18.2's vegetation only.", e);
            return null;
        }
    }

    /**
     * One order for every biome's patches that keeps each biome's own order (they are all cut from 1.21.5's one
     * vegetal step order): each list's neighbors are edges, ties go by name.
     */
    private static List<String> order(Map<String, List<String>> features) {
        Map<String, Set<String>> after = new HashMap<>();
        Map<String, Integer> before = new HashMap<>();
        for (List<String> list : features.values()) {
            String last = null;
            for (String f : list) {
                before.putIfAbsent(f, 0);
                after.putIfAbsent(f, new HashSet<String>());
                if (last != null && after.get(last).add(f)) {
                    before.put(f, before.get(f) + 1);
                }
                last = f;
            }
        }
        TreeSet<String> ready = new TreeSet<>();
        for (Map.Entry<String, Integer> e : before.entrySet()) {
            if (e.getValue() == 0) {
                ready.add(e.getKey());
            }
        }
        List<String> out = new ArrayList<>();
        while (!ready.isEmpty()) {
            String f = ready.pollFirst();
            out.add(f);
            for (String g : after.get(f)) {
                int n = before.get(g) - 1;
                before.put(g, n);
                if (n == 0) {
                    ready.add(g);
                }
            }
        }
        // A cycle (lists that disagree) leaves names out; they go last.
        for (String f : new TreeSet<>(before.keySet())) {
            if (!out.contains(f)) {
                out.add(f);
            }
        }
        return out;
    }

    /** Leaves the cactus patches to Tricky Trials ({@link Decorator.TreeGate#CACTUS}). */
    Decorator.TreeGate cactusGate() {
        return (cx, cz, kind) -> kind != Decorator.TreeGate.CACTUS;
    }

    /** The chunk's vegetation request, added to the updates the chunk waits with. */
    static int[] withVegetation(int[] updates) {
        int[] out = Arrays.copyOf(updates, updates.length + 1);
        out[updates.length] = Marks.pack(0, com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise.MIN_Y, 0,
                Marks.VEGETATION);
        return out;
    }

    /** Posts the event to every listener but Tricky Trials' own 1.12.2 decoration (EventBus.post, without it). */
    void postDecorated(Event event) {
        if (skipped.isEmpty()) {
            MinecraftForge.EVENT_BUS.post(event);
            return;
        }
        for (IEventListener listener : event.getListenerList().getListeners(busID)) {
            if (!skipped.contains(listener)) {
                listener.invoke(event);
            }
        }
    }

    /**
     * Runs a chunk's requests, in the order the core made them (its trees' fallen trees and leaf litter), then its
     * patches; the chunk and its eight neighbors are in the world.
     */
    void run(World world, Chunk chunk, int[] requests) {
        Random rand = new Random(world.getSeed() ^ ((long) chunk.x * 341873128712L + (long) chunk.z * 132897987541L) ^ 0x2A1F5C3BL);
        int bx = chunk.x << 4;
        int bz = chunk.z << 4;
        try {
            for (int e : requests) {
                int kind = Marks.kind(e);
                BlockPos pos = new BlockPos(bx + Marks.x(e), Marks.y(e), bz + Marks.z(e));
                if (kind >= Marks.FALLEN && kind < Marks.FALLEN + fallen.length) {
                    fallenPlace.invoke(null, world, rand, pos, fallen[kind - Marks.FALLEN]);
                } else if (kind == Marks.LITTER) {
                    leafLitter.invoke(null, world, rand, Collections.singletonList(pos));
                } else if (kind == Marks.LITTER_2X2) {
                    leafLitter.invoke(null, world, rand, Arrays.asList(pos, pos.east(), pos.south(), pos.south().east()));
                } else if (kind == Marks.VEGETATION) {
                    vegetation(world, rand, chunk);
                }
            }
        } catch (ReflectiveOperationException ex) {
            CavesAndCliffsBackport.LOGGER.warn("Tricky Trials' plants failed in chunk {}, {}.", chunk.x, chunk.z, ex);
        }
    }

    /** Every patch of the biomes on the chunk's surface, once, in 1.21.5's order (see the class comment). */
    private void vegetation(World world, Random rand, Chunk chunk) throws ReflectiveOperationException {
        Set<String> names = new LinkedHashSet<>();
        byte[] ids = com.exiledradio.cavesandcliffsbackport.height.IdsCompat.biomeBytes(chunk);
        Set<String> here = new HashSet<>();
        for (byte id : ids) {
            net.minecraft.world.biome.Biome b = net.minecraft.world.biome.Biome.getBiome(id & 255);
            here.add(modernName(world, b));
        }
        for (String biome : here) {
            List<String> list = biome == null ? null : features.get(biome);
            if (list != null) {
                names.addAll(list);
            }
        }
        if (names.isEmpty()) {
            return;
        }
        BlockPos corner = new BlockPos(chunk.x << 4, 0, chunk.z << 4);
        boolean cactus = gate == null || gate.allows(chunk.x, chunk.z, Decorator.TreeGate.CACTUS);
        for (String f : order) {
            if (!names.contains(f) || f.startsWith("[") || f.startsWith("patch_cactus") && !cactus) {
                continue;
            }
            place.invoke(null, world, rand, corner, f);
        }
    }

    private Method modernName;

    /** Tricky Trials' 1.21.5 name for a biome of the world. */
    @Nullable
    private String modernName(World world, @Nullable net.minecraft.world.biome.Biome biome) throws ReflectiveOperationException {
        if (modernName == null) {
            modernName = Class.forName(FLORA + "FloraBiomes").getMethod("modernName", net.minecraft.world.biome.Biome.class);
        }
        return (String) modernName.invoke(null, biome);
    }
}
