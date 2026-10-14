package dev.eugheist.thaumicenergistics;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import dev.eugheist.tc4modern.RecipeAspects;
import dev.eugheist.tc4modern.api.TC4Addons;
import dev.eugheist.thaumicenergistics.me.EssentiaStorageType;

/**
 * AEAspectRegister: scan tags for every AE2 item.
 *
 * Original rules kept: fixed base tags (crystals, fluix, ender dust, silicon, sky stone, matter ball, facades, the four cable
 * families in every colour, cable anchor, P2P tunnels, singularities, encoded patterns); everything else takes its recipe
 * (AE2 rv3: grinder, inscriber, crafting; modern AE2: inscriber, crafting, transform, charger) where each aspect is the highest
 * amount found on any ingredient, plus the original per-block bonuses/removals, trimmed to the six largest aspects.
 *
 * Added for this port: items AE2 rv3 did not have, or that have no recipe (presses, budding quartz, buds, creative items,
 * mysterious cube...), receive designed tags so that every AE2 item scans. Essentia cells take the 1k item cell tags (at most
 * four aspects) plus Praecantatio 3 and Auram 5 as registerThEItems did.
 */
public final class AEAspects {
    private AEAspects() {}
    private static final int CRYSTAL_BASE = 2, CRYSTAL_PURE = 5, FLUIX_CHARGE = 6;
    static final Map<String, AspectList> BASE = new LinkedHashMap<>(), DESIGNED = new LinkedHashMap<>();
    private static final Map<RecipeManager, Map<Item, AspectList>> CACHE = new WeakHashMap<>();
    private static final ThreadLocal<Set<Item>> CHAIN = ThreadLocal.withInitial(HashSet::new);
    static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black", "fluix"};

    static AspectList tags(Object... kv) { var list = new AspectList(); for (int i = 0; i < kv.length; i += 2) list.add((Aspect) kv[i], (Integer) kv[i + 1]); return list; }
    static void base(String path, AspectList tags) { BASE.put("ae2:" + path, tags); }
    static void designed(String path, AspectList tags) { DESIGNED.put("ae2:" + path, tags); }

    static {
        // registerBase
        base("certus_quartz_crystal", tags(Aspect.CRYSTAL, CRYSTAL_BASE));
        base("charged_certus_quartz_crystal", tags(Aspect.CRYSTAL, CRYSTAL_BASE, Aspect.ENERGY, 4));
        base("quartz_ore", tags(Aspect.CRYSTAL, CRYSTAL_BASE, Aspect.EARTH, 1));
        base("deepslate_quartz_ore", tags(Aspect.CRYSTAL, CRYSTAL_BASE, Aspect.EARTH, 1));
        base("certus_crystal_seed", tags(Aspect.CRYSTAL, 1, Aspect.EXCHANGE, 1));
        base("fluix_crystal", tags(Aspect.CRYSTAL, CRYSTAL_BASE, Aspect.ENERGY, FLUIX_CHARGE));
        base("ender_dust", tags(Aspect.ELDRITCH, 2, Aspect.MOTION, 2, Aspect.MAGIC, 1, Aspect.ENTROPY, 1));
        base("silicon", tags(Aspect.FIRE, 1, Aspect.SENSES, 1, Aspect.ORDER, 1));
        for (var sky : List.of("sky_stone_block", "smooth_sky_stone_block", "sky_stone_brick", "sky_stone_small_brick")) base(sky, tags(Aspect.ELDRITCH, 2, Aspect.EARTH, 1, Aspect.DARKNESS, 1));
        base("matter_ball", tags(Aspect.SLIME, 4, Aspect.EARTH, 4, Aspect.FLIGHT, 4));
        base("facade", tags(Aspect.METAL, 1));
        var cable = tags(Aspect.CRYSTAL, 1, Aspect.ENERGY, 2);
        for (var color : COLORS) {
            base(color + "_glass_cable", cable.copy());
            base(color + "_covered_cable", cable.copy().add(Aspect.CLOTH, 1));
            base(color + "_smart_cable", cable.copy().add(Aspect.CLOTH, 1).add(Aspect.LIGHT, 1).add(Aspect.ENERGY, 1));
            base(color + "_smart_dense_cable", cable.copy().add(Aspect.CLOTH, 1).add(Aspect.LIGHT, 2).add(Aspect.ENERGY, 3));
            // Designed: rv3 had one dense cable (smart); the plain dense cable carries the extra channels without the lights.
            base(color + "_covered_dense_cable", cable.copy().add(Aspect.CLOTH, 2).add(Aspect.ENERGY, 3));
        }
        base("cable_anchor", tags(Aspect.METAL, 1));
        var p2p = tags(Aspect.METAL, 6, Aspect.ENERGY, 6, Aspect.CRYSTAL, 4, Aspect.GREED, 4, Aspect.ORDER, 4, Aspect.SENSES, 4);
        base("me_p2p_tunnel", p2p.copy()); base("item_p2p_tunnel", p2p.copy());
        base("fluid_p2p_tunnel", p2p.copy().add(Aspect.WATER, 4));
        base("light_p2p_tunnel", p2p.copy().add(Aspect.LIGHT, 4));
        base("redstone_p2p_tunnel", p2p.copy().add(Aspect.ENERGY, 2));
        base("fe_p2p_tunnel", p2p.copy().add(Aspect.ENERGY, 2));
        base("singularity", tags(Aspect.DARKNESS, 10, Aspect.VOID, 10, Aspect.ENTROPY, 10));
        base("quantum_entangled_singularity", tags(Aspect.ENERGY, 8, Aspect.EXCHANGE, 8, Aspect.MOTION, 4, Aspect.ELDRITCH, 4));
        for (var pattern : List.of("crafting_pattern", "processing_pattern", "smithing_table_pattern", "stonecutting_pattern")) base(pattern, tags(Aspect.CRAFT, 3, Aspect.MIND, 3));

        // Designed for items without a usable recipe.
        for (var press : List.of("calculation_processor_press", "engineering_processor_press", "logic_processor_press", "silicon_press")) designed(press, tags(Aspect.METAL, 6, Aspect.ORDER, 4, Aspect.CRAFT, 3, Aspect.ELDRITCH, 1));
        designed("name_press", tags(Aspect.METAL, 6, Aspect.ORDER, 2, Aspect.MIND, 2));
        designed("flawless_budding_quartz", tags(Aspect.CRYSTAL, 8, Aspect.LIFE, 4, Aspect.EARTH, 2));
        designed("flawed_budding_quartz", tags(Aspect.CRYSTAL, 6, Aspect.LIFE, 2, Aspect.EARTH, 2));
        designed("chipped_budding_quartz", tags(Aspect.CRYSTAL, 4, Aspect.LIFE, 1, Aspect.EARTH, 2));
        designed("damaged_budding_quartz", tags(Aspect.CRYSTAL, 3, Aspect.EARTH, 2, Aspect.ENTROPY, 1));
        designed("small_quartz_bud", tags(Aspect.CRYSTAL, 1));
        designed("medium_quartz_bud", tags(Aspect.CRYSTAL, 1, Aspect.LIFE, 1));
        designed("large_quartz_bud", tags(Aspect.CRYSTAL, 2, Aspect.LIFE, 1));
        designed("quartz_cluster", tags(Aspect.CRYSTAL, 3));
        designed("sky_dust", tags(Aspect.ELDRITCH, 1, Aspect.EARTH, 1, Aspect.ENTROPY, 1));
        designed("creative_energy_cell", tags(Aspect.ENERGY, 32, Aspect.MAGIC, 8, Aspect.VOID, 8));
        designed("creative_storage_cell", tags(Aspect.VOID, 32, Aspect.MAGIC, 8, Aspect.CRYSTAL, 8));
        designed("mysterious_cube", tags(Aspect.ELDRITCH, 6, Aspect.MIND, 4, Aspect.MECHANISM, 4));
        designed("paint", tags(Aspect.SENSES, 1, Aspect.ENTROPY, 1));
        designed("missing_content", tags(Aspect.ENTROPY, 1, Aspect.VOID, 1));
        designed("wrapped_generic_stack", tags(Aspect.VOID, 1, Aspect.EXCHANGE, 1));
        designed("guide", tags(Aspect.MIND, 3, Aspect.MECHANISM, 1));
        for (var debug : List.of("debug_card", "debug_cube_gen", "debug_energy_gen", "debug_eraser", "debug_item_gen", "debug_meteorite_placer", "debug_phantom_node", "debug_replicator_card"))
            designed(debug, tags(Aspect.MAGIC, 1, Aspect.MECHANISM, 1));
    }

    public static void register() {
        for (var entry : BASE.entrySet()) TC4Addons.registerObjectTag(ResourceLocation.parse(entry.getKey()), entry.getValue());
        TC4Addons.registerAspectProvider(AEAspects::provide);
    }

    @Nullable static AspectList provide(ItemStack stack, Level level) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id.getNamespace().equals("ae2")) return ae2(stack.getItem(), level);
        if (id.getNamespace().equals(ThaumicEnergistics.ID)) return thaumicEnergistics(stack.getItem(), level);
        return null;
    }

    /** registerThEItems: the 1k item cell tags, largest four kept, plus Praecantatio 3 and Auram 5, for every essentia cell. */
    @Nullable static AspectList thaumicEnergistics(Item item, Level level) {
        for (var type : EssentiaStorageType.values()) {
            if (type.creative() || ThaumicEnergistics.CELLS.get(type).get() != item) continue;
            var cell = map(RecipeAspects.get(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:item_storage_cell_1k"))), level));
            if (cell.size() == 0) return null;
            int count = cell.size();
            if (count > 4) { var sorted = cell.getAspectsSortedAmount(); cell.remove(sorted[count - 1]); if (count > 5) cell.remove(sorted[count - 2]); }
            return cell.add(Aspect.MAGIC, 3).add(Aspect.AURA, 5);
        }
        return null;
    }

    static AspectList map(Map<String, Integer> tags) { var list = new AspectList(); tags.forEach((k, v) -> { var a = Aspect.getAspect(k); if (a != null && v > 0) list.add(a, v); }); return list; }

    @Nullable static synchronized AspectList ae2(Item item, Level level) {
        var id = BuiltInRegistries.ITEM.getKey(item).toString();
        if (BASE.containsKey(id)) return BASE.get(id).copy();
        var cache = CACHE.computeIfAbsent(level.getRecipeManager(), m -> new HashMap<>());
        if (cache.containsKey(item)) return copy(cache.get(item));
        var chain = CHAIN.get();
        if (chain.contains(item)) return copy(DESIGNED.get(id));
        chain.add(item);
        try {
            var result = derive(item, id, level);
            if ((result == null || result.size() == 0) && DESIGNED.containsKey(id)) result = DESIGNED.get(id).copy();
            if (result == null || result.size() == 0) result = tags(Aspect.MECHANISM, 1, Aspect.CRYSTAL, 1);
            cache.put(item, result);
            return result.copy();
        } finally { chain.remove(item); }
    }
    @Nullable static AspectList copy(@Nullable AspectList list) { return list == null ? null : list.copy(); }

    static final List<String> RECIPE_ORDER = List.of("ae2:inscriber", "minecraft:crafting", "ae2:transform", "ae2:charger");

    @Nullable static AspectList derive(Item item, String id, Level level) {
        for (var typeId : RECIPE_ORDER) {
            var type = BuiltInRegistries.RECIPE_TYPE.get(ResourceLocation.parse(typeId));
            if (type == null) continue;
            for (RecipeHolder<?> holder : allOf(level.getRecipeManager(), type)) {
                Recipe<?> recipe = holder.value();
                var output = recipe.getResultItem(level.registryAccess());
                if (output.isEmpty() || output.getItem() != item) continue;
                var ingredientAspects = new AspectList();
                for (Ingredient ingredient : recipe.getIngredients()) {
                    var choices = ingredient.getItems();
                    if (choices.length == 0) continue;
                    var tags = map(RecipeAspects.get(choices[0], level));
                    for (var aspect : tags.getAspects()) {
                        int have = ingredientAspects.getAmount(aspect), amount = tags.getAmount(aspect);
                        if (have < amount) ingredientAspects.add(aspect, amount - have);
                    }
                }
                if (ingredientAspects.size() == 0) continue;
                var bonus = new AspectList();
                bonuses(id, ingredientAspects, bonus);
                return finalAspects(ingredientAspects, bonus);
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Collection<RecipeHolder<?>> allOf(RecipeManager manager, RecipeType<?> type) { return (Collection) manager.getAllRecipesFor((RecipeType) type); }

    /** addPredefinedBonuses. */
    static void bonuses(String id, AspectList ingredients, AspectList bonus) {
        switch (id) {
            case "ae2:molecular_assembler" -> bonus.add(Aspect.CRAFT, 4).add(Aspect.ENERGY, 6).add(Aspect.MECHANISM, 2).add(Aspect.CRYSTAL, 1);
            case "ae2:sky_stone_block" -> bonus.add(Aspect.DARKNESS, 3);
            case "ae2:sky_stone_chest", "ae2:smooth_sky_stone_chest" -> { bonus.add(Aspect.VOID, 4); ingredients.remove(Aspect.EARTH); }
            case "ae2:quartz_glass" -> bonus.add(Aspect.CRYSTAL, 4);
            case "ae2:controller" -> { bonus.add(Aspect.MECHANISM, 3).add(Aspect.SENSES, 1).add(Aspect.MIND, 9); ingredients.remove(Aspect.FIRE); ingredients.remove(Aspect.ORDER); }
            case "ae2:drive", "ae2:chest" -> { bonus.add(Aspect.VOID, 4); strip(ingredients); }
            case "ae2:io_port" -> { bonus.add(Aspect.EXCHANGE, 4); strip(ingredients); }
            case "ae2:condenser" -> bonus.add(Aspect.VOID, 10);
            case "ae2:redstone_card" -> bonus.add(Aspect.SENSES, 2);
            case "ae2:cell_workbench" -> bonus.add(Aspect.CLOTH, 2);
            case "ae2:interface" -> bonus.add(Aspect.EXCHANGE, 6);
            case "ae2:logic_processor" -> { processor(ingredients); bonus.add(Aspect.MIND, 3).add(Aspect.METAL, 1); }
            case "ae2:calculation_processor" -> { processor(ingredients); bonus.add(Aspect.MIND, 6).add(Aspect.METAL, 1); }
            case "ae2:engineering_processor" -> { processor(ingredients); bonus.add(Aspect.MIND, 9); }
            default -> {}
        }
    }
    static void strip(AspectList list) { list.remove(Aspect.FIRE); list.remove(Aspect.ORDER); list.remove(Aspect.SENSES); list.remove(Aspect.MECHANISM); }
    static void processor(AspectList list) { list.remove(Aspect.METAL); list.remove(Aspect.FIRE); list.remove(Aspect.ORDER); }

    /** getFinalAspects: ingredient maxima plus bonuses, only the six largest aspects kept. */
    static AspectList finalAspects(AspectList ingredients, AspectList bonus) {
        var result = new AspectList();
        for (var a : ingredients.getAspects()) if (a != null) result.add(a, ingredients.getAmount(a));
        for (var a : bonus.getAspects()) if (a != null) result.add(a, bonus.getAmount(a));
        if (result.size() > 6) { var sorted = result.getAspectsSortedAmount(); for (int i = 6; i < sorted.length; i++) result.remove(sorted[i]); }
        return result;
    }
}
