package dev.eugheist.thaumicenergistics.client;

import java.text.DecimalFormat;
import java.util.List;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.api.config.ActionItems;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import thaumcraft.api.aspects.Aspect;
import dev.eugheist.tc4modern.ArcaneRecipes;
import dev.eugheist.tc4modern.ClientKnowledge;
import dev.eugheist.tc4modern.ResearchKnowledge;
import dev.eugheist.tc4modern.ResearchProgress;
import dev.eugheist.tc4modern.WandData;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.menu.ArcaneTermMenu;

/**
 * GuiArcaneCraftingTerminal on AE2's terminal screen: the original background, the clear-grid and swap-armour buttons and the
 * primal vis costs in two columns at (136,90), blinking (alpha 0.25-0.75) while the wand lacks the vis.
 */
public class ArcaneTermScreen extends MEStorageScreen<ArcaneTermMenu> {
    static final DecimalFormat COST = new DecimalFormat("0.#");
    public ArcaneTermScreen(ArcaneTermMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        var clear = new ActionButton(ActionItems.S_STASH, b -> menu.clearGrid());
        clear.setHalfSize(true);clear.setDisableBackground(true);
        widgets.add("clearCraftingGrid", clear);
        widgets.add("swapArmor", new SwapArmorButton(menu));
        addToLeftToolbar(new MagnetButton(menu));
    }

    /** ArcaneCrafingCost list for the current grid, computed like the workbench screen from the synced knowledge. */
    public List<float[]> costs() {
        var out = new java.util.ArrayList<float[]>();
        var level = minecraft.level;var items = menu.gridItems();
        if (level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, net.minecraft.world.item.crafting.CraftingInput.of(3, 3, items), level).isPresent()) return out;
        var data = new CompoundTag();data.put(ResearchKnowledge.KEY, ClientKnowledge.knowledge.copy());
        var recipe = ArcaneRecipes.find(items, ResearchProgress.completed(data));
        if (recipe == null) return out;
        var wand = menu.wand();var primals = Aspect.getPrimalAspects();
        for (int i = 0; i < primals.size(); i++) {
            var aspect = primals.get(i);int base = recipe.cost().getAmount(aspect);
            if (base <= 0) continue;
            int required = base * 100;boolean enough = false;
            if (WandData.isWand(wand)) { required = (int) (required * WandData.craftingModifier(wand, minecraft.player, aspect)); enough = WandData.get(wand, aspect) >= required; }
            out.add(new float[]{i, Math.round(required / 100f * 10f) / 10f, enough ? 1 : 0});
        }
        return out;
    }

    @Override public void drawFG(GuiGraphics gui, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(gui, offsetX, offsetY, mouseX, mouseY);
        // Title cut before the search field (the Russian name is longer than the space AE2 leaves).
        var name = Component.translatable(menu.getType() == ArcaneTermMenu.WIRELESS_TYPE ? "item.thaumicenergistics.wireless_arcane_crafting_terminal" : "item.thaumicenergistics.arcane_crafting_terminal").getString();
        int room = 72;
        if (font.width(name) > room) name = font.plainSubstrByWidth(name, room - font.width("...")) + "...";
        gui.drawString(font, name, 8, 6, 0x404040, false);
        int column = 0, y = imageHeight - 153;
        var primals = Aspect.getPrimalAspects();
        for (var cost : costs()) {
            var aspect = primals.get((int) cost[0]);
            float alpha = cost[2] > 0 ? 1f : pingPong(0.5, 0.25f, 0.75f);
            drawTag(gui, 136 + column * 18, y, aspect, cost[1], alpha);
            if (++column == 2) { column = 0; y += 18; }
        }
    }
    static float pingPong(double speed, float min, float max) {
        double t = (System.currentTimeMillis() / 1000.0) * speed;double phase = t - Math.floor(t);
        double tri = phase < 0.5 ? phase * 2 : 2 - phase * 2;
        return (float) (min + (max - min) * tri);
    }
    /** UtilsFX.drawTag: the coloured aspect icon with its amount in the lower right corner. */
    void drawTag(GuiGraphics gui, int x, int y, Aspect aspect, float amount, float alpha) {
        int c = aspect.getColor();
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, alpha);
        gui.blit(aspect.getImage(), x, y, 0, 0, 16, 16, 16, 16);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        var text = COST.format(amount);
        gui.pose().pushPose();gui.pose().translate(0, 0, 200);gui.pose().scale(.5f, .5f, 1);
        int tx = (x + 16) * 2 - font.width(text), ty = (y + 16) * 2 - 8;
        gui.drawString(font, text, tx + 1, ty + 1, 0, false);gui.drawString(font, text, tx, ty, 0xFFFFFF, false);
        gui.pose().popPose();
    }

    /** Magnet mode of the wireless terminal (ae2wtlib magnet card installed): off / to inventory / into the ME network. */
    static final class MagnetButton extends net.minecraft.client.gui.components.Button {
        final ArcaneTermMenu menu;
        MagnetButton(ArcaneTermMenu menu) { super(0, 0, 16, 16, Component.empty(), b -> menu.cycleMagnet(), DEFAULT_NARRATION);this.menu = menu; }
        @Override protected void renderWidget(GuiGraphics gui, int mx, int my, float partial) {
            var stack = menu.magnetTerminal();visible = !stack.isEmpty();
            if (!visible) return;
            int mode = dev.eugheist.thaumicenergistics.item.ArcaneMagnet.mode(stack);
            gui.fill(getX(), getY(), getX() + 16, getY() + 16, isHovered() ? 0xFFB0B0C8 : 0xFF8B8BA0);
            var card = dev.eugheist.thaumicenergistics.item.ArcaneMagnet.card();
            if (card != null) gui.renderFakeItem(new ItemStack(card), getX(), getY());
            if (mode == dev.eugheist.thaumicenergistics.item.ArcaneMagnet.OFF) gui.fill(getX() + 1, getY() + 7, getX() + 15, getY() + 9, 0xFFD03030);
            else if (mode == dev.eugheist.thaumicenergistics.item.ArcaneMagnet.NETWORK) gui.fill(getX() + 10, getY() + 10, getX() + 15, getY() + 15, 0xFF30A0F0);
            setTooltip(Tooltip.create(Component.translatable("thaumicenergistics.magnet.mode", dev.eugheist.thaumicenergistics.item.ArcaneMagnet.describe(mode))));
        }
    }

    /** GuiButtonSwapArmor: the 8x8 ThE swap icon on a regular button. */
    static final class SwapArmorButton extends AbstractWidget {
        static final net.minecraft.resources.ResourceLocation STATES = ThaumicEnergistics.id("textures/gui/states.png");
        static final net.minecraft.resources.ResourceLocation AE = ThaumicEnergistics.id("textures/gui/ae2_states.png");
        final ArcaneTermMenu menu;
        SwapArmorButton(ArcaneTermMenu menu) {
            super(0, 0, 8, 8, Component.translatable("thaumicenergistics.tooltip.button.swap.armor.title"));
            this.menu = menu;
            setTooltip(Tooltip.create(Component.translatable("thaumicenergistics.tooltip.button.swap.armor.title").append("\n").append(Component.translatable("thaumicenergistics.tooltip.button.swap.armor").withStyle(ChatFormatting.GRAY))));
        }
        @Override protected void renderWidget(GuiGraphics gui, int mx, int my, float partial) {
            gui.blit(AE, getX(), getY(), 8, 8, 240, 240, 16, 16, 256, 256);
            gui.blit(STATES, getX(), getY(), 8, 8, 0, 0, 16, 16, 256, 256);
        }
        @Override public void onClick(double mx, double my) { menu.swapArmor(); }
        @Override protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
    }
}
