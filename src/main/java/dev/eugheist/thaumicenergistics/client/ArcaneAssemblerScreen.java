package dev.eugheist.thaumicenergistics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.block.ArcaneAssemblerEntity;
import dev.eugheist.thaumicenergistics.menu.ArcaneAssemblerMenu;

/** GuiArcaneAssembler: six primal vis bars (out of 1500) and the progress bar, filled from the texture's coloured strips. */
public final class ArcaneAssemblerScreen extends AbstractContainerScreen<ArcaneAssemblerMenu> {
    static final ResourceLocation BG = ThaumicEnergistics.id("textures/gui/arcane.assembler.png");
    static final ResourceLocation STATES = ThaumicEnergistics.id("textures/gui/ae2_states.png");
    static final int[] BAR_X = {41, 59, 77, 95, 113, 131};
    public ArcaneAssemblerScreen(ArcaneAssemblerMenu menu, Inventory inv, Component title) { super(menu, inv, title); imageWidth = menu.hasNetworkTool() ? 247 : 234; imageHeight = 197; inventoryLabelY = 1000; }

    void bar(GuiGraphics gui, int x, float percent) {
        int h = (int) Math.floor(percent * 16);
        if (h > 0) gui.blit(BG, leftPos + x, topPos + 87 + 16 - h, x, 202 + 16 - h, 4, h);
    }
    @Override protected void renderBg(GuiGraphics gui, float partial, int mx, int my) {
        if (menu.hasNetworkTool()) gui.blit(BG, leftPos, topPos, 0, 0, 247, imageHeight);
        else { gui.blit(BG, leftPos, topPos, 0, 0, 176, imageHeight); gui.blit(BG, leftPos + 176, topPos, 176, 0, 58, 104); }
        for (int i = 0; i < 6; i++) bar(gui, BAR_X[i], menu.vis(i) / (float) ArcaneAssemblerEntity.MAX_STORED);
        bar(gui, 149, menu.progress());
        for (int i = 0; i < 4; i++) gui.blit(STATES, leftPos + 187, topPos + 26 + i * 18, 240, 208, 16, 16);
        if (menu.hasNetworkTool()) for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) gui.blit(STATES, leftPos + 187 + c * 18, topPos + 137 + r * 18, 240, 208, 16, 16);
    }
    @Override protected void renderLabels(GuiGraphics gui, int mx, int my) { gui.drawString(font, title, 6, 6, 0, false); }
    @Override public void render(GuiGraphics gui, int mx, int my, float partial) {
        super.render(gui, mx, my, partial);
        for (int i = 0; i < 6; i++) if (mx >= leftPos + BAR_X[i] - 1 && mx < leftPos + BAR_X[i] + 5 && my >= topPos + 86 && my < topPos + 104) {
            var a = ArcaneAssemblerEntity.PRIMALS[i];
            gui.renderTooltip(font, Component.literal(a.getName() + ": " + String.format("%.1f", menu.vis(i) / 10f) + " / " + ArcaneAssemblerEntity.MAX_STORED / 10), mx, my);return;
        }
        renderTooltip(gui, mx, my);
    }
}
