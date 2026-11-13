"""Generate the original threaded-shaft atlas for the screw rail.

The 4px shaft matches Create's shaft dimensions, with an original helical
iron surface. The existing linear mechanism palettes are reused unchanged.
Atlas 32x32: side wrap [0,0..16,16], 4px endcap [0,16..4,20].
Clockwise side strips are north, east, south, west, each 4x16px.
"""

from PIL import Image, ImageDraw

from generate_textures import DEEP, EDGE, INK, PATCH, STEEL, TEXTURES


# Four turns per block, one continuous thread around all four shaft faces.
def thread_color(circumference, height):
    phase = (4 * height - circumference) % 16
    if phase < 3:
        return DEEP
    if phase < 7:
        return STEEL
    if phase < 11:
        return EDGE
    return PATCH


def main():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    texture = Image.new("RGB", (32, 32), STEEL)
    draw = ImageDraw.Draw(texture)
    for height in range(16):
        for circumference in range(16):
            draw.point((circumference, height), fill=thread_color(circumference, height))
    # A plain machined end, without a connector, symbol or painted decoration.
    draw.rectangle((0, 16, 3, 19), fill=DEEP)
    draw.rectangle((1, 17, 2, 18), fill=STEEL)
    draw.point((1, 17), fill=EDGE)
    draw.point((3, 19), fill=INK)
    texture.save(TEXTURES / "screw_shaft.png", optimize=True)
    print("Generated original 4px threaded shaft atlas (32x32).")


if __name__ == "__main__":
    main()
