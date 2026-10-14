package dev.eugheist.thaumicenergistics.menu;

import appeng.api.config.RedstoneMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.me.EssentiaTiles;
import dev.eugheist.thaumicenergistics.part.EssentiaExportBusPart;
import dev.eugheist.thaumicenergistics.part.EssentiaIOBusPart;
import dev.eugheist.thaumicenergistics.part.PartMenus;

/**
 * ContainerPartEssentiaIOBus: player inventory at y102/160, four upgrade slots at (187,8), network tool. Buttons 0..8 are the
 * filter widgets (carried aspect item sets, empty hand clears), 20 cycles the redstone mode, 21 toggles void mode.
 */
public final class EssentiaIOBusMenu extends PartMenu<EssentiaIOBusPart> {
    public static final int BUTTON_REDSTONE = 20, BUTTON_VOID = 21;
    public EssentiaIOBusMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) { this(id, inventory, PartMenus.read(inventory, buf, EssentiaIOBusPart.class)); }
    public EssentiaIOBusMenu(int id, Inventory inventory, @Nullable EssentiaIOBusPart part) {
        super(ThaumicEnergistics.IO_BUS_MENU.get(), id, inventory, part);
        bindPlayer(inventory, 102, 160);
        addUpgrades(part == null ? null : part.getUpgrades(), 4, 187, 8);
        addNetworkTool();
        var sources = new IntSource[13];
        for (int i = 0; i < 9; i++) { int idx = i; sources[i] = () -> EssentiaTiles.id(part.getAspect(idx)); }
        sources[9] = () -> part.filterSize();
        sources[10] = () -> part.redstoneControlled() ? 1 : 0;
        sources[11] = () -> part.getRSMode().ordinal();
        sources[12] = () -> part.isVoidAllowed() ? 1 : 0;
        track(13, sources);
    }
    @Nullable public Aspect aspect(int i) { return EssentiaTiles.byId(data[i]); }
    public int filterSize() { return data[9]; }
    public boolean redstoneControlled() { return data[10] != 0; }
    public RedstoneMode redstoneMode() { return RedstoneMode.values()[Math.max(0, Math.min(RedstoneMode.values().length - 1, data[11]))]; }
    public boolean voidAllowed() { return data[12] != 0; }
    public boolean isExport() { return part instanceof EssentiaExportBusPart; }
    public boolean slotOpen(int i) { return EssentiaIOBusPart.SLOT_TIER[i] <= filterSize(); }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (part == null) return false;
        if (button >= 0 && button < 9) {
            var held = EssentiaTiles.filterAspect(getCarried());
            if (held != null) for (int i = 0; i < 9; i++) if (part.getAspect(i) == held) return true;
            part.setAspect(button, held);
            return true;
        }
        if (button == BUTTON_REDSTONE) { var modes = RedstoneMode.values(); part.setRSMode(modes[(part.getRSMode().ordinal() + 1) % modes.length]); return true; }
        if (button == BUTTON_VOID) { part.toggleVoid(); return true; }
        return false;
    }
    @Override protected boolean quickMoveAspect(ItemStack stack) { return part != null && part.addAspect(EssentiaTiles.filterAspect(stack)); }
}
