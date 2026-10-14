package dev.eugheist.thaumicenergistics;

import appeng.api.config.Actionable;
import appeng.api.config.RedstoneMode;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.research.ResearchCategories;
import dev.eugheist.tc4modern.WardedJarEntity;
import dev.eugheist.thaumicenergistics.me.EssentiaCellInventory;
import dev.eugheist.thaumicenergistics.me.EssentiaKey;
import dev.eugheist.thaumicenergistics.me.EssentiaStorageType;
import dev.eugheist.thaumicenergistics.menu.*;
import dev.eugheist.thaumicenergistics.part.*;

/** Hidden stage 2 audit (-Dthaumicenergistics.audit=true -Dthaumicenergistics.stage2=true): buses, emitter, monitors on a live network. */
@EventBusSubscriber(modid = ThaumicEnergistics.ID, value = Dist.CLIENT)
public final class ThEStage2Audit {
    static final BlockPos BASE = new BlockPos(600, 121, -90);
    static int ticks;
    static void check(boolean ok, String what) { ThEAudit.check(ok, what); }
    static EssentiaKey key(Aspect a) { return EssentiaKey.of(a); }
    static BlockPos cable(int i) { return BASE.east(2 + i); }
    static ItemStack item(String id) { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))); }
    static <T> T part(ServerLevel l, int i, Direction side) { @SuppressWarnings("unchecked") var p = (T) PartHelper.getPart(l, cable(i), side); return p; }
    static WardedJarEntity jar(ServerLevel l, BlockPos pos, boolean voidJar, Aspect a, int amount) {
        l.setBlock(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse(voidJar ? "tc4modern:void_jar" : "tc4modern:warded_jar")).defaultBlockState(), 3);
        var jar = (WardedJarEntity) l.getBlockEntity(pos);
        if (a != null) { jar.state.aspect = a; jar.state.amount = amount; }
        return jar;
    }
    static long stored(ServerLevel l, Aspect a) { var grid = ((appeng.parts.AEBasePart) PartHelper.getPart(l, cable(0), Direction.NORTH)).getMainNode().getGrid(); return grid.getStorageService().getCachedInventory().get(key(a)); }
    static appeng.api.storage.MEStorage storage(ServerLevel l) { return ((appeng.parts.AEBasePart) PartHelper.getPart(l, cable(0), Direction.NORTH)).getMainNode().getGrid().getStorageService().getInventory(); }

    @SubscribeEvent static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("thaumicenergistics.stage2")) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ticks++;
        var server = mc.getSingleplayerServer();var id = mc.player.getUUID();
        if (ticks == 40) server.execute(() -> {
            var p = server.getPlayerList().getPlayer(id);var l = p.serverLevel();
            l.getEntitiesOfClass(Entity.class, new AABB(BASE).inflate(14), e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(Entity::discard);
            for (int x = -6; x <= 12; x++) for (int z = -6; z <= 6; z++) for (int y = -1; y < 6; y++) l.setBlock(BASE.offset(x, y, z), (y == -1 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
            l.setDayTime(6000);
            research();
            l.setBlock(BASE, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")).defaultBlockState(), 3);
            l.setBlock(BASE.east(), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:drive")).defaultBlockState(), 3);
            var cell = new ItemStack(ThaumicEnergistics.CELLS.get(EssentiaStorageType.TYPE_4K).get());
            var ci = (EssentiaCellInventory) StorageCells.getCellInventory(cell, null);
            ci.insert(key(Aspect.AIR), 640, Actionable.MODULATE, IActionSource.empty());ci.insert(key(Aspect.FIRE), 128, Actionable.MODULATE, IActionSource.empty());
            ((DriveBlockEntity) l.getBlockEntity(BASE.east())).getInternalInventory().setItemDirect(0, cell);
            for (int i = 0; i < 8; i++) PartHelper.setPart(l, cable(i), null, p, (IPartItem<?>) BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable")));
            PartHelper.setPart(l, cable(0), Direction.NORTH, p, ThaumicEnergistics.ESSENTIA_TERMINAL.get());
            // 1: import bus under a jar of 20 ordo; 2: export bus (ignis filter) under an empty jar; 3: storage bus under a 30 aqua jar;
            // 4: storage bus with void mode under a full void jar of perditio; 5: level emitter; 6: storage monitor; 7: conversion monitor.
            var imp = (EssentiaImportBusPart) PartHelper.setPart(l, cable(1), Direction.UP, p, ThaumicEnergistics.ESSENTIA_IMPORT_BUS.get());
            jar(l, cable(1).above(), false, Aspect.ORDER, 20);
            var exp = (EssentiaExportBusPart) PartHelper.setPart(l, cable(2), Direction.UP, p, ThaumicEnergistics.ESSENTIA_EXPORT_BUS.get());
            jar(l, cable(2).above(), false, null, 0);
            exp.setAspect(EssentiaIOBusPart.CENTER, Aspect.FIRE);
            var sb = (EssentiaStorageBusPart) PartHelper.setPart(l, cable(3), Direction.UP, p, ThaumicEnergistics.ESSENTIA_STORAGE_BUS.get());
            jar(l, cable(3).above(), false, Aspect.WATER, 30);
            sb.setPriority(10);
            var vb = (EssentiaStorageBusPart) PartHelper.setPart(l, cable(4), Direction.UP, p, ThaumicEnergistics.ESSENTIA_STORAGE_BUS.get());
            jar(l, cable(4).above(), true, Aspect.ENTROPY, 64);
            vb.setAspect(0, Aspect.ENTROPY);vb.setVoidAllowed(true);vb.setPriority(5);
            var em = (EssentiaLevelEmitterPart) PartHelper.setPart(l, cable(5), Direction.UP, p, ThaumicEnergistics.ESSENTIA_LEVEL_EMITTER.get());
            check(imp != null && exp != null && sb != null && vb != null && em != null, "all stage 2 parts placed on cables");
            PartHelper.setPart(l, cable(6), Direction.NORTH, p, ThaumicEnergistics.ESSENTIA_STORAGE_MONITOR.get());
            PartHelper.setPart(l, cable(7), Direction.NORTH, p, ThaumicEnergistics.ESSENTIA_CONVERSION_MONITOR.get());
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            p.connection.teleport(cable(4).getX() + .5, cable(4).getY(), cable(4).getZ() - 3.2, 0, 20);
            p.getInventory().clearContent();
        });
        if (ticks == 160) server.execute(() -> {
            var p = server.getPlayerList().getPlayer(id);var l = p.serverLevel();
            EssentiaImportBusPart imp = part(l, 1, Direction.UP);EssentiaExportBusPart exp = part(l, 2, Direction.UP);
            check(imp.isActive() && exp.isActive(), "buses powered with channels");
            var importJar = (WardedJarEntity) l.getBlockEntity(cable(1).above());
            check(importJar.state.amount == 0 && stored(l, Aspect.ORDER) == 20, "import bus moved all 20 ordo from the jar into the network");
            var exportJar = (WardedJarEntity) l.getBlockEntity(cable(2).above());
            check(exportJar.state.aspect == Aspect.FIRE && exportJar.state.amount > 0, "export bus filled the empty jar with filtered ignis (" + exportJar.state.amount + ")");
            check(stored(l, Aspect.FIRE) + exportJar.state.amount == 128 + 0 || stored(l, Aspect.FIRE) + exportJar.state.amount == 128, "ignis conserved between network and jar");
            // Storage bus: the jar's aqua shows up, and with priority 10 new aqua goes to the jar first.
            check(stored(l, Aspect.WATER) == 30, "storage bus exposes the jar's 30 aqua");
            check(storage(l).insert(key(Aspect.WATER), 10, Actionable.MODULATE, IActionSource.empty()) == 10 && ((WardedJarEntity) l.getBlockEntity(cable(3).above())).state.amount == 40, "aqua inserted into the network lands in the prioritised jar");
            check(storage(l).extract(key(Aspect.WATER), 15, Actionable.MODULATE, IActionSource.empty()) == 15 && ((WardedJarEntity) l.getBlockEntity(cable(3).above())).state.amount == 25, "aqua extracted from the network comes out of the jar");
            check(storage(l).insert(key(Aspect.EARTH), 5, Actionable.MODULATE, IActionSource.empty()) == 5 && ((WardedJarEntity) l.getBlockEntity(cable(3).above())).state.aspect == Aspect.WATER, "the aqua jar refuses terra (goes to the cell)");
            // Void storage bus: perditio overflow is destroyed by the full void jar.
            long before = stored(l, Aspect.ENTROPY);
            check(before == 64 && storage(l).insert(key(Aspect.ENTROPY), 50, Actionable.MODULATE, IActionSource.empty()) == 50 && ((WardedJarEntity) l.getBlockEntity(cable(4).above())).state.amount == 64, "void-mode storage bus voids perditio into the full void jar");
            // Import filter: with a filter of aer the ordo jar is ignored.
            importJar.state.aspect = Aspect.ORDER;importJar.state.amount = 10;importJar.changed();imp.setAspect(EssentiaIOBusPart.CENTER, Aspect.AIR);
            // Upgrades: capacity cards open the filter tiers.
            check(!imp.slotOpen(1) && !imp.slotOpen(0), "no capacity card: only the centre filter slot");
            imp.getUpgrades().addItems(new ItemStack(AEItems.CAPACITY_CARD.asItem()));
            check(imp.slotOpen(1) && !imp.slotOpen(0), "one capacity card opens the cross");
            imp.getUpgrades().addItems(new ItemStack(AEItems.CAPACITY_CARD.asItem()));
            check(imp.slotOpen(0) && imp.filterSize() == 2, "two capacity cards open the corners");
            check(imp.getUpgrades().addItems(new ItemStack(AEItems.CAPACITY_CARD.asItem())).getCount() == 1, "a third capacity card is refused (max 2)");
            imp.getUpgrades().addItems(new ItemStack(AEItems.SPEED_CARD.asItem(), 2));
            check(imp.speedCards() == 2, "two speed cards accepted");
            // Menu buttons: carried aer phial sets slot 1, duplicate refused, empty hand clears.
            var menu = new EssentiaIOBusMenu(0, p.getInventory(), imp);
            menu.setCarried(dev.eugheist.tc4modern.EssentiaItems.phialOf(Aspect.FIRE, 8)); menu.clickMenuButton(p, 1);
            check(imp.getAspect(1) == Aspect.FIRE, "IO bus menu: widget click with an ignis phial sets the filter");
            menu.setCarried(dev.eugheist.tc4modern.EssentiaItems.phialOf(Aspect.FIRE, 8)); menu.clickMenuButton(p, 3);
            check(imp.getAspect(3) == null, "IO bus menu: an aspect already filtered is not added twice");
            menu.setCarried(ItemStack.EMPTY); menu.clickMenuButton(p, 1);
            check(imp.getAspect(1) == null, "IO bus menu: empty hand clears the widget");
            menu.clickMenuButton(p, EssentiaIOBusMenu.BUTTON_REDSTONE);
            check(imp.getRSMode() == RedstoneMode.LOW_SIGNAL, "redstone button cycles the mode");
            imp.setRSMode(RedstoneMode.IGNORE);
            // Level emitter: aer 640, threshold 600 high -> on; extract 100 -> off; low mode -> on.
            EssentiaLevelEmitterPart em = part(l, 5, Direction.UP);
            var emenu = new EssentiaLevelEmitterMenu(0, p.getInventory(), em);
            emenu.setCarried(dev.eugheist.tc4modern.EssentiaItems.phialOf(Aspect.AIR, 8)); emenu.clickMenuButton(p, 0); emenu.setCarried(ItemStack.EMPTY);
            emenu.setThreshold(499); emenu.clickMenuButton(p, 1); emenu.clickMenuButton(p, 13); emenu.clickMenuButton(p, 14);
            check(em.getTracked() == Aspect.AIR && em.getThreshold() == 10400, "emitter tracks aer from a phial; 499 +1 +10000 -100 = threshold " + em.getThreshold());
            emenu.setThreshold(600);
            check(em.isProvidingStrongPower() == 15, "emitter on: 640 aer >= 600");
            storage(l).extract(key(Aspect.AIR), 100, Actionable.MODULATE, IActionSource.empty());
        });
        if (ticks == 200) server.execute(() -> {
            var p = server.getPlayerList().getPlayer(id);var l = p.serverLevel();
            EssentiaLevelEmitterPart em = part(l, 5, Direction.UP);
            check(em.isProvidingStrongPower() == 0, "emitter off after aer drops to 540");
            em.toggleMode();
            check(em.isProvidingStrongPower() == 15, "low mode emits below the threshold");
            var importJar = (WardedJarEntity) l.getBlockEntity(cable(1).above());
            check(importJar.state.amount == 10, "import bus with an aer filter leaves the ordo jar alone");
            // Monitors.
            EssentiaMonitors.Storage sm = part(l, 6, Direction.NORTH);
            p.setItemInHand(InteractionHand.MAIN_HAND, dev.eugheist.tc4modern.EssentiaItems.phialOf(Aspect.FIRE, 8));
            sm.onUseItemOn(p.getMainHandItem(), p, InteractionHand.MAIN_HAND, Vec3.ZERO);
            check(sm.getDisplayed() == key(Aspect.FIRE), "storage monitor tracks ignis from a phial");
            p.setItemInHand(InteractionHand.MAIN_HAND, item("ae2:certus_quartz_wrench"));
            sm.onUseItemOn(p.getMainHandItem(), p, InteractionHand.MAIN_HAND, Vec3.ZERO);
            check(sm.isLocked(), "wrench locks the storage monitor");
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            sm.onUseWithoutItem(p, Vec3.ZERO);
            check(sm.getDisplayed() == key(Aspect.FIRE), "locked monitor keeps its aspect on an empty-hand click");
            EssentiaMonitors.Conversion cm = part(l, 7, Direction.NORTH);
            p.setItemInHand(InteractionHand.MAIN_HAND, dev.eugheist.tc4modern.EssentiaItems.phialOf(Aspect.AIR, 8));
            cm.onUseItemOn(p.getMainHandItem(), p, InteractionHand.MAIN_HAND, Vec3.ZERO);
            p.setItemInHand(InteractionHand.MAIN_HAND, item("ae2:certus_quartz_wrench"));
            cm.onUseItemOn(p.getMainHandItem(), p, InteractionHand.MAIN_HAND, Vec3.ZERO);
            check(cm.getDisplayed() == key(Aspect.AIR) && cm.isLocked(), "conversion monitor tracks aer and locks");
            long air = storage(l).extract(key(Aspect.AIR), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
            p.setItemInHand(InteractionHand.MAIN_HAND, item("tc4modern:phial"));
            cm.onUseItemOn(p.getMainHandItem(), p, InteractionHand.MAIN_HAND, Vec3.ZERO);
            long air2 = storage(l).extract(key(Aspect.AIR), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
            int filled = -1;for (int i = 0; i < 36; i++) { var c = dev.eugheist.thaumicenergistics.me.EssentiaContainerItemStrategy.INSTANCE.getContainedStack(p.getInventory().getItem(i)); if (c != null && c.what() == key(Aspect.AIR)) filled = i; }
            check(air - air2 == 8 && filled >= 0, "locked conversion monitor fills an empty phial with 8 aer from the network");
            p.getInventory().setItem(10, dev.eugheist.tc4modern.EssentiaItems.phialOf(Aspect.AIR, 8));
            p.getInventory().selected = filled < 9 ? filled : 0; if (filled >= 9) { p.getInventory().setItem(0, p.getInventory().getItem(filled)); p.getInventory().setItem(filled, ItemStack.EMPTY); }
            p.setShiftKeyDown(true);
            cm.onUseItemOn(p.getMainHandItem(), p, InteractionHand.MAIN_HAND, Vec3.ZERO);
            long air3 = storage(l).extract(key(Aspect.AIR), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
            check(air3 - air2 == 8, "sneak-click empties the held phial into the network");
            p.setItemInHand(InteractionHand.MAIN_HAND, item("tc4modern:phial"));
            cm.onUseItemOn(p.getMainHandItem(), p, InteractionHand.MAIN_HAND, Vec3.ZERO);
            long air4 = storage(l).extract(key(Aspect.AIR), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
            check(air4 - air3 == 8, "sneak double-click empties every aer container in the inventory");
            p.setShiftKeyDown(false);
            p.getInventory().clearContent();
            p.getInventory().setItem(8, item("ae2:network_tool"));
            PartMenus.open(part(l, 1, Direction.UP), p);
        });
        if (ticks == 215) { check(mc.screen instanceof dev.eugheist.thaumicenergistics.client.EssentiaIOBusScreen s && s.getMenu().hasNetworkTool() && s.getMenu().filterSize() == 2, "import bus GUI opens with network tool panel and synced filter size"); ThEAudit.shot(mc, "import-bus.png"); }
        if (ticks == 220) server.execute(() -> { var p = server.getPlayerList().getPlayer(id); p.getInventory().clearContent(); PartMenus.open(part(p.serverLevel(), 2, Direction.UP), p); });
        if (ticks == 235) { check(mc.screen instanceof dev.eugheist.thaumicenergistics.client.EssentiaIOBusScreen s && s.getMenu().isExport() && s.getMenu().aspect(4) == Aspect.FIRE, "export bus GUI shows the ignis filter"); ThEAudit.shot(mc, "export-bus.png"); }
        if (ticks == 240) server.execute(() -> { var p = server.getPlayerList().getPlayer(id); PartMenus.open(part(p.serverLevel(), 4, Direction.UP), p); });
        if (ticks == 255) { check(mc.screen instanceof dev.eugheist.thaumicenergistics.client.EssentiaStorageBusScreen s && s.getMenu().voidAllowed() && s.getMenu().aspect(0) == Aspect.ENTROPY, "storage bus GUI shows void mode and perditio filter"); ThEAudit.shot(mc, "storage-bus.png"); mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, EssentiaStorageBusMenu.BUTTON_PRIORITY); }
        if (ticks == 270) { check(mc.player.containerMenu instanceof appeng.menu.implementations.PriorityMenu, "priority tab opens AE2's priority menu"); ThEAudit.shot(mc, "storage-bus-priority.png"); }
        if (ticks == 275) server.execute(() -> { var p = server.getPlayerList().getPlayer(id); PartMenus.open(part(p.serverLevel(), 5, Direction.UP), p); });
        if (ticks == 290) { check(mc.screen instanceof dev.eugheist.thaumicenergistics.client.EssentiaLevelEmitterScreen s && s.getMenu().threshold() == 600 && s.getMenu().tracked() == Aspect.AIR, "emitter GUI shows aer and threshold 600 (long over short data slots)"); ThEAudit.shot(mc, "level-emitter.png");
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ThresholdPayload(1234567890L)); }
        if (ticks == 300) server.execute(() -> { EssentiaLevelEmitterPart em = part(server.getPlayerList().getPlayer(id).serverLevel(), 5, Direction.UP); check(em.getThreshold() == 1234567890L, "typed threshold payload reaches the server"); });
        if (ticks == 305) mc.setScreen(null);
        if (ticks == 320) ThEAudit.shot(mc, "stage2-parts.png");
        if (ticks == 325) { var inv = mc.player.getInventory(); var book = new dev.eugheist.tc4modern.ThaumonomiconScreen(new dev.eugheist.tc4modern.ThaumonomiconMenu(0, inv), inv, net.minecraft.network.chat.Component.empty()); mc.setScreen(book); ThEAudit.invoke(book, "auditBrowser", ThEResearch.TAB, 0, 0); }
        if (ticks == 335) ThEAudit.shot(mc, "research-tab-stage2.png");
        if (ticks == 340) ThEAudit.invoke(mc.screen, "auditOpen", ResearchCategories.getResearch(ThEResearch.IO), 2);
        if (ticks == 350) ThEAudit.shot(mc, "research-io.png");
        if (ticks == 355) ThEAudit.invoke(mc.screen, "auditOpen", ResearchCategories.getResearch(ThEResearch.ESSTERM), 4);
        if (ticks == 365) ThEAudit.shot(mc, "research-essterm-emitter.png");
        if (ticks == 370) ThEAudit.invoke(mc.screen, "auditOpen", ResearchCategories.getResearch(ThEResearch.ESSTERM), 6);
        if (ticks == 380) { ThEAudit.shot(mc, "research-essterm-monitors.png"); mc.setScreen(null); }
        if (ticks == 390) { System.out.println("THE_AUDIT ALL PASS"); mc.stop(); }
    }

    static void research() {
        var io = ResearchCategories.getResearch(ThEResearch.IO);
        check(io != null && io.displayColumn == -2 && io.displayRow == -2 && io.getComplexity() == 2 && io.tags.getAmount(Aspect.MECHANISM) == 5, "TEIO at -2,-2 with the original aspects");
        check(java.util.Arrays.asList(io.parents).contains("thaumicenergistics.Pseudo.TUBEFILTER") && ResearchCategories.getResearch("thaumicenergistics.Pseudo.TUBEFILTER") != null, "TEIO hangs off the pseudo TUBEFILTER");
        var grid = new java.util.ArrayList<ItemStack>();
        for (var s : new String[]{"tc4modern:warded_jar", "thaumicenergistics:diffusion_core", "tc4modern:warded_jar", "minecraft:iron_ingot", "tc4modern:essentia_tube_filter", "minecraft:iron_ingot", "", "", ""}) grid.add(s.isEmpty() ? ItemStack.EMPTY : item(s));
        var found = dev.eugheist.tc4modern.ArcaneRecipes.find(grid, java.util.Set.of(ThEResearch.IO));
        check(found != null && found.output().is(ThaumicEnergistics.ESSENTIA_IMPORT_BUS.get()), "import bus arcane recipe JDJ/IFI");
        var emitter = new java.util.ArrayList<ItemStack>(java.util.List.of(item("ae2:calculation_processor"), item("minecraft:redstone_torch"), item("tc4modern:salis_mundus"), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
        var e = dev.eugheist.tc4modern.ArcaneRecipes.find(emitter, java.util.Set.of(ThEResearch.ESSTERM));
        check(e != null && e.output().getCount() == 4 && e.cost().getAmount(Aspect.FIRE) == 4, "level emitter: 4 from calculation processor + torch + salis, ignis 4");
    }
}
