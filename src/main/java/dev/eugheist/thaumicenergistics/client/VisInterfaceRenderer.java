package dev.eugheist.thaumicenergistics.client;

import appeng.api.orientation.BlockOrientation;
import appeng.client.render.BlockEntityRenderHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import dev.eugheist.thaumicenergistics.part.VisInterfacePart;

/** The vis runes of PartVisInterface.renderStatic, tinted with the colour of the aspect currently drained, at full brightness. */
public final class VisInterfaceRenderer {
    private VisInterfaceRenderer() {}
    public static void render(VisInterfacePart part, PoseStack pose, MultiBufferSource buffers) {
        int color = part.drainColor();
        if (color == 0) return;
        pose.pushPose();
        pose.translate(.5, .5, .5);
        BlockEntityRenderHelper.rotateToFace(pose, BlockOrientation.get(part.getSide(), 0));
        pose.translate(0, 0, 7 / 16f + .002f);
        var consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(VisInterfacePart.RUNES));
        var m = pose.last();
        float s = 4 / 16f;int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        float[][] v = {{-s, -s, 1, 1}, {s, -s, 0, 1}, {s, s, 0, 0}, {-s, s, 1, 0}};
        for (var p : v) consumer.addVertex(m, p[0], p[1], 0).setColor(r, g, b, 255).setUv(p[2], p[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(m, 0, 0, 1);
        pose.popPose();
    }
}
