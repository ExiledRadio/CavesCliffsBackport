package com.exiledradio.cavesandcliffsbackport.axolotl.client;

import com.exiledradio.cavesandcliffsbackport.axolotl.EntityAxolotl;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

/**
 * 1.17's {@code AxolotlModel}: the same boxes, pivots and five poses (lying still, crawling, hovering in
 * water, swimming, playing dead).
 *
 * <p>Every pose eases each part a twentieth of the way toward its goal per frame, starting from where the
 * part was last drawn for this axolotl, so the model keeps its last rotations on the entity
 * ({@link EntityAxolotl#modelRotations}) as 1.17 does.
 */
public class ModelAxolotl extends ModelBase {

    private final ModelRenderer body, head, topGills, leftGills, rightGills;
    private final ModelRenderer leftHindLeg, rightHindLeg, leftFrontLeg, rightFrontLeg, tail;
    /** In the order they are saved on the entity. */
    private final ModelRenderer[] parts;

    public ModelAxolotl() {
        this.textureWidth = 64;
        this.textureHeight = 64;
        this.body = new ModelRenderer(this, 0, 11);
        this.body.addBox(-4.0F, -2.0F, -9.0F, 8, 4, 10);
        this.body.setTextureOffset(2, 17).addBox(0.0F, -3.0F, -8.0F, 0, 5, 9);
        this.body.setRotationPoint(0.0F, 20.0F, 5.0F);

        this.head = part(0, 1, -4.0F, -3.0F, -5.0F, 8, 5, 5, 0.0F, 0.0F, -9.0F);
        this.topGills = part(3, 37, -4.0F, -3.0F, 0.0F, 8, 3, 0, 0.0F, -3.0F, -1.0F);
        this.leftGills = part(0, 40, -3.0F, -5.0F, 0.0F, 3, 7, 0, -4.0F, 0.0F, -1.0F);
        this.rightGills = part(11, 40, 0.0F, -5.0F, 0.0F, 3, 7, 0, 4.0F, 0.0F, -1.0F);
        this.head.addChild(this.topGills);
        this.head.addChild(this.leftGills);
        this.head.addChild(this.rightGills);

        this.rightHindLeg = part(2, 13, -2.0F, 0.0F, 0.0F, 3, 5, 0, -3.5F, 1.0F, -1.0F);
        this.leftHindLeg = part(2, 13, -1.0F, 0.0F, 0.0F, 3, 5, 0, 3.5F, 1.0F, -1.0F);
        this.rightFrontLeg = part(2, 13, -2.0F, 0.0F, 0.0F, 3, 5, 0, -3.5F, 1.0F, -8.0F);
        this.leftFrontLeg = part(2, 13, -1.0F, 0.0F, 0.0F, 3, 5, 0, 3.5F, 1.0F, -8.0F);
        this.tail = new ModelRenderer(this, 2, 19);
        this.tail.addBox(0.0F, -3.0F, 0.0F, 0, 5, 12);
        this.tail.setRotationPoint(0.0F, 0.0F, 1.0F);

        this.body.addChild(this.head);
        this.body.addChild(this.rightHindLeg);
        this.body.addChild(this.leftHindLeg);
        this.body.addChild(this.rightFrontLeg);
        this.body.addChild(this.leftFrontLeg);
        this.body.addChild(this.tail);
        this.parts = new ModelRenderer[] {this.body, this.head, this.rightHindLeg, this.leftHindLeg, this.rightFrontLeg,
                this.leftFrontLeg, this.tail, this.topGills, this.leftGills, this.rightGills};
    }

    /** A part with one box, grown by 1.17's 0.001 so flat parts do not fight the body's faces. */
    private ModelRenderer part(int u, int v, float x, float y, float z, int w, int h, int d, float px, float py, float pz) {
        ModelRenderer r = new ModelRenderer(this, u, v);
        r.addBox(x, y, z, w, h, d, 0.001F);
        r.setRotationPoint(px, py, pz);
        return r;
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        this.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, entity);
        if (this.isChild) {
            // 1.17's baby: the whole model at half size, standing on the same ground.
            GlStateManager.pushMatrix();
            GlStateManager.scale(0.5F, 0.5F, 0.5F);
            GlStateManager.translate(0.0F, 24.0F * scale, 0.0F);
            this.body.render(scale);
            GlStateManager.popMatrix();
        } else {
            this.body.render(scale);
        }
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale, Entity entity) {
        EntityAxolotl axolotl = (EntityAxolotl) entity;
        this.body.rotationPointX = 0.0F;
        this.head.rotationPointY = 0.0F;
        this.body.rotationPointY = 20.0F;
        if (axolotl.modelRotationsSet) {
            for (int i = 0; i < this.parts.length; i++) {
                this.parts[i].rotateAngleX = axolotl.modelRotations[i * 3];
                this.parts[i].rotateAngleY = axolotl.modelRotations[i * 3 + 1];
                this.parts[i].rotateAngleZ = axolotl.modelRotations[i * 3 + 2];
            }
        } else {
            for (ModelRenderer part : this.parts) {
                set(part, 0.0F, 0.0F, 0.0F);
            }
            set(this.body, headPitch * 0.017453292F, netHeadYaw * 0.017453292F, 0.0F);
        }
        boolean save = true;
        if (axolotl.isPlayingDead()) {
            this.playDead(netHeadYaw);
        } else {
            boolean moving = axolotl.motionX * axolotl.motionX + axolotl.motionZ * axolotl.motionZ > 1.0E-7D
                    || axolotl.rotationPitch != axolotl.prevRotationPitch || axolotl.rotationYaw != axolotl.prevRotationYaw
                    || axolotl.lastTickPosX != axolotl.posX || axolotl.lastTickPosZ != axolotl.posZ;
            if (axolotl.isInWater()) {
                if (moving) {
                    this.swimming(ageInTicks, headPitch);
                } else {
                    this.hovering(ageInTicks);
                }
            } else if (axolotl.onGround) {
                if (moving) {
                    this.crawling(ageInTicks, netHeadYaw);
                } else {
                    this.lyingStill(ageInTicks, netHeadYaw);
                }
            }
        }
        if (save) {
            for (int i = 0; i < this.parts.length; i++) {
                axolotl.modelRotations[i * 3] = this.parts[i].rotateAngleX;
                axolotl.modelRotations[i * 3 + 1] = this.parts[i].rotateAngleY;
                axolotl.modelRotations[i * 3 + 2] = this.parts[i].rotateAngleZ;
            }
            axolotl.modelRotationsSet = true;
        }
    }

    private static void set(ModelRenderer part, float x, float y, float z) {
        part.rotateAngleX = x;
        part.rotateAngleY = y;
        part.rotateAngleZ = z;
    }

    private static float lerp(float from, float to) {
        return lerp(0.05F, from, to);
    }

    /** 1.17's {@code Mth.rotLerp}, used as it is there (on radians). */
    private static float lerp(float amount, float from, float to) {
        return from + amount * MathHelper.wrapDegrees(to - from);
    }

    private static void lerpPart(ModelRenderer part, float x, float y, float z) {
        set(part, lerp(part.rotateAngleX, x), lerp(part.rotateAngleY, y), lerp(part.rotateAngleZ, z));
    }

    private void mirrorLegs() {
        lerpPart(this.rightHindLeg, this.leftHindLeg.rotateAngleX, -this.leftHindLeg.rotateAngleY, -this.leftHindLeg.rotateAngleZ);
        lerpPart(this.rightFrontLeg, this.leftFrontLeg.rotateAngleX, -this.leftFrontLeg.rotateAngleY, -this.leftFrontLeg.rotateAngleZ);
    }

    private void lyingStill(float age, float yaw) {
        float f = age * 0.09F;
        float sin = MathHelper.sin(f);
        float cos = MathHelper.cos(f);
        float a = sin * sin - 2.0F * sin;
        float b = cos * cos - 3.0F * sin;
        this.head.rotateAngleX = lerp(this.head.rotateAngleX, -0.09F * a);
        this.head.rotateAngleY = lerp(this.head.rotateAngleY, 0.0F);
        this.head.rotateAngleZ = lerp(this.head.rotateAngleZ, -0.2F);
        this.tail.rotateAngleY = lerp(this.tail.rotateAngleY, -0.1F + 0.1F * a);
        this.topGills.rotateAngleX = lerp(this.topGills.rotateAngleX, 0.6F + 0.05F * b);
        this.leftGills.rotateAngleY = lerp(this.leftGills.rotateAngleY, -this.topGills.rotateAngleX);
        this.rightGills.rotateAngleY = lerp(this.rightGills.rotateAngleY, -this.leftGills.rotateAngleY);
        lerpPart(this.leftHindLeg, 1.1F, 1.0F, 0.0F);
        lerpPart(this.leftFrontLeg, 0.8F, 2.3F, -0.5F);
        this.mirrorLegs();
        this.body.rotateAngleX = lerp(0.2F, this.body.rotateAngleX, 0.0F);
        this.body.rotateAngleY = lerp(this.body.rotateAngleY, yaw * 0.017453292F);
        this.body.rotateAngleZ = lerp(this.body.rotateAngleZ, 0.0F);
    }

    private void crawling(float age, float yaw) {
        float f = age * 0.11F;
        float cos = MathHelper.cos(f);
        float a = (cos * cos - 2.0F * cos) / 5.0F;
        float b = 0.7F * cos;
        this.head.rotateAngleX = lerp(this.head.rotateAngleX, 0.0F);
        this.head.rotateAngleY = lerp(this.head.rotateAngleY, 0.09F * cos);
        this.head.rotateAngleZ = lerp(this.head.rotateAngleZ, 0.0F);
        this.tail.rotateAngleY = lerp(this.tail.rotateAngleY, this.head.rotateAngleY);
        this.topGills.rotateAngleX = lerp(this.topGills.rotateAngleX, 0.6F - 0.08F * (cos * cos + 2.0F * MathHelper.sin(f)));
        this.leftGills.rotateAngleY = lerp(this.leftGills.rotateAngleY, -this.topGills.rotateAngleX);
        this.rightGills.rotateAngleY = lerp(this.rightGills.rotateAngleY, -this.leftGills.rotateAngleY);
        lerpPart(this.leftHindLeg, 0.9424779F, 1.5F - a, -0.1F);
        lerpPart(this.leftFrontLeg, 1.0995574F, ((float) Math.PI / 2F) - b, 0.0F);
        lerpPart(this.rightHindLeg, this.leftHindLeg.rotateAngleX, -1.0F - a, 0.0F);
        lerpPart(this.rightFrontLeg, this.leftFrontLeg.rotateAngleX, (-(float) Math.PI / 2F) - b, 0.0F);
        this.body.rotateAngleX = lerp(0.2F, this.body.rotateAngleX, 0.0F);
        this.body.rotateAngleY = lerp(this.body.rotateAngleY, yaw * 0.017453292F);
        this.body.rotateAngleZ = lerp(this.body.rotateAngleZ, 0.0F);
    }

    private void hovering(float age) {
        float f = age * 0.075F;
        float cos = MathHelper.cos(f);
        float bob = MathHelper.sin(f) * 0.15F;
        this.body.rotateAngleX = lerp(this.body.rotateAngleX, -0.15F + 0.075F * cos);
        this.body.rotationPointY -= bob;
        this.head.rotateAngleX = lerp(this.head.rotateAngleX, -this.body.rotateAngleX);
        this.topGills.rotateAngleX = lerp(this.topGills.rotateAngleX, 0.2F * cos);
        this.leftGills.rotateAngleY = lerp(this.leftGills.rotateAngleY, -0.3F * cos - 0.19F);
        this.rightGills.rotateAngleY = lerp(this.rightGills.rotateAngleY, -this.leftGills.rotateAngleY);
        lerpPart(this.leftHindLeg, 2.3561945F - cos * 0.11F, 0.47123894F, 1.7278761F);
        lerpPart(this.leftFrontLeg, ((float) Math.PI / 4F) - cos * 0.2F, 2.042035F, 0.0F);
        this.mirrorLegs();
        this.tail.rotateAngleY = lerp(this.tail.rotateAngleY, 0.5F * cos);
        this.head.rotateAngleY = lerp(this.head.rotateAngleY, 0.0F);
        this.head.rotateAngleZ = lerp(this.head.rotateAngleZ, 0.0F);
    }

    private void swimming(float age, float pitch) {
        float f = age * 0.33F;
        float sin = MathHelper.sin(f);
        float cos = MathHelper.cos(f);
        float sway = 0.13F * sin;
        this.body.rotateAngleX = lerp(0.1F, this.body.rotateAngleX, pitch * 0.017453292F + sway);
        this.head.rotateAngleX = -sway * 1.8F;
        this.body.rotationPointY -= 0.45F * cos;
        this.topGills.rotateAngleX = lerp(this.topGills.rotateAngleX, -0.5F * sin - 0.8F);
        this.leftGills.rotateAngleY = lerp(this.leftGills.rotateAngleY, 0.3F * sin + 0.9F);
        this.rightGills.rotateAngleY = lerp(this.rightGills.rotateAngleY, -this.leftGills.rotateAngleY);
        this.tail.rotateAngleY = lerp(this.tail.rotateAngleY, 0.3F * MathHelper.cos(f * 0.9F));
        lerpPart(this.leftHindLeg, 1.8849558F, -0.4F * sin, (float) Math.PI / 2F);
        lerpPart(this.leftFrontLeg, 1.8849558F, -0.2F * cos - 0.1F, (float) Math.PI / 2F);
        this.mirrorLegs();
        this.head.rotateAngleY = lerp(this.head.rotateAngleY, 0.0F);
        this.head.rotateAngleZ = lerp(this.head.rotateAngleZ, 0.0F);
    }

    private void playDead(float yaw) {
        lerpPart(this.leftHindLeg, 1.4137167F, 1.0995574F, (float) Math.PI / 4F);
        lerpPart(this.leftFrontLeg, (float) Math.PI / 4F, 2.042035F, 0.0F);
        this.body.rotateAngleX = lerp(this.body.rotateAngleX, -0.15F);
        this.body.rotateAngleZ = lerp(this.body.rotateAngleZ, 0.35F);
        this.mirrorLegs();
        this.body.rotateAngleY = lerp(this.body.rotateAngleY, yaw * 0.017453292F);
        this.head.rotateAngleX = lerp(this.head.rotateAngleX, 0.0F);
        this.head.rotateAngleY = lerp(this.head.rotateAngleY, 0.0F);
        this.head.rotateAngleZ = lerp(this.head.rotateAngleZ, 0.0F);
        this.tail.rotateAngleY = lerp(this.tail.rotateAngleY, 0.0F);
        lerpPart(this.topGills, 0.0F, 0.0F, 0.0F);
        lerpPart(this.leftGills, 0.0F, 0.0F, 0.0F);
        lerpPart(this.rightGills, 0.0F, 0.0F, 0.0F);
    }
}
