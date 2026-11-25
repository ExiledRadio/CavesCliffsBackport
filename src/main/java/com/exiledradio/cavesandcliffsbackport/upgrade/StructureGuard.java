package com.exiledradio.cavesandcliffsbackport.upgrade;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import net.minecraft.world.World;
import net.minecraft.world.gen.MapGenBase;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.reflect.Field;

/**
 * No structure starts in an old chunk of an upgraded world, and none reaches into one (1.18: ProtoChunk refuses a
 * start in an upgrading chunk, and an old chunk is past the structure steps). Every structure generator built on
 * {@code MapGenStructure} goes through {@code recursiveGenerate} when it makes a start (1.12's own, Backport Core's
 * {@code ScatteredStructure}, {@code ModernJigsaw} and data-driven structures, every family backport's, other mods'):
 * the coremod hands each new start here ({@code ModCompatPatches.StructureGuardPatch}). A start that would lie in or
 * reach into an old chunk is emptied (no pieces), so nothing is built; it is still kept, so the same chunk is not
 * asked again. Starts saved before the upgrade are read from the save, not made here, and go on as before.
 */
public final class StructureGuard {

    private static Field worldField;
    /** Starts emptied this session (for the self-test and the log). */
    public static volatile int emptied;

    private StructureGuard() {
    }

    /** Whether the coremod patched MapGenStructure (else no world may be upgraded). */
    public static boolean patched() {
        try {
            Class.forName("net.minecraft.world.gen.structure.MapGenStructure");
        } catch (ClassNotFoundException e) {
            return false;
        }
        return com.exiledradio.cavesandcliffsbackport.height.asm.PatchReport.compatPatched("StructureGuard");
    }

    private static World world(MapGenStructure gen) {
        try {
            if (worldField == null) {
                worldField = ObfuscationReflectionHelper.findField(MapGenBase.class, "field_75039_c");
            }
            return (World) worldField.get(gen);
        } catch (Exception e) {
            return null;
        }
    }

    /** From MapGenStructure.recursiveGenerate, right after getStructureStart. */
    public static StructureStart filter(StructureStart start, MapGenStructure gen) {
        if (start == null) {
            return null;
        }
        World world = world(gen);
        WorldUpgrade up = WorldUpgrade.of(world);
        // Another mod's generator (RTG, OTG) still makes the chunks from y 0 up: its structures go where they always did.
        if (up == null || up.hybrid()) {
            return start;
        }
        if (touchesOld(up, start)) {
            start.getComponents().clear();
            emptied++;
            if (emptied <= 5) {
                CavesAndCliffsBackport.LOGGER.debug("Upgraded world: no {} at chunk {}, {}: it would lie in or reach into "
                        + "an old chunk.", gen.getStructureName(), start.getChunkPosX(), start.getChunkPosZ());
            }
        }
        return start;
    }

    static boolean touchesOld(WorldUpgrade up, StructureStart start) {
        if (up.isOld(start.getChunkPosX(), start.getChunkPosZ())) {
            return true;
        }
        StructureBoundingBox box = start.getBoundingBox();
        if (box == null || start.getComponents().isEmpty()) {
            return false;
        }
        for (int cx = box.minX >> 4; cx <= box.maxX >> 4; cx++) {
            for (int cz = box.minZ >> 4; cz <= box.maxZ >> 4; cz++) {
                if (up.isOld(cx, cz)) {
                    return true;
                }
            }
        }
        return false;
    }
}
