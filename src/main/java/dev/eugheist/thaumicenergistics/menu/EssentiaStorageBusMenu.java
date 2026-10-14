package dev.eugheist.thaumicenergistics.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.me.EssentiaTiles;
import dev.eugheist.thaumicenergistics.part.EssentiaStorageBusPart;
import dev.eugheist.thaumicenergistics.part.PartMenus;

/** ContainerPartEssentiaStorageBus: 9 filter widgets (buttons 0..8), inverter slot at (187,8), 20 = priority, 21 = void. */
public final class EssentiaStorageBusMenu extends PartMenu<EssentiaStorageBusPart> {
    public static final int BUTTON_PRIORITY = 20, BUTTON_VOID = 21;
    public EssentiaStorageBusMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) { this(id, inventory, PartMenus.read(inventory, buf, EssentiaStorageBusPart.class)); }
    public EssentiaStorageBusMenu(int id, Inventory inventory, @Nullable EssentiaStorageBusPart part) {
        super(ThaumicEnergistics.STORAGE_BUS_MENU.get(), id, inventory, part);
        bindPlayer(inventory, 102, 160);
        addUpgrades(part == null ? null : part.getUpgrades(), 1, 187, 8);
        addNetworkTool();
        var sources = new IntSource[10];
        for (int i = 0; i < 9; i++) { int idx = i; sources[i] = () -> EssentiaTiles.id(part.getAspect(idx)); }
        sources[9] = () -> part.isVoidAllowed() ? 1 : 0;
        track(10, sources);
    }
    @Nullable public Aspect aspect(int i) { return EssentiaTiles.byId(data[i]); }
    public boolean voidAllowed() { return data[9] != 0; }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (part == null) return false;
        if (button >= 0 && button < 9) {
            var held = EssentiaTiles.filterAspect(getCarried());
            if (held != null) for (int i = 0; i < 9; i++) if (part.getAspect(i) == held) return true;
            part.setAspect(button, held);
            return true;
        }
        if (button == BUTTON_VOID) { part.setVoidAllowed(!part.isVoidAllowed()); return true; }
        if (button == BUTTON_PRIORITY) { part.openPriority(player); return true; }
        return false;
    }
    @Override protected boolean quickMoveAspect(ItemStack stack) { return part != null && part.addAspect(EssentiaTiles.filterAspect(stack)); }
}
