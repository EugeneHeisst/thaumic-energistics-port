package dev.eugheist.thaumicenergistics.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;

/**
 * TileEssentiaProvider: an essentia transport on every side with suction 8. A neighbour asking for an aspect sees the network's
 * amount of it and draws from the network; every 15 ticks (5 after a successful input) it also pulls one essentia from each
 * neighbouring transport that offers it into the network. 3 AE/t.
 */
public class EssentiaProviderEntity extends ProviderEntity implements IEssentiaTransport {
    private int tickCount, tickRate = 15;
    public EssentiaProviderEntity(BlockPos pos, BlockState state) { super(ThaumicEnergistics.ESSENTIA_PROVIDER_ENTITY.get(), pos, state, 3); }

    Aspect wanted(Direction side) {
        return level.getBlockEntity(worldPosition.relative(side)) instanceof IEssentiaTransport neighbour ? neighbour.getSuctionType(side.getOpposite()) : null;
    }

    @Override public void serverTick() {
        super.serverTick();
        if (++tickCount < tickRate) return;
        tickCount = 0;tickRate = 15;
        // EssentiaTransportHelper.takeEssentiaFromTransportNeighbors
        for (var side : Direction.values()) {
            if (!(level.getBlockEntity(worldPosition.relative(side)) instanceof IEssentiaTransport src)) continue;
            var from = side.getOpposite();
            if (!src.canOutputTo(from) || src.getEssentiaAmount(from) <= 0) continue;
            int suck = getSuctionAmount(side);
            if (suck < src.getMinimumSuction() || suck < src.getSuctionAmount(from)) continue;
            var aspect = src.getEssentiaType(from);
            if (aspect == null || insert(aspect, 1, true) != 1) continue;
            int taken = src.takeEssentia(aspect, 1, from);
            if (taken > 0) addEssentia(aspect, taken, side);
        }
    }

    public boolean isConnectable(Direction face) { return true; }
    public boolean canInputFrom(Direction face) { return true; }
    public boolean canOutputTo(Direction face) { return true; }
    public void setSuction(Aspect aspect, int amount) {}
    public Aspect getSuctionType(Direction face) { return null; }
    public int getSuctionAmount(Direction face) { return 8; }
    public int getMinimumSuction() { return 1; }
    public boolean renderExtendedTube() { return false; }
    public Aspect getEssentiaType(Direction face) { var a = wanted(face); return a != null && amountInNetwork(a) > 0 ? a : null; }
    public int getEssentiaAmount(Direction face) { var a = wanted(face); return a == null ? 0 : (int) Math.min(amountInNetwork(a), Integer.MAX_VALUE); }
    public int takeEssentia(Aspect aspect, int amount, Direction face) { return extract(aspect, amount, false); }
    public int addEssentia(Aspect aspect, int amount, Direction face) { int accepted = insert(aspect, amount, false); if (accepted > 0) tickRate = 5; return accepted; }
}
