package dev.eugheist.thaumicenergistics.menu;

import java.util.List;
import appeng.items.tools.NetworkToolItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.block.ArcaneAssemblerEntity;
import dev.eugheist.thaumicenergistics.craft.KnowledgeCore;

/**
 * ContainerArcaneAssembler: knowledge core (187,8), its 21 patterns shown at (26,25), four speed-card slots (187,26), the
 * item being assembled (14,87), four discount armour slots (210,26), player inventory at y115/173, network tool panel.
 * Vis levels and progress travel as data slots.
 */
public final class ArcaneAssemblerMenu extends AbstractContainerMenu {
    @Nullable public final ArcaneAssemblerEntity assembler;
    final Player player;
    final SimpleContainer shown = new SimpleContainer(KnowledgeCore.MAX + 1);
    public final int coreSlot, playerStart, upgradeStart, armorStart;
    public int toolStart = -1;
    final DataSlot[] vis = new DataSlot[6];
    final DataSlot progress = DataSlot.standalone();
    ItemStack lastCore = ItemStack.EMPTY;

    public ArcaneAssemblerMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, inv.player.level().getBlockEntity(buf.readBlockPos()) instanceof ArcaneAssemblerEntity e ? e : null, buf.readBoolean());
    }
    public ArcaneAssemblerMenu(int id, Inventory inv, @Nullable ArcaneAssemblerEntity assembler) { this(id, inv, assembler, NetworkToolItem.findNetworkToolInv(inv.player) != null); }
    ArcaneAssemblerMenu(int id, Inventory inv, @Nullable ArcaneAssemblerEntity assembler, boolean tool) {
        super(ThaumicEnergistics.ARCANE_ASSEMBLER_MENU.get(), id);
        this.assembler = assembler;this.player = inv.player;
        Container core = assembler != null ? assembler.core : new SimpleContainer(1);
        coreSlot = slots.size();
        addSlot(new Slot(core, 0, 187, 8) { @Override public boolean mayPlace(ItemStack s) { return s.is(ThaumicEnergistics.KNOWLEDGE_CORE.get()); } @Override public int getMaxStackSize() { return 1; } });
        for (int r = 0; r < 3; r++) for (int c = 0; c < 7; c++) addSlot(locked(shown, r * 7 + c, 26 + c * 18, 25 + r * 18));
        addSlot(locked(shown, KnowledgeCore.MAX, 14, 87));
        upgradeStart = slots.size();
        var upgrades = assembler != null ? assembler.getUpgrades().toContainer() : new SimpleContainer(4);
        for (int i = 0; i < 4; i++) { int idx = i; addSlot(new Slot(upgrades, i, 187, 26 + i * 18) { @Override public boolean mayPlace(ItemStack s) { return assembler != null && assembler.getUpgrades().isItemValid(idx, s); } @Override public int getMaxStackSize() { return 1; } }); }
        armorStart = slots.size();
        Container armor = assembler != null ? assembler.armor : new SimpleContainer(4);
        var types = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) { var type = types[i]; addSlot(new Slot(armor, i, 210, 26 + i * 18) { @Override public boolean mayPlace(ItemStack s) { return s.canEquip(type, player); } @Override public int getMaxStackSize() { return 1; } }); }
        playerStart = slots.size();
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, 9 + r * 9 + c, 8 + c * 18, 115 + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, 173));
        if (tool) {
            var host = NetworkToolItem.findNetworkToolInv(player);
            var toolInv = host != null ? host.getInventory() : null;var container = toolInv != null ? toolInv.toContainer() : new SimpleContainer(9);
            toolStart = slots.size();
            for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) { int idx = c + r * 3; addSlot(new Slot(container, idx, 187 + c * 18, 137 + r * 18) { @Override public boolean mayPlace(ItemStack s) { return toolInv == null || toolInv.isItemValid(idx, s); } @Override public int getMaxStackSize() { return 1; } }); }
        }
        for (int i = 0; i < 6; i++) vis[i] = addDataSlot(DataSlot.standalone());
        addDataSlot(progress);
        sync();
    }
    static Slot locked(Container c, int i, int x, int y) { return new Slot(c, i, x, y) { @Override public boolean mayPlace(ItemStack s) { return false; } @Override public boolean mayPickup(Player p) { return false; } }; }
    public boolean hasNetworkTool() { return toolStart >= 0; }
    public int vis(int i) { return vis[i].get(); }
    public float progress() { return progress.get() / 100f; }

    void sync() {
        if (assembler == null || player.level().isClientSide) return;
        var core = assembler.core.getItem(0);
        if (!ItemStack.matches(core, lastCore)) {
            lastCore = core.copy();
            var list = core.isEmpty() ? List.<dev.eugheist.thaumicenergistics.craft.ArcanePattern>of() : KnowledgeCore.read(core, player.level().registryAccess());
            for (int i = 0; i < KnowledgeCore.MAX; i++) shown.setItem(i, i < list.size() ? list.get(i).output().copy() : ItemStack.EMPTY);
        }
        var target = assembler.target();
        shown.setItem(KnowledgeCore.MAX, target == null ? ItemStack.EMPTY : target.copy());
        for (int i = 0; i < 6; i++) vis[i].set(assembler.storedVis.getAmount(ArcaneAssemblerEntity.PRIMALS[i]));
        progress.set((int) (assembler.progress() * 100));
    }
    @Override public void broadcastChanges() { sync(); super.broadcastChanges(); }

    @Override public ItemStack quickMoveStack(Player p, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(p)) return ItemStack.EMPTY;
        var stack = slot.getItem();
        if (index >= playerStart && (toolStart < 0 || index < toolStart)) {
            boolean moved = slots.get(coreSlot).mayPlace(stack) && !slots.get(coreSlot).hasItem() && moveItemStackTo(stack, coreSlot, coreSlot + 1, false)
                || moveItemStackTo(stack, upgradeStart, upgradeStart + 4, false) || moveItemStackTo(stack, armorStart, armorStart + 4, false);
            if (!moved) { int hotbar = playerStart + 27; if (index < hotbar ? !moveItemStackTo(stack, hotbar, playerStart + 36, false) : !moveItemStackTo(stack, playerStart, hotbar, false)) return ItemStack.EMPTY; }
        } else if (!moveItemStackTo(stack, playerStart, playerStart + 36, true)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return ItemStack.EMPTY;
    }
    @Override public boolean stillValid(Player p) { return assembler != null && !assembler.isRemoved() && p.distanceToSqr(assembler.getBlockPos().getCenter()) <= 64; }
}
