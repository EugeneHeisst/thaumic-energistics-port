package dev.eugheist.thaumicenergistics.menu;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.items.tools.NetworkToolItem;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import dev.eugheist.thaumicenergistics.part.PartMenus;

/**
 * ContainerWithNetworkTool: player inventory, the part's upgrade slots in a column at x=187 and, when the player carries a
 * network tool, its 3x3 upgrade inventory at (187,102). Shift-click sends upgrade cards to the part, then to the tool.
 */
public abstract class PartMenu<P extends PartMenus.Host> extends AbstractContainerMenu {
    @Nullable public final P part;
    protected final Player player;
    public int upgradeStart = -1, upgradeEnd = -1, toolStart = -1, toolEnd = -1, playerStart, playerEnd;

    protected PartMenu(MenuType<?> type, int id, Inventory inventory, @Nullable P part) { super(type, id); this.part = part; this.player = inventory.player; }

    protected void bindPlayer(Inventory inventory, int y, int hotbarY) {
        Integer toolSlot = toolSlot(inventory);
        playerStart = slots.size();
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addPlayerSlot(inventory, 9 + row * 9 + col, 8 + col * 18, y + row * 18, toolSlot);
        for (int col = 0; col < 9; col++) addPlayerSlot(inventory, col, 8 + col * 18, hotbarY, toolSlot);
        playerEnd = slots.size();
    }
    private void addPlayerSlot(Inventory inv, int index, int x, int y, @Nullable Integer locked) {
        addSlot(locked != null && locked == index ? new Slot(inv, index, x, y) { @Override public boolean mayPickup(Player p) { return false; } @Override public boolean mayPlace(ItemStack s) { return false; } } : new Slot(inv, index, x, y));
    }
    @Nullable static Integer toolSlot(Inventory inv) { for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).getItem() instanceof NetworkToolItem) return i; return null; }

    protected void addUpgrades(@Nullable IUpgradeInventory upgrades, int count, int x, int y) {
        var container = upgrades != null ? upgrades.toContainer() : new net.minecraft.world.SimpleContainer(count);
        upgradeStart = slots.size();
        for (int i = 0; i < count; i++) {
            int index = i;
            addSlot(new Slot(container, i, x, y + i * 18) { @Override public boolean mayPlace(ItemStack s) { return upgrades != null && upgrades.isItemValid(index, s); } @Override public int getMaxStackSize() { return 1; } });
        }
        upgradeEnd = slots.size();
    }
    protected void addNetworkTool() {
        appeng.api.inventories.InternalInventory inv;net.minecraft.world.Container container;
        if (player.level().isClientSide) {
            if (!PartMenus.TOOL.get()) return;
            var host = NetworkToolItem.findNetworkToolInv(player);
            inv = host != null ? host.getInventory() : null;
            container = inv != null ? inv.toContainer() : new net.minecraft.world.SimpleContainer(9);
        } else {
            var host = NetworkToolItem.findNetworkToolInv(player);
            if (host == null) return;
            inv = host.getInventory();container = inv.toContainer();
        }
        var tool = inv;
        toolStart = slots.size();
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            int index = col + row * 3;
            addSlot(new Slot(container, index, 187 + col * 18, 102 + row * 18) { @Override public boolean mayPlace(ItemStack s) { return tool == null || tool.isItemValid(index, s); } @Override public int getMaxStackSize() { return 1; } });
        }
        toolEnd = slots.size();
    }
    public boolean hasNetworkTool() { return toolStart >= 0; }
    public boolean isUpgradeOrToolSlot(Slot slot) { int i = slot.index; return i >= upgradeStart && i < upgradeEnd || i >= toolStart && i < toolEnd; }

    /** Shift-click hook for aspect items (filter assignment); true consumes the click. */
    protected boolean quickMoveAspect(ItemStack stack) { return false; }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        var stack = slot.getItem();
        boolean fromPlayer = index >= playerStart && index < playerEnd;
        if (fromPlayer && quickMoveAspect(stack)) { broadcastChanges(); return ItemStack.EMPTY; }
        if (fromPlayer) {
            boolean moved = upgradeStart >= 0 && moveItemStackTo(stack, upgradeStart, upgradeEnd, false) || toolStart >= 0 && moveItemStackTo(stack, toolStart, toolEnd, false);
            if (!moved) {
                int hotbar = playerStart + 27;
                if (index < hotbar ? !moveItemStackTo(stack, hotbar, playerEnd, false) : !moveItemStackTo(stack, playerStart, hotbar, false)) return ItemStack.EMPTY;
            }
        } else if (index >= upgradeStart && index < upgradeEnd) {
            if (!(toolStart >= 0 && moveItemStackTo(stack, toolStart, toolEnd, false)) && !moveItemStackTo(stack, playerStart, playerEnd, true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, playerStart, playerEnd, true)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return ItemStack.EMPTY;
    }

    @Override public boolean stillValid(Player player) { return part != null && part.stillValid(player); }

    /** A data slot that the server recomputes from the part every broadcast. */
    protected interface IntSource { int get(); }
    protected int[] data;
    protected void track(int count, IntSource... sources) {
        data = new int[count];
        for (int i = 0; i < count; i++) {
            int idx = i;
            addDataSlot(new net.minecraft.world.inventory.DataSlot() {
                @Override public int get() { if (part != null && !player.level().isClientSide) data[idx] = sources[idx].get(); return data[idx]; }
                @Override public void set(int value) { data[idx] = value; }
            });
        }
    }
}
