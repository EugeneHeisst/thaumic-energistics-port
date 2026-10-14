package dev.eugheist.thaumicenergistics.craft;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/** HandlerKnowledgeCore: up to 21 arcane patterns kept in the knowledge core, one per result item. */
public final class KnowledgeCore {
    public static final int MAX = 21;
    private KnowledgeCore() {}
    public static List<ArcanePattern> read(ItemStack core, HolderLookup.Provider registries) {
        var out = new ArrayList<ArcanePattern>();
        var data = core.get(DataComponents.CUSTOM_DATA);
        if (data == null) return out;
        var list = data.copyTag().getList("Patterns", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) { var p = ArcanePattern.load(list.getCompound(i), registries); if (p != null) out.add(p); }
        return out;
    }
    static void write(ItemStack core, List<ArcanePattern> patterns, HolderLookup.Provider registries) {
        var list = new ListTag();for (var p : patterns) list.add(p.save(registries));
        CustomData.update(DataComponents.CUSTOM_DATA, core, tag -> { if (list.isEmpty()) tag.remove("Patterns"); else tag.put("Patterns", list); });
        if (core.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).isEmpty()) core.remove(DataComponents.CUSTOM_DATA);
    }
    @Nullable public static ArcanePattern forOutput(ItemStack core, ItemStack output, HolderLookup.Provider registries) {
        for (var p : read(core, registries)) if (ItemStack.isSameItemSameComponents(p.output(), output)) return p;
        return null;
    }
    public static boolean add(ItemStack core, ArcanePattern pattern, HolderLookup.Provider registries) {
        var list = read(core, registries);
        if (list.size() >= MAX || list.stream().anyMatch(p -> ItemStack.isSameItemSameComponents(p.output(), pattern.output()))) return false;
        list.add(pattern);write(core, list, registries);return true;
    }
    public static boolean remove(ItemStack core, ItemStack output, HolderLookup.Provider registries) {
        var list = read(core, registries);
        if (!list.removeIf(p -> ItemStack.isSameItemSameComponents(p.output(), output))) return false;
        write(core, list, registries);return true;
    }
}
