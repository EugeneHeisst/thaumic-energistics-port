# Thaumic Energistics 1.1.3.0 → Minecraft 1.21.1 / NeoForge

Separate addon (`thaumicenergistics`) on top of tc4modern (≥0.17.143) and Applied Energistics 2 19.2.18 (+ GuideME).
Reference material lives in `related/` (ignored): original jar + CFR sources, AE2 rv3-beta-6 (1.7.10) and 19.2.18 (1.21.1) decompiled
for API comparison, Applied Mekanistics and ArsEng as modern custom-key-type examples.

## AE2 1.7.10 → 1.21.1 migration notes (applied in this port)
| rv3 (1.7.10) | 19.2 (1.21.1) |
|---|---|
| Essentia faked as `GaseousEssentia` fluids on `StorageChannel.FLUIDS`, unit conversion helper | Own `AEKeyType` (`EssentiaKeyType`) + `EssentiaKey` per aspect, `AEKeyTypes.register` during item registration |
| `ICellHandler.getCellInventory(stack, saveProvider, channel)` → `IMEInventoryHandler` | `ICellHandler.getCellInventory(stack, saveProvider)` → `StorageCell` (`insert/extract/getAvailableStacks/getStatus/persist`) |
| `IAEStack`/`IItemList`, `injectItems/extractItems` returning leftovers | `AEKey` + `long` amounts, `insert/extract` return amount moved, `KeyCounter` |
| `getStatusForCell` int 1/2/3 | `CellState` (EMPTY/NOT_EMPTY/TYPES_FULL/FULL) |
| `AEApi.instance().definitions()` | static ids / tags (`ae2:illuminated_panel` replaces ore `itemIlluminatedPanel`) |
| Part rendering via `IPartRenderHelper` | JSON part models (`PartModels`), terminal = display base + three light layers |
| Terminal container/GUI per channel | `AbstractTerminalPart` + `KeyTypeSelection` restricted to essentia |
| Grinder/inscriber/crafting recipes for scan tags | inscriber, crafting, transform, charger (no grinder in 19.2) |
| `IAspectContainer` on the facing tile | tc4modern `api.AspectContainers` (jars, void jar, reservoir both ways; alembic, centrifuge, buffer, furnace nozzle extract only; original permission capacities) |
| `ICellContainer.getCellArray(FLUIDS)` + `MENetworkCellArrayUpdate` | `IStorageProvider.mountInventories` + `IStorageProvider.requestUpdate`; the network re-reads `getAvailableStacks`, no alteration posting |
| `IEssentiaWatcher` | `IStorageWatcherNode`/`IStackWatcher` on the essentia key |
| `StackUpgradeInventory` + `Upgrades.X.registerItem` | `UpgradeablePart` (`UpgradeInventories.forMachine`) + `Upgrades.add(card, part, max, group)` |
| Interface/condenser storage bus handlers | `AECapabilities.ME_STORAGE` of the facing block, filtered to essentia |
| GUI packets (aspect slots, redstone, void, threshold) | menu buttons (`clickMenuButton`) + data slots (shorts, so the threshold uses three 16-bit slots) + one `ThresholdPayload` |

## Stage 1 (done)
Essentia key type, cells 1k/4k/16k(16348 bytes)/64k/creative with original slot/partition/creative logic, components, casing,
coalescence/diffusion cores, essentia terminal part, research tab TERESEARCH/TECORES/TESTORAGE/TEESSTERM + pseudo entries,
original recipes (arcane via tc4modern TC4Addons, crafting JSON), AEAspectRegister port + designed tags for every AE2 item,
essentia cell workbench (63-widget partition GUI, original buttons from the AE2 rv3 states sheet), phial (exactly 8) and warded/void jar (64, label filter) fill/drain through ME terminals via ContainerItemStrategy, original textures and translations. Audit: `./gradlew runAuditClient` (hidden in Xvfb, isolated TEInspection world) ALL PASS.

## Stage 2 (done)
tc4modern 0.17.144 `AspectContainers` API. Essentia import/export buses (3x3 filter opened by 0/1/2 capacity cards, 4+8/speed card
essentia per second, 10-40 tick ops, redstone card modes incl. pulse on every edge, export void mode), essentia storage bus (jar /
reservoir / ME interface or condenser, 9 filters + inverter, priority via AE2's priority menu, void jar mode, 0.3 AE per essentia),
essentia level emitter (high/low, threshold up to 9 999 999 999, +-1/10/100 and shift x100), essentia storage & conversion monitors
on AE2 monitor displays (aspect items track, wrench locks, locked conversion monitor fills phials/jars and labels, sneak empties,
sneak double-click empties the inventory). Original GUIs (io bus / storage bus / level emitter textures, network tool panel),
part models built from the original render boxes and textures, TEIO research (pseudo TUBEFILTER) and ESSTERM pages 3-4 with the
original recipes. Deviation: the import bus only drains what the network accepted (the original drained the whole request).
Audit: `JAVA_TOOL_OPTIONS=-Dthaumicenergistics.stage2=true ./gradlew runAuditClient`.
