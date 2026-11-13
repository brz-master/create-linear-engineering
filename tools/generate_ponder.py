"""Build the Ponder baseplate and English language entries. Requires nbtlib."""
from pathlib import Path
import json
import re
import nbtlib as nbt

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/create_linear_engineering'
palette = nbt.List[nbt.Compound]([nbt.Compound({'Name': nbt.String(name)}) for name in ['minecraft:smooth_stone', 'minecraft:air']])
blocks = nbt.List[nbt.Compound]([
    nbt.Compound({'pos': nbt.List[nbt.Int]([x, y, z]), 'state': nbt.Int(0 if y == 0 else 1)})
    for x in range(7) for y in range(7) for z in range(7)
])
target = ASSETS / 'ponder/linear.nbt'
target.parent.mkdir(parents=True, exist_ok=True)
nbt.File({'DataVersion': nbt.Int(3955), 'size': nbt.List[nbt.Int]([7, 7, 7]),
          'palette': palette, 'blocks': blocks, 'entities': nbt.List[nbt.Compound]()}).save(target, gzipped=True)

source = '\n'.join((ROOT/f'src/main/java/io/github/brzmaster/createlinearengineering/client/{name}.java').read_text(encoding='utf-8') for name in ['LinearScenes', 'ScrewScenes', 'SensorScenes'])
english = {}
for part in source.split('public static void ')[1:]:
    scene_id, title = re.search(r'(?:start\(scene, |scene.title\()"([^"]+)", "([^"]+)"\)', part).groups()
    texts = re.findall(r'text\(scene, "([^"]+)"', part)
    english[scene_id] = [title, *texts]
p = ASSETS / 'lang/en_us.json'
data = json.loads(p.read_text(encoding='utf-8'))
data['itemGroup.create_linear_engineering'] = 'Create: Linear Engineering'
for scene_id, values in english.items():
    for i, value in enumerate(values):
        key = 'header' if i == 0 else f'text_{i}'
        data[f'create_linear_engineering.ponder.{scene_id}.{key}'] = value
p.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
print(f'Generated Ponder template and {len(english)} English storyboards.')
