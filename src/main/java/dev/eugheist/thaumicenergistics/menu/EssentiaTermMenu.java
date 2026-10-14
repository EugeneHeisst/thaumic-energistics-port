package dev.eugheist.thaumicenergistics.menu;

import java.util.ArrayList;
import java.util.List;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageHelper;
import appeng.menu.locator.MenuHostLocator;
import dev.eugheist.tc4modern.EssentiaItems;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.me.EssentiaContainerItemStrategy;
import dev.eugheist.thaumicenergistics.me.EssentiaKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

/**
 * ContainerEssentiaCellTerminalBase: the original essentia terminal. The network's essentia list is mirrored to the client,
 * one aspect can be selected, the input/output work slots fill or empty phials and jars (and mark jar labels) with up to 64
 * essentia every 3 ticks, and clicking an aspect with a held container fills or empties it directly.
 */
public class EssentiaTermMenu extends AbstractContainerMenu {
    /** Where the terminal lives: a part on a cable or a wireless terminal in the player's hands. */
    public interface Host {
        @Nullable IGrid grid();
        IActionSource source(Player player);
        boolean stillValid(Player player);
        @Nullable Aspect selected();
        void selected(@Nullable Aspect aspect);
        int sortMode();
        int viewMode();
        void modes(int sort, int view);
        /** The two work slots: kept by a part, temporary (returned on close) for a wireless terminal. */
        Container workSlots();
        boolean temporarySlots();
        @Nullable MenuHostLocator locator();
        default void tick() {}
    }
    public record Entry(String aspect, long amount, boolean craftable) {
        public Aspect getAspect() { return Aspect.getAspect(aspect); }
    }
    public static final int SORT_ALPHABETIC = 0, SORT_AMOUNT = 1, VIEW_ALL = 0, VIEW_STORED = 1, VIEW_CRAFTABLE = 2;

    @Nullable final Host host;
    final Player player;
    final Container work;
    final Slot input, output;
    int tickCounter, workCounter, syncCounter, audioTracker;
    long lastSound;
    List<Entry> lastSent;
    // Client mirror.
    public List<Entry> entries = List.of();
    @Nullable public Aspect selected;
    public int sortMode, viewMode, version;

    public EssentiaTermMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) { this(id, inventory, (Host) null); }
    public EssentiaTermMenu(int id, Inventory inventory, @Nullable Host host) {
        super(ThaumicEnergistics.ESSENTIA_TERMINAL_MENU.get(), id);
        this.host = host;this.player = inventory.player;
        this.work = host != null ? host.workSlots() : new SimpleContainer(2);
        input = addSlot(new Slot(work, 0, 8, 92) { @Override public boolean mayPlace(ItemStack s) { return workable(s); } });
        output = addSlot(new Slot(work, 1, 26, 92) { @Override public boolean mayPlace(ItemStack s) { return false; } });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 122 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 180));
        if (host != null) { selected = host.selected();sortMode = host.sortMode();viewMode = host.viewMode(); }
    }

    static boolean label(ItemStack s) { return BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals("tc4modern:jar_label"); }
    static boolean workable(ItemStack s) { return label(s) || EssentiaContainerItemStrategy.isContainer(s); }

    @Override public boolean stillValid(Player player) { return host == null || host.stillValid(player); }
    @Override public void removed(Player player) { super.removed(player); if (host != null && host.temporarySlots() && !player.level().isClientSide) clearContainer(player, work); }

    @Override public void broadcastChanges() {
        if (host != null && player instanceof ServerPlayer sp) {
            host.tick();
            if (++tickCounter > 3) { tickCounter = 0;transferFromWorkSlots(); }
            if (syncCounter-- <= 0) { syncCounter = 5;sync(sp, false); }
        }
        super.broadcastChanges();
    }

    /** The network's essentia (stored and craftable), sent whenever it or the terminal's selection and modes change. */
    void sync(ServerPlayer sp, boolean force) {
        var list = new ArrayList<Entry>();var grid = host.grid();
        if (grid != null) {
            var craftable = grid.getCraftingService().getCraftables(k -> k instanceof EssentiaKey);
            var seen = new java.util.HashSet<AEKey>();
            for (var e : grid.getStorageService().getCachedInventory()) if (e.getKey() instanceof EssentiaKey k && e.getLongValue() > 0) { list.add(new Entry(k.getAspect().getTag(), e.getLongValue(), craftable.contains(k)));seen.add(k); }
            for (var k : craftable) if (!seen.contains(k)) list.add(new Entry(((EssentiaKey)k).getAspect().getTag(), 0, true));
        }
        list.sort(java.util.Comparator.comparing(Entry::aspect));
        var sel = host.selected();
        if (!force && list.equals(lastSent) && sel == selected && sortMode == host.sortMode() && viewMode == host.viewMode()) return;
        lastSent = list;selected = sel;sortMode = host.sortMode();viewMode = host.viewMode();
        PacketDistributor.sendToPlayer(sp, new ListPayload(containerId, list, sel == null ? "" : sel.getTag(), sortMode, viewMode));
    }

    // Essentia transfer -----------------------------------------------------------------------------------------------------

    static final class Ctx implements EssentiaContainerItemStrategy.Context {
        final ItemStack stack;ItemStack result = ItemStack.EMPTY;
        Ctx(ItemStack stack) { this.stack = stack.copyWithCount(1); }
        @Override public ItemStack getStack() { return stack; }
        @Override public void addOverflow(ItemStack s) { result = s; }
    }
    /** transferEssentia on a single item: label -> marked label, empty container -> filled from the network, else drained into it. */
    @Nullable ItemStack transfer(ItemStack one, @Nullable Aspect aspect, Actionable mode) {
        var grid = host == null ? null : host.grid();
        if (grid == null || one.isEmpty()) return null;
        if (label(one)) {
            if (aspect == null) return null;
            var marked = one.copyWithCount(1);EssentiaItems.setAspects(marked, new AspectList().add(aspect, 1));
            if (mode == Actionable.MODULATE) workCounter++;
            return marked;
        }
        if (!EssentiaContainerItemStrategy.isContainer(one)) return null;
        var strategy = EssentiaContainerItemStrategy.INSTANCE;var storage = grid.getStorageService().getInventory();var energy = grid.getEnergyService();var src = host.source(player);
        var held = strategy.getContainedStack(one);
        if (held == null) {
            if (aspect == null) return null;
            var key = EssentiaKey.of(aspect);
            long available = StorageHelper.poweredExtraction(energy, storage, key, EssentiaContainerItemStrategy.JAR, src, Actionable.SIMULATE);
            if (available <= 0) return null;
            var ctx = new Ctx(one);
            long put = strategy.insert(ctx, key, available, Actionable.SIMULATE);
            if (put <= 0) return null;
            if (mode == Actionable.SIMULATE) return EssentiaItems.phialOf(aspect, 1);
            long got = StorageHelper.poweredExtraction(energy, storage, key, put, src);
            if (got < put) { if (got > 0) StorageHelper.poweredInsert(energy, storage, key, got, src); return null; }
            strategy.insert(ctx, key, put, Actionable.MODULATE);workCounter += (int)put;
            return ctx.result;
        }
        var key = (EssentiaKey)held.what();
        long accepted = StorageHelper.poweredInsert(energy, storage, key, held.amount(), src, Actionable.SIMULATE);
        if (accepted <= 0) return null;
        var ctx = new Ctx(one);
        long taken = strategy.extract(ctx, key, accepted, Actionable.SIMULATE);
        if (taken <= 0) return null;
        if (mode == Actionable.SIMULATE) return one;
        long inserted = StorageHelper.poweredInsert(energy, storage, key, taken, src);
        if (inserted < taken) { if (inserted > 0) StorageHelper.poweredExtraction(energy, storage, key, inserted, src); return null; }
        strategy.extract(ctx, key, taken, Actionable.MODULATE);workCounter += (int)taken;
        return ctx.result;
    }
    boolean fitsOutput(ItemStack result) {
        var out = output.getItem();
        return out.isEmpty() || ItemStack.isSameItemSameComponents(out, result) && out.getCount() + result.getCount() <= out.getMaxStackSize();
    }
    /** transferEssentiaFromWorkSlots: one item at a time until 64 essentia moved, the input ran out or the output is full. */
    void transferFromWorkSlots() {
        workCounter = 0;
        if (output.getItem().getCount() >= output.getItem().getMaxStackSize() && !output.getItem().isEmpty()) return;
        while (!input.getItem().isEmpty()) {
            var one = input.getItem().copyWithCount(1);
            var simulated = transfer(one, host.selected(), Actionable.SIMULATE);
            if (simulated == null) break;
            // A simulated fill only knows the aspect, so check the output with the real result before taking anything.
            var result = transfer(one, host.selected(), Actionable.MODULATE);
            if (result == null) break;
            if (!fitsOutput(result)) { undo(one, result);break; }
            if (output.getItem().isEmpty()) output.set(result);else output.getItem().grow(1);
            input.getItem().shrink(1);input.setChanged();output.setChanged();
            if (workCounter >= 64) break;
        }
        if (workCounter > 0) sound(label(output.getItem()) ? 1 : 0);
    }
    /** Puts the essentia of a result that did not fit back where it came from. */
    void undo(ItemStack before, ItemStack after) {
        var grid = host.grid();if (grid == null) return;
        var strategy = EssentiaContainerItemStrategy.INSTANCE;var storage = grid.getStorageService().getInventory();var src = host.source(player);
        var was = strategy.getContainedStack(before);var now = strategy.getContainedStack(after);
        long diff = (now == null ? 0 : now.amount()) - (was == null ? 0 : was.amount());
        var key = now != null ? now.what() : was != null ? was.what() : null;
        if (key == null || diff == 0) return;
        if (diff > 0) storage.insert(key, diff, Actionable.MODULATE, src);else storage.extract(key, -diff, Actionable.MODULATE, src);
    }
    void sound(int type) {
        long now = System.currentTimeMillis();
        if (now - lastSound < 900) return;
        lastSound = now;
        SoundEvent s = type == 1 ? SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("tc4modern", "page")) : SoundEvents.GENERIC_SWIM;
        player.level().playSound(null, player.blockPosition(), s, SoundSource.PLAYERS, type == 1 ? 1 : .2f, 1);
    }

    /** onInteractWithHeldItem: the carried container (one of the stack) is filled with or emptied of the clicked aspect. */
    void interactHeld(@Nullable Aspect aspect) {
        var carried = getCarried();
        if (carried.isEmpty()) return;
        var one = carried.copyWithCount(1);
        var result = transfer(one, aspect, Actionable.SIMULATE);
        if (result == null) return;
        result = transfer(one, aspect, Actionable.MODULATE);
        if (result == null) return;
        if (carried.getCount() > 1) { carried.shrink(1);if (!player.getInventory().add(result)) player.drop(result, false); }
        else setCarried(result);
        sound(label(result) ? 1 : 0);
        broadcastChanges();
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();var copy = stack.copy();
        if (index < 2) { if (!moveItemStackTo(stack, 2, 38, true)) return ItemStack.EMPTY; }
        else if (workable(stack)) { if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY; }
        else if (index < 29) { if (!moveItemStackTo(stack, 29, 38, false)) return ItemStack.EMPTY; }
        else if (!moveItemStackTo(stack, 2, 29, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);else slot.setChanged();
        return copy;
    }

    void action(ServerPlayer sp, int action, @Nullable Aspect aspect, boolean flag) {
        if (host == null) return;
        switch (action) {
            case 0 -> host.selected(aspect);
            case 1 -> interactHeld(aspect);
            case 2 -> { // sendAutoCraft: AE2's craft amount screen for the aspect.
                var grid = host.grid();var locator = host.locator();
                if (aspect != null && grid != null && locator != null && grid.getCraftingService().isCraftable(EssentiaKey.of(aspect)))
                    appeng.menu.me.crafting.CraftAmountMenu.open(sp, locator, EssentiaKey.of(aspect), 1);
            }
            case 3 -> host.modes((host.sortMode() + 1) % 2, host.viewMode());
            case 4 -> host.modes(host.sortMode(), (host.viewMode() + (flag ? 2 : 1)) % 3);
            default -> {}
        }
        sync(sp, true);
    }

    // Packets ---------------------------------------------------------------------------------------------------------------

    public record ListPayload(int containerId, List<Entry> entries, String selected, int sort, int view) implements CustomPacketPayload {
        public static final Type<ListPayload> TYPE = new Type<>(ThaumicEnergistics.id("essentia_terminal_list"));
        static final StreamCodec<ByteBuf, Entry> ENTRY = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Entry::aspect, ByteBufCodecs.VAR_LONG, Entry::amount, ByteBufCodecs.BOOL, Entry::craftable, Entry::new);
        public static final StreamCodec<ByteBuf, ListPayload> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, ListPayload::containerId, ENTRY.apply(ByteBufCodecs.list()), ListPayload::entries,
            ByteBufCodecs.STRING_UTF8, ListPayload::selected, ByteBufCodecs.VAR_INT, ListPayload::sort, ByteBufCodecs.VAR_INT, ListPayload::view, ListPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        public static void handle(ListPayload p, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (!(context.player().containerMenu instanceof EssentiaTermMenu menu) || menu.containerId != p.containerId()) return;
                menu.entries = p.entries().stream().filter(e -> e.getAspect() != null).toList();
                menu.selected = p.selected().isEmpty() ? null : Aspect.getAspect(p.selected());
                menu.sortMode = p.sort();menu.viewMode = p.view();menu.version++;
            });
        }
    }
    /** Packet_S_EssentiaCellTerminal: 0 select, 1 held item, 2 autocraft, 3 sorting, 4 view (flag = backwards). */
    public record ActionPayload(int containerId, int action, String aspect, boolean flag) implements CustomPacketPayload {
        public static final Type<ActionPayload> TYPE = new Type<>(ThaumicEnergistics.id("essentia_terminal_action"));
        public static final StreamCodec<ByteBuf, ActionPayload> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, ActionPayload::containerId, ByteBufCodecs.VAR_INT, ActionPayload::action,
            ByteBufCodecs.STRING_UTF8, ActionPayload::aspect, ByteBufCodecs.BOOL, ActionPayload::flag, ActionPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        public static void handle(ActionPayload p, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer sp && sp.containerMenu instanceof EssentiaTermMenu menu && menu.containerId == p.containerId())
                    menu.action(sp, p.action(), p.aspect().isEmpty() ? null : Aspect.getAspect(p.aspect()), p.flag());
            });
        }
    }
    public void send(int action, @Nullable Aspect aspect, boolean flag) { PacketDistributor.sendToServer(new ActionPayload(containerId, action, aspect == null ? "" : aspect.getTag(), flag)); }
}
