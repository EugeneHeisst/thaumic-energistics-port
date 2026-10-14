package dev.eugheist.thaumicenergistics.block;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;

/**
 * TileEssentiaVibrationChamber: stores up to 64 ignis or potentia (sucking from tubes on every side but the front, suction
 * 128 falling to 32 as it fills, alternating its wanted aspect every 5 s while empty). Each essentia burns for half a coal's
 * time at 5 AE per processing tick (ignis) or coal/1.6 at 8 AE (potentia); the processing speed (20..200%) rises while the
 * network takes all the power and falls when it is full, like the AE2 vibration chamber.
 */
public class VibrationChamberEntity extends AENetworkedBlockEntity implements IGridTickable, IEssentiaTransport {
    public static final int MAX = 64;
    @Nullable public Aspect stored, processing;
    public int storedAmount, ticksRemaining, totalTicks, speed = 20;
    double powerPerProcessingTick, powerPerTick;

    public VibrationChamberEntity(BlockPos pos, BlockState state) {
        super(ThaumicEnergistics.VIBRATION_CHAMBER_ENTITY.get(), pos, state);
        getMainNode().setIdlePowerUsage(0).addService(IGridTickable.class, this);
    }
    public static boolean accepts(Aspect a) { return a == Aspect.FIRE || a == Aspect.ENERGY; }
    public int mode() { return processing == Aspect.FIRE ? 1 : processing == Aspect.ENERGY ? 2 : 0; }
    public double powerPerTick() { return powerPerTick; }
    public double maxPowerPerTick() { return powerPerProcessingTick * 2; }
    @Override public AECableType getCableConnectionType(Direction dir) { return AECableType.COVERED; }

    /** addEssentia: same aspect only, up to 64. */
    public int insert(Aspect aspect, int amount, boolean simulate) {
        if (stored != null && storedAmount > 0 && stored != aspect || !accepts(aspect)) return 0;
        int added = Math.min(amount, MAX - storedAmount);
        if (added > 0 && !simulate) { stored = aspect; storedAmount += added; markForUpdate(); saveChanges(); }
        return added;
    }

    static int coalBurn() { return 1600; }
    boolean consume() {
        if (stored == null || storedAmount <= 0) return false;
        int time;
        if (stored == Aspect.FIRE) { time = coalBurn() / 2; powerPerProcessingTick = 5; } else { time = (int) (coalBurn() / 1.6f); powerPerProcessingTick = 8; }
        totalTicks = time;processing = stored;
        if (--storedAmount <= 0) { storedAmount = 0; stored = null; }
        ticksRemaining += time;
        markForUpdate();saveChanges();
        return true;
    }

    @Override public TickingRequest getTickingRequest(IGridNode node) { return new TickingRequest(10, 40, false); }
    @Override public TickRateModulation tickingRequest(IGridNode node, int ticks) {
        var rate = TickRateModulation.IDLE;
        if (ticksRemaining > 0) rate = process(node, ticks); else { powerPerTick = 0; speed = 20; }
        if (ticksRemaining == 0 && consume()) rate = TickRateModulation.URGENT;
        if (storedAmount < MAX) pull();
        return rate;
    }
    TickRateModulation process(IGridNode node, int ticks) {
        speed = Math.max(20, Math.min(200, speed));
        double dilation = speed / 100.0, processed = ticks * dilation;
        ticksRemaining = (int) (ticksRemaining - processed);
        powerPerTick = processed * powerPerProcessingTick / ticks;
        if (ticksRemaining <= 0) { processed += ticksRemaining; ticksRemaining = 0; processing = null; markForUpdate(); saveChanges(); }
        double produced = processed * powerPerProcessingTick;
        var energy = node.getGrid().getEnergyService();
        var rate = TickRateModulation.SLOWER;
        double rejected = energy.injectPower(produced, Actionable.SIMULATE);
        if (rejected > 0) { produced = Math.max(0, produced - rejected); speed -= ticks; }
        else { rate = TickRateModulation.FASTER; speed += ticks; }
        if (produced > 0) energy.injectPower(produced, Actionable.MODULATE);
        return rate;
    }
    /** takeEssentiaFromTransportNeighbors. */
    void pull() {
        for (var side : Direction.values()) {
            if (!canInputFrom(side) || !(level.getBlockEntity(worldPosition.relative(side)) instanceof IEssentiaTransport src)) continue;
            var from = side.getOpposite();
            if (!src.canOutputTo(from) || src.getEssentiaAmount(from) <= 0) continue;
            int suck = getSuctionAmount(side);
            if (suck < src.getMinimumSuction() || suck < src.getSuctionAmount(from)) continue;
            var a = src.getEssentiaType(from);
            if (a == null || insert(a, 1, true) != 1) continue;
            insert(a, src.takeEssentia(a, 1, from), false);
        }
    }

    Direction front() { return getFront(); }
    public boolean isConnectable(Direction face) { return face != front(); }
    public boolean canInputFrom(Direction face) { return face != front(); }
    public boolean canOutputTo(Direction face) { return false; }
    public void setSuction(Aspect a, int n) {}
    public Aspect getSuctionType(Direction face) { if (stored != null && storedAmount > 0) return stored; return level != null && level.getGameTime() % 200 > 100 ? Aspect.ENERGY : Aspect.FIRE; }
    public int getSuctionAmount(Direction face) { return storedAmount >= MAX ? 0 : (int) (128f * (1f - storedAmount * .75f / MAX)); }
    public int takeEssentia(Aspect a, int n, Direction face) { return 0; }
    public int addEssentia(Aspect a, int n, Direction face) { return canInputFrom(face) ? insert(a, n, false) : 0; }
    public Aspect getEssentiaType(Direction face) { return storedAmount > 0 ? stored : null; }
    public int getEssentiaAmount(Direction face) { return storedAmount; }
    public int getMinimumSuction() { return 0; }
    public boolean renderExtendedTube() { return false; }

    @Override public void loadTag(CompoundTag d, HolderLookup.Provider r) {
        super.loadTag(d, r);
        stored = d.contains("StoredAspect") ? Aspect.getAspect(d.getString("StoredAspect")) : null;storedAmount = stored == null ? 0 : d.getInt("StoredAmount");
        ticksRemaining = d.getInt("TRemain");
        if (ticksRemaining > 0) { speed = d.getInt("ProcSpeed"); powerPerProcessingTick = d.getDouble("PwrPerTick"); processing = Aspect.getAspect(d.getString("ProcAspect")); totalTicks = d.getInt("TotalTicks"); }
    }
    @Override public void saveAdditional(CompoundTag d, HolderLookup.Provider r) {
        super.saveAdditional(d, r);
        if (stored != null && storedAmount > 0) { d.putString("StoredAspect", stored.getTag()); d.putInt("StoredAmount", storedAmount); }
        if (ticksRemaining > 0) { d.putInt("TRemain", ticksRemaining); d.putInt("ProcSpeed", speed); d.putDouble("PwrPerTick", powerPerProcessingTick); d.putInt("TotalTicks", totalTicks); if (processing != null) d.putString("ProcAspect", processing.getTag()); }
    }
    @Override protected void writeToStream(RegistryFriendlyByteBuf data) { super.writeToStream(data); data.writeUtf(processing == null ? "" : processing.getTag()); }
    @Override protected boolean readFromStream(RegistryFriendlyByteBuf data) { boolean c = super.readFromStream(data); var t = data.readUtf(); var p = t.isEmpty() ? null : Aspect.getAspect(t); c |= p != processing; processing = p; return c; }
}
