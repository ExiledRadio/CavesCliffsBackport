package com.exiledradio.cavesandcliffsbackport.deepslate.client;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.deepslate.ModdedOres;

import com.google.common.collect.ImmutableSet;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BakedQuadRetextured;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.color.ItemColors;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.vecmath.Matrix4f;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The client half of stream O1: how the deepslate forms of other mods' ores look.
 *
 * <h2>Textures</h2>
 * Made at every resource load (startup, F3 + T, a resource pack change) and served by an in-memory pack that sits
 * below every resource pack, so a pack can replace any of them by name:
 * <ul>
 *   <li>{@code cavesandcliffsbackport:block/deepslate_ores/<modid>/<texture path>}: one per texture the original's
 *       model uses. The texture minus the stone it was drawn on (vanilla's, in the current pack or the default one,
 *       or the mod's own stone: what most of its ore textures share) is drawn over deepslate. Overlay layers (mostly
 *       see-through textures, such as glowing ore bits) are used as they are; a texture with no stone to subtract
 *       is kept and listed in the log.</li>
 *   <li>{@code cavesandcliffsbackport:block/deepslate_ores/_form/<modid>/<name>}: the same for the model's main
 *       texture, for the plain cube model the blockstate names.</li>
 * </ul>
 *
 * <h2>Models</h2>
 * The pack also serves a blockstate and block and item models (a cube of the main texture). Once the models are
 * baked, each form's is replaced by the original's baked model with its quads retextured, so ores drawn in several
 * layers (an overlay layer, an emissive one) or not as a plain cube keep their shape and layers; the original's
 * color handlers tint it. Only known model names are asked for, never the whole registry (VintageFix bakes lazily).
 */
public final class ModdedOreClient {

    static final String ORES = "block/deepslate_ores/";
    private static final String TEXTURES = "textures/" + ORES;

    /** Generated files of the current load: location -> bytes. */
    private static final Map<ResourceLocation, byte[]> FILES = new ConcurrentHashMap<ResourceLocation, byte[]>();
    /** Original sprite name -> our sprite name, for the textures that were redrawn this load. */
    private static final Map<String, ResourceLocation> REDRAWN = new HashMap<String, ResourceLocation>();
    /** What the last load did, for the log and the client self-test. */
    private static final List<String> KEPT = new ArrayList<String>();
    private static final Map<Block, String> BAKED = new HashMap<Block, String>();

    private ModdedOreClient() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new ModdedOreClient());
        Minecraft mc = Minecraft.getMinecraft();
        try {
            List<IResourcePack> defaults = ReflectionHelper.getPrivateValue(Minecraft.class, mc, "defaultResourcePacks", "field_110449_ao");
            Pack pack = new Pack();
            // Kept for every later reload (F3 + T, a resource pack change), which rebuilds from this list...
            defaults.add(pack);
            // ...and added to the live manager now: the startup reload has happened, the model and texture loads have not.
            if (mc.getResourceManager() instanceof net.minecraft.client.resources.SimpleReloadableResourceManager) {
                ((net.minecraft.client.resources.SimpleReloadableResourceManager) mc.getResourceManager()).reloadResourcePack(pack);
            }
        } catch (RuntimeException e) {
            CavesAndCliffsBackport.LOGGER.warn("Modded ores: could not add the texture pack; deepslate forms of other mods' ores will have no textures.", e);
        }
        if ("moddedores".equals(System.getProperty(CavesAndCliffsBackport.MOD_ID + ".clienttest"))) {
            // A test build's client test (runClient -Pclienttest=moddedores), reached by name: a release has no test classes.
            try {
                Class.forName("com.exiledradio.cavesandcliffsbackport.selftest.ModdedOresClientSelfTest")
                        .getMethod("start", String.class).invoke(null, "moddedores");
            } catch (ReflectiveOperationException e) {
                CavesAndCliffsBackport.LOGGER.error("CLIENT SELFTEST DONE pass=0 fail=1: the test classes are not in this build.", e);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Model names

    static String formPath(ModdedOres.Form f) {
        return f.block().getRegistryName().getPath();
    }

    /** The form's path without "deepslate/": modid/name[_meta]. */
    private static String formTail(ModdedOres.Form f) {
        return formPath(f).substring(ModdedOres.PREFIX.length());
    }

    static ResourceLocation formTexture(ModdedOres.Form f) {
        return new ResourceLocation(CavesAndCliffsBackport.MOD_ID, ORES + "_form/" + formTail(f));
    }

    static ResourceLocation redrawnName(ResourceLocation original) {
        return new ResourceLocation(CavesAndCliffsBackport.MOD_ID, ORES + original.getNamespace() + "/" + original.getPath());
    }

    private static ResourceLocation file(ResourceLocation sprite) {
        return new ResourceLocation(sprite.getNamespace(), "textures/" + sprite.getPath() + ".png");
    }

    @SubscribeEvent
    public void registerModels(ModelRegistryEvent event) {
        for (ModdedOres.Form f : ModdedOres.forms()) {
            ModelLoader.setCustomModelResourceLocation(f.item(), 0, new ModelResourceLocation(f.block().getRegistryName(), "inventory"));
        }
    }

    @SubscribeEvent
    public void blockColors(ColorHandlerEvent.Block event) {
        final BlockColors colors = event.getBlockColors();
        for (final ModdedOres.Form f : ModdedOres.forms()) {
            colors.registerBlockColorHandler((state, world, pos, tint) -> colors.colorMultiplier(f.original, world, pos, tint), f.block());
        }
    }

    @SubscribeEvent
    public void itemColors(ColorHandlerEvent.Item event) {
        final ItemColors colors = event.getItemColors();
        for (final ModdedOres.Form f : ModdedOres.forms()) {
            colors.registerItemColorHandler((stack, tint) -> {
                ItemStack o = f.originalStack();
                return o.isEmpty() ? -1 : colors.colorMultiplier(o, tint);
            }, f.item());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Textures

    @SubscribeEvent
    public void stitch(TextureStitchEvent.Pre event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getMap() != mc.getTextureMapBlocks() || ModdedOres.forms().isEmpty()) {
            return;
        }
        FILES.clear();
        REDRAWN.clear();
        KEPT.clear();
        IResourceManager rm = mc.getResourceManager();
        BufferedImage deepslate = read(rm, new ResourceLocation(CavesAndCliffsBackport.MOD_ID, "textures/block/deepslate.png"));
        if (deepslate == null) {
            CavesAndCliffsBackport.LOGGER.warn("Modded ores: no deepslate texture; forms keep their stone textures.");
            return;
        }
        List<BufferedImage> stones = new ArrayList<BufferedImage>();
        ResourceLocation stone = new ResourceLocation("minecraft", "textures/blocks/stone.png");
        addIfPresent(stones, read(rm, stone));
        try (InputStream in = mc.getResourcePackRepository().rprDefaultResourcePack.getInputStream(stone)) {
            addIfPresent(stones, OrePainter.argb(ImageIO.read(in)));
        } catch (IOException | RuntimeException ignored) {
            // The current pack's stone is enough.
        }

        // Each form's textures, from the original's model.
        Map<ModdedOres.Form, List<ResourceLocation>> textures = new LinkedHashMap<ModdedOres.Form, List<ResourceLocation>>();
        Map<Block, Map<IBlockState, ModelResourceLocation>> variants = new HashMap<Block, Map<IBlockState, ModelResourceLocation>>();
        for (ModdedOres.Form f : ModdedOres.forms()) {
            List<ResourceLocation> list = new ArrayList<ResourceLocation>();
            try {
                Block b = f.original.getBlock();
                Map<IBlockState, ModelResourceLocation> v = variants.get(b);
                if (v == null) {
                    v = shapes(mc).getBlockStateMapper().getVariants(b);
                    variants.put(b, v);
                }
                ModelResourceLocation mrl = v.get(f.original);
                if (mrl != null) {
                    IModel model = ModelLoaderRegistry.getModelOrMissing(mrl);
                    for (ResourceLocation t : model.getTextures()) {
                        if (!t.getPath().startsWith("missingno") && !list.contains(t)) {
                            list.add(t);
                        }
                    }
                }
            } catch (Exception | LinkageError e) {
                CavesAndCliffsBackport.LOGGER.debug("Modded ores: no model for {}", f, e);
            }
            textures.put(f, list);
        }

        // Read every texture once, and sort them: layers, ores on vanilla stone, and the rest (by mod).
        Map<ResourceLocation, BufferedImage> images = new LinkedHashMap<ResourceLocation, BufferedImage>();
        for (List<ResourceLocation> list : textures.values()) {
            for (ResourceLocation t : list) {
                if (!images.containsKey(t)) {
                    images.put(t, read(rm, file(t)));
                }
            }
        }
        Map<ResourceLocation, List<int[]>> bases = new HashMap<ResourceLocation, List<int[]>>();
        Map<String, List<ResourceLocation>> unmatched = new HashMap<String, List<ResourceLocation>>();
        for (Map.Entry<ResourceLocation, BufferedImage> e : images.entrySet()) {
            BufferedImage img = e.getValue();
            if (img == null) {
                continue;
            }
            int[] px = OrePainter.firstFrame(img);
            if (OrePainter.clear(px) >= OrePainter.LAYER) {
                continue;
            }
            int w = img.getWidth(), fh = Math.min(OrePainter.frame(img), img.getHeight());
            List<int[]> mine = new ArrayList<int[]>();
            boolean matched = false;
            for (BufferedImage s : stones) {
                int[] b = OrePainter.scaled(s, w, fh);
                mine.add(b);
                matched |= OrePainter.match(px, b) >= OrePainter.MATCH;
            }
            if (matched) {
                bases.put(e.getKey(), mine);
            } else {
                String key = e.getKey().getNamespace() + "@" + w + "x" + fh;
                List<ResourceLocation> l = unmatched.get(key);
                if (l == null) {
                    l = new ArrayList<ResourceLocation>();
                    unmatched.put(key, l);
                }
                l.add(e.getKey());
            }
        }
        // The mod's own stone: what most of its other ore textures share.
        for (List<ResourceLocation> group : unmatched.values()) {
            List<int[]> frames = new ArrayList<int[]>();
            for (ResourceLocation t : group) {
                frames.add(OrePainter.firstFrame(images.get(t)));
            }
            int[] own = OrePainter.consensus(frames);
            for (ResourceLocation t : group) {
                if (own != null && OrePainter.match(OrePainter.firstFrame(images.get(t)), own) >= OrePainter.MATCH) {
                    bases.put(t, Collections.singletonList(own));
                } else {
                    KEPT.add(t.toString());
                }
            }
        }

        // Paint them, and register the sprites.
        TextureMap map = event.getMap();
        for (Map.Entry<ResourceLocation, List<int[]>> e : bases.entrySet()) {
            ResourceLocation ours = redrawnName(e.getKey());
            if (put(rm, file(ours), OrePainter.paint(images.get(e.getKey()), e.getValue(), deepslate), file(e.getKey()))) {
                REDRAWN.put(e.getKey().toString(), ours);
                map.registerSprite(ours);
            }
        }
        // Each form's own cube texture: its main texture redrawn (the first ore texture the model names), else deepslate.
        int plain = 0;
        for (Map.Entry<ModdedOres.Form, List<ResourceLocation>> e : textures.entrySet()) {
            ResourceLocation main = null;
            for (ResourceLocation t : e.getValue()) {
                if (bases.containsKey(t) && OrePainter.match(OrePainter.firstFrame(images.get(t)), bases.get(t).get(0)) < 0.9) {
                    main = t;
                    break;
                }
            }
            ResourceLocation formTex = formTexture(e.getKey());
            BufferedImage img = main != null ? OrePainter.paint(images.get(main), bases.get(main), deepslate) : deepslate;
            if (main == null) {
                plain++;
            }
            put(rm, file(formTex), img, main != null ? file(main) : null);
            map.registerSprite(formTex);
        }
        CavesAndCliffsBackport.LOGGER.info("Modded ores: drew {} deepslate ore textures for {} forms; {} with no ore texture found{}.",
                REDRAWN.size(), textures.size(), plain, KEPT.isEmpty() ? "" : "; kept as they are (no stone found under them): " + KEPT);
    }

    /**
     * The block model shapes, through the model manager: the first model load (at startup) runs before the block
     * renderer exists.
     */
    private static net.minecraft.client.renderer.BlockModelShapes shapes(Minecraft mc) {
        net.minecraft.client.renderer.block.model.ModelManager manager =
                ReflectionHelper.getPrivateValue(Minecraft.class, mc, "modelManager", "field_175617_aL");
        return manager.getBlockModelShapes();
    }

    private static void addIfPresent(List<BufferedImage> list, @Nullable BufferedImage img) {
        if (img != null) {
            list.add(img);
        }
    }

    @Nullable
    private static BufferedImage read(IResourceManager rm, ResourceLocation loc) {
        try (IResource r = rm.getResource(loc)) {
            BufferedImage img = ImageIO.read(r.getInputStream());
            return img == null ? null : OrePainter.argb(img);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Stores a painted texture, with the original's animation file when it has one. */
    private static boolean put(IResourceManager rm, ResourceLocation file, BufferedImage img, @Nullable ResourceLocation originalFile) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            FILES.put(file, out.toByteArray());
        } catch (IOException e) {
            return false;
        }
        if (originalFile != null) {
            try (IResource r = rm.getResource(new ResourceLocation(originalFile.getNamespace(), originalFile.getPath() + ".mcmeta"))) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                InputStream in = r.getInputStream();
                for (int n; (n = in.read(buf)) > 0; ) {
                    out.write(buf, 0, n);
                }
                FILES.put(new ResourceLocation(file.getNamespace(), file.getPath() + ".mcmeta"), out.toByteArray());
            } catch (IOException | RuntimeException ignored) {
                // Not animated.
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Models

    @SubscribeEvent(priority = EventPriority.LOW)
    public void bake(ModelBakeEvent event) {
        BAKED.clear();
        if (ModdedOres.forms().isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        TextureMap atlas = mc.getTextureMapBlocks();
        Map<String, TextureAtlasSprite> remap = new HashMap<String, TextureAtlasSprite>();
        for (Map.Entry<String, ResourceLocation> e : REDRAWN.entrySet()) {
            TextureAtlasSprite s = atlas.getAtlasSprite(e.getValue().toString());
            if (s != atlas.getMissingSprite()) {
                remap.put(e.getKey(), s);
            }
        }
        IBakedModel missing = event.getModelManager().getMissingModel();
        Map<Block, Map<IBlockState, ModelResourceLocation>> variants = new HashMap<Block, Map<IBlockState, ModelResourceLocation>>();
        int wrapped = 0;
        for (ModdedOres.Form f : ModdedOres.forms()) {
            try {
                Block b = f.original.getBlock();
                Map<IBlockState, ModelResourceLocation> v = variants.get(b);
                if (v == null) {
                    v = event.getModelManager().getBlockModelShapes().getBlockStateMapper().getVariants(b);
                    variants.put(b, v);
                }
                ModelResourceLocation mrl = v.get(f.original);
                // One known name at a time: never a walk over the registry's keys.
                IBakedModel base = mrl == null ? null : event.getModelRegistry().getObject(mrl);
                if (base == null || base == missing) {
                    BAKED.put(f.block(), "cube");
                    continue;
                }
                Retextured model = new Retextured(base, f.original, remap);
                event.getModelRegistry().putObject(new ModelResourceLocation(f.block().getRegistryName(), "normal"), model);
                event.getModelRegistry().putObject(new ModelResourceLocation(f.block().getRegistryName(), "inventory"), model);
                BAKED.put(f.block(), "retextured");
                wrapped++;
            } catch (Exception | LinkageError e) {
                BAKED.put(f.block(), "cube");
                CavesAndCliffsBackport.LOGGER.debug("Modded ores: kept the cube model for {}", f, e);
            }
        }
        CavesAndCliffsBackport.LOGGER.info("Modded ores: {} of {} forms drawn from their ore's own model.", wrapped, ModdedOres.forms().size());
    }

    /** For the client self-test: "retextured" or "cube" per form block, from the last model load. */
    public static Map<Block, String> baked() {
        return Collections.unmodifiableMap(BAKED);
    }

    /** For the client self-test: original sprite name -> our sprite name. */
    public static Map<String, ResourceLocation> redrawn() {
        return Collections.unmodifiableMap(REDRAWN);
    }

    public static ResourceLocation formSprite(ModdedOres.Form f) {
        return formTexture(f);
    }

    /** For the client self-test: textures kept as they are at the last load (no stone found under them). */
    public static List<String> kept() {
        return Collections.unmodifiableList(KEPT);
    }

    /** The original ore's baked model, its quads moved onto the redrawn sprites. */
    static final class Retextured implements IBakedModel {
        private final IBakedModel base;
        private final IBlockState original;
        private final Map<String, TextureAtlasSprite> remap;
        private final Map<BakedQuad, BakedQuad> quads = new ConcurrentHashMap<BakedQuad, BakedQuad>();

        Retextured(IBakedModel base, IBlockState original, Map<String, TextureAtlasSprite> remap) {
            this.base = base;
            this.original = original;
            this.remap = remap;
        }

        private BakedQuad quad(BakedQuad q) {
            BakedQuad done = this.quads.get(q);
            if (done != null) {
                return done;
            }
            TextureAtlasSprite s = q.getSprite() == null ? null : this.remap.get(q.getSprite().getIconName());
            done = s == null ? q : new BakedQuadRetextured(q, s);
            // A model that makes new quads on every call is not cached, so this never grows without end.
            if (this.quads.size() < 4096) {
                this.quads.put(q, done);
            }
            return done;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
            List<BakedQuad> in = this.base.getQuads(state == null ? null : this.original, side, rand);
            if (in.isEmpty()) {
                return in;
            }
            List<BakedQuad> out = new ArrayList<BakedQuad>(in.size());
            for (BakedQuad q : in) {
                out.add(quad(q));
            }
            return out;
        }

        @Override
        public boolean isAmbientOcclusion() {
            return this.base.isAmbientOcclusion();
        }

        @Override
        public boolean isAmbientOcclusion(IBlockState state) {
            return this.base.isAmbientOcclusion(this.original);
        }

        @Override
        public boolean isGui3d() {
            return this.base.isGui3d();
        }

        @Override
        public boolean isBuiltInRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleTexture() {
            TextureAtlasSprite p = this.base.getParticleTexture();
            TextureAtlasSprite s = p == null ? null : this.remap.get(p.getIconName());
            return s != null ? s : p;
        }

        @SuppressWarnings("deprecation")
        @Override
        public ItemCameraTransforms getItemCameraTransforms() {
            return this.base.getItemCameraTransforms();
        }

        @Override
        public ItemOverrideList getOverrides() {
            return ItemOverrideList.NONE;
        }

        @Override
        public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
            Pair<? extends IBakedModel, Matrix4f> p = this.base.handlePerspective(type);
            IBakedModel m = p.getLeft() == this.base ? this : new Retextured(p.getLeft(), this.original, this.remap);
            return Pair.of(m, p.getRight());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The pack

    /**
     * Serves the forms' blockstates and models (made from their names) and the textures painted at the last stitch.
     * A default pack: below every resource pack, never listed.
     */
    static final class Pack implements IResourcePack {

        @Nullable
        private static ModdedOres.Form form(String path, String folder, String ext) {
            if (!path.startsWith(folder) || !path.endsWith(ext)) {
                return null;
            }
            String name = path.substring(folder.length(), path.length() - ext.length());
            Block b = net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.getValue(new ResourceLocation(CavesAndCliffsBackport.MOD_ID, name));
            return b == null ? null : ModdedOres.form(b);
        }

        @Nullable
        private static String json(ResourceLocation loc) {
            if (!CavesAndCliffsBackport.MOD_ID.equals(loc.getNamespace())) {
                return null;
            }
            String p = loc.getPath();
            ModdedOres.Form f = form(p, "blockstates/", ".json");
            if (f != null) {
                String model = CavesAndCliffsBackport.MOD_ID + ":" + formPath(f);
                return "{\"variants\":{\"normal\":{\"model\":\"" + model + "\"},\"inventory\":{\"model\":\"" + model + "\"}}}";
            }
            f = form(p, "models/block/", ".json");
            if (f != null) {
                return "{\"parent\":\"block/cube_all\",\"textures\":{\"all\":\"" + formTexture(f) + "\"}}";
            }
            f = form(p, "models/item/", ".json");
            if (f != null) {
                return "{\"parent\":\"" + CavesAndCliffsBackport.MOD_ID + ":block/" + formPath(f) + "\"}";
            }
            return null;
        }

        @Override
        public InputStream getInputStream(ResourceLocation location) throws IOException {
            byte[] b = FILES.get(location);
            if (b == null) {
                String j = json(location);
                b = j == null ? null : j.getBytes(StandardCharsets.UTF_8);
            }
            if (b == null) {
                throw new FileNotFoundException(location.toString());
            }
            return new ByteArrayInputStream(b);
        }

        @Override
        public boolean resourceExists(ResourceLocation location) {
            if (!CavesAndCliffsBackport.MOD_ID.equals(location.getNamespace())) {
                return false;
            }
            if (location.getPath().startsWith(TEXTURES)) {
                return FILES.containsKey(location);
            }
            return location.getPath().contains(ModdedOres.PREFIX) && json(location) != null;
        }

        @Override
        public Set<String> getResourceDomains() {
            return ImmutableSet.of(CavesAndCliffsBackport.MOD_ID);
        }

        @Nullable
        @Override
        public <T extends IMetadataSection> T getPackMetadata(MetadataSerializer serializer, String section) {
            return null;
        }

        @Override
        public BufferedImage getPackImage() throws IOException {
            throw new FileNotFoundException("pack.png");
        }

        @Override
        public String getPackName() {
            return CavesAndCliffsBackport.MOD_ID + ":modded_ores";
        }
    }
}
