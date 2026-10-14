package dev.eugheist.thaumicenergistics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.menu.DistillationEncoderMenu;

/** GuiDistillationPatternEncoder: 176x234; aspect column at (65,24), selected aspect (116,69), encode arrow (146,94). */
public final class DistillationEncoderScreen extends AbstractContainerScreen<DistillationEncoderMenu> {
    static final ResourceLocation BG = ThaumicEnergistics.id("textures/gui/distillation.encoder.png");
    static final ResourceLocation STATES = ThaumicEnergistics.id("textures/gui/ae2_states.png");
    public DistillationEncoderScreen(DistillationEncoderMenu menu, Inventory inv, Component title) { super(menu, inv, title); imageWidth = 176; imageHeight = 234; inventoryLabelY = 1000; }
    boolean over(double mx, double my, int x, int y) { return mx >= leftPos + x && mx < leftPos + x + 16 && my >= topPos + y && my < topPos + y + 16; }

    @Override protected void renderBg(GuiGraphics gui, float partial, int mx, int my) {
        gui.blit(BG, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        for (int i = 0; i < 6; i++) {
            var a = menu.aspect(i);
            if (a != null) aspect(gui, a, menu.amount(i), 65, 24 + i * 18);
            else if (!menu.source().isEmpty() && i == 0 && !menu.scanned()) gui.blit(ResourceLocation.fromNamespaceAndPath("tc4modern", "textures/aspects/_unknown.png"), leftPos + 65, topPos + 24, 0, 0, 16, 16, 16, 16);
        }
        var sel = menu.selected();
        if (sel != null) aspect(gui, sel, menu.selectedAmount(), 116, 69);
        gui.blit(STATES, leftPos + 146, topPos + 94, 240, 240, 16, 16);gui.blit(STATES, leftPos + 146, topPos + 94, 128, 0, 16, 16);
    }
    void aspect(GuiGraphics gui, thaumcraft.api.aspects.Aspect a, int amount, int x, int y) {
        CellWorkbenchScreen.drawAspect(gui, a, leftPos + x, topPos + y);
        gui.pose().pushPose();gui.pose().translate(0, 0, 200);
        var s = String.valueOf(amount);gui.drawString(font, s, leftPos + x + 17 - font.width(s), topPos + y + 9, 0xFFFFFF, true);
        gui.pose().popPose();
    }
    @Override protected void renderLabels(GuiGraphics gui, int mx, int my) { gui.drawString(font, title, 6, 6, 0, false); }
    @Override public void render(GuiGraphics gui, int mx, int my, float partial) {
        super.render(gui, mx, my, partial);
        for (int i = 0; i < 6; i++) if (over(mx, my, 65, 24 + i * 18) && menu.aspect(i) != null) { var a = menu.aspect(i); gui.renderComponentTooltip(font, java.util.List.of(Component.literal(a.getName()), Component.literal(a.getLocalizedDescription()).withStyle(net.minecraft.ChatFormatting.GRAY)), mx, my); return; }
        if (over(mx, my, 146, 94)) { gui.renderComponentTooltip(font, java.util.List.of(Component.translatable("gui.tooltips.ae2.Encode"), Component.translatable("gui.tooltips.ae2.EncodeDescription").withStyle(net.minecraft.ChatFormatting.GRAY)), mx, my); return; }
        renderTooltip(gui, mx, my);
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < 6; i++) if (over(mx, my, 65, 24 + i * 18)) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 10 + i); return true; }
        if (over(mx, my, 146, 94)) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, DistillationEncoderMenu.BUTTON_ENCODE); return true; }
        return super.mouseClicked(mx, my, button);
    }
}
