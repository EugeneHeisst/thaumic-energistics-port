package dev.eugheist.thaumicenergistics;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.common.MEStorageMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.research.ResearchCategories;
import dev.eugheist.tc4modern.ArcaneRecipes;
import dev.eugheist.tc4modern.RecipeAspects;
import dev.eugheist.thaumicenergistics.me.EssentiaCellInventory;
import dev.eugheist.thaumicenergistics.me.EssentiaKey;
import dev.eugheist.thaumicenergistics.me.EssentiaKeyType;
import dev.eugheist.thaumicenergistics.me.EssentiaStorageType;
import dev.eugheist.thaumicenergistics.part.EssentiaTerminalPart;

/** Hidden native audit (-Dthaumicenergistics.audit=true, isolated TEInspection world): stage 1 against original behaviour. */
@EventBusSubscriber(modid = ThaumicEnergistics.ID, value = Dist.CLIENT)
public final class ThEAudit {
    static final BlockPos BASE = new BlockPos(600, 121, -90);
    static int ticks; static volatile boolean built; static final List<String> aspectTable = new ArrayList<>();
    static void check(boolean ok, String what) { if (!ok) throw new IllegalStateException("THE_AUDIT FAIL " + what); System.out.println("THE_AUDIT PASS " + what); }
    static ItemStack cell(EssentiaStorageType type) { return new ItemStack(ThaumicEnergistics.CELLS.get(type).get()); }
    static EssentiaKey key(Aspect a) { return EssentiaKey.of(a); }
    static void shot(Minecraft mc, String name) {
        try { var dir = Path.of("audit"); Files.createDirectories(dir); try (var img = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) { img.writeToFile(dir.resolve(name)); } }
        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    static void cells() {
        check(AEKeyTypes.get(ThaumicEnergistics.id("essentia")) == EssentiaKeyType.TYPE, "essentia key type registered with AE2");
        var src = IActionSource.empty();
        // 1k: 1024 bytes, two essentia per byte, no type overhead.
        var one = cell(EssentiaStorageType.TYPE_1K);var inv = (EssentiaCellInventory) StorageCells.getCellInventory(one, null);
        check(inv != null && inv.getTotalBytes() == 1024 && inv.getTotalTypes() == 12, "1k cell: 1024 bytes, 12 types");
        check(inv.insert(key(Aspect.ORDER), 3000, Actionable.SIMULATE, src) == 2048 && inv.getUsedBytes() == 0, "simulate fills to 2048 essentia without storing");
        check(inv.insert(key(Aspect.ORDER), 3000, Actionable.MODULATE, src) == 2048 && inv.getUsedBytes() == 1024 && inv.getStatus() == appeng.api.storage.cells.CellState.FULL, "1k cell holds exactly 2048 ordo and reports full");
        var reread = (EssentiaCellInventory) StorageCells.getCellInventory(one, null);var counter = new KeyCounter();reread.getAvailableStacks(counter);
        check(counter.get(key(Aspect.ORDER)) == 2048, "cell contents persist in the item");
        check(reread.extract(key(Aspect.ORDER), 47, Actionable.MODULATE, src) == 47 && reread.getUsedBytes() == 1001, "extract 47 leaves 2001 essentia = ceil(2001/2) = 1001 bytes");
        // 64k: 12 aspect types at most.
        var big = cell(EssentiaStorageType.TYPE_64K);var bigInv = (EssentiaCellInventory) StorageCells.getCellInventory(big, null);int accepted = 0;
        for (var aspect : Aspect.aspects.values()) if (bigInv.insert(key(aspect), 10, Actionable.MODULATE, src) == 10) accepted++;
        check(accepted == 12 && bigInv.getUsedTypes() == 12 && bigInv.getStatus() == appeng.api.storage.cells.CellState.TYPES_FULL, "64k cell accepts exactly 12 aspects, status types-full");
        check(bigInv.getTotalBytes() == 65536 && ((EssentiaCellInventory) StorageCells.getCellInventory(cell(EssentiaStorageType.TYPE_16K), null)).getTotalBytes() == 16348, "64k = 65536 bytes, original 16k = 16348 bytes");
        // Partition.
        var part = cell(EssentiaStorageType.TYPE_4K);var partInv = (EssentiaCellInventory) StorageCells.getCellInventory(part, null);partInv.setPartition(List.of(Aspect.FIRE));
        check(partInv.insert(key(Aspect.ORDER), 5, Actionable.MODULATE, src) == 0 && partInv.insert(key(Aspect.FIRE), 5, Actionable.MODULATE, src) == 5, "partitioned cell takes only its aspects");
        // Creative.
        var creative = cell(EssentiaStorageType.CREATIVE);var cInv = (EssentiaCellInventory) StorageCells.getCellInventory(creative, null);cInv.setPartition(List.of(Aspect.AIR, Aspect.MAGIC));
        cInv = (EssentiaCellInventory) StorageCells.getCellInventory(creative, null);var cc = new KeyCounter();cInv.getAvailableStacks(cc);
        check(cc.get(key(Aspect.AIR)) == EssentiaCellInventory.CREATIVE_AMOUNT && cInv.extract(key(Aspect.MAGIC), 999, Actionable.MODULATE, src) == 999 && cInv.extract(key(Aspect.FIRE), 1, Actionable.MODULATE, src) == 0, "creative cell: 2e9 of each partitioned aspect, infinite extract");
        check(cInv.insert(key(Aspect.AIR), 10, Actionable.MODULATE, src) == 0, "creative cell accepts nothing");
        check(cInv.insert(appeng.api.stacks.AEItemKey.of(Items.DIRT), 1, Actionable.MODULATE, src) == 0 && inv.insert(appeng.api.stacks.AEItemKey.of(Items.DIRT), 1, Actionable.SIMULATE, src) == 0, "essentia cells refuse items");
    }

    static void research() {
        for (var key : List.of(ThEResearch.BASIC, ThEResearch.CORES, ThEResearch.STORAGE, ThEResearch.ESSTERM)) check(ResearchCategories.getResearch(key) != null && ResearchCategories.getResearch(key).category.equals(ThEResearch.TAB), "research " + key + " in tab");
        var storage = ResearchCategories.getResearch(ThEResearch.STORAGE);
        check(storage.displayColumn == 0 && storage.displayRow == 2 && storage.getComplexity() == 2 && storage.tags.getAmount(Aspect.VOID) == 5, "TESTORAGE original position, complexity and aspects");
        check(ResearchCategories.getResearch("thaumicenergistics.Pseudo.DISTILESSENTIA") != null && java.util.Arrays.asList(ResearchCategories.getResearch("DISTILESSENTIA").siblings).contains("thaumicenergistics.Pseudo.DISTILESSENTIA"), "pseudo DISTILESSENTIA linked as sibling");
        var grid = new ArrayList<ItemStack>();
        String[] rows = {"EQ ", "QPQ", " QE"};
        for (var row : rows) for (char ch : row.toCharArray()) grid.add(switch (ch) { case 'E' -> new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("tc4modern:wisp_essence"))); case 'Q' -> new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:charged_certus_quartz_crystal"))); case 'P' -> new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:logic_processor"))); default -> ItemStack.EMPTY; });
        var found = ArcaneRecipes.find(grid, java.util.Set.of(ThEResearch.STORAGE));
        check(found != null && found.output().is(ThaumicEnergistics.COMPONENTS.get(EssentiaStorageType.TYPE_1K).get()) && found.cost().getAmount(Aspect.FIRE) == 3, "1k component arcane recipe with charged certus (quartz group), fire 3 order 1");
        check(ArcaneRecipes.find(grid, java.util.Set.of()) == null, "component recipe locked behind TESTORAGE");
    }

    static void aspects(net.minecraft.world.level.Level level) {
        int total = 0, empty = 0;
        for (var item : BuiltInRegistries.ITEM) {
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().equals("ae2")) continue;
            total++;
            var tags = RecipeAspects.get(new ItemStack(item), level);
            if (tags.isEmpty()) empty++;
            aspectTable.add(id.getPath() + "\t" + tags);
        }
        try { Files.createDirectories(Path.of("audit")); Files.write(Path.of("audit", "ae2-aspects.tsv"), aspectTable); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        check(total > 250 && empty == 0, "every AE2 item has aspects (" + total + " items)");
        check(RecipeAspects.get(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:certus_quartz_crystal"))), level).equals(java.util.Map.of("vitreus", 2)), "certus quartz = vitreus 2 (original base)");
        var fluix = RecipeAspects.get(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_crystal"))), level);
        check(fluix.get("vitreus") == 2 && fluix.get("potentia") == 6, "fluix crystal = vitreus 2 potentia 6");
        var logic = RecipeAspects.get(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:logic_processor"))), level);
        check(logic.getOrDefault("cognitio", 0) >= 3 && logic.size() <= 6, "logic processor derived from its inscriber recipe with the original mind bonus " + logic);
        var cell = RecipeAspects.get(cell(EssentiaStorageType.TYPE_1K), level);
        check(cell.getOrDefault("auram", 0) >= 5 && cell.getOrDefault("praecantatio", 0) >= 3, "essentia cell = 1k item cell tags + magic 3 + aura 5 " + cell);
    }

    @SubscribeEvent static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("thaumicenergistics.audit") || Boolean.getBoolean("thaumicenergistics.stage2") || Boolean.getBoolean("thaumicenergistics.stage3") || Boolean.getBoolean("thaumicenergistics.stage4") || Boolean.getBoolean("thaumicenergistics.stage5") || Boolean.getBoolean("thaumicenergistics.stage6")) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ticks++;
        var server = mc.getSingleplayerServer();var id = mc.player.getUUID();
        if (ticks == 40) server.execute(() -> {
            var p = server.getPlayerList().getPlayer(id);var l = p.serverLevel();
            l.getEntitiesOfClass(Entity.class, new AABB(BASE).inflate(12), e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(Entity::discard);
            for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) for (int y = -1; y < 6; y++) l.setBlock(BASE.offset(x, y, z), (y == -1 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
            l.setDayTime(6000);
            cells(); research(); aspects(l);
            // Network: creative energy cell - drive - cable - essentia terminal (ad-hoc network, no controller needed).
            l.setBlock(BASE, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")).defaultBlockState(), 3);
            l.setBlock(BASE.east(), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:drive")).defaultBlockState(), 3);
            var cablePos = BASE.east(2);
            PartHelper.setPart(l, cablePos, null, p, (appeng.api.parts.IPartItem<?>) BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable")));
            var terminal = PartHelper.setPart(l, cablePos, Direction.NORTH, p, ThaumicEnergistics.ESSENTIA_TERMINAL.get());
            check(terminal instanceof EssentiaTerminalPart, "essentia terminal part placed on cable");
            var drive = (DriveBlockEntity) l.getBlockEntity(BASE.east());
            var filled = cell(EssentiaStorageType.TYPE_4K);
            var fi = (EssentiaCellInventory) StorageCells.getCellInventory(filled, null);
            fi.insert(key(Aspect.AIR), 640, Actionable.MODULATE, IActionSource.empty());fi.insert(key(Aspect.FIRE), 128, Actionable.MODULATE, IActionSource.empty());fi.insert(key(Aspect.MAGIC), 75, Actionable.MODULATE, IActionSource.empty());
            drive.getInternalInventory().setItemDirect(0, filled);
            drive.getInternalInventory().setItemDirect(1, cell(EssentiaStorageType.TYPE_1K));
            p.connection.teleport(cablePos.getX() + .5, cablePos.getY(), cablePos.getZ() - 2.2, 0, 15);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            p.getInventory().clearContent();
            p.getInventory().setItem(0, cell(EssentiaStorageType.TYPE_1K));
            p.getInventory().setItem(1, big64());
            built = true;
        });
        if (ticks == 120) server.execute(() -> {
            var p = server.getPlayerList().getPlayer(id);var l = p.serverLevel();
            var terminal = (EssentiaTerminalPart) PartHelper.getPart(l, BASE.east(2), Direction.NORTH);
            var grid = terminal.getMainNode().getGrid();
            check(grid != null && terminal.isActive(), "terminal powered and on the network");
            var storage = grid.getStorageService().getInventory();
            var seen = new KeyCounter();storage.getAvailableStacks(seen);
            check(seen.get(key(Aspect.AIR)) == 640 && seen.get(key(Aspect.FIRE)) == 128 && seen.get(key(Aspect.MAGIC)) == 75, "network sees the essentia in the drive cell");
            check(storage.insert(key(Aspect.ORDER), 300, Actionable.MODULATE, IActionSource.empty()) == 300 && storage.extract(key(Aspect.AIR), 40, Actionable.MODULATE, IActionSource.empty()) == 40, "network inserts ordo and extracts aer through the cells");
            MenuOpener.open(MEStorageMenu.TYPE, p, MenuLocators.forPart(terminal));
            workbenchAndContainers(p, l);
        });
        if (ticks == 160) { check(mc.player.containerMenu instanceof MEStorageMenu, "essentia terminal opens the ME storage menu"); shot(mc, "essentia-terminal.png"); }
        if (ticks == 170) mc.player.closeContainer();
        if (ticks == 180) shot(mc, "terminal-on-cable.png");
        if (ticks == 190) { var inv = mc.player.getInventory(); var book = new dev.eugheist.tc4modern.ThaumonomiconScreen(new dev.eugheist.tc4modern.ThaumonomiconMenu(0, inv), inv, Component.empty()); mc.setScreen(book); invoke(book, "auditBrowser", ThEResearch.TAB, 0, 0); }
        if (ticks == 200) shot(mc, "research-tab.png");
        if (ticks == 205) invoke(mc.screen, "auditOpen", ResearchCategories.getResearch(ThEResearch.STORAGE), 2);
        if (ticks == 215) shot(mc, "research-storage-recipes.png");
        if (ticks == 220) invoke(mc.screen, "auditOpen", ResearchCategories.getResearch(ThEResearch.STORAGE), 4);
        if (ticks == 230) { shot(mc, "research-storage-cells.png"); mc.setScreen(null); }
        if (ticks == 235) server.execute(() -> { var p = server.getPlayerList().getPlayer(id); var wb = (dev.eugheist.thaumicenergistics.block.CellWorkbenchEntity) p.serverLevel().getBlockEntity(WORKBENCH);
            p.openMenu(new net.minecraft.world.SimpleMenuProvider((cid, inv, pl) -> new dev.eugheist.thaumicenergistics.block.CellWorkbenchMenu(cid, inv, wb), Component.translatable("thaumicenergistics.gui.essentia.cell.workbench.title")), buf -> buf.writeBlockPos(WORKBENCH)); });
        if (ticks == 238) { check(mc.screen instanceof dev.eugheist.thaumicenergistics.client.CellWorkbenchScreen, "essentia cell workbench GUI opens"); }
        if (ticks == 239) shot(mc, "cell-workbench.png");
        if (ticks == 240) { mc.setScreen(null); var lines = new ArrayList<Component>(); var stack = mc.player.getInventory().getItem(1); stack.getItem().appendHoverText(stack, net.minecraft.world.item.Item.TooltipContext.of(mc.level), lines, net.minecraft.world.item.TooltipFlag.NORMAL);
            System.out.println("THE_AUDIT tooltip " + lines.stream().map(Component::getString).toList());
            check(lines.size() >= 3 && lines.get(0).getString().contains("467") && lines.get(0).getString().contains("65536") && lines.get(1).getString().contains("2") && lines.get(1).getString().contains("12"), "64k cell tooltip: 467 of 65536 bytes, 2 of 12 types"); }
        if (ticks == 250) { System.out.println("THE_AUDIT ALL PASS"); mc.stop(); }
    }

    static final BlockPos WORKBENCH = BASE.west(2);
    static ItemStack phial(Aspect a) { return dev.eugheist.tc4modern.EssentiaItems.phialOf(a, 8); }
    static void workbenchAndContainers(net.minecraft.server.level.ServerPlayer p, net.minecraft.server.level.ServerLevel l) {
        l.setBlock(WORKBENCH, ThaumicEnergistics.CELL_WORKBENCH.get().defaultBlockState(), 3);
        var wb = (dev.eugheist.thaumicenergistics.block.CellWorkbenchEntity) l.getBlockEntity(WORKBENCH);
        wb.inventory.setItem(0, cell(EssentiaStorageType.TYPE_1K));
        var menu = new dev.eugheist.thaumicenergistics.block.CellWorkbenchMenu(0, p.getInventory(), wb);
        menu.setCarried(phial(Aspect.FIRE)); menu.clickMenuButton(p, 0);
        check(wb.partition().equals(List.of(Aspect.FIRE)), "workbench: clicking an empty widget with an ignis phial partitions ignis");
        menu.setCarried(phial(Aspect.AIR)); menu.clickMenuButton(p, 0);
        check(wb.partition().equals(List.of(Aspect.AIR)), "workbench: clicking a used widget with another aspect replaces it");
        menu.setCarried(phial(Aspect.WATER)); menu.clickMenuButton(p, 5);
        menu.setCarried(ItemStack.EMPTY); menu.clickMenuButton(p, 0);
        check(wb.partition().equals(List.of(Aspect.WATER)), "workbench: empty hand removes the clicked aspect");
        menu.clickMenuButton(p, dev.eugheist.thaumicenergistics.block.CellWorkbenchMenu.BUTTON_CLEAR);
        check(wb.partition().isEmpty(), "workbench: clear button");
        var filled = cell(EssentiaStorageType.TYPE_1K);var fi = (EssentiaCellInventory) StorageCells.getCellInventory(filled, null);
        fi.insert(key(Aspect.EARTH), 10, Actionable.MODULATE, IActionSource.empty());fi.insert(key(Aspect.ORDER), 4, Actionable.MODULATE, IActionSource.empty());
        wb.inventory.setItem(0, filled);menu.clickMenuButton(p, dev.eugheist.thaumicenergistics.block.CellWorkbenchMenu.BUTTON_TO_CONTENTS);
        check(wb.partition().equals(List.of(Aspect.EARTH, Aspect.ORDER)), "workbench: partition to current contents");
        menu.setCarried(ItemStack.EMPTY);
        wb.inventory.setItem(0, ItemStack.EMPTY);var shown = cell(EssentiaStorageType.TYPE_4K);var si = (EssentiaCellInventory) StorageCells.getCellInventory(shown, null);
        si.setPartition(List.of(Aspect.AIR, Aspect.FIRE, Aspect.WATER, Aspect.EARTH, Aspect.ORDER, Aspect.ENTROPY, Aspect.MAGIC, Aspect.MIND, Aspect.CRYSTAL, Aspect.ENERGY));wb.inventory.setItem(0, shown);
        // Container items through the terminal strategy.
        var s = dev.eugheist.thaumicenergistics.me.EssentiaContainerItemStrategy.INSTANCE;
        p.getInventory().setItem(5, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("tc4modern:phial"))));
        var ctx = s.findPlayerSlotContext(p, 5);
        check(ctx != null && s.insert(ctx, key(Aspect.ORDER), 7, Actionable.SIMULATE) == 0 && s.insert(ctx, key(Aspect.ORDER), 20, Actionable.MODULATE) == 8, "empty phial fills with exactly 8 essentia");
        var phialSlot = -1; for (int i = 0; i < 36; i++) { var st = p.getInventory().getItem(i); if (BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().equals("essentia_phial")) phialSlot = i; }
        check(phialSlot >= 0 && s.getContainedStack(p.getInventory().getItem(phialSlot)).amount() == 8, "filled ordo phial returned to inventory");
        var pctx = s.findPlayerSlotContext(p, phialSlot);
        check(s.extract(pctx, key(Aspect.FIRE), 8, Actionable.SIMULATE) == 0 && s.extract(pctx, key(Aspect.ORDER), 8, Actionable.MODULATE) == 8, "phial empties all 8 of its own aspect only");
        p.getInventory().setItem(6, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("tc4modern:warded_jar"))));
        var jctx = s.findPlayerSlotContext(p, 6);
        check(s.insert(jctx, key(Aspect.AIR), 100, Actionable.MODULATE) == 64, "jar fills to 64");
        int jarSlot = -1; for (int i = 0; i < 36; i++) { var st = p.getInventory().getItem(i); var c = s.getContainedStack(st); if (c != null && c.amount() == 64) jarSlot = i; }
        check(jarSlot >= 0 && s.extract(s.findPlayerSlotContext(p, jarSlot), key(Aspect.AIR), 30, Actionable.MODULATE) == 30, "jar gives back part of its essentia");
        p.getInventory().clearContent();p.getInventory().setItem(0, cell(EssentiaStorageType.TYPE_1K));p.getInventory().setItem(1, big64());
    }

    static ItemStack big64() {
        var stack = cell(EssentiaStorageType.TYPE_64K);var inv = (EssentiaCellInventory) StorageCells.getCellInventory(stack, null);
        inv.insert(key(Aspect.WATER), 900, Actionable.MODULATE, IActionSource.empty());inv.insert(key(Aspect.EARTH), 33, Actionable.MODULATE, IActionSource.empty());
        return stack;
    }

    static void invoke(Object target, String name, Object... args) {
        try { for (var m : target.getClass().getDeclaredMethods()) if (m.getName().equals(name) && m.getParameterCount() == args.length) { m.setAccessible(true); m.invoke(target, args); return; } throw new IllegalStateException("No " + name); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
}
