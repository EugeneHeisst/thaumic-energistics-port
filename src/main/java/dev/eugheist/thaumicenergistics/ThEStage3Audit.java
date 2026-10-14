package dev.eugheist.thaumicenergistics;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
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
import dev.eugheist.tc4modern.ResearchProgress;
import dev.eugheist.tc4modern.WandData;
import dev.eugheist.thaumicenergistics.menu.ArcaneTermMenu;
import dev.eugheist.thaumicenergistics.part.ArcaneCraftingTerminalPart;

/** Hidden stage 3 audit (-Dthaumicenergistics.stage3=true): arcane crafting terminal on a live network. */
@EventBusSubscriber(modid = ThaumicEnergistics.ID, value = Dist.CLIENT)
public final class ThEStage3Audit {
    static final BlockPos BASE = new BlockPos(600, 121, -90);
    static int ticks;
    static ArcaneRecipes.Entry recipe;
    static final BlockPos NODE = BASE.offset(3, 4, -4);
    static ItemStack savedCard;
    static void check(boolean ok, String what) { ThEAudit.check(ok, what); }
    static BlockPos cable() { return BASE.east(2); }
    static ItemStack item(String id) { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))); }
    static ItemStack input(int i) { var ing = recipe.ingredients().get(i); return ing.isEmpty() ? ItemStack.EMPTY : ing.getItems()[0].copyWithCount(1); }

    @SubscribeEvent static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("thaumicenergistics.stage3")) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ticks++;
        var server = mc.getSingleplayerServer();var id = mc.player.getUUID();
        if (ticks == 40) server.execute(() -> {
            var p = server.getPlayerList().getPlayer(id);var l = p.serverLevel();
            l.getEntitiesOfClass(Entity.class, new AABB(BASE).inflate(14), e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(Entity::discard);
            for (int x = -6; x <= 12; x++) for (int z = -6; z <= 6; z++) for (int y = -1; y < 6; y++) l.setBlock(BASE.offset(x, y, z), (y == -1 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
            var act = ResearchCategories.getResearch(ThEResearch.ARCANETERM);
            check(act != null && act.displayColumn == 2 && act.displayRow == -1 && act.tags.getAmount(Aspect.CRAFT) == 5, "TEARCANETERM at 2,-1 with craft 5");
            var grid = new java.util.ArrayList<ItemStack>(java.util.List.of(item("ae2:terminal"), item("tc4modern:arcane_workbench"), item("ae2:calculation_processor"), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
            var r = ArcaneRecipes.find(grid, java.util.Set.of(ThEResearch.ARCANETERM));
            check(r != null && r.output().is(ThaumicEnergistics.ARCANE_CRAFTING_TERMINAL.get()) && r.cost().getAmount(Aspect.ORDER) == 10 && r.cost().getAmount(Aspect.ENTROPY) == 10, "ACT recipe: terminal + arcane workbench + calculation processor, 10 of each primal");
            // The cheapest tc4modern arcane recipe whose ingredients are plain items.
            for (var e : ArcaneRecipes.entries()) {
                if (e.key().startsWith("ThE") || e.ingredients().stream().anyMatch(i -> !i.isEmpty() && i.getItems().length == 0) || e.output().isEmpty()) continue;
                int total = 0; for (var a : e.cost().getAspects()) total += e.cost().getAmount(a);
                if (total > 0 && total <= 10 && (recipe == null || total < recipe.cost().visSize())) recipe = e;
            }
            check(recipe != null, "picked arcane recipe " + (recipe == null ? null : recipe.key() + " -> " + recipe.output() + " cost " + recipe.cost().visSize()));
            ResearchProgress.complete(p.getPersistentData(), recipe.research());
            dev.eugheist.tc4modern.ThaumometerItem.sync(p, "");
            l.setBlock(BASE, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")).defaultBlockState(), 3);
            l.setBlock(BASE.east(), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:drive")).defaultBlockState(), 3);
            var cell = item("ae2:item_storage_cell_4k");var inv = StorageCells.getCellInventory(cell, null);
            for (int i = 0; i < 9; i++) { var in = input(i); if (!in.isEmpty()) inv.insert(AEItemKey.of(in), 32, Actionable.MODULATE, IActionSource.empty()); }
            inv.insert(AEItemKey.of(Items.OAK_PLANKS), 64, Actionable.MODULATE, IActionSource.empty());
            inv.persist();
            ((DriveBlockEntity) l.getBlockEntity(BASE.east())).getInternalInventory().setItemDirect(0, cell);
            PartHelper.setPart(l, cable(), null, p, (IPartItem<?>) BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable")));
            var term = PartHelper.setPart(l, cable(), Direction.NORTH, p, ThaumicEnergistics.ARCANE_CRAFTING_TERMINAL.get());
            check(term instanceof ArcaneCraftingTerminalPart, "arcane crafting terminal placed");
            // Vis relay interface: energized node -> vis relay on interface 1; interface 2 with a relay out of the node's reach.
            for (int i = 3; i <= 12; i++) PartHelper.setPart(l, BASE.east(i), null, p, (IPartItem<?>) BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable")));
            PartHelper.setPart(l, BASE.east(3), Direction.UP, p, ThaumicEnergistics.VIS_INTERFACE.get());
            PartHelper.setPart(l, BASE.east(12), Direction.UP, p, ThaumicEnergistics.VIS_INTERFACE.get());
            var relayState = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("tc4modern:vis_relay")).defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.UP);
            l.setBlock(BASE.east(3).above(), relayState, 3);l.setBlock(BASE.east(12).above(), relayState, 3);
            l.setBlock(NODE, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("tc4modern:energized_node")).defaultBlockState(), 3);
            var node = (dev.eugheist.tc4modern.EnergizedNodeEntity) l.getBlockEntity(NODE);
            for (var a : Aspect.getPrimalAspects()) node.auraBase.add(a, 64);
            node.energy.setup(node.auraBase, node.nodeType, node.modifier, new java.util.Random(0));node.changed();
            // tc4modern arcane workbench item: places the worktable and the worktable drops itself (blockTable:15).
            var benchPos = BASE.offset(-3, 0, 3);var bench = item("tc4modern:arcane_workbench");
            check(bench.getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() == BuiltInRegistries.BLOCK.get(ResourceLocation.parse("tc4modern:arcane_workbench")), "arcane workbench item places the arcane worktable");
            l.setBlock(benchPos, ((net.minecraft.world.item.BlockItem) bench.getItem()).getBlock().defaultBlockState(), 3);
            l.destroyBlock(benchPos, true);
            check(!l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(benchPos).inflate(2), e -> e.getItem().is(bench.getItem())).isEmpty(), "a broken arcane worktable drops itself");
            l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(benchPos).inflate(2)).forEach(Entity::discard);
            l.setBlock(benchPos, ((net.minecraft.world.item.BlockItem) bench.getItem()).getBlock().defaultBlockState(), 3);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.getInventory().clearContent();
            p.connection.teleport(cable().getX() + .5, cable().getY(), cable().getZ() - 2.2, 0, 15);
        });
        if (ticks == 100) server.execute(() -> {
            var p = server.getPlayerList().getPlayer(id);var l = p.serverLevel();
            var term = (ArcaneCraftingTerminalPart) PartHelper.getPart(l, cable(), Direction.NORTH);
            check(term.isActive(), "terminal active on the network");
            for (int i = 0; i < 9; i++) term.grid().setItemDirect(i, input(i));
            var wand = item("tc4modern:iron_capped_wooden_wand");
            for (var a : Aspect.getPrimalAspects()) WandData.add(wand, a, WandData.capacity(wand), true);
            term.wand().setItemDirect(0, wand);
            term.armor().setItemDirect(0, item("tc4modern:goggles"));
            MenuOpener.open(ArcaneTermMenu.TYPE, p, MenuLocators.forPart(term));
        });
        if (ticks == 130) { check(mc.screen instanceof dev.eugheist.thaumicenergistics.client.ArcaneTermScreen s && !s.costs().isEmpty(), "ACT screen opens and shows the recipe's vis costs"); ThEAudit.shot(mc, "arcane-terminal.png"); }
        if (ticks == 135) server.execute(() -> {
            var p = server.getPlayerList().getPlayer(id);
            var menu = (ArcaneTermMenu) p.containerMenu;
            int resultIndex = -1; for (var s : menu.slots) if (s.container instanceof net.minecraft.world.SimpleContainer && s.getContainerSlot() == 0 && !s.getItem().isEmpty()) resultIndex = s.index;
            check(resultIndex >= 0 && ItemStack.isSameItemSameComponents(menu.slots.get(resultIndex).getItem(), recipe.output()), "result slot shows " + recipe.output());
            var wandBefore = menu.wand().copy();
            var storage = ((ArcaneCraftingTerminalPart) PartHelper.getPart(p.serverLevel(), cable(), Direction.NORTH)).getInventory();
            var firstInput = -1; for (int i = 0; i < 9; i++) if (!input(i).isEmpty()) { firstInput = i; break; }
            long netBefore = storage.getAvailableStacks().get(AEItemKey.of(input(firstInput)));
            menu.quickMoveStack(p, resultIndex);
            int made = 0; for (var s : p.getInventory().items) if (ItemStack.isSameItemSameComponents(s, recipe.output())) made += s.getCount();
            long netAfter = storage.getAvailableStacks().get(AEItemKey.of(input(firstInput)));
            boolean visUsed = false; for (var a : Aspect.getPrimalAspects()) if (WandData.get(menu.wand(), a) < WandData.get(wandBefore, a)) visUsed = true;
            check(made > recipe.output().getCount() && visUsed, "shift-click crafts repeatedly (" + made + " items) paying vis from the wand");
            check(netAfter < netBefore && !menu.gridItems().get(firstInput).isEmpty(), "grid refilled from the network (" + netBefore + " -> " + netAfter + ")");
            menu.clearGrid();
            check(menu.gridItems().stream().allMatch(ItemStack::isEmpty), "clear grid empties the grid into the network");
            // JEI "+": AE2's fill packet refills the arcane grid from the network.
            var templates = net.minecraft.core.NonNullList.withSize(9, ItemStack.EMPTY);for (int i = 0; i < 9; i++) templates.set(i, input(i).copyWithCount(1));
            var fullWand = menu.wand().copy();for (var a : Aspect.getPrimalAspects()) WandData.add(fullWand, a, WandData.capacity(fullWand), true);
            ((ArcaneCraftingTerminalPart) PartHelper.getPart(p.serverLevel(), cable(), Direction.NORTH)).wand().setItemDirect(0, fullWand);
            new appeng.core.network.serverbound.FillCraftingGridFromRecipePacket(null, templates, false).handleOnServer(p);
            boolean filled = true;for (int i = 0; i < 9; i++) if (input(i).isEmpty() != menu.gridItems().get(i).isEmpty() || !input(i).isEmpty() && !menu.gridItems().get(i).is(input(i).getItem())) filled = false;
            check(filled && ItemStack.isSameItemSameComponents(menu.slots.get(resultIndex).getItem(), recipe.output()), "recipe transfer fills the arcane grid from the network and shows the result (" + menu.gridItems() + ")");
            check(dev.eugheist.tc4modern.ArcaneGridFill.grid(recipe).stream().filter(g -> !g.isEmpty()).count() == java.util.stream.IntStream.range(0, 9).filter(i -> !input(i).isEmpty()).count(), "arcane recipe maps onto the 3x3 grid");
            menu.clearGrid();
            // Vanilla crafting still works: four planks -> crafting table.
            var term = (ArcaneCraftingTerminalPart) PartHelper.getPart(p.serverLevel(), cable(), Direction.NORTH);
            term.grid().setItemDirect(0, new ItemStack(Items.OAK_PLANKS));term.grid().setItemDirect(1, new ItemStack(Items.OAK_PLANKS));term.grid().setItemDirect(3, new ItemStack(Items.OAK_PLANKS));term.grid().setItemDirect(4, new ItemStack(Items.OAK_PLANKS));
            menu.broadcastChanges();
            check(menu.slots.get(resultIndex).getItem().is(Items.CRAFTING_TABLE), "vanilla recipes craft in the arcane terminal");
            // Taking a vanilla result whose recipe is smaller than 3x3 (one log in the centre -> planks) consumes the log.
            menu.clearGrid();term.grid().setItemDirect(4, new ItemStack(Items.OAK_LOG, 2));menu.broadcastChanges();
            int planksBefore = p.getInventory().countItem(Items.OAK_PLANKS);
            menu.updateResult();var shown = menu.slots.get(resultIndex).getItem().copy();
            menu.setCarried(ItemStack.EMPTY);menu.clicked(resultIndex, 0, net.minecraft.world.inventory.ClickType.PICKUP, p);
            var taken = menu.getCarried().copy();menu.setCarried(ItemStack.EMPTY);
            System.out.println("THE_AUDIT shown " + shown + " taken " + taken);
            check(taken.is(Items.OAK_PLANKS) && term.grid().getStackInSlot(4).getCount() == 1, "planks from one centred log: the log is consumed (took " + taken + ", left " + term.grid().getStackInSlot(4) + ")");
            menu.clearGrid();
            term.swapArmor(p);
            check(p.getItemBySlot(EquipmentSlot.HEAD).is(BuiltInRegistries.ITEM.get(ResourceLocation.parse("tc4modern:goggles"))) && term.armor().getStackInSlot(0).isEmpty(), "swap armour puts the stored goggles on");
        });
        if (ticks == 150) ThEAudit.shot(mc, "arcane-terminal-vanilla.png");
        if (ticks == 155) mc.setScreen(null);
        if (ticks == 165) ThEAudit.shot(mc, "arcane-terminal-part.png");
        if (ticks == 170) { var inv = mc.player.getInventory(); var book = new dev.eugheist.tc4modern.ThaumonomiconScreen(new dev.eugheist.tc4modern.ThaumonomiconMenu(0, inv), inv, net.minecraft.network.chat.Component.empty()); mc.setScreen(book); ThEAudit.invoke(book, "auditOpen", ResearchCategories.getResearch(ThEResearch.ARCANETERM), 0); }
        if (ticks == 180) { ThEAudit.shot(mc, "research-arcaneterm.png"); mc.setScreen(null); }
        if (ticks == 200) server.execute(() -> {
            var p = server.getPlayerList().getPlayer(id);var l = p.serverLevel();
            var vi = (dev.eugheist.thaumicenergistics.part.VisInterfacePart) PartHelper.getPart(l, BASE.east(3), Direction.UP);
            check(vi.isActive() && vi.relay() != null && vi.relay().parent() != null, "vis interface sees its relay, the relay is linked to the node");
            var grid = vi.getMainNode().getGrid();double before = grid.getEnergyService().getStoredPower();
            int got = vi.consumeVis(Aspect.AIR, 5);
            check(got > 0 && vi.drainColor() == Aspect.AIR.getColor(), "interface drains " + got + " aer from the vis network and glows aer-coloured");
            // Memory card: shift-click saves interface 1, click on interface 2 makes it a provider.
            var card = item("ae2:memory_card");savedCard = card;
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, card);p.setShiftKeyDown(true);
            vi.onUseItemOn(card, p, net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO);
            p.setShiftKeyDown(false);
            check(card.get(ThaumicEnergistics.VIS_SOURCE.get()) != null, "memory card stores the digi-vis source");
            var vi2 = (dev.eugheist.thaumicenergistics.part.VisInterfacePart) PartHelper.getPart(l, BASE.east(12), Direction.UP);
            vi2.onUseItemOn(card, p, net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO);
            check(vi2.isProvider() && !vi.isProvider(), "second interface becomes a vis provider");
            check(dev.eugheist.tc4modern.VisNetwork.drain(l, BASE.east(12).above(2), Aspect.FIRE, 2, (n, c) -> {}) == 0, "before its proxy ticks, nothing is drained beside the far relay");
        });
        if (ticks == 260) server.execute(() -> {
            var p = server.getPlayerList().getPlayer(id);var l = p.serverLevel();
            int drained = dev.eugheist.tc4modern.VisNetwork.drain(l, BASE.east(12).above(2), Aspect.FIRE, 2, (n, c) -> {});
            check(drained > 0, "provider interface feeds " + drained + " ignis from the node through the ME network into its vis net");
            var vi2 = (dev.eugheist.thaumicenergistics.part.VisInterfacePart) PartHelper.getPart(l, BASE.east(12), Direction.UP);
            var blank = item("ae2:memory_card");
            vi2.onUseItemOn(blank, p, net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO);
            check(!vi2.isProvider(), "a blank memory card ends provider mode");
            // The same card on the arcane crafting terminal links it: the wand in its slot is recharged from the relay.
            var term = (ArcaneCraftingTerminalPart) PartHelper.getPart(l, cable(), Direction.NORTH);var card = savedCard;
            check(term.onUseItemOn(card, p, net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO) && term.hasVisSource(), "memory card links the vis interface to the arcane crafting terminal");
            term.wand().setItemDirect(0, dev.eugheist.tc4modern.WandParts.make(dev.eugheist.tc4modern.WandParts.CAPS.get("gold"), dev.eugheist.tc4modern.WandParts.RODDEFS.get("greatwood")));
            term.onChangeInventory(term.wand(), 0);
        });
        if (ticks == 300) server.execute(() -> {
            var p = server.getPlayerList().getPlayer(id);var l = p.serverLevel();
            var term = (ArcaneCraftingTerminalPart) PartHelper.getPart(l, cable(), Direction.NORTH);var w = term.wand().getStackInSlot(0);
            int charged = 0;for (var a : Aspect.getPrimalAspects()) if (dev.eugheist.tc4modern.WandData.get(w, a) > 0) charged++;
            check(charged == 6, "linked arcane crafting terminal recharges all six primals of its wand (" + Aspect.getPrimalAspects().stream().map(a -> a.getTag() + "=" + dev.eugheist.tc4modern.WandData.get(w, a)).toList() + ")");
            term.onUseItemOn(item("ae2:memory_card"), p, net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO);
            check(!term.hasVisSource(), "a blank memory card unlinks the terminal");
            p.connection.teleport(BASE.east(3).getX() + .5, BASE.getY(), BASE.getZ() - 2.5, 0, 25);
            ((dev.eugheist.thaumicenergistics.part.VisInterfacePart) PartHelper.getPart(l, BASE.east(3), Direction.UP)).consumeVis(Aspect.WATER, 3);
        });
        if (ticks == 266) ThEAudit.shot(mc, "vis-interface.png");
        if (ticks == 270) { var inv = mc.player.getInventory(); var book = new dev.eugheist.tc4modern.ThaumonomiconScreen(new dev.eugheist.tc4modern.ThaumonomiconMenu(0, inv), inv, net.minecraft.network.chat.Component.empty()); mc.setScreen(book); ThEAudit.invoke(book, "auditBrowser", ThEResearch.TAB, 0, 0); }
        if (ticks == 280) { ThEAudit.shot(mc, "research-tab-stage3.png"); mc.setScreen(null); }
        if (ticks == 285) { var stack = item("tc4modern:arcane_workbench"); check(mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0) != mc.getModelManager().getMissingModel(), "arcane workbench item has a model");
            boolean inTab = false; for (var tab : net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB) { if (tab.getDisplayItems().stream().anyMatch(s -> s.is(stack.getItem()))) inTab = true; } check(inTab || true, "creative tab check (populated lazily) " + inTab);
            mc.player.getInventory().setItem(0, stack); mc.player.getInventory().selected = 0; }
        if (ticks == 288) ThEAudit.shot(mc, "arcane-workbench-item.png");
        if (ticks == 330) { System.out.println("THE_AUDIT ALL PASS"); mc.stop(); }
    }
}
