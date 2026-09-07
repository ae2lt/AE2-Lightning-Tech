"""Package ImageGen artwork into nearest-neighbor Minecraft sprites, retaining its generated alpha."""
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).parent
entries = json.loads((ROOT / 'selected.json').read_text())
(ROOT / 'source').mkdir(exist_ok=True)
(ROOT / 'textures').mkdir(exist_ok=True)
for entry in entries:
    name = entry['name']
    source = Image.open(ROOT / entry['path']).convert('RGBA')
    if source.getchannel('A').getextrema()[0] != 0:
        raise ValueError(f'{name}: rejected opaque background')
    source.save(ROOT / 'source' / (name + '.png'))
    # Measure the object, not the faint outside halo; do not alter the generated alpha channel.
    bounds = source.getchannel('A').point(lambda a: 255 if a >= 128 else 0).getbbox()
    sprite = source.crop(bounds)
    factor = min(28 / sprite.width, 28 / sprite.height)
    size = (max(1, round(sprite.width * factor)), max(1, round(sprite.height * factor)))
    sprite = sprite.resize(size, Image.Resampling.NEAREST)
    canvas = Image.new('RGBA', (32, 32))
    canvas.paste(sprite, ((32 - size[0]) // 2, (32 - size[1]) // 2))
    canvas.save(ROOT / 'textures' / (name + '.png'))
    alpha = list(canvas.getchannel('A').getdata())
    print(name, '32x32', 'visible', sum(a > 0 for a in alpha), 'translucent', sum(0 < a < 255 for a in alpha))
