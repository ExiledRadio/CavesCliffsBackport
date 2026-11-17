package com.exiledradio.cavesandcliffsbackport.height.compat;

import com.exiledradio.cavesandcliffsbackport.height.Heights;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/**
 * KAGIC X11p13 in a world with 1.18's height (plan {@code docs/streams/moddedores.md}, O3; patches in
 * {@code height.asm.ModCompatPatches}). Each method answers exactly what KAGIC's own code did in a world of 0-255.
 */
public final class KagicHooks {

    /** KAGIC's injector floor above the world's bottom: a seed goes no lower than y 5. */
    static final int FLOOR = 5;
    /** The lowest y the depth bonus divides by: KAGIC's lowest seed y in a 0-255 world. */
    static final int DEPTH_MIN = FLOOR + 1;

    private KagicHooks() {
    }

    /** {@code BlockInjector}: the lowest y a gem seed may go, 5 above the world's bottom. */
    public static int injectorFloor(World world) {
        return Heights.minY(world) + FLOOR;
    }

    /**
     * {@code BlockInjector.calcDistanceUsingRedstoneSignals}: what it adds to the distance when a seed is already
     * there, so the result lands below the floor and the injection fails. 256 cannot do that in a taller world.
     */
    public static int seedTaken(World world) {
        return Heights.isTall(world) ? 1024 : 256;
    }

    /**
     * {@code InjectorResult.create}: the y its depth bonus divides by. A seed in a 0-255 world sits at 6 or higher;
     * below that (a taller world) it counts as 6, so it never divides by 0 or by a negative y.
     */
    public static int depthY(int y, World world) {
        return Heights.isTall(world) ? Math.max(y, DEPTH_MIN) : y;
    }

    /** Meteor rubies fall from the top of the world (KAGIC: 256). */
    public static double meteorY(Entity ruby) {
        return Heights.maxY(ruby.world);
    }

    /** {@code /meteorruby}: the same, from the command sender's world, as a double for the ruby's position. */
    public static double meteorY(ICommandSender sender) {
        return Heights.maxY(sender.getEntityWorld());
    }

    /** {@code /meteorruby}: the same as an int, for the sound's position and the chat message. */
    public static int meteorTop(ICommandSender sender) {
        return Heights.maxY(sender.getEntityWorld());
    }
}
