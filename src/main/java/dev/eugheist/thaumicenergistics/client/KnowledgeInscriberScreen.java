package dev.eugheist.thaumicenergistics.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.menu.KnowledgeInscriberMenu;

/** GuiKnowledgeInscriber: 210x244, the shimmering research strip behind the arrow, save/erase (141,109) and clear (80,89). */
public final class KnowledgeInscriberScreen extends AbstractContainerScreen<KnowledgeInscriberMenu> {
    static final ResourceLocation BG = ThaumicEnergistics.id("textures/gui/knowledge.inscriber.png");
    static final ResourceLocation RESEARCH = ThaumicEnergistics.id("textures/research/research_background.png");
    static final ResourceLocation STATES = ThaumicEnergistics.id("textures/gui/ae2_states.png");
    public KnowledgeInscriberScreen(KnowledgeInscriberMenu menu, Inventory inv, Component title) { super(menu, inv, title); imageWidth = 210; imageHeight = 244; inventoryLabelY = 1000; }

    @Override protected void renderBg(GuiGraphics gui, float partial, int mx, int my) {
        long slow = System.currentTimeMillis() / 100L;
        float red = Math.abs(1f - (slow % 50L) / 50f * 2f), green = 1f - red;
        RenderSystem.setShaderColor(red, green, 1, 1);
        gui.blit(RESEARCH, leftPos + 80, topPos + 106, 55 - (int) (slow % 10L), 25, 35, 20);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        gui.blit(BG, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        var state = menu.saveState();
        gui.blit(STATES, leftPos + 141, topPos + 109, 240, 240, 16, 16);
        if (state == KnowledgeInscriberMenu.SaveState.SAVE) gui.blit(STATES, leftPos + 141, topPos + 109, 0, 176, 16, 16);
        if (state == KnowledgeInscriberMenu.SaveState.DELETE) gui.blit(STATES, leftPos + 141, topPos + 109, 0, 192, 16, 16);
        gui.pose().pushPose();gui.pose().translate(leftPos + 80, topPos + 89, 0);gui.pose().scale(.5f, .5f, 1);
        gui.blit(STATES, 0, 0, 240, 240, 16, 16);gui.blit(STATES, 0, 0, 96, 0, 16, 16);
        gui.pose().popPose();
    }
    @Override protected void renderLabels(GuiGraphics gui, int mx, int my) { gui.drawString(font, title, 6, 6, 0, false); }
    boolean over(double mx, double my, int x, int y, int s) { return mx >= leftPos + x && mx < leftPos + x + s && my >= topPos + y && my < topPos + y + s; }
    @Override public void render(GuiGraphics gui, int mx, int my, float partial) {
        super.render(gui, mx, my, partial);
        if (over(mx, my, 141, 109, 16)) {
            String key = switch (menu.saveState()) { case SAVE -> "save"; case DELETE -> "delete"; case FULL -> "full"; case MISSING -> "missing"; default -> "invalid"; };
            gui.renderTooltip(font, Component.translatable("thaumicenergistics.tooltip.button.inscriber." + key), mx, my);
        } else if (over(mx, my, 80, 89, 8)) gui.renderComponentTooltip(font, java.util.List.of(Component.translatable("gui.tooltips.ae2.Stash"), Component.translatable("gui.tooltips.ae2.StashDesc").withStyle(net.minecraft.ChatFormatting.GRAY)), mx, my);
        else renderTooltip(gui, mx, my);
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (over(mx, my, 141, 109, 16)) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, KnowledgeInscriberMenu.BUTTON_SAVE); return true; }
        if (over(mx, my, 80, 89, 8)) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, KnowledgeInscriberMenu.BUTTON_CLEAR); return true; }
        return super.mouseClicked(mx, my, button);
    }
}
