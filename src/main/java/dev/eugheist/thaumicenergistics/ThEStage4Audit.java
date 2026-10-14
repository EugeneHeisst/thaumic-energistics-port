package dev.eugheist.thaumicenergistics;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.research.ResearchCategories;
import dev.eugheist.tc4modern.WardedJarEntity;
import dev.eugheist.thaumicenergistics.block.EssentiaProviderEntity;
import dev.eugheist.thaumicenergistics.block.InfusionProviderEntity;
import dev.eugheist.thaumicenergistics.me.EssentiaCellInventory;
import dev.eugheist.thaumicenergistics.me.EssentiaKey;
import dev.eugheist.thaumicenergistics.me.EssentiaStorageType;

/** Hidden stage 4 audit (-Dthaumicenergistics.stage4=true): providers and autocrafting blocks on a live network. */
@EventBusSubscriber(modid = ThaumicEnergistics.ID, value = Dist.CLIENT)
public final class ThEStage4Audit {
    static final BlockPos BASE = new BlockPos(600, 121, -90);
    static int ticks;
    static void check(boolean ok, String what) { ThEAudit.check(ok, what); }
    static ItemStack item(String id) { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))); }
    static BlockPos ESS = BASE.east(3).above(2), INF = BASE.west(1), OUT_JAR = BASE.east(3), IN_JAR = BASE.east(5).above(), ESS2 = BASE.east(5).above(2);
    static long stored(ServerLevel l, Aspect a) { var be = (EssentiaProviderEntity) l.getBlockEntity(ESS); return be.getMainNode().getGrid().getStorageService().getCachedInventory().get(EssentiaKey.of(a)); }
    static WardedJarEntity jar(ServerLevel l, BlockPos pos, Aspect a, int amount, Aspect filter) {
        l.setBlock(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("tc4modern:warded_jar")).defaultBlockState(), 3);
        var jar = (WardedJarEntity) l.getBlockEntity(pos);jar.state.aspect = a;jar.state.amount = amount;jar.state.aspectFilter = filter;jar.changed();return jar;
    }

    static final BlockPos INS = BASE.offset(-3, 0, 3), ASM = BASE.east(6), CPU = BASE.east(7), DPE = BASE.offset(-5, 0, 3);
    static ArcaneRecipes_Entry recipe;
    record ArcaneRecipes_Entry(dev.eugheist.tc4modern.ArcaneRecipes.Entry e) {}
    static ItemStack input(int i) { var ing = recipe.e().ingredients().get(i); return ing.isEmpty() ? ItemStack.EMPTY : ing.getItems()[0].copyWithCount(1); }

    static void autocraftSetup(net.minecraft.server.level.ServerPlayer p) {
        var l = p.serverLevel();
        for (var e : dev.eugheist.tc4modern.ArcaneRecipes.entries()) if (e.key().equals("WardedJar")) recipe = new ArcaneRecipes_Entry(e);
        check(recipe != null, "warded jar arcane recipe found");
        dev.eugheist.tc4modern.ResearchProgress.complete(p.getPersistentData(), recipe.e().research());
        dev.eugheist.tc4modern.ResearchKnowledge.grantAllItemScans(p.getPersistentData());dev.eugheist.tc4modern.ResearchKnowledge.grantAll(p.getPersistentData());
        dev.eugheist.tc4modern.ThaumometerItem.sync(p, "");
        // Item cell with the jar's ingredients, crafting CPU and the assembler on the network.
        var cell = item("ae2:item_storage_cell_4k");var inv = StorageCells.getCellInventory(cell, null);
        for (int i = 0; i < 9; i++) { var in = input(i); if (!in.isEmpty()) inv.insert(appeng.api.stacks.AEItemKey.of(in), 64, Actionable.MODULATE, IActionSource.empty()); }
        inv.persist();
        ((DriveBlockEntity) l.getBlockEntity(BASE.east())).getInternalInventory().setItemDirect(1, cell);
        l.setBlock(ASM, ThaumicEnergistics.ARCANE_ASSEMBLER.get().defaultBlockState(), 3);
        l.setBlock(CPU, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:1k_crafting_storage")).defaultBlockState(), 3);
        l.setBlock(INS, ThaumicEnergistics.KNOWLEDGE_INSCRIBER.get().defaultBlockState(), 3);
        l.setBlock(DPE, ThaumicEnergistics.DISTILLATION_ENCODER.get().defaultBlockState(), 3);
        p.connection.teleport(BASE.getX() - 1.5, BASE.getY(), BASE.getZ() + 0.5, 135, 20);
        var ins = (dev.eugheist.thaumicenergistics.block.KnowledgeInscriberEntity) l.getBlockEntity(INS);
        ins.inventory.setItem(0, new ItemStack(ThaumicEnergistics.KNOWLEDGE_CORE.get()));
        p.openMenu(new net.minecraft.world.SimpleMenuProvider((cid, pinv, pl) -> ins.menu(cid, pinv), net.minecraft.network.chat.Component.translatable("block.thaumicenergistics.knowledge_inscriber")), buf -> buf.writeBlockPos(INS));
    }
    static void inscribe(net.minecraft.server.level.ServerPlayer p) {
        var menu = (dev.eugheist.thaumicenergistics.menu.KnowledgeInscriberMenu) p.containerMenu;
        for (int i = 0; i < 9; i++) { menu.setCarried(input(i)); menu.clicked(menu.gridStart + i, 0, net.minecraft.world.inventory.ClickType.PICKUP, p); }
        menu.setCarried(ItemStack.EMPTY);
        check(menu.slots.get(menu.resultSlot).getItem().is(recipe.e().output().getItem()) && menu.saveState() == dev.eugheist.thaumicenergistics.menu.KnowledgeInscriberMenu.SaveState.SAVE, "inscriber recognises the warded jar recipe, ready to save");
        menu.clickMenuButton(p, dev.eugheist.thaumicenergistics.menu.KnowledgeInscriberMenu.BUTTON_SAVE);
        var core = ((dev.eugheist.thaumicenergistics.block.KnowledgeInscriberEntity) p.level().getBlockEntity(INS)).inventory.getItem(0);
        var stored = dev.eugheist.thaumicenergistics.craft.KnowledgeCore.read(core, p.level().registryAccess());
        check(stored.size() == 1 && stored.get(0).output().is(recipe.e().output().getItem()) && menu.saveState() == dev.eugheist.thaumicenergistics.menu.KnowledgeInscriberMenu.SaveState.DELETE, "pattern saved on the knowledge core; button now erases");
        check(!menu.slots.get(menu.storedStart).getItem().isEmpty(), "stored pattern shown in the core list");
        menu.clickMenuButton(p, dev.eugheist.thaumicenergistics.menu.KnowledgeInscriberMenu.BUTTON_CLEAR);
        menu.clicked(menu.storedStart, 0, net.minecraft.world.inventory.ClickType.PICKUP, p);
        check(!menu.slots.get(menu.gridStart + 4).getItem().isEmpty() || !menu.slots.get(menu.gridStart).getItem().isEmpty(), "clicking a stored pattern loads it back into the grid");
        // Move the core into the assembler, with full vis.
        var asm = (dev.eugheist.thaumicenergistics.block.ArcaneAssemblerEntity) p.level().getBlockEntity(ASM);
        asm.core.setItem(0, core.copy());
        for (var pr : dev.eugheist.thaumicenergistics.block.ArcaneAssemblerEntity.PRIMALS) asm.storedVis.add(pr, 1500);
    }
    static long jarsIn(net.minecraft.server.level.ServerPlayer p) {
        var asm = (dev.eugheist.thaumicenergistics.block.ArcaneAssemblerEntity) p.level().getBlockEntity(ASM);
        return asm.getMainNode().getGrid().getStorageService().getInventory().getAvailableStacks().get(appeng.api.stacks.AEItemKey.of(recipe.e().output()));
    }
    static void requestCraft(net.minecraft.server.level.ServerPlayer p) {
        p.closeContainer();
        var asm = (dev.eugheist.thaumicenergistics.block.ArcaneAssemblerEntity) p.level().getBlockEntity(ASM);
        check(asm.getMainNode().isActive() && asm.getAvailablePatterns().size() == 1, "assembler online, offering the core's pattern to autocrafting");
        System.out.println("THE_AUDIT network items " + kc(asm.getMainNode().getGrid().getStorageService().getInventory().getAvailableStacks()) + " pattern inputs " + java.util.Arrays.stream(asm.getAvailablePatterns().get(0).getInputs()).map(i -> java.util.Arrays.toString(i.getPossibleInputs())).toList());
        var grid = asm.getMainNode().getGrid();
        check(grid.getCraftingService().isCraftable(appeng.api.stacks.AEItemKey.of(recipe.e().output())), "the network can now craft warded jars");
        var src = IActionSource.ofPlayer(p, asm);
        future = grid.getCraftingService().beginCraftingCalculation(p.level(), () -> src, appeng.api.stacks.AEItemKey.of(recipe.e().output()), 3, appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
        before = jarsIn(p);
        visBefore = asm.storedVis.visSize();
    }
    static String kc(appeng.api.stacks.KeyCounter c) { var sb = new StringBuilder(); for (var e : c) sb.append(e.getKey()).append('=').append(e.getLongValue()).append(' '); return sb.toString(); }
    static long before;static int visBefore;
    static java.util.concurrent.Future<appeng.api.networking.crafting.ICraftingPlan> future;
    /** The plan is computed off-thread and needs the server thread to progress: poll it from later ticks. */
    static boolean submit(net.minecraft.server.level.ServerPlayer p) {
        if (future == null || !future.isDone()) return false;
        try {
            var plan = future.get();future = null;
            check(!plan.simulation(), "crafting plan for 3 warded jars needs nothing missing " + kc(plan.missingItems()) + " used " + kc(plan.usedItems()));
            var asm = (dev.eugheist.thaumicenergistics.block.ArcaneAssemblerEntity) p.level().getBlockEntity(ASM);
            var result = asm.getMainNode().getGrid().getCraftingService().submitJob(plan, null, null, false, IActionSource.ofPlayer(p));
            check(result.successful(), "crafting job submitted to the CPU (" + result.errorCode() + ")");
            return true;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    static void checkCraft(net.minecraft.server.level.ServerPlayer p) {
        var asm = (dev.eugheist.thaumicenergistics.block.ArcaneAssemblerEntity) p.level().getBlockEntity(ASM);
        check(jarsIn(p) - before == 3, "arcane assembler crafted 3 warded jars into the network (" + before + " -> " + jarsIn(p) + ")");
        check(asm.storedVis.visSize() < visBefore && !asm.isCrafting(), "assembler spent stored vis and is idle (" + visBefore + " -> " + asm.storedVis.visSize() + ")");
        // Memory card linking: a card carrying a vis source links the assembler, a blank card clears it.
        var card = item("ae2:memory_card");
        card.set(ThaumicEnergistics.VIS_SOURCE.get(), new dev.eugheist.thaumicenergistics.part.VisInterfacePart.VisSource(p.level().dimension(), BASE, Direction.UP, 1));
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, card);
        p.level().getBlockState(ASM).useItemOn(card, p.level(), p, net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(ASM.getCenter(), Direction.UP, ASM, false));
        check(asm.hasVisSource(), "memory card with a vis interface links the assembler");
        var blank = item("ae2:memory_card");
        p.level().getBlockState(ASM).useItemOn(blank, p.level(), p, net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(ASM.getCenter(), Direction.UP, ASM, false));
        check(!asm.hasVisSource(), "blank memory card unlinks it");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }
    static void encoder(net.minecraft.server.level.ServerPlayer p) {
        var be = (dev.eugheist.thaumicenergistics.block.DistillationEncoderEntity) p.level().getBlockEntity(DPE);
        be.inventory.setItem(dev.eugheist.thaumicenergistics.block.DistillationEncoderEntity.BLANK, new ItemStack(appeng.core.definitions.AEItems.BLANK_PATTERN.asItem(), 4));
        var menu = new dev.eugheist.thaumicenergistics.menu.DistillationEncoderMenu(0, p.getInventory(), be);
        menu.setCarried(new ItemStack(net.minecraft.world.item.Items.BLAZE_POWDER));
        menu.clicked(menu.sourceSlot, 0, net.minecraft.world.inventory.ClickType.PICKUP, p);
        menu.setCarried(ItemStack.EMPTY);
        check(menu.scanned() && menu.aspect(0) != null && menu.selected() == menu.aspect(0), "scanned blaze powder shows its aspects, largest selected: " + menu.aspect(0) + " x" + menu.amount(0));
        menu.clickMenuButton(p, dev.eugheist.thaumicenergistics.menu.DistillationEncoderMenu.BUTTON_ENCODE);
        var pattern = be.inventory.getItem(dev.eugheist.thaumicenergistics.block.DistillationEncoderEntity.ENCODED);
        var details = appeng.api.crafting.PatternDetailsHelper.decodePattern(pattern, p.level());
        check(details != null && details.getOutputs().get(0).what() instanceof EssentiaKey k && k.getAspect() == menu.aspect(0) && details.getOutputs().get(0).amount() == menu.amount(0)
            && be.inventory.getItem(dev.eugheist.thaumicenergistics.block.DistillationEncoderEntity.BLANK).getCount() == 3, "encoded a processing pattern: 1 blaze powder -> " + menu.amount(0) + " " + menu.aspect(0) + " essentia, one blank used");
        // Reading the pattern back restores the selection.
        be.inventory.setItem(dev.eugheist.thaumicenergistics.block.DistillationEncoderEntity.SOURCE, ItemStack.EMPTY);
        var reopened = new dev.eugheist.thaumicenergistics.menu.DistillationEncoderMenu(0, p.getInventory(), be);
        check(reopened.source().is(net.minecraft.world.item.Items.BLAZE_POWDER) && reopened.selected() == menu.aspect(0), "an encoded distillation pattern loads back into the encoder");
        p.connection.teleport(DPE.getX() + .5, DPE.getY(), DPE.getZ() - 2.5, 0, 20);
        p.openMenu(new net.minecraft.world.SimpleMenuProvider((cid, inv, pl) -> be.menu(cid, inv), net.minecraft.network.chat.Component.translatable("block.thaumicenergistics.distillation_encoder")), buf -> buf.writeBlockPos(DPE));
    }

    @SubscribeEvent static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("thaumicenergistics.stage4")) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ticks++;
        var server = mc.getSingleplayerServer();var id = mc.player.getUUID();
        if (ticks == 40) server.execute(() -> {
            var p = server.getPlayerList().getPlayer(id);var l = p.serverLevel();
            l.getEntitiesOfClass(Entity.class, new AABB(BASE).inflate(14), e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(Entity::discard);
            for (int x = -6; x <= 12; x++) for (int z = -6; z <= 6; z++) for (int y = -1; y < 6; y++) l.setBlock(BASE.offset(x, y, z), (y == -1 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
            for (var k : new String[]{ThEResearch.ESSPROV, ThEResearch.INFPROV}) check(ResearchCategories.getResearch(k) != null, "research " + k);
            var r = ResearchCategories.getResearch(ThEResearch.ESSPROV);
            check(r.displayColumn == -2 && r.displayRow == -4 && r.getComplexity() == 3 && r.tags.getAmount(Aspect.SENSES) == 7, "TEESSPROV original position/aspects");
            var inf = dev.eugheist.tc4modern.InfusionItemRecipes.all().stream().filter(x -> x.key().equals("ThEInfusionProvider")).findFirst().orElseThrow();
            check(inf.instability() == 4 && inf.aspects().getAmount(Aspect.MECHANISM) == 64 && inf.central().get() == item("ae2:interface").getItem() && inf.components().size() == 8, "infusion provider infusion recipe (ME interface centre, 8 components, instability 4)");
            l.setBlock(BASE, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")).defaultBlockState(), 3);
            l.setBlock(BASE.east(), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:drive")).defaultBlockState(), 3);
            var cell = new ItemStack(ThaumicEnergistics.CELLS.get(EssentiaStorageType.TYPE_4K).get());var ci = (EssentiaCellInventory) StorageCells.getCellInventory(cell, null);
            ci.insert(EssentiaKey.of(Aspect.FIRE), 200, Actionable.MODULATE, IActionSource.empty());ci.insert(EssentiaKey.of(Aspect.AIR), 50, Actionable.MODULATE, IActionSource.empty());
            ((DriveBlockEntity) l.getBlockEntity(BASE.east())).getInternalInventory().setItemDirect(0, cell);
            for (int i = 2; i <= 5; i++) PartHelper.setPart(l, BASE.east(i), null, p, (IPartItem<?>) BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable")));
            PartHelper.setPart(l, BASE.east(3).above(), null, p, (IPartItem<?>) BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable")));
            // Essentia provider on top of a cable column; jar (ignis filter) two below through a tube? The provider feeds a tube into the jar.
            l.setBlock(ESS, ThaumicEnergistics.ESSENTIA_PROVIDER.get().defaultBlockState(), 3);
            l.setBlock(ESS.north(), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("tc4modern:essentia_tube")).defaultBlockState(), 3);
            jar(l, ESS.north().below(), null, 0, Aspect.FIRE);
            // A full ordo jar under a second provider: the provider pulls its essentia into the network.
            PartHelper.setPart(l, BASE.east(5).above(), null, p, (IPartItem<?>) BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:fluix_glass_cable")));
            l.setBlock(ESS2, ThaumicEnergistics.ESSENTIA_PROVIDER.get().defaultBlockState(), 3);
            l.setBlock(ESS2.above(), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("tc4modern:essentia_tube")).defaultBlockState(), 3);
            // Infusion provider next to the drive.
            l.setBlock(INF, ThaumicEnergistics.INFUSION_PROVIDER.get().defaultBlockState(), 3);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            p.connection.teleport(BASE.east(3).getX() + .5, BASE.getY(), BASE.getZ() - 5, 0, 15);
        });
        if (ticks == 100) server.execute(() -> {
            var p = server.getPlayerList().getPlayer(id);var l = p.serverLevel();
            var ess = (EssentiaProviderEntity) l.getBlockEntity(ESS);
            check(ess.isActive(), "essentia provider active on the network (channel + power)");
            check(ess.getSuctionAmount(Direction.UP) == 8 && ess.getMinimumSuction() == 1 && ess.isConnectable(Direction.NORTH), "provider: suction 8, minimum 1, connectable everywhere");
            var infp = (InfusionProviderEntity) l.getBlockEntity(INF);
            check(infp.isActive(), "infusion provider active");
            var live = ess.getMainNode().getGrid().getStorageService().getInventory();
            long airBefore = live.getAvailableStacks().get(EssentiaKey.of(Aspect.AIR));
            boolean drained = dev.eugheist.tc4modern.EssentiaSources.drain(l.getBlockEntity(BASE.east()), Aspect.AIR, 12);
            check(drained && live.getAvailableStacks().get(EssentiaKey.of(Aspect.AIR)) == airBefore - 1, "infusion-style drain takes aer from the network through the infusion provider (" + drained + ", " + airBefore + ")");
            check(!infp.takeFromContainer(Aspect.WATER, 1), "infusion provider refuses aspects the network lacks");
            // Pulling: a full ordo jar's tube contents into the network.
            var tube = l.getBlockEntity(ESS2.above());
            check(tube instanceof thaumcraft.api.aspects.IEssentiaTransport, "tube above the second provider");
            int added = ((dev.eugheist.tc4modern.EssentiaTubeEntity) tube).addEssentia(Aspect.ORDER, 4, Direction.UP);
            var t = (thaumcraft.api.aspects.IEssentiaTransport) tube;
            System.out.println("THE_AUDIT tube added=" + added + " amount=" + t.getEssentiaAmount(Direction.DOWN) + " type=" + t.getEssentiaType(Direction.DOWN) + " suction=" + t.getSuctionAmount(Direction.DOWN) + " min=" + t.getMinimumSuction() + " out=" + t.canOutputTo(Direction.DOWN));
        });
        if (ticks == 400) server.execute(() -> {
            var p = server.getPlayerList().getPlayer(id);var l = p.serverLevel();
            var jarBe = (WardedJarEntity) l.getBlockEntity(ESS.north().below());
            check(jarBe.state.aspect == Aspect.FIRE && jarBe.state.amount > 0, "filtered jar fills with ignis from the network through a tube (" + jarBe.state.amount + ")");
            check(stored(l, Aspect.FIRE) == 200 - jarBe.state.amount, "ignis left the network exactly as the jar received it");
            check(stored(l, Aspect.ORDER) >= 1, "provider pulled the ordo offered by its neighbouring tube into the network (" + stored(l, Aspect.ORDER) + ")");
        });
        if (ticks == 410) ThEAudit.shot(mc, "providers.png");
        if (ticks == 415) { var inv = mc.player.getInventory(); var book = new dev.eugheist.tc4modern.ThaumonomiconScreen(new dev.eugheist.tc4modern.ThaumonomiconMenu(0, inv), inv, net.minecraft.network.chat.Component.empty()); mc.setScreen(book); ThEAudit.invoke(book, "auditOpen", ResearchCategories.getResearch(ThEResearch.INFPROV), 0); }
        if (ticks == 425) { ThEAudit.shot(mc, "research-infprov.png"); mc.setScreen(null); }
        if (ticks == 430) server.execute(() -> { try { autocraftSetup(server.getPlayerList().getPlayer(id)); } catch (Throwable t) { t.printStackTrace(); } System.out.println("THE_AUDIT menu after setup: " + server.getPlayerList().getPlayer(id).containerMenu); });
        if (ticks == 445) { check(mc.screen instanceof dev.eugheist.thaumicenergistics.client.KnowledgeInscriberScreen, "knowledge inscriber GUI opens " + mc.screen); ThEAudit.shot(mc, "knowledge-inscriber.png"); }
        if (ticks == 450) server.execute(() -> inscribe(server.getPlayerList().getPlayer(id)));
        if (ticks == 465) { ThEAudit.shot(mc, "knowledge-inscriber-saved.png"); }
        if (ticks == 470) server.execute(() -> requestCraft(server.getPlayerList().getPlayer(id)));
        if (ticks > 470 && ticks < 520 && ticks % 2 == 0) server.execute(() -> submit(server.getPlayerList().getPlayer(id)));
        if (ticks == 520) server.execute(() -> { var p = server.getPlayerList().getPlayer(id); p.connection.teleport(ASM.getX() + .5, ASM.getY(), ASM.getZ() - 2.5, 0, 20); p.openMenu(new net.minecraft.world.SimpleMenuProvider((cid, inv, pl) -> ((dev.eugheist.thaumicenergistics.block.ArcaneAssemblerEntity) p.level().getBlockEntity(ASM)).menu(cid, inv), net.minecraft.network.chat.Component.translatable("thaumicenergistics.gui.arcane.assembler.title")), buf -> { buf.writeBlockPos(ASM); buf.writeBoolean(false); }); });
        if (ticks == 535) { check(mc.screen instanceof dev.eugheist.thaumicenergistics.client.ArcaneAssemblerScreen, "arcane assembler GUI opens"); ThEAudit.shot(mc, "arcane-assembler.png"); mc.player.closeContainer(); }
        if (ticks == 700) server.execute(() -> checkCraft(server.getPlayerList().getPlayer(id)));
        if (ticks == 705) server.execute(() -> encoder(server.getPlayerList().getPlayer(id)));
        if (ticks == 720) { check(mc.screen instanceof dev.eugheist.thaumicenergistics.client.DistillationEncoderScreen, "distillation encoder GUI opens"); ThEAudit.shot(mc, "distillation-encoder.png"); mc.player.closeContainer(); }
        if (ticks == 730) ThEAudit.shot(mc, "autocrafting-blocks.png");
        if (ticks == 735) { var inv = mc.player.getInventory(); var book = new dev.eugheist.tc4modern.ThaumonomiconScreen(new dev.eugheist.tc4modern.ThaumonomiconMenu(0, inv), inv, net.minecraft.network.chat.Component.empty()); mc.setScreen(book); ThEAudit.invoke(book, "auditBrowser", ThEResearch.TAB, 0, 0); }
        if (ticks == 745) { ThEAudit.shot(mc, "research-tab-stage4.png"); mc.setScreen(null); }
        if (ticks == 750) { System.out.println("THE_AUDIT ALL PASS"); mc.stop(); }
    }
}
