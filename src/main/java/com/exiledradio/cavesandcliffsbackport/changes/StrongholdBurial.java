package com.exiledradio.cavesandcliffsbackport.changes;

import com.exiledradio.cavesandcliffsbackport.ModConfig;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.structure.MapGenStronghold;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.InitMapGenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 1.17's buried strongholds in the Default world type (21w07a, MC-216191): "Strongholds are no longer exposed in
 * oceans. Instead, they get covered by the seabed." 1.17 did it with the noise (a stronghold piece's {@code
 * NoiseEffect.BURY} raises the density around its box, so the ground closes over it); Caves & Cliffs worlds do the
 * same ({@code terrain.core.Beardifier}).
 *
 * <p>1.12.2's Default generator has no density to raise, so here the water of the terrain around each stronghold piece
 * (its box and one block beyond, the faces 1.12.2's {@code isLiquidInStructureBoundingBox} reads) is made stone when
 * the chunk's terrain is made, after the caves, before anything is built. 1.12.2 then builds the pieces it skipped for
 * touching water, so the stronghold is whole, sealed in a mound of stone on the sea floor. Air (caves) is left alone,
 * as 1.17 carves its caves after the noise. Only new chunks; with the setting off the generator is 1.12.2's.
 */
public final class StrongholdBurial {

    /** Blocks beyond a piece's box that are buried with it. */
    public static final int MARGIN = 1;

    private StrongholdBurial() {
    }

    public static void init() {
        MinecraftForge.TERRAIN_GEN_BUS.register(StrongholdBurial.class);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void initMapGen(InitMapGenEvent event) {
        if (event.getType() == InitMapGenEvent.EventType.STRONGHOLD && event.getNewGen() != null
                && event.getNewGen().getClass() == MapGenStronghold.class) {
            event.setNewGen(new Generator());
        }
    }

    /** 1.12.2's stronghold generator, which buries its pieces in each new chunk's terrain. */
    public static final class Generator extends MapGenStronghold {
        @Override
        public void generate(World world, int chunkX, int chunkZ, ChunkPrimer primer) {
            super.generate(world, chunkX, chunkZ, primer);
            if (ModConfig.changes.strongholdBurial) {
                for (StructureStart start : this.structureMap.values()) {
                    bury(start, chunkX, chunkZ, primer);
                }
            }
        }
    }

    /** Makes the water of one chunk's terrain within {@value #MARGIN} of the start's pieces stone. */
    public static int bury(StructureStart start, int chunkX, int chunkZ, ChunkPrimer primer) {
        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        StructureBoundingBox chunk = new StructureBoundingBox(x0 - MARGIN, z0 - MARGIN, x0 + 15 + MARGIN, z0 + 15 + MARGIN);
        if (start == null || !start.isSizeableStructure() || !start.getBoundingBox().intersectsWith(chunk)) {
            return 0;
        }
        IBlockState stone = Blocks.STONE.getDefaultState();
        int buried = 0;
        for (StructureComponent piece : start.getComponents()) {
            StructureBoundingBox b = piece.getBoundingBox();
            if (!b.intersectsWith(chunk)) {
                continue;
            }
            int minX = Math.max(b.minX - MARGIN, x0) - x0;
            int maxX = Math.min(b.maxX + MARGIN, x0 + 15) - x0;
            int minZ = Math.max(b.minZ - MARGIN, z0) - z0;
            int maxZ = Math.min(b.maxZ + MARGIN, z0 + 15) - z0;
            int minY = Math.max(b.minY - MARGIN, 1);
            int maxY = Math.min(b.maxY + MARGIN, 255);
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        if (primer.getBlockState(x, y, z).getMaterial() == Material.WATER) {
                            primer.setBlockState(x, y, z, stone);
                            buried++;
                        }
                    }
                }
            }
        }
        return buried;
    }
}
