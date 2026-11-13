package com.exiledradio.cavesandcliffsbackport.terrain;

import com.exiledradio.backportcore.structure.TerrainAdaptation;
import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Beardifier;
import com.exiledradio.cavesandcliffsbackport.terrain.core.Pipeline;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 1.18's terrain adaptation around structures ({@code adapt_noise}: villages, pillager outposts, strongholds), for
 * the pipeline's fills: before a chunk's fill is queued, the structure starts that can reach it are laid out (1.12
 * lays out a start when the chunks around it are generated, and builds it when they are populated; laying it out
 * first changes nothing, as every start comes from its own seed) and their pieces given to
 * {@link Beardifier#forChunk}, in 1.18's order: by structure, each structure's starts in the order a chunk lists its
 * references, each start's pieces in its own order.
 *
 * <ul>
 *   <li>Villages: 1.12's houses, lamps and wells as 1.18's rigid jigsaw pieces (beard), their ground at the level
 *       {@link ModernStructures.Village} set them on; roads follow the ground (1.18's terrain-matching pieces have
 *       no beard of their own).</li>
 *   <li>Strongholds: every piece buried (1.18's StrongholdPiece noise effect).</li>
 *   <li>Other mods' structures registered with Backport Core's {@link TerrainAdaptation} (Village &amp; Pillage's
 *       outposts), after these.</li>
 * </ul>
 *
 * <p>Where a backport builds the villages instead (Village &amp; Pillage's 1.14 villages, through Backport Core's
 * {@code StructureGenerators}), 1.12's are neither laid out nor adapted to: the backport's registered village kind
 * takes their place at the front of the order.
 */
final class StructureBeards implements Pipeline.BeardSource {

    private final World world;
    /** Null where a backport builds the villages in place of 1.12's. */
    private final ModernStructures.Village village;
    private final ModernStructures.Stronghold stronghold;
    private final Set<String> unsupported = new HashSet<>();

    StructureBeards(World world, ModernStructures.Village village, ModernStructures.Stronghold stronghold) {
        this.world = world;
        this.village = village;
        this.stronghold = stronghold;
    }

    @Override
    public Beardifier forChunk(int cx, int cz) {
        final List<Beardifier.Piece> pieces = new ArrayList<>();
        List<TerrainAdaptation.Entry> registered = TerrainAdaptation.registered();
        if (village != null) {
            village.startsNear(world, cx, cz, start -> {
                for (StructureComponent c : start.getComponents()) {
                    if (ModernStructures.grounded(c)) {
                        StructureBoundingBox b = c.getBoundingBox();
                        pieces.add(new Beardifier.Piece(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, Beardifier.BEARD,
                                true, ModernStructures.groundOf(c) - b.minY, true, new int[0]));
                    }
                }
            });
        } else {
            for (TerrainAdaptation.Entry e : registered) {
                if (e.kind.equals(com.exiledradio.backportcore.structure.Structures.VILLAGE)) {
                    plan(e, cx, cz, pieces);
                }
            }
        }
        stronghold.startsNear(world, cx, cz, start -> add(start, Beardifier.BURY, pieces));
        for (TerrainAdaptation.Entry e : registered) {
            if (village != null || !e.kind.equals(com.exiledradio.backportcore.structure.Structures.VILLAGE)) {
                plan(e, cx, cz, pieces);
            }
        }
        return pieces.isEmpty() ? null : Beardifier.forChunk(cx, cz, pieces);
    }

    /** A registered kind's starts near the chunk, their pieces with its noise effect. */
    private void plan(TerrainAdaptation.Entry e, int cx, int cz, List<Beardifier.Piece> pieces) {
        final int effect = effect(e);
        if (effect == Beardifier.NONE) {
            return;
        }
        try {
            e.planner.startsNear(world, cx, cz, start -> add(start, effect, pieces));
        } catch (RuntimeException ex) {
            CavesAndCliffsBackport.LOGGER.error("Laying out {} near chunk {}, {} failed; the terrain does not adapt to it there.",
                    e.kind, cx, cz, ex);
        }
    }

    /** A start's pieces with one noise effect; jigsaw pieces by their projection, ground and junctions. */
    private static void add(StructureStart start, int effect, List<Beardifier.Piece> pieces) {
        for (StructureComponent c : start.getComponents()) {
            StructureBoundingBox b = c.getBoundingBox();
            if (c instanceof TerrainAdaptation.AdaptingPiece) {
                TerrainAdaptation.AdaptingPiece a = (TerrainAdaptation.AdaptingPiece) c;
                int[] j = a.junctions();
                pieces.add(new Beardifier.Piece(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, effect, true,
                        a.groundLevelDelta(), a.rigid(), j == null ? new int[0] : j));
            } else {
                pieces.add(new Beardifier.Piece(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, effect));
            }
        }
    }

    /** 1.18.2's noise effects: beard_thin is its BEARD, bury its BURY; 1.19's beard_box and encapsulate it has not. */
    private int effect(TerrainAdaptation.Entry e) {
        switch (e.adaptation) {
            case BEARD_THIN:
                return Beardifier.BEARD;
            case BURY:
                return Beardifier.BURY;
            case NONE:
                return Beardifier.NONE;
            default:
                if (unsupported.add(e.kind)) {
                    CavesAndCliffsBackport.LOGGER.warn("{} asks for terrain adaptation {}, which 1.18.2's terrain does not "
                            + "have; the terrain does not adapt to it.", e.kind, e.adaptation);
                }
                return Beardifier.NONE;
        }
    }
}
