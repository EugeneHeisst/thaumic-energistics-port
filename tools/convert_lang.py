"""Convert the original Thaumic Energistics .lang files to 1.21 JSON: keep every key, add modern item/block ids, %d -> %s."""
import json, re, sys, pathlib
SRC = pathlib.Path(sys.argv[1]); DST = pathlib.Path(sys.argv[2])
P = 'thaumicenergistics.'
IDS = {
  'item.essentia.cell.%s.name': 'item.thaumicenergistics.essentia_cell_%s',
  'item.storage.component.%s.name': 'item.thaumicenergistics.essentia_storage_component_%s',
}
SIMPLE = {
  'item.storage.casing.name': 'item.thaumicenergistics.essentia_cell_casing',
  'item.material.diffusion.core.name': 'item.thaumicenergistics.diffusion_core',
  'item.material.coalescence.core.name': 'item.thaumicenergistics.coalescence_core',
  'item.material.iron.gear.name': 'item.thaumicenergistics.iron_gear',
  'item.wireless.essentia.terminal.name': 'item.thaumicenergistics.wireless_essentia_terminal',
  'item.knowledge.core.name': 'item.thaumicenergistics.knowledge_core',
  'item.focus.aewrench.name': 'item.thaumicenergistics.focus_aewrench',
  'item.golem.wifi.backpack.name': 'item.thaumicenergistics.golem_wifi_backpack',
  'aeparts.essentia.ImportBus.name': 'item.thaumicenergistics.essentia_import_bus',
  'aeparts.essentia.ExportBus.name': 'item.thaumicenergistics.essentia_export_bus',
  'aeparts.essentia.levelemitter.name': 'item.thaumicenergistics.essentia_level_emitter',
  'aeparts.essentia.StorageBus.name': 'item.thaumicenergistics.essentia_storage_bus',
  'aeparts.essentia.terminal.name': 'item.thaumicenergistics.essentia_terminal',
  'aeparts.arcane.crafting.terminal.name': 'item.thaumicenergistics.arcane_crafting_terminal',
  'aeparts.vis.interface.name': 'item.thaumicenergistics.vis_interface',
  'aeparts.essentia.storage.monitor.name': 'item.thaumicenergistics.essentia_storage_monitor',
  'aeparts.essentia.conversion.monitor.name': 'item.thaumicenergistics.essentia_conversion_monitor',
  'block.essentia.provider.name': 'block.thaumicenergistics.essentia_provider',
  'block.infusion.provider.name': 'block.thaumicenergistics.infusion_provider',
  'block.gear.box.name': 'block.thaumicenergistics.gear_box',
  'block.golem.gear.box.name': 'block.thaumicenergistics.golem_gear_box',
  'block.essentia.cell.workbench.name': 'block.thaumicenergistics.essentia_cell_workbench',
  'block.arcane.assembler.name': 'block.thaumicenergistics.arcane_assembler',
  'block.knowledge.inscriber.name': 'block.thaumicenergistics.knowledge_inscriber',
  'block.essentia.vibration.chamber.name': 'block.thaumicenergistics.essentia_vibration_chamber',
  'block.distillation.encoder.name': 'block.thaumicenergistics.distillation_encoder',
}
EXTRA = {'ru_ru': {'thaumicenergistics.key.essentia': 'Эссенция', 'item.thaumicenergistics.arcane_pattern': 'Тайный шаблон'}, 'en_us': {'thaumicenergistics.key.essentia': 'Essentia', 'item.thaumicenergistics.arcane_pattern': 'Arcane Pattern'}}
for f in sorted(SRC.glob('*.lang')):
    lang = f.stem.lower(); out = {}
    for line in f.read_text(encoding='utf-8').splitlines():
        if not line.strip() or line.lstrip().startswith('#') or '=' not in line: continue
        k, v = line.split('=', 1); k = k.strip(); v = re.sub(r'%(\d+\$)?d', lambda m: '%' + (m.group(1) or '') + 's', v)
        out[k] = v
        if k.startswith(P):
            rest = k[len(P):]
            if rest in SIMPLE: out[SIMPLE[rest]] = v
            for pat, mod in IDS.items():
                m = re.fullmatch(pat.replace('.', r'\.').replace('%s', '([a-z0-9]+)'), rest)
                if m: out[mod % m.group(1)] = v
    out.update(EXTRA.get(lang, {}))
    if lang == 'ru_ru': out.update({k: v for k, v in json.loads((pathlib.Path(__file__).parent / 'ru_extra.json').read_text(encoding='utf-8')).items() if k not in out})
    (DST / f'{lang}.json').write_text(json.dumps(out, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(lang, len(out))
