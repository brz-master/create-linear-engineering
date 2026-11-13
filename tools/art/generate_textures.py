"""Draw two original forged-steel atlases using Pillow.

Each 32x32 atlas keeps the established four 16x16 tiles: top, side,
end, underside. Geometry supplies the shape; texture only describes metal
beveled edges, shallow relief and a few fasteners. No borrowed Create artwork is used.
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
TEXTURES = ROOT / "src/main/resources/assets/create_linear_engineering/textures/block"
RENDERS = ROOT / "docs/renders"

# Original neutral iron palette, inspired by Create's forged-metal materials.
# Reference PNGs are inspected separately and never sampled by this generator.
INK = "#303136"
DEEP = "#414247"
STEEL = "#55565b"
LIGHT = "#939297"
CARRIAGE = "#5e5f64"
EDGE = "#76777c"
PATCH = "#606166"
DARK_PATCH = "#4d4e53"
WHITE = "#e2e5e9"


def panel(base):
    tile = Image.new("RGB", (16, 16), base)
    draw = ImageDraw.Draw(tile)
    # Sparse, deliberate forged-metal marks; no all-over random noise.
    for x, y, width in ((3, 3, 2), (11, 5, 1), (6, 9, 2), (2, 12, 1), (12, 13, 2)):
        draw.line((x, y, x + width - 1, y), fill=PATCH)
    for x, y in ((8, 2), (4, 7), (11, 11)):
        draw.line((x, y, x + 1, y), fill=DARK_PATCH)
    return tile, draw


def bolt(draw, x, y):
    draw.point((x, y), fill=LIGHT)
    draw.point((x, y + 1), fill=INK)


def rail_tiles():
    top, d = panel(STEEL)
    # The foot and polished stem each have their own longitudinal bevel.
    for x in (0, 10):
        d.line((x, 0, x, 15), fill=EDGE)
        d.line((x + 1, 0, x + 1, 15), fill=DEEP)
        d.line((x + 5, 0, x + 5, 15), fill=INK)
    d.rectangle((6, 0, 9, 15), fill=PATCH)
    d.line((6, 0, 6, 15), fill=EDGE)
    d.line((9, 0, 9, 15), fill=DEEP)
    for y in (4, 11):
        d.point((7, y), fill=STEEL)

    side, d = panel(STEEL)
    # World-space atlas rows follow the 4px foot and 8px central stem.
    d.line((0, 4, 15, 4), fill=EDGE)
    d.line((0, 5, 15, 5), fill=DEEP)
    d.line((0, 11, 15, 11), fill=DEEP)
    d.line((0, 12, 15, 12), fill=EDGE)
    d.line((0, 14, 15, 14), fill=DEEP)
    d.line((0, 15, 15, 15), fill=INK)

    end, d = panel(STEEL)
    # A bevel follows the actual T silhouette, rather than a box painted on it.
    d.line((0, 12, 5, 12), fill=EDGE)
    d.line((10, 12, 15, 12), fill=EDGE)
    d.line((0, 13, 0, 14), fill=EDGE)
    d.line((0, 15, 15, 15), fill=INK)
    d.line((15, 13, 15, 15), fill=DEEP)
    d.line((6, 4, 9, 4), fill=EDGE)
    d.line((6, 5, 6, 11), fill=EDGE)
    d.line((9, 5, 9, 11), fill=DEEP)
    underside, _ = panel(DEEP)
    return top, side, end, underside


def carriage_tiles():
    top, d = panel(CARRIAGE)
    # One shallow framed fastening face, like a forged bracket plate.
    d.rectangle((0, 0, 15, 15), outline=DEEP)
    d.line((0, 0, 15, 0), fill=EDGE)
    d.line((0, 1, 0, 14), fill=EDGE)
    d.line((1, 15, 15, 15), fill=INK)
    d.line((15, 1, 15, 15), fill=INK)
    d.rectangle((3, 5, 12, 10), fill=STEEL)
    d.line((3, 5, 12, 5), fill=DEEP)
    d.line((3, 5, 3, 10), fill=DEEP)
    d.line((4, 10, 12, 10), fill=PATCH)
    d.line((7, 7, 8, 7), fill=PATCH)
    for x, y in ((2, 4), (13, 4), (2, 10), (13, 10)):
        bolt(d, x, y)

    side, d = panel(CARRIAGE)
    d.line((0, 0, 15, 0), fill=EDGE)
    d.line((0, 3, 15, 3), fill=DEEP)
    d.line((0, 4, 15, 4), fill=DEEP)
    d.line((0, 11, 15, 11), fill=INK)
    d.line((0, 0, 0, 10), fill=EDGE)
    d.line((15, 0, 15, 11), fill=DEEP)
    for x in (4, 11):
        bolt(d, x, 6)

    end, d = panel(CARRIAGE)
    d.line((0, 0, 15, 0), fill=EDGE)
    d.line((0, 1, 0, 10), fill=EDGE)
    d.line((15, 1, 15, 11), fill=INK)
    d.line((0, 11, 5, 11), fill=INK)
    d.line((10, 11, 15, 11), fill=INK)
    # The inner U bevel remains legible without making a floating dark gap.
    d.line((5, 3, 10, 3), fill=DEEP)
    d.line((5, 4, 5, 10), fill=DEEP)
    d.line((10, 4, 10, 10), fill=EDGE)
    for x in (2, 13):
        bolt(d, x, 2)
    underside, _ = panel(DEEP)
    return top, side, end, underside


def atlas(tiles, name):
    result = Image.new("RGB", (32, 32))
    for tile, xy in zip(tiles, ((0, 0), (16, 0), (0, 16), (16, 16))):
        result.paste(tile, xy)
    result.save(TEXTURES / f"{name}.png", optimize=True)
    return result


def main():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    RENDERS.mkdir(parents=True, exist_ok=True)
    rail = atlas(rail_tiles(), "linear_rail")
    carriage = atlas(carriage_tiles(), "linear_carriage")
    sheet = Image.new("RGB", (960, 660), "#20252d")
    d = ImageDraw.Draw(sheet)
    font_path = Path("C:/Windows/Fonts/consola.ttf")
    font = ImageFont.truetype(str(font_path), 25) if font_path.exists() else ImageFont.load_default()
    small = ImageFont.truetype(str(font_path), 18) if font_path.exists() else font
    d.text((40, 25), "LINEAR ENGINEERING / steel atlases", font=font, fill=WHITE)
    for art, x, label in ((rail, 40, "LINEAR RAIL"), (carriage, 520, "LINEAR CARRIAGE")):
        d.text((x, 82), label, font=font, fill=LIGHT)
        sheet.paste(art.resize((400, 400), Image.Resampling.NEAREST), (x, 126))
        d.text((x, 540), "TOP         SIDE", font=small, fill=LIGHT)
        d.text((x, 570), "END         UNDERSIDE", font=small, fill=LIGHT)
    d.text((40, 618), "32 x 32 px / forged steel / original pixels", font=small, fill=LIGHT)
    sheet.save(RENDERS / "texture-atlases.png", optimize=True)
    print("Generated two original 32x32 forged-steel atlases and contact sheet.")


if __name__ == "__main__":
    main()
