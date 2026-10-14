package dev.eugheist.thaumicenergistics;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import dev.eugheist.tc4modern.Golemancy;
import dev.eugheist.tc4modern.StrawGolem;
import dev.eugheist.tc4modern.WardedJarEntity;
import dev.eugheist.thaumicenergistics.client.GolemBackpackLayer;
import dev.eugheist.thaumicenergistics.golem.WifiGolems;
import dev.eugheist.thaumicenergistics.item.GolemBackpackItem;
import dev.eugheist.thaumicenergistics.me.EssentiaKey;
import dev.eugheist.thaumicenergistics.me.EssentiaStorageType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.research.ResearchCategories;

/** Hidden stage 6 audit (-Dthaumicenergistics.stage6=true): golem wifi backpack on gather/fill/liquid/essentia golems over a real access point. */
@EventBusSubscriber(modid = ThaumicEnergistics.ID, value = Dist.CLIENT)
public final class ThEStage6Audit {
    static final BlockPos BASE = new BlockPos(700, 121, -90), DRIVE = BASE.east(), WAP = DRIVE.above();
    static final BlockPos GATHER_HOME = BASE.offset(0, 0, 4), FILL_CHEST = BASE.offset(3, 0, 4), SPA = BASE.offset(6, 0, 4), JAR = BASE.offset(9, 0, 4), SOURCE_JAR = BASE.offset(9, 0, 7), FAR = BASE.offset(60, 0, 0);
    static int ticks;static StrawGolem gather, fill, liquid, essentia, unloader, far, unlinked;static long cobbleBefore;
    static void check(boolean ok, String what) { ThEAudit.check(ok, what); }
    static ItemStack item(String id) { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))); }
    static net.minecraft.world.level.block.state.BlockState block(String id) { return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState(); }

    @SubscribeEvent static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("thaumicenergistics.stage6")) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ticks++;
        var server = mc.getSingleplayerServer();var id = mc.player.getUUID();
        if (ticks == 40) server.execute(() -> run(() -> setup(server.getPlayerList().getPlayer(id))));
        if (ticks == 80) server.execute(() -> run(() -> direct(server.getPlayerList().getPlayer(id))));
        if (ticks == 400) server.execute(() -> run(() -> live(server.getPlayerList().getPlayer(id))));
        if (ticks == 405) server.execute(() -> run(() -> showcase(server.getPlayerList().getPlayer(id))));
        if (ticks == 420) { check(GolemBackpackLayer.rendered > 0, "backpack layer rendered on golems (" + GolemBackpackLayer.rendered + ")"); ThEAudit.shot(mc, "golem-backpacks.png"); }
        if (ticks == 425) server.execute(() -> run(() -> bell(server.getPlayerList().getPlayer(id))));
        if (ticks == 435) { var inv = mc.player.getInventory(); var book = new dev.eugheist.tc4modern.ThaumonomiconScreen(new dev.eugheist.tc4modern.ThaumonomiconMenu(0, inv), inv, net.minecraft.network.chat.Component.empty()); mc.setScreen(book); ThEAudit.invoke(book, "auditOpen", ResearchCategories.getResearch(ThEResearch.GOLEMWIFI), 0); }
        if (ticks == 445) ThEAudit.shot(mc, "research-golemwifi.png");
        if (ticks == 450) { ThEAudit.invoke(mc.screen, "auditOpen", ResearchCategories.getResearch(ThEResearch.GOLEMWIFI), 2); }
        if (ticks == 460) { ThEAudit.shot(mc, "research-golemwifi-recipe.png"); mc.setScreen(null); }
        if (ticks == 465) server.execute(() -> run(() -> models(server.getPlayerList().getPlayer(id))));
        if (ticks == 472) {
            var be = mc.level.getBlockEntity(MODELS) instanceof dev.eugheist.thaumicenergistics.block.GearBoxEntity g ? g : null;
            check(be != null && be.shaftRotation > 0, "crank reaches the client and spins the gearbox shafts (" + (be == null ? "no entity" : be.shaftRotation) + ")");
        }
        if (ticks == 490) {
            check(dev.eugheist.thaumicenergistics.client.LegacyModels.gearboxes > 0 && dev.eugheist.thaumicenergistics.client.LegacyModels.assemblers > 0, "original gearbox and arcane assembler models rendered (" + dev.eugheist.thaumicenergistics.client.LegacyModels.gearboxes + "/" + dev.eugheist.thaumicenergistics.client.LegacyModels.assemblers + ")");
            ThEAudit.shot(mc, "legacy-models.png");
        }
        if (ticks == 495) server.execute(() -> run(() -> terminal(server.getPlayerList().getPlayer(id))));
        if (ticks == 515) {
            check(mc.screen instanceof dev.eugheist.thaumicenergistics.client.EssentiaTermScreen, "essentia terminal part opens the original terminal GUI (" + mc.screen + ")");
            var menu = (dev.eugheist.thaumicenergistics.menu.EssentiaTermMenu) mc.player.containerMenu;
            check(menu.entries.stream().anyMatch(e -> e.getAspect() == Aspect.FIRE && e.amount() > 0) && menu.entries.stream().anyMatch(e -> e.getAspect() == Aspect.WATER), "terminal lists the network's ignis and aqua (" + menu.entries + ")");
            ThEAudit.shot(mc, "essentia-terminal.png");
            menu.send(0, Aspect.FIRE, false);
        }
        if (ticks == 525) {
            var menu = (dev.eugheist.thaumicenergistics.menu.EssentiaTermMenu) mc.player.containerMenu;
            check(menu.selected == Aspect.FIRE, "clicking ignis selects it");
            server.execute(() -> run(() -> { fireBefore = fire(server);var sm = (dev.eugheist.thaumicenergistics.menu.EssentiaTermMenu) server.getPlayerList().getPlayer(id).containerMenu; sm.slots.get(0).set(item("tc4modern:phial").copyWithCount(2)); }));
        }
        if (ticks == 545) server.execute(() -> run(() -> {
            var sp = server.getPlayerList().getPlayer(id);var sm = (dev.eugheist.thaumicenergistics.menu.EssentiaTermMenu) sp.containerMenu;
            var out = sm.slots.get(1).getItem();var aspects = dev.eugheist.tc4modern.EssentiaItems.getAspects(out);
            check(sm.slots.get(0).getItem().isEmpty() && out.getCount() == 2 && aspects != null && aspects.getAmount(Aspect.FIRE) == 8 && fire(server) == fireBefore - 16, "work slots fill 2 phials with 8 ignis each from the network (" + out + ", ignis " + fireBefore + " -> " + fire(server) + ")");
            long aqua = storage(sp.serverLevel()).extract(EssentiaKey.of(Aspect.WATER), 100000, Actionable.SIMULATE, IActionSource.empty());
            sm.setCarried(item("tc4modern:warded_jar"));
            invokeAction(sm, sp, 1, Aspect.WATER);
            var jar = sm.getCarried();var ja = dev.eugheist.tc4modern.EssentiaItems.getAspects(jar);
            check(ja != null && ja.getAmount(Aspect.WATER) == Math.min(64, aqua), "clicking aqua with a held empty jar fills it with the network's aqua (" + (ja == null ? 0 : ja.getAmount(Aspect.WATER)) + " of " + aqua + ")");
            invokeAction(sm, sp, 1, Aspect.WATER);
            check(dev.eugheist.tc4modern.EssentiaItems.getAspects(sm.getCarried()) == null || dev.eugheist.tc4modern.EssentiaItems.getAspects(sm.getCarried()).size() == 0, "clicking again with the full jar empties it back into the network");
            sm.setCarried(ItemStack.EMPTY);
            invokeAction(sm, sp, 3, null);
        }));
        if (ticks == 555) {
            var menu = (dev.eugheist.thaumicenergistics.menu.EssentiaTermMenu) mc.player.containerMenu;
            check(menu.sortMode == dev.eugheist.thaumicenergistics.menu.EssentiaTermMenu.SORT_AMOUNT, "sorting button switches to amount order");
            ThEAudit.shot(mc, "essentia-terminal-selected.png");
            mc.player.closeContainer();
        }
        if (ticks == 560) server.execute(() -> run(() -> wireless(server.getPlayerList().getPlayer(id))));
        if (ticks == 575) {
            check(mc.screen instanceof dev.eugheist.thaumicenergistics.client.EssentiaTermScreen && ((dev.eugheist.thaumicenergistics.menu.EssentiaTermMenu) mc.player.containerMenu).entries.stream().anyMatch(e -> e.getAspect() == Aspect.FIRE), "wireless essentia terminal opens the same GUI with the network list");
            ThEAudit.shot(mc, "wireless-essentia-terminal.png");
            largeFonts(true);
            dev.eugheist.thaumicenergistics.client.EssentiaTermScreen.largeLabels = 0;
        }
        if (ticks == 578) {
            check(dev.eugheist.thaumicenergistics.client.EssentiaTermScreen.largeLabels > 0, "AE2 large terminal font draws the essentia amounts with AE2's large labels (" + dev.eugheist.thaumicenergistics.client.EssentiaTermScreen.largeLabels + ")");
            ThEAudit.shot(mc, "essentia-terminal-large-font.png");
            largeFonts(false);
            mc.player.closeContainer();
        }
        if (ticks == 582) {
            var problems = new java.util.ArrayList<String>();int total = 0;
            for (var cat : ResearchCategories.researchCategories.values()) for (var r : cat.research.values()) {
                total++;var problem = dev.eugheist.tc4modern.ThaumonomiconScreen.researchIconProblem(mc, r);
                if (problem != null) problems.add(r.key + ": " + problem);
            }
            check(problems.isEmpty(), "every research of every tab has a visible book icon (" + total + " checked) " + problems);
            var inv = mc.player.getInventory();
            var book = new dev.eugheist.tc4modern.ThaumonomiconScreen(new dev.eugheist.tc4modern.ThaumonomiconMenu(0, inv), inv, net.minecraft.network.chat.Component.empty());
            mc.setScreen(book);book.auditBrowser(ThEResearch.TAB, 0, -2);
        }
        if (ticks == 588) ThEAudit.shot(mc, "thaumonomicon-te-tab-top.png");
        if (ticks == 590) ((dev.eugheist.tc4modern.ThaumonomiconScreen) mc.screen).auditBrowser(ThEResearch.TAB, -3, 0);
        if (ticks == 596) { ThEAudit.shot(mc, "thaumonomicon-te-tab-left.png");mc.setScreen(null); }
        if (ticks == 600) server.execute(() -> run(() -> wirelessArcane(server.getPlayerList().getPlayer(id))));
        // AE2 "open wireless terminal" hotkey with the terminal somewhere in the inventory.
        if (ticks == 610) server.execute(() -> run(() -> new appeng.core.network.serverbound.HotkeyPacket("wireless_terminal").handleOnServer(server.getPlayerList().getPlayer(id))));
        if (ticks == 625) {
            check(mc.screen instanceof dev.eugheist.thaumicenergistics.client.ArcaneTermScreen && mc.player.containerMenu.getType() == dev.eugheist.thaumicenergistics.menu.ArcaneTermMenu.WIRELESS_TYPE, "AE2 wireless terminal hotkey opens the wireless arcane crafting terminal from the inventory (" + mc.screen + ")");
            ThEAudit.shot(mc, "wireless-arcane-terminal.png");
            server.execute(() -> run(() -> {
                var p = server.getPlayerList().getPlayer(id);var menu = (dev.eugheist.thaumicenergistics.menu.ArcaneTermMenu) p.containerMenu;
                var storage = menu.getGridNode().getGrid().getStorageService().getInventory();
                storage.insert(AEItemKey.of(Items.COBBLESTONE), 8, Actionable.MODULATE, appeng.api.networking.security.IActionSource.ofPlayer(p));
                var templates = net.minecraft.core.NonNullList.withSize(9, ItemStack.EMPTY);for (int i : new int[]{0, 1, 2, 3, 5, 6, 7, 8}) templates.set(i, new ItemStack(Items.COBBLESTONE));
                new appeng.core.network.serverbound.FillCraftingGridFromRecipePacket(null, templates, false).handleOnServer(p);
                check(menu.getCraftingMatrix().getStackInSlot(8).is(Items.COBBLESTONE) && menu.slots.stream().anyMatch(sl -> sl.getItem().is(Items.FURNACE)), "JEI fill pulls 8 cobblestone from the network into the wireless grid, furnace shown as result");
                var wand = dev.eugheist.tc4modern.WandParts.make(dev.eugheist.tc4modern.WandParts.CAPS.get("gold"), dev.eugheist.tc4modern.WandParts.RODDEFS.get("greatwood"));
                ((dev.eugheist.thaumicenergistics.menu.ArcaneTermHost) menu.getTarget()).wand().setItemDirect(0, wand);
                var stack = p.getInventory().getItem(20);
                var grid = stack.get(appeng.api.ids.AEComponents.CRAFTING_INV);var wandInv = stack.get(ThaumicEnergistics.ARCANE_WAND_INV.get());
                check(grid != null && grid.getStackInSlot(0).is(Items.COBBLESTONE) && wandInv != null && dev.eugheist.tc4modern.WandData.isWand(wandInv.getStackInSlot(0)), "grid and wand are stored on the wireless terminal item");
            }));
        }
        if (ticks == 720) server.execute(() -> run(() -> {
            var p = server.getPlayerList().getPlayer(id);var menu = (dev.eugheist.thaumicenergistics.menu.ArcaneTermMenu) p.containerMenu;
            var w = ((dev.eugheist.thaumicenergistics.menu.ArcaneTermHost) menu.getTarget()).wand().getStackInSlot(0);
            check(Aspect.getPrimalAspects().stream().allMatch(a -> dev.eugheist.tc4modern.WandData.get(w, a) > 0), "open wireless arcane terminal recharges its wand from the network's vis relay interface (" + Aspect.getPrimalAspects().stream().map(a -> a.getTag() + "=" + dev.eugheist.tc4modern.WandData.get(w, a)).toList() + ")");
            p.closeContainer();
        }));
        if (ticks == 730) server.execute(() -> run(() -> {
            var p = server.getPlayerList().getPlayer(id);var l = p.serverLevel();
            var term = p.getInventory().getItem(20);
            check(dev.eugheist.tc4modern.BaublesInventory.fits(1, term, p) && dev.eugheist.tc4modern.BaublesInventory.fits(3, term, p), "wireless terminal fits any Baubles slot (ring, belt)");
            var card = dev.eugheist.thaumicenergistics.item.ArcaneMagnet.card();
            check(card != null, "ae2wtlib magnet card present");
            check(ThaumicEnergistics.WIRELESS_ARCANE_TERMINAL.get().getUpgrades(term).addItems(new ItemStack(card)).isEmpty(), "magnet card goes into the wireless arcane terminal's upgrades");
            dev.eugheist.thaumicenergistics.item.ArcaneMagnet.mode(term, dev.eugheist.thaumicenergistics.item.ArcaneMagnet.NETWORK);
            dev.eugheist.tc4modern.BaublesInventory.of(p).setItem(1, term.copy());p.getInventory().setItem(20, ItemStack.EMPTY);
            var drop = new net.minecraft.world.entity.item.ItemEntity(l, p.getX() + 6, p.getY() + 1, p.getZ(), new ItemStack(Items.DIAMOND, 3));drop.setNoPickUpDelay();l.addFreshEntity(drop);
        }));
        if (ticks == 750) server.execute(() -> run(() -> {
            var p = server.getPlayerList().getPlayer(id);
            var worn = dev.eugheist.tc4modern.BaublesInventory.of(p).getItem(1);
            var grid = ThaumicEnergistics.WIRELESS_ARCANE_TERMINAL.get().getLinkedGrid(worn, p.level(), null);
            long diamonds = grid.getStorageService().getInventory().getAvailableStacks().get(AEItemKey.of(Items.DIAMOND));
            check(diamonds >= 3 && p.getInventory().countItem(Items.DIAMOND) == 0, "magnet from the worn terminal pulls dropped diamonds into the ME network (" + diamonds + ")");
            new appeng.core.network.serverbound.HotkeyPacket("wireless_terminal").handleOnServer(p);
        }));
        if (ticks == 765) {
            check(mc.screen instanceof dev.eugheist.thaumicenergistics.client.ArcaneTermScreen, "wireless terminal key opens the terminal worn in a Baubles slot (" + mc.screen + ")");
            ThEAudit.shot(mc, "wireless-arcane-terminal-bauble.png");
        }
        if (ticks == 770) server.execute(() -> run(() -> { var p = server.getPlayerList().getPlayer(id);var m = (dev.eugheist.thaumicenergistics.menu.ArcaneTermMenu) p.containerMenu;
            check(!m.magnetTerminal().isEmpty(), "terminal GUI sees the magnet card");m.cycleMagnet();
            check(dev.eugheist.thaumicenergistics.item.ArcaneMagnet.mode(dev.eugheist.tc4modern.BaublesInventory.of(p).getItem(1)) == dev.eugheist.thaumicenergistics.item.ArcaneMagnet.OFF, "magnet button cycles network -> off on the worn terminal");p.closeContainer(); }));
        // Dimension trip with the terminal worn: the client rebuilds its player and must get the Baubles slots again.
        if (ticks == 780) server.execute(() -> run(() -> { var p = server.getPlayerList().getPlayer(id);var nether = server.getLevel(net.minecraft.world.level.Level.NETHER);p.teleportTo(nether, 0.5, 100, 0.5, 0, 0); }));
        if (ticks == 830) server.execute(() -> run(() -> { var p = server.getPlayerList().getPlayer(id);p.teleportTo(server.overworld(), BASE.getX() + .5, BASE.getY(), BASE.getZ() + 3.5, 0, 10); }));
        if (ticks == 880) {
            check(!dev.eugheist.tc4modern.BaublesInventory.of(mc.player).getItem(1).isEmpty(), "after the Nether and back the client still sees the worn terminal");
            server.execute(() -> run(() -> new appeng.core.network.serverbound.HotkeyPacket("wireless_terminal").handleOnServer(server.getPlayerList().getPlayer(id))));
        }
        if (ticks == 895) { check(mc.screen instanceof dev.eugheist.thaumicenergistics.client.ArcaneTermScreen && mc.getConnection() != null, "worn terminal opens after a dimension trip (" + mc.screen + ")"); mc.player.closeContainer(); }
        if (ticks == 900) { System.out.println("THE_AUDIT ALL PASS"); mc.stop(); }
    }
    static void largeFonts(boolean on) {
        try {
            var f = appeng.core.AEConfig.class.getDeclaredField("client");f.setAccessible(true);var client = f.get(appeng.core.AEConfig.instance());
            var v = client.getClass().getDeclaredField("useLargeFonts");v.setAccessible(true);((net.neoforged.neoforge.common.ModConfigSpec.BooleanValue) v.get(client)).set(on);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    static void run(Runnable r) { try { r.run(); } catch (RuntimeException e) { e.printStackTrace(); throw e; } }

    static StrawGolem golem(ServerLevel l, ServerPlayer p, BlockPos home, Direction face, net.minecraft.world.item.Item core, ItemStack backpack) {
        var g = Golemancy.ENTITY.get().create(l);
        g.moveTo(home.getX() + .5, home.getY(), home.getZ() + .5, 0, 0);
        g.home = home;g.homeFace = face;
        l.addFreshEntity(g);g.paused = true;
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(core));p.interactOn(g, InteractionHand.MAIN_HAND);
        if (!backpack.isEmpty()) { p.setItemInHand(InteractionHand.MAIN_HAND, backpack);p.interactOn(g, InteractionHand.MAIN_HAND); }
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return g;
    }
    static ItemStack linked(ServerLevel l) { var b = new ItemStack(ThaumicEnergistics.GOLEM_WIFI_BACKPACK.get());GolemBackpackItem.LINKABLE_HANDLER.link(b, GlobalPos.of(l.dimension(), WAP));return b; }
    static appeng.api.storage.MEStorage storage(ServerLevel l) { return ((DriveBlockEntity) l.getBlockEntity(DRIVE)).getMainNode().getGrid().getStorageService().getInventory(); }

    static void setup(ServerPlayer p) {
        var l = p.serverLevel();
        l.getEntitiesOfClass(Entity.class, new AABB(BASE).inflate(70), e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(Entity::discard);
        for (int x = -4; x <= 14; x++) for (int z = -4; z <= 10; z++) for (int y = -1; y < 5; y++) l.setBlock(BASE.offset(x, y, z), (y == -1 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) { l.setBlock(FAR.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3); l.setBlock(FAR.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 3); l.setBlock(FAR.offset(x, 1, z), Blocks.AIR.defaultBlockState(), 3); }
        l.getEntitiesOfClass(Entity.class, new AABB(BASE).inflate(70), e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(Entity::discard);
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.getInventory().clearContent();
        check(ResearchCategories.getResearch(ThEResearch.GOLEMWIFI) != null, "research TEGOLEMWIFIBACKPACK");
        check(dev.eugheist.tc4modern.api.TC4Addons.arcaneRecipes().stream().anyMatch(r -> r.key().equals("ThEGolemWifiBackpack")), "backpack arcane recipe (TIT/NWN/FCF)");
        check(appeng.api.features.GridLinkables.get(ThaumicEnergistics.GOLEM_WIFI_BACKPACK.get()) != null, "backpack links in access points");
        // Network: creative energy, drive with item/fluid/essentia cells, wireless access point.
        l.setBlock(BASE, block("ae2:creative_energy_cell"), 3);
        l.setBlock(DRIVE, block("ae2:drive"), 3);
        var wap = block("ae2:wireless_access_point");l.setBlock(WAP, appeng.api.orientation.IOrientationStrategy.get(wap).setFacing(wap, Direction.UP), 3);
        var items = item("ae2:item_storage_cell_4k");var src = IActionSource.empty();
        StorageCells.getCellInventory(items, null).insert(AEItemKey.of(Items.IRON_INGOT), 64, Actionable.MODULATE, src);
        var fluids = item("ae2:fluid_storage_cell_4k");
        StorageCells.getCellInventory(fluids, null).insert(AEFluidKey.of(Fluids.WATER), 8000, Actionable.MODULATE, src);
        var ess = ThEAudit.cell(EssentiaStorageType.TYPE_1K);
        StorageCells.getCellInventory(ess, null).insert(EssentiaKey.of(Aspect.FIRE), 64, Actionable.MODULATE, src);
        var drive = (DriveBlockEntity) l.getBlockEntity(DRIVE);
        drive.getInternalInventory().setItemDirect(0, items);drive.getInternalInventory().setItemDirect(1, fluids);drive.getInternalInventory().setItemDirect(2, ess);
        // Homes.
        l.setBlock(FILL_CHEST, Blocks.CHEST.defaultBlockState(), 3);
        l.setBlock(SPA, dev.eugheist.tc4modern.Hygiene.SPA.get().defaultBlockState(), 3);l.setBlock(SPA.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        l.setBlock(JAR, block("tc4modern:warded_jar"), 3);((WardedJarEntity) l.getBlockEntity(JAR)).state.aspectFilter = Aspect.FIRE;
        l.setBlock(SOURCE_JAR, block("tc4modern:warded_jar"), 3);var source = (WardedJarEntity) l.getBlockEntity(SOURCE_JAR);source.state.aspect = Aspect.WATER;source.state.amount = 20;
        // Golems.
        unlinked = golem(l, p, BASE.offset(-2, 0, 8), Direction.UP, Golemancy.GATHER.get(), new ItemStack(ThaumicEnergistics.GOLEM_WIFI_BACKPACK.get()));
        check(WifiGolems.data(unlinked) == null, "an unlinked backpack is refused");
        var notSupported = golem(l, p, BASE.offset(-3, 0, 8), Direction.UP, Golemancy.GUARD.get(), linked(l));
        check(WifiGolems.data(notSupported) == null && !p.getMainHandItem().is(ThaumicEnergistics.GOLEM_WIFI_BACKPACK.get()), "guard golems take no backpack");
        p.closeContainer();notSupported.discard();
        gather = golem(l, p, GATHER_HOME, Direction.UP, Golemancy.GATHER.get(), linked(l));
        check(WifiGolems.data(gather) != null, "linked backpack fitted to a gather golem");
        fill = golem(l, p, FILL_CHEST.north(), Direction.NORTH, Golemancy.FILL.get(), linked(l));
        fill.filters.setItem(0, new ItemStack(Items.IRON_INGOT, 16));
        liquid = golem(l, p, SPA.north(), Direction.NORTH, Golemancy.LIQUID.get(), linked(l));
        liquid.filters.setItem(0, new ItemStack(Items.WATER_BUCKET));
        essentia = golem(l, p, JAR.offset(0, 0, -2), Direction.UP, Golemancy.ESSENTIA.get(), linked(l));
        var mark = new CompoundTag();mark.putLong("pos", JAR.asLong());mark.putByte("side", (byte) 1);mark.putByte("color", (byte) -1);essentia.markers.add(mark);
        unloader = golem(l, p, SOURCE_JAR.above(), Direction.UP, Golemancy.ESSENTIA.get(), linked(l));
        far = golem(l, p, FAR, Direction.UP, Golemancy.GATHER.get(), linked(l));
        // Facade skin.
        p.setItemInHand(InteractionHand.MAIN_HAND, appeng.core.definitions.AEItems.FACADE.get().createFacadeForItem(new ItemStack(Items.IRON_BLOCK), false));
        p.interactOn(gather, InteractionHand.MAIN_HAND);p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(WifiGolems.data(gather).getByte("skin") == WifiGolems.Skin.IRON.ordinal() && WifiGolems.data(gather).contains("facade"), "iron block facade gives the iron backpack skin");
        p.connection.teleport(BASE.getX() + 4.5, BASE.getY() + 3, BASE.getZ() - 3, 0, 40);
    }

    /** One AIAENetworkGolem step per core, called directly. */
    static void direct(ServerPlayer p) {
        var l = p.serverLevel();
        var wapBe = (appeng.blockentity.networking.WirelessAccessPointBlockEntity) l.getBlockEntity(WAP);var node = wapBe.getMainNode();
        check(wapBe.isActive(), "access point active on the ad-hoc network (state " + l.getBlockState(WAP) + ", powered " + node.isPowered() + ", online " + node.isOnline() + ", grid " + (node.getGrid() == null ? "none" : node.getGrid().size() + " nodes") + ", sides " + (node.getNode() == null ? "?" : node.getNode().getConnectedSides()) + ")");
        for (var g : new StrawGolem[]{gather, fill, liquid, essentia, unloader, far, unlinked}) g.paused = false;
        var storage = storage(l);var src = IActionSource.empty();
        // Gather: carried items go into the network, 8 per step without order upgrades.
        gather.carried(new ItemStack(Items.COBBLESTONE, 20));
        var goal = new WifiGolems.NetworkGoal(gather);
        check(goal.canUse(), "gather golem in range wants the network");goal.start();
        check(gather.carried().getCount() == 12 && storage.extract(AEItemKey.of(Items.COBBLESTONE), 64, Actionable.SIMULATE, src) == 8, "gather golem deposits 8 items per step (" + gather.carried() + ")");
        check(WifiGolems.data(gather).getBoolean("range"), "in-range flag synced on the golem");
        // Fill: the chest lacks 16 iron, the golem pulls 8 from the network.
        goal = new WifiGolems.NetworkGoal(fill);
        check(goal.canUse(), "fill golem wants iron for its chest");goal.start();
        check(fill.carried().is(Items.IRON_INGOT) && fill.carried().getCount() == Math.min(8, fill.carryLimit()), "fill golem takes min(8, carry limit) iron from the network (" + fill.carried() + ")");
        // Liquid: one water filter, spa takes water -> 100 mB per step.
        goal = new WifiGolems.NetworkGoal(liquid);
        check(goal.canUse(), "liquid golem wants water for its spa");goal.start();
        check(liquid.fluidCarried().is(Fluids.WATER) && liquid.fluidCarried().getAmount() == 100, "liquid golem takes 100 mB water (" + liquid.fluidCarried().getAmount() + ")");
        // Essentia: home is no essentia source -> fill the marked ignis jar from the network.
        goal = new WifiGolems.NetworkGoal(essentia);
        check(goal.canUse(), "essentia golem finds the labelled ignis jar");goal.start();
        check(essentia.essentia() == Aspect.FIRE && essentia.essentiaAmount() == 4, "essentia golem takes 4 ignis from the network (" + essentia.essentiaAmount() + ")");
        // Essentia unloading: home is a jar -> carried essentia goes into the network when idle.
        unloader.essentia(Aspect.WATER, 10);unloader.getNavigation().stop();
        goal = new WifiGolems.NetworkGoal(unloader);
        check(goal.canUse(), "essentia golem at a source unloads into the network");goal.start();
        check(unloader.essentiaAmount() == 6 && storage.extract(EssentiaKey.of(Aspect.WATER), 64, Actionable.SIMULATE, src) == 4, "4 aqua unloaded into the network");
        // Out of range.
        goal = new WifiGolems.NetworkGoal(far);far.carried(new ItemStack(Items.DIRT, 4));
        check(!goal.canUse() && !WifiGolems.data(far).getBoolean("range") && far.carried().getCount() == 4, "a golem 60 blocks away is out of range");
        // Live AI: let the golems run.
        cobbleBefore = storage.extract(AEItemKey.of(Items.COBBLESTONE), 640, Actionable.SIMULATE, src);
        var drop = new ItemEntity(l, GATHER_HOME.getX() + 1.5, GATHER_HOME.getY(), GATHER_HOME.getZ() + .5, new ItemStack(Items.REDSTONE, 5));drop.setPickUpDelay(0);l.addFreshEntity(drop);
    }

    static void live(ServerPlayer p) {
        var l = p.serverLevel();var storage = storage(l);var src = IActionSource.empty();
        System.out.println("THE_AUDIT debug gather carried " + gather.carried() + " pos " + gather.blockPosition() + " drops " + l.getEntitiesOfClass(ItemEntity.class, new AABB(GATHER_HOME).inflate(8)).stream().map(e -> e.getItem() + "@" + e.blockPosition()).toList());
        long cobble = storage.extract(AEItemKey.of(Items.COBBLESTONE), 640, Actionable.SIMULATE, src), redstone = storage.extract(AEItemKey.of(Items.REDSTONE), 64, Actionable.SIMULATE, src);
        long other = unlinked.carried().is(Items.REDSTONE) ? unlinked.carried().getCount() : 0;
        check(gather.carried().isEmpty() && cobble == 20 && redstone >= 3 && redstone + other == 5, "live gather golem: carried load and picked-up redstone end in the network (cobble " + cobble + ", redstone " + redstone + ", unlinked golem holds " + other + ")");
        var chest = l.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, FILL_CHEST, Direction.NORTH);int iron = 0;
        for (int i = 0; i < chest.getSlots(); i++) if (chest.getStackInSlot(i).is(Items.IRON_INGOT)) iron += chest.getStackInSlot(i).getCount();
        check(iron >= Math.min(16, 3 * fill.carryLimit()), "live fill golem keeps stocking its chest with iron from the network (" + iron + ")");
        var spa = (dev.eugheist.tc4modern.SpaEntity) l.getBlockEntity(SPA);
        check(spa.tank.getFluidAmount() >= 500 && spa.tank.getFluid().is(Fluids.WATER), "live liquid golem filled the spa from the network (" + spa.tank.getFluidAmount() + " mB)");
        var jar = (WardedJarEntity) l.getBlockEntity(JAR);
        check(jar.state.amount >= 8 && jar.state.aspect == Aspect.FIRE, "live essentia golem filled the ignis jar from the network (" + jar.state.amount + ")");
    }

    /** Two motionless golems with their backs to the camera: thaumium (default) and gold facade skins, plus one far golem. */
    static void showcase(ServerPlayer p) {
        var l = p.serverLevel();var at = BASE.offset(4, 0, -9);
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 4; z++) { l.setBlock(at.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3); for (int y = 0; y < 3; y++) l.setBlock(at.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3); }
        var a = golem(l, p, at, Direction.UP, Golemancy.GATHER.get(), linked(l));
        var b = golem(l, p, at.east(), Direction.UP, Golemancy.FILL.get(), linked(l));
        p.setItemInHand(InteractionHand.MAIN_HAND, appeng.core.definitions.AEItems.FACADE.get().createFacadeForItem(new ItemStack(Items.GOLD_BLOCK), false));p.interactOn(b, InteractionHand.MAIN_HAND);p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        for (var g : new StrawGolem[]{a, b}) { g.paused = false;g.setNoAi(true);g.setYRot(160);g.setYBodyRot(160);g.setYHeadRot(160);g.moveTo(g.getX() - .5, g.getY(), g.getZ(), 160, 0); var w = WifiGolems.data(g); }
        new WifiGolems.NetworkGoal(a).canUse();new WifiGolems.NetworkGoal(b).canUse();
        p.connection.teleport(at.getX() + 1, at.getY() + .2, at.getZ() + 2.2, 180, 15);
    }

    static final BlockPos MODELS = BASE.offset(4, 0, -16);
    /** Iron gearbox with a charger on its east side (no gear there), thaumium gearbox, arcane assembler; the items in the hotbar. */
    static void models(ServerPlayer p) {
        var l = p.serverLevel();
        for (int x = -3; x <= 5; x++) for (int z = -2; z <= 4; z++) { l.setBlock(MODELS.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3); for (int y = 0; y < 3; y++) l.setBlock(MODELS.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3); }
        l.setBlock(MODELS, ThaumicEnergistics.IRON_GEARBOX.get().defaultBlockState(), 3);
        l.setBlock(MODELS.east(), block("ae2:charger"), 3);
        ((appeng.blockentity.misc.ChargerBlockEntity) l.getBlockEntity(MODELS.east())).getInternalInventory().setItemDirect(0, item("ae2:certus_quartz_crystal"));
        l.setBlock(MODELS.west(2), ThaumicEnergistics.THAUMIUM_GEARBOX.get().defaultBlockState(), 3);
        l.setBlock(MODELS.east(3), ThaumicEnergistics.ARCANE_ASSEMBLER.get().defaultBlockState(), 3);
        ThaumicEnergistics.IRON_GEARBOX.get().crank(l, MODELS);
        p.getInventory().clearContent();
        p.getInventory().setItem(0, new ItemStack(ThaumicEnergistics.IRON_GEARBOX_ITEM.get()));p.getInventory().setItem(1, new ItemStack(ThaumicEnergistics.THAUMIUM_GEARBOX_ITEM.get()));
        p.getInventory().setItem(2, new ItemStack(ThaumicEnergistics.ARCANE_ASSEMBLER_ITEM.get()));p.getInventory().setItem(3, new ItemStack(ThaumicEnergistics.GOLEM_WIFI_BACKPACK.get()));
        p.getInventory().selected = 0;
        p.connection.teleport(MODELS.getX() + 2, MODELS.getY() + .8, MODELS.getZ() + 3.6, 180, 20);
    }

    static long fireBefore;
    static final BlockPos TERMINAL = BASE.west();
    static long fire(net.minecraft.server.MinecraftServer server) { return storage(server.overworld()).extract(EssentiaKey.of(Aspect.FIRE), 100000, Actionable.SIMULATE, IActionSource.empty()); }
    static void invokeAction(dev.eugheist.thaumicenergistics.menu.EssentiaTermMenu menu, ServerPlayer p, int action, Aspect aspect) {
        try { var m = dev.eugheist.thaumicenergistics.menu.EssentiaTermMenu.class.getDeclaredMethod("action", ServerPlayer.class, int.class, Aspect.class, boolean.class); m.setAccessible(true); m.invoke(menu, p, action, aspect, false); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    /** A cable on the creative energy cell with the essentia terminal on its west face. */
    static void terminal(ServerPlayer p) {
        var l = p.serverLevel();
        l.getEntitiesOfClass(StrawGolem.class, new AABB(BASE).inflate(70)).forEach(Entity::discard);
        l.setBlock(TERMINAL, Blocks.AIR.defaultBlockState(), 3);
        appeng.api.parts.PartHelper.setPart(l, TERMINAL, null, p, (appeng.api.parts.IPartItem<?>) BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable")));
        appeng.api.parts.PartHelper.setPart(l, TERMINAL, Direction.WEST, p, (appeng.api.parts.IPartItem<?>) ThaumicEnergistics.ESSENTIA_TERMINAL.get());
        p.connection.teleport(TERMINAL.getX() - 1.5, TERMINAL.getY(), TERMINAL.getZ() + .5, -90, 10);
        var host = (appeng.api.parts.IPartHost) l.getBlockEntity(TERMINAL);
        check(host != null && host.getPart(Direction.WEST) instanceof dev.eugheist.thaumicenergistics.part.EssentiaTerminalPart, "essentia terminal part placed");
        server(p).execute(() -> run(() -> host.getPart(Direction.WEST).onUseWithoutItem(p, TERMINAL.getCenter())));
    }
    static net.minecraft.server.MinecraftServer server(ServerPlayer p) { return p.getServer(); }
    static void wireless(ServerPlayer p) {
        var l = p.serverLevel();
        var term = new ItemStack(ThaumicEnergistics.WIRELESS_ESSENTIA_TERMINAL.get());
        appeng.items.tools.powered.WirelessTerminalItem.LINKABLE_HANDLER.link(term, GlobalPos.of(l.dimension(), WAP));
        ThaumicEnergistics.WIRELESS_ESSENTIA_TERMINAL.get().injectAEPower(term, 1_000_000, Actionable.MODULATE);
        p.getInventory().clearContent();p.setItemInHand(InteractionHand.MAIN_HAND, term);
        p.connection.teleport(BASE.getX() + .5, BASE.getY(), BASE.getZ() + 3.5, 0, 10);
        server(p).execute(() -> run(() -> term.getItem().use(l, p, InteractionHand.MAIN_HAND)));
    }

    static void wirelessArcane(ServerPlayer p) {
        var l = p.serverLevel();
        // A vis relay interface on the same network, fed by an energized node through a vis relay.
        appeng.api.parts.PartHelper.setPart(l, TERMINAL, Direction.UP, p, (appeng.api.parts.IPartItem<?>) ThaumicEnergistics.VIS_INTERFACE.get());
        l.setBlock(TERMINAL.above(), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("tc4modern:vis_relay")).defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.UP), 3);
        var nodePos = TERMINAL.above().offset(0, 3, -4);
        l.setBlock(nodePos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("tc4modern:energized_node")).defaultBlockState(), 3);
        var node = (dev.eugheist.tc4modern.EnergizedNodeEntity) l.getBlockEntity(nodePos);
        for (var a : Aspect.getPrimalAspects()) node.auraBase.add(a, 64);
        node.energy.setup(node.auraBase, node.nodeType, node.modifier, new java.util.Random(0));node.changed();
        var term = new ItemStack(ThaumicEnergistics.WIRELESS_ARCANE_TERMINAL.get());
        check(appeng.api.features.GridLinkables.get(term.getItem()) != null, "wireless arcane terminal links in access points");
        appeng.items.tools.powered.WirelessTerminalItem.LINKABLE_HANDLER.link(term, GlobalPos.of(l.dimension(), WAP));
        ThaumicEnergistics.WIRELESS_ARCANE_TERMINAL.get().injectAEPower(term, 1_000_000, Actionable.MODULATE);
        p.getInventory().clearContent();dev.eugheist.tc4modern.BaublesInventory.of(p).clearContent();p.getInventory().setItem(20, term);
        p.connection.teleport(BASE.getX() + .5, BASE.getY(), BASE.getZ() + 3.5, 0, 10);
    }

    static void bell(ServerPlayer p) {
        var l = p.serverLevel();
        var area = new AABB(gather.blockPosition()).inflate(3);
        l.getEntitiesOfClass(ItemEntity.class, area).forEach(Entity::discard);
        var bell = new ItemStack(Golemancy.BELL.get());
        bell.getItem().onLeftClickEntity(bell, p, gather);
        var drops = l.getEntitiesOfClass(ItemEntity.class, area);
        var pack = drops.stream().map(ItemEntity::getItem).filter(s -> s.is(ThaumicEnergistics.GOLEM_WIFI_BACKPACK.get())).findFirst();
        check(gather.isRemoved() && pack.isPresent() && GolemBackpackItem.link(pack.get()) != null, "golem bell drops the still-linked backpack");
        check(drops.stream().anyMatch(e -> e.getItem().getItem() instanceof appeng.items.parts.FacadeItem), "golem bell drops the backpack facade");
    }
}
