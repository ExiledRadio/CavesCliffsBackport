package com.exiledradio.cavesandcliffsbackport.height.mixin;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Empty: it only puts {@code World} in front of {@code height.asm.StellarCoreMixinPlugin}, whose postApply does the
 * work. Applied only when StellarCore is installed; priority after StellarCore's default 1000.
 */
@Mixin(value = World.class, priority = 1100)
public abstract class MixinWorldStellarCore {
}
