package com.exiledradio.cavesandcliffsbackport.height;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.ChunkGeneratorOverworld;
import net.minecraft.world.gen.IChunkGenerator;

/**
 * The "Caves & Cliffs" world type ({@code cavesandcliffs}): an overworld from y -64 to 319.
 *
 * <p>The terrain is 1.18.2's ({@code terrain.ModernGenerator}). A Caves & Cliffs world created before the 1.18
 * terrain existed keeps the terrain it was made with: vanilla 1.12's (same seed, same biomes, same structures as the
 * Default type) with solid stone from y -1 down to bedrock at y -64 ({@link Generator}).
 *
 * <p>With Cubic Chunks installed the coremod is off, and {@code cubic.CubicWorldType} (this type made cubic) is
 * registered in its place: the same terrain, in Cubic Chunks' cubes.
 */
public class WorldTypeCavesAndCliffs extends WorldType {

    public static final String NAME = "cavesandcliffs";

    protected WorldTypeCavesAndCliffs() {
        super(NAME);
    }

    @Override
    public IChunkGenerator getChunkGenerator(World world, String generatorOptions) {
        IChunkGenerator modern = com.exiledradio.cavesandcliffsbackport.terrain.ModernTerrain.generator(world);
        if (modern != null) {
            return modern;
        }
        return new Generator(world, world.getSeed(), world.getWorldInfo().isMapFeaturesEnabled(), generatorOptions);
    }

    @Override
    public net.minecraft.world.biome.BiomeProvider getBiomeProvider(World world) {
        net.minecraft.world.biome.BiomeProvider modern = com.exiledradio.cavesandcliffsbackport.terrain.ModernTerrain.biomeProvider(world);
        return modern != null ? modern : super.getBiomeProvider(world);
    }

    @Override
    public boolean isCustomizable() {
        return false;
    }

    /** Vanilla's overworld generator, then the sections below y 0. */
    static final class Generator extends ChunkGeneratorOverworld {
        private final World world;
        private boolean checked;

        Generator(World world, long seed, boolean features, String options) {
            super(world, seed, features, options);
            this.world = world;
        }

        @Override
        public Chunk generateChunk(int x, int z) {
            Chunk chunk = super.generateChunk(x, z);
            if (Heights.isExtended(this.world)) {
                fillBelowZero(chunk, this.world.provider.hasSkyLight());
                if (!this.checked) {
                    this.checked = true;
                    // A mixin that overwrites a patched Chunk method after our transformer is invisible to the drift
                    // report; the first chunk shows it.
                    if (chunk.getBlockState(0, HeightLayout.EXTENDED_MIN_Y, 0).getBlock() != Blocks.BEDROCK) {
                        com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport.LOGGER.error("Extended height is BROKEN in "
                                + "this game: Chunk.getBlockState answers {} at y -64 where the chunk holds bedrock. Another mod "
                                + "replaces that method after this mod's patch (a mixin @Overwrite). Remove it or turn that "
                                + "part of it off, or set height.enabled=false.", chunk.getBlockState(0, -64, 0));
                    }
                }
            }
            return chunk;
        }
    }

    /**
     * Fills y -64..-1 of a freshly generated chunk: bedrock at -64, stone above. Written straight into the four
     * sections (slots 20-23); their sky light stays 0, which is right under solid stone, so no relight is needed.
     */
    static void fillBelowZero(Chunk chunk, boolean sky) {
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        IBlockState stone = Blocks.STONE.getDefaultState();
        IBlockState bedrock = Blocks.BEDROCK.getDefaultState();
        for (int slot = HeightLayout.FIRST_LOW_SLOT; slot < HeightLayout.EXTENDED_SECTIONS; slot++) {
            int base = HeightLayout.slotBase(slot);
            ExtendedBlockStorage section = new ExtendedBlockStorage(base, sky);
            for (int y = 0; y < 16; y++) {
                IBlockState state = base + y == HeightLayout.EXTENDED_MIN_Y ? bedrock : stone;
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        section.set(x, y, z, state);
                    }
                }
            }
            sections[slot] = section;
        }
    }
}
