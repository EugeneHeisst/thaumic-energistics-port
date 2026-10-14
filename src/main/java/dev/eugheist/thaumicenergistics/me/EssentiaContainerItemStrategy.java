package dev.eugheist.thaumicenergistics.me;

import appeng.api.behaviors.ContainerItemStrategy;
import appeng.api.config.Actionable;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import dev.eugheist.tc4modern.EssentiaItems;

/**
 * EssentiaItemContainerHelper for the terminals: phials hold exactly 8 of one aspect and fill or empty all at once; warded and
 * void jar items hold up to 64 of one aspect and respect their label filter. Works on one item of a stack at a time.
 */
public final class EssentiaContainerItemStrategy implements ContainerItemStrategy<EssentiaKey, EssentiaContainerItemStrategy.Context> {
    public static final EssentiaContainerItemStrategy INSTANCE = new EssentiaContainerItemStrategy();
    public static final int PHIAL = 8, JAR = 64;
    private EssentiaContainerItemStrategy() {}

    static String id(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
    static boolean emptyPhial(ItemStack s) { return id(s).equals("tc4modern:phial"); }
    static boolean filledPhial(ItemStack s) { return id(s).equals("tc4modern:essentia_phial"); }
    static boolean jar(ItemStack s) { var id = id(s); return id.equals("tc4modern:warded_jar") || id.equals("tc4modern:void_jar"); }
    static boolean container(ItemStack s) { return !s.isEmpty() && (emptyPhial(s) || filledPhial(s) || jar(s)); }
    public static boolean isContainer(ItemStack s) { return container(s); }
    public static Context playerSlot(Player player, int slot) { return new PlayerSlot(player, slot); }
    @Nullable static Aspect filter(ItemStack jar) { var data = jar.get(DataComponents.CUSTOM_DATA); return data == null ? null : Aspect.getAspect(data.copyTag().getString("AspectFilter")); }

    @Override @Nullable public GenericStack getContainedStack(ItemStack stack) {
        if (!(filledPhial(stack) || jar(stack))) return null;
        var aspects = EssentiaItems.getAspects(stack);
        if (aspects == null || aspects.size() == 0) return null;
        var aspect = aspects.getAspects()[0];
        return new GenericStack(EssentiaKey.of(aspect), aspects.getAmount(aspect));
    }
    @Override @Nullable public Context findCarriedContext(Player player, AbstractContainerMenu menu) { return container(menu.getCarried()) ? new Carried(player, menu) : null; }
    @Override @Nullable public Context findPlayerSlotContext(Player player, int slot) { return container(player.getInventory().getItem(slot)) ? new PlayerSlot(player, slot) : null; }

    /** Pull essentia out of the item into the network. */
    @Override public long extract(Context context, EssentiaKey what, long amount, Actionable mode) {
        var stack = context.getStack();var held = getContainedStack(stack);
        if (held == null || !held.what().equals(what)) return 0;
        long taken;
        ItemStack result;
        if (filledPhial(stack)) { if (amount < PHIAL) return 0; taken = PHIAL; result = new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("tc4modern:phial"))); }
        else {
            taken = Math.min(amount, held.amount());
            result = stack.copyWithCount(1);long left = held.amount() - taken;
            CustomData.update(DataComponents.CUSTOM_DATA, result, tag -> { tag.remove("Aspects"); if (left > 0) new AspectList().add(what.getAspect(), (int) left).writeToNBT(tag); });
            if (result.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).isEmpty()) result.remove(DataComponents.CUSTOM_DATA);
        }
        if (taken > 0 && mode == Actionable.MODULATE) { stack.shrink(1); context.addOverflow(result); }
        return taken;
    }

    /** Push essentia from the network into the item. */
    @Override public long insert(Context context, EssentiaKey what, long amount, Actionable mode) {
        var stack = context.getStack();
        long put;
        ItemStack result;
        if (emptyPhial(stack)) { if (amount < PHIAL) return 0; put = PHIAL; result = EssentiaItems.phialOf(what.getAspect(), PHIAL); }
        else if (jar(stack)) {
            var filter = filter(stack);if (filter != null && filter != what.getAspect()) return 0;
            var held = getContainedStack(stack);
            if (held != null && !held.what().equals(what)) return 0;
            long have = held == null ? 0 : held.amount();
            put = Math.min(amount, JAR - have);
            if (put <= 0) return 0;
            result = stack.copyWithCount(1);long total = have + put;
            CustomData.update(DataComponents.CUSTOM_DATA, result, tag -> { tag.remove("Aspects"); new AspectList().add(what.getAspect(), (int) total).writeToNBT(tag); });
        } else return 0;
        if (mode == Actionable.MODULATE) { stack.shrink(1); context.addOverflow(result); }
        return put;
    }

    @Override public void playFillSound(Player player, EssentiaKey what) { player.level().playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, .25f, 1); }
    @Override public void playEmptySound(Player player, EssentiaKey what) { player.level().playSound(null, player.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, .25f, 1); }
    @Override @Nullable public GenericStack getExtractableContent(Context context) { return getContainedStack(context.getStack()); }

    public interface Context { ItemStack getStack(); void addOverflow(ItemStack stack); }
    record Carried(Player player, AbstractContainerMenu menu) implements Context {
        public ItemStack getStack() { return menu.getCarried(); }
        public void addOverflow(ItemStack stack) { if (menu.getCarried().isEmpty()) menu.setCarried(stack); else player.getInventory().placeItemBackInInventory(stack); }
    }
    record PlayerSlot(Player player, int slot) implements Context {
        public ItemStack getStack() { return player.getInventory().getItem(slot); }
        public void addOverflow(ItemStack stack) { player.getInventory().placeItemBackInInventory(stack); }
    }
}
