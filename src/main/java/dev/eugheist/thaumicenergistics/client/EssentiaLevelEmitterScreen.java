package dev.eugheist.thaumicenergistics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import dev.eugheist.thaumicenergistics.menu.EssentiaLevelEmitterMenu;
import dev.eugheist.thaumicenergistics.menu.ThresholdPayload;

/**
 * GuiEssentiaLevelEmitter: +1/+10/+100 and -1/-10/-100 buttons (x100 while shift is held), a 10-digit amount field at (25,44),
 * the tracked aspect widget at (123,39) and the high/low emit button left of the panel.
 */
public final class EssentiaLevelEmitterScreen extends PartScreen<EssentiaLevelEmitterMenu> {
    static final net.minecraft.resources.ResourceLocation BG = dev.eugheist.thaumicenergistics.ThaumicEnergistics.id("textures/gui/essentia.level.emitter.png");
    static final String[] LABELS = {"+1", "+10", "+100", "-1", "-10", "-100"}, SHIFTED = {"+100", "+1000", "+10000", "-100", "-1000", "-10000"};
    final net.minecraft.client.gui.components.Button[] deltas = new net.minecraft.client.gui.components.Button[6];
    EditBox amount;
    long shown = -1;

    public EssentiaLevelEmitterScreen(EssentiaLevelEmitterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176; imageHeight = 184; inventoryLabelY = 1000;
        button(EssentiaLevelEmitterMenu.BUTTON_REDSTONE, -18, 2, () -> redstoneIcon(menu.mode()), () -> redstoneTooltip(menu.mode(), true), () -> true);
    }

    @Override protected void init() {
        super.init();
        for (int row = 0; row < 2; row++) for (int col = 0; col < 3; col++) {
            int index = row * 3 + col;
            deltas[index] = addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal(LABELS[index]), b -> click((Screen.hasShiftDown() ? 11 : 1) + index))
                .bounds(leftPos + 15 + col * 50, topPos + 17 + row * 42, 42, 20).build());
        }
        amount = new EditBox(font, leftPos + 25, topPos + 44, 80, 10, Component.empty());
        amount.setBordered(false); amount.setMaxLength(10); amount.setTextColor(0xFFFFFF);
        amount.setFilter(s -> s.chars().allMatch(Character::isDigit));
        amount.setResponder(s -> { if (shown >= 0) { long v = 0; try { v = s.isEmpty() ? 0 : Long.parseLong(s); } catch (NumberFormatException e) {} if (v != shown) { shown = v; PacketDistributor.sendToServer(new ThresholdPayload(v)); } } });
        addRenderableWidget(amount); setInitialFocus(amount);
    }

    @Override protected void containerTick() {
        super.containerTick();
        if (menu.threshold() != shown && !(amount.isFocused() && shown >= 0 && amount.getValue().isEmpty())) { long v = menu.threshold(); shown = -1; amount.setValue(Long.toString(v)); shown = v; }
    }

    @Override protected void renderBg(GuiGraphics gui, float partial, int mx, int my) {
        gui.blit(BG, leftPos, topPos, 0, 0, 176, 184);
        aspectSlot(gui, 123, 39, menu.tracked());
        drawButtons(gui);
    }
    @Override protected void renderLabels(GuiGraphics gui, int mx, int my) { gui.drawString(font, title, 8, 5, 0, false); }
    @Override public void render(GuiGraphics gui, int mx, int my, float partial) {
        for (int i = 0; i < 6; i++) deltas[i].setMessage(Component.literal(Screen.hasShiftDown() ? SHIFTED[i] : LABELS[i]));
        super.render(gui, mx, my, partial);
        if (overAspectSlot(mx, my, 123, 39)) { aspectHover(gui, 123, 39, menu.tracked(), mx, my); return; }
        if (buttonTooltip(gui, mx, my)) return;
        renderTooltip(gui, mx, my);
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (overAspectSlot(mx, my, 123, 39)) { click(0); return true; }
        return super.mouseClicked(mx, my, button);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (amount.isFocused() && key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { amount.keyPressed(key, scan, mods); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
