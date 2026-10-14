package dev.eugheist.thaumicenergistics.client;

import java.util.List;
import appeng.api.client.AEKeyRenderHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import dev.eugheist.thaumicenergistics.me.EssentiaKey;

/** Draws essentia keys with the TC4 aspect icon tinted by the aspect colour (UtilsFX.drawTag), in GUIs and on monitors. */
public final class EssentiaKeyRenderer implements AEKeyRenderHandler<EssentiaKey> {
    public static final EssentiaKeyRenderer INSTANCE = new EssentiaKeyRenderer();
    private EssentiaKeyRenderer() {}

    @Override public void drawInGui(Minecraft minecraft, GuiGraphics gui, int x, int y, EssentiaKey key) {
        int color = key.getAspect().getColor();
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, 1);
        gui.blit(key.getAspect().getImage(), x, y, 0, 0, 16, 16, 16, 16);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    @Override public void drawOnBlockFace(PoseStack pose, MultiBufferSource buffers, EssentiaKey key, float scale, int light, Level level) {
        int color = key.getAspect().getColor() | 0xFF000000;
        pose.pushPose();
        pose.translate(0, 0, 0.01f);
        var out = buffers.getBuffer(RenderType.entityTranslucent(key.getAspect().getImage()));
        float s = (scale - .05f) / 2;
        var last = pose.last();
        float[][] v = {{-s, -s, 0, 1}, {s, -s, 1, 1}, {s, s, 1, 0}, {-s, s, 0, 0}};
        for (var p : v) out.addVertex(last, p[0], p[1], 0).setColor(color).setUv(p[2], p[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, 0, 0, 1);
        pose.popPose();
    }

    @Override public Component getDisplayName(EssentiaKey key) { return key.getDisplayName(); }
    @Override public List<Component> getTooltip(EssentiaKey key) {
        return List.of(key.getDisplayName(), Component.literal(key.getAspect().getLocalizedDescription()).withStyle(net.minecraft.ChatFormatting.GRAY), Component.literal("Thaumic Energistics").withStyle(net.minecraft.ChatFormatting.BLUE, net.minecraft.ChatFormatting.ITALIC));
    }
}
