package dev.eugheist.thaumicenergistics.craft;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.AspectList;
import dev.eugheist.tc4modern.ArcaneRecipes;

/**
 * ArcaneCraftingPattern: the 3x3 grid as inscribed (one item per slot, with the recipe's accepted substitutes, inscribed item
 * first), the result and the recipe's primal vis cost (whole vis).
 */
public record ArcanePattern(List<List<ItemStack>> slots, ItemStack output, AspectList cost) {
    public static final int SIZE = 9;

    public ItemStack preferred(int slot) { var s = slots.get(slot); return s.isEmpty() ? ItemStack.EMPTY : s.get(0); }
    public boolean valid() { return !output.isEmpty() && slots.size() == SIZE && slots.stream().anyMatch(s -> !s.isEmpty()); }

    /** Build from the inscriber grid and the matched recipe: every slot keeps the recipe ingredient it satisfies. */
    @Nullable public static ArcanePattern of(List<ItemStack> grid, ArcaneRecipes.Entry recipe) {
        var slots = new ArrayList<List<ItemStack>>();
        for (var placed : grid) {
            if (placed.isEmpty()) { slots.add(List.of()); continue; }
            var options = new ArrayList<ItemStack>();options.add(placed.copyWithCount(1));
            for (var ing : recipe.ingredients()) {
                if (ing.isEmpty() || !ing.test(placed)) continue;
                for (var alt : ing.getItems()) if (!ItemStack.isSameItemSameComponents(alt, placed)) options.add(alt.copyWithCount(1));
                break;
            }
            slots.add(List.copyOf(options));
        }
        var pattern = new ArcanePattern(List.copyOf(slots), recipe.output().copy(), recipe.cost().copy());
        return pattern.valid() ? pattern : null;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();var grid = new ListTag();
        for (var options : slots) { var list = new ListTag(); for (var s : options) list.add(s.save(registries)); grid.add(list); }
        tag.put("Grid", grid);tag.put("Output", output.save(registries));
        var cost = new CompoundTag();this.cost.writeToNBT(cost);tag.put("Cost", cost);
        return tag;
    }
    @Nullable public static ArcanePattern load(CompoundTag tag, HolderLookup.Provider registries) {
        var grid = tag.getList("Grid", Tag.TAG_LIST);
        var slots = new ArrayList<List<ItemStack>>();
        for (int i = 0; i < grid.size(); i++) {
            var options = new ArrayList<ItemStack>();var list = grid.getList(i);
            for (int j = 0; j < list.size(); j++) ItemStack.parse(registries, list.getCompound(j)).ifPresent(options::add);
            slots.add(List.copyOf(options));
        }
        var output = ItemStack.parseOptional(registries, tag.getCompound("Output"));
        var cost = new AspectList();cost.readFromNBT(tag.getCompound("Cost"));
        var pattern = new ArcanePattern(List.copyOf(slots), output, cost);
        return pattern.valid() ? pattern : null;
    }
}
