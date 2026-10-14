package dev.eugheist.thaumicenergistics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import dev.eugheist.thaumicenergistics.menu.EssentiaIOBusMenu;
import dev.eugheist.thaumicenergistics.me.EssentiaTiles;

/** GuiEssentiaIO: 3x3 filter at (61,21) revealed by capacity cards, redstone button (with a redstone card) and void button left. */
public final class EssentiaIOBusScreen extends PartScreen<EssentiaIOBusMenu> {
    public EssentiaIOBusScreen(EssentiaIOBusMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = menu.hasNetworkTool() ? 246 : 211; imageHeight = 184; inventoryLabelY = 1000;
        button(EssentiaIOBusMenu.BUTTON_REDSTONE, -18, 2, () -> redstoneIcon(menu.redstoneMode()), () -> redstoneTooltip(menu.redstoneMode(), false), menu::redstoneControlled);
    }
    int voidY() { return menu.redstoneControlled() ? 20 : 2; }
    static int slotX(int i) { return 61 + i % 3 * 18; }
    static int slotY(int i) { return 21 + i / 3 * 18; }

    @Override protected void renderBg(GuiGraphics gui, float partial, int mx, int my) {
        if (menu.hasNetworkTool()) gui.blit(IO_BUS, leftPos, topPos, 0, 0, 246, 184);
        else { gui.blit(IO_BUS, leftPos, topPos, 0, 0, 176, 184); gui.blit(IO_BUS, leftPos + 176, topPos, 176, 0, 35, 86); }
        drawCardSlots(gui);
        for (int i = 0; i < 9; i++) if (menu.slotOpen(i)) aspectSlot(gui, slotX(i), slotY(i), menu.aspect(i));
        drawButtons(gui);
        if (menu.isExport()) voidButton(gui, -19, voidY(), menu.voidAllowed());
    }
    @Override protected void renderLabels(GuiGraphics gui, int mx, int my) { gui.drawString(font, title, 6, 5, 0, false); }

    @Override public void render(GuiGraphics gui, int mx, int my, float partial) {
        super.render(gui, mx, my, partial);
        for (int i = 0; i < 9; i++) if (menu.slotOpen(i) && overAspectSlot(mx, my, slotX(i), slotY(i))) { aspectHover(gui, slotX(i), slotY(i), menu.aspect(i), mx, my); return; }
        if (buttonTooltip(gui, mx, my)) return;
        if (menu.isExport() && overVoid(mx, my)) { gui.renderComponentTooltip(font, voidTooltip(menu.voidAllowed()), mx, my); return; }
        renderTooltip(gui, mx, my);
    }
    boolean overVoid(double mx, double my) { return mx >= leftPos - 19 && mx < leftPos - 1 && my >= topPos + voidY() && my < topPos + voidY() + 18; }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < 9; i++) if (menu.slotOpen(i) && overAspectSlot(mx, my, slotX(i), slotY(i))) {
            var held = EssentiaTiles.filterAspect(menu.getCarried());
            for (int j = 0; j < 9; j++) if (held != null && menu.aspect(j) == held) return true;
            click(i); return true;
        }
        if (menu.isExport() && overVoid(mx, my)) { click(EssentiaIOBusMenu.BUTTON_VOID); return true; }
        return super.mouseClicked(mx, my, button);
    }
}
