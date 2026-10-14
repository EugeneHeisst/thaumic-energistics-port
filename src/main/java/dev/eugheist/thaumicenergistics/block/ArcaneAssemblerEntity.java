package dev.eugheist.thaumicenergistics.block;

import java.util.ArrayList;
import java.util.List;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.GridFlags;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageHelper;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.blockentity.ServerTickingBlockEntity;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.MachineSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import dev.eugheist.tc4modern.WandParts;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.craft.ArcanePattern;
import dev.eugheist.thaumicenergistics.craft.ArcanePatternDetails;
import dev.eugheist.thaumicenergistics.craft.KnowledgeCore;
import dev.eugheist.thaumicenergistics.menu.ArcaneAssemblerMenu;
import dev.eugheist.thaumicenergistics.part.VisInterfacePart;

/**
 * TileArcaneAssembler: offers every pattern of its knowledge core to the network's autocrafting. Vis comes from the vis relay
 * interface linked with a memory card (refilled every 5 ticks, at most 1500 of each primal); a craft takes 20 ticks minus 4 per
 * speed card, draws 1.5 AE/t (+0.75 per card, +15% per point of warp on the discount armour) and costs
 * ceil(discount x cost x 10) vis per primal, the discount being a thaumium sceptre's minus the stored armour's.
 */
public class ArcaneAssemblerEntity extends AENetworkedBlockEntity implements ICraftingProvider, ServerTickingBlockEntity, IUpgradeableObject, MachineBlock.Host {
    public static final Aspect[] PRIMALS = {Aspect.AIR, Aspect.WATER, Aspect.FIRE, Aspect.ORDER, Aspect.ENTROPY, Aspect.EARTH};
    public static final int MAX_STORED = 1500;
    public final SimpleContainer core = new SimpleContainer(1) {
        @Override public boolean canPlaceItem(int s, ItemStack st) { return st.is(ThaumicEnergistics.KNOWLEDGE_CORE.get()); }
        @Override public int getMaxStackSize() { return 1; }
        @Override public void setChanged() { super.setChanged(); patternsDirty = true; ArcaneAssemblerEntity.this.setChanged(); }
    };
    public final SimpleContainer armor = new SimpleContainer(4) {
        @Override public int getMaxStackSize() { return 1; }
        @Override public void setChanged() { super.setChanged(); ArcaneAssemblerEntity.this.setChanged(); }
    };
    private final IUpgradeInventory upgrades;
    private final MachineSource source = new MachineSource(this);
    public final AspectList storedVis = new AspectList();
    @Nullable private VisInterfacePart.VisSource visSource;
    @Nullable private ArcanePatternDetails current;
    private List<IPatternDetails> patterns = List.of();
    private boolean patternsDirty = true, crafting;
    private int craftTicks, visTicks;

    public ArcaneAssemblerEntity(BlockPos pos, BlockState state) {
        super(ThaumicEnergistics.ARCANE_ASSEMBLER_ENTITY.get(), pos, state);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(0).addService(ICraftingProvider.class, this);
        upgrades = UpgradeInventories.forMachine(ThaumicEnergistics.ARCANE_ASSEMBLER_ITEM.get(), 4, this::saveChanges);
    }

    @Override public IUpgradeInventory getUpgrades() { return upgrades; }
    @Override public AECableType getCableConnectionType(Direction dir) { return AECableType.SMART; }
    @Override public net.minecraft.world.Container drops() { var all = new SimpleContainer(9); all.setItem(0, core.getItem(0)); for (int i = 0; i < 4; i++) all.setItem(1 + i, armor.getItem(i)); for (int i = 0; i < 4; i++) all.setItem(5 + i, upgrades.getStackInSlot(i)); return all; }
    @Override public AbstractContainerMenu menu(int id, Inventory inv) { return new ArcaneAssemblerMenu(id, inv, this); }

    public int speedCards() { return upgrades.getInstalledUpgrades(AEItems.SPEED_CARD); }
    int ticksPerCraft() { return 20 - 4 * speedCards(); }
    public float progress() { return crafting ? Math.min(1f, craftTicks / (float) ticksPerCraft()) : 0; }
    public boolean isCrafting() { return crafting; }
    @Nullable public ItemStack target() { return current == null ? null : current.pattern.output(); }
    public boolean hasVisSource() { return visSource != null; }

    /** calculateVisDiscounts: thaumium sceptre modifier minus the armour's vis discount. */
    public float discount(Aspect primal) {
        var cap = WandParts.CAPS.get("thaumium");
        float armour = 0;
        for (int i = 0; i < 4; i++) { var s = armor.getItem(i); if (s.getItem() instanceof thaumcraft.api.IVisDiscountGear gear) armour += gear.getVisDiscount(s, null, primal) / 100f; }
        return thaumcraft.common.lib.vis.VisStorage.consumptionModifier(cap == null ? 0.9f : cap.modifier(primal), armour, 0, false, true, true);
    }
    float warpMultiplier() { int warp = 0; for (int i = 0; i < 4; i++) { var s = armor.getItem(i); if (s.getItem() instanceof thaumcraft.api.IWarpingGear g) warp += g.getWarp(s, null); } return 1f + warp * 0.15f; }
    int required(Aspect primal) { return (int) Math.ceil(discount(primal) * current.pattern.cost().getAmount(primal) * 10); }

    // ICraftingProvider
    @Override public List<IPatternDetails> getAvailablePatterns() { return patterns; }
    @Override public boolean isBusy() { return crafting; }
    @Override public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) {
        if (crafting || !(details instanceof ArcanePatternDetails arcane) || !getMainNode().isActive()) return false;
        crafting = true;craftTicks = 0;current = arcane;
        markForUpdate();saveChanges();
        return true;
    }

    void rebuildPatterns() {
        patternsDirty = false;
        var list = new ArrayList<IPatternDetails>();
        var stack = core.getItem(0);
        if (!stack.isEmpty() && level != null) for (var p : KnowledgeCore.read(stack, level.registryAccess())) list.add(new ArcanePatternDetails(ArcanePatternDetails.definitionOf(p, level.registryAccess()), p));
        patterns = List.copyOf(list);
        ICraftingProvider.requestUpdate(getMainNode());
    }

    @Nullable VisInterfacePart visInterface() {
        if (visSource == null || !(level instanceof ServerLevel server)) return null;
        var world = server.getServer().getLevel(visSource.dimension());
        if (world == null || !world.isLoaded(visSource.pos()) || !(world.getBlockEntity(visSource.pos()) instanceof IPartHost host)) return null;
        if (!(host.getPart(visSource.side()) instanceof VisInterfacePart part) || part.uid() != visSource.uid()) return null;
        var grid = getMainNode().getGrid();
        return grid != null && grid == part.getMainNode().getGrid() ? part : null;
    }
    void replenish() {
        var vi = visInterface();
        if (vi == null) return;
        boolean changed = false;
        for (var primal : PRIMALS) {
            int need = MAX_STORED - storedVis.getAmount(primal);
            if (need <= 0) continue;
            int got = vi.consumeVis(primal, need);
            if (got > 0) { storedVis.add(primal, got); changed = true; }
        }
        if (changed) { markForUpdate(); saveChanges(); }
    }

    @Override public void serverTick() {
        if (patternsDirty) rebuildPatterns();
        if (!getMainNode().isActive()) return;
        if (visSource != null && ++visTicks >= 5) { visTicks = 0; replenish(); }
        if (crafting) craftingTick();
    }
    void craftingTick() {
        if (current == null) { crafting = false; return; }
        for (var a : current.pattern.cost().getAspects()) if (storedVis.getAmount(a) < required(a)) return;
        var grid = getMainNode().getGrid();
        if (grid == null) return;
        if (craftTicks >= ticksPerCraft()) {
            var out = AEItemKey.of(current.pattern.output());long count = current.pattern.output().getCount();
            var inv = grid.getStorageService().getInventory();
            if (inv.insert(out, count, Actionable.SIMULATE, source) < count) return;
            inv.insert(out, count, Actionable.MODULATE, source);
            crafting = false;current = null;craftTicks = 0;
            markForUpdate();saveChanges();
            return;
        }
        double power = (1.5 + 1.5 * speedCards() / 2.0) * warpMultiplier();
        if (grid.getEnergyService().extractAEPower(power, Actionable.MODULATE, PowerMultiplier.CONFIG) >= power - 0.0001) {
            if (++craftTicks >= ticksPerCraft()) { for (var a : current.pattern.cost().getAspects()) storedVis.reduce(a, required(a)); }
            if (craftTicks % 5 == 0) markForUpdate();
        }
    }

    /** Dismantled assemblers keep their vis (TileArcaneAssembler.getCraftTag); a freshly crafted one arrives full. */
    @Override public void exportSettings(appeng.util.SettingsFrom mode, net.minecraft.core.component.DataComponentMap.Builder builder, @Nullable net.minecraft.world.entity.player.Player player) {
        super.exportSettings(mode, builder, player);
        if (mode == appeng.util.SettingsFrom.DISMANTLE_ITEM) { var tag = new CompoundTag(); storedVis.writeToNBT(tag, "stored_vis"); tag.putBoolean("has_vis_tag", true); builder.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag)); }
    }
    @Override public void importSettings(appeng.util.SettingsFrom mode, net.minecraft.core.component.DataComponentMap input, @Nullable net.minecraft.world.entity.player.Player player) {
        super.importSettings(mode, input, player);
        if (mode != appeng.util.SettingsFrom.DISMANTLE_ITEM) return;
        var data = input.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        storedVis.aspects.clear();
        if (data != null && data.copyTag().getBoolean("has_vis_tag")) storedVis.readFromNBT(data.copyTag(), "stored_vis");
        else for (var p : PRIMALS) storedVis.add(p, MAX_STORED);
        saveChanges();
    }
    @Override public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        if (!core.getItem(0).isEmpty()) drops.add(core.getItem(0));
        for (int i = 0; i < 4; i++) if (!armor.getItem(i).isEmpty()) drops.add(armor.getItem(i));
        for (var u : upgrades) if (!u.isEmpty()) drops.add(u);
    }
    public void setVisSource(@Nullable VisInterfacePart.VisSource source) { visSource = source; saveChanges(); }

    @Override public void loadTag(CompoundTag data, HolderLookup.Provider r) {
        super.loadTag(data, r);
        core.setItem(0, data.contains("kcore") ? ItemStack.parseOptional(r, data.getCompound("kcore")) : ItemStack.EMPTY);
        var arm = data.getList("armor", 10);for (int i = 0; i < 4; i++) armor.setItem(i, i < arm.size() ? ItemStack.parseOptional(r, arm.getCompound(i)) : ItemStack.EMPTY);
        upgrades.readFromNBT(data, "upgrades", r);
        storedVis.aspects.clear();if (data.contains("stored_vis")) storedVis.readFromNBT(data, "stored_vis");
        visSource = data.contains("vis_interface") ? VisInterfacePart.VisSource.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, data.get("vis_interface")).result().orElse(null) : null;
        crafting = data.getBoolean("isCrafting");craftTicks = 0;
        current = null;
        if (crafting && data.contains("pattern") && level != null) { var p = ArcanePattern.load(data.getCompound("pattern"), r); if (p != null) current = new ArcanePatternDetails(ArcanePatternDetails.definitionOf(p, r), p); }
        if (current == null) crafting = false;
        patternsDirty = true;
    }
    @Override public void saveAdditional(CompoundTag data, HolderLookup.Provider r) {
        super.saveAdditional(data, r);
        if (!core.getItem(0).isEmpty()) data.put("kcore", core.getItem(0).save(r));
        var arm = new net.minecraft.nbt.ListTag();for (int i = 0; i < 4; i++) arm.add(armor.getItem(i).isEmpty() ? new CompoundTag() : armor.getItem(i).save(r));data.put("armor", arm);
        upgrades.writeToNBT(data, "upgrades", r);
        if (storedVis.size() > 0) storedVis.writeToNBT(data, "stored_vis");
        if (visSource != null) VisInterfacePart.VisSource.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, visSource).result().ifPresent(t -> data.put("vis_interface", t));
        data.putBoolean("isCrafting", crafting);
        if (current != null) data.put("pattern", current.pattern.save(r));
    }
    @Override protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(crafting);data.writeVarInt(craftTicks);
        for (var p : PRIMALS) data.writeVarInt(storedVis.getAmount(p));
    }
    @Override protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        crafting = data.readBoolean();craftTicks = data.readVarInt();
        storedVis.aspects.clear();for (var p : PRIMALS) { int v = data.readVarInt(); if (v > 0) storedVis.add(p, v); }
        return changed;
    }
}
