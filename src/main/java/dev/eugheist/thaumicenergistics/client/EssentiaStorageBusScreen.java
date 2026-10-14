package dev.eugheist.thaumicenergistics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import dev.eugheist.thaumicenergistics.menu.EssentiaStorageBusMenu;
import dev.eugheist.thaumicenergistics.me.EssentiaTiles;

/** GuiEssentiaStorageBus: 3x3 filter at (61,21), priority tab at (154,0), void button at (-18,8), inverter slot right. */
public final class EssentiaStorageBusScreen extends PartScreen<EssentiaStorageBusMenu> {
    static final net.minecraft.resources.ResourceLocation BG = dev.eugheist.thaumicenergistics.ThaumicEnergistics.id("textures/gui/essentia.storage.bus.png");
    public EssentiaStorageBusScreen(EssentiaStorageBusMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = menu.hasNetworkTool() ? 246 : 210; imageHeight = 184; inventoryLabelY = 1000;
        tab(EssentiaStorageBusMenu.BUTTON_PRIORITY, 154, 0, new int[]{32, 64}, Component.translatable("gui.ae2.Priority"));
    }
    static int slotX(int i) { return 61 + i % 3 * 18; }
    static int slotY(int i) { return 21 + i / 3 * 18; }

    @Override protected void renderBg(GuiGraphics gui, float partial, int mx, int my) {
        gui.blit(BG, leftPos, topPos, 0, 0, 176, 222);
        gui.blit(BG, leftPos + 179, topPos, 179, 0, 32, 32);
        if (menu.hasNetworkTool()) gui.blit(BG, leftPos + 179, topPos + 93, 178, 93, 68, 68);
        drawCardSlots(gui);
        for (int i = 0; i < 9; i++) aspectSlot(gui, slotX(i), slotY(i), menu.aspect(i));
        drawButtons(gui);
        voidButton(gui, -18, 8, menu.voidAllowed());
    }
    @Override protected void renderLabels(GuiGraphics gui, int mx, int my) { gui.drawString(font, title, 6, 5, 0, false); }
    boolean overVoid(double mx, double my) { return mx >= leftPos - 18 && mx < leftPos && my >= topPos + 8 && my < topPos + 26; }

    @Override public void render(GuiGraphics gui, int mx, int my, float partial) {
        super.render(gui, mx, my, partial);
        for (int i = 0; i < 9; i++) if (overAspectSlot(mx, my, slotX(i), slotY(i))) { aspectHover(gui, slotX(i), slotY(i), menu.aspect(i), mx, my); return; }
        if (buttonTooltip(gui, mx, my)) return;
        if (overVoid(mx, my)) { gui.renderComponentTooltip(font, voidTooltip(menu.voidAllowed()), mx, my); return; }
        renderTooltip(gui, mx, my);
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < 9; i++) if (overAspectSlot(mx, my, slotX(i), slotY(i))) {
            var held = EssentiaTiles.filterAspect(menu.getCarried());
            for (int j = 0; j < 9; j++) if (held != null && menu.aspect(j) == held) return true;
            click(i); return true;
        }
        if (overVoid(mx, my)) { click(EssentiaStorageBusMenu.BUTTON_VOID); return true; }
        return super.mouseClicked(mx, my, button);
    }
}
