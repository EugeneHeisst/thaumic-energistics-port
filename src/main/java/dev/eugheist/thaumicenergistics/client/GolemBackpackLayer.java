package dev.eugheist.thaumicenergistics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.eugheist.tc4modern.StrawGolem;
import dev.eugheist.thaumicenergistics.golem.WifiGolems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** WirelessGolemHandler.renderGolem + ModelGolemWifiBackpack: the pack on the golem's back with a fluix pearl spinning on the antenna, red when out of range. */
public final class GolemBackpackLayer<M extends EntityModel<StrawGolem>> extends RenderLayer<StrawGolem, M> {
    public static int rendered;
    static final ModelPart PACK = build();
    static ModelPart build() {
        var mesh = new MeshDefinition();var r = mesh.getRoot();var flip = PartPose.rotation(3.14f, 0, 0);
        r.addOrReplaceChild("pack_back", CubeListBuilder.create().texOffs(0, 0).addBox(-1, -3, -3, 2, 6, 6), flip);
        r.addOrReplaceChild("pack_front", CubeListBuilder.create().texOffs(2, 0).addBox(-1.5f, -1, -2, 1, 2, 4), flip);
        r.addOrReplaceChild("antenna", CubeListBuilder.create().texOffs(10, 0).addBox(-.5f, -6, -.5f, 1, 3, 1), flip);
        return LayerDefinition.create(mesh, 16, 16).bakeRoot();
    }
    static final float MIN_U = 8 / 16f, MAX_U = 13.5f / 16f, MIN_V = 6 / 16f, MAX_V = 11.5f / 16f, SCALE = .2f, WIDTH = .32f, FD = -.013f / SCALE, FD2 = FD + FD;

    public GolemBackpackLayer(RenderLayerParent<StrawGolem, M> parent) { super(parent); }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, StrawGolem golem, float limb, float amount, float partial, float age, float yaw, float pitch) {
        var wifi = WifiGolems.data(golem);
        if (wifi == null) return;
        rendered++;
        boolean inRange = wifi.getBoolean("range"), active = inRange && golem.core() >= 0 && !golem.paused;
        var skin = WifiGolems.Skin.byId(wifi.getByte("skin"));
        int overlay = LivingEntityRenderer.getOverlayCoords(golem, 0);
        var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(skin.texture));
        pose.pushPose();
        // Model space (y down, -z forward) to the original frame: y up, +x forward, 0.64 above the feet, 0.16 behind the centre.
        pose.translate(0, 1.501 - .64, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(180));pose.mulPose(Axis.YP.rotationDegrees(90));
        pose.translate(-.16f, 0, 0);pose.scale(.8f, .8f, .8f);
        PACK.render(pose, vc, light, overlay);
        float spin = active && !Minecraft.getInstance().isPaused() ? (age * 3) % 360 : 0;
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.translate(-.0955f, .275f, -.045f);pose.scale(SCALE, SCALE, SCALE);
        int color = inRange ? 0xFFFFFFFF : 0xFFFF0000;
        face(pose, vc, MIN_U, MAX_U, color, light, overlay);
        pose.translate(0, 0, WIDTH - FD2);face(pose, vc, MAX_U, MIN_U, color, light, overlay);
        pose.mulPose(Axis.YP.rotationDegrees(90));pose.translate(-WIDTH - FD, 0, WIDTH + FD);face(pose, vc, MAX_U, MIN_U, color, light, overlay);
        pose.translate(0, 0, WIDTH - FD2);face(pose, vc, MIN_U, MAX_U, color, light, overlay);
        pose.popPose();
    }
    static void face(PoseStack pose, VertexConsumer vc, float minU, float maxU, int color, int light, int overlay) {
        var last = pose.last();
        float[][] v = {{0, 0, maxU, MAX_V}, {1, 0, minU, MAX_V}, {1, 1, minU, MIN_V}, {0, 1, maxU, MIN_V}};
        for (var p : v) vc.addVertex(last, p[0], p[1], 0).setColor(color).setUv(p[2], p[3]).setOverlay(overlay).setLight(light).setNormal(last, 0, 0, 1);
    }
}
