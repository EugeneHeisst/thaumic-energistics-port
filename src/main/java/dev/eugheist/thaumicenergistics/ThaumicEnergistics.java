package dev.eugheist.thaumicenergistics;

import java.util.EnumMap;
import java.util.Map;
import appeng.api.parts.PartModels;
import appeng.api.storage.StorageCells;
import appeng.items.parts.PartItem;
import appeng.items.parts.PartModelsHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import dev.eugheist.thaumicenergistics.item.EssentiaCellItem;
import dev.eugheist.thaumicenergistics.me.EssentiaCellHandler;
import dev.eugheist.thaumicenergistics.me.EssentiaKey;
import dev.eugheist.thaumicenergistics.me.EssentiaKeyType;
import dev.eugheist.thaumicenergistics.me.EssentiaStorageType;
import dev.eugheist.thaumicenergistics.part.EssentiaTerminalPart;

/** Thaumic Energistics 1.1.3.0 (Nividica) ported to Minecraft 1.21.1 / NeoForge on top of tc4modern and AE2 19.2. */
@Mod(ThaumicEnergistics.ID)
public final class ThaumicEnergistics {
    public static final String ID = "thaumicenergistics";
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ID);
    public static final net.neoforged.neoforge.registries.DeferredBlock<dev.eugheist.thaumicenergistics.block.CellWorkbenchBlock> CELL_WORKBENCH = BLOCKS.register("essentia_cell_workbench",
        () -> new dev.eugheist.thaumicenergistics.block.CellWorkbenchBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().strength(1f).sound(net.minecraft.world.level.block.SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<dev.eugheist.thaumicenergistics.block.CellWorkbenchEntity>> CELL_WORKBENCH_ENTITY =
        BLOCK_ENTITIES.register("essentia_cell_workbench", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(dev.eugheist.thaumicenergistics.block.CellWorkbenchEntity::new, CELL_WORKBENCH.get()).build(null));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<dev.eugheist.thaumicenergistics.block.CellWorkbenchMenu>> CELL_WORKBENCH_MENU =
        MENUS.register("essentia_cell_workbench", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(dev.eugheist.thaumicenergistics.block.CellWorkbenchMenu::new));

    public static final Map<EssentiaStorageType, DeferredItem<EssentiaCellItem>> CELLS = new EnumMap<>(EssentiaStorageType.class);
    public static final Map<EssentiaStorageType, DeferredItem<Item>> COMPONENTS = new EnumMap<>(EssentiaStorageType.class);
    static {
        for (var type : EssentiaStorageType.values()) {
            CELLS.put(type, ITEMS.register("essentia_cell_" + type.suffix, () -> new EssentiaCellItem(type)));
            if (!type.creative()) COMPONENTS.put(type, ITEMS.register("essentia_storage_component_" + type.suffix, () -> new Item(new Item.Properties().rarity(type.rarity))));
        }
    }
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CELL_WORKBENCH_ITEM = ITEMS.registerSimpleBlockItem(CELL_WORKBENCH);
    public static final DeferredItem<Item> CELL_CASING = ITEMS.register("essentia_cell_casing", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> COALESCENCE_CORE = ITEMS.register("coalescence_core", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> DIFFUSION_CORE = ITEMS.register("diffusion_core", () -> new Item(new Item.Properties()));
    public static final DeferredItem<PartItem<EssentiaTerminalPart>> ESSENTIA_TERMINAL = ITEMS.register("essentia_terminal", () -> {
        PartModels.registerModels(PartModelsHelper.createModels(EssentiaTerminalPart.class));
        return new PartItem<>(new Item.Properties(), EssentiaTerminalPart.class, EssentiaTerminalPart::new);
    });

    public static final DeferredItem<PartItem<dev.eugheist.thaumicenergistics.part.EssentiaImportBusPart>> ESSENTIA_IMPORT_BUS = part("essentia_import_bus", dev.eugheist.thaumicenergistics.part.EssentiaImportBusPart.class, dev.eugheist.thaumicenergistics.part.EssentiaImportBusPart::new);
    public static final DeferredItem<PartItem<dev.eugheist.thaumicenergistics.part.EssentiaExportBusPart>> ESSENTIA_EXPORT_BUS = part("essentia_export_bus", dev.eugheist.thaumicenergistics.part.EssentiaExportBusPart.class, dev.eugheist.thaumicenergistics.part.EssentiaExportBusPart::new);
    public static final DeferredItem<PartItem<dev.eugheist.thaumicenergistics.part.EssentiaStorageBusPart>> ESSENTIA_STORAGE_BUS = part("essentia_storage_bus", dev.eugheist.thaumicenergistics.part.EssentiaStorageBusPart.class, dev.eugheist.thaumicenergistics.part.EssentiaStorageBusPart::new);
    public static final DeferredItem<PartItem<dev.eugheist.thaumicenergistics.part.EssentiaLevelEmitterPart>> ESSENTIA_LEVEL_EMITTER = part("essentia_level_emitter", dev.eugheist.thaumicenergistics.part.EssentiaLevelEmitterPart.class, dev.eugheist.thaumicenergistics.part.EssentiaLevelEmitterPart::new);
    public static final DeferredItem<PartItem<dev.eugheist.thaumicenergistics.part.EssentiaMonitors.Storage>> ESSENTIA_STORAGE_MONITOR = part("essentia_storage_monitor", dev.eugheist.thaumicenergistics.part.EssentiaMonitors.Storage.class, dev.eugheist.thaumicenergistics.part.EssentiaMonitors.Storage::new);
    public static final DeferredItem<PartItem<dev.eugheist.thaumicenergistics.part.EssentiaMonitors.Conversion>> ESSENTIA_CONVERSION_MONITOR = part("essentia_conversion_monitor", dev.eugheist.thaumicenergistics.part.EssentiaMonitors.Conversion.class, dev.eugheist.thaumicenergistics.part.EssentiaMonitors.Conversion::new);
    static <T extends appeng.api.parts.IPart> DeferredItem<PartItem<T>> part(String name, Class<T> type, java.util.function.Function<appeng.api.parts.IPartItem<T>, T> factory) {
        return ITEMS.register(name, () -> { PartModels.registerModels(PartModelsHelper.createModels(type)); return new PartItem<>(new Item.Properties(), type, factory); });
    }
    public static final DeferredItem<PartItem<dev.eugheist.thaumicenergistics.part.ArcaneCraftingTerminalPart>> ARCANE_CRAFTING_TERMINAL = part("arcane_crafting_terminal", dev.eugheist.thaumicenergistics.part.ArcaneCraftingTerminalPart.class, dev.eugheist.thaumicenergistics.part.ArcaneCraftingTerminalPart::new);
    public static final DeferredItem<PartItem<dev.eugheist.thaumicenergistics.part.VisInterfacePart>> VIS_INTERFACE = part("vis_interface", dev.eugheist.thaumicenergistics.part.VisInterfacePart.class, dev.eugheist.thaumicenergistics.part.VisInterfacePart::new);
    public static final net.neoforged.neoforge.registries.DeferredBlock<dev.eugheist.thaumicenergistics.block.ProviderBlock<dev.eugheist.thaumicenergistics.block.EssentiaProviderEntity>> ESSENTIA_PROVIDER = BLOCKS.register("essentia_provider", () -> new dev.eugheist.thaumicenergistics.block.ProviderBlock<>());
    public static final net.neoforged.neoforge.registries.DeferredBlock<dev.eugheist.thaumicenergistics.block.ProviderBlock<dev.eugheist.thaumicenergistics.block.InfusionProviderEntity>> INFUSION_PROVIDER = BLOCKS.register("infusion_provider", () -> new dev.eugheist.thaumicenergistics.block.ProviderBlock<>());
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ESSENTIA_PROVIDER_ITEM = ITEMS.registerSimpleBlockItem(ESSENTIA_PROVIDER);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> INFUSION_PROVIDER_ITEM = ITEMS.registerSimpleBlockItem(INFUSION_PROVIDER);
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<dev.eugheist.thaumicenergistics.block.EssentiaProviderEntity>> ESSENTIA_PROVIDER_ENTITY =
        BLOCK_ENTITIES.register("essentia_provider", () -> provider(ESSENTIA_PROVIDER.get(), ESSENTIA_PROVIDER_ITEM.get(), dev.eugheist.thaumicenergistics.block.EssentiaProviderEntity.class, dev.eugheist.thaumicenergistics.block.EssentiaProviderEntity::new));
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<dev.eugheist.thaumicenergistics.block.InfusionProviderEntity>> INFUSION_PROVIDER_ENTITY =
        BLOCK_ENTITIES.register("infusion_provider", () -> provider(INFUSION_PROVIDER.get(), INFUSION_PROVIDER_ITEM.get(), dev.eugheist.thaumicenergistics.block.InfusionProviderEntity.class, dev.eugheist.thaumicenergistics.block.InfusionProviderEntity::new));
    /** The AE2 block entity wiring ArsEng uses: type, server ticker, item for the network visual. */
    static <T extends dev.eugheist.thaumicenergistics.block.ProviderEntity> net.minecraft.world.level.block.entity.BlockEntityType<T> provider(dev.eugheist.thaumicenergistics.block.ProviderBlock<T> block, Item item, Class<T> type, net.minecraft.world.level.block.entity.BlockEntityType.BlockEntitySupplier<T> factory) {
        var beType = net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(factory, block).build(null);
        block.setBlockEntity(type, beType, null, (level, pos, state, be) -> be.serverTick());
        appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(beType, item);
        return beType;
    }
    public static final DeferredItem<Item> KNOWLEDGE_CORE = ITEMS.register("knowledge_core", () -> new Item(new Item.Properties().stacksTo(1)) {
        @Override public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, java.util.List<Component> lines, net.minecraft.world.item.TooltipFlag flag) {
            var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            int n = data == null ? 0 : data.copyTag().getList("Patterns", 10).size();
            if (n > 0) lines.add(Component.literal(n + " / " + dev.eugheist.thaumicenergistics.craft.KnowledgeCore.MAX).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    });
    public static final DeferredItem<dev.eugheist.thaumicenergistics.item.WrenchFocusItem> FOCUS_AEWRENCH = ITEMS.register("focus_aewrench", dev.eugheist.thaumicenergistics.item.WrenchFocusItem::new);
    /** Definition item of arcane patterns for AE2 crafting jobs (never in the creative tab). */
    public static final DeferredItem<Item> ARCANE_PATTERN = ITEMS.register("arcane_pattern", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final net.neoforged.neoforge.registries.DeferredBlock<dev.eugheist.thaumicenergistics.block.MachineBlock<dev.eugheist.thaumicenergistics.block.KnowledgeInscriberEntity>> KNOWLEDGE_INSCRIBER = BLOCKS.register("knowledge_inscriber",
        () -> new dev.eugheist.thaumicenergistics.block.MachineBlock<>(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().strength(1f).sound(net.minecraft.world.level.block.SoundType.METAL), dev.eugheist.thaumicenergistics.block.KnowledgeInscriberEntity::new, "block.thaumicenergistics.knowledge_inscriber"));
    public static final net.neoforged.neoforge.registries.DeferredBlock<dev.eugheist.thaumicenergistics.block.FacingMachineBlock<dev.eugheist.thaumicenergistics.block.DistillationEncoderEntity>> DISTILLATION_ENCODER = BLOCKS.register("distillation_encoder",
        () -> new dev.eugheist.thaumicenergistics.block.FacingMachineBlock<>(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().strength(1f).sound(net.minecraft.world.level.block.SoundType.METAL), dev.eugheist.thaumicenergistics.block.DistillationEncoderEntity::new, "block.thaumicenergistics.distillation_encoder"));
    public static final net.neoforged.neoforge.registries.DeferredBlock<dev.eugheist.thaumicenergistics.block.ArcaneAssemblerBlock> ARCANE_ASSEMBLER = BLOCKS.register("arcane_assembler", dev.eugheist.thaumicenergistics.block.ArcaneAssemblerBlock::new);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> KNOWLEDGE_INSCRIBER_ITEM = ITEMS.registerSimpleBlockItem(KNOWLEDGE_INSCRIBER);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> DISTILLATION_ENCODER_ITEM = ITEMS.registerSimpleBlockItem(DISTILLATION_ENCODER);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ARCANE_ASSEMBLER_ITEM = ITEMS.register("arcane_assembler", () -> new net.minecraft.world.item.BlockItem(ARCANE_ASSEMBLER.get(), new Item.Properties()) {
        @Override public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, java.util.List<Component> lines, net.minecraft.world.item.TooltipFlag flag) {
            var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (data == null || !data.copyTag().getBoolean("has_vis_tag") || data.copyTag().getCompound("stored_vis").isEmpty() && !data.copyTag().contains("stored_vis")) lines.add(Component.translatable("thaumicenergistics.tooltip.arcane.assembler.hasVis").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
        }
    });
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<dev.eugheist.thaumicenergistics.block.KnowledgeInscriberEntity>> KNOWLEDGE_INSCRIBER_ENTITY =
        BLOCK_ENTITIES.register("knowledge_inscriber", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(dev.eugheist.thaumicenergistics.block.KnowledgeInscriberEntity::new, KNOWLEDGE_INSCRIBER.get()).build(null));
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<dev.eugheist.thaumicenergistics.block.DistillationEncoderEntity>> DISTILLATION_ENCODER_ENTITY =
        BLOCK_ENTITIES.register("distillation_encoder", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(dev.eugheist.thaumicenergistics.block.DistillationEncoderEntity::new, DISTILLATION_ENCODER.get()).build(null));
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<dev.eugheist.thaumicenergistics.block.ArcaneAssemblerEntity>> ARCANE_ASSEMBLER_ENTITY =
        BLOCK_ENTITIES.register("arcane_assembler", () -> {
            var type = net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(dev.eugheist.thaumicenergistics.block.ArcaneAssemblerEntity::new, ARCANE_ASSEMBLER.get()).build(null);
            ARCANE_ASSEMBLER.get().setBlockEntity(dev.eugheist.thaumicenergistics.block.ArcaneAssemblerEntity.class, type, null, (level, pos, state, be) -> be.serverTick());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(type, ARCANE_ASSEMBLER_ITEM.get());
            return type;
        });
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<dev.eugheist.thaumicenergistics.menu.KnowledgeInscriberMenu>> KNOWLEDGE_INSCRIBER_MENU = MENUS.register("knowledge_inscriber", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(dev.eugheist.thaumicenergistics.menu.KnowledgeInscriberMenu::new));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<dev.eugheist.thaumicenergistics.menu.DistillationEncoderMenu>> DISTILLATION_ENCODER_MENU = MENUS.register("distillation_encoder", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(dev.eugheist.thaumicenergistics.menu.DistillationEncoderMenu::new));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<dev.eugheist.thaumicenergistics.menu.ArcaneAssemblerMenu>> ARCANE_ASSEMBLER_MENU = MENUS.register("arcane_assembler", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(dev.eugheist.thaumicenergistics.menu.ArcaneAssemblerMenu::new));
    public static final net.neoforged.neoforge.registries.DeferredBlock<dev.eugheist.thaumicenergistics.block.VibrationChamberBlock> VIBRATION_CHAMBER = BLOCKS.register("essentia_vibration_chamber", dev.eugheist.thaumicenergistics.block.VibrationChamberBlock::new);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> VIBRATION_CHAMBER_ITEM = ITEMS.registerSimpleBlockItem(VIBRATION_CHAMBER);
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<dev.eugheist.thaumicenergistics.block.VibrationChamberEntity>> VIBRATION_CHAMBER_ENTITY =
        BLOCK_ENTITIES.register("essentia_vibration_chamber", () -> {
            var type = net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(dev.eugheist.thaumicenergistics.block.VibrationChamberEntity::new, VIBRATION_CHAMBER.get()).build(null);
            VIBRATION_CHAMBER.get().setBlockEntity(dev.eugheist.thaumicenergistics.block.VibrationChamberEntity.class, type, null, null);
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(type, VIBRATION_CHAMBER_ITEM.get());
            return type;
        });
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<dev.eugheist.thaumicenergistics.menu.VibrationChamberMenu>> VIBRATION_CHAMBER_MENU = MENUS.register("essentia_vibration_chamber", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(dev.eugheist.thaumicenergistics.menu.VibrationChamberMenu::new));
    public static final net.neoforged.neoforge.registries.DeferredBlock<dev.eugheist.thaumicenergistics.block.GearBoxBlock> IRON_GEARBOX = BLOCKS.register("gear_box", () -> new dev.eugheist.thaumicenergistics.block.GearBoxBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().strength(2f).sound(net.minecraft.world.level.block.SoundType.METAL).requiresCorrectToolForDrops().noOcclusion(), false));
    public static final net.neoforged.neoforge.registries.DeferredBlock<dev.eugheist.thaumicenergistics.block.GearBoxBlock> THAUMIUM_GEARBOX = BLOCKS.register("golem_gear_box", () -> new dev.eugheist.thaumicenergistics.block.GearBoxBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().strength(2f).sound(net.minecraft.world.level.block.SoundType.METAL).requiresCorrectToolForDrops().noOcclusion(), true));
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<dev.eugheist.thaumicenergistics.block.GearBoxEntity>> GEARBOX_ENTITY =
        BLOCK_ENTITIES.register("gear_box", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(dev.eugheist.thaumicenergistics.block.GearBoxEntity::new, IRON_GEARBOX.get(), THAUMIUM_GEARBOX.get()).build(null));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> IRON_GEARBOX_ITEM = ITEMS.registerSimpleBlockItem(IRON_GEARBOX);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> THAUMIUM_GEARBOX_ITEM = ITEMS.registerSimpleBlockItem(THAUMIUM_GEARBOX);
    public static final DeferredItem<Item> IRON_GEAR = ITEMS.register("iron_gear", () -> new Item(new Item.Properties()));
    public static final DeferredItem<dev.eugheist.thaumicenergistics.item.GolemBackpackItem> GOLEM_WIFI_BACKPACK = ITEMS.register("golem_wifi_backpack", () -> new dev.eugheist.thaumicenergistics.item.GolemBackpackItem(new Item.Properties()));
    public static final DeferredItem<dev.eugheist.thaumicenergistics.item.WirelessEssentiaTerminalItem> WIRELESS_ESSENTIA_TERMINAL = ITEMS.register("wireless_essentia_terminal", () -> new dev.eugheist.thaumicenergistics.item.WirelessEssentiaTerminalItem(new Item.Properties()));
    public static final DeferredItem<dev.eugheist.thaumicenergistics.item.WirelessArcaneTerminalItem> WIRELESS_ARCANE_TERMINAL = ITEMS.register("wireless_arcane_crafting_terminal", () -> new dev.eugheist.thaumicenergistics.item.WirelessArcaneTerminalItem(new Item.Properties()));
    public static final DeferredRegister.DataComponents COMPONENTS_REG = DeferredRegister.createDataComponents(net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE, ID);
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<dev.eugheist.thaumicenergistics.part.VisInterfacePart.VisSource>> VIS_SOURCE =
        COMPONENTS_REG.registerComponentType("vis_source", b -> b.persistent(dev.eugheist.thaumicenergistics.part.VisInterfacePart.VisSource.CODEC).networkSynchronized(dev.eugheist.thaumicenergistics.part.VisInterfacePart.VisSource.STREAM));
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.ItemContainerContents>> ARCANE_WAND_INV =
        COMPONENTS_REG.registerComponentType("arcane_wand_inv", b -> b.persistent(net.minecraft.world.item.component.ItemContainerContents.CODEC).networkSynchronized(net.minecraft.world.item.component.ItemContainerContents.STREAM_CODEC));
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<net.minecraft.world.item.component.ItemContainerContents>> ARCANE_ARMOR_INV =
        COMPONENTS_REG.registerComponentType("arcane_armor_inv", b -> b.persistent(net.minecraft.world.item.component.ItemContainerContents.CODEC).networkSynchronized(net.minecraft.world.item.component.ItemContainerContents.STREAM_CODEC));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<dev.eugheist.thaumicenergistics.menu.EssentiaIOBusMenu>> IO_BUS_MENU =
        MENUS.register("essentia_io_bus", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(dev.eugheist.thaumicenergistics.menu.EssentiaIOBusMenu::new));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<dev.eugheist.thaumicenergistics.menu.EssentiaStorageBusMenu>> STORAGE_BUS_MENU =
        MENUS.register("essentia_storage_bus", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(dev.eugheist.thaumicenergistics.menu.EssentiaStorageBusMenu::new));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<dev.eugheist.thaumicenergistics.menu.EssentiaLevelEmitterMenu>> LEVEL_EMITTER_MENU =
        MENUS.register("essentia_level_emitter", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(dev.eugheist.thaumicenergistics.menu.EssentiaLevelEmitterMenu::new));

    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<dev.eugheist.thaumicenergistics.menu.EssentiaTermMenu>> ESSENTIA_TERMINAL_MENU =
        MENUS.register("essentia_terminal", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(dev.eugheist.thaumicenergistics.menu.EssentiaTermMenu::new));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.ThaumicEnergistics"))
        .icon(() -> new ItemStack(CELLS.get(EssentiaStorageType.TYPE_64K).get()))
        .displayItems((params, out) -> ITEMS.getEntries().forEach(entry -> { if (entry.get() != ARCANE_PATTERN.get()) out.accept(entry.get()); }))
        .build());

    public ThaumicEnergistics(IEventBus bus) {
        ITEMS.register(bus);
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        COMPONENTS_REG.register(bus);
        TABS.register(bus);
        bus.addListener(EssentiaKeyType::register);
        StorageCells.addCellHandler(EssentiaCellHandler.INSTANCE);
        appeng.api.behaviors.ContainerItemStrategies.register(EssentiaKeyType.TYPE, EssentiaKey.class, dev.eugheist.thaumicenergistics.me.EssentiaContainerItemStrategy.INSTANCE);
        dev.eugheist.thaumicenergistics.menu.ArcaneTermMenu.init();
        appeng.api.crafting.PatternDetailsHelper.registerDecoder(dev.eugheist.thaumicenergistics.craft.ArcanePatternDetails.DECODER);
        bus.addListener(this::setup);
        dev.eugheist.tc4modern.api.TC4Addons.registerGolemHook(dev.eugheist.thaumicenergistics.golem.WifiGolems.INSTANCE);
        // EssentiaTileContainerHelper: the E.V.C. takes up to 64 ignis/potentia from export and storage buses.
        dev.eugheist.tc4modern.api.AspectContainers.register(tile -> tile instanceof dev.eugheist.thaumicenergistics.block.VibrationChamberEntity evc ? new dev.eugheist.tc4modern.api.AspectContainers.Container() {
            public thaumcraft.api.aspects.AspectList getAspects() { var l = new thaumcraft.api.aspects.AspectList(); if (evc.stored != null && evc.storedAmount > 0) l.add(evc.stored, evc.storedAmount); return l; }
            public int capacity() { return dev.eugheist.thaumicenergistics.block.VibrationChamberEntity.MAX; }
            public boolean canExtract() { return false; }
            public boolean canInject() { return true; }
            public boolean accepts(thaumcraft.api.aspects.Aspect a) { return evc.insert(a, 1, true) == 1; }
            public boolean take(thaumcraft.api.aspects.Aspect a, int n) { return false; }
            public int add(thaumcraft.api.aspects.Aspect a, int n) { return n - evc.insert(a, n, false); }
        } : null);
        bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> {
            event.registerBlockEntity(appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST, ESSENTIA_PROVIDER_ENTITY.get(), (be, ctx) -> be);
            event.registerBlockEntity(appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST, INFUSION_PROVIDER_ENTITY.get(), (be, ctx) -> be);
            event.registerBlockEntity(appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST, ARCANE_ASSEMBLER_ENTITY.get(), (be, ctx) -> be);
            event.registerBlockEntity(appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST, VIBRATION_CHAMBER_ENTITY.get(), (be, ctx) -> be);
        });
        bus.addListener((net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) -> {
            var registrar = event.registrar("1");
            registrar.playToServer(dev.eugheist.thaumicenergistics.menu.ThresholdPayload.TYPE, dev.eugheist.thaumicenergistics.menu.ThresholdPayload.CODEC, dev.eugheist.thaumicenergistics.menu.ThresholdPayload::handle);
            registrar.playToServer(dev.eugheist.thaumicenergistics.menu.EssentiaTermMenu.ActionPayload.TYPE, dev.eugheist.thaumicenergistics.menu.EssentiaTermMenu.ActionPayload.CODEC, dev.eugheist.thaumicenergistics.menu.EssentiaTermMenu.ActionPayload::handle);
            registrar.playToClient(dev.eugheist.thaumicenergistics.menu.EssentiaTermMenu.ListPayload.TYPE, dev.eugheist.thaumicenergistics.menu.EssentiaTermMenu.ListPayload.CODEC, dev.eugheist.thaumicenergistics.menu.EssentiaTermMenu.ListPayload::handle);
        });
        if (FMLEnvironment.dist == Dist.CLIENT) { bus.addListener(Client::clientSetup); bus.addListener(Client::screens); bus.addListener(Client::colors); bus.addListener(Client::blockColors); bus.addListener(Client::layers); bus.addListener(Client::renderers); bus.addListener(Client::itemExtensions); }
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ThEResearch.register();
            AEAspects.register();
            // AEPartsEnum upgrades: IO buses take 2 capacity, 1 redstone and 2 speed cards; the storage bus one inverter.
            String io = "thaumicenergistics.group.essentia.transport";
            for (var bus : java.util.List.of(ESSENTIA_IMPORT_BUS.get(), ESSENTIA_EXPORT_BUS.get())) {
                appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.CAPACITY_CARD, bus, 2, io);
                appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.REDSTONE_CARD, bus, 1, io);
                appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.SPEED_CARD, bus, 2, io);
            }
            appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.INVERTER_CARD, ESSENTIA_STORAGE_BUS.get(), 1);
            appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.SPEED_CARD, ARCANE_ASSEMBLER_ITEM.get(), 4);
            appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.ENERGY_CARD, WIRELESS_ESSENTIA_TERMINAL.get(), 2);
            appeng.api.features.GridLinkables.register(WIRELESS_ESSENTIA_TERMINAL.get(), appeng.items.tools.powered.WirelessTerminalItem.LINKABLE_HANDLER);
            appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.ENERGY_CARD, WIRELESS_ARCANE_TERMINAL.get(), 2);
            appeng.api.features.GridLinkables.register(WIRELESS_ARCANE_TERMINAL.get(), appeng.items.tools.powered.WirelessTerminalItem.LINKABLE_HANDLER);
            // AE2 "open wireless terminal" key opens these too (first one found in the inventory or curios).
            appeng.hotkeys.HotkeyActions.register(WIRELESS_ARCANE_TERMINAL.get(), (player, locator) -> WIRELESS_ARCANE_TERMINAL.get().openFromInventory(player, locator), "wireless_terminal");
            appeng.hotkeys.HotkeyActions.register(WIRELESS_ESSENTIA_TERMINAL.get(), (player, locator) -> WIRELESS_ESSENTIA_TERMINAL.get().openFromInventory(player, locator), "wireless_terminal");
            // Terminals worn in Baubles slots: AE2 menus find them there, the wireless terminal key opens them.
            appeng.menu.locator.MenuLocators.register(dev.eugheist.thaumicenergistics.item.BaubleLocator.class, dev.eugheist.thaumicenergistics.item.BaubleLocator::writeToPacket, dev.eugheist.thaumicenergistics.item.BaubleLocator::readFromPacket);
            appeng.hotkeys.HotkeyActions.register(player -> {
                for (var item : java.util.List.<appeng.items.tools.powered.WirelessTerminalItem>of(WIRELESS_ARCANE_TERMINAL.get(), WIRELESS_ESSENTIA_TERMINAL.get())) {
                    var at = dev.eugheist.thaumicenergistics.item.BaubleLocator.find(player, item.getClass());
                    if (at != null) return item.openFromInventory(player, at);
                }
                return false;
            }, "wireless_terminal");
            // ae2wtlib's magnet card fits the wireless arcane terminal; its magnet key also switches our magnet.
            var magnet = dev.eugheist.thaumicenergistics.item.ArcaneMagnet.card();
            if (magnet != null) {
                appeng.api.upgrades.Upgrades.add(magnet, WIRELESS_ARCANE_TERMINAL.get(), 1);
                appeng.hotkeys.HotkeyActions.register(dev.eugheist.thaumicenergistics.item.ArcaneMagnet::cycle, "ae2wtlib_magnet");
            }
            appeng.api.features.GridLinkables.register(GOLEM_WIFI_BACKPACK.get(), dev.eugheist.thaumicenergistics.item.GolemBackpackItem.LINKABLE_HANDLER);
        });
    }


    /** Keyboard.isKeyDown(LSHIFT/RSHIFT) of the original tooltips; false on a dedicated server. */
    public static boolean clientShiftDown() { return FMLEnvironment.dist == Dist.CLIENT && Client.shift(); }
    static final class Client {
        static void clientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> appeng.api.client.AEKeyRendering.register(EssentiaKeyType.TYPE, EssentiaKey.class, dev.eugheist.thaumicenergistics.client.EssentiaKeyRenderer.INSTANCE));
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        static void layers(net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers event) {
            if (event.getRenderer(dev.eugheist.tc4modern.Golemancy.ENTITY.get()) instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer renderer)
                renderer.addLayer(new dev.eugheist.thaumicenergistics.client.GolemBackpackLayer(renderer));
        }
        static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(GEARBOX_ENTITY.get(), c -> new dev.eugheist.thaumicenergistics.client.LegacyModels.GearboxRenderer());
            event.registerBlockEntityRenderer(ARCANE_ASSEMBLER_ENTITY.get(), c -> new dev.eugheist.thaumicenergistics.client.LegacyModels.AssemblerRenderer());
        }
        /** TileAsItemRenderer: the items draw the same models. */
        static void itemExtensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
            event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
                @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() { return dev.eugheist.thaumicenergistics.client.LegacyModels.ItemRenderer.INSTANCE; }
            }, IRON_GEARBOX_ITEM.get(), THAUMIUM_GEARBOX_ITEM.get(), ARCANE_ASSEMBLER_ITEM.get());
        }
        static boolean shift() { return net.minecraft.client.gui.screens.Screen.hasShiftDown(); }
        static void screens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
            event.register(CELL_WORKBENCH_MENU.get(), dev.eugheist.thaumicenergistics.client.CellWorkbenchScreen::new);
            event.register(ESSENTIA_TERMINAL_MENU.get(), dev.eugheist.thaumicenergistics.client.EssentiaTermScreen::new);
            event.register(IO_BUS_MENU.get(), dev.eugheist.thaumicenergistics.client.EssentiaIOBusScreen::new);
            event.register(STORAGE_BUS_MENU.get(), dev.eugheist.thaumicenergistics.client.EssentiaStorageBusScreen::new);
            event.register(LEVEL_EMITTER_MENU.get(), dev.eugheist.thaumicenergistics.client.EssentiaLevelEmitterScreen::new);
            event.register(KNOWLEDGE_INSCRIBER_MENU.get(), dev.eugheist.thaumicenergistics.client.KnowledgeInscriberScreen::new);
            event.register(DISTILLATION_ENCODER_MENU.get(), dev.eugheist.thaumicenergistics.client.DistillationEncoderScreen::new);
            event.register(ARCANE_ASSEMBLER_MENU.get(), dev.eugheist.thaumicenergistics.client.ArcaneAssemblerScreen::new);
            event.register(VIBRATION_CHAMBER_MENU.get(), dev.eugheist.thaumicenergistics.client.VibrationChamberScreen::new);
            appeng.init.client.InitScreens.register(event, dev.eugheist.thaumicenergistics.menu.ArcaneTermMenu.TYPE, dev.eugheist.thaumicenergistics.client.ArcaneTermScreen::new, "/screens/thaumicenergistics/arcane_crafting_terminal.json");
            appeng.init.client.InitScreens.register(event, dev.eugheist.thaumicenergistics.menu.ArcaneTermMenu.WIRELESS_TYPE, dev.eugheist.thaumicenergistics.client.ArcaneTermScreen::new, "/screens/thaumicenergistics/arcane_crafting_terminal.json");
        }
        /** RenderBlockProviderBase: the overlay pass in the grid colour's medium variant while active, black otherwise. */
        static void blockColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
            event.register((state, level, pos, tint) -> {
                if (tint != 1 || level == null || pos == null) return -1;
                return level.getBlockEntity(pos) instanceof dev.eugheist.thaumicenergistics.block.ProviderEntity p && p.isActive() ? 0xFF000000 | p.getColor().mediumVariant : 0xFF000000;
            }, ESSENTIA_PROVIDER.get(), INFUSION_PROVIDER.get());
        }
        /** ThEPartBase.INVENTORY_OVERLAY_COLOR: part overlays in the inventory use the black variant of transparent. */
        static void colors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
            event.register((stack, tint) -> tint == 1 ? 0xFF000000 | appeng.api.util.AEColor.TRANSPARENT.blackVariant : -1, ESSENTIA_IMPORT_BUS.get(), ESSENTIA_EXPORT_BUS.get(), ESSENTIA_STORAGE_BUS.get());
        }
    }
}
