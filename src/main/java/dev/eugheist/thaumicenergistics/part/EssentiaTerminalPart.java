package dev.eugheist.thaumicenergistics.part;

import java.util.List;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.util.KeyTypeSelection;
import appeng.items.parts.PartModels;
import appeng.menu.ISubMenu;
import appeng.menu.locator.MenuHostLocator;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractTerminalPart;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.me.EssentiaKeyType;
import dev.eugheist.thaumicenergistics.menu.EssentiaTermMenu;

/**
 * PartEssentiaTerminal: the original essentia terminal GUI on AE2's display base. The part keeps its two work slots, the
 * selected aspect and the sorting/view modes. AE2's crafting screens return here.
 */
public class EssentiaTerminalPart extends AbstractTerminalPart {
    @PartModels public static final ResourceLocation MODEL_OFF = ThaumicEnergistics.id("part/essentia_terminal_off");
    @PartModels public static final ResourceLocation MODEL_ON = ThaumicEnergistics.id("part/essentia_terminal_on");
    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);
    private final KeyTypeSelection essentiaOnly = new KeyTypeSelection(() -> {}, type -> type == EssentiaKeyType.TYPE);
    final SimpleContainer work = new SimpleContainer(2) { @Override public void setChanged() { super.setChanged(); getHost().markForSave(); } };
    @Nullable Aspect selected;int sort, view;

    public EssentiaTerminalPart(IPartItem<?> partItem) { super(partItem); essentiaOnly.setEnabled(EssentiaKeyType.TYPE, true); }

    @Override public IPartModel getStaticModels() { return this.selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL); }
    @Override public KeyTypeSelection getKeyTypeSelection() { return essentiaOnly; }

    @Override public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!player.level().isClientSide) open((ServerPlayer) player);
        return true;
    }
    @Override public void returnToMainMenu(Player player, ISubMenu subMenu) { open((ServerPlayer) player); }
    void open(ServerPlayer player) {
        var host = new Host();
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new EssentiaTermMenu(id, inv, host), getPartItem().asItem().getDescription()));
    }

    final class Host implements EssentiaTermMenu.Host {
        @Override @Nullable public IGrid grid() { return isActive() ? getMainNode().getGrid() : null; }
        @Override public IActionSource source(Player player) { return IActionSource.ofPlayer(player, EssentiaTerminalPart.this); }
        @Override public boolean stillValid(Player player) {
            var be = getBlockEntity();
            return be != null && !be.isRemoved() && getHost().getPart(getSide()) == EssentiaTerminalPart.this && player.distanceToSqr(be.getBlockPos().getCenter()) <= 64;
        }
        @Override @Nullable public Aspect selected() { return selected; }
        @Override public void selected(@Nullable Aspect aspect) { selected = aspect;getHost().markForSave(); }
        @Override public int sortMode() { return sort; }
        @Override public int viewMode() { return view; }
        @Override public void modes(int s, int v) { sort = s;view = v;getHost().markForSave(); }
        @Override public Container workSlots() { return work; }
        @Override public boolean temporarySlots() { return false; }
        @Override public MenuHostLocator locator() { return MenuLocators.forPart(EssentiaTerminalPart.this); }
    }

    @Override public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        work.clearContent();
        var list = data.getList("ThEWorkSlots", 10);
        for (int i = 0; i < Math.min(2, list.size()); i++) work.setItem(i, ItemStack.parseOptional(registries, list.getCompound(i)));
        selected = data.contains("ThESelected") ? Aspect.getAspect(data.getString("ThESelected")) : null;
        sort = data.getInt("ThESort");view = data.getInt("ThEView");
    }
    @Override public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        var list = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < 2; i++) list.add(work.getItem(i).saveOptional(registries));
        data.put("ThEWorkSlots", list);
        if (selected != null) data.putString("ThESelected", selected.getTag());
        data.putInt("ThESort", sort);data.putInt("ThEView", view);
    }
    @Override public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        for (int i = 0; i < 2; i++) if (!work.getItem(i).isEmpty()) drops.add(work.getItem(i).copy());
    }
    @Override public void clearContent() { super.clearContent(); work.clearContent(); }
}
