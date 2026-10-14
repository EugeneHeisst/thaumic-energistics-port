package dev.eugheist.thaumicenergistics.item;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.menu.locator.MenuLocators;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;

/**
 * Magnet card (ae2wtlib's ae2wtlib:magnet_card) in the wireless arcane crafting terminal: while the terminal is anywhere in
 * the inventory, items within 16 blocks fly to the player, or go straight into the linked ME network. Modes: off, to the
 * inventory, into the network; switched by the terminal GUI button or ae2wtlib's magnet key. Sneaking pauses it.
 */
@EventBusSubscriber(modid = ThaumicEnergistics.ID)
public final class ArcaneMagnet {
    private ArcaneMagnet() {}
    public static final int OFF = 0, INVENTORY = 1, NETWORK = 2;
    static final double RANGE = 16;

    @Nullable public static Item card() { var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("ae2wtlib", "magnet_card")); return item == Items.AIR ? null : item; }
    public static boolean installed(ItemStack terminal) {
        var card = card();
        return card != null && terminal.getItem() instanceof WirelessArcaneTerminalItem item && item.getUpgrades(terminal).isInstalled(card);
    }
    public static int mode(ItemStack terminal) { return terminal.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("ThEMagnet"); }
    public static void mode(ItemStack terminal, int mode) { CustomData.update(DataComponents.CUSTOM_DATA, terminal, t -> t.putInt("ThEMagnet", mode)); }
    public static Component describe(int mode) {
        return Component.translatable("thaumicenergistics.magnet." + (mode == INVENTORY ? "inventory" : mode == NETWORK ? "network" : "off")).withStyle(mode == OFF ? ChatFormatting.RED : ChatFormatting.GREEN);
    }
    /** Inventory slot of the first wireless arcane terminal with a magnet card, -1 if none. */
    public static int find(Player player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (installed(inv.getItem(i))) return i;
        return -1;
    }
    public static boolean cycle(Player player) {
        var at = locate(player);
        if (at == null) return false;
        var stack = at.locateItem(player);int next = (mode(stack) + 1) % 3;mode(stack, next);
        player.displayClientMessage(Component.translatable("thaumicenergistics.magnet.mode", describe(next)), true);
        return true;
    }

    /** The terminal the magnet works from: inventory first, then the Baubles slots. */
    @Nullable static appeng.menu.locator.ItemMenuHostLocator locate(Player player) {
        int slot = find(player);
        if (slot >= 0) return MenuLocators.forInventorySlot(slot);
        var baubles = dev.eugheist.tc4modern.BaublesInventory.of(player);
        for (int i = 0; i < baubles.getContainerSize(); i++) if (installed(baubles.getItem(i))) return new BaubleLocator(i);
        return null;
    }
    @SubscribeEvent static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 4 != 0 || player.isSpectator() || player.isShiftKeyDown()) return;
        var at = locate(player);
        if (at == null) return;
        var stack = at.locateItem(player);int mode = mode(stack);
        if (mode == OFF) return;
        var item = (WirelessArcaneTerminalItem) stack.getItem();
        if (item.getAECurrentPower(stack) <= 0) return;
        var items = player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(RANGE), e -> e.isAlive() && !e.hasPickUpDelay() && !e.getItem().isEmpty());
        if (items.isEmpty()) return;
        WirelessArcaneTerminalItem.Host host = mode == NETWORK ? item.getMenuHost(player, at, null) : null;
        for (var entity : items) {
            if (host != null && host.getLinkStatus().connected()) {
                var what = entity.getItem();var key = AEItemKey.of(what);
                long inserted = key == null ? 0 : host.insert(player, key, what.getCount(), Actionable.MODULATE);
                if (inserted > 0) { var rest = what.copy();rest.shrink((int) inserted);if (rest.isEmpty()) { entity.discard();continue; } entity.setItem(rest); }
            }
            entity.setPos(player.getX(), player.getY(), player.getZ());
        }
    }
}
