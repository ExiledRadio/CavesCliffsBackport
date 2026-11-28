package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A biome region's own climate parameter list and the search over it: 1.18.2's Climate.RTree (10 children per node,
 * built and searched as {@link Climate} builds and searches the overworld's), over leaves whose values are ints: a
 * 1.18 biome id (0 and up) or another mod's biome (-1 - its index). TerraBlender gives each region such a tree.
 *
 * <p>Immutable once built; any thread.
 */
public final class RegionTree {

    private final Climate.Node root;

    private RegionTree(Climate.Node root) {
        this.root = root;
    }

    /** The value of the closest leaf to a target (7 quantized values, offset 0), as a cold-start search finds it. */
    public int search(long[] target) {
        return Climate.search(root, target, null, false).biome; // 1.18.2's distance: these are 1.18.2 tables
    }

    /** Leaves added one by one, then built into a tree. */
    public static final class Builder {
        private final List<Climate.Node> leaves = new ArrayList<>();

        /** A leaf: quantized bounds in Mojang's parameter order (offset last). */
        public void add(long[] min, long[] max, int value) {
            Climate.Node n = new Climate.Node();
            System.arraycopy(min, 0, n.min, 0, 7);
            System.arraycopy(max, 0, n.max, 0, 7);
            n.biome = value;
            leaves.add(n);
        }

        public int size() {
            return leaves.size();
        }

        public RegionTree build() {
            return new RegionTree(Climate.build(new ArrayList<>(leaves), 10));
        }
    }
}
