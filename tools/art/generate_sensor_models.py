"""Generate vanilla blockstate variants inheriting existing rail geometry.

Only the opaque surface atlas changes. No overlay quads, extra geometry, or
per-frame sensor renderer; orientation/UVs remain owned by the base models.
"""

import copy
import json
import sys
from pathlib import Path

from generate_models import ASSETS, write_json

ROOT = Path(__file__).resolve().parents[2]
MODES = ("approximate", "precise")
STATES = ("off", "on")


def generate():
    # Regenerate canonical variants first; this also makes repeated runs idempotent.
    import generate_models
    import generate_screw_models
    generate_models.main()
    generate_screw_models.generate()
    for rail in ("linear_rail", "screw_rail"):
        path = ASSETS / "blockstates" / f"{rail}.json"
        base_variants = json.loads(path.read_text(encoding="utf-8"))["variants"]
        variants = {}
        for mounting, base in base_variants.items():
            variants[f"{mounting},sensor=none"] = base
            for mode in MODES:
                for state in STATES:
                    name = f"block/sensors/{base['model'].rsplit('/', 1)[-1]}_{mode}_{state}"
                    write_json(ASSETS / "models" / f"{name}.json", {
                        "parent": base["model"],
                        "textures": {"surface": f"create_linear_engineering:block/rail_sensor_{mode}_{state}"}})
                    variants[f"{mounting},sensor={mode},powered={'true' if state == 'on' else 'false'}"] = {
                        "model": f"create_linear_engineering:{name}"}
        write_json(path, {"variants": variants})
    write_json(ASSETS / "models/item/rail_sensor.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "create_linear_engineering:item/rail_sensor"}})


def render_previews():
    from PIL import Image, ImageDraw
    import render_blocks as renderer

    base_surface_color = renderer.surface_color

    def load_voxels(parts):
        sensor = next((name for name, _ in parts if name.startswith("rail_sensor_")), None)
        voxels = {}
        for name, offset in parts:
            if name.startswith("rail_sensor_"): continue
            folder = "item" if name == "screw_rail" else "block"
            source = json.loads((ASSETS / "models" / folder / f"{name}.json").read_text())
            if sensor and name in ("linear_rail", "screw_rail"):
                source["textures"]["surface"] = f"create_linear_engineering:block/{sensor}"
            texture = renderer.load_textures(source)
            for original in source["elements"]:
                piece = copy.deepcopy(original)
                piece["from"] = [round(v * 4) for v in piece["from"]]
                piece["to"] = [round(v * 4) for v in piece["to"]]
                delta = tuple(round(v * 4) for v in offset)
                lo = [piece["from"][i] + delta[i] for i in range(3)]
                hi = [piece["to"][i] + delta[i] for i in range(3)]
                for x in range(lo[0], hi[0]):
                    for y in range(lo[1], hi[1]):
                        for z in range(lo[2], hi[2]):
                            voxels[x, y, z] = (piece, delta, texture)
        return voxels

    surface_color = base_surface_color

    renderer.load_voxels = load_voxels
    renderer.surface_color = surface_color
    output = ROOT / "docs/renders"
    output.mkdir(parents=True, exist_ok=True)

    for rail, label, filename in (("linear_rail", "LINEAR RAIL", "sensor-blocks.png"),
                                   ("screw_rail", "SCREW RAIL", "sensor-screw-blocks.png")):
        image = Image.new("RGB", (1600, 1200), renderer.BG)
        draw = ImageDraw.Draw(image)
        draw.text((64, 36), f"RAIL SENSOR / {label}", font=renderer.font(40, True), fill=renderer.WHITE)
        draw.text((66, 94), "BASE PERIMETER WIRE / DOT OR STRIP ON BOTH LEDGES", font=renderer.font(19), fill=renderer.TEAL)
        for row, mode in enumerate(MODES):
            for col, state in enumerate(STATES):
                x, y = 48 + col * 768, 150 + row * 455
                draw.rounded_rectangle((x, y, x + 736, y + 425), radius=12, fill=renderer.PANEL)
                title = f"{'APPROXIMATE' if mode == 'approximate' else 'PRECISE'} / {'ON' if state == 'on' else 'OFF'}"
                draw.text((x + 30, y + 24), title, font=renderer.font(23, True), fill=renderer.GOLD)
                renderer.render(draw, [(rail, (0, 0, 0)), (f"rail_sensor_{mode}_{state}", (0, 0, 0))],
                                (x + 70, y + 90, x + 666, y + 340), 2.6)
                text = "Long strip along the rail axis." if mode == "approximate" else "Central dot on both ledges."
                draw.text((x + 30, y + 372), text, font=renderer.font(21), fill=renderer.MUTED)
        draw.text((64, 1110), "Circuit integrated into the steel texture, with no raised plate.", font=renderer.font(22), fill=renderer.MUTED)
        draw.text((64, 1154), "Rendered from the final models and textures; not an in-game screenshot.", font=renderer.font(20), fill=renderer.MUTED)
        image.save(output / filename)

    # Top view reads actual model faces, not an illustrative redrawn schematic.
    states = Image.new("RGB", (1400, 850), renderer.BG)
    states_draw = ImageDraw.Draw(states)
    states_draw.text((50, 28), "TOP VIEW / DOT AND STRIP", font=renderer.font(34, True), fill=renderer.WHITE)
    for row, (rail, label) in enumerate((("linear_rail", "Linear"), ("screw_rail", "Screw"))):
        for col, (mode, state) in enumerate((m, s) for m in MODES for s in STATES):
            parts = [(rail, (0, 0, 0)), (f"rail_sensor_{mode}_{state}", (0, 0, 0))]
            voxels = load_voxels(parts)
            tops = {}
            for (x, y, z), cell in voxels.items():
                if (x, z) not in tops or y > tops[x, z][0]:
                    tops[x, z] = (y, cell)
            view = Image.new("RGB", (64, 64), renderer.PANEL)
            for (x, z), (y, cell) in tops.items():
                view.putpixel((x, z), surface_color(cell, (x + .5, y + 1, z + .5), "up"))
            x, y = 50 + col * 340, 140 + row * 345
            states.paste(view.resize((272, 272), Image.Resampling.NEAREST), (x, y))
            text = f"{label}: {'strip' if mode == 'approximate' else 'dot'} / {'on' if state == 'on' else 'off'}"
            states_draw.text((x, y - 35), text, font=renderer.font(19), fill=renderer.MUTED)
    states_draw.text((50, 808), "The pale red wire follows the perimeter; dots and strips indicate the detection area.", font=renderer.font(21), fill=renderer.MUTED)
    states.save(output / "sensor-states.png")

    detail = Image.new("RGB", (1600, 800), renderer.BG)
    detail_draw = ImageDraw.Draw(detail)
    detail_draw.text((64, 36), "FLUSH CIRCUIT / FREE CARRIAGES", font=renderer.font(36, True), fill=renderer.WHITE)
    for x, rail, carriage in ((0, "linear_rail", "linear_carriage"), (800, "screw_rail", "screw_carriage")):
        renderer.render(detail_draw, [(rail, (0, 0, 0)), (carriage, (0, 0, 0)),
                        ("rail_sensor_precise_on", (0, 0, 0))], (x + 80, 150, x + 720, 635), 3.1)
    detail_draw.text((64, 718), "The circuit stays flush with the base; passing carriages cover it without obstruction.",
                     font=renderer.font(22), fill=renderer.MUTED)
    detail.save(output / "sensor-detail.png")

    item = Image.open(ASSETS / "textures/item/rail_sensor.png").convert("RGBA")
    inventory = Image.new("RGB", (640, 280), "#c6c6c6")
    inventory_draw = ImageDraw.Draw(inventory)
    for x, size in ((100, 64), (340, 128)):
        preview = item.resize((size, size), Image.Resampling.NEAREST)
        inventory.paste(preview, (x, 35), preview)
    inventory_draw.text((90, 194), "Rail Sensor / rail-mounted circuit", font=renderer.font(21), fill="#333840")
    inventory_draw.text((90, 238), "Item texture at 64 and 128 px, not a screenshot.", font=renderer.font(18), fill="#333840")
    inventory.save(output / "sensor-inventory-preview.png")


if __name__ == "__main__":
    generate()
    if "--render" in sys.argv:
        render_previews()
