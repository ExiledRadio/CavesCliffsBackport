package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.backportcore.worldgen.FlatWorlds;
import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkGeneratorFlat;
import net.minecraft.world.gen.FlatGeneratorInfo;
import net.minecraft.world.gen.FlatLayerInfo;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenVillage;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A real superflat world ({@code tools/flat/selftest.sh <preset>}, never part of "all"): generates the chunks around
 * spawn, then scans them for this mod's blocks and mobs.
 *
 * <ul>
 *   <li>Always: the layers are the preset's (no deepslate, whatever the options), no dripstone, lush caves or powder
 *       snow (a cave biome's features, or 1.18's surface rule, never a superflat feature), and none of this mod's
 *       mobs (1.12's superflat spawns nothing while it generates).</li>
 *   <li>Without {@code decoration}: none of this mod's blocks at all, and every block below the top layer is the
 *       preset's, outside villages.</li>
 *   <li>With {@code decoration}: only what 1.17 and 1.18 put in every Overworld biome (copper ore, amethyst geodes,
 *       glow lichen), and with stone in the preset, copper ore and geodes.</li>
 * </ul>
 */
final class FlatSelfTest implements SelfTest {

    /** The blocks 1.17 and 1.18 decorate any Overworld biome with: ores, geodes and glow lichen. */
    private static final Set<String> DECORATION = new HashSet<String>(Arrays.asList("copper_ore", "amethyst_block",
            "budding_amethyst", "small_amethyst_bud", "medium_amethyst_bud", "large_amethyst_bud", "amethyst_cluster",
            "calcite", "smooth_basalt", "glow_lichen", "glow_lichen_down", "glow_lichen_up", "glow_lichen_up_down"));

    private static final int GENERATE = 6;
    /** The chunks scanned: these have all been populated (their +x, +z neighbors are loaded). */
    private static final int SCAN = 5;

    @Override
    public String name() {
        return "flat";
    }

    @Override
    public void run(Check check) {
        WorldServer world = check.world;
        String options = world.getWorldInfo().getGeneratorOptions();
        if (!check.ok(FlatWorlds.isFlat(world), "a superflat world (run tools/flat/selftest.sh): " + world.getWorldType().getName())) {
            return;
        }
        boolean decoration = FlatWorlds.decoration(world);
        check.eq(options.contains("decoration"), decoration, "decoration as the preset says (" + options + ")");

        // The preset's column, bottom up.
        FlatGeneratorInfo info = FlatWorlds.info(world);
        IBlockState[] column = new IBlockState[256];
        boolean stone = false;
        int top = 0;
        for (FlatLayerInfo layer : info.getFlatLayers()) {
            for (int y = layer.getMinY(); y < layer.getMinY() + layer.getLayerCount() && y < 256; y++) {
                column[y] = layer.getLayerMaterial();
                top = Math.max(top, y);
            }
            stone |= layer.getLayerMaterial().getBlock() == Blocks.STONE;
        }

        BlockPos spawn = world.getSpawnPoint();
        int cx = spawn.getX() >> 4, cz = spawn.getZ() >> 4;
        for (int x = cx - GENERATE; x <= cx + GENERATE; x++) {
            for (int z = cz - GENERATE; z <= cz + GENERATE; z++) {
                world.getChunkProvider().provideChunk(x, z);
            }
        }

        Map<String, Integer> ours = new TreeMap<String, Integer>();
        int changed = 0;
        String firstChange = null;
        int unpopulated = 0;
        int scanTop = Math.min(255, top + 24);
        List<StructureBoundingBox> villages = villagePieces(world);
        for (int x = cx - SCAN; x <= cx + SCAN; x++) {
            for (int z = cz - SCAN; z <= cz + SCAN; z++) {
                Chunk chunk = world.getChunk(x, z);
                if (!chunk.isTerrainPopulated()) {
                    unpopulated++;
                }
                for (int bx = 0; bx < 16; bx++) {
                    for (int bz = 0; bz < 16; bz++) {
                        for (int y = 0; y <= scanTop; y++) {
                            IBlockState s = chunk.getBlockState(bx, y, bz);
                            Block b = s.getBlock();
                            ResourceLocation id = b.getRegistryName();
                            if (id != null && CavesAndCliffsBackport.MOD_ID.equals(id.getNamespace())) {
                                Integer n = ours.get(id.getPath());
                                ours.put(id.getPath(), n == null ? 1 : n + 1);
                            }
                            // Below the top layer, outside villages (they pave the top and sink their foundations);
                            // the decorator's ores change it too.
                            if (!decoration && y < top && column[y] != null && s != column[y]
                                    && !inside(villages, x * 16 + bx, z * 16 + bz)) {
                                changed++;
                                if (firstChange == null) {
                                    firstChange = s + " at " + new BlockPos(x * 16 + bx, y, z * 16 + bz) + ", preset " + column[y];
                                }
                            }
                        }
                    }
                }
            }
        }
        CavesAndCliffsBackport.LOGGER.info("SELFTEST flat: preset {}, this mod's blocks {}", options, ours);
        check.eq(0, unpopulated, "every scanned chunk populated");
        CavesAndCliffsBackport.LOGGER.info("SELFTEST flat: {} village pieces", villages.size());

        boolean deepslate = false;
        Set<String> outside = new HashSet<String>();
        for (String name : ours.keySet()) {
            deepslate |= name.contains("deepslate");
            if (!DECORATION.contains(name)) {
                outside.add(name);
            }
        }
        check.ok(!deepslate, "no deepslate in a superflat world: " + ours);
        check.ok(outside.isEmpty(), "none of this mod's blocks but 1.17's and 1.18's decoration of any biome: " + outside);
        if (!decoration) {
            check.ok(ours.isEmpty(), "no decoration: none of this mod's blocks: " + ours);
            check.eq(0, changed, "no decoration: the layers are exactly the preset's below the top (first: " + firstChange + ")");
        } else if (stone) {
            check.ok(ours.containsKey("copper_ore"), "decoration with stone: copper ore: " + ours);
            check.ok(ours.containsKey("calcite") && ours.containsKey("amethyst_block"), "decoration with stone: amethyst geodes: " + ours);
        }

        Set<String> mobs = new HashSet<String>();
        for (Entity e : world.loadedEntityList) {
            ResourceLocation id = EntityList.getKey(e);
            if (id != null && CavesAndCliffsBackport.MOD_ID.equals(id.getNamespace())) {
                mobs.add(id.getPath());
            }
        }
        check.ok(mobs.isEmpty(), "none of this mod's mobs spawned while generating: " + mobs);
    }

    /** Every piece of every village the generator has laid out (a well sinks its shaft deep into the layers). */
    @SuppressWarnings("unchecked")
    private static List<StructureBoundingBox> villagePieces(WorldServer world) {
        List<StructureBoundingBox> boxes = new ArrayList<StructureBoundingBox>();
        try {
            IChunkGenerator generator = world.getChunkProvider().chunkGenerator;
            Field generators = ReflectionHelper.findField(ChunkGeneratorFlat.class, "structureGenerators", "field_82696_f");
            Field starts = ReflectionHelper.findField(MapGenStructure.class, "structureMap", "field_75053_d");
            for (MapGenStructure structure : ((Map<String, MapGenStructure>) generators.get(generator)).values()) {
                if (structure instanceof MapGenVillage) {
                    for (StructureStart start : ((Map<Long, StructureStart>) starts.get(structure)).values()) {
                        for (StructureComponent piece : start.getComponents()) {
                            boxes.add(piece.getBoundingBox());
                        }
                    }
                }
            }
        } catch (Exception e) {
            CavesAndCliffsBackport.LOGGER.error("SELFTEST flat: no village pieces", e);
        }
        return boxes;
    }

    private static boolean inside(List<StructureBoundingBox> boxes, int x, int z) {
        for (StructureBoundingBox b : boxes) {
            if (x >= b.minX && x <= b.maxX && z >= b.minZ && z <= b.maxZ) {
                return true;
            }
        }
        return false;
    }
}
