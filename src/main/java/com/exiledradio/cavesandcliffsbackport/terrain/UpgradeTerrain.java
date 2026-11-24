package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Blending;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Upgrade;
import com.exiledradio.cavesandcliffsbackport.upgrade.OldChunks;
import com.exiledradio.cavesandcliffsbackport.upgrade.WorldUpgrade;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An upgraded world's chunks for the 1.18 pipeline ({@link Pipeline.Upgrades}): which are old and still to upgrade,
 * the blending data of the old ones near each chunk, and the old blocks the 1.18 features read from y 0 up.
 * Server thread only.
 */
final class UpgradeTerrain implements Pipeline.Upgrades {

    final WorldUpgrade upgrade;
    final OldChunks chunks;
    private final StateMap states;
    /** BlendingData per old chunk, calculated once (its sides never change: which chunks are old is fixed). */
    private final LinkedHashMap<Long, Blending.Data> data = new LinkedHashMap<Long, Blending.Data>(1024, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Blending.Data> eldest) {
            return size() > 8192;
        }
    };
    /** Old chunks with no data (not on disk): kept apart so they are not read again. */
    private static final Blending.Data NONE = null;
    /** Block code per state id (OldChunks.stateId), 0xFFFF until asked. */
    private final char[] codes = new char[65536];
    @Nullable
    private Map<IBlockState, Integer> byState;

    UpgradeTerrain(WorldServer world, WorldUpgrade upgrade, StateMap states) {
        this.upgrade = upgrade;
        this.chunks = new OldChunks(world);
        this.states = states;
        Arrays.fill(codes, (char) 0xFFFF);
    }

    private static long key(int cx, int cz) {
        return (long) cx << 32 | (cz & 0xFFFFFFFFL);
    }

    /**
     * Whether the pipeline must fill and decorate the chunk although the world holds it: an old chunk that is still
     * to get its part below y 0 and gets one (it has bedrock at y 0). An old chunk that never will is marked done.
     */
    boolean upgrades(int cx, int cz) {
        if (!upgrade.pending(cx, cz)) {
            return false;
        }
        OldChunks.OldChunk c = chunks.get(cx, cz);
        if (c == null || !c.eligible()) {
            upgrade.markDone(cx, cz);
            return false;
        }
        return true;
    }

    @Nullable
    @Override
    public Upgrade forChunk(int cx, int cz, boolean blocks) {
        boolean old = upgrade.isOld(cx, cz);
        boolean upgrading = old && upgrades(cx, cz);
        int r = Blending.HEIGHT_RANGE_CHUNKS;
        int side = 2 * r + 1;
        Blending.Data[] near = null;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (!upgrade.isOld(cx + dx, cz + dz)) {
                    continue;
                }
                Blending.Data d = data(cx + dx, cz + dz);
                if (d != null) {
                    if (near == null) {
                        near = new Blending.Data[side * side];
                    }
                    near[(dx + r) * side + dz + r] = d;
                }
            }
        }
        if (near == null && !old) {
            return null;
        }
        Blending.Blender blender = near == null ? null : new Blending.Blender(cx, cz, near);
        OldChunks.OldChunk c = old ? chunks.get(cx, cz) : null;
        // Only a chunk the carvers run in has the filter: a new one, or an old one being upgraded.
        Blending.CarveMask mask = old && !upgrading ? null : Blending.CarveMask.of(upgrading, oldSides(cx, cz));
        char[] oldCodes = blocks && c != null ? codes(c) : null;
        boolean[] holes = upgrading ? c.holes() : null;
        byte[] biomes = c != null ? biomes(c) : null;
        return new Upgrade(upgrading, upgrading && !c.populated, blender, mask, oldCodes, holes, biomes);
    }

    /** The Direction8 bits around cx, cz whose chunk is old. */
    private int oldSides(int cx, int cz) {
        int sides = 0;
        for (int d = 0; d < 8; d++) {
            if (upgrade.isOld(cx + Blending.STEPS[d][0], cz + Blending.STEPS[d][1])) {
                sides |= 1 << d;
            }
        }
        return sides;
    }

    @Nullable
    private Blending.Data data(int cx, int cz) {
        long k = key(cx, cz);
        if (data.containsKey(k)) {
            return data.get(k);
        }
        OldChunks.OldChunk c = chunks.get(cx, cz);
        Blending.Data d = NONE;
        if (c != null) {
            int newSides = ~oldSides(cx, cz) & 0xFF;
            d = new Blending.Data(OldChunks.blocks(c), newSides);
        }
        data.put(k, d);
        return d;
    }

    /** The old chunk's biomes per column (x * 16 + z), 1.18 ids. */
    private static byte[] biomes(OldChunks.OldChunk c) {
        byte[] out = new byte[256];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                out[x * 16 + z] = (byte) BiomeMap.upgraded(c.biome(x, z));
            }
        }
        return out;
    }

    /** The old chunk's blocks as block codes, (x * 16 + z) * 256 + y. */
    char[] codes(OldChunks.OldChunk c) {
        char[] ids = c.ids();
        char[] out = new char[ids.length];
        int last = -1;
        char lastCode = 0;
        for (int i = 0; i < ids.length; i++) {
            int id = ids[i];
            if (id != last) {
                last = id;
                lastCode = code(id);
            }
            out[i] = lastCode;
        }
        return out;
    }

    /** The block code of a 1.12 state: its own where the 1.18 terrain has it, else air, water, lava or stone. */
    char code(int id) {
        char c = codes[id];
        if (c != 0xFFFF) {
            return c;
        }
        if (byState == null) {
            byState = new HashMap<>();
            for (int code = BlockCodes.count() - 1; code >= 0; code--) {
                IBlockState s = states.state(code);
                if (s != null) {
                    byState.put(s, code);
                }
            }
        }
        IBlockState state = OldChunks.state(id);
        int code;
        Integer own = state == null ? null : byState.get(state);
        if (own != null) {
            code = own;
        } else if (state == null || state.getMaterial() == Material.AIR) {
            code = BlockCodes.AIR;
        } else if (state.getMaterial() == Material.WATER) {
            code = BlockCodes.WATER;
        } else if (state.getMaterial() == Material.LAVA) {
            code = BlockCodes.LAVA;
        } else if (state.getBlock() == Blocks.BEDROCK) {
            code = BlockCodes.BEDROCK;
        } else {
            code = BlockCodes.STONE;
        }
        codes[id] = (char) code;
        return (char) code;
    }
}
