package dev.eugheist.thaumicenergistics.menu;

import java.util.ArrayList;
import java.util.List;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
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
import thaumcraft.api.aspects.Aspect;
import dev.eugheist.tc4modern.RecipeAspects;
import dev.eugheist.tc4modern.api.TC4Addons;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.block.DistillationEncoderEntity;
import dev.eugheist.thaumicenergistics.me.EssentiaKey;
import dev.eugheist.thaumicenergistics.me.EssentiaTiles;

/**
 * ContainerDistillationPatternEncoder: the "Thaumometer" ghost slot (15,69) shows the item's aspects in six slots (65,24),
 * largest first, only once the player has scanned it; clicking one selects it (116,69). Encode (button 0) writes an AE2
 * processing pattern "1 item -> N essentia of the selected aspect" onto the encoded pattern, or a blank one. An encoded
 * distillation pattern put in (146,113) is read back into the slots.
 */
public final class DistillationEncoderMenu extends AbstractContainerMenu {
    public static final int BUTTON_ENCODE = 0;
    @Nullable final DistillationEncoderEntity encoder;
    final Player player;
    final Container inv;
    public final int sourceSlot, blankSlot, encodedSlot, playerStart;
    final DataSlot[] aspects = new DataSlot[6], amounts = new DataSlot[6];
    final DataSlot selected = DataSlot.standalone(), scanned = DataSlot.standalone();
    ItemStack lastSource = ItemStack.EMPTY, lastPattern = ItemStack.EMPTY;

    public DistillationEncoderMenu(int id, Inventory pi, RegistryFriendlyByteBuf buf) { this(id, pi, pi.player.level().getBlockEntity(buf.readBlockPos()) instanceof DistillationEncoderEntity e ? e : null); }
    public DistillationEncoderMenu(int id, Inventory pi, @Nullable DistillationEncoderEntity encoder) {
        super(ThaumicEnergistics.DISTILLATION_ENCODER_MENU.get(), id);
        this.encoder = encoder;this.player = pi.player;
        inv = encoder != null ? encoder.inventory : new SimpleContainer(3);
        sourceSlot = slots.size();
        addSlot(new Slot(inv, DistillationEncoderEntity.SOURCE, 15, 69) { @Override public boolean mayPlace(ItemStack s) { return false; } @Override public boolean mayPickup(Player p) { return false; } });
        blankSlot = slots.size();
        addSlot(new Slot(inv, DistillationEncoderEntity.BLANK, 146, 75) { @Override public boolean mayPlace(ItemStack s) { return s.is(appeng.core.definitions.AEItems.BLANK_PATTERN.asItem()); } });
        encodedSlot = slots.size();
        addSlot(new Slot(inv, DistillationEncoderEntity.ENCODED, 146, 113) { @Override public boolean mayPlace(ItemStack s) { return PatternDetailsHelper.isEncodedPattern(s); } @Override public int getMaxStackSize() { return 1; } });
        playerStart = slots.size();
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(pi, 9 + r * 9 + c, 8 + c * 18, 152 + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(pi, c, 8 + c * 18, 210));
        for (int i = 0; i < 6; i++) { aspects[i] = addDataSlot(DataSlot.standalone()); amounts[i] = addDataSlot(DataSlot.standalone()); aspects[i].set(-1); }
        addDataSlot(selected);addDataSlot(scanned);selected.set(-1);
        if (!player.level().isClientSide) { loadPattern(); scan(true); }
    }

    public ItemStack source() { return inv.getItem(DistillationEncoderEntity.SOURCE); }
    @Nullable public Aspect aspect(int i) { return EssentiaTiles.byId(aspects[i].get()); }
    public int amount(int i) { return amounts[i].get(); }
    @Nullable public Aspect selected() { return EssentiaTiles.byId(selected.get()); }
    public int selectedAmount() { for (int i = 0; i < 6; i++) if (aspects[i].get() == selected.get() && selected.get() >= 0) return amounts[i].get(); return 0; }
    public boolean scanned() { return scanned.get() != 0; }

    /** scanSourceItem: the item's aspects, largest first, when the player has scanned it. */
    void scan(boolean select) {
        lastSource = source().copy();
        for (int i = 0; i < 6; i++) { aspects[i].set(-1); amounts[i].set(0); }
        if (select) selected.set(-1);
        scanned.set(0);
        if (source().isEmpty()) return;
        var tags = RecipeAspects.get(source(), player.level());
        boolean known = TC4Addons.scanned(player.getPersistentData(), source());
        scanned.set(known ? 1 : 0);
        if (!known || tags.isEmpty()) return;
        var sorted = new ArrayList<>(tags.entrySet());sorted.sort((a, b) -> b.getValue() - a.getValue());
        for (int i = 0; i < Math.min(6, sorted.size()); i++) {
            var a = Aspect.getAspect(sorted.get(i).getKey());
            aspects[i].set(EssentiaTiles.id(a));amounts[i].set(sorted.get(i).getValue());
        }
        if (select && aspects[0].get() >= 0) select(0);
    }
    /** setSelectedAspect: only aspects the player has discovered. */
    void select(int i) {
        var a = aspect(i);
        selected.set(a != null && TC4Addons.discovered(player.getPersistentData(), a) ? aspects[i].get() : -1);
    }

    /** loadPattern: an encoded distillation pattern restores its item and aspect. */
    void loadPattern() {
        var pattern = inv.getItem(DistillationEncoderEntity.ENCODED);lastPattern = pattern.copy();
        if (pattern.isEmpty()) return;
        var details = PatternDetailsHelper.decodePattern(pattern, player.level());
        if (details == null || details.getInputs().length != 1 || details.getOutputs().size() != 1 || !(details.getOutputs().get(0).what() instanceof EssentiaKey key)) return;
        var in = details.getInputs()[0].getPossibleInputs()[0].what();
        if (!(in instanceof AEItemKey item)) return;
        inv.setItem(DistillationEncoderEntity.SOURCE, item.toStack());
        scan(false);
        for (int i = 0; i < 6; i++) if (aspect(i) == key.getAspect()) select(i);
    }

    @Override public void broadcastChanges() {
        if (!player.level().isClientSide) {
            if (!ItemStack.matches(inv.getItem(DistillationEncoderEntity.ENCODED), lastPattern)) loadPattern();
            if (!ItemStack.matches(source(), lastSource)) scan(true);
        }
        super.broadcastChanges();
    }

    @Override public void clicked(int slotId, int button, ClickType type, Player p) {
        if (slotId == sourceSlot) { if (!p.level().isClientSide) { var c = getCarried(); inv.setItem(DistillationEncoderEntity.SOURCE, c.isEmpty() ? ItemStack.EMPTY : c.copyWithCount(1)); scan(true); broadcastChanges(); } return; }
        super.clicked(slotId, button, type, p);
    }

    @Override public boolean clickMenuButton(Player p, int button) {
        if (button >= 10 && button < 16) { select(button - 10); return true; }
        if (button != BUTTON_ENCODE) return false;
        var aspect = selected();int amount = selectedAmount();
        if (aspect == null || amount <= 0 || source().isEmpty()) return false;
        var encoded = PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(source()), 1)), List.of(new GenericStack(EssentiaKey.of(aspect), amount)));
        if (!inv.getItem(DistillationEncoderEntity.ENCODED).isEmpty()) inv.setItem(DistillationEncoderEntity.ENCODED, encoded);
        else if (!inv.getItem(DistillationEncoderEntity.BLANK).isEmpty()) { inv.removeItem(DistillationEncoderEntity.BLANK, 1); inv.setItem(DistillationEncoderEntity.ENCODED, encoded); }
        else return false;
        lastPattern = encoded.copy();
        return true;
    }

    @Override public ItemStack quickMoveStack(Player p, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(p)) return ItemStack.EMPTY;
        var stack = slot.getItem();
        if (index >= playerStart) {
            if (slots.get(blankSlot).mayPlace(stack)) { if (!moveItemStackTo(stack, blankSlot, blankSlot + 1, false)) return ItemStack.EMPTY; }
            else if (slots.get(encodedSlot).mayPlace(stack) && !slots.get(encodedSlot).hasItem()) { if (!moveItemStackTo(stack, encodedSlot, encodedSlot + 1, false)) return ItemStack.EMPTY; }
            else { inv.setItem(DistillationEncoderEntity.SOURCE, stack.copyWithCount(1)); scan(true); return ItemStack.EMPTY; }
        } else if (!moveItemStackTo(stack, playerStart, slots.size(), true)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return ItemStack.EMPTY;
    }
    @Override public boolean stillValid(Player p) { return encoder != null && !encoder.isRemoved() && p.distanceToSqr(encoder.getBlockPos().getCenter()) <= 64; }
}
