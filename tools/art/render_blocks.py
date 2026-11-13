"""Render the shipped JSON models and PNG atlases without borrowed art.

This is a small orthographic model preview, not a Minecraft screenshot.
Only Pillow is required. The renderer culls touching unit faces so adjacent
rail segments form a continuous surface in the assembly preview.
"""

import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/create_linear_engineering"
OUTPUT = ROOT / "docs/renders"
BG = "#deded9"
PANEL = "#efefeb"
WHITE = "#292d31"
MUTED = "#5b6265"
TEAL = "#5b646b"
GOLD = "#30363a"
VOXEL_SCALE = 1


def font(size, bold=False):
    path = Path("C:/Windows/Fonts") / ("segoeuib.ttf" if bold else "segoeui.ttf")
    if path.exists():
        return ImageFont.truetype(str(path), size)
    return ImageFont.load_default(size=size)


def load_model(name, folder="block"):
    source = json.loads((ASSETS / "models" / folder / f"{name}.json").read_text())
    parent = source.get("parent", "")
    if parent.startswith("create_linear_engineering:"):
        parent_folder, parent_name = parent.split(":", 1)[1].split("/", 1)
        base = load_model(parent_name, parent_folder)
        source = {**base, **source, "textures": {**base.get("textures", {}), **source.get("textures", {})}}
    return source


def load_textures(model):
    textures = {}
    for key, path in model["textures"].items():
        while path.startswith("#"):
            path = model["textures"][path[1:]]
        textures[key] = Image.open(ASSETS / "textures" / f"{path.split(':', 1)[1]}.png").convert("RGB")
    return textures


def load_voxels(parts):
    voxels = {}
    for name, offset in parts:
        model = load_model(name.removeprefix("item/"), "item" if name.startswith("item/") else "block")
        texture = load_textures(model)
        for original in model["elements"]:
            element = dict(original, **{
                "from": [round(v * VOXEL_SCALE) for v in original["from"]],
                "to": [round(v * VOXEL_SCALE) for v in original["to"]]})
            delta = tuple(round(v * VOXEL_SCALE) for v in offset)
            lo = [element["from"][i] + delta[i] for i in range(3)]
            hi = [element["to"][i] + delta[i] for i in range(3)]
            for x in range(lo[0], hi[0]):
                for y in range(lo[1], hi[1]):
                    for z in range(lo[2], hi[2]):
                        voxels[x, y, z] = (element, delta, texture)
    return voxels


def projection(p):
    x, y, z = p
    return ((x - z) * math.sqrt(3) / 2, (x + z) / 2 - y)


def surface_color(cell, position, face):
    element, offset, texture = cell
    x, y, z = [position[i] - offset[i] for i in range(3)]
    a, b = element["from"], element["to"]
    if face == "up":
        u, v = (x - a[0]) / (b[0] - a[0]), (z - a[2]) / (b[2] - a[2])
    elif face == "south":
        u, v = (x - a[0]) / (b[0] - a[0]), (b[1] - y) / (b[1] - a[1])
    else:
        u, v = (b[2] - z) / (b[2] - a[2]), (b[1] - y) / (b[1] - a[1])
    properties = element["faces"][face]
    if isinstance(texture, dict):
        texture = texture[properties["texture"][1:]]
    for _ in range(properties.get("rotation", 0) // 90):
        u, v = v, 1 - u
    uv = properties["uv"]
    tx = min(texture.width - 1, max(0, int((uv[0] + (uv[2] - uv[0]) * u) * texture.width / 16)))
    ty = min(texture.height - 1, max(0, int((uv[1] + (uv[3] - uv[1]) * v) * texture.height / 16)))
    light = {"up": 1.12, "south": .88, "east": .73}[face]
    return tuple(min(255, round(c * light)) for c in texture.getpixel((tx, ty)))


def render(draw, parts, area, scale):
    voxels = load_voxels(parts)
    faces = []
    for (x, y, z), cell in voxels.items():
        options = [
            ("up", (x, y + 1, z), [(x, y + 1, z), (x + 1, y + 1, z),
                                  (x + 1, y + 1, z + 1), (x, y + 1, z + 1)]),
            ("south", (x, y, z + 1), [(x, y, z + 1), (x + 1, y, z + 1),
                                     (x + 1, y + 1, z + 1), (x, y + 1, z + 1)]),
            ("east", (x + 1, y, z), [(x + 1, y, z), (x + 1, y, z + 1),
                                    (x + 1, y + 1, z + 1), (x + 1, y + 1, z)]),
        ]
        for name, neighbor, corners in options:
            if neighbor in voxels:
                continue
            center = tuple(sum(p[i] for p in corners) / 4 for i in range(3))
            points = [projection(p) for p in corners]
            faces.append((sum(center), points, surface_color(cell, center, name)))
    points = [p for _, poly, _ in faces for p in poly]
    bounds = [min(p[0] for p in points), min(p[1] for p in points),
              max(p[0] for p in points), max(p[1] for p in points)]
    ox = (area[0] + area[2]) / 2 - (bounds[0] + bounds[2]) * scale / 2
    oy = (area[1] + area[3]) / 2 - (bounds[1] + bounds[3]) * scale / 2
    for _, poly, color in sorted(faces, key=lambda face: face[0]):
        draw.polygon([(round(ox + x * scale), round(oy + y * scale)) for x, y in poly], fill=color)


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGB", (1600, 1380), BG)
    draw = ImageDraw.Draw(image)
    draw.text((64, 36), "CREATE: LINEAR ENGINEERING", font=font(42, True), fill=WHITE)
    draw.text((66, 94), "WORKED STEEL / COMPLEMENTARY PROFILES", font=font(20), fill=TEAL)
    for box in [(48, 150, 784, 650), (816, 150, 1552, 650),
                (48, 680, 784, 1280), (816, 680, 1552, 1280)]:
        draw.rounded_rectangle(box, radius=12, fill=PANEL)
    draw.text((80, 174), "01 / INVERTED-T RAIL", font=font(24, True), fill=GOLD)
    draw.text((848, 174), "02 / U-SHAPED CARRIAGE", font=font(24, True), fill=GOLD)
    render(draw, [("linear_rail", (0, 0, 0))], (100, 250, 730, 545), 13)
    render(draw, [("linear_carriage", (0, 0, 0))], (870, 250, 1500, 545), 13)
    draw.text((80, 592), "Wide base with a continuous central spine.", font=font(22), fill=MUTED)
    draw.text((848, 592), "The channel wraps around the rail spine.", font=font(22), fill=MUTED)
    draw.text((80, 704), "ASSEMBLED / CONTACT SURFACES", font=font(23, True), fill=GOLD)
    render(draw, [("linear_rail", (0, 0, z)) for z in (-16, 0, 16)] +
                 [("linear_carriage", (0, 0, 0))], (85, 800, 745, 1130), 7.8)
    draw.text((80, 1200), "Combined height: one block.", font=font(21), fill=MUTED)
    draw.text((848, 704), "EXPLODED / FIT", font=font(23, True), fill=GOLD)
    render(draw, [("linear_rail", (0, 0, 0)), ("linear_carriage", (0, 22, 0))],
           (865, 775, 1510, 1135), 8)
    draw.text((848, 1200), "Separated to show the complementary profiles.", font=font(21), fill=MUTED)
    draw.text((64, 1320), "Rendered from the distributed models and textures. Not an in-game screenshot.",
              font=font(20), fill=MUTED)
    image.save(OUTPUT / "final-blocks.png")
    for name in ("linear_rail", "linear_carriage"):
        item = Image.new("RGB", (900, 760), BG)
        render(ImageDraw.Draw(item), [(name, (0, 0, 0))], (70, 70, 830, 690), 23)
        item.save(OUTPUT / f"{name}.png")
    orientations = Image.new("RGB", (1600, 800), BG)
    orientation_draw = ImageDraw.Draw(orientations)
    for normal, x, label in (("down", 0, "CEILING"), ("east", 800, "WALL")):
        orientation_draw.text((x + 64, 48), label, font=font(30, True), fill=WHITE)
        parts = [(f"oriented/linear_rail_south_{normal}", (0, 0, z)) for z in (-16, 0, 16)]
        parts.append((f"oriented/linear_carriage_south_{normal}", (0, 0, 0)))
        render(orientation_draw, parts, (x + 50, 160, x + 750, 650), 9)
    orientation_draw.text((64, 740), "The same models, oriented by facing and mounting normal.", font=font(22), fill=MUTED)
    orientations.save(OUTPUT / "mounting-orientations.png")
    inventory = Image.new("RGB", (640, 250), "#c6c6c6")
    inventory_draw = ImageDraw.Draw(inventory)
    for i, name in enumerate(("linear_rail", "linear_carriage")):
        x = i * 320
        render(inventory_draw, [(name, (0, 0, 0))], (x + 40, 65, x + 104, 129), 1.7)
        render(inventory_draw, [(name, (0, 0, 0))], (x + 140, 35, x + 268, 163), 3.4)
        inventory_draw.text((x + 40, 184), name, font=font(17), fill="#333840")
    inventory_draw.text((40, 222), "64 / 128 px readability render, not an in-game screenshot", font=font(16), fill="#333840")
    inventory.save(OUTPUT / "inventory-preview.png")


if __name__ == "__main__":
    main()
