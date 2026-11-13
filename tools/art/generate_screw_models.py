"""Generate the motorized screw rail assets without modifying linear rail assets.

Fixed geometry uses the established +Z rail / +Y mounting normal. The rotating
partial is +Y like Create's shaft renderer and exactly matches its 4px section.
Run with --render for previews assembled from the shipped models and textures.
"""

import copy
import json
import sys
from pathlib import Path

from generate_models import ASSETS, DIRECTIONS, carriage_variants, element, lined, oriented, write_json

ROOT = Path(__file__).resolve().parents[2]


def block_model(texture, elements):
    textures = {"surface": f"create_linear_engineering:block/{texture}",
                "particle": f"create_linear_engineering:block/{texture}"}
    if any(face["texture"] == "#lining" for piece in elements for face in piece["faces"].values()):
        textures["lining"] = "create_linear_engineering:block/carriage_lining_none"
    return {"parent": "minecraft:block/block", "textures": textures,
            "elements": elements}


def generate():
    rail = [element([0, 0, 0], [16, 4, 16]),
            element([6, 4, 0], [10, 5, 16])]
    carriage = [lined(element([0, 12, 0], [16, 16, 16]), "down"),
                lined(element([0, 4, 0], [4, 12, 16]), "east", "down"),
                lined(element([12, 4, 0], [16, 12, 16]), "west", "down")]
    # Six small engagement teeth remain outside the 2*sqrt(2) px rotating
    # shaft envelope. Feet contact the rail foot; no mesh penetrates the shaft.
    for x0, x1 in ((4, 6.5), (9.5, 12)):
        for z0 in (3, 7, 11):
            tooth = element([x0, 10.5, z0], [x1, 12, z0 + 2])
            carriage.append(lined(tooth, *tooth["faces"]))
    for name, texture, elements in (("screw_rail", "linear_rail", rail),
                                     ("screw_carriage", "linear_carriage", carriage)):
        write_json(ASSETS / "models/block" / f"{name}.json", block_model(texture, elements))
        write_json(ASSETS / "models/item" / f"{name}.json",
                   {"parent": f"create_linear_engineering:block/{name}"})
        variants = {}
        for facing, axis in DIRECTIONS.items():
            for normal, mount in DIRECTIONS.items():
                effective = normal
                if any(a * b for a, b in zip(axis, mount)):
                    effective = {"up": "north", "down": "south"}.get(facing, "up")
                path = f"block/oriented/{name}_{facing}_{effective}"
                if facing == "south" and effective == "up":
                    path = f"block/{name}"
                else:
                    write_json(ASSETS / "models" / f"{path}.json",
                               block_model(texture, oriented(elements, facing, effective)))
                variants[f"facing={facing},normal={normal}"] = {
                    "model": f"create_linear_engineering:{path}"}
        write_json(ASSETS / "blockstates" / f"{name}.json", {
            "variants": carriage_variants(variants) if name.endswith("carriage") else variants})
    # Source Create shaft.json is [6,0,6]..[10,16,10], not a 6px shaft.
    shaft = {"from": [6, 0, 6], "to": [10, 16, 10], "faces": {}}
    for i, face in enumerate(("north", "east", "south", "west")):
        shaft["faces"][face] = {"uv": [i * 2, 0, (i + 1) * 2, 8], "texture": "#surface"}
    for face in ("up", "down"):
        shaft["faces"][face] = {"uv": [0, 8, 2, 10], "texture": "#surface"}
    write_json(ASSETS / "models/block/screw_shaft.json", block_model("screw_shaft", [shaft]))
    # Compose only the inventory model. In world the block renders the fixed
    # foot, while the kinetic renderer supplies the independently moving shaft.
    shaft_z = oriented([shaft], "down", "south")
    for part in shaft_z:
        for face in part["faces"].values():
            face["texture"] = "#screw"
    item = block_model("linear_rail", rail + shaft_z)
    item["textures"]["screw"] = "create_linear_engineering:block/screw_shaft"
    write_json(ASSETS / "models/item/screw_rail.json", item)


def render_previews():
    from PIL import Image, ImageDraw
    import render_blocks as renderer

    def load_voxels(parts):
        # Half-pixel grid preserves the new teeth exactly. Reuse the existing
        # face rasterizer rather than introducing a second rendering system.
        voxels = {}
        for name, offset in parts:
            if name.startswith("preview/"):
                base, facing, normal = name.removeprefix("preview/").rsplit("_", 2)
                model = json.loads((ASSETS / "models/item" / f"{base}.json").read_text())
                # The inventory shaft already has a Y-to-Z UV rotation.
                # Preserve it when applying a second mounting transform.
                for piece in model["elements"]:
                    for face in piece["faces"].values():
                        face["source_rotation"] = face.pop("rotation", 0)
                model["elements"] = oriented(model["elements"], facing, normal)
                for piece in model["elements"]:
                    for face in piece["faces"].values():
                        rotation = (face.get("rotation", 0) + face.pop("source_rotation")) % 360
                        face["rotation"] = rotation
            else:
                folder = "item" if name == "screw_rail" else "block"
                model = json.loads((ASSETS / "models" / folder / f"{name}.json").read_text())
            texture = renderer.load_textures(model)
            for source in model["elements"]:
                piece = copy.deepcopy(source)
                piece["from"] = [round(v * 2) for v in piece["from"]]
                piece["to"] = [round(v * 2) for v in piece["to"]]
                scaled_offset = tuple(round(v * 2) for v in offset)
                lo = [piece["from"][i] + scaled_offset[i] for i in range(3)]
                hi = [piece["to"][i] + scaled_offset[i] for i in range(3)]
                for x in range(lo[0], hi[0]):
                    for y in range(lo[1], hi[1]):
                        for z in range(lo[2], hi[2]):
                            voxels[x, y, z] = (piece, scaled_offset, texture)
        return voxels

    renderer.load_voxels = load_voxels
    output = ROOT / "docs/renders"
    output.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGB", (1600, 1380), renderer.BG)
    draw = ImageDraw.Draw(image)
    draw.text((64, 36), "CREATE: LINEAR ENGINEERING / SCREW DRIVE", font=renderer.font(42, True), fill=renderer.WHITE)
    draw.text((66, 94), "WORKED STEEL / FOUR-PIXEL CREATE SHAFT", font=renderer.font(20), fill=renderer.TEAL)
    for box in ((48, 150, 784, 650), (816, 150, 1552, 650),
                (48, 680, 784, 1280), (816, 680, 1552, 1280)):
        draw.rounded_rectangle(box, radius=12, fill=renderer.PANEL)
    draw.text((80, 174), "01 / ROTATING SCREW RAIL", font=renderer.font(23, True), fill=renderer.GOLD)
    draw.text((848, 174), "02 / TOOTHED CARRIAGE", font=renderer.font(23, True), fill=renderer.GOLD)
    renderer.render(draw, [("screw_rail", (0, 0, 0))], (100, 250, 730, 545), 6.5)
    renderer.render(draw, [("screw_carriage", (0, 0, 0))], (870, 250, 1500, 545), 6.5)
    draw.text((80, 592), "Fixed base with an independently rotating screw.", font=renderer.font(21), fill=renderer.MUTED)
    draw.text((848, 592), "Six inner teeth; feet in contact with the guide.", font=renderer.font(21), fill=renderer.MUTED)
    draw.text((80, 704), "ASSEMBLED / COMPLEMENTARY PROFILES", font=renderer.font(22, True), fill=renderer.GOLD)
    renderer.render(draw, [("screw_rail", (0, 0, z)) for z in (-16, 0, 16)] +
                    [("screw_carriage", (0, 0, 0))], (85, 800, 745, 1130), 3.9)
    draw.text((80, 1200), "Continuous guide, visible screw and engaged carriage.", font=renderer.font(20), fill=renderer.MUTED)
    draw.text((848, 704), "EXPLODED / ENGAGEMENT TEETH", font=renderer.font(23, True), fill=renderer.GOLD)
    renderer.render(draw, [("screw_rail", (0, 0, 0)), ("screw_carriage", (0, 22, 0))],
                    (865, 775, 1510, 1135), 4)
    draw.text((848, 1200), "Illustrative separation; parts touch in game.", font=renderer.font(20), fill=renderer.MUTED)
    draw.text((64, 1320), "Rendered from the distributed models and textures; not an in-game screenshot.",
              font=renderer.font(20), fill=renderer.MUTED)
    image.save(output / "screw-blocks.png")
    inventory = Image.new("RGB", (640, 250), "#c6c6c6")
    inventory_draw = ImageDraw.Draw(inventory)
    for i, name in enumerate(("screw_rail", "screw_carriage")):
        x = i * 320
        renderer.render(inventory_draw, [(name, (0, 0, 0))], (x + 40, 65, x + 104, 129), .85)
        renderer.render(inventory_draw, [(name, (0, 0, 0))], (x + 140, 35, x + 268, 163), 1.7)
        inventory_draw.text((x + 40, 184), name, font=renderer.font(17), fill="#333840")
    inventory_draw.text((40, 222), "64 / 128 px readability render, not an in-game screenshot", font=renderer.font(16), fill="#333840")
    inventory.save(output / "screw-inventory-preview.png")
    detail = Image.new("RGB", (1100, 800), renderer.BG)
    detail_draw = ImageDraw.Draw(detail)
    detail_draw.text((50, 35), "CARRIAGE UNDERSIDE / INNER TEETH", font=renderer.font(30, True), fill=renderer.WHITE)
    renderer.render(detail_draw, [("oriented/screw_carriage_south_down", (0, 0, 0))],
                    (100, 150, 1000, 650), 9)
    detail_draw.text((50, 734), "Six recessed teeth leave the screw free to rotate without intersecting the mesh.",
                     font=renderer.font(22), fill=renderer.MUTED)
    detail.save(output / "screw-teeth-detail.png")
    mounts = Image.new("RGB", (1600, 800), renderer.BG)
    mounts_draw = ImageDraw.Draw(mounts)
    for normal, x, label in (("down", 0, "CEILING"), ("east", 800, "WALL")):
        mounts_draw.text((x + 64, 48), label, font=renderer.font(30, True), fill=renderer.WHITE)
        parts = [(f"preview/screw_rail_south_{normal}", (0, 0, z)) for z in (-16, 0, 16)]
        parts.append((f"oriented/screw_carriage_south_{normal}", (0, 0, 0)))
        renderer.render(mounts_draw, parts, (x + 50, 160, x + 750, 650), 4.5)
    mounts_draw.text((64, 740), "The fixed base and drive screw share their axis in every orientation.",
                    font=renderer.font(22), fill=renderer.MUTED)
    mounts.save(output / "screw-mounting-orientations.png")


if __name__ == "__main__":
    generate()
    if "--render" in sys.argv:
        render_previews()
