package com.exiledradio.cavesandcliffsbackport.cubic;

import com.exiledradio.cavesandcliffsbackport.height.WorldTypeCavesAndCliffs;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernGenerator;
import com.exiledradio.cavesandcliffsbackport.terrain.ModernTerrain;
import io.github.opencubicchunks.cubicchunks.api.util.IntRange;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorldType;
import io.github.opencubicchunks.cubicchunks.api.worldgen.ICubeGenerator;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;

/**
 * The Caves &amp; Cliffs world type ({@code cavesandcliffs}) as a Cubic Chunks world type, registered in its place when
 * Cubic Chunks is installed. Its overworld is cubic from y -64 to 319 (Cubic Chunks' own height limits, set for the
 * world by {@link CubicWorlds#onAttachWorld}), and its cubes come from the 1.18 terrain ({@link CubicGenerator}). A
 * Caves &amp; Cliffs world that keeps 1.12's terrain ({@code ModernTerrain}: one made before the 1.18 terrain) gets
 * Cubic Chunks' vanilla-compatibility generator over that terrain, as any vanilla world type does.
 */
public final class CubicWorldType extends WorldTypeCavesAndCliffs implements ICubicWorldType {

    CubicWorldType() {
    }

    @Nullable
    @Override
    public ICubeGenerator createCubeGenerator(World world) {
        ModernGenerator generator = ModernTerrain.generator(world);
        return generator == null ? null : new CubicGenerator((WorldServer) world, generator);
    }

    @Override
    public IntRange calculateGenerationHeightRange(WorldServer world) {
        return new IntRange(CubicSupport.MIN_Y, CubicSupport.MAX_Y);
    }

    @Override
    public boolean hasCubicGeneratorForWorld(World world) {
        return world instanceof WorldServer && ModernTerrain.of(world) != null;
    }
}
