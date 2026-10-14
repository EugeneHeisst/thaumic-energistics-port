package dev.eugheist.thaumicenergistics.part;

import java.lang.ref.WeakReference;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.util.AECableType;
import appeng.api.ids.AEComponents;
import appeng.items.parts.PartModels;
import appeng.parts.AEBasePart;
import appeng.parts.PartModel;
import appeng.util.SettingsFrom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.visnet.VisNearbyRules;
import thaumcraft.api.visnet.VisParentRules;
import dev.eugheist.tc4modern.VisNetwork;
import dev.eugheist.tc4modern.VisRelayEntity;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;

/**
 * PartVisInterface: draws vis from the vis relay mounted on it (relay facing away from the part) for the network, 4 AE per
 * request. Shift-clicking it with a memory card stores it as a digi-vis source; using that card on another interface of the
 * same network turns that one into a provider whose relay position becomes a vis source fed through the network from the
 * stored interface. A blank memory card ends provider mode. The runes glow in the colour of the aspect being drained.
 */
public class VisInterfacePart extends AEBasePart implements IGridTickable {
    @PartModels public static final ResourceLocation MODEL_BASE = ThaumicEnergistics.id("part/vis_interface_base");
    @PartModels public static final ResourceLocation MODEL_OFF = ThaumicEnergistics.id("part/vis_interface_off");
    @PartModels public static final ResourceLocation MODEL_HAS_CHANNEL = ThaumicEnergistics.id("part/vis_interface_has_channel");
    static final IPartModel OFF = new PartModel(MODEL_BASE, MODEL_OFF), HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_HAS_CHANNEL);
    public static final ResourceLocation RUNES = ThaumicEnergistics.id("textures/part/vis_interface_runes.png");

    private long uid = System.currentTimeMillis() ^ System.identityHashCode(this);
    private int drainColor;
    private long lastColorUpdate;
    private boolean provider;
    @Nullable private VisSource source;
    @Nullable private Proxy proxy;

    public VisInterfacePart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setFlags(appeng.api.networking.GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(0).addService(IGridTickable.class, this);
    }

    public long uid() { return uid; }
    public boolean isProvider() { return provider; }
    public int drainColor() { return drainColor; }

    @Nullable public VisRelayEntity relay() {
        var level = getLevel();
        if (level == null) return null;
        var pos = getBlockEntity().getBlockPos().relative(getSide());
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof VisRelayEntity r && r.relay.orientation == getSide().get3DDataValue() ? r : null;
    }

    /** DigiVisSourceData.tryGetSource: the stored interface if it still exists and is on the given grid. */
    @Nullable public static VisInterfacePart find(@Nullable net.minecraft.world.level.Level level, VisSource source, @Nullable appeng.api.networking.IGrid grid) {
        if (grid == null || !(level instanceof net.minecraft.server.level.ServerLevel server)) return null;
        var world = server.getServer().getLevel(source.dimension());
        if (world == null || !world.isLoaded(source.pos()) || !(world.getBlockEntity(source.pos()) instanceof appeng.api.parts.IPartHost host)) return null;
        if (!(host.getPart(source.side()) instanceof VisInterfacePart part) || part.uid != source.uid()) return null;
        return grid == part.getMainNode().getGrid() ? part : null;
    }

    /** IDigiVisSource.consumeVis. */
    public int consumeVis(Aspect aspect, int amount) {
        if (!isActive()) return 0;
        int received = 0;
        if (provider) { var s = sourceInterface(); if (s != null) received = s.consumeVis(aspect, amount); }
        else {
            var relay = relay();var grid = getMainNode().getGrid();
            if (relay == null || grid == null) return 0;
            var energy = grid.getEnergyService();
            if (energy.extractAEPower(4, Actionable.SIMULATE, PowerMultiplier.CONFIG) < 4) return 0;
            received = relay.consumeVis(aspect, amount);
            if (received > 0) energy.extractAEPower(4, Actionable.MODULATE, PowerMultiplier.CONFIG);
        }
        if (received > 0) setDrainColor(aspect.getColor());
        return received;
    }

    void setDrainColor(int color) {
        long now = System.currentTimeMillis();
        if (color != 0) {
            if (color == drainColor) { lastColorUpdate = now; return; }
            if (now - lastColorUpdate <= 250) return;
            lastColorUpdate = now;
        }
        drainColor = color;
        getHost().markForUpdate();
    }

    /** isP2PSourceValid: same grid, another interface, not itself a provider. */
    @Nullable VisInterfacePart sourceInterface() {
        if (source == null || !(getLevel() instanceof ServerLevel level)) return null;
        var world = level.getServer().getLevel(source.dimension());
        if (world == null || !world.isLoaded(source.pos()) || !(world.getBlockEntity(source.pos()) instanceof IPartHost host)) return null;
        if (!(host.getPart(source.side()) instanceof VisInterfacePart other) || other == this || other.uid != source.uid() || other.provider) return null;
        var mine = getMainNode().getGrid();
        return mine != null && mine == other.getMainNode().getGrid() ? other : null;
    }

    void setProvider(boolean on) {
        if (!on) { source = null; dropProxy(); }
        provider = on;
        getHost().markForSave();
    }
    void dropProxy() { if (proxy != null && getLevel() instanceof ServerLevel level) VisNetwork.unregister(level, proxy); proxy = null; }

    @Override public TickingRequest getTickingRequest(IGridNode node) { return new TickingRequest(30, 30, false); }
    @Override public TickRateModulation tickingRequest(IGridNode node, int ticks) {
        if (drainColor != 0 && System.currentTimeMillis() - lastColorUpdate > 500) setDrainColor(0);
        if (provider && getLevel() instanceof ServerLevel level) {
            boolean valid = sourceInterface() != null, hasRelay = relay() != null;
            if ((!valid || !hasRelay) && proxy != null) dropProxy();
            else if (valid && hasRelay && proxy == null) { proxy = new Proxy(); VisNetwork.register(level, proxy); }
            else if (proxy != null) proxy.refreshAttunement(level);
        }
        return TickRateModulation.SAME;
    }
    @Override public void removeFromWorld() { dropProxy(); super.removeFromWorld(); }

    /** VisProviderProxy: a vis source at the relay's position with range 1, attuned like that relay. */
    final class Proxy implements VisParentRules.Node {
        private boolean busy;
        private int attunement = -1;
        BlockPos pos() { return getBlockEntity().getBlockPos().relative(getSide()); }
        public int x() { return pos().getX(); }
        public int y() { return pos().getY(); }
        public int z() { return pos().getZ(); }
        public int range() { return 1; }
        public int attunement() { return attunement; }
        public boolean valid() { return !getBlockEntity().isRemoved() && provider && relay() != null; }
        public boolean loaded() { return getLevel() != null && getLevel().isLoaded(pos()); }
        public Iterable<? extends VisNearbyRules.Node> children() { return getLevel() instanceof ServerLevel level ? VisNetwork.children(level, this) : java.util.List.of(); }
        public int consumeVis(Aspect aspect, int amount) {
            if (busy || !valid()) return 0;
            busy = true;
            try { return VisInterfacePart.this.consumeVis(aspect, amount); } finally { busy = false; }
        }
        void refreshAttunement(ServerLevel level) {
            var relay = relay();int color = relay == null ? -1 : relay.attunement();
            if (color != attunement) { attunement = color; VisNetwork.unregister(level, this); VisNetwork.register(level, this); }
        }
    }

    // Memory card: shift = save this interface as the source, use = link (provider mode), blank card = clear.
    @Override public boolean onUseItemOn(ItemStack held, Player player, InteractionHand hand, Vec3 pos) {
        if (held.getItem() instanceof IMemoryCard card && !appeng.util.InteractionUtil.isInAlternateUseMode(player) && held.get(AEComponents.EXPORTED_SETTINGS_SOURCE) == null) {
            if (!isClientSide() && provider) { setProvider(false); card.notifyUser(player, MemoryCardMessages.SETTINGS_CLEARED); }
            return true;
        }
        return super.onUseItemOn(held, player, hand, pos);
    }
    @Override public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder) {
        super.exportSettings(mode, builder);
        if (mode == SettingsFrom.MEMORY_CARD) {
            builder.set(ThaumicEnergistics.VIS_SOURCE.get(), new VisSource(getLevel().dimension(), getBlockEntity().getBlockPos(), getSide(), uid));
            setProvider(false);
        }
    }
    @Override public void importSettings(SettingsFrom mode, DataComponentMap input, @Nullable Player player) {
        super.importSettings(mode, input, player);
        var data = input.get(ThaumicEnergistics.VIS_SOURCE.get());
        if (mode != SettingsFrom.MEMORY_CARD || data == null) return;
        source = data;
        if (sourceInterface() == null) { source = null; if (player != null) player.displayClientMessage(net.minecraft.network.chat.Component.translatable("chat.ae2.InvalidMachine"), true); return; }
        setProvider(true);
        source = data;
    }

    @Override public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        if (data.contains("uid")) uid = data.getLong("uid");
        provider = data.getBoolean("isProvider");
        source = provider && data.contains("linkedSource") ? VisSource.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, data.get("linkedSource")).result().orElse(null) : null;
    }
    @Override public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        data.putLong("uid", uid);
        if (provider) { data.putBoolean("isProvider", true); if (source != null) VisSource.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, source).result().ifPresent(t -> data.put("linkedSource", t)); }
    }
    @Override public void writeToStream(RegistryFriendlyByteBuf data) { super.writeToStream(data); data.writeInt(drainColor); }
    @Override public boolean readFromStream(RegistryFriendlyByteBuf data) { boolean r = super.readFromStream(data); int old = drainColor; drainColor = data.readInt(); return r || old != drainColor; }

    @Override public float getCableConnectionLength(AECableType cable) { return 2; }
    @Override public void getBoxes(IPartCollisionHelper helper) { helper.addBox(6, 6, 15, 10, 10, 16); helper.addBox(4, 4, 14, 12, 12, 15); helper.addBox(5, 5, 13, 11, 11, 14); }
    @Override public IPartModel getStaticModels() { return isActive() ? HAS_CHANNEL : OFF; }
    @Override public boolean requireDynamicRender() { return true; }
    @Override @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    public void renderDynamic(float partialTicks, com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay) {
        dev.eugheist.thaumicenergistics.client.VisInterfaceRenderer.render(this, pose, buffers);
    }

    /** DigiVisSourceData. */
    public record VisSource(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos pos, Direction side, long uid) {
        public static final com.mojang.serialization.Codec<VisSource> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
            net.minecraft.resources.ResourceKey.codec(net.minecraft.core.registries.Registries.DIMENSION).fieldOf("dimension").forGetter(VisSource::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(VisSource::pos), Direction.CODEC.fieldOf("side").forGetter(VisSource::side),
            com.mojang.serialization.Codec.LONG.fieldOf("uid").forGetter(VisSource::uid)).apply(i, VisSource::new));
        public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, VisSource> STREAM = net.minecraft.network.codec.ByteBufCodecs.fromCodec(CODEC);
    }
}
