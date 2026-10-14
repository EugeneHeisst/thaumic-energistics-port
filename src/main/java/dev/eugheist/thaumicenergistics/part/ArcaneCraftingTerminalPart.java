package dev.eugheist.thaumicenergistics.part;

import java.util.List;
import appeng.api.inventories.InternalInventory;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractTerminalPart;
import appeng.util.inv.AppEngInternalInventory;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.menu.ArcaneTermMenu;

/**
 * PartArcaneCraftingTerminal: an ME item terminal with an arcane 3x3 grid, a wand slot (no staffs), five view cells and four
 * stored armour pieces that can be swapped with the worn set for a vis discount. Inventory survives in the part like the
 * original 20-slot IInventory (grid 0-8, wand 9, armour 16-19); the result slot is computed.
 */
public class ArcaneCraftingTerminalPart extends AbstractTerminalPart implements appeng.api.networking.ticking.IGridTickable, dev.eugheist.thaumicenergistics.menu.ArcaneTermHost {
    public static final ResourceLocation INV_GRID = ThaumicEnergistics.id("arcane_grid"), INV_WAND = ThaumicEnergistics.id("arcane_wand"), INV_ARMOR = ThaumicEnergistics.id("arcane_armor");
    @PartModels public static final ResourceLocation MODEL_BASE_ACT = ThaumicEnergistics.id("part/arcane_crafting_terminal_base");
    @PartModels public static final ResourceLocation MODEL_OFF = ThaumicEnergistics.id("part/arcane_crafting_terminal_off");
    @PartModels public static final ResourceLocation MODEL_ON = ThaumicEnergistics.id("part/arcane_crafting_terminal_on");
    @PartModels public static final ResourceLocation MODEL_HAS_CHANNEL = ThaumicEnergistics.id("part/arcane_crafting_terminal_has_channel");
    static final IPartModel OFF = new PartModel(MODEL_BASE_ACT, MODEL_OFF), ON = new PartModel(MODEL_BASE_ACT, MODEL_ON), HAS_CHANNEL = new PartModel(MODEL_BASE_ACT, MODEL_HAS_CHANNEL);

    private final AppEngInternalInventory grid = new AppEngInternalInventory(this, 9);
    private final AppEngInternalInventory wand = new AppEngInternalInventory(this, 1, 1);
    private final AppEngInternalInventory armor = new AppEngInternalInventory(this, 4, 1);

    @org.jetbrains.annotations.Nullable private VisInterfacePart.VisSource visSource;

    public ArcaneCraftingTerminalPart(IPartItem<?> partItem) { super(partItem); getMainNode().setIdlePowerUsage(0.5).addService(appeng.api.networking.ticking.IGridTickable.class, this); }

    public boolean hasVisSource() { return visSource != null; }
    public void setVisSource(@org.jetbrains.annotations.Nullable VisInterfacePart.VisSource source) {
        visSource = source;saveChanges();
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    // Memory card holding a vis relay interface: links it so the wand in the terminal is recharged; a blank card unlinks.
    @Override public boolean onUseItemOn(ItemStack held, Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.Vec3 pos) {
        if (held.getItem() instanceof appeng.api.implementations.items.IMemoryCard card && !appeng.util.InteractionUtil.isInAlternateUseMode(player)) {
            var source = held.get(ThaumicEnergistics.VIS_SOURCE.get());
            boolean blank = held.get(appeng.api.ids.AEComponents.EXPORTED_SETTINGS_SOURCE) == null;
            if (source != null || blank) {
                if (!isClientSide()) {
                    setVisSource(source);
                    card.notifyUser(player, source != null ? appeng.api.implementations.items.MemoryCardMessages.SETTINGS_LOADED : appeng.api.implementations.items.MemoryCardMessages.SETTINGS_CLEARED);
                }
                return true;
            }
        }
        return super.onUseItemOn(held, player, hand, pos);
    }

    /** tickingRequest: tops every primal of the wand up to its capacity from the linked interface. */
    @Override public appeng.api.networking.ticking.TickingRequest getTickingRequest(appeng.api.networking.IGridNode node) { return new appeng.api.networking.ticking.TickingRequest(2, 20, false); }
    @Override public appeng.api.networking.ticking.TickRateModulation tickingRequest(appeng.api.networking.IGridNode node, int ticks) {
        if (visSource == null) return appeng.api.networking.ticking.TickRateModulation.IDLE;
        var stack = wand.getStackInSlot(0);
        if (!dev.eugheist.tc4modern.WandData.isWand(stack)) return appeng.api.networking.ticking.TickRateModulation.IDLE;
        var vi = VisInterfacePart.find(getLevel(), visSource, node.getGrid());
        if (vi == null) return appeng.api.networking.ticking.TickRateModulation.SLOWER;
        int r = charge(wand, vi);boolean changed = (r & 1) != 0, full = (r & 2) != 0;
        return full ? appeng.api.networking.ticking.TickRateModulation.IDLE : changed ? appeng.api.networking.ticking.TickRateModulation.FASTER : appeng.api.networking.ticking.TickRateModulation.SLOWER;
    }
    /** Tops every primal of the wand in the slot up to its capacity from the interface; bit 1 = vis taken, bit 2 = now full. */
    public static int charge(appeng.api.inventories.InternalInventory wand, VisInterfacePart vi) {
        var stack = wand.getStackInSlot(0).copy();
        if (!dev.eugheist.tc4modern.WandData.isWand(stack)) return 2;
        int capacity = dev.eugheist.tc4modern.WandData.capacity(stack);boolean changed = false, full = true;
        for (var primal : thaumcraft.api.aspects.Aspect.getPrimalAspects()) {
            int need = capacity - dev.eugheist.tc4modern.WandData.get(stack, primal);
            if (need <= 0) continue;
            int got = vi.consumeVis(primal, need);
            if (got > 0) { dev.eugheist.tc4modern.WandData.add(stack, primal, got, true);changed = true; }
            if (got < need) full = false;
        }
        if (changed) wand.setItemDirect(0, stack);
        return (changed ? 1 : 0) | (full ? 2 : 0);
    }
    @Override public void onChangeInventory(appeng.util.inv.AppEngInternalInventory inv, int slot) {
        super.onChangeInventory(inv, slot);
        if (inv == wand) getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    @Override public MenuType<?> getMenuType(Player player) { return ArcaneTermMenu.TYPE; }
    @Override public InternalInventory getSubInventory(ResourceLocation id) {
        if (id.equals(INV_GRID)) return grid;
        if (id.equals(INV_WAND)) return wand;
        if (id.equals(INV_ARMOR)) return armor;
        return super.getSubInventory(id);
    }
    public AppEngInternalInventory grid() { return grid; }
    public AppEngInternalInventory wand() { return wand; }
    public AppEngInternalInventory armor() { return armor; }

    @Override public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        if (wrenched) return;
        for (var inv : List.of(grid, wand, armor)) for (var is : inv) if (!is.isEmpty()) drops.add(is);
    }
    @Override public void clearContent() { super.clearContent(); grid.clear(); wand.clear(); armor.clear(); }
    @Override public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        grid.readFromNBT(data, "arcaneGrid", registries);wand.readFromNBT(data, "arcaneWand", registries);armor.readFromNBT(data, "arcaneArmor", registries);
        visSource = data.contains("VisInterface") ? VisInterfacePart.VisSource.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, data.get("VisInterface")).result().orElse(null) : null;
    }
    @Override public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        grid.writeToNBT(data, "arcaneGrid", registries);wand.writeToNBT(data, "arcaneWand", registries);armor.writeToNBT(data, "arcaneArmor", registries);
        if (visSource != null) VisInterfacePart.VisSource.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, visSource).result().ifPresent(t -> data.put("VisInterface", t));
    }
    @Override public IPartModel getStaticModels() { return selectModel(OFF, ON, HAS_CHANNEL); }
}
