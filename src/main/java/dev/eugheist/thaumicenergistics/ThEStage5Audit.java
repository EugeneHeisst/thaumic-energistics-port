package dev.eugheist.thaumicenergistics;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.research.ResearchCategories;
import dev.eugheist.thaumicenergistics.block.VibrationChamberEntity;

/** Hidden stage 5 audit (-Dthaumicenergistics.stage5=true): E.V.C., gearboxes, wrench focus, quartz dupe, facades, wireless terminal. */
@EventBusSubscriber(modid = ThaumicEnergistics.ID, value = Dist.CLIENT)
public final class ThEStage5Audit {
    static final BlockPos BASE = new BlockPos(600, 121, -90), EVC = BASE.east(2), CELL = BASE.east(1), GB = BASE.offset(0, 0, 4), CHARGER = GB.east(), TGB = BASE.offset(4, 0, 4), CHARGER2 = TGB.east(), WRENCHED = BASE.offset(-3, 0, 0);
    static int ticks;static double energyBefore;
    static void check(boolean ok, String what) { ThEAudit.check(ok, what); }
    static ItemStack item(String id) { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))); }
    static net.minecraft.world.level.block.state.BlockState block(String id) { return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState(); }

    @SubscribeEvent static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("thaumicenergistics.stage5")) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ticks++;
        var server = mc.getSingleplayerServer();var id = mc.player.getUUID();
        if (ticks == 40) server.execute(() -> { try { setup(server.getPlayerList().getPlayer(id)); } catch (RuntimeException e) { e.printStackTrace(); throw e; } });
        if (ticks == 300) server.execute(() -> { try { verify(server.getPlayerList().getPlayer(id)); } catch (RuntimeException e) { e.printStackTrace(); throw e; } });
        if (ticks == 305) server.execute(() -> { var p = server.getPlayerList().getPlayer(id); p.connection.teleport(EVC.getX() + .5, EVC.getY(), EVC.getZ() - 2, 0, 15); p.openMenu(new net.minecraft.world.SimpleMenuProvider((cid, inv, pl) -> new dev.eugheist.thaumicenergistics.menu.VibrationChamberMenu(cid, inv, (VibrationChamberEntity) p.level().getBlockEntity(EVC)), net.minecraft.network.chat.Component.translatable("block.thaumicenergistics.essentia_vibration_chamber")), buf -> buf.writeBlockPos(EVC)); });
        if (ticks == 320) { check(mc.screen instanceof dev.eugheist.thaumicenergistics.client.VibrationChamberScreen, "E.V.C. GUI opens"); ThEAudit.shot(mc, "vibration-chamber.png"); mc.player.closeContainer(); }
        if (ticks == 330) ThEAudit.shot(mc, "stage5-blocks.png");
        if (ticks == 335) { var inv = mc.player.getInventory(); var book = new dev.eugheist.tc4modern.ThaumonomiconScreen(new dev.eugheist.tc4modern.ThaumonomiconMenu(0, inv), inv, net.minecraft.network.chat.Component.empty()); mc.setScreen(book); ThEAudit.invoke(book, "auditBrowser", ThEResearch.TAB, 0, 0); }
        if (ticks == 345) ThEAudit.shot(mc, "research-tab-stage5.png");
        if (ticks == 350) ThEAudit.invoke(mc.screen, "auditOpen", ResearchCategories.getResearch(ThEResearch.CERTUSDUPE), 0);
        if (ticks == 360) { ThEAudit.shot(mc, "research-certusdupe.png"); mc.setScreen(null); }
        if (ticks == 365) { System.out.println("THE_AUDIT ALL PASS"); mc.stop(); }
    }

    static void setup(ServerPlayer p) {
        var l = p.serverLevel();
        l.getEntitiesOfClass(Entity.class, new AABB(BASE).inflate(14), e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(Entity::discard);
        for (int x = -6; x <= 12; x++) for (int z = -6; z <= 8; z++) for (int y = -1; y < 6; y++) l.setBlock(BASE.offset(x, y, z), (y == -1 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        // Research and recipes.
        for (var k : new String[]{ThEResearch.CERTUSDUPE, ThEResearch.FOCUSWRENCH, ThEResearch.ESSVIBCMBR, ThEResearch.IRONGEARBOX, ThEResearch.THAUMGBOX}) check(ResearchCategories.getResearch(k) != null, "research " + k);
        var dupe = dev.eugheist.tc4modern.CrucibleRecipes.byKey().get("ThECertusDupe");
        check(dupe != null && dupe.getRecipeOutput().getCount() == 2 && dupe.catalystMatches(item("ae2:certus_quartz_crystal")) && dupe.aspects.getAmount(Aspect.CRYSTAL) == 4, "crucible duplicates certus quartz (crystal 4, water 2 -> 2 crystals)");
        check(dev.eugheist.tc4modern.CrucibleRecipes.entries().contains(dupe), "dupe recipe usable by crucibles (entries list)");
        var tgb = dev.eugheist.tc4modern.CrucibleRecipes.byKey().get("ThEThaumiumGearBox");
        check(tgb != null && tgb.catalystMatches(new ItemStack(ThaumicEnergistics.IRON_GEARBOX_ITEM.get())), "thaumium gearbox crucible recipe from the iron gearbox");
        // Facades from Thaumcraft blocks.
        var facade = appeng.core.definitions.AEItems.FACADE.get().createFacadeForItem(item("tc4modern:arcane_stone"), false);
        check(!facade.isEmpty(), "AE2 facade from arcane stone");
        for (var f : new String[]{"tc4modern:thaumium_block", "tc4modern:greatwood_planks", "tc4modern:warded_glass", "tc4modern:amber_block", "tc4modern:obsidian_tile"}) System.out.println("THE_AUDIT facade " + f + " -> " + !appeng.core.definitions.AEItems.FACADE.get().createFacadeForItem(item(f), false).isEmpty());
        // Wireless essentia terminal.
        check(appeng.api.features.GridLinkables.get(ThaumicEnergistics.WIRELESS_ESSENTIA_TERMINAL.get()) != null, "wireless essentia terminal links in access points");
        p.getInventory().clearContent();p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ThaumicEnergistics.WIRELESS_ESSENTIA_TERMINAL.get()));
        var host = ThaumicEnergistics.WIRELESS_ESSENTIA_TERMINAL.get().getMenuHost(p, appeng.menu.locator.MenuLocators.forHand(p, InteractionHand.MAIN_HAND), null);
        check(host != null && host.getKeyTypeSelection().enabledSet().equals(java.util.List.of(dev.eugheist.thaumicenergistics.me.EssentiaKeyType.TYPE)), "wireless terminal shows essentia only");
        p.getInventory().clearContent();
        // E.V.C. on a network with an empty energy cell.
        l.setBlock(CELL, block("ae2:energy_cell"), 3);
        l.setBlock(EVC, ThaumicEnergistics.VIBRATION_CHAMBER.get().defaultBlockState(), 3);
        var evc = (VibrationChamberEntity) l.getBlockEntity(EVC);
        check(evc.insert(Aspect.FIRE, 10, false) == 10 && evc.insert(Aspect.ENERGY, 1, true) == 0 && evc.insert(Aspect.WATER, 1, true) == 0, "E.V.C. stores ignis, refuses other aspects while holding ignis");
        check(dev.eugheist.tc4modern.api.AspectContainers.find(l, EVC) != null, "E.V.C. is an essentia target for export/storage buses");
        // Gearboxes next to chargers holding certus quartz.
        l.setBlock(GB, ThaumicEnergistics.IRON_GEARBOX.get().defaultBlockState(), 3);
        l.setBlock(CHARGER, block("ae2:charger"), 3);
        l.setBlock(TGB, ThaumicEnergistics.THAUMIUM_GEARBOX.get().defaultBlockState(), 3);
        l.setBlock(CHARGER2, block("ae2:charger"), 3);
        for (var c : new BlockPos[]{CHARGER, CHARGER2}) ((appeng.blockentity.misc.ChargerBlockEntity) l.getBlockEntity(c)).getInternalInventory().setItemDirect(0, item("ae2:certus_quartz_crystal"));
        var fake = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(l);
        var hit = new BlockHitResult(GB.getCenter(), Direction.UP, GB, false);
        check(l.getBlockState(GB).useWithoutItem(l, fake, hit) == net.minecraft.world.InteractionResult.PASS, "golems/fake players cannot work the iron gearbox");
        for (int i = 0; i < 90; i++) l.getBlockState(GB).useWithoutItem(l, p, hit);
        var hit2 = new BlockHitResult(TGB.getCenter(), Direction.UP, TGB, false);
        for (int i = 0; i < 90; i++) l.getBlockState(TGB).useWithoutItem(l, fake, hit2);
        // Wrench focus: sneak-use dismantles an AE block.
        l.setBlock(WRENCHED, block("ae2:energy_cell"), 3);
        p.setShiftKeyDown(true);
        boolean popped = dev.eugheist.thaumicenergistics.item.WrenchFocusItem.class.getDeclaredMethods().length > 0 && invokeWrench(l, p, new BlockHitResult(WRENCHED.getCenter(), Direction.UP, WRENCHED, false));
        p.setShiftKeyDown(false);
        check(popped && l.getBlockState(WRENCHED).isAir(), "wrench focus dismantles an AE energy cell");
        energyBefore = 0;
        p.connection.teleport(BASE.getX() + 2.5, BASE.getY(), BASE.getZ() - 4, 0, 15);
    }
    static boolean invokeWrench(net.minecraft.server.level.ServerLevel l, ServerPlayer p, BlockHitResult hit) {
        try { var m = dev.eugheist.thaumicenergistics.item.WrenchFocusItem.class.getDeclaredMethod("useAsWrench", net.minecraft.world.level.Level.class, net.minecraft.world.entity.player.Player.class, BlockHitResult.class); m.setAccessible(true); return (boolean) m.invoke(null, l, p, hit); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    static void verify(ServerPlayer p) {
        var l = p.serverLevel();
        var evc = (VibrationChamberEntity) l.getBlockEntity(EVC);
        var grid = evc.getMainNode().getGrid();
        double stored = grid.getEnergyService().getStoredPower();
        check(evc.storedAmount < 10 && stored > 0, "E.V.C. burned ignis into network power (" + evc.storedAmount + " left, " + (int) stored + " AE stored)");
        for (var c : new BlockPos[]{CHARGER, CHARGER2}) {
            var out = ((appeng.blockentity.misc.ChargerBlockEntity) l.getBlockEntity(c)).getInternalInventory().getStackInSlot(0);
            check(out.is(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:charged_certus_quartz_crystal"))), (c == CHARGER ? "player-cranked iron gearbox" : "golem-cranked thaumium gearbox") + " charged the certus quartz in the charger: " + out);
        }
    }
}
