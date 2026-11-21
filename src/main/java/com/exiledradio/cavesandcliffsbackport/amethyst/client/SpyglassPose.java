package com.exiledradio.cavesandcliffsbackport.amethyst.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The spyglass's third-person pose, 1.17's {@code ArmPose.SPYGLASS}: the arm holding it comes up to the face and
 * follows the head, and the spyglass is drawn at the eye with its {@code head} transform, as 1.17's
 * {@code PlayerItemInHandLayer.renderArmWithSpyglass} does (the one in the hand is hidden meanwhile).
 *
 * <p>{@code ModelBiped.setRotationAngles} calls {@link #pre} at its head and {@link #post} before it returns
 * ({@code ChangesTransformer}). Armor models are {@code ModelBiped}s posed from the same entity, so they follow; the
 * player's sleeves copy the arm after this returns.</p>
 */
public final class SpyglassPose {

    private static final float ARM_DOWN = 1.9198622F;
    private static final float CROUCH = 0.2617994F;
    private static final float ARM_IN = 0.2617994F;

    private static ModelBiped stashedModel;
    private static Entity stashedEntity;
    /** The eye layer is drawing the spyglass, so it is shown, not hidden. */
    static boolean atEye;

    private final Set<RenderPlayer> layered = Collections.newSetFromMap(new WeakHashMap<RenderPlayer, Boolean>());

    public static void pre(ModelBiped model, Entity entity) {
        stashedModel = model;
        stashedEntity = entity;
    }

    /** Takes only the model: Mojang's frames may mark the entity unusable by the method's end; it comes from {@link #pre}. */
    public static void post(ModelBiped model) {
        Entity entity = stashedModel == model ? stashedEntity : null;
        stashedModel = null;
        stashedEntity = null;
        if (!(entity instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase living = (EntityLivingBase) entity;
        EnumHand hand = SpyglassClient.poseHand(living);
        if (hand == null) {
            return;
        }
        boolean right = side(living, hand) == EnumHandSide.RIGHT;
        ModelRenderer arm = right ? model.bipedRightArm : model.bipedLeftArm;
        // 1.17 poses the arm, then adds the crouch's 0.4 (which 1.12.2 had already added); the idle bob is skipped.
        arm.rotateAngleX = MathHelper.clamp(model.bipedHead.rotateAngleX - ARM_DOWN - (model.isSneak ? CROUCH : 0.0F), -2.4F, 3.3F)
                + (model.isSneak ? 0.4F : 0.0F);
        arm.rotateAngleY = model.bipedHead.rotateAngleY + (right ? -ARM_IN : ARM_IN);
        arm.rotateAngleZ = 0.0F;
    }

    static EnumHandSide side(EntityLivingBase e, EnumHand hand) {
        return hand == EnumHand.MAIN_HAND ? e.getPrimaryHand() : e.getPrimaryHand().opposite();
    }

    @SubscribeEvent
    public void renderPlayer(RenderPlayerEvent.Pre event) {
        RenderPlayer renderer = event.getRenderer();
        if (this.layered.add(renderer)) {
            renderer.addLayer(new AtEye(renderer));
        }
    }

    /** Draws the spyglass at the eye while it is held up, as 1.17's {@code renderArmWithSpyglass}. */
    static final class AtEye implements LayerRenderer<EntityLivingBase> {
        private final RenderLivingBase<?> renderer;

        AtEye(RenderLivingBase<?> renderer) {
            this.renderer = renderer;
        }

        @Override
        public void doRenderLayer(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks,
                float ageInTicks, float netHeadYaw, float headPitch, float scale) {
            EnumHand hand = SpyglassClient.poseHand(entity);
            if (hand == null || !(this.renderer.getMainModel() instanceof ModelBiped)) {
                return;
            }
            ItemStack stack = entity.getHeldItem(hand);
            ModelRenderer head = ((ModelBiped) this.renderer.getMainModel()).bipedHead;
            GlStateManager.pushMatrix();
            if (entity.isSneaking()) {
                GlStateManager.translate(0.0F, 0.2F, 0.0F);
            }
            float pitch = head.rotateAngleX;
            head.rotateAngleX = MathHelper.clamp(pitch, -(float) Math.PI / 6.0F, (float) Math.PI / 2.0F);
            head.postRender(0.0625F);
            head.rotateAngleX = pitch;
            GlStateManager.translate(0.0F, -0.25F, 0.0F);
            GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.scale(0.625F, -0.625F, -0.625F);
            atEye = true;
            try {
                Minecraft.getMinecraft().getItemRenderer().renderItem(entity, stack, ItemCameraTransforms.TransformType.HEAD);
            } finally {
                atEye = false;
                GlStateManager.popMatrix();
            }
        }

        @Override
        public boolean shouldCombineTextures() {
            return false;
        }
    }
}
