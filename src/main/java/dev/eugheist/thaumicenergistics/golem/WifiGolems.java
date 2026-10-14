package dev.eugheist.thaumicenergistics.golem;

import java.util.function.BiConsumer;
import appeng.api.config.Actionable;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import appeng.items.parts.FacadeItem;
import dev.eugheist.tc4modern.AlembicEntity;
import dev.eugheist.tc4modern.StrawGolem;
import dev.eugheist.tc4modern.WardedJarEntity;
import dev.eugheist.tc4modern.api.TC4Addons;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.item.GolemBackpackItem;
import dev.eugheist.thaumicenergistics.me.EssentiaKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;

/**
 * WirelessGolemHandler + AIAENetworkGolem/AIGolemWifi*: a linked backpack on a gather, fill, liquid or essentia golem
 * moves items, fluids and essentia straight to/from the ME network while an access point of that network is in range.
 * State lives in the golem's synced addon tag under "thaumicenergistics:wifi".
 */
public final class WifiGolems implements TC4Addons.GolemHook {
    public static final WifiGolems INSTANCE = new WifiGolems();
    public static final String TAG = "thaumicenergistics:wifi";
    /** GolemCoreType Fill(0), Gather(2), Liquid(5), Essentia(6). */
    static boolean supported(int core) { return core == 0 || core == 2 || core == 5 || core == 6; }
    static final int[] FLUID_RATES = {100, 250, 500}, ITEM_RATES = {8, 24, 32}, ESS_RATES = {4, 12, 16};

    /** BackpackSkins in original order; the default is thaumium. */
    public enum Skin {
        THAUMIUM("Thaum", "tc4modern:thaumium_block"), STONE("Stone", "minecraft:stone"), STRAW("Straw", "minecraft:hay_block"),
        GREATWOOD("Wood", "tc4modern:greatwood_log"), FLESH("Flesh", "tc4modern:flesh_block"), CLAY("Clay", "minecraft:bricks"),
        IRON("Iron", "minecraft:iron_block"), TALLOW("Tallow", "tc4modern:tallow_block"), GOLD("Gold", "minecraft:gold_block"), DIAMOND("Diamond", "minecraft:diamond_block");
        public final ResourceLocation texture;final String block;
        Skin(String tex, String block) { texture = ThaumicEnergistics.id("textures/models/golem_backpack/" + tex.toLowerCase() + ".png"); this.block = block; }
        public static Skin byId(int id) { return id >= 0 && id < values().length ? values()[id] : THAUMIUM; }
        @Nullable static Skin of(ItemStack facade) {
            if (!(facade.getItem() instanceof FacadeItem item)) return null;
            var id = BuiltInRegistries.ITEM.getKey(item.getTextureItem(facade).getItem()).toString();
            for (var s : values()) if (s.block.equals(id)) return s;
            return null;
        }
    }

    @Nullable public static CompoundTag data(StrawGolem g) { var t = g.addonData(); return t.contains(TAG) ? t.getCompound(TAG) : null; }
    static void data(StrawGolem g, @Nullable CompoundTag wifi) { var t = g.addonData(); if (wifi == null) t.remove(TAG); else t.put(TAG, wifi); g.addonData(t); }
    @Nullable static GlobalPos link(CompoundTag wifi) {
        if (!wifi.contains("pos")) return null;
        var dim = ResourceLocation.tryParse(wifi.getString("dim"));
        return dim == null ? null : GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(wifi.getLong("pos")));
    }
    static void sound(StrawGolem g, String id) { g.level().playSound(null, g, SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("tc4modern", id)), SoundSource.NEUTRAL, .5f, 1); }

    @Override public void goals(StrawGolem golem, BiConsumer<Integer, Goal> add) { add.accept(1, new NetworkGoal(golem)); }

    @Override public InteractionResult interact(StrawGolem g, Player p, InteractionHand hand) {
        var held = p.getItemInHand(hand);
        var wifi = data(g);
        if (held.getItem() instanceof GolemBackpackItem) {
            // canHandleInteration_Backpack: only one backpack, only the four network-aware cores.
            if (wifi != null || !supported(g.core())) return InteractionResult.PASS;
            if (g.level().isClientSide) return InteractionResult.SUCCESS;
            var link = GolemBackpackItem.link(held);
            if (link == null) { p.displayClientMessage(appeng.core.localization.GuiText.Unlinked.text(), true); return InteractionResult.CONSUME; }
            var tag = new CompoundTag();tag.putString("dim", link.dimension().location().toString());tag.putLong("pos", link.pos().asLong());
            data(g, tag);sound(g, "upgrade");
            held.shrink(1);
            return InteractionResult.CONSUME;
        }
        if (held.getItem() instanceof FacadeItem && wifi != null) {
            var skin = Skin.of(held);
            if (skin == null) return InteractionResult.PASS;
            if (g.level().isClientSide) return InteractionResult.SUCCESS;
            if (wifi.contains("facade") && !p.getAbilities().instabuild) g.spawnAtLocation(ItemStack.parseOptional(g.registryAccess(), wifi.getCompound("facade")), .5f);
            wifi.put("facade", held.copyWithCount(1).save(g.registryAccess()));wifi.putByte("skin", (byte)skin.ordinal());
            if (!p.getAbilities().instabuild) held.shrink(1);
            data(g, wifi);sound(g, "cameraticks");
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /** bellLeftClicked: picking up or dismantling the golem drops the linked backpack and its facade. */
    @Override public void removed(StrawGolem g, Player player, boolean dismantled) {
        var wifi = data(g);
        if (wifi == null) return;
        var backpack = new ItemStack(ThaumicEnergistics.GOLEM_WIFI_BACKPACK.get());
        var link = link(wifi);
        if (link != null) GolemBackpackItem.LINKABLE_HANDLER.link(backpack, link);
        g.spawnAtLocation(backpack, .5f);
        if (wifi.contains("facade")) g.spawnAtLocation(ItemStack.parseOptional(g.registryAccess(), wifi.getCompound("facade")), .5f);
        data(g, null);
    }

    /** WirelessAELink: the linked access point's grid, if one of its access points reaches the golem. */
    record Connection(IGrid grid, IActionSource source) {}
    @Nullable static Connection connect(StrawGolem g, CompoundTag wifi) {
        var link = link(wifi);
        if (link == null || !(g.level() instanceof ServerLevel level)) return null;
        var linked = level.getServer().getLevel(link.dimension());
        if (linked == null || !linked.isLoaded(link.pos()) || !(linked.getBlockEntity(link.pos()) instanceof IWirelessAccessPoint linkedWap)) return null;
        var grid = linkedWap.getGrid();
        if (grid == null) return null;
        for (var wap : grid.getMachines(WirelessAccessPointBlockEntity.class)) {
            var at = wap.getLocation();
            if (at.getLevel() != g.level() || !wap.isActive()) continue;
            double range = wap.getRange(), dx = at.getPos().getX() - g.getX(), dy = at.getPos().getY() - g.getY(), dz = at.getPos().getZ() - g.getZ();
            if (dx * dx + dy * dy + dz * dz < range * range) return new Connection(grid, IActionSource.ofMachine(wap::getActionableNode));
        }
        return null;
    }

    /** AIAENetworkGolem: checks the network every 20 ticks and does one transfer at the rate of its order upgrades (x2 advanced). */
    public static final class NetworkGoal extends Goal {
        final StrawGolem g;int timer;Connection net;
        Aspect wanted;BlockPos jar;
        public NetworkGoal(StrawGolem g) { this.g = g; }
        int rate(int[] rates) { return rates[Math.min(2, g.upgrades(4))] * (g.advanced() ? 2 : 1); }
        @Override public boolean canUse() {
            if (g.level().isClientSide || timer-- > 0) return false;
            timer = 20;
            var wifi = data(g);
            if (wifi == null) return false;
            net = supported(g.core()) ? connect(g, wifi) : null;
            if (wifi.getBoolean("range") != (net != null)) { wifi.putBoolean("range", net != null);data(g, wifi); }
            return net != null && g.working() && needed();
        }
        @Override public boolean canContinueToUse() { return false; }
        BlockPos homeContainer() { return g.home.relative(g.homeFace.getOpposite()); }
        boolean needed() {
            return switch (g.core()) {
                case 2 -> !g.carried().isEmpty();
                case 0 -> g.carried().isEmpty();
                case 5 -> missingFluid() != null;
                case 6 -> essentiaNeeded();
                default -> false;
            };
        }
        @Override public void start() {
            var storage = net.grid().getStorageService().getInventory();var energy = net.grid().getEnergyService();
            switch (g.core()) {
                case 2 -> { // AIGolemWifiGather.depositStack
                    var held = g.carried();var key = AEItemKey.of(held);
                    long inserted = StorageHelper.poweredInsert(energy, storage, key, Math.min(held.getCount(), rate(ITEM_RATES)), net.source());
                    if (inserted > 0) { g.carried(held.copyWithCount(held.getCount() - (int)inserted));if (g.carried().isEmpty()) g.act(); }
                }
                case 0 -> { // AIGolemWifiFill: the first filter item the home container is short of.
                    var home = g.level().getCapability(Capabilities.ItemHandler.BLOCK, homeContainer(), g.homeFace);
                    for (int i = 0; i < g.filterCount(); i++) {
                        var filter = g.filters.getItem(i);
                        if (filter.isEmpty()) continue;
                        int need = g.fillAny ? g.carryLimit() : filter.getCount();
                        if (!g.fillAny && home != null) for (int s = 0; s < home.getSlots(); s++) if (g.matches(filter, home.getStackInSlot(s))) need -= home.getStackInSlot(s).getCount();
                        need = Math.min(Math.min(need, g.carryLimit()), rate(ITEM_RATES));
                        if (need <= 0) continue;
                        var key = AEItemKey.of(filter);
                        long got = StorageHelper.poweredExtraction(energy, storage, key, need, net.source());
                        if (got > 0) { g.carried(key.toStack((int)got));g.act();return; }
                    }
                }
                case 5 -> { // AIGolemWifiLiquid: top the carried fluid up to the golem's limit.
                    var fluid = missingFluid();
                    if (fluid == null) return;
                    var carried = g.fluidCarried();
                    int room = Math.min(g.fluidCarryLimit() - carried.getAmount(), rate(FLUID_RATES));
                    if (room <= 0) return;
                    long got = StorageHelper.poweredExtraction(energy, storage, AEFluidKey.of(fluid), room, net.source());
                    if (got > 0) g.fluidCarried(fluid.copyWithAmount(carried.getAmount() + (int)got));
                }
                case 6 -> {
                    if (wanted != null) { // fill mode: take what the target jar is missing
                        if (!(g.level().getBlockEntity(jar) instanceof WardedJarEntity j)) return;
                        boolean isVoid = j.state instanceof thaumcraft.common.tiles.essentia.VoidJarState;
                        int amount = isVoid ? j.state.maxAmount : j.state.maxAmount - j.state.amount;
                        long got = StorageHelper.poweredExtraction(energy, storage, EssentiaKey.of(wanted), Math.min(amount, rate(ESS_RATES)), net.source());
                        if (got > 0) g.essentia(wanted, (int)got);
                    } else if (g.essentia() != null && g.essentiaAmount() > 0) {
                        long put = StorageHelper.poweredInsert(energy, storage, EssentiaKey.of(g.essentia()), Math.min(g.essentiaAmount(), rate(ESS_RATES)), net.source());
                        if (put > 0) g.essentia(g.essentia(), g.essentiaAmount() - (int)put);
                    }
                }
                default -> {}
            }
        }
        /** GolemHelper.getMissingLiquids with a single result: the one filter fluid the home tank still takes. */
        @Nullable FluidStack missingFluid() {
            FluidStack found = null;
            for (int i = 0; i < g.filterCount(); i++) {
                var f = FluidUtil.getFluidContained(g.filters.getItem(i));
                if (f.isEmpty()) continue;
                if (found != null && !FluidStack.isSameFluidSameComponents(found, f.get())) return null;
                found = f.get().copyWithAmount(1000);
            }
            if (found == null) return null;
            var carried = g.fluidCarried();
            if (!carried.isEmpty() && !FluidStack.isSameFluidSameComponents(carried, found)) return null;
            var tank = g.level().getCapability(Capabilities.FluidHandler.BLOCK, homeContainer(), g.homeFace);
            return tank != null && tank.fill(found, IFluidHandler.FluidAction.SIMULATE) > 0 ? found : null;
        }
        /** AIGolemWifiEssentia.needsNetworkNow: fill marked jars when home is no essentia source, else unload into the network once idle. */
        boolean essentiaNeeded() {
            wanted = null;jar = null;
            var c = homeContainer();
            boolean fill = !(g.level().getBlockEntity(c) instanceof IEssentiaTransport);
            for (int i = 1; i < 6 && fill; i++) fill = !(g.level().getBlockEntity(c.above(i)) instanceof AlembicEntity);
            if (!fill) return g.getNavigation().isDone() && g.essentiaAmount() > 0;
            if (g.essentiaAmount() > 0) return false;
            findJar();
            return wanted != null;
        }
        /** findClosestValidJar: marked jars and every jar touching them, within range of home, labelled or holding essentia, with room. */
        void findJar() {
            var seen = new java.util.HashSet<BlockPos>();var pending = new java.util.ArrayDeque<BlockPos>();
            for (var value : g.markers) { var p = BlockPos.of(((CompoundTag)value).getLong("pos")); if (g.level().getBlockEntity(p) instanceof WardedJarEntity && seen.add(p)) pending.add(p); }
            double range = g.range() * (double)g.range(), best = Double.MAX_VALUE;boolean bestVoid = false;
            var storage = net.grid().getStorageService().getInventory();
            while (!pending.isEmpty() && seen.size() < 200) {
                var p = pending.removeFirst();
                if (!(g.level().getBlockEntity(p) instanceof WardedJarEntity j)) continue;
                for (var d : Direction.values()) { var n = p.relative(d); if (g.level().getBlockEntity(n) instanceof WardedJarEntity && seen.add(n)) pending.add(n); }
                double x = p.getX() + .5 - g.home.getX(), y = p.getY() + .5 - g.home.getY(), z = p.getZ() + .5 - g.home.getZ(), dist = x * x + y * y + z * z;
                if (dist > range) continue;
                boolean isVoid = j.state instanceof thaumcraft.common.tiles.essentia.VoidJarState;
                if ((!bestVoid || isVoid) && dist > best) continue;
                var aspect = j.state.aspectFilter != null ? j.state.aspectFilter : j.state.aspect;
                if (aspect == null || !isVoid && j.state.amount >= j.state.maxAmount) continue;
                if (storage.extract(EssentiaKey.of(aspect), 1, Actionable.SIMULATE, net.source()) <= 0) continue;
                wanted = aspect;jar = p;best = dist;bestVoid = isVoid;
            }
        }
    }
}
