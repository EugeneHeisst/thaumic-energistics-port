package dev.eugheist.thaumicenergistics.client;

import java.util.ArrayList;
import java.util.List;
import appeng.client.gui.me.common.StackSizeRenderer;
import appeng.core.AEConfig;
import appeng.core.localization.ButtonToolTips;
import appeng.util.ReadableNumberConverter;
import com.mojang.blaze3d.platform.InputConstants;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.menu.EssentiaTermMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;

/**
 * GuiEssentiaCellTerminal: 9x4 aspect selectors with a pulsing frame on the selected one, search box, AE2-style scrollbar,
 * sorting and view buttons left of the panel, the selected aspect's name and amount under the grid.
 */
public class EssentiaTermScreen extends AbstractContainerScreen<EssentiaTermMenu> {
    static final ResourceLocation BG = ThaumicEnergistics.id("textures/gui/essentia.terminal.png"), STATES = ThaumicEnergistics.id("textures/gui/ae2_states.png");
    static final ResourceLocation SCROLLER = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller"), SCROLLER_OFF = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller_disabled");
    static final int SCROLL_X = 175, SCROLL_Y = 18, SCROLL_H = 70;
    EditBox search;
    final List<EssentiaTermMenu.Entry> view = new ArrayList<>();
    int seenVersion = -1, scroll, maxScroll;String term = "";boolean dragging;
    public static int renders, largeLabels;

    public EssentiaTermScreen(EssentiaTermMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); imageWidth = 195;imageHeight = 204; }

    @Override protected void init() {
        super.init();
        search = new EditBox(font, leftPos + 98, topPos + 6, 69, 10, Component.empty());
        search.setBordered(false);search.setMaxLength(14);search.setTextColor(0xFFFFFF);search.setValue(term);
        search.setResponder(s -> { term = s.trim().toLowerCase();refresh(); });
        addWidget(search);
        refresh();
    }

    /** updateView: search on the aspect name or tag, view mode filter, alphabetic or amount sort. */
    void refresh() {
        view.clear();
        for (var e : menu.entries) {
            var a = e.getAspect();
            if (menu.viewMode == EssentiaTermMenu.VIEW_CRAFTABLE && !e.craftable() || menu.viewMode == EssentiaTermMenu.VIEW_STORED && e.amount() <= 0) continue;
            if (!term.isEmpty() && !a.getName().toLowerCase().contains(term) && !a.getTag().contains(term)) continue;
            view.add(e);
        }
        java.util.Comparator<EssentiaTermMenu.Entry> byName = java.util.Comparator.comparing(e -> e.getAspect().getName());
        view.sort(menu.sortMode == EssentiaTermMenu.SORT_AMOUNT ? java.util.Comparator.<EssentiaTermMenu.Entry>comparingLong(e -> -e.amount()).thenComparing(byName) : byName);
        maxScroll = Math.max(0, (int)Math.ceil(Math.max(0, view.size() - 36) / 9.0));
        scroll = Math.min(scroll, maxScroll);
        seenVersion = menu.version;
    }

    @Nullable EssentiaTermMenu.Entry selectedEntry() { for (var e : menu.entries) if (e.getAspect() == menu.selected) return e; return null; }
    int widget(double mx, double my) {
        int x = (int)mx - leftPos - 7, y = (int)my - topPos - 17;
        if (x < 0 || y < 0 || x >= 162 || y >= 72) return -1;
        int i = (y / 18) * 9 + x / 18 + scroll * 9;
        return i < view.size() ? i : -1;
    }

    @Override protected void renderBg(GuiGraphics gui, float partial, int mx, int my) {
        if (seenVersion != menu.version) refresh();
        gui.blit(BG, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        // GuiButtonSortingMode / GuiButtonViewType
        button(gui, -18, 9, menu.sortMode == EssentiaTermMenu.SORT_AMOUNT ? 16 : 0, 64);
        button(gui, -18, 29, menu.viewMode == EssentiaTermMenu.VIEW_CRAFTABLE ? 48 : menu.viewMode == EssentiaTermMenu.VIEW_STORED ? 0 : 32, 16);
        // GuiScrollbar
        int thumb = maxScroll == 0 ? 0 : (SCROLL_H - 15) * scroll / maxScroll;
        gui.blitSprite(maxScroll == 0 ? SCROLLER_OFF : SCROLLER, leftPos + SCROLL_X, topPos + SCROLL_Y + thumb, 12, 15);
        search.render(gui, mx, my, partial);
    }
    void button(GuiGraphics gui, int x, int y, int u, int v) { gui.blit(STATES, leftPos + x, topPos + y, 240, 240, 16, 16);gui.blit(STATES, leftPos + x, topPos + y, u, v, 16, 16); }

    @Override protected void renderLabels(GuiGraphics gui, int mx, int my) {
        gui.drawString(font, title, 7, 6, 0x404040, false);
        renders++;
        long t = System.currentTimeMillis() / 45;int pulse = Math.abs(Math.abs((int)(t % 30)) - 15);
        for (int w = 0; w < 36; w++) {
            int i = w + scroll * 9;
            if (i >= view.size()) break;
            var e = view.get(i);var a = e.getAspect();int x = 7 + (w % 9) * 18, y = 17 + (w / 9) * 18;
            if (a == menu.selected) {
                // drawHollowRectWithCorners: pulsing aspect-coloured fill (alpha 0x70 -> 0x20) inside a cyan frame.
                int alpha = 0x70 - (0x50 * pulse / 15);
                gui.fill(x, y, x + 18, y + 18, alpha << 24 | a.getColor() & 0xFFFFFF);
                gui.renderOutline(x, y, 18, 18, 0xFF00FFFF);
            }
            CellWorkbenchScreen.drawAspect(gui, a, x + 1, y + 1);
            boolean hide = menu.viewMode == EssentiaTermMenu.VIEW_CRAFTABLE;
            if (AEConfig.instance().isUseLargeFonts()) {
                // AE2 "useTerminalUseLargeFont": its own larger slot labels, 3 digits and "+" for craftable like the ME terminal.
                largeLabels++;
                if (e.amount() > 0 && !hide) {
                    StackSizeRenderer.renderSizeLabel(gui, font, x + 1, y + 1, ReadableNumberConverter.format(e.amount(), 3), true);
                    if (e.craftable()) StackSizeRenderer.renderSizeLabel(gui, font, x - 10, y - 10, "+", false);
                } else if (e.craftable()) StackSizeRenderer.renderSizeLabel(gui, font, x + 1, y + 1, "+", true);
            } else if (e.amount() > 0 && !hide || e.craftable()) {
                String text = e.amount() > 0 && !hide ? ReadableNumberConverter.format(e.amount(), 4) : Component.translatable("thaumicenergistics.gui.craft").getString();
                gui.pose().pushPose();gui.pose().translate(0, 0, 300);gui.pose().scale(.5f, .5f, 1);
                gui.drawString(font, text, (x + 17) * 2 - font.width(text), (y + 13) * 2, 0xFFFFFF, true);
                gui.pose().popPose();
            }
        }
        var sel = selectedEntry();
        if (sel != null) {
            gui.drawString(font, Component.translatable("thaumicenergistics.gui.selected.aspect").getString() + ": " + sel.getAspect().getName(), 45, 91, 0x404040, false);
            gui.drawString(font, Component.translatable("thaumicenergistics.gui.selected.amount").getString() + ": " + sel.amount(), 45, 101, 0x404040, false);
        }
    }

    @Override public void render(GuiGraphics gui, int mx, int my, float partial) {
        super.render(gui, mx, my, partial);
        int i = widget(mx, my);
        if (i >= 0 && menu.getCarried().isEmpty()) {
            var e = view.get(i);var a = e.getAspect();
            var lines = new ArrayList<Component>();lines.add(Component.literal(a.getName()));
            lines.add(Component.literal(a.getLocalizedDescription()).withStyle(ChatFormatting.GRAY));
            if (e.amount() > 0) lines.add(Component.literal(String.format("%,d", e.amount())).withStyle(ChatFormatting.GRAY));
            if (e.craftable()) lines.add(ButtonToolTips.Craftable.text().withStyle(ChatFormatting.DARK_GRAY));
            gui.renderComponentTooltip(font, lines, mx, my);
        } else if (over(mx, my, -18, 9)) gui.renderComponentTooltip(font, List.of(ButtonToolTips.SortBy.text(), (menu.sortMode == EssentiaTermMenu.SORT_AMOUNT ? ButtonToolTips.NumberOfItems : ButtonToolTips.ItemName).text().withStyle(ChatFormatting.GRAY)), mx, my);
        else if (over(mx, my, -18, 29)) gui.renderComponentTooltip(font, List.of(ButtonToolTips.View.text(), (menu.viewMode == EssentiaTermMenu.VIEW_CRAFTABLE ? ButtonToolTips.Craftable : menu.viewMode == EssentiaTermMenu.VIEW_STORED ? ButtonToolTips.StoredItems : ButtonToolTips.StoredCraftable).text().withStyle(ChatFormatting.GRAY)), mx, my);
        else renderTooltip(gui, mx, my);
    }
    boolean over(double mx, double my, int x, int y) { return mx >= leftPos + x && mx < leftPos + x + 16 && my >= topPos + y && my < topPos + y + 16; }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        int i = widget(mx, my);
        if (i >= 0) {
            var e = view.get(i);var a = e.getAspect();
            if (!menu.getCarried().isEmpty()) menu.send(1, a, false);
            else {
                boolean craftView = menu.viewMode == EssentiaTermMenu.VIEW_CRAFTABLE;
                if (button != 1 && e.craftable() && (craftView || button == 2 || button == 0 && e.amount() == 0)) menu.send(2, a, false);
                else {
                    // WidgetAspectSelector.onMouseClicked: toggles the selection.
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
                    menu.send(0, a == menu.selected ? null : a, false);
                }
            }
            return true;
        }
        if (mx >= search.getX() && mx < search.getX() + search.getWidth() && my >= search.getY() && my < search.getY() + search.getHeight()) {
            if (button == 1) search.setValue("");
            search.setFocused(true);setFocused(search);
            return true;
        }
        search.setFocused(false);
        if (over(mx, my, -18, 9) && button != 2) { click();menu.send(3, null, button == 1);return true; }
        if (over(mx, my, -18, 29) && button != 2) { click();menu.send(4, null, button == 1);return true; }
        if (mx >= leftPos + SCROLL_X && mx < leftPos + SCROLL_X + 12 && my >= topPos + SCROLL_Y && my < topPos + SCROLL_Y + SCROLL_H) { dragging = true;drag(my);return true; }
        return super.mouseClicked(mx, my, button);
    }
    void click() { minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1)); }
    void drag(double my) { if (maxScroll > 0) scroll = Math.max(0, Math.min(maxScroll, (int)Math.round((my - topPos - SCROLL_Y - 7.5) / (SCROLL_H - 15) * maxScroll))); }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) { if (dragging) { drag(my);return true; } return super.mouseDragged(mx, my, button, dx, dy); }
    @Override public boolean mouseReleased(double mx, double my, int button) { dragging = false;return super.mouseReleased(mx, my, button); }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx > leftPos + 195 || Screen.hasShiftDown()) return super.mouseScrolled(mx, my, sx, sy);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int)Math.signum(sy)));
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (search.isFocused()) {
            if (key == InputConstants.KEY_ESCAPE) { search.setFocused(false);return true; }
            if (search.keyPressed(key, scan, mods) || search.canConsumeInput()) return true;
        }
        if (key == InputConstants.KEY_HOME) { scroll = 0;return true; }
        if (key == InputConstants.KEY_END) { scroll = maxScroll;return true; }
        if (key == InputConstants.KEY_UP) { scroll = Math.max(0, scroll - 1);return true; }
        if (key == InputConstants.KEY_DOWN) { scroll = Math.min(maxScroll, scroll + 1);return true; }
        return super.keyPressed(key, scan, mods);
    }
    @Override public boolean charTyped(char c, int mods) { if (search.isFocused() && search.charTyped(c, mods)) return true; return super.charTyped(c, mods); }
}
