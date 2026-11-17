package com.exiledradio.cavesandcliffsbackport.height.asm;

import net.minecraft.launchwrapper.Launch;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.annotation.Nullable;
import java.util.List;

import static com.exiledradio.cavesandcliffsbackport.height.asm.Asm.*;

/**
 * StellarCore's {@code WorldBlockPosJudgement} option (on by default) {@code @Overwrite}s
 * {@code World.isOutsideBuildHeight} with {@code y < 0 || y >= 256}, replacing {@link WorldPatch}'s patch of it (and
 * {@code isValid}, which calls it). Mixin runs after every coremod transformer, so our transformer cannot see that
 * body. Instead, when StellarCore is installed, this mod adds a mixin config of its own
 * ({@code mixins.cavesandcliffsbackport.stellarcore.json}: one empty mixin on {@code World}) whose plugin
 * ({@link StellarCoreMixinPlugin}) gets {@code World} after every mixin is applied and points StellarCore's two
 * compares at the world's height, as {@link WorldPatch} does for vanilla's. StellarCore's code stays.
 *
 * <p>The match is exact (StellarCore 1.5.22 and 1.6.0 compile to the same bytecode); if the body is anything else,
 * nothing changes and {@code HeightHooks}' probe keeps the world 0-255 and says why. This class never touches Mixin
 * itself: the config is added by reflection, and only when StellarCore (which needs Mixin) is installed.
 */
public final class StellarCoreCompat {

    static final String CONFIG = "mixins.cavesandcliffsbackport.stellarcore.json";
    private static final String MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    private static final String STELLAR = "github.kasuminova.stellarcore.";

    /** StellarCore's version, or null when it is not installed. */
    @Nullable
    private static volatile String version;
    /** Null until World is mixed; then "adapted", "not overwritten", or why the body was left alone. */
    @Nullable
    private static volatile String result;

    private StellarCoreCompat() {
    }

    /** Called by the loading plugin when extended height is on: adds the mixin config if StellarCore is installed. */
    static void register() {
        if (Launch.classLoader == null
                || Launch.classLoader.getResource("github/kasuminova/stellarcore/mixin/StellarCoreEarlyMixinLoader.class") == null) {
            return;
        }
        String v = HeightCorePlugin.constant("github/kasuminova/stellarcore/Tags.class", "VERSION");
        version = v != null ? v : "unknown";
        try {
            Class.forName("org.spongepowered.asm.mixin.Mixins", true, Launch.classLoader)
                    .getMethod("addConfiguration", String.class).invoke(null, CONFIG);
            HeightCorePlugin.LOG.info("Extended height: StellarCore {} is installed; added {} to adapt its World "
                    + "position checks.", version, CONFIG);
        } catch (Throwable t) {
            result = "this mod's mixin config could not be added (" + t + ")";
            HeightCorePlugin.LOG.error("Extended height: StellarCore {} is installed, but {} could not be added.", version, CONFIG, t);
        }
    }

    /** StellarCore's version, or null when it is not installed. */
    @Nullable
    public static String version() {
        return version;
    }

    /** What happened to World's StellarCore checks: null if nothing ran, else "adapted", "not overwritten" or why not. */
    @Nullable
    public static String result() {
        return result;
    }

    /** From the mixin plugin's postApply on World. */
    static void postApply(ClassNode world) {
        try {
            result = rewrite(world);
        } catch (RuntimeException e) {
            result = "the rewrite threw " + e;
            HeightCorePlugin.LOG.error("Extended height: adapting StellarCore's World checks threw; left as they are.", e);
            return;
        }
        if ("adapted".equals(result)) {
            HeightCorePlugin.LOG.info("Extended height: StellarCore {}'s World.isOutsideBuildHeight now answers the "
                    + "world's height.", version);
        } else if (!"not overwritten".equals(result)) {
            HeightCorePlugin.LOG.error("Extended height: StellarCore {}'s World.isOutsideBuildHeight is left as it is: {}.",
                    version, result);
        }
    }

    /**
     * Rewrites StellarCore's {@code isOutsideBuildHeight} in place: {@code y < 0} becomes {@code y < minY} and
     * {@code y >= 256} becomes {@code y >= maxY}. Returns "adapted", "not overwritten" (StellarCore's option is off: our
     * own patch is still there), or why the method was left alone. Changes nothing unless it returns "adapted".
     */
    static String rewrite(ClassNode world) {
        MethodNode outside = method(world, "isOutsideBuildHeight", "func_189509_E", "(Lnet/minecraft/util/math/BlockPos;)Z");
        if (outside == null) {
            return "World.isOutsideBuildHeight was not found";
        }
        if (!mergedFromStellar(outside)) {
            return "not overwritten";
        }
        if (!world.interfaces.contains(WorldPatch.HEIGHT_WORLD)) {
            return "World is not patched for extended height";
        }
        // aload 1; getY; istore y; iload y; iflt T; iload y; sipush 256; if_icmplt F; T: iconst_1; goto E; F: iconst_0; E: ireturn
        List<AbstractInsnNode> starts = find(outside, aload(1), getY(), istore(-1));
        if (realCount(outside) != 12 || starts.size() != 1) {
            return "its body is not the one this mod knows";
        }
        int y = ((org.objectweb.asm.tree.VarInsnNode) at(starts.get(0), 2)).var;
        List<AbstractInsnNode> body = find(outside, aload(1), getY(), istore(y), iload(y), op(Opcodes.IFLT), iload(y),
                icst(256), op(Opcodes.IF_ICMPLT), op(Opcodes.ICONST_1), op(Opcodes.GOTO), op(Opcodes.ICONST_0),
                op(Opcodes.IRETURN));
        if (body.size() != 1) {
            return "its body is not the one this mod knows";
        }
        // isValid must still ask isOutsideBuildHeight (StellarCore's isValid does), or it keeps 0-255 on its own.
        MethodNode valid = method(world, "isValid", "func_175701_a", "(Lnet/minecraft/util/math/BlockPos;)Z");
        if (valid == null || find(valid, aload(0), aload(1),
                invoke(Opcodes.INVOKEVIRTUAL, "isOutsideBuildHeight", "func_189509_E", "(Lnet/minecraft/util/math/BlockPos;)Z")).size() != 1) {
            return "World.isValid does not call isOutsideBuildHeight";
        }
        AbstractInsnNode below = at(body.get(0), 4);
        AbstractInsnNode top = at(body.get(0), 6);
        compareWith(outside, below, worldInt(self(), "minY"));
        replace(outside, top, worldInt(self(), "maxY"));
        return "adapted";
    }

    /** Mixin marks a merged method with {@code @MixinMerged} (visible in current Mixin builds, invisible in older ones). */
    private static boolean mergedFromStellar(MethodNode m) {
        return mergedFromStellar(m.visibleAnnotations) || mergedFromStellar(m.invisibleAnnotations);
    }

    private static boolean mergedFromStellar(@Nullable List<AnnotationNode> annotations) {
        if (annotations == null) {
            return false;
        }
        for (AnnotationNode a : annotations) {
            if (MERGED.equals(a.desc) && a.values != null) {
                for (int i = 0; i + 1 < a.values.size(); i += 2) {
                    Object value = a.values.get(i + 1);
                    if ("mixin".equals(a.values.get(i)) && value instanceof String && ((String) value).startsWith(STELLAR)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int realCount(MethodNode m) {
        int n = 0;
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i.getOpcode() >= 0) {
                n++;
            }
        }
        return n;
    }
}
