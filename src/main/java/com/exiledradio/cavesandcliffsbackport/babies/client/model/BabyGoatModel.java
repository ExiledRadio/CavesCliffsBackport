package com.exiledradio.cavesandcliffsbackport.babies.client.model;

import com.exiledradio.cavesandcliffsbackport.goat.EntityGoat;

/**
 * 26.3's {@code BabyGoatModel}: {@code QuadrupedModel.setupAnim} and {@code GoatModel}'s horns (the baby has small
 * ones, shown as the goat has them) and ram; out of a ram the head is held at 22.5 degrees.
 */
public final class BabyGoatModel extends BabyModel<EntityGoat> {

    private final ModelPart head;
    private final ModelPart leftHorn;
    private final ModelPart rightHorn;
    private final ModelPart rightHindLeg;
    private final ModelPart leftHindLeg;
    private final ModelPart rightFrontLeg;
    private final ModelPart leftFrontLeg;

    public BabyGoatModel() {
        super(createBodyLayer());
        head = root.getChild("head");
        leftHorn = head.getChild("left_horn");
        rightHorn = head.getChild("right_horn");
        rightHindLeg = root.getChild("right_hind_leg");
        leftHindLeg = root.getChild("left_hind_leg");
        rightFrontLeg = root.getChild("right_front_leg");
        leftFrontLeg = root.getChild("left_front_leg");
    }

    /** 26.3's {@code BabyGoatModel.createBodyLayer}. */
    static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("left_hind_leg", CubeListBuilder.create().texOffs(29, 12).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 5.0F, 2.0F),
                PartPose.offset(1.5F, 19.5F, 3.0F));
        root.addOrReplaceChild("right_hind_leg", CubeListBuilder.create().texOffs(21, 12).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 5.0F, 2.0F),
                PartPose.offset(-1.5F, 19.5F, 3.0F));
        root.addOrReplaceChild("right_front_leg", CubeListBuilder.create().texOffs(21, 5).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 5.0F, 2.0F),
                PartPose.offset(-1.5F, 19.5F, -2.0F));
        root.addOrReplaceChild("left_front_leg", CubeListBuilder.create().texOffs(29, 5).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 5.0F, 2.0F),
                PartPose.offset(1.5F, 19.5F, -2.0F));
        root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 10).addBox(-3.0F, -2.3F, -4.5F, 6.0F, 5.0F, 9.0F)
                        .texOffs(0, 24).addBox(-2.5F, -2.2F, -4.0F, 5.0F, 4.0F, 8.0F),
                PartPose.offset(0.0F, 17.8F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -3.8126F, -5.1548F, 4.0F, 4.0F, 6.0F),
                PartPose.offsetAndRotation(0.0F, 15.5F, -3.0F, 0.4363F, 0.0F, 0.0F));
        head.addOrReplaceChild("right_horn",
                CubeListBuilder.create().texOffs(24, 0).mirror().addBox(0.0F, -4.5F, 0.0F, 1.0F, 2.0F, 1.0F).mirror(false),
                PartPose.offsetAndRotation(-1.5F, -1.5F, -1.0F, (float) (-Math.PI / 8), 0.0F, 0.0F));
        head.addOrReplaceChild("left_horn",
                CubeListBuilder.create().texOffs(24, 0).mirror().addBox(2.0F, -4.5F, 0.0F, 1.0F, 2.0F, 1.0F).mirror(false),
                PartPose.offsetAndRotation(-1.5F, -1.5F, -1.0F, (float) (-Math.PI / 8), 0.0F, 0.0F));
        head.addOrReplaceChild("right_ear",
                CubeListBuilder.create().texOffs(0, 12).mirror().addBox(-2.0F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F).mirror(false),
                PartPose.offsetAndRotation(-1.7F, -2.3126F, 0.1452F, 0.0F, -0.5236F, 0.0F));
        head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(0, 12).addBox(0.0F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F),
                PartPose.offsetAndRotation(1.7F, -2.3126F, 0.1452F, 0.0F, 0.5236F, 0.0F));
        head.addOrReplaceChild("HeadMain", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -2.5F, -4.0F, 4.0F, 4.0F, 6.0F),
                PartPose.offset(0.0F, -1.3126F, -1.1548F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    protected void setupAnim(EntityGoat goat, float pos, float speed, float ageInTicks, float yRot, float xRot) {
        quadruped(head, rightHindLeg, leftHindLeg, rightFrontLeg, leftFrontLeg, pos, speed, yRot, xRot);
        // GoatModel.setupAnim
        leftHorn.visible = goat.hasLeftHorn();
        rightHorn.visible = goat.hasRightHorn();
        float ramming = goat.getRammingXHeadRot();
        if (ramming != 0.0F) {
            head.xRot = ramming;
        } else {
            // BabyGoatModel.setupAnim
            head.xRot = (float) (Math.PI / 8);
        }
    }
}
