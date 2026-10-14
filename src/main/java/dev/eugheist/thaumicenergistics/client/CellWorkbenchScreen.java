package dev.eugheist.thaumicenergistics.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumcraft.api.aspects.Aspect;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.block.CellWorkbenchMenu;

/**
 * GuiEssentiaCellWorkbench: 176x251 original background, AE2 cell slot backdrop, 63 aspect widgets (9x7 at 7,28) drawn on the
 * essentia IO bus slot sprite, and the wrench (partition to contents) and clear-grid buttons left of the panel.
 */
public final class CellWorkbenchScreen extends AbstractContainerScreen<CellWorkbenchMenu> {
    static final ResourceLocation BG = ThaumicEnergistics.id("textures/gui/essentia.cell.workbench.png");
    static final ResourceLocation IO_BUS = ThaumicEnergistics.id("textures/gui/essentia.io.bus.png");
    static final ResourceLocation STATES = ThaumicEnergistics.id("textures/gui/ae2_states.png");
    public CellWorkbenchScreen(CellWorkbenchMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); imageWidth = 176; imageHeight = 251; }

    int widgetX(int i) { return leftPos + 7 + (i % 9) * 18; }
    int widgetY(int i) { return topPos + 28 + (i / 9) * 18; }
    int widgetAt(double mx, double my) { for (int i = 0; i < 63; i++) if (mx >= widgetX(i) && mx < widgetX(i) + 17 && my >= widgetY(i) && my < widgetY(i) + 17) return i; return -1; }
    boolean over(double mx, double my, int x, int y) { return mx >= leftPos + x && mx < leftPos + x + 16 && my >= topPos + y && my < topPos + y + 16; }

    @Override protected void renderBg(GuiGraphics gui, float partial, int mx, int my) {
        gui.blit(BG, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        gui.blit(STATES, leftPos + CellWorkbenchMenu.CELL_X, topPos + CellWorkbenchMenu.CELL_Y, 240, 0, 16, 16);
        for (var button : new int[][]{{-18, 28, 32, 64}, {-18, 8, 96, 0}}) { gui.blit(STATES, leftPos + button[0], topPos + button[1], 240, 240, 16, 16); gui.blit(STATES, leftPos + button[0], topPos + button[1], button[2], button[3], 16, 16); }
        var partition = menu.partition();
        for (int i = 0; i < 63; i++) {
            gui.blit(IO_BUS, widgetX(i), widgetY(i), 79, 39, 18, 18);
            if (i < partition.size()) drawAspect(gui, partition.get(i), widgetX(i) + 1, widgetY(i) + 1);
        }
    }
    static void drawAspect(GuiGraphics gui, Aspect aspect, int x, int y) {
        int c = aspect.getColor();
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, 1);
        gui.blit(aspect.getImage(), x, y, 0, 0, 16, 16, 16, 16);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    @Override protected void renderLabels(GuiGraphics gui, int mx, int my) { gui.drawString(font, title, 6, 6, 0, false); }

    @Override public void render(GuiGraphics gui, int mx, int my, float partial) {
        super.render(gui, mx, my, partial);
        int i = widgetAt(mx, my);
        if (i >= 0) {
            gui.fillGradient(widgetX(i) + 1, widgetY(i) + 1, widgetX(i) + 17, widgetY(i) + 17, 0x80FFFFFF, 0x80FFFFFF);
            var partition = menu.partition();
            if (i < partition.size()) gui.renderComponentTooltip(font, java.util.List.of(Component.literal(partition.get(i).getName()), Component.literal(partition.get(i).getLocalizedDescription())), mx, my);
        } else if (over(mx, my, -18, 28)) gui.renderComponentTooltip(font, java.util.List.of(Component.translatable("gui.tooltips.ae2.PartitionStorage"), Component.translatable("gui.tooltips.ae2.PartitionStorageHint").withStyle(net.minecraft.ChatFormatting.GRAY)), mx, my);
        else if (over(mx, my, -18, 8)) gui.renderComponentTooltip(font, java.util.List.of(Component.translatable("gui.tooltips.ae2.Clear"), Component.translatable("gui.tooltips.ae2.ClearSettings").withStyle(net.minecraft.ChatFormatting.GRAY)), mx, my);
        else renderTooltip(gui, mx, my);
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        int i = widgetAt(mx, my);
        int id = i >= 0 ? i : over(mx, my, -18, 28) ? CellWorkbenchMenu.BUTTON_TO_CONTENTS : over(mx, my, -18, 8) ? CellWorkbenchMenu.BUTTON_CLEAR : -1;
        if (id >= 0) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); return true; }
        return super.mouseClicked(mx, my, button);
    }
}
