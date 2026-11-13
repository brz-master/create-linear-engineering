"""Bake the original circuit into the existing iron atlas at build time.

Opaque 64px atlases preserve both the original iron pixels and the 32px circuit.
Minecraft renders one surface, using ordinary block model mipmaps and culling.
"""
from PIL import Image, ImageDraw

from generate_textures import DEEP, EDGE, INK, LIGHT, STEEL, TEXTURES

# Original warm redstone colours, readable against the existing neutral iron.
# The surrounding cable deliberately remains lighter than the active marker.
PALETTES = {
    False: {
        'wire': '#b95f50', 'wire_light': '#d48470', 'wire_dark': '#94463d',
        'marker': '#83272b', 'marker_light': '#a63a37', 'marker_dark': '#5d2026',
    },
    True: {
        'wire': '#e47d64', 'wire_light': '#ffa88a', 'wire_dark': '#bc5747',
        'marker': '#e53c2b', 'marker_light': '#ff9970', 'marker_dark': '#aa2a27',
    },
}


def bevel(draw, box, base, light, dark):
    x0, z0, x1, z1 = box
    draw.rectangle(box, fill=base)
    draw.line((x0, z0, x1, z0), fill=light)
    draw.line((x0, z0, x0, z1), fill=light)
    draw.line((x0, z1, x1, z1), fill=dark)
    draw.line((x1, z0, x1, z1), fill=dark)


def circuit(precise, powered):
    image = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    p = PALETTES[powered]
    # A one-model-pixel cable around the perimeter, inset one model pixel.
    # End runs beneath the central stem are hidden by the two ledge quads.
    draw.rectangle((2, 2, 29, 29), outline=p['wire'], width=2)
    draw.line((2, 2, 29, 2), fill=p['wire_light'])
    draw.line((2, 2, 2, 29), fill=p['wire_light'])
    draw.line((2, 29, 29, 29), fill=p['wire_dark'])
    draw.line((29, 2, 29, 29), fill=p['wire_dark'])
    # 2x2 model-pixel centred dot, or 2x10 longitudinal contact stripe.
    z0, z1 = (14, 17) if precise else (6, 25)
    for x0, x1 in ((4, 7), (24, 27)):
        bevel(draw, (x0, z0, x1, z1), p['marker'], p['marker_light'], p['marker_dark'])
    return image


def inventory_item():
    """A thin iron-backed redstone loop, not another block or bulky housing."""
    image = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    p = PALETTES[False]
    # Slim iron backing gives the loose accessory a readable physical edge.
    draw.rectangle((3, 3, 28, 28), outline=INK, width=4)
    draw.rectangle((4, 4, 27, 27), outline=STEEL, width=2)
    draw.line((4, 4, 27, 4), fill=EDGE)
    draw.line((4, 4, 4, 27), fill=EDGE)
    draw.rectangle((5, 5, 26, 26), outline=p['wire'], width=2)
    draw.line((5, 5, 26, 5), fill=p['wire_light'])
    draw.line((5, 5, 5, 26), fill=p['wire_light'])
    for x0, x1 in ((6, 9), (22, 25)):
        bevel(draw, (x0, 11, x1, 20), p['marker'], p['marker_light'], p['marker_dark'])
    # Two modest iron terminals retain the Create material vocabulary.
    for x0, x1 in ((10, 13), (18, 21)):
        bevel(draw, (x0, 25, x1, 29), STEEL, LIGHT, DEEP)
    return image


def main():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    for precise in (False, True):
        for powered in (False, True):
            name = f"rail_sensor_{'precise' if precise else 'approximate'}_{'on' if powered else 'off'}"
            atlas = Image.open(TEXTURES / 'linear_rail.png').convert('RGBA').resize((64, 64), Image.Resampling.NEAREST)
            top = circuit(precise, powered)
            # The central stem shares the top-face UVs: keep its strip plain iron.
            ImageDraw.Draw(top).rectangle((12, 0, 19, 31), fill=(0, 0, 0, 0))
            atlas.alpha_composite(top, (0, 0))
            atlas.convert('RGB').save(TEXTURES / f'{name}.png', optimize=True)
    item_dir = TEXTURES.parent / 'item'
    item_dir.mkdir(parents=True, exist_ok=True)
    inventory_item().save(item_dir / 'rail_sensor.png', optimize=True)
    print('Generated four opaque rail circuit atlases and the Rail Sensor item.')


if __name__ == '__main__':
    main()
