package dev.eugheist.thaumicenergistics.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.menu.VibrationChamberMenu;

/** GuiEssentiaVibrationChamber: 176x82, the alchemical furnace's essentia vial and flame, and the AE power bar. */
public final class VibrationChamberScreen extends AbstractContainerScreen<VibrationChamberMenu> {
    static final ResourceLocation BG = ThaumicEnergistics.id("textures/gui/essentia.vibration.chamber.png");
    static final ResourceLocation FURNACE = ResourceLocation.fromNamespaceAndPath("tc4modern", "textures/gui/gui_alchemyfurnace.png");
    public VibrationChamberScreen(VibrationChamberMenu menu, Inventory inv, Component title) { super(menu, inv, title); imageWidth = 176; imageHeight = 82; inventoryLabelY = 1000; titleLabelY = 1000; }
    @Override protected void renderBg(GuiGraphics gui, float partial, int mx, int my) {
        gui.blit(BG, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int power = (int) (18 * menu.powerPercent());
        gui.blit(BG, leftPos + 96, topPos + 32 + power, 51, 83 + power, 6, 18 - power);
        int stored = (int) (48 * (1f - menu.storedAmount() / 64f));
        gui.blit(FURNACE, leftPos + 62, topPos + 16 + stored, 200, stored, 8, 48 - stored);
        RenderSystem.enableBlend();
        gui.blit(FURNACE, leftPos + 61, topPos + 12, 232, 0, 11, 56);
        int progress = (int) (20 * menu.ticksPercent());
        if (menu.processing() != null) gui.blit(FURNACE, leftPos + 78, topPos + 30 + progress, 177, progress, 15, 20 - progress);
        RenderSystem.disableBlend();
    }
    boolean in(int x, int y, int w, int h, int mx, int my) { return mx >= leftPos + x && mx < leftPos + x + w && my >= topPos + y && my < topPos + y + h; }
    @Override public void render(GuiGraphics gui, int mx, int my, float partial) {
        super.render(gui, mx, my, partial);
        if (in(61, 12, 11, 56, mx, my)) gui.renderTooltip(font, Component.literal(menu.stored() != null && menu.storedAmount() > 0 ? menu.stored().getName() + " x " + menu.storedAmount() : "Empty"), mx, my);
        else if (in(78, 30, 15, 20, mx, my) && menu.processing() != null) gui.renderComponentTooltip(font, java.util.List.of(Component.translatable("thaumicenergistics.gui.vibration.chamber.processing", menu.processing().getName()), Component.literal(String.format("%.0fs", menu.ticksRemaining() / 20f))), mx, my);
        else if (in(96, 32, 6, 18, mx, my)) gui.renderTooltip(font, Component.literal(String.format("%.0f AE/t", menu.powerPerTick())), mx, my);
        else renderTooltip(gui, mx, my);
    }
}
