"""Original grease pixels and previews; geometry remains in the canonical generators.

Run after generate_textures.py and generate_sensor_models.py. No borrowed art,
overlay geometry, or dynamic textures: inherited slots separate lining and outer bleed.
"""

from PIL import Image, ImageColor, ImageDraw

from generate_models import ASSETS, write_json
import render_blocks as renderer

PALETTES = {
    "low": ("#b5d3ce", "#e6eee0", "#f6f6da", "#6e9c99"),
    "high": ("#b87821", "#e5b449", "#f5d67b", "#724722"),
}


def lining(grease):
    texture = Image.open(ASSETS / "textures/block/linear_carriage.png").convert("RGB")
    draw = ImageDraw.Draw(texture)
    if grease == "none":
        base, bright, light, shadow = "#62676b", "#8c9295", "#a5adae", "#454b50"
    else:
        base, bright, light, shadow = PALETTES[grease]
    # Longitudinal running films inside the U; outer bleed has its own surface atlas.
    draw.rectangle((16, 4, 31, 11), fill=base)
    for row, color in ((4, shadow), (5, bright), (7, light), (10, bright), (11, shadow)):
        draw.line((16, row, 31, row), fill=color)
    draw.rectangle((6, 16, 9, 31), fill=base)
    # Roof underside and both contact feet use this same smooth tile. No rivets
    # or forged-metal speckles remain on a running surface, including dry ones.
    draw.rectangle((16, 16, 31, 31), fill=base)
    for x in (6, 22):
        draw.line((x, 16, x, 31), fill=bright)
        draw.line((x + 3, 16, x + 3, 31), fill=shadow)
    for left, right in ((16, 21), (26, 31)):
        draw.line((left, 16, left, 31), fill=bright)
        draw.line((right, 16, right, 31), fill=shadow)
    # These tiles also cover the six screw engagement teeth.
    draw.rectangle((4, 0, 11, 15), fill=base)
    draw.line((4, 0, 4, 15), fill=bright)
    draw.line((11, 0, 11, 15), fill=shadow)
    if grease != "none":
        for x, y in ((19, 6), (26, 9), (8, 3), (23, 22), (24, 28)):
            draw.point((x, y), fill=light)
    texture.save(ASSETS / f"textures/block/carriage_lining_{grease}.png", optimize=True)


def grease_icon(grease):
    base, bright, light, shadow = PALETTES[grease]
    image = Image.new("RGBA", (16, 16))
    draw = ImageDraw.Draw(image)
    if grease == "high":
        outline = [(2, 8), (3, 8), (3, 6), (5, 6), (5, 3), (7, 2), (9, 3),
                   (10, 5), (12, 5), (12, 8), (14, 9), (14, 12), (12, 13),
                   (4, 13), (2, 12), (1, 10)]
        draw.polygon(outline, fill=shadow)
        draw.polygon([(3, 9), (4, 7), (6, 7), (6, 4), (8, 3), (9, 6),
                      (11, 6), (11, 9), (13, 10), (12, 12), (4, 12), (2, 10)], fill=base)
        draw.line((5, 8, 9, 8, 10, 9), fill=bright, width=1)
        draw.line((4, 10, 10, 10), fill=bright)
        draw.line((6, 4, 6, 6), fill=light)
        draw.point((7, 3), fill=light)
        draw.line((3, 9, 3, 10), fill=light)
        draw.line((10, 12, 12, 11), fill=shadow)
    else:
        outline = [(1, 10), (3, 8), (6, 8), (7, 6), (7, 3), (9, 1), (11, 3),
                   (11, 6), (10, 8), (13, 8), (15, 10), (14, 12), (11, 13),
                   (4, 13), (1, 12)]
        draw.polygon(outline, fill=shadow)
        draw.polygon([(2, 10), (4, 9), (7, 9), (8, 6), (8, 3), (9, 2),
                      (10, 3), (10, 6), (9, 9), (13, 9), (14, 10), (13, 11),
                      (10, 12), (4, 12), (2, 11)], fill=base)
        draw.line((4, 9, 7, 9), fill=bright)
        draw.line((8, 4, 8, 6), fill=light)
        draw.line((3, 10, 5, 10), fill=light)
        draw.line((6, 11, 11, 11), fill=bright)
        draw.point((12, 9), fill=light)
        draw.point((13, 10), fill=bright)
    return image


def outer_bleed(grease):
    """One faded pixel at the exposed foot, with a few softer wicking marks above."""
    texture = Image.open(ASSETS / "textures/block/linear_carriage.png").convert("RGB")
    pigment = ImageColor.getrgb(PALETTES[grease][1])

    def stain(x, y, opacity):
        metal = texture.getpixel((x, y))
        texture.putpixel((x, y), tuple(round(a * (1 - opacity) + b * opacity) for a, b in zip(metal, pigment)))

    for z in range(16):
        stain(16 + z, 11, .66 if z % 5 else .48)
        if z in (2, 3, 9, 13):
            stain(16 + z, 10, .22)
    for x in (*range(6), *range(10, 16)):
        stain(x, 27, .58)
        if x in (1, 4, 11, 14):
            stain(x, 26, .18)
    texture.save(ASSETS / f"textures/block/carriage_surface_{grease}.png", optimize=True)


def generate():
    for grease in ("none", "low", "high"):
        lining(grease)
    for grease in PALETTES:
        outer_bleed(grease)
        name = f"{grease}_viscosity_grease"
        grease_icon(grease).save(ASSETS / f"textures/item/{name}.png", optimize=True)
        write_json(ASSETS / f"models/item/{name}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"create_linear_engineering:item/{name}"}})


def render_previews():
    renderer.VOXEL_SCALE = 2  # Preserve the screw teeth's half-pixel dimensions.
    output = renderer.OUTPUT
    output.mkdir(parents=True, exist_ok=True)
    sheet = Image.new("RGB", (1500, 1040), renderer.BG)
    draw = ImageDraw.Draw(sheet)
    draw.text((48, 30), "FULL-LENGTH CARRIAGES / INNER SURFACES", font=renderer.font(34, True), fill=renderer.WHITE)
    draw.text((48, 80), "Smooth channel and feet, with faded grease at the outer contact edge.",
              font=renderer.font(21), fill=renderer.MUTED)
    for column, (grease, label) in enumerate((("none", "DRY"), ("high", "HIGH / AMBER"), ("low", "LOW / PALE"))):
        x = column * 500
        draw.rounded_rectangle((x + 25, 130, x + 475, 910), radius=12, fill=renderer.PANEL)
        draw.text((x + 55, 155), label, font=renderer.font(24, True), fill=renderer.WHITE)
        for row, carriage in enumerate(("linear_carriage", "screw_carriage")):
            base = f"oriented/{carriage}_south_down"
            name = base if grease == "none" else f"greased/{carriage}_south_down_{grease}"
            renderer.render(draw, [(name, (0, 0, 0))], (x + 45, 225 + row * 345, x + 455, 475 + row * 345), 4.7)
            draw.text((x + 55, 490 + row * 345), "LINEAR" if row == 0 else "SCREW / SIX TEETH",
                      font=renderer.font(19), fill=renderer.MUTED)
    draw.text((48, 958), "Underside view: the U-channel stays open and the rail fits without intersections.",
              font=renderer.font(21), fill=renderer.MUTED)
    draw.text((48, 996), "Technical render of the final models and textures, not an in-game screenshot.", font=renderer.font(19), fill=renderer.MUTED)
    sheet.save(output / "grease-carriages.png")
    mounted = Image.new("RGB", (1500, 1260), renderer.BG)
    draw = ImageDraw.Draw(mounted)
    draw.text((48, 28), "VISIBLE GREASE / MOUNTED CARRIAGES", font=renderer.font(32, True), fill=renderer.WHITE)
    draw.text((48, 78), "Faded grease at the rail contact; the body retains its dark steel finish.",
              font=renderer.font(21), fill=renderer.MUTED)
    for column, (grease, label) in enumerate((("none", "DRY"), ("high", "HIGH / AMBER"), ("low", "LOW / PALE"))):
        x = column * 500
        draw.rounded_rectangle((x + 25, 130, x + 475, 875), radius=12, fill=renderer.PANEL)
        draw.text((x + 50, 150), label, font=renderer.font(23, True), fill=renderer.WHITE)
        for row, (rail, carriage) in enumerate((("linear_rail", "linear_carriage"), ("item/screw_rail", "screw_carriage"))):
            name = carriage if grease == "none" else f"greased/{carriage}_{grease}"
            renderer.render(draw, [(rail, (0, 0, z)) for z in (-16, 0, 16)] + [(name, (0, 0, 0))],
                            (x + 45, 220 + row * 335, x + 455, 465 + row * 335), 3)
            draw.text((x + 50, 475 + row * 335), "LINEAR" if row == 0 else "SCREW", font=renderer.font(19), fill=renderer.MUTED)
    draw.text((48, 925), "SMALL-SCALE VIEW / CARRIAGE ABOUT 64 PX", font=renderer.font(23, True), fill=renderer.WHITE)
    for column, grease in enumerate(("none", "high", "low")):
        name = "linear_carriage" if grease == "none" else f"greased/linear_carriage_{grease}"
        renderer.render(draw, [("linear_rail", (0, 0, z)) for z in (-16, 0, 16)] + [(name, (0, 0, 0))],
                        (column * 500 + 70, 985, column * 500 + 430, 1165), 1.7)
    draw.text((48, 1210), "Rendered from the distributed models and textures. Geometry and collisions are unchanged.",
              font=renderer.font(20), fill=renderer.MUTED)
    mounted.save(output / "grease-mounted.png")
    icons = Image.new("RGB", (1000, 360), renderer.BG)
    draw = ImageDraw.Draw(icons)
    draw.text((40, 25), "GREASE / ORIGINAL 16 x 16 PIXEL ART", font=renderer.font(28, True), fill=renderer.WHITE)
    for column, (grease, label) in enumerate((("high", "HIGH / thick, amber"), ("low", "LOW / thin, pale"))):
        x = column * 500
        item = Image.open(ASSETS / f"textures/item/{grease}_viscosity_grease.png").convert("RGBA")
        for dx, size in ((65, 64), (195, 128)):
            preview = item.resize((size, size), Image.Resampling.NEAREST)
            icons.paste(preview, (x + dx, 95), preview)
        draw.text((x + 55, 260), label, font=renderer.font(23), fill=renderer.WHITE)
    draw.text((40, 320), "Icons at 64 and 128 px, using original textures.", font=renderer.font(19), fill=renderer.MUTED)
    icons.save(output / "grease-items.png")


if __name__ == "__main__":
    generate()
    render_previews()
