package com.exiledradio.cavesandcliffsbackport;

import com.exiledradio.cavesandcliffsbackport.height.asm.ChangesTransformer;
import com.exiledradio.cavesandcliffsbackport.height.asm.HeightCorePlugin;
import com.exiledradio.cavesandcliffsbackport.height.asm.HeightTransformer;

import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The release-jar check behind {@code tools/release/boot-server.sh}: with {@code -Dcavesandcliffsbackport.loadPatched=true}
 * it loads and initializes every class this mod's coremods patch, once the server has started, so the JVM verifies
 * each one, and logs {@code PATCHCHECK ok <n>} or one {@code PATCHCHECK FAIL} line per class.
 *
 * <p>Why: Mojang's classes that Forge does not patch keep ProGuard's stack-map frames, which mark a local TOP once
 * vanilla stops reading it. A patch that loads such a local is a VerifyError only in the real game, and only once the
 * class is linked: the dev runs recompile Minecraft with javac and never show it, and extended height's own start-up
 * check loads its targets without linking them. Without the property this does nothing.
 */
public final class PatchCheck {

    static final String PROPERTY = CavesAndCliffsBackport.MOD_ID + ".loadPatched";

    private PatchCheck() {
    }

    /** Every class a transformer of this mod patches on the given side and can load in this game. */
    public static List<String> targets(boolean client) {
        List<String> out = new ArrayList<String>(ChangesTransformer.targets(client));
        if (HeightCorePlugin.active()) {
            ClassLoader loader = PatchCheck.class.getClassLoader();
            for (Map.Entry<String, Boolean> target : HeightTransformer.targets(client).entrySet()) {
                // Another mod's class (false) is checked only when that mod is installed.
                if (target.getValue() || loader.getResource(target.getKey().replace('.', '/') + ".class") != null) {
                    out.add(target.getKey());
                }
            }
        }
        return out;
    }

    /** Runs the check when the property asks for it. */
    static void runIfAsked() {
        if (!Boolean.getBoolean(PROPERTY)) {
            return;
        }
        boolean client = FMLCommonHandler.instance().getSide().isClient();
        List<String> targets = targets(client);
        int failed = 0;
        for (String target : targets) {
            try {
                Class.forName(target, true, PatchCheck.class.getClassLoader());
            } catch (Throwable t) {
                failed++;
                CavesAndCliffsBackport.LOGGER.error("PATCHCHECK FAIL {}: {}", target, t.toString().split("\n")[0], t);
            }
        }
        if (failed == 0) {
            CavesAndCliffsBackport.LOGGER.info("PATCHCHECK ok {} patched classes loaded and verified ({}{}).", targets.size(),
                    client ? "client" : "server", HeightCorePlugin.active() ? ", extended height on" : "");
        }
    }
}
