package com.exiledradio.cavesandcliffsbackport.height.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Hands each target class to its patch. Everything else passes through untouched. */
public final class HeightTransformer implements IClassTransformer {

    private final Map<String, ClassPatch> patches = new LinkedHashMap<String, ClassPatch>();

    public HeightTransformer() {
        for (ClassPatch p : all(HeightCorePlugin.client)) {
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
            // Celeritas's classes exist only with Celeritas installed.
            out.put(p.target, !p.target.startsWith("org.taumc."));
        }
        return Collections.unmodifiableMap(out);
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !HeightCorePlugin.active) {
            return basicClass;
        }
        ClassPatch patch = this.patches.get(transformedName);
        return patch == null ? basicClass : patch.transform(basicClass);
    }
}
