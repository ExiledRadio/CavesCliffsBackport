package com.exiledradio.cavesandcliffsbackport.height;

import com.exiledradio.cavesandcliffsbackport.height.asm.PatchReport;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import javax.annotation.Nullable;

/**
 * Valkyrie (Red Studio): without OptiFine its mixins overwrite the render frustum methods that extended height patches
 * ({@code height.asm.ValkyriePatches}). Mixin reads those mixin classes through the transformers when ViewFrustum and
 * RenderGlobal load, which {@code HeightContent}'s start-up check does; both must have been patched by then, or its
 * code would put every render chunk at y 0 to 383 (nothing drawn below 0).
 */
final class ValkyrieCheck {

    static final String LABEL_PREFIX = "Valkyrie";
    static final String MADE_FOR = "0.3-Dev-5";

    private ValkyrieCheck() {
    }

    static String version() {
        ModContainer mod = Loader.instance().getIndexedModList().get("valkyrie");
        return mod == null ? "?" : mod.getVersion();
    }

    /** Why extended height cannot run with the installed Valkyrie, or null (not installed, or patched). */
    @Nullable
    static String unpatched() {
        if (!Loader.isModLoaded("valkyrie") || optiFine()) {
            // With OptiFine, Valkyrie leaves its frustum mixins out (mixins.valkyrieOptifineIncompatible.json).
            return null;
        }
        for (String label : new String[] {"ValkyrieViewFrustumMixin", "ValkyrieRenderGlobalMixin"}) {
            if (!PatchReport.classPatched(label)) {
                return "Valkyrie " + version() + " is installed but its " + label.substring(LABEL_PREFIX.length())
                        + " was not patched (not read through the transformers); its render frustum would draw nothing "
                        + "below y 0. Remove Valkyrie";
            }
        }
        return null;
    }

    /** Valkyrie's own answer (Red Core's {@code OptiNotFine}), so the two agree; false if it cannot be asked. */
    private static boolean optiFine() {
        try {
            return (Boolean) Class.forName("dev.redstudio.redcore.utils.OptiNotFine")
                    .getMethod("isOptiFineInstalled").invoke(null);
        } catch (Throwable t) {
            return false;
        }
    }
}
