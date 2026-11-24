package com.exiledradio.cavesandcliffsbackport.deepslate.client;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.deepslate.ModdedOres;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraft.block.properties.IProperty;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Shader block ids (OptiFine's shaders) for our blocks: each gets the id a shader pack gives the vanilla block it is,
 * and each deepslate form of another mod's ore the id its original ore gets.
 *
 * <p>Shaders tell blocks apart by the ids a pack gives them in {@code shaders/block.properties} (Complementary's
 * glowing ores, for one). A modern pack names our blocks by their vanilla names ({@code deepslate_iron_ore}), which
 * 1.12 has no blocks for, and no pack names a deepslate form. Besides the pack's file, OptiFine reads
 * {@code <modid>:shaders/block.properties} from every mod's resources each time it loads the ids (a shader pack or
 * option change, a resource reload). Ours is made at that moment and served by a pack below every resource pack: the
 * shader pack's own file (and other mods' files) read with OptiFine's own macro processor and block parser; every
 * entry naming a vanilla block 1.12 lacks is repeated for our block of that name, and every form whose original an
 * entry matches is listed under that entry's id - the first one, as OptiFine takes the first match. Without OptiFine,
 * or with no shader pack, nothing asks for the file and nothing here runs.
 */
public final class ShaderBlockIds {

    static final ResourceLocation FILE = new ResourceLocation(CavesAndCliffsBackport.MOD_ID, "shaders/block.properties");
    private static final String PACK_FILE = "/shaders/block.properties";

    /** What the last build listed, for the client self-test and the log: form registry name -> shader block id. */
    static final Map<String, Integer> LAST = new LinkedHashMap<String, Integer>();
    /** Our blocks' entries (by their vanilla names) in the last build. */
    static final java.util.Set<String> NAMED = new java.util.LinkedHashSet<String>();

    private ShaderBlockIds() {
    }

    /** Adds the pack that serves the file. */
    public static void init() {
        Minecraft mc = Minecraft.getMinecraft();
        try {
            List<net.minecraft.client.resources.IResourcePack> defaults = net.minecraftforge.fml.relauncher.ReflectionHelper
                    .getPrivateValue(Minecraft.class, mc, "defaultResourcePacks", "field_110449_ao");
            Pack pack = new Pack();
            defaults.add(pack);
            if (mc.getResourceManager() instanceof net.minecraft.client.resources.SimpleReloadableResourceManager) {
                ((net.minecraft.client.resources.SimpleReloadableResourceManager) mc.getResourceManager()).reloadResourcePack(pack);
            }
        } catch (RuntimeException e) {
            CavesAndCliffsBackport.LOGGER.warn("Shader block ids: could not add the pack; shader packs will not know our blocks.", e);
        }
    }

    /** Serves {@link #FILE}, made when it is asked for. */
    static final class Pack implements net.minecraft.client.resources.IResourcePack {
        @Override
        public InputStream getInputStream(ResourceLocation location) throws java.io.IOException {
            if (!FILE.equals(location)) {
                throw new java.io.FileNotFoundException(location.toString());
            }
            return new java.io.ByteArrayInputStream(bytes());
        }

        @Override
        public boolean resourceExists(ResourceLocation location) {
            return FILE.equals(location);
        }

        @Override
        public java.util.Set<String> getResourceDomains() {
            return java.util.Collections.singleton(CavesAndCliffsBackport.MOD_ID);
        }

        @Nullable
        @Override
        public <T extends net.minecraft.client.resources.data.IMetadataSection> T getPackMetadata(
                net.minecraft.client.resources.data.MetadataSerializer serializer, String section) {
            return null;
        }

        @Override
        public java.awt.image.BufferedImage getPackImage() throws java.io.IOException {
            throw new java.io.FileNotFoundException("pack.png");
        }

        @Override
        public String getPackName() {
            return CavesAndCliffsBackport.MOD_ID + ":shader_block_ids";
        }
    }

    /** @return the file's text: possibly empty, never null */
    public static String build() {
        LAST.clear();
        NAMED.clear();
        try {
            List<Properties> files = new ArrayList<Properties>();
            Class<?> shaders = Class.forName("net.optifine.shaders.Shaders");
            Object pack = shaders.getMethod("getShaderPack").invoke(null);
            if (pack != null) {
                Method res = Class.forName("net.optifine.shaders.IShaderPack").getMethod("getResourceAsStream", String.class);
                addIfPresent(files, (InputStream) res.invoke(pack, PACK_FILE), PACK_FILE);
            }
            // Other mods' files come after the pack's, in mod order, as OptiFine reads them.
            for (ModContainer mod : Loader.instance().getActiveModList()) {
                if (CavesAndCliffsBackport.MOD_ID.equals(mod.getModId())) {
                    continue;
                }
                ResourceLocation loc = new ResourceLocation(mod.getModId(), "shaders/block.properties");
                try (IResource r = Minecraft.getMinecraft().getResourceManager().getResource(loc)) {
                    addIfPresent(files, r.getInputStream(), loc.toString());
                } catch (Exception ignored) {
                    // No file from this mod.
                }
            }
            if (files.isEmpty()) {
                return "";
            }
            Object parser = Class.forName("net.optifine.config.ConnectedParser").getConstructor(String.class).newInstance("Shaders");
            Method parse = parser.getClass().getMethod("parseMatchBlocks", String.class);
            Method matches = Class.forName("net.optifine.config.MatchBlock").getMethod("matches", int.class, int.class);

            // Each form's original, as OptiFine sees it when it draws: the block's number and the state's metadata.
            List<ModdedOres.Form> forms = new ArrayList<ModdedOres.Form>();
            for (ModdedOres.Form f : ModdedOres.forms()) {
                if (f.block() != null && f.block().getRegistryName() != null) {
                    forms.add(f);
                }
            }
            int[] ids = new int[forms.size()], metas = new int[forms.size()];
            for (int i = 0; i < forms.size(); i++) {
                Block b = forms.get(i).original.getBlock();
                ids[i] = Block.getIdFromBlock(b);
                metas[i] = b.getMetaFromState(forms.get(i).original);
            }
            Map<Integer, StringBuilder> out = new LinkedHashMap<Integer, StringBuilder>();
            for (Properties p : files) {
                for (Object k : p.keySet()) {
                    String key = String.valueOf(k);
                    if (!key.startsWith("block.")) {
                        continue;
                    }
                    int alias;
                    try {
                        alias = Integer.parseInt(key.substring("block.".length()).trim());
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    if (alias < 0) {
                        continue;
                    }
                    Object[] blocks = (Object[]) parse.invoke(parser, String.valueOf(p.get(k)));
                    if (blocks == null) {
                        continue;
                    }
                    for (String token : String.valueOf(p.get(k)).trim().split("\\s+")) {
                        String ours = ours(token);
                        if (ours != null && NAMED.add(ours)) {
                            StringBuilder line = out.get(alias);
                            if (line == null) {
                                out.put(alias, line = new StringBuilder());
                            }
                            line.append(line.length() == 0 ? "" : " ").append(ours);
                        }
                    }
                    for (int i = 0; i < forms.size(); i++) {
                        String name = forms.get(i).block().getRegistryName().toString();
                        if (LAST.containsKey(name)) {
                            continue;
                        }
                        for (Object mb : blocks) {
                            if (mb != null && (Boolean) matches.invoke(mb, ids[i], metas[i])) {
                                LAST.put(name, alias);
                                StringBuilder line = out.get(alias);
                                if (line == null) {
                                    out.put(alias, line = new StringBuilder());
                                }
                                line.append(line.length() == 0 ? "" : " ").append(name);
                                break;
                            }
                        }
                    }
                }
            }
            StringBuilder text = new StringBuilder("# Made by Caves & Cliffs Backport: its blocks get the ids of the vanilla blocks they are,"
                    + " and each deepslate form of a modded ore its original's.\n");
            for (Map.Entry<Integer, StringBuilder> e : out.entrySet()) {
                text.append("block.").append(e.getKey()).append('=').append(e.getValue()).append('\n');
            }
            CavesAndCliffsBackport.LOGGER.info("Shader block ids: {} entries for our blocks by their vanilla names; {} of {} deepslate forms of modded ores.",
                    NAMED.size(), LAST.size(), forms.size());
            return text.toString();
        } catch (ClassNotFoundException e) {
            return "";
        } catch (Exception | LinkageError e) {
            CavesAndCliffsBackport.LOGGER.warn("Modded ores: could not give deepslate forms their ores' shader block ids.", e);
            return "";
        }
    }

    /**
     * A pack's entry for a vanilla block 1.12 doesn't have ({@code deepslate_iron_ore}, {@code minecraft:tuff}) as the
     * entry for our block of that name, its properties kept where our block has them; null when it is no such entry.
     */
    @Nullable
    static String ours(String token) {
        if (token.isEmpty() || Character.isDigit(token.charAt(0))) {
            return null;
        }
        String[] parts = token.split(":");
        int first = parts.length > 1 && !parts[1].contains("=") ? 2 : 1;
        String ns = first == 2 ? parts[0] : "minecraft";
        String name = parts[first - 1];
        if (!"minecraft".equals(ns) || name.contains("=")) {
            return null;
        }
        if (ForgeRegistries.BLOCKS.containsKey(new ResourceLocation("minecraft", name))) {
            return null;
        }
        ResourceLocation ourName = new ResourceLocation(CavesAndCliffsBackport.MOD_ID, name);
        Block block = ForgeRegistries.BLOCKS.containsKey(ourName) ? ForgeRegistries.BLOCKS.getValue(ourName) : null;
        if (block == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(ourName.toString());
        for (int i = first; i < parts.length; i++) {
            String[] kv = parts[i].split("=", 2);
            IProperty<?> prop = kv.length == 2 ? block.getBlockState().getProperty(kv[0]) : null;
            if (prop == null) {
                return null;
            }
            for (String v : kv[1].split(",")) {
                if (!prop.parseValue(v).isPresent()) {
                    return null;
                }
            }
            out.append(':').append(parts[i]);
        }
        return out.toString();
    }

    /**
     * Reads a block.properties as OptiFine does: macros and {@code #if}s first, then keys in file order (OptiFine's
     * ordered properties), as the first matching entry wins.
     */
    private static void addIfPresent(List<Properties> files, @Nullable InputStream in, String path) throws Exception {
        if (in == null) {
            return;
        }
        try {
            InputStream processed = (InputStream) Class.forName("net.optifine.shaders.config.MacroProcessor")
                    .getMethod("process", InputStream.class, String.class).invoke(null, in, path);
            Properties p = ordered();
            p.load(processed);
            processed.close();
            files.add(p);
        } finally {
            in.close();
        }
    }

    private static Properties ordered() {
        try {
            return (Properties) Class.forName("net.optifine.util.PropertiesOrdered").getConstructor().newInstance();
        } catch (ReflectiveOperationException | ClassCastException e) {
            return new Properties();
        }
    }

    static byte[] bytes() {
        return build().getBytes(StandardCharsets.ISO_8859_1);
    }
}
