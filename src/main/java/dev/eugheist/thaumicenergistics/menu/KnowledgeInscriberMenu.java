package dev.eugheist.thaumicenergistics.menu;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import dev.eugheist.tc4modern.ArcaneRecipes;
import dev.eugheist.tc4modern.ResearchProgress;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.block.KnowledgeInscriberEntity;
import dev.eugheist.thaumicenergistics.craft.ArcanePattern;
import dev.eugheist.thaumicenergistics.craft.KnowledgeCore;

/**
 * ContainerKnowledgeInscriber: knowledge core at (186,8), its 21 stored results at (26,18) (click loads the pattern), a
 * 3x3 ghost grid at (26,90) matched against the player's arcane recipes, result at (116,108). Button 0 saves or erases,
 * button 1 clears the grid.
 */
public final class KnowledgeInscriberMenu extends AbstractContainerMenu {
    public enum SaveState { INVALID, FULL, MISSING, SAVE, DELETE }
    public static final int BUTTON_SAVE = 0, BUTTON_CLEAR = 1;
    @Nullable final KnowledgeInscriberEntity inscriber;
    final Player player;
    final Container core;
    final SimpleContainer stored = new SimpleContainer(KnowledgeCore.MAX), grid = new SimpleContainer(9), result = new SimpleContainer(1);
    public final int coreSlot, storedStart, gridStart, resultSlot, playerStart;
    @Nullable ArcaneRecipes.Entry recipe;
    final DataSlot state = DataSlot.standalone();

    public KnowledgeInscriberMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) { this(id, inv, inv.player.level().getBlockEntity(buf.readBlockPos()) instanceof KnowledgeInscriberEntity e ? e : null); }
    public KnowledgeInscriberMenu(int id, Inventory inv, @Nullable KnowledgeInscriberEntity inscriber) {
        super(ThaumicEnergistics.KNOWLEDGE_INSCRIBER_MENU.get(), id);
        this.inscriber = inscriber;this.player = inv.player;
        core = inscriber != null ? inscriber.inventory : new SimpleContainer(1);
        coreSlot = slots.size();
        addSlot(new Slot(core, 0, 186, 8) { @Override public boolean mayPlace(ItemStack s) { return s.is(ThaumicEnergistics.KNOWLEDGE_CORE.get()); } @Override public void setChanged() { super.setChanged(); refresh(); } });
        storedStart = slots.size();
        for (int r = 0; r < 3; r++) for (int c = 0; c < 7; c++) addSlot(new Slot(stored, r * 7 + c, 26 + c * 18, 18 + r * 18) { @Override public boolean mayPlace(ItemStack s) { return false; } @Override public boolean mayPickup(Player p) { return false; } });
        gridStart = slots.size();
        for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) addSlot(new Slot(grid, r * 3 + c, 26 + c * 18, 90 + r * 18) { @Override public boolean mayPlace(ItemStack s) { return false; } @Override public boolean mayPickup(Player p) { return false; } });
        resultSlot = slots.size();
        addSlot(new Slot(result, 0, 116, 108) { @Override public boolean mayPlace(ItemStack s) { return false; } @Override public boolean mayPickup(Player p) { return false; } });
        playerStart = slots.size();
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, 9 + r * 9 + c, 8 + c * 18, 162 + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, 220));
        addDataSlot(state);
        refresh();
    }

    public SaveState saveState() { return SaveState.values()[Math.max(0, Math.min(4, state.get()))]; }
    ItemStack coreStack() { return core.getItem(0); }

    void refresh() {
        if (player.level().isClientSide) return;
        var level = player.level();var core = coreStack();
        var list = core.isEmpty() ? List.<ArcanePattern>of() : KnowledgeCore.read(core, level.registryAccess());
        for (int i = 0; i < KnowledgeCore.MAX; i++) stored.setItem(i, i < list.size() ? list.get(i).output().copy() : ItemStack.EMPTY);
        var items = new ArrayList<ItemStack>();for (int i = 0; i < 9; i++) items.add(grid.getItem(i));
        recipe = ArcaneRecipes.find(items, ResearchProgress.completed(player.getPersistentData()));
        result.setItem(0, recipe == null ? ItemStack.EMPTY : recipe.output().copy());
        SaveState s;
        if (core.isEmpty()) s = SaveState.MISSING;
        else if (recipe == null) s = SaveState.INVALID;
        else if (KnowledgeCore.forOutput(core, recipe.output(), level.registryAccess()) != null) s = SaveState.DELETE;
        else s = list.size() >= KnowledgeCore.MAX ? SaveState.FULL : SaveState.SAVE;
        state.set(s.ordinal());
        broadcastChanges();
    }

    void load(ArcanePattern pattern) { for (int i = 0; i < 9; i++) grid.setItem(i, pattern.preferred(i).copy()); refresh(); }

    @Override public void clicked(int slotId, int button, ClickType type, Player p) {
        if (slotId >= gridStart && slotId < gridStart + 9) {
            if (!p.level().isClientSide) { var carried = getCarried(); grid.setItem(slotId - gridStart, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1)); refresh(); }
            return;
        }
        if (slotId >= storedStart && slotId < storedStart + KnowledgeCore.MAX) {
            if (!p.level().isClientSide && !coreStack().isEmpty()) {
                var out = stored.getItem(slotId - storedStart);
                var pattern = out.isEmpty() ? null : KnowledgeCore.forOutput(coreStack(), out, p.level().registryAccess());
                if (pattern != null) load(pattern);
            }
            return;
        }
        super.clicked(slotId, button, type, p);
    }

    @Override public boolean clickMenuButton(Player p, int button) {
        if (button == BUTTON_CLEAR) { for (int i = 0; i < 9; i++) grid.setItem(i, ItemStack.EMPTY); refresh(); return true; }
        if (button != BUTTON_SAVE) return false;
        var level = p.level();var core = coreStack();
        if (saveState() == SaveState.SAVE && recipe != null) {
            var items = new ArrayList<ItemStack>();for (int i = 0; i < 9; i++) items.add(grid.getItem(i));
            var pattern = ArcanePattern.of(items, recipe);
            if (pattern != null && KnowledgeCore.add(core, pattern, level.registryAccess())) core(core);
        } else if (saveState() == SaveState.DELETE && recipe != null) {
            if (KnowledgeCore.remove(core, recipe.output(), level.registryAccess())) core(core);
        }
        refresh();
        return true;
    }
    void core(ItemStack changed) { this.core.setItem(0, changed); this.core.setChanged(); }

    @Override public ItemStack quickMoveStack(Player p, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(p)) return ItemStack.EMPTY;
        var stack = slot.getItem();
        if (index == coreSlot) { if (!moveItemStackTo(stack, playerStart, slots.size(), true)) return ItemStack.EMPTY; }
        else if (index >= playerStart) {
            if (stack.is(ThaumicEnergistics.KNOWLEDGE_CORE.get()) && !slots.get(coreSlot).hasItem()) { if (!moveItemStackTo(stack, coreSlot, coreSlot + 1, false)) return ItemStack.EMPTY; }
            else { int hotbar = playerStart + 27; if (index < hotbar ? !moveItemStackTo(stack, hotbar, slots.size(), false) : !moveItemStackTo(stack, playerStart, hotbar, false)) return ItemStack.EMPTY; }
        } else return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        refresh();
        return ItemStack.EMPTY;
    }
    @Override public boolean stillValid(Player p) { return inscriber != null && !inscriber.isRemoved() && p.distanceToSqr(inscriber.getBlockPos().getCenter()) <= 64; }
}
