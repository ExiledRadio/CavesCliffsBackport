package com.exiledradio.cavesandcliffsbackport.babies.client.model;

import com.exiledradio.cavesandcliffsbackport.axolotl.EntityAxolotl;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 26.3's {@code BabyAxolotlModel}: seven keyframe animations, one running at a time as 26.3's
 * {@code Axolotl.tickBabyAnimations} picks it. 1.17's axolotl entity has no animation states, so this model keeps
 * them per axolotl on the client, advanced once a tick when the baby is drawn.
 */
public final class BabyAxolotlModel extends BabyModel<EntityAxolotl> {

    private static final float MAX_WALK_ANIMATION_SPEED = 15.0F;
    private static final float WALK_ANIMATION_SCALE_FACTOR = 30.0F;

    private final KeyframeAnimation walkAnimation;
    private final KeyframeAnimation walkUnderwaterAnimation;
    private final KeyframeAnimation swimAnimation;
    private final KeyframeAnimation idleOnGroundAnimation;
    private final KeyframeAnimation idleOnGroundUnderWaterAnimation;
    private final KeyframeAnimation idleUnderWaterAnimation;
    private final KeyframeAnimation playDeadAnimation;
    private final Map<EntityAxolotl, States> states = new WeakHashMap<EntityAxolotl, States>();

    public BabyAxolotlModel() {
        super(createBodyLayer());
        swimAnimation = BabyAxolotlAnimation.BABY_AXOLOTL_SWIM.bake(root);
        walkAnimation = BabyAxolotlAnimation.AXOLOTL_WALK_FLOOR.bake(root);
        walkUnderwaterAnimation = BabyAxolotlAnimation.WALK_FLOOR_UNDERWATER.bake(root);
        idleUnderWaterAnimation = BabyAxolotlAnimation.IDLE_UNDERWATER.bake(root);
        idleOnGroundUnderWaterAnimation = BabyAxolotlAnimation.IDLE_FLOOR_UNDERWATER.bake(root);
        idleOnGroundAnimation = BabyAxolotlAnimation.BABY_AXOLOTL_IDLE_FLOOR.bake(root);
        playDeadAnimation = BabyAxolotlAnimation.BABY_AXOLOTL_PLAY_DEAD.bake(root);
    }

    /** 26.3's {@code BabyAxolotlModel.createBodyLayer}. */
    static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition top = mesh.getRoot();
        PartDefinition root = top.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.0F, -0.75F, -2.75F, 4.0F, 2.0F, 6.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 12).addBox(0.0F, -1.75F, -2.75F, 0.0F, 3.0F, 5.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -1.25F, 1.75F));
        body.addOrReplaceChild("right_front_leg",
                CubeListBuilder.create().texOffs(20, 16).addBox(-3.0F, 0.0F, -0.5F, 3.0F, 0.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-2.0F, 0.25F, -1.25F));
        PartDefinition rightLeg = body.addOrReplaceChild("right_hind_leg", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-2.0F, 0.25F, 1.75F, 0.0F, 1.5708F, 1.5708F));
        rightLeg.addOrReplaceChild("right_leg_r1",
                CubeListBuilder.create().texOffs(20, 14).addBox(0.0F, 0.0F, -0.5F, 3.0F, 0.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -1.5708F, 0.0F, 1.5708F));
        body.addOrReplaceChild("left_front_leg",
                CubeListBuilder.create().texOffs(20, 13).addBox(0.0F, 0.0F, -0.5F, 3.0F, 0.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(2.0F, 0.25F, -1.25F));
        body.addOrReplaceChild("left_hind_leg",
                CubeListBuilder.create().texOffs(20, 14).addBox(0.0F, 0.0F, -0.5F, 3.0F, 0.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(2.0F, 0.25F, 1.75F));
        body.addOrReplaceChild("tail",
                CubeListBuilder.create().texOffs(10, 9).addBox(0.0F, -1.5F, -1.0F, 0.0F, 3.0F, 8.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -0.25F, 3.25F));
        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 8).addBox(-3.0F, -2.0F, -4.0F, 6.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.25F, -2.75F));
        head.addOrReplaceChild("left_gills",
                CubeListBuilder.create().texOffs(20, 8).addBox(0.0F, -3.5F, 0.0F, 3.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offset(3.0F, -0.5F, -2.0F));
        head.addOrReplaceChild("right_gills",
                CubeListBuilder.create().texOffs(20, 3).addBox(-3.0F, -3.5F, 0.0F, 3.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-3.0F, -0.5F, -2.0F));
        head.addOrReplaceChild("top_gills",
                CubeListBuilder.create().texOffs(20, 0).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, -2.0F, -2.0F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    protected void setupAnim(EntityAxolotl e, float pos, float speed, float ageInTicks, float yRot, float xRot) {
        States s = states.get(e);
        if (s == null) {
            s = new States();
            states.put(e, s);
        }
        s.tick(e);
        if (s.walk.isStarted()) {
            walkAnimation.applyWalk(pos, speed, MAX_WALK_ANIMATION_SPEED, WALK_ANIMATION_SCALE_FACTOR);
        }
        swimAnimation.apply(s.swim, ageInTicks);
        // As 26.3 has it: the underwater walk is timed by the walk's state.
        walkUnderwaterAnimation.apply(s.walk, ageInTicks);
        idleOnGroundAnimation.apply(s.idleOnGround, ageInTicks);
        idleUnderWaterAnimation.apply(s.idleUnderWater, ageInTicks);
        idleOnGroundUnderWaterAnimation.apply(s.idleUnderWaterOnGround, ageInTicks);
        playDeadAnimation.apply(s.playDead, ageInTicks);
    }

    /** 26.3's {@code Axolotl} animation states, for one baby. */
    static final class States {
        final AnimationState swim = new AnimationState();
        final AnimationState walk = new AnimationState();
        final AnimationState walkUnderWater = new AnimationState();
        final AnimationState idleUnderWater = new AnimationState();
        final AnimationState idleUnderWaterOnGround = new AnimationState();
        final AnimationState idleOnGround = new AnimationState();
        final AnimationState playDead = new AnimationState();
        private final AnimationState[] all = {swim, walk, walkUnderWater, idleUnderWater, idleUnderWaterOnGround, idleOnGround, playDead};
        private int lastTick = Integer.MIN_VALUE;

        /** {@code Axolotl.tickBabyAnimations}, once per entity tick. */
        void tick(EntityAxolotl e) {
            if (e.ticksExisted == lastTick) {
                return;
            }
            lastTick = e.ticksExisted;
            boolean inWater = e.isInWater();
            boolean onGround = e.onGround;
            boolean moving = e.limbSwingAmount > 1.0E-5F || e.rotationPitch != e.prevRotationPitch || e.rotationYaw != e.prevRotationYaw;
            if (e.isPlayingDead()) {
                solo(playDead, e.ticksExisted);
            } else if (moving) {
                if (inWater && !onGround) {
                    solo(swim, e.ticksExisted);
                } else if (!inWater && onGround) {
                    solo(walk, e.ticksExisted);
                } else {
                    solo(walkUnderWater, e.ticksExisted);
                }
            } else if (inWater && !onGround) {
                solo(idleUnderWater, e.ticksExisted);
            } else if (inWater) {
                solo(idleUnderWaterOnGround, e.ticksExisted);
            } else {
                solo(idleOnGround, e.ticksExisted);
            }
        }

        private void solo(AnimationState start, int tick) {
            for (AnimationState a : all) {
                if (a == start) {
                    a.startIfStopped(tick);
                } else {
                    a.stop();
                }
            }
        }
    }
}
