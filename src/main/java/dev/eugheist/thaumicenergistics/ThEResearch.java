package dev.eugheist.thaumicenergistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.core.registries.Registries;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.ArcanePattern;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchItem;
import thaumcraft.api.research.ResearchPage;
import dev.eugheist.tc4modern.ArcaneRecipes;
import dev.eugheist.tc4modern.api.TC4Addons;
import dev.eugheist.thaumicenergistics.me.EssentiaStorageType;

/**
 * ResearchRegistry + Feature*.registerResearch/registerCrafting for the ported features: the "thaumicenergistics" tab with
 * TERESEARCH, TECORES, TESTORAGE and TEESSTERM at their original positions, aspects, parents and pages, plus the
 * pseudo entries that mirror DISTILESSENTIA and WARDEDARCANA inside the tab.
 */
public final class ThEResearch {
    private ThEResearch() {}
    public static final String TAB = "thaumicenergistics";
    public static final String BASIC = key("RESEARCH"), CORES = key("CORES"), STORAGE = key("STORAGE"), ESSTERM = key("ESSTERM"), IO = key("IO"), ARCANETERM = key("ARCANETERM"), VISINT = key("VISINT"), ESSPROV = key("ESSPROV"), INFPROV = key("INFPROV"), ASSEMBLER = key("ARCANEASSEMBLER"), INSCRIBER = key("KNOWLEDGEINSCRIBER"), DPE = key("DISTILLATIONPATTERNENCODER"), CERTUSDUPE = key("CERTUSDUPE"), FOCUSWRENCH = key("FOCUSWRENCH"), ESSVIBCMBR = key("ESSVIBCMBR"), IRONGEARBOX = key("IRONGEARBOX"), THAUMGBOX = key("THAUMGBOX"), GOLEMWIFI = key("GOLEMWIFIBACKPACK");
    static String key(String name) { return "thaumicenergistics.TE" + name; }
    static String page(String name, int index) { return "thaumicenergistics.research_page.TE" + name + "." + index; }

    static Item item(String id) { var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)); if (item == net.minecraft.world.item.Items.AIR) throw new IllegalStateException("Missing ingredient " + id); return item; }
    static Ingredient of(String... ids) { var items = new ItemStack[ids.length]; for (int i = 0; i < ids.length; i++) items[i] = new ItemStack(item(ids[i])); return Ingredient.of(items); }
    static Ingredient tag(String id) { return Ingredient.of(TagKey.create(Registries.ITEM, ResourceLocation.parse(id))); }
    static Ingredient of(Item item) { return Ingredient.of(item); }

    /** ThaumcraftApi.addArcaneCraftingRecipe: rows with characters keyed into the map; ' ' is an empty slot. */
    static String shaped(String key, String research, ItemStack output, AspectList cost, String[] rows, Map<Character, Ingredient> keys) {
        int height = rows.length, width = rows[0].length();
        var ingredients = new ArrayList<Ingredient>();var predicates = new ArrayList<Predicate<ItemStack>>();
        for (var row : rows) for (char c : row.toCharArray()) { var ing = c == ' ' ? Ingredient.EMPTY : keys.get(c); ingredients.add(ing); predicates.add(c == ' ' ? null : ing::test); }
        TC4Addons.addArcaneRecipe(new ArcaneRecipes.Entry(key, research, width, height, List.copyOf(ingredients), output, cost, new ArcanePattern<>(research, width, height, predicates)));
        return key;
    }
    /** ThaumcraftApi.addShapelessArcaneCraftingRecipe. */
    static String shapeless(String key, String research, ItemStack output, AspectList cost, Ingredient... inputs) {
        // The 3x3 arcane grid: inputs fill rows of three, the remainder are empty slots (ShapelessArcaneRecipe has no shape).
        int height = (inputs.length + 2) / 3;var ingredients = new ArrayList<Ingredient>();var predicates = new ArrayList<Predicate<ItemStack>>();
        for (int i = 0; i < height * 3; i++) { var ing = i < inputs.length ? inputs[i] : Ingredient.EMPTY; ingredients.add(ing); predicates.add(i < inputs.length ? ing::test : null); }
        TC4Addons.addArcaneRecipe(new ArcaneRecipes.Entry(key, research, 3, height, List.copyOf(ingredients), output, cost, new ArcanePattern<>(research, 3, height, predicates).setShapeless(true)));
        return key;
    }
    /** ThaumcraftApi.addInfusionCraftingRecipe. */
    static String infusion(String key, String research, net.minecraft.world.level.ItemLike output, int instability, AspectList aspects, String central, String... components) {
        var parts = new ArrayList<java.util.function.Supplier<Item>>();for (var c : components) { var it = item(c); parts.add(() -> it); }
        var centre = item(central);
        TC4Addons.addInfusionRecipe(new dev.eugheist.tc4modern.InfusionItemRecipes.Recipe(key, research, output::asItem, instability, aspects, () -> centre, List.copyOf(parts)));
        return key;
    }
    static ResearchPage infusionPage(String key) { return recipe(ResearchPage.PageType.INFUSION_CRAFTING, key); }
    static ResearchPage text(String key) { return new ResearchPage(key); }
    static ResearchPage recipe(ResearchPage.PageType type, String... keys) { var page = new ResearchPage(""); page.type = type; page.recipeKey = String.join("|", keys); return page; }
    static ResearchPage arcane(String... keys) { return recipe(ResearchPage.PageType.ARCANE_CRAFTING, keys); }
    static ResearchPage crafting(String... ids) { var keys = new String[ids.length]; for (int i = 0; i < ids.length; i++) keys[i] = ThaumicEnergistics.ID + ":" + ids[i]; return recipe(ResearchPage.PageType.NORMAL_CRAFTING, keys); }
    static ItemStack stack(net.minecraft.world.level.ItemLike item) { return new ItemStack(item); }

    /** PseudoResearchItem: a hidden stub in this tab that shows a real research from another tab and completes with it. */
    static void pseudo(String realKey, int column, int row) {
        var real = ResearchCategories.getResearch(realKey);
        if (real == null) return;
        String key = "thaumicenergistics.Pseudo." + realKey;
        var pseudo = real.icon_item != null ? new Pseudo(key, real, column, row, real.icon_item) : new Pseudo(key, real, column, row, real.icon_resource);
        pseudo.setStub().setHidden();
        var siblings = new ArrayList<String>();if (real.siblings != null) siblings.addAll(List.of(real.siblings));siblings.add(key);real.setSiblings(siblings.toArray(String[]::new));
        pseudo.registerResearchItem();
        dev.eugheist.tc4modern.api.TC4Addons.registerResearchIconAlias(key, realKey);
    }
    static final class Pseudo extends ResearchItem {
        final ResearchItem real;
        Pseudo(String key, ResearchItem real, int column, int row, ItemStack icon) { super(key, TAB, new AspectList(), column, row, 1, icon); this.real = real; }
        Pseudo(String key, ResearchItem real, int column, int row, ResourceLocation icon) { super(key, TAB, new AspectList(), column, row, 1, icon); this.real = real; }
        @Override public String getName() { return real.getName(); }
        @Override public String getText() { return real.getText(); }
        @Override public ResearchPage[] getPages() { return real.getPages(); }
    }

    public static void register() {
        thaumcraft.common.config.ConfigResearchData.init();
        var tabIcon = ThaumicEnergistics.id("textures/research/tab_icon.png");
        ResearchCategories.registerCategory(TAB, tabIcon, ThaumicEnergistics.id("textures/research/research_background.png"));
        // FeatureThaumicEnergisticsResearch
        new ResearchItem(BASIC, TAB, new AspectList(), 0, 0, 0, tabIcon).setPages(text(page("RESEARCH", 1))).setRound().setAutoUnlock().registerResearchItem();
        pseudo("DISTILESSENTIA", -2, 0);
        if (dev.eugheist.tc4modern.WarpConfig.wardedStone()) pseudo("WARDEDARCANA", 1, 2);
        String distil = "thaumicenergistics.Pseudo.DISTILESSENTIA";

        // FeatureConversionCores
        var coalescence = shapeless("ThECoalescenceCore", CORES, stack(ThaumicEnergistics.COALESCENCE_CORE.get()), new AspectList().add(Aspect.WATER, 2).add(Aspect.ORDER, 2),
            of("tc4modern:quicksilver_drop"), of("tc4modern:quicksilver_drop"), of("tc4modern:quicksilver_drop"), of("tc4modern:order_shard"), of("ae2:formation_core"));
        var diffusion = shapeless("ThEDiffusionCore", CORES, stack(ThaumicEnergistics.DIFFUSION_CORE.get()), new AspectList().add(Aspect.WATER, 2).add(Aspect.ENTROPY, 2),
            of("tc4modern:quicksilver_drop"), of("tc4modern:quicksilver_drop"), of("tc4modern:quicksilver_drop"), of("tc4modern:entropy_shard"), of("ae2:annihilation_core"));
        new ResearchItem(CORES, TAB, new AspectList().add(Aspect.SLIME, 3).add(Aspect.MAGIC, 5).add(Aspect.MECHANISM, 5).add(Aspect.EXCHANGE, 5), 0, -2, 2, stack(ThaumicEnergistics.COALESCENCE_CORE.get()))
            .setPages(text(page("CORES", 1)), text(page("CORES", 2)), arcane(coalescence), arcane(diffusion))
            .setParents(BASIC, distil).setParentsHidden("DISTILESSENTIA").registerResearchItem();

        // FeatureCells
        var c = ThaumicEnergistics.COMPONENTS;var cells = ThaumicEnergistics.CELLS;
        var quartzGroup = of("ae2:certus_quartz_crystal", "ae2:charged_certus_quartz_crystal");
        var essence = of("tc4modern:wisp_essence");var salis = of("tc4modern:salis_mundus");var quartzGlass = of("ae2:quartz_glass");
        String comp1 = shaped("ThEStorageComponent1k", STORAGE, stack(c.get(EssentiaStorageType.TYPE_1K).get()), new AspectList().add(Aspect.FIRE, 3).add(Aspect.ORDER, 1),
            new String[]{"EQ ", "QPQ", " QE"}, Map.of('E', essence, 'Q', quartzGroup, 'P', of("ae2:logic_processor")));
        String comp4 = shaped("ThEStorageComponent4k", STORAGE, stack(c.get(EssentiaStorageType.TYPE_4K).get()), new AspectList().add(Aspect.FIRE, 3).add(Aspect.ORDER, 2),
            new String[]{"EPE", "1G1", "E1E"}, Map.of('E', essence, '1', of(c.get(EssentiaStorageType.TYPE_1K).get()), 'P', of("ae2:calculation_processor"), 'G', quartzGlass));
        String comp16 = shaped("ThEStorageComponent16k", STORAGE, stack(c.get(EssentiaStorageType.TYPE_16K).get()), new AspectList().add(Aspect.FIRE, 3).add(Aspect.ORDER, 4),
            new String[]{"SPE", "4G4", "E4S"}, Map.of('E', essence, 'S', salis, '4', of(c.get(EssentiaStorageType.TYPE_4K).get()), 'P', of("ae2:engineering_processor"), 'G', quartzGlass));
        String comp64 = shaped("ThEStorageComponent64k", STORAGE, stack(c.get(EssentiaStorageType.TYPE_64K).get()), new AspectList().add(Aspect.FIRE, 3).add(Aspect.ORDER, 8),
            new String[]{"SPS", "6G6", "S6S"}, Map.of('S', salis, '6', of(c.get(EssentiaStorageType.TYPE_16K).get()), 'P', of("ae2:engineering_processor"), 'G', quartzGlass));
        var storageParents = new ArrayList<String>(List.of(BASIC, distil));
        if (dev.eugheist.tc4modern.WarpConfig.wardedStone()) storageParents.add("thaumicenergistics.Pseudo.WARDEDARCANA");
        new ResearchItem(STORAGE, TAB, new AspectList().add(Aspect.VOID, 5).add(Aspect.ENERGY, 5).add(Aspect.CRYSTAL, 3).add(Aspect.METAL, 3), 0, 2, 2, stack(cells.get(EssentiaStorageType.TYPE_64K).get()))
            .setPages(text(page("STORAGE", 1)), text(page("STORAGE", 2)), arcane(comp1), arcane(comp4, comp16, comp64), crafting("essentia_cell_casing"),
                crafting("essentia_cell_1k", "essentia_cell_4k", "essentia_cell_16k", "essentia_cell_64k"),
                crafting("essentia_cell_1k_from_component", "essentia_cell_4k_from_component", "essentia_cell_16k_from_component", "essentia_cell_64k_from_component"),
                crafting("essentia_cell_workbench"))
            .setParents(storageParents.toArray(String[]::new)).setParentsHidden("DISTILESSENTIA").registerResearchItem();

        // FeatureEssentiaMonitoring (the wireless terminal page arrives with that item)
        var terminal = shapeless("ThEEssentiaTerminal", ESSTERM, stack(ThaumicEnergistics.ESSENTIA_TERMINAL.get()), new AspectList().add(Aspect.WATER, 5).add(Aspect.ORDER, 2).add(Aspect.FIRE, 1),
            tag("ae2:illuminated_panel"), of(ThaumicEnergistics.DIFFUSION_CORE.get()), of(ThaumicEnergistics.COALESCENCE_CORE.get()), of("ae2:logic_processor"), of("tc4modern:vis_filter"));
        var emitter = shapeless("ThEEssentiaLevelEmitter", ESSTERM, new ItemStack(ThaumicEnergistics.ESSENTIA_LEVEL_EMITTER.get(), 4), new AspectList().add(Aspect.FIRE, 4),
            of("ae2:calculation_processor"), of("minecraft:redstone_torch"), of("tc4modern:salis_mundus"));
        new ResearchItem(ESSTERM, TAB, new AspectList().add(Aspect.EXCHANGE, 5).add(Aspect.SENSES, 5).add(Aspect.ENERGY, 3).add(Aspect.WATER, 3), -1, -4, 1, stack(ThaumicEnergistics.ESSENTIA_TERMINAL.get()))
            .setPages(text(page("ESSTERM", 1)), text(page("ESSTERM", 2)), arcane(terminal), crafting("wireless_essentia_terminal"), text(page("ESSTERM", 3)), arcane(emitter), text(page("ESSTERM", 4)),
                crafting("essentia_storage_monitor"), crafting("essentia_conversion_monitor"))
            .setParents(CORES).setConcealed().setSecondary().registerResearchItem();

        // FeatureACT
        var act = shapeless("ThEArcaneCraftingTerminal", ARCANETERM, stack(ThaumicEnergistics.ARCANE_CRAFTING_TERMINAL.get()),
            new AspectList().add(Aspect.AIR, 10).add(Aspect.EARTH, 10).add(Aspect.ENTROPY, 10).add(Aspect.FIRE, 10).add(Aspect.ORDER, 10).add(Aspect.WATER, 10),
            of("ae2:terminal"), of("tc4modern:arcane_workbench"), of("ae2:calculation_processor"));
        new ResearchItem(ARCANETERM, TAB, new AspectList().add(Aspect.CRAFT, 5).add(Aspect.ENERGY, 3).add(Aspect.MECHANISM, 3).add(Aspect.VOID, 3), 2, -1, 1, stack(ThaumicEnergistics.ARCANE_CRAFTING_TERMINAL.get()))
            .setPages(text(page("ARCANETERM", 1)), arcane(act), text(page("ARCANETERM", 2)), crafting("wireless_arcane_crafting_terminal")).setParents(BASIC).registerResearchItem();

        // FeatureEssentiaProvider / FeatureInfusionProvider
        var providerCost = new AspectList().add(Aspect.MECHANISM, 64).add(Aspect.MAGIC, 32).add(Aspect.ORDER, 32).add(Aspect.EXCHANGE, 16);
        var essProvider = infusion("ThEEssentiaProvider", ESSPROV, ThaumicEnergistics.ESSENTIA_PROVIDER.get(), 3, providerCost, "ae2:interface",
            "tc4modern:essentia_tube_filter", "tc4modern:salis_mundus", "thaumicenergistics:coalescence_core", "tc4modern:water_shard",
            "tc4modern:essentia_tube_filter", "tc4modern:salis_mundus", "thaumicenergistics:diffusion_core", "tc4modern:water_shard");
        new ResearchItem(ESSPROV, TAB, new AspectList().add(Aspect.MECHANISM, 3).add(Aspect.MAGIC, 5).add(Aspect.ORDER, 3).add(Aspect.SENSES, 7), -2, -4, 3, stack(ThaumicEnergistics.ESSENTIA_PROVIDER.get()))
            .setPages(text(page("ESSPROV", 1)), infusionPage(essProvider)).setParents(IO).setParentsHidden("INFUSION", "TUBEFILTER").setConcealed().registerResearchItem();
        boolean mirrors = dev.eugheist.tc4modern.WarpConfig.allowMirrors();
        if (mirrors) pseudo("MIRROR", -4, 0); else pseudo("JARLABEL", -4, 0);
        pseudo("INFUSION", -6, 0);
        String mirror = mirrors ? "tc4modern:mirror_essentia" : "tc4modern:warded_jar";
        var infProvider = infusion("ThEInfusionProvider", INFPROV, ThaumicEnergistics.INFUSION_PROVIDER.get(), 4, providerCost.copy(), "ae2:interface",
            mirror, "tc4modern:salis_mundus", "thaumicenergistics:coalescence_core", "tc4modern:air_shard",
            mirror, "tc4modern:salis_mundus", "thaumicenergistics:coalescence_core", "tc4modern:air_shard");
        new ResearchItem(INFPROV, TAB, new AspectList().add(Aspect.MECHANISM, 3).add(Aspect.MAGIC, 3).add(Aspect.EXCHANGE, 7).add(Aspect.MOTION, 7).add(Aspect.SENSES, 5), -5, -2, 3, stack(ThaumicEnergistics.INFUSION_PROVIDER.get()))
            .setPages(text(page("INFPROV", 1)), infusionPage(infProvider))
            .setParents(IO, "thaumicenergistics.Pseudo." + (mirrors ? "MIRROR" : "JARLABEL"), "thaumicenergistics.Pseudo.INFUSION").setParentsHidden(mirrors ? "MIRROR" : "JARLABEL", "INFUSION")
            .setConcealed().setSpecial().registerResearchItem();

        // FeatureVisRelayInterface
        pseudo("VISPOWER", 4, 0);
        var visInterface = shapeless("ThEVisRelayInterface", VISINT, stack(ThaumicEnergistics.VIS_INTERFACE.get()),
            new AspectList().add(Aspect.AIR, 2).add(Aspect.EARTH, 2).add(Aspect.ENTROPY, 2).add(Aspect.FIRE, 2).add(Aspect.ORDER, 2).add(Aspect.WATER, 2),
            of("tc4modern:balanced_shard"), of("ae2:me_p2p_tunnel"));
        new ResearchItem(VISINT, TAB, new AspectList().add(Aspect.AURA, 5).add(Aspect.ENERGY, 4).add(Aspect.VOID, 3).add(Aspect.MECHANISM, 2), 4, -1, 2, stack(ThaumicEnergistics.VIS_INTERFACE.get()))
            .setPages(text(page("VISINT", 1)), arcane(visInterface), text(page("VISINT", 2)), text(page("VISINT", 3)))
            .setParents(ARCANETERM, "thaumicenergistics.Pseudo.VISPOWER").setParentsHidden("VISPOWER").registerResearchItem();

        // FeatureAutocrafting_Arcane (the crafting sceptre component is any wand of casting)
        pseudo("SCEPTRE", 5, 0);
        var assembler = infusion("ThEArcaneAssembler", ASSEMBLER, ThaumicEnergistics.ARCANE_ASSEMBLER.get(), 7,
            new AspectList().add(Aspect.CRAFT, 64).add(Aspect.EXCHANGE, 32).add(Aspect.AURA, 16).add(Aspect.MAGIC, 16).add(Aspect.METAL, 8).add(Aspect.CRYSTAL, 8), "ae2:molecular_assembler",
            "tc4modern:wand", "tc4modern:air_shard", "tc4modern:earth_shard", "tc4modern:water_shard", "tc4modern:balanced_shard", "tc4modern:order_shard", "tc4modern:entropy_shard", "tc4modern:fire_shard");
        new ResearchItem(ASSEMBLER, TAB, new AspectList().add(Aspect.CRAFT, 5).add(Aspect.MECHANISM, 3).add(Aspect.MIND, 3).add(Aspect.EXCHANGE, 3).add(Aspect.AURA, 3).add(Aspect.GREED, 3), 6, -1, 3, stack(ThaumicEnergistics.ARCANE_ASSEMBLER.get()))
            .setPages(text(page("ARCANEASSEMBLER", 1)), infusionPage(assembler), text(page("ARCANEASSEMBLER", 2)), text(page("ARCANEASSEMBLER", 3)))
            .setParents(VISINT, "thaumicenergistics.Pseudo.SCEPTRE").setParentsHidden(ARCANETERM).setConcealed().registerResearchItem();
        var kcore = shaped("ThEKnowledgeCore", INSCRIBER, stack(ThaumicEnergistics.KNOWLEDGE_CORE.get()), new AspectList().add(Aspect.WATER, 3).add(Aspect.ORDER, 3).add(Aspect.EARTH, 1),
            new String[]{"VLV", "LZL", "VCV"}, Map.of('V', of("ae2:quartz_vibrant_glass"), 'L', of("minecraft:lapis_lazuli"), 'Z', of("tc4modern:zombie_brain"), 'C', of("ae2:calculation_processor")));
        var inscriber = shaped("ThEKnowledgeInscriber", INSCRIBER, stack(ThaumicEnergistics.KNOWLEDGE_INSCRIBER.get()), new AspectList().add(Aspect.WATER, 5).add(Aspect.ORDER, 5).add(Aspect.EARTH, 5).add(Aspect.ENTROPY, 5).add(Aspect.FIRE, 5).add(Aspect.AIR, 5),
            new String[]{"IPI", " T ", "ILI"}, Map.of('I', tag("c:ingots/iron"), 'P', tag("ae2:illuminated_panel"), 'T', of("tc4modern:thaumonomicon"), 'L', of("ae2:logic_processor")));
        new ResearchItem(INSCRIBER, TAB, new AspectList().add(Aspect.MIND, 5).add(Aspect.MECHANISM, 3).add(Aspect.CRAFT, 3).add(Aspect.EXCHANGE, 1), 6, 0, 1, stack(ThaumicEnergistics.KNOWLEDGE_INSCRIBER.get()))
            .setPages(text(page("KNOWLEDGEINSCRIBER", 1)), arcane(kcore), arcane(inscriber), text(page("KNOWLEDGEINSCRIBER", 2)))
            .setParents(ASSEMBLER).setSecondary().setConcealed().registerResearchItem();

        // FeatureAutocrafting_Essentia
        var dpe = shaped("ThEDistillationPatternEncoder", DPE, stack(ThaumicEnergistics.DISTILLATION_ENCODER.get()), new AspectList().add(Aspect.ORDER, 5).add(Aspect.ENTROPY, 5).add(Aspect.FIRE, 3),
            new String[]{"IPI", " T ", "IEI"}, Map.of('I', tag("c:ingots/iron"), 'P', tag("ae2:illuminated_panel"), 'T', of("tc4modern:thaumometer"), 'E', of("ae2:engineering_processor")));
        new ResearchItem(DPE, TAB, new AspectList().add(Aspect.CRAFT, 5).add(Aspect.EXCHANGE, 5).add(Aspect.ENTROPY, 5).add(Aspect.MECHANISM, 3).add(Aspect.GREED, 3).add(Aspect.MIND, 1), -4, -4, 3, stack(ThaumicEnergistics.DISTILLATION_ENCODER.get()))
            .setPages(text(page("DISTILLATIONPATTERNENCODER", 1)), arcane(dpe)).setParents(IO).setParentsHidden(ESSTERM).setConcealed().registerResearchItem();

        // FeatureQuartzDupe: crucible duplication of certus and nether quartz
        pseudo("ALCHEMICALDUPLICATION", -5, -6);
        TC4Addons.addCrucibleRecipe("ThECertusDupe", new thaumcraft.api.crafting.CrucibleRecipe(CERTUSDUPE, new ItemStack(item("ae2:certus_quartz_crystal"), 2), tag("c:gems/certus_quartz"), new AspectList().add(Aspect.CRYSTAL, 4).add(Aspect.WATER, 2)));
        TC4Addons.addCrucibleRecipe("ThENetherQuartzDupe", new thaumcraft.api.crafting.CrucibleRecipe(CERTUSDUPE, new ItemStack(item("minecraft:quartz"), 2), tag("c:gems/quartz"), new AspectList().add(Aspect.CRYSTAL, 4).add(Aspect.WATER, 2).add(Aspect.ENERGY, 2)));
        new ResearchItem(CERTUSDUPE, TAB, new AspectList().add(Aspect.CRYSTAL, 5), -5, -5, 1, new ItemStack(item("ae2:certus_quartz_crystal")))
            .setPages(text(page("CERTUSDUPE", 1)), recipe(ResearchPage.PageType.CRUCIBLE_CRAFTING, "ThECertusDupe"), recipe(ResearchPage.PageType.CRUCIBLE_CRAFTING, "ThENetherQuartzDupe"))
            .setParents("thaumicenergistics.Pseudo.ALCHEMICALDUPLICATION").setParentsHidden("ALCHEMICALDUPLICATION").setSecondary().setConcealed().registerResearchItem();

        // FeatureWrenchFocus
        pseudo("FOCUSFIRE", -4, -7);
        var focus = shaped("ThEWrenchFocus", FOCUSWRENCH, stack(ThaumicEnergistics.FOCUS_AEWRENCH.get()), new AspectList().add(Aspect.AIR, 10).add(Aspect.FIRE, 10),
            new String[]{"ANF", "NWN", "FNA"}, Map.of('A', of("tc4modern:air_shard"), 'F', of("tc4modern:fire_shard"), 'N', tag("c:gems/quartz"), 'W', of("ae2:certus_quartz_wrench")));
        new ResearchItem(FOCUSWRENCH, TAB, new AspectList().add(Aspect.MECHANISM, 6).add(Aspect.TOOL, 5).add(Aspect.MAGIC, 3), -3, -7, 1, stack(ThaumicEnergistics.FOCUS_AEWRENCH.get()))
            .setPages(text(page("FOCUSWRENCH", 1)), arcane(focus)).setParents("thaumicenergistics.Pseudo.FOCUSFIRE").setSecondary().registerResearchItem();

        // FeatureEssentiaVibrationChamber
        var evc = shapeless("ThEEssentiaVibrationChamber", ESSVIBCMBR, stack(ThaumicEnergistics.VIBRATION_CHAMBER.get()), new AspectList().add(Aspect.WATER, 2).add(Aspect.ENTROPY, 4).add(Aspect.FIRE, 7).add(Aspect.ORDER, 4),
            of("ae2:vibration_chamber"), of(ThaumicEnergistics.DIFFUSION_CORE.get()), of("tc4modern:warded_jar"));
        new ResearchItem(ESSVIBCMBR, TAB, new AspectList().add(Aspect.MECHANISM, 5).add(Aspect.MAGIC, 3).add(Aspect.WATER, 1).add(Aspect.MOTION, 3).add(Aspect.ENERGY, 5), 1, -4, 2, stack(ThaumicEnergistics.VIBRATION_CHAMBER.get()))
            .setPages(text(page("ESSVIBCMBR", 1)), arcane(evc), text(page("ESSVIBCMBR", 2)), text(page("ESSVIBCMBR", 3))).setParents(CORES).setConcealed().registerResearchItem();

        // FeatureGearbox (no wooden gear in 1.21: a stick, as the original fell back to)
        var gear = shaped("ThEIronGear", IRONGEARBOX, stack(ThaumicEnergistics.IRON_GEAR.get()), new AspectList().add(Aspect.EARTH, 1).add(Aspect.FIRE, 1),
            new String[]{" I ", " W ", "I I"}, Map.of('I', tag("c:ingots/iron"), 'W', tag("c:rods/wooden")));
        var gearbox = shaped("ThEIronGearBox", IRONGEARBOX, stack(ThaumicEnergistics.IRON_GEARBOX.get()), new AspectList().add(Aspect.AIR, 2).add(Aspect.ORDER, 2),
            new String[]{"SGS", "GGG", "SGS"}, Map.of('S', of("minecraft:cobblestone"), 'G', tag("c:gears/iron")));
        new ResearchItem(IRONGEARBOX, TAB, new AspectList().add(Aspect.MECHANISM, 6).add(Aspect.METAL, 4).add(Aspect.EXCHANGE, 4), 4, -5, 1, stack(ThaumicEnergistics.IRON_GEAR.get()))
            .setPages(text(page("IRONGEARBOX", 1)), text(page("IRONGEARBOX", 2)), arcane(gear), arcane(gearbox)).setSecondary().registerResearchItem();
        pseudo("COREUSE", 3, -6);
        TC4Addons.addCrucibleRecipe("ThEThaumiumGearBox", new thaumcraft.api.crafting.CrucibleRecipe(THAUMGBOX, stack(ThaumicEnergistics.THAUMIUM_GEARBOX.get()), of(ThaumicEnergistics.IRON_GEARBOX_ITEM.get()), new AspectList().add(Aspect.METAL, 16).add(Aspect.MAGIC, 16)));
        new ResearchItem(THAUMGBOX, TAB, new AspectList().add(Aspect.MECHANISM, 10).add(Aspect.MAGIC, 8).add(Aspect.METAL, 5), 4, -6, 1, stack(ThaumicEnergistics.THAUMIUM_GEARBOX.get()))
            .setPages(text(page("THAUMGBOX", 1)), recipe(ResearchPage.PageType.CRUCIBLE_CRAFTING, "ThEThaumiumGearBox"))
            .setParents(IRONGEARBOX, "thaumicenergistics.Pseudo.COREUSE").setParentsHidden("COREUSE").setSecondary().registerResearchItem();

        // FeatureGolemBackpack
        pseudo("COREGATHER", 1, -6);
        var backpack = shaped("ThEGolemWifiBackpack", GOLEMWIFI, stack(ThaumicEnergistics.GOLEM_WIFI_BACKPACK.get()), new AspectList().add(Aspect.AIR, 6).add(Aspect.FIRE, 4).add(Aspect.ORDER, 3),
            new String[]{"TIT", "NWN", "FCF"}, Map.of('T', of("tc4modern:thaumium_ingot"), 'I', of("ae2:cable_interface"), 'N', of("tc4modern:nitor"), 'W', of("ae2:wireless_receiver"), 'F', tag("c:gems/fluix"), 'C', of("ae2:charger")));
        String golemPage = "thaumicenergistics.research_page.TEGOLEMWIFIBACKPACK.";
        new ResearchItem(GOLEMWIFI, TAB, new AspectList().add(Aspect.ENERGY, 5).add(Aspect.AURA, 5).add(Aspect.MIND, 3).add(Aspect.ARMOR, 3).add(Aspect.MOTION, 1).add(Aspect.EXCHANGE, 1), 0, -6, 3, stack(ThaumicEnergistics.GOLEM_WIFI_BACKPACK.get()))
            .setPages(text(page("GOLEMWIFIBACKPACK", 1)), arcane(backpack), text(page("GOLEMWIFIBACKPACK", 2)), text(page("GOLEMWIFIBACKPACK", 3)), text(golemPage + "COREGATHER"),
                new ResearchPage("COREFILL", golemPage + "COREFILL"), new ResearchPage("CORELIQUID", golemPage + "CORELIQUID"), new ResearchPage("COREALCHEMY", golemPage + "COREALCHEMY"),
                new ResearchPage("UPGRADEORDER", golemPage + "UPGRADEORDER"), new ResearchPage("ADVANCEDGOLEM", golemPage + "ADVANCEDGOLEM"))
            .setParents(CORES, "COREGATHER", "thaumicenergistics.Pseudo.COREGATHER").setConcealed().registerResearchItem();

        // FeatureEssentiaIOBuses
        pseudo("TUBEFILTER", -3, 0);
        var ironIngot = tag("c:ingots/iron");var filterTube = of("tc4modern:essentia_tube_filter");var wardedJar = of("tc4modern:warded_jar");
        var wardedGlass = dev.eugheist.tc4modern.WarpConfig.wardedStone() ? of("tc4modern:warded_glass") : tag("c:glass_blocks/colorless");
        var diffusionCore = of(ThaumicEnergistics.DIFFUSION_CORE.get());var coalescenceCore = of(ThaumicEnergistics.COALESCENCE_CORE.get());
        var ioCost = new AspectList().add(Aspect.FIRE, 2).add(Aspect.EARTH, 2).add(Aspect.WATER, 1);
        var importBus = shaped("ThEEssentiaImportBus", IO, stack(ThaumicEnergistics.ESSENTIA_IMPORT_BUS.get()), ioCost, new String[]{"JDJ", "IFI"}, Map.of('J', wardedJar, 'D', diffusionCore, 'I', ironIngot, 'F', filterTube));
        var exportBus = shaped("ThEEssentiaExportBus", IO, stack(ThaumicEnergistics.ESSENTIA_EXPORT_BUS.get()), ioCost.copy(), new String[]{"JCJ", "IFI"}, Map.of('J', wardedJar, 'C', coalescenceCore, 'I', ironIngot, 'F', filterTube));
        var storageBus = shaped("ThEEssentiaStorageBus", IO, stack(ThaumicEnergistics.ESSENTIA_STORAGE_BUS.get()), new AspectList().add(Aspect.FIRE, 3).add(Aspect.EARTH, 3).add(Aspect.WATER, 1),
            new String[]{"DFC", "IWI"}, Map.of('D', diffusionCore, 'C', coalescenceCore, 'I', ironIngot, 'F', filterTube, 'W', wardedGlass));
        new ResearchItem(IO, TAB, new AspectList().add(Aspect.MECHANISM, 5).add(Aspect.METAL, 3).add(Aspect.CRYSTAL, 3).add(Aspect.AIR, 3), -2, -2, 2, stack(ThaumicEnergistics.ESSENTIA_EXPORT_BUS.get()))
            .setPages(text(page("IO", 1)), text(page("IO", 2)), arcane(importBus), arcane(exportBus), arcane(storageBus))
            .setParents(CORES, "thaumicenergistics.Pseudo.TUBEFILTER").setParentsHidden("TUBEFILTER").setConcealed().registerResearchItem();
    }
}
