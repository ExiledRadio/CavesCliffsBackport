package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.backportcore.structure.FreezeTopLayer;
import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.height.Heights;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernGenerator;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernTerrain;
import com.exiledradio.cavesandcliffsbackport.terrain.core.BiomeSource;
import com.exiledradio.cavesandcliffsbackport.terrain.core.ColumnCache;
import com.exiledradio.cavesandcliffsbackport.terrain.core.OverworldNoise;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Structures;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Snow over structures in a Caves &amp; Cliffs world ({@code sh tools/height/selftest.sh structsnow}): the pipeline runs
 * 1.18's freeze_top_layer with the chunk's features, before populate builds the structures, so the generator snows
 * again over their columns (Backport Core's FreezeTopLayer, by 1.18's temperatures). One igloo built without that pass
 * (bare), one with it, and a snowy village: every column a piece covers whose top could take a snow layer by 1.18's
 * rule must have one.
 */
public final class StructSnowSelfTest implements SelfTest {

    @Override
    public String name() {
        return "structsnow";
    }

    @Override
    public void run(Check check) throws Exception {
        WorldServer world = check.world;
        if (!Heights.isExtended(world) || !ModernTerrain.owns(world)) {
            check.fail("structsnow needs a new Caves & Cliffs world (tools/height/selftest.sh structsnow)");
            return;
        }
        ModernGenerator generator = (ModernGenerator) world.getChunkProvider().chunkGenerator;
        check.ok(FreezeTopLayer.climate(world) == generator, "the generator gives Core its 1.18 climate");
        OverworldNoise noise = new OverworldNoise(world.getSeed());
        ColumnCache columns = new ColumnCache(noise);
        Structures core = new Structures(noise, columns,
                new BiomeSource(noise, columns, new com.exiledradio.cavesandcliffsbackport.terrain.core.Climate()));
        BlockPos spawn = world.getSpawnPoint();
        // Villages: built in populate by 1.12's pieces, after the pipeline snowed. One without the pass, one with it.
        List<ChunkPos> villages = find(world, core, Structures.VILLAGES, "village_snowy", spawn, 2, 7);
        if (!check.eq(2, villages.size(), "two snowy villages, not yet generated (" + villages + ")")) {
            return;
        }
        build(world, villages.get(0), 7, false);
        build(world, villages.get(1), 7, true);
        int[] without = survey(world, generator, generator.villageGenerator(), villages.get(0), "snowy village without the pass");
        int[] with = survey(world, generator, generator.villageGenerator(), villages.get(1), "snowy village with the pass");
        CavesAndCliffsBackport.LOGGER.info("SNOW before/after: snowy village without the pass {} of {} piece columns snowed ({} could "
                + "be), with it {} of {} ({} could be)", without[0], without[2], without[1], with[0], with[2], with[1]);
        check.ok(without[1] > 0, "without the pass the village has bare tops (" + without[1] + ")");
        check.ok(with[2] > 0 && with[1] == 0, "with the pass every village column that can snow did (" + with[0] + " snowed, "
                + with[1] + " open of " + with[2] + ")");
        // An igloo (its template has snow on the roof already; the pass must leave it as it is and cover the rest).
        List<ChunkPos> igloos = find(world, core, Structures.IGLOOS, null, spawn, 1, 4);
        if (!check.ok(!igloos.isEmpty(), "an igloo, not yet generated")) {
            return;
        }
        MapGenStructure temple = field(generator, "temple");
        build(world, igloos.get(0), 3, true);
        int[] igloo = survey(world, generator, temple, igloos.get(0), "igloo");
        check.ok(igloo[2] > 0 && igloo[1] == 0, "every igloo column that can snow did (" + igloo[0] + " snowed, " + igloo[1]
                + " open of " + igloo[2] + ")");
    }

    private static List<ChunkPos> find(WorldServer world, Structures core, int set, String name, BlockPos spawn, int want,
                                       int radius) {
        List<ChunkPos> found = new ArrayList<ChunkPos>();
        int sx = spawn.getX() >> 4;
        int sz = spawn.getZ() >> 4;
        for (int r = 8; r < 400 && found.size() < want; r++) {
            for (int dx = -r; dx <= r && found.size() < want; dx++) {
                for (int dz = -r; dz <= r && found.size() < want; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) {
                        continue;
                    }
                    int cx = sx + dx;
                    int cz = sz + dz;
                    int i = core.startAt(set, cx, cz);
                    if (i < 0 || (name != null && !name.equals(Structures.STRUCTURE_NAMES[set][i])) || generated(world, cx, cz, radius)) {
                        continue;
                    }
                    boolean far = true;
                    for (ChunkPos c : found) {
                        far &= Math.abs(c.x - cx) > 2 * radius + 4 || Math.abs(c.z - cz) > 2 * radius + 4;
                    }
                    if (far) {
                        found.add(new ChunkPos(cx, cz));
                    }
                }
            }
        }
        return found;
    }

    private static boolean generated(WorldServer world, int cx, int cz, int radius) {
        for (int dx = -radius - 1; dx <= radius + 1; ++dx) {
            for (int dz = -radius - 1; dz <= radius + 1; ++dz) {
                if (world.getChunkProvider().isChunkGeneratedAt(cx + dx, cz + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void build(WorldServer world, ChunkPos c, int radius, boolean pass) {
        FreezeTopLayer.setEnabled(pass);
        try {
            for (int dx = -radius; dx <= radius + 1; ++dx) {
                for (int dz = -radius; dz <= radius + 1; ++dz) {
                    world.getChunk(c.x + dx, c.z + dz);
                }
            }
        } finally {
            FreezeTopLayer.setEnabled(true);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object owner, String name) throws ReflectiveOperationException {
        Field f = owner.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(owner);
    }

    @SuppressWarnings("unchecked")
    private static StructureStart start(MapGenStructure structure, ChunkPos c) {
        Map<Long, StructureStart> starts = (Map<Long, StructureStart>) ObfuscationReflectionHelper.getPrivateValue(
                MapGenStructure.class, structure, "structureMap", "field_75053_d");
        return starts.get(ChunkPos.asLong(c.x, c.z));
    }

    /**
     * @return {snowed columns, columns where 1.18 would still lay a snow layer, columns surveyed}: those the start's
     * pieces cover, where the chunk that decorates them is populated and it is cold enough to snow
     */
    private static int[] survey(WorldServer world, ModernGenerator generator, MapGenStructure structure, ChunkPos c, String name) {
        StructureStart start = start(structure, c);
        if (start == null || !start.isSizeableStructure()) {
            CavesAndCliffsBackport.LOGGER.info("SNOW no {} laid out at {}", name, c);
            return new int[] {0, 0, 0};
        }
        StructureBoundingBox all = start.getBoundingBox();
        int w = all.maxX - all.minX + 1;
        int d = all.maxZ - all.minZ + 1;
        boolean[] covered = new boolean[w * d];
        for (StructureComponent piece : start.getComponents()) {
            StructureBoundingBox b = piece.getBoundingBox();
            for (int x = Math.max(b.minX, all.minX); x <= Math.min(b.maxX, all.maxX); ++x) {
                for (int z = Math.max(b.minZ, all.minZ); z <= Math.min(b.maxZ, all.maxZ); ++z) {
                    covered[(x - all.minX) + (z - all.minZ) * w] = true;
                }
            }
        }
        int snowed = 0;
        int open = 0;
        int columns = 0;
        StringBuilder map = new StringBuilder();
        for (int z = all.minZ; z <= all.maxZ; ++z) {
            map.append('\n');
            for (int x = all.minX; x <= all.maxX; ++x) {
                if (!covered[(x - all.minX) + (z - all.minZ) * w]
                        || !world.getChunkProvider().isChunkGeneratedAt((x - 8) >> 4, (z - 8) >> 4)
                        || !world.getChunk((x - 8) >> 4, (z - 8) >> 4).isTerrainPopulated()) {
                    map.append(' ');
                    continue;
                }
                BlockPos top = world.getPrecipitationHeight(new BlockPos(x, 0, z));
                if (!generator.coldEnoughToSnow(world, top)) {
                    map.append('~');
                    continue;
                }
                columns++;
                IBlockState at = world.getBlockState(top);
                Block under = world.getBlockState(top.down()).getBlock();
                if (at.getBlock() == Blocks.SNOW_LAYER) {
                    snowed++;
                    map.append('*');
                } else if (at.getBlock().isAir(at, world, top) && Blocks.SNOW_LAYER.canPlaceBlockAt(world, top)) {
                    // 1.18's rule while decorating: its light check always passes (light comes after the features).
                    open++;
                    if (open <= 10 && !name.contains("without")) {
                        CavesAndCliffsBackport.LOGGER.info("SNOW open column in the {} at {}: under {}, block light {}", name, top,
                                world.getBlockState(top.down()), world.getLightFor(EnumSkyBlock.BLOCK, top));
                    }
                    map.append(under == Blocks.GRASS ? 'g' : 'o');
                } else {
                    map.append('.');
                }
            }
        }
        CavesAndCliffsBackport.LOGGER.info("SNOW top view, {} at {} (* snow, g bare grass, o other bare top, . cannot snow: "
                + "water, ...):{}", name, all, map);
        return new int[] {snowed, open, columns};
    }
}
