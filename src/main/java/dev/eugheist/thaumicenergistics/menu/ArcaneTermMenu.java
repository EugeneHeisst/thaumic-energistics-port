package dev.eugheist.thaumicenergistics.menu;

import java.util.ArrayList;
import java.util.List;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.CraftingMatrixSlot;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;
import dev.eugheist.tc4modern.ArcaneRecipes;
import dev.eugheist.tc4modern.ResearchProgress;
import dev.eugheist.tc4modern.WandData;
import dev.eugheist.tc4modern.WandParts;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.part.ArcaneCraftingTerminalPart;

/**
 * ContainerPartArcaneCraftingTerminal on AE2's ME storage menu: arcane grid, wand, computed result (vanilla recipes first,
 * then arcane recipes paid from the wand), stored armour. Taking the result refills grid slots that ran out from the player's
 * inventory, then from the network (exact item, then the same item with other data); shift-click crafts up to a stack.
 */
public class ArcaneTermMenu extends MEStorageMenu implements appeng.helpers.ICraftingGridMenu {
    public static final SlotSemantic WAND = SlotSemantics.register("THAUMICENERGISTICS_WAND", false);
    public static final SlotSemantic ARMOR = SlotSemantics.register("THAUMICENERGISTICS_ARMOR", false);
    public static final SlotSemantic RESULT = SlotSemantics.register("THAUMICENERGISTICS_ARCANE_RESULT", false);
    public static final MenuType<ArcaneTermMenu> TYPE = MenuTypeBuilder.create((int id, Inventory ip, ArcaneCraftingTerminalPart host) -> new ArcaneTermMenu(ArcaneTermMenu.TYPE, id, ip, host), ArcaneCraftingTerminalPart.class).build(ThaumicEnergistics.id("arcane_crafting_terminal"));
    public static final MenuType<ArcaneTermMenu> WIRELESS_TYPE = MenuTypeBuilder.create((int id, Inventory ip, dev.eugheist.thaumicenergistics.item.WirelessArcaneTerminalItem.Host host) -> new ArcaneTermMenu(ArcaneTermMenu.WIRELESS_TYPE, id, ip, host), dev.eugheist.thaumicenergistics.item.WirelessArcaneTerminalItem.Host.class).build(ThaumicEnergistics.id("wireless_arcane_crafting_terminal"));
    public static void init() {}
    /** AE2 FillCraftingGridFromRecipePacket (JEI "+"): fills the arcane grid from the network, then the player's inventory. */
    @Override public appeng.api.inventories.InternalInventory getCraftingMatrix() { return terminal.grid(); }
    @Override public appeng.api.networking.IGridNode getGridNode() { return terminal instanceof appeng.helpers.WirelessTerminalMenuHost<?> w ? w.getActionableNode() : super.getGridNode(); }

    final ArcaneTermHost terminal;
    final List<Slot> gridSlots = new ArrayList<>();
    final Slot wandSlot, resultSlot;
    final SimpleContainer result = new SimpleContainer(1);
    @Nullable RecipeHolder<CraftingRecipe> vanilla;
    @Nullable ArcaneRecipes.Entry arcane;

    public ArcaneTermMenu(MenuType<?> menuType, int id, Inventory ip, ArcaneTermHost host) {
        super(menuType, id, ip, host, true);
        terminal = host;
        for (int i = 0; i < 9; i++) { var s = new CraftingMatrixSlot(this, host.grid(), i); gridSlots.add(s); addSlot(s, SlotSemantics.CRAFTING_GRID); }
        wandSlot = addSlot(new AppEngSlot(host.wand(), 0) {
            @Override public boolean mayPlace(ItemStack s) { return WandData.isWand(s) && !WandParts.isStaff(s); }
            @Override public int getMaxStackSize() { return 1; }
            @Override public void setChanged() { super.setChanged(); updateResult(); }
        }, WAND);
        var armorSlots = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) {
            var type = armorSlots[i];
            addSlot(new AppEngSlot(host.armor(), i) {
                @Override public boolean mayPlace(ItemStack s) { return s.canEquip(type, getPlayer()); }
                @Override public int getMaxStackSize() { return 1; }
            }, ARMOR);
        }
        resultSlot = addSlot(new Slot(result, 0, 0, 0) {
            @Override public boolean mayPlace(ItemStack s) { return false; }
            @Override public void onTake(Player player, ItemStack stack) { if (!player.level().isClientSide) { stack.onCraftedBy(player.level(), player, stack.getCount()); craft(player); updateResult(); } }
        }, RESULT);
        registerClientAction("clearGrid", this::clearGrid);
        registerClientAction("swapArmor", () -> terminal.swapArmor(getPlayer()));
        registerClientAction("cycleMagnet", this::cycleMagnet);
        updateResult();
    }

    public List<ItemStack> gridItems() { var list = new ArrayList<ItemStack>(9); for (var s : gridSlots) list.add(s.getItem()); return list; }
    public ItemStack wand() { return wandSlot.getItem(); }

    @Override public void slotsChanged(Container container) { super.slotsChanged(container); updateResult(); }
    /** Wireless terminal with ae2wtlib's magnet card: the terminal stack, else EMPTY. */
    public ItemStack magnetTerminal() {
        return terminal instanceof dev.eugheist.thaumicenergistics.item.WirelessArcaneTerminalItem.Host h && dev.eugheist.thaumicenergistics.item.ArcaneMagnet.installed(h.getItemStack()) ? h.getItemStack() : ItemStack.EMPTY;
    }
    public void cycleMagnet() {
        if (isClientSide()) { sendClientAction("cycleMagnet");return; }
        var stack = magnetTerminal();
        if (!stack.isEmpty()) dev.eugheist.thaumicenergistics.item.ArcaneMagnet.mode(stack, (dev.eugheist.thaumicenergistics.item.ArcaneMagnet.mode(stack) + 1) % 3);
    }
    int visTicks;
    @Override public void broadcastChanges() {
        if (!isClientSide()) { if (terminal instanceof dev.eugheist.thaumicenergistics.item.WirelessArcaneTerminalItem.Host && ++visTicks >= 10) { visTicks = 0;chargeWireless(); } updateResult(); }
        super.broadcastChanges();
    }
    /** Wireless terminal: while open, the wand is recharged from the vis relay interfaces of the network it is linked to. */
    void chargeWireless() {
        var node = getGridNode();
        if (node == null || !getLinkStatus().connected() || !WandData.isWand(terminal.wand().getStackInSlot(0))) return;
        for (var vi : node.getGrid().getMachines(dev.eugheist.thaumicenergistics.part.VisInterfacePart.class))
            if ((ArcaneCraftingTerminalPart.charge(terminal.wand(), vi) & 2) != 0) break;
    }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return slot != resultSlot && super.canTakeItemForPickAll(stack, slot); }

    void match() {
        var level = getPlayer().level();var items = gridItems();
        vanilla = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, items), level).orElse(null);
        arcane = vanilla == null ? ArcaneRecipes.find(items, ResearchProgress.completed(getPlayer().getPersistentData())) : null;
    }
    public void updateResult() {
        if (isClientSide()) return;
        match();
        ItemStack out = ItemStack.EMPTY;
        if (vanilla != null) out = vanilla.value().assemble(CraftingInput.of(3, 3, gridItems()), getPlayer().level().registryAccess());
        else if (arcane != null && WandData.consumeCrafting(wand(), arcane.cost(), false, getPlayer())) out = arcane.output().copy();
        if (!ItemStack.matches(out, result.getItem(0))) result.setItem(0, out);
    }

    /** SlotArcaneCraftingResult.onPickupFromSlotViaTransfer. */
    void craft(Player player) {
        match();
        List<ItemStack> remaining = null;int left = 0, top = 0, width = 3;
        if (vanilla != null) {
            // The recipe input is trimmed to the used area (ResultSlot.onTake): map grid slots into it.
            var positioned = CraftingInput.ofPositioned(3, 3, gridItems());
            left = positioned.left();top = positioned.top();width = positioned.input().width();
            remaining = vanilla.value().getRemainingItems(positioned.input());
        }
        else if (arcane == null || !WandData.consumeCrafting(wand(), arcane.cost(), true, player)) return;
        for (int i = 0; i < 9; i++) {
            var stack = gridSlots.get(i).getItem();
            if (stack.isEmpty()) continue;
            int at = (i / 3 - top) * width + (i % 3 - left);
            var remainder = remaining != null ? (at >= 0 && at < remaining.size() ? remaining.get(at) : ItemStack.EMPTY) : stack.getCraftingRemainingItem();
            if (!remainder.isEmpty()) {
                if (stack.getCount() == 1) { terminal.grid().setItemDirect(i, remainder.copy()); continue; }
                terminal.grid().setItemDirect(i, stack.copyWithCount(stack.getCount() - 1));
                if (!player.getInventory().add(remainder.copy())) player.drop(remainder.copy(), false);
                continue;
            }
            if (stack.getCount() == 1) { var refill = replenish(player, stack); terminal.grid().setItemDirect(i, refill); }
            else terminal.grid().setItemDirect(i, stack.copyWithCount(stack.getCount() - 1));
        }
        terminal.saveChanges();
    }

    /** requestCraftingReplenishment: one more of the same item from the player, then the network (exact, then same item). */
    ItemStack replenish(Player player, ItemStack like) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.items.size(); i++) if (ItemStack.isSameItemSameComponents(inv.items.get(i), like)) return inv.removeItem(i, 1);
        var key = AEItemKey.of(like);
        if (StorageHelper.poweredExtraction(energySource, storage, key, 1, getActionSource(), Actionable.MODULATE) == 1) return key.toStack();
        for (var entry : storage.getAvailableStacks()) {
            if (entry.getKey() instanceof AEItemKey other && other.getItem() == like.getItem() && !other.equals(key)
                && StorageHelper.poweredExtraction(energySource, storage, other, 1, getActionSource(), Actionable.MODULATE) == 1) return other.toStack();
        }
        return ItemStack.EMPTY;
    }

    /** clearCraftingGrid: back into the network, the rest to the player. */
    public void clearGrid() {
        if (isClientSide()) { sendClientAction("clearGrid"); return; }
        for (int i = 0; i < 9; i++) {
            var stack = terminal.grid().getStackInSlot(i);
            if (stack.isEmpty()) continue;
            long stored = StorageHelper.poweredInsert(energySource, storage, AEItemKey.of(stack), stack.getCount(), getActionSource(), Actionable.MODULATE);
            var left = stack.copyWithCount(stack.getCount() - (int) stored);
            if (!left.isEmpty() && !getPlayer().getInventory().add(left)) getPlayer().drop(left, false);
            terminal.grid().setItemDirect(i, ItemStack.EMPTY);
        }
        terminal.saveChanges();
        updateResult();
    }
    public void swapArmor() { sendClientAction("swapArmor"); }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (isClientSide()) return ItemStack.EMPTY;
        var slot = slots.get(index);
        if (slot == resultSlot) { shiftCraft(player); return ItemStack.EMPTY; }
        if (isPlayerSideSlot(slot) && slot.hasItem() && wandSlot.mayPlace(slot.getItem()) && !wandSlot.hasItem()) {
            wandSlot.set(slot.getItem().split(1)); slot.setChanged(); return ItemStack.EMPTY;
        }
        if (gridSlots.contains(slot) && slot.hasItem()) {
            var stack = slot.getItem();
            long stored = StorageHelper.poweredInsert(energySource, storage, AEItemKey.of(stack), stack.getCount(), getActionSource(), Actionable.MODULATE);
            var left = stack.copyWithCount(stack.getCount() - (int) stored);
            if (!left.isEmpty() && !player.getInventory().add(left)) return ItemStack.EMPTY;
            slot.set(left.isEmpty() ? ItemStack.EMPTY : left);
            return ItemStack.EMPTY;
        }
        return super.quickMoveStack(player, index);
    }

    static boolean fits(Inventory inv, ItemStack stack) {
        int room = 0;
        for (var s : inv.items) room += s.isEmpty() ? stack.getMaxStackSize() : ItemStack.isSameItemSameComponents(s, stack) ? s.getMaxStackSize() - s.getCount() : 0;
        return room >= stack.getCount();
    }

    /** doShiftAutoCrafting: repeat while the same result fits in the player's inventory, up to 64 items. */
    void shiftCraft(Player player) {
        var first = result.getItem(0).copy();
        if (first.isEmpty()) return;
        for (int made = 0; made + first.getCount() <= 64; made += first.getCount()) {
            var out = result.getItem(0);
            if (out.isEmpty() || !ItemStack.isSameItemSameComponents(out, first)) break;
            if (!fits(player.getInventory(), out)) break;
            var give = out.copy();
            player.getInventory().add(give.copy());
            give.onCraftedBy(player.level(), player, give.getCount());
            craft(player);
            updateResult();
        }
        broadcastChanges();
    }
}
