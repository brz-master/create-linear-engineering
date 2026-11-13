"""Generate original inverse-T rail and mating U-carriage Minecraft models.

Coordinates are sixteenths. The canonical rail runs along +Z (south), with
its mounting foot at y=0. Both parts share an origin when assembled.
"""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/create_linear_engineering"


def element(start, end):
    x0, y0, z0 = start
    x1, y1, z1 = end
    # Each atlas quadrant is 16px. Crop in block coordinates so a pixel always
    # has the same size, even on the four-pixel foot and narrow running stem.
    face_uv = {
        "up": [x0 / 2, z0 / 2, x1 / 2, z1 / 2],
        "down": [8 + x0 / 2, 8 + z0 / 2, 8 + x1 / 2, 8 + z1 / 2],
        "north": [x0 / 2, 8 + (16 - y1) / 2, x1 / 2, 8 + (16 - y0) / 2],
        "south": [x0 / 2, 8 + (16 - y1) / 2, x1 / 2, 8 + (16 - y0) / 2],
        "west": [8 + z0 / 2, (16 - y1) / 2, 8 + z1 / 2, (16 - y0) / 2],
        "east": [8 + z0 / 2, (16 - y1) / 2, 8 + z1 / 2, (16 - y0) / 2],
    }
    return {"from": start, "to": end,
            "faces": {face: {"uv": uv, "texture": "#surface"}
                      for face, uv in face_uv.items()}}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


DIRECTIONS = {"down": (0, -1, 0), "up": (0, 1, 0), "north": (0, 0, -1),
              "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}
FACE_U = {"down": (1, 0, 0), "up": (1, 0, 0), "north": (-1, 0, 0),
          "south": (1, 0, 0), "west": (0, 0, 1), "east": (0, 0, -1)}
FACE_V = {"down": (0, 0, -1), "up": (0, 0, 1), "north": (0, -1, 0),
          "south": (0, -1, 0), "west": (0, -1, 0), "east": (0, -1, 0)}


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0])


def oriented(elements, facing, normal):
    z, y = DIRECTIONS[facing], DIRECTIONS[normal]
    x = cross(y, z)

    def transform(v):
        return tuple(x[i] * v[0] + y[i] * v[1] + z[i] * v[2] for i in range(3))

    result = []
    for source in elements:
        corners = [transform(tuple(p[i] - 8 for i in range(3)))
                   for p in (source["from"], source["to"])]
        target = {"from": [min(p[i] for p in corners) + 8 for i in range(3)],
                  "to": [max(p[i] for p in corners) + 8 for i in range(3)], "faces": {}}
        for face, properties in source["faces"].items():
            target_face = next(k for k, direction in DIRECTIONS.items()
                               if direction == transform(DIRECTIONS[face]))
            u = transform(FACE_U[face])
            new_u, new_v = FACE_U[target_face], FACE_V[target_face]
            candidates = [new_u, new_v, tuple(-v for v in new_u), tuple(-v for v in new_v)]
            rotation = candidates.index(u) * 90
            target["faces"][target_face] = dict(properties)
            if rotation:
                target["faces"][target_face]["rotation"] = rotation
        result.append(target)
    return result


def model(name, elements):
    textures = {"surface": f"create_linear_engineering:block/{name}",
                "particle": f"create_linear_engineering:block/{name}"}
    if name.endswith("carriage"):
        textures["lining"] = "create_linear_engineering:block/carriage_lining_none"
    return {"parent": "minecraft:block/block", "textures": textures,
            "elements": elements}


def lined(piece, *faces):
    for face in faces:
        piece["faces"][face]["texture"] = "#lining"
    return piece


def carriage_variants(variants):
    """Share geometry; grease coats the lining and faintly bleeds at its outer edge."""
    result = {}
    for mounting, base in variants.items():
        result[f"{mounting},grease=none"] = base
        for grease in ("low", "high"):
            name = f"block/greased/{base['model'].rsplit('/', 1)[-1]}_{grease}"
            write_json(ASSETS / "models" / f"{name}.json", {
                "parent": base["model"],
                "textures": {"lining": f"create_linear_engineering:block/carriage_lining_{grease}",
                             "surface": f"create_linear_engineering:block/carriage_surface_{grease}"}})
            result[f"{mounting},grease={grease}"] = {"model": f"create_linear_engineering:{name}"}
    return result


def main():
    models = {
        "linear_rail": [
            element([0, 0, 0], [16, 4, 16]),
            element([6, 4, 0], [10, 12, 16]),
        ],
        "linear_carriage": [
            lined(element([0, 12, 0], [16, 16, 16]), "down"),
            lined(element([0, 4, 0], [6, 12, 16]), "east", "down"),
            lined(element([10, 4, 0], [16, 12, 16]), "west", "down"),
        ],
    }
    for name, elements in models.items():
        write_json(ASSETS / "models/block" / f"{name}.json", model(name, elements))
        write_json(ASSETS / "models/item" / f"{name}.json", {
            "parent": f"create_linear_engineering:block/{name}",
        })
        variants = {}
        for facing, axis in DIRECTIONS.items():
            for normal, mount in DIRECTIONS.items():
                effective = normal
                if any(a * b for a, b in zip(axis, mount)):
                    effective = {"up": "north", "down": "south"}.get(facing, "up")
                suffix = f"{facing}_{effective}"
                path = f"block/oriented/{name}_{suffix}"
                if facing == "south" and effective == "up":
                    path = f"block/{name}"
                else:
                    write_json(ASSETS / "models" / f"{path}.json",
                               model(name, oriented(elements, facing, effective)))
                variants[f"facing={facing},normal={normal}"] = {
                    "model": f"create_linear_engineering:{path}"}
        write_json(ASSETS / "blockstates" / f"{name}.json", {
            "variants": carriage_variants(variants) if name.endswith("carriage") else variants})


if __name__ == "__main__":
    main()
