package com.exiledradio.cavesandcliffsbackport.amethyst.client;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.amethyst.AmethystContent;
import com.exiledradio.cavesandcliffsbackport.amethyst.ItemSpyglass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraft.util.SoundCategory;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverride;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

/**
 * Looking through the spyglass, client side: the field of view drops to a tenth (as Java's), the held
 * item is hidden, and the scope texture is drawn over the screen with black around it; the crosshair and
 * the rest of the HUD stay hidden while scoping in first person. As Java's, it is held as the 3D
 * {@code spyglass_in_hand} model and shown as the flat icon in the GUI, on the ground and in frames. Not in vanilla:
 * holding the Zoom with Spyglass key does the same with a spyglass anywhere in the inventory.
 */
public final class SpyglassClient {

    private static final ResourceLocation SCOPE = new ResourceLocation(CavesAndCliffsBackport.MOD_ID, "textures/misc/spyglass_scope.png");

    private SpyglassClient() {
    }

    /** Not in vanilla: held, it looks through a spyglass anywhere in the inventory. */
    static final KeyBinding ZOOM = new KeyBinding("key.cavesandcliffsbackport.spyglassZoom", Keyboard.KEY_Z,
            "key.categories.cavesandcliffsbackport");

    /** The zoom key is looking through a spyglass right now. */
    private static boolean keyZoom;

    public static void init() {
        ClientRegistry.registerKeyBinding(ZOOM);
        MinecraftForge.EVENT_BUS.register(new SpyglassClient());
        MinecraftForge.EVENT_BUS.register(new SpyglassPose());
    }

    /**
     * The hand whose spyglass this entity holds up to its eye, or null: using it, or (this player only) scoping
     * through the zoom key with one in hand, main hand first. Not when it is only in the inventory.
     */
    @Nullable
    static EnumHand poseHand(EntityLivingBase e) {
        if (e.isHandActive() && e.getActiveItemStack().getItem() instanceof ItemSpyglass) {
            return e.getActiveHand();
        }
        if (keyZoom && e == Minecraft.getMinecraft().player) {
            for (EnumHand hand : EnumHand.values()) {
                if (e.getHeldItem(hand).getItem() instanceof ItemSpyglass) {
                    return hand;
                }
            }
        }
        return null;
    }

    static boolean hasSpyglass(EntityPlayer p) {
        for (ItemStack stack : p.inventory.mainInventory) {
            if (stack.getItem() instanceof ItemSpyglass) {
                return true;
            }
        }
        for (ItemStack stack : p.inventory.offHandInventory) {
            if (stack.getItem() instanceof ItemSpyglass) {
                return true;
            }
        }
        return false;
    }

    private static boolean usingSpyglass(EntityPlayer p) {
        return p.isHandActive() && p.getActiveItemStack().getItem() instanceof ItemSpyglass;
    }

    /** Starts and ends the zoom key's scope, with the spyglass's sounds (heard by this player only). */
    private static void updateKeyZoom(Minecraft mc) {
        EntityPlayer p = mc.player;
        boolean want = p != null && ModConfig.amethyst.zoomKey && mc.currentScreen == null && ZOOM.isKeyDown()
                && !p.isSpectator() && !usingSpyglass(p) && hasSpyglass(p);
        if (want == keyZoom) {
            return;
        }
        keyZoom = want;
        if (p != null && mc.world != null) {
            mc.world.playSound(p.posX, p.posY, p.posZ, want ? AmethystContent.spyglassUse : AmethystContent.spyglassStop,
                    SoundCategory.PLAYERS, 1.0F, 1.0F, false);
        }
    }

    /** Slowed and not sprinting while the zoom key scopes, as while using the spyglass. */
    @SubscribeEvent
    public void input(InputUpdateEvent event) {
        if (keyZoom && event.getEntityPlayer() == Minecraft.getMinecraft().player) {
            event.getMovementInput().moveStrafe *= 0.2F;
            event.getMovementInput().moveForward *= 0.2F;
            event.getEntityPlayer().setSprinting(false);
        }
    }

    private static ModelResourceLocation inventory(String name) {
        return new ModelResourceLocation(CavesAndCliffsBackport.MOD_ID + ":" + name, "inventory");
    }

    @SubscribeEvent
    public void registerModels(ModelRegistryEvent event) {
        if (AmethystContent.spyglass != null) {
            ModelLoader.registerItemVariants(AmethystContent.spyglass,
                    new ResourceLocation(CavesAndCliffsBackport.MOD_ID, "spyglass"),
                    new ResourceLocation(CavesAndCliffsBackport.MOD_ID, "spyglass_in_hand"));
        }
    }

    @SubscribeEvent
    public void bakeModels(ModelBakeEvent event) {
        IBakedModel icon = event.getModelRegistry().getObject(inventory("spyglass"));
        IBakedModel held = event.getModelRegistry().getObject(inventory("spyglass_in_hand"));
        if (icon == null || held == null) {
            CavesAndCliffsBackport.LOGGER.warn("Spyglass model is incomplete; it is held as its icon.");
            return;
        }
        event.getModelRegistry().putObject(inventory("spyglass"), new ByPerspective(icon, new SeamlessSpyglassModel(held)));
    }

    /** The icon in the GUI, on the ground and in frames; the in-hand model held, worn and in first person. */
    static final class ByPerspective implements IBakedModel {
        final IBakedModel icon;
        final IBakedModel held;

        ByPerspective(IBakedModel icon, IBakedModel held) {
            this.icon = icon;
            this.held = held;
        }

        static boolean flat(ItemCameraTransforms.TransformType type) {
            return type == ItemCameraTransforms.TransformType.GUI || type == ItemCameraTransforms.TransformType.GROUND
                    || type == ItemCameraTransforms.TransformType.FIXED;
        }

        @Override
        public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
            return (flat(type) ? this.icon : this.held).handlePerspective(type);
        }

        private final IBakedModel notInHand = new NotInHand(this);

        /** While held up to the eye, the spyglass is drawn there by {@link SpyglassPose}, not in the hand. */
        private final ItemOverrideList overrides = new ItemOverrideList(Collections.<ItemOverride>emptyList()) {
            @Override
            public IBakedModel handleItemState(IBakedModel original, ItemStack stack, @Nullable World world,
                    @Nullable EntityLivingBase entity) {
                if (entity == null || SpyglassPose.atEye) {
                    return original;
                }
                EnumHand hand = poseHand(entity);
                return hand != null && entity.getHeldItem(hand) == stack ? ByPerspective.this.notInHand : original;
            }
        };

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
            return this.icon.getQuads(state, side, rand);
        }

        @Override
        public boolean isAmbientOcclusion() {
            return this.icon.isAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return this.icon.isGui3d();
        }

        @Override
        public boolean isBuiltInRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleTexture() {
            return this.icon.getParticleTexture();
        }

        @SuppressWarnings("deprecation")
        @Override
        public ItemCameraTransforms getItemCameraTransforms() {
            return this.icon.getItemCameraTransforms();
        }

        @Override
        public ItemOverrideList getOverrides() {
            return this.overrides;
        }
    }

    /** Draws nothing held in third person; otherwise as {@link ByPerspective}. */
    static final class NotInHand implements IBakedModel {
        private final ByPerspective model;

        NotInHand(ByPerspective model) {
            this.model = model;
        }

        @Override
        public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
            if (type == ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND
                    || type == ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND) {
                return Pair.of(this, null);
            }
            return this.model.handlePerspective(type);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
            return Collections.emptyList();
        }

        @Override
        public boolean isAmbientOcclusion() {
            return false;
        }

        @Override
        public boolean isGui3d() {
            return this.model.isGui3d();
        }

        @Override
        public boolean isBuiltInRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleTexture() {
            return this.model.getParticleTexture();
        }

        @SuppressWarnings("deprecation")
        @Override
        public ItemCameraTransforms getItemCameraTransforms() {
            return ItemCameraTransforms.DEFAULT;
        }

        @Override
        public ItemOverrideList getOverrides() {
            return ItemOverrideList.NONE;
        }
    }

    /** Client ticks the zoom-out takes to settle: 1.12 halves the gap to the target field of view each tick. */
    private static final int SETTLE_TICKS = 20;

    private int sinceScoping = SETTLE_TICKS;

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            updateKeyZoom(Minecraft.getMinecraft());
            this.sinceScoping = scoping() ? 0 : Math.min(this.sinceScoping + 1, SETTLE_TICKS);
        }
    }

    /**
     * 1.12 redoes its search for visible chunk sections only when the camera moves or turns, and that search culls by
     * the frustum of the moment, so a zoomed-in search keeps drawing only the narrow view after the field of view
     * widens again. While the field of view changes for the spyglass the search is redone every frame; 1.18+ applies
     * the frustum every frame apart from the search, so it never has this.
     */
    @SubscribeEvent
    public void renderTick(TickEvent.RenderTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.START || this.sinceScoping >= SETTLE_TICKS || mc.renderGlobal == null
                || dirtyField == null) {
            return;
        }
        try {
            dirtyField.setBoolean(mc.renderGlobal, true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            CavesAndCliffsBackport.LOGGER.warn("Could not refresh the visible chunks after zooming with the spyglass.", e);
            dirtyField = null;
        }
    }

    /** {@code RenderGlobal.displayListEntitiesDirty}; OptiFine keeps it, and renderers that replace the search ignore it. */
    @Nullable
    private static Field dirtyField = findDirtyField();

    @Nullable
    private static Field findDirtyField() {
        try {
            return ReflectionHelper.findField(RenderGlobal.class, "displayListEntitiesDirty", "field_147595_R");
        } catch (RuntimeException e) {
            CavesAndCliffsBackport.LOGGER.warn("RenderGlobal has no displayListEntitiesDirty; zooming with the spyglass will not refresh the visible chunks.");
            return null;
        }
    }

    static boolean scoping() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.player;
        return p != null && mc.gameSettings.thirdPersonView == 0 && (keyZoom || usingSpyglass(p));
    }

    @SubscribeEvent
    public void fov(FOVUpdateEvent event) {
        if (scoping()) {
            event.setNewfov(0.1F);
        }
    }

    @SubscribeEvent
    public void hand(RenderHandEvent event) {
        if (scoping()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void overlay(RenderGameOverlayEvent.Pre event) {
        if (!scoping()) {
            return;
        }
        // 1.17 draws the spyglass instead of the pumpkin blur and the powder snow frost (both on HELMET here).
        if (event.getType() == RenderGameOverlayEvent.ElementType.CROSSHAIRS || event.getType() == RenderGameOverlayEvent.ElementType.HELMET) {
            event.setCanceled(true);
            return;
        }
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        ScaledResolution res = event.getResolution();
        int w = res.getScaledWidth(), h = res.getScaledHeight();
        int size = Math.min(w, h);
        int x = (w - size) / 2, y = (h - size) / 2;
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.color(1, 1, 1, 1);
        Minecraft.getMinecraft().getTextureManager().bindTexture(SCOPE);
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, size, size, size, size);
        Gui.drawRect(0, 0, w, y, 0xFF000000);
        Gui.drawRect(0, y + size, w, h, 0xFF000000);
        Gui.drawRect(0, y, x, y + size, 0xFF000000);
        Gui.drawRect(x + size, y, w, y + size, 0xFF000000);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.color(1, 1, 1, 1);
    }
}
