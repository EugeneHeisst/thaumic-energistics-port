package dev.eugheist.thaumicenergistics.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.menu.PartMenu;

/**
 * ThEBaseGui: AE2 rv3 state icons for buttons, the upgrade/network-tool slot card backdrop, and the aspect slot widget
 * (essentia.io.bus.png 79,39 frame with the coloured aspect, white hover underlay, name + description tooltip).
 */
public abstract class PartScreen<M extends PartMenu<?>> extends AbstractContainerScreen<M> {
    static final ResourceLocation STATES = ThaumicEnergistics.id("textures/gui/ae2_states.png");
    static final ResourceLocation IO_BUS = ThaumicEnergistics.id("textures/gui/essentia.io.bus.png");
    protected final List<Button> buttons = new ArrayList<>();

    protected PartScreen(M menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    /** A state button: 16x16 regular button (or 22x22 tab) with an icon, relative to the panel. */
    protected record Button(int id, int x, int y, int w, int h, java.util.function.Supplier<int[]> icon, java.util.function.Supplier<List<Component>> tooltip, java.util.function.BooleanSupplier visible) {}
    protected void button(int id, int x, int y, java.util.function.Supplier<int[]> icon, java.util.function.Supplier<List<Component>> tooltip, java.util.function.BooleanSupplier visible) { buttons.add(new Button(id, x, y, 16, 16, icon, tooltip, visible)); }
    protected void tab(int id, int x, int y, int[] icon, Component tooltip) { buttons.add(new Button(id, x, y, 22, 22, () -> icon, () -> List.of(tooltip), () -> true)); }

    protected void drawButtons(GuiGraphics gui) {
        for (var b : buttons) {
            if (!b.visible().getAsBoolean()) continue;
            if (b.w() == 22) { gui.blit(STATES, leftPos + b.x(), topPos + b.y(), 208, 0, 22, 22); var i = b.icon().get(); gui.blit(STATES, leftPos + b.x() + 3, topPos + b.y() + 3, i[0], i[1], 16, 16); }
            else { gui.blit(STATES, leftPos + b.x(), topPos + b.y(), 240, 240, 16, 16); var i = b.icon().get(); if (i != null) gui.blit(STATES, leftPos + b.x(), topPos + b.y(), i[0], i[1], 16, 16); }
        }
    }
    @Nullable protected Button buttonAt(double mx, double my) {
        for (var b : buttons) if (b.visible().getAsBoolean() && mx >= leftPos + b.x() && mx < leftPos + b.x() + b.w() && my >= topPos + b.y() && my < topPos + b.y() + b.h()) return b;
        return null;
    }
    protected void drawCardSlots(GuiGraphics gui) {
        for (var slot : menu.slots) if (menu.isUpgradeOrToolSlot(slot)) gui.blit(STATES, leftPos + slot.x, topPos + slot.y, 240, 208, 16, 16);
    }

    /** WidgetAspectSlot.drawWidget. */
    protected void aspectSlot(GuiGraphics gui, int x, int y, @Nullable Aspect aspect) {
        gui.blit(IO_BUS, leftPos + x, topPos + y, 79, 39, 18, 18);
        if (aspect != null) CellWorkbenchScreen.drawAspect(gui, aspect, leftPos + x + 1, topPos + y + 1);
    }
    protected boolean overAspectSlot(double mx, double my, int x, int y) { return mx >= leftPos + x && mx < leftPos + x + 18 && my >= topPos + y && my < topPos + y + 18; }
    protected void aspectHover(GuiGraphics gui, int x, int y, @Nullable Aspect aspect, int mx, int my) {
        gui.fillGradient(leftPos + x + 1, topPos + y + 1, leftPos + x + 17, topPos + y + 17, 0x80FFFFFF, 0x80FFFFFF);
        if (aspect != null) gui.renderComponentTooltip(font, List.of(Component.literal(aspect.getName()), Component.literal(aspect.getLocalizedDescription()).withStyle(ChatFormatting.GRAY)), mx, my);
    }

    protected static List<Component> about(String title, String explanation) { return List.of(Component.translatable(title), Component.translatable(explanation).withStyle(ChatFormatting.GRAY)); }
    protected static int[] redstoneIcon(appeng.api.config.RedstoneMode mode) {
        return switch (mode) { case LOW_SIGNAL -> new int[]{0, 0}; case HIGH_SIGNAL -> new int[]{16, 0}; case SIGNAL_PULSE -> new int[]{32, 0}; default -> new int[]{48, 0}; };
    }
    protected static List<Component> redstoneTooltip(appeng.api.config.RedstoneMode mode, boolean emitter) {
        String e = switch (mode) {
            case HIGH_SIGNAL -> emitter ? "gui.tooltips.ae2.EmitLevelAbove" : "gui.tooltips.ae2.ActiveWithSignal";
            case LOW_SIGNAL -> emitter ? "gui.tooltips.ae2.EmitLevelsBelow" : "gui.tooltips.ae2.ActiveWithoutSignal";
            case SIGNAL_PULSE -> "gui.tooltips.ae2.ActiveOnPulse";
            default -> "gui.tooltips.ae2.AlwaysActive";
        };
        return about("gui.tooltips.ae2.RedstoneMode", e);
    }

    /** GuiButtonAllowVoid: void jar with the disabled cross when off. */
    protected void voidButton(GuiGraphics gui, int x, int y, boolean allowed) {
        gui.blit(STATES, leftPos + x, topPos + y, 240, 240, 16, 16);
        gui.renderItem(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("tc4modern:void_jar"))), leftPos + x + 1, topPos + y + 1);
        if (!allowed) { gui.pose().pushPose(); gui.pose().translate(0, 0, 200); gui.blit(STATES, leftPos + x + 1, topPos + y + 1, 0, 128, 16, 16); gui.pose().popPose(); }
    }
    protected static List<Component> voidTooltip(boolean allowed) {
        return List.of(Component.translatable("thaumicenergistics.tooltip.button.void"),
            Component.translatable(allowed ? "thaumicenergistics.tooltip.button.void.allowed" : "thaumicenergistics.tooltip.button.void.disabled").withStyle(ChatFormatting.GRAY),
            Component.translatable("thaumicenergistics.tooltip.button.void.note").withStyle(ChatFormatting.ITALIC));
    }

    protected void click(int id) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        var b = buttonAt(mx, my);
        if (b != null) { click(b.id()); minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1)); return true; }
        return super.mouseClicked(mx, my, button);
    }
    protected boolean buttonTooltip(GuiGraphics gui, int mx, int my) { var b = buttonAt(mx, my); if (b == null) return false; gui.renderComponentTooltip(font, b.tooltip().get(), mx, my); return true; }
}
