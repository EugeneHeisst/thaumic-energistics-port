package dev.eugheist.thaumicenergistics.item;

import java.util.function.BiConsumer;
import appeng.api.ids.AEComponents;
import appeng.api.inventories.InternalInventory;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.menu.ArcaneTermHost;
import dev.eugheist.thaumicenergistics.menu.ArcaneTermMenu;

/**
 * Wireless arcane crafting terminal: the arcane crafting terminal GUI through a linked wireless access point (link it in the
 * access point, charge it in a charger). Grid, wand and stored armour live on the item like AE2's wireless crafting terminal.
 */
public class WirelessArcaneTerminalItem extends WirelessTerminalItem implements baubles.api.IBauble {
    /** Worn in any Baubles slot; the wireless terminal key opens it from there. */
    @Override public baubles.api.BaubleType getBaubleType(net.minecraft.world.item.ItemStack stack) { return baubles.api.BaubleType.TRINKET; }
    public WirelessArcaneTerminalItem(Properties props) { super(() -> appeng.core.AEConfig.instance().getWirelessTerminalBattery().getAsDouble(), props.stacksTo(1)); }
    @Override public MenuType<?> getMenuType() { return ArcaneTermMenu.WIRELESS_TYPE; }
    /** Three upgrade slots: energy cards and ae2wtlib's magnet card. */
    @Override public appeng.api.upgrades.IUpgradeInventory getUpgrades(net.minecraft.world.item.ItemStack stack) {
        return appeng.api.upgrades.UpgradeInventories.forItem(stack, 3, (s, upgrades) -> setAEMaxPowerMultiplier(s, 1 + appeng.api.upgrades.Upgrades.getEnergyCardMultiplier(upgrades)));
    }
    @Override @Nullable public Host getMenuHost(Player player, ItemMenuHostLocator locator, @Nullable BlockHitResult hit) {
        return new Host(this, player, locator, (p, sub) -> openFromInventory(p, locator, true));
    }

    public static class Host extends WirelessTerminalMenuHost<WirelessArcaneTerminalItem> implements ArcaneTermHost {
        final AppEngInternalInventory grid, wand, armor;
        Host(WirelessArcaneTerminalItem item, Player player, ItemMenuHostLocator locator, BiConsumer<Player, ISubMenu> back) {
            super(item, player, locator, back);
            grid = inventory(player, AEComponents.CRAFTING_INV, 9, 64);
            wand = inventory(player, ThaumicEnergistics.ARCANE_WAND_INV.get(), 1, 1);
            armor = inventory(player, ThaumicEnergistics.ARCANE_ARMOR_INV.get(), 4, 1);
        }
        AppEngInternalInventory inventory(Player player, DataComponentType<ItemContainerContents> type, int size, int max) {
            var inv = new AppEngInternalInventory(new InternalInventoryHost() {
                @Override public void saveChangedInventory(AppEngInternalInventory changed) { getItemStack().set(type, changed.toItemContainerContents()); }
                @Override public boolean isClientSide() { return player.level().isClientSide(); }
            }, size, max);
            inv.fromItemContainerContents(getItemStack().getOrDefault(type, ItemContainerContents.EMPTY));
            return inv;
        }
        @Override public InternalInventory grid() { return grid; }
        @Override public InternalInventory wand() { return wand; }
        @Override public InternalInventory armor() { return armor; }
        @Override public void saveChanges() {
            var stack = getItemStack();
            stack.set(AEComponents.CRAFTING_INV, grid.toItemContainerContents());
            stack.set(ThaumicEnergistics.ARCANE_WAND_INV.get(), wand.toItemContainerContents());
            stack.set(ThaumicEnergistics.ARCANE_ARMOR_INV.get(), armor.toItemContainerContents());
        }
    }
}
