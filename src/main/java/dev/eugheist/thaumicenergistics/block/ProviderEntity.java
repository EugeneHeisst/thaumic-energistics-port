package dev.eugheist.thaumicenergistics.block;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.storage.StorageHelper;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.blockentity.ServerTickingBlockEntity;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.me.helpers.MachineSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import dev.eugheist.thaumicenergistics.me.EssentiaKey;

/**
 * TileProviderBase: an ME device needing a channel that takes the colour of the cable it is attached to (unless recoloured)
 * and lights its overlay in that colour while active. Essentia comes from and goes to the network's storage.
 */
public abstract class ProviderEntity extends AENetworkedBlockEntity implements ServerTickingBlockEntity {
    protected final MachineSource source = new MachineSource(this);
    private boolean active;
    private AEColor color = AEColor.TRANSPARENT;
    private int checks;

    protected ProviderEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, double idle) {
        super(type, pos, state);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(idle);
    }

    public boolean isActive() { return isClientSide() ? active : getMainNode().isActive(); }
    public AEColor getColor() { return color; }
    @Override public AECableType getCableConnectionType(Direction dir) { return AECableType.SMART; }

    /** checkGridConnectionColor: adopt the colour of a neighbouring cable. */
    void checkColor() {
        AEColor found = AEColor.TRANSPARENT;
        for (var side : Direction.values())
            if (level.getBlockEntity(worldPosition.relative(side)) instanceof CableBusBlockEntity cable && cable.getPart(null) != null) { found = cable.getColor(); break; }
        if (found != color) { color = found; getMainNode().setGridColor(found); markForUpdate(); saveChanges(); }
    }

    @Override public void serverTick() {
        if (++checks % 20 == 0) {
            checkColor();
            boolean now = getMainNode().isActive();
            if (now != active) { active = now; markForUpdate(); }
        }
    }

    /** extractEssentiaFromNetwork. */
    protected int extract(Aspect aspect, int amount, boolean mustMatch) {
        var grid = getMainNode().getGrid();
        if (grid == null || !getMainNode().isActive()) return 0;
        var inv = grid.getStorageService().getInventory();var energy = grid.getEnergyService();var key = EssentiaKey.of(aspect);
        long can = StorageHelper.poweredExtraction(energy, inv, key, amount, source, Actionable.SIMULATE);
        if (can == 0 || mustMatch && can != amount) return 0;
        return (int) StorageHelper.poweredExtraction(energy, inv, key, can, source, Actionable.MODULATE);
    }
    protected long amountInNetwork(Aspect aspect) {
        var grid = getMainNode().getGrid();
        return grid == null || !getMainNode().isActive() ? 0 : grid.getStorageService().getCachedInventory().get(EssentiaKey.of(aspect));
    }
    protected int insert(Aspect aspect, int amount, boolean simulate) {
        var grid = getMainNode().getGrid();
        if (grid == null || !getMainNode().isActive()) return 0;
        return (int) StorageHelper.poweredInsert(grid.getEnergyService(), grid.getStorageService().getInventory(), EssentiaKey.of(aspect), amount, source, simulate ? Actionable.SIMULATE : Actionable.MODULATE);
    }

    @Override protected void writeToStream(RegistryFriendlyByteBuf data) { super.writeToStream(data); data.writeByte(color.ordinal()); data.writeBoolean(getMainNode().isActive()); }
    @Override protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        var c = AEColor.values()[data.readByte()];boolean a = data.readBoolean();
        changed |= c != color || a != active; color = c; active = a;
        if (changed && level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
        return changed;
    }
    @Override public void loadTag(CompoundTag data, HolderLookup.Provider registries) { super.loadTag(data, registries); color = AEColor.values()[Math.max(0, Math.min(AEColor.values().length - 1, data.getInt("TEColor")))]; }
    @Override public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) { super.saveAdditional(data, registries); data.putInt("TEColor", color.ordinal()); }
}
