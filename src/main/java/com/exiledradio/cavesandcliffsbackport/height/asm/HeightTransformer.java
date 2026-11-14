package com.exiledradio.cavesandcliffsbackport.height.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Hands each target class to its patch. Everything else passes through untouched. */
public final class HeightTransformer implements IClassTransformer {

    private final Map<String, ClassPatch> patches = new LinkedHashMap<String, ClassPatch>();

    public HeightTransformer() {
        // The side from FML directly: FML builds the transformers before it hands the plugin its data.
        boolean client = net.minecraftforge.fml.relauncher.FMLLaunchHandler.side().isClient();
        HeightCorePlugin.client = client;
        for (ClassPatch p : all(client)) {
            this.patches.put(p.target, p);
        }
    }

    /** Every patch for one side; the self-test and the offline drift check use the same list. */
    public static ClassPatch[] all(boolean client) {
        ClassPatch[] common = {
                new WorldPatch(client),
                new WorldProviderPatch(),
                new ChunkPatch(client),
                new ChunkCachePatch(client),
                new AnvilChunkLoaderPatch(),
                new SPacketChunkDataPatch(),
                new PlayerChunkMapEntryPatch(),
                new SmallPatches.EntityPatch(),
                new SmallPatches.FallingBlockPatch(),
                new SmallPatches.NetHandlerPlayServerPatch(),
                new SmallPatches.SpawnerPatch(),
                new SmallPatches.WorldInfoPatch(),
                new SmallPatches.VintageFixChunkPatch(),
                new PortalPatches.BlockPortalPatch(),
                new PortalPatches.BlockPortalSizePatch(),
                new PortalPatches.TeleporterPatch(),
        };
        if (!client) {
            return common;
        }
        ClassPatch[] clientOnly = {
                new ClientPatches.NetHandlerPlayClientPatch(),
                new ClientPatches.WorldClientPatch(),
                new ClientPatches.ViewFrustumPatch(),
                new ClientPatches.RenderGlobalPatch(),
                new ClientPatches.EntityRendererPatch(),
                new ClientPatches.CeleritasManagerPatch(),
                new ClientPatches.CeleritasSectionPatch(),
                new ClientPatches.CeleritasSlicePatch(),
                new ClientPatches.EntityCullingAccessPatch(),
                new ClientPatches.NothiriumWorldUtilPatch(),
                new ClientPatches.NothiriumRenderChunkPatch(),
                new ClientPatches.NothiriumDirectionPatch(true),
                new ClientPatches.NothiriumDirectionPatch(false),
        };
        ClassPatch[] out = new ClassPatch[common.length + clientOnly.length];
        System.arraycopy(common, 0, out, 0, common.length);
        System.arraycopy(clientOnly, 0, out, common.length, clientOnly.length);
        return out;
    }

    /** The patched classes by name, for the start-up check that loads each one. */
    public static Map<String, Boolean> targets(boolean client) {
        Map<String, Boolean> out = new LinkedHashMap<String, Boolean>();
        for (ClassPatch p : all(client)) {
            if (p.target.startsWith("org.embeddedt.vintagefix.")) {
                // A mixin class: Mixin reads it (through the transformers) when it applies it; loading it directly
                // is an error.
                continue;
            }
            // Celeritas, Nothirium and VintageFix classes exist only with those mods installed.
            out.put(p.target, !p.target.startsWith("org.taumc.") && !p.target.startsWith("meldexun.")
                    && !p.target.startsWith("org.embeddedt.vintagefix."));
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * {@code -Dcavesandcliffsbackport.height.dump=<dir>}: each target class is written there as this transformer
     * receives it (after every earlier transformer, OptiFine's class replacements included), to read the code a
     * pack's other mods leave when a site is not found.
     */
    private static final String DUMP = System.getProperty("cavesandcliffsbackport.height.dump");

    private static void dump(String name, byte[] bytes) {
        try {
            java.io.File f = new java.io.File(DUMP, name + ".class");
            f.getParentFile().mkdirs();
            java.nio.file.Files.write(f.toPath(), bytes);
        } catch (java.io.IOException e) {
            HeightCorePlugin.LOG.warn("Could not dump {}: {}", name, e.toString());
        }
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !HeightCorePlugin.active) {
            return basicClass;
        }
        ClassPatch patch = this.patches.get(transformedName);
        if (patch != null && DUMP != null) {
            dump(transformedName, basicClass);
        }
        return patch == null ? basicClass : patch.transform(basicClass);
    }
}
