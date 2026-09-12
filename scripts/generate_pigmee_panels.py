#!/usr/bin/env python3
"""Author the geometric 16px panel art and data from one editable palette.

Only the new Pigmee panel resources and their tag/language entries are owned here.
No existing texture is recolored or modified. Compact CTM quadrant placement follows
CtmTileSelector; the 1px shadow / 1px rim matches the approved isolated/T/full cases.
Run from any directory: python3 scripts/generate_pigmee_panels.py
"""
import json
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/ae2lt"
DATA = RES / "data/ae2lt"
# Minecraft dye order. Muted colors remain distinct, with the same body hue in both styles.
PALETTE = [
    ("white", "白色", "D9DEDC"), ("orange", "橙色", "C98D55"),
    ("magenta", "品红色", "B676A7"), ("light_blue", "淡蓝色", "8CB6CC"),
    ("yellow", "黄色", "D6BD69"), ("lime", "黄绿色", "9CB870"),
    ("pink", "粉红色", "DBA9B4"), ("gray", "灰色", "777D81"),
    ("light_gray", "淡灰色", "B5B8B1"), ("cyan", "青色", "649D9D"),
    ("purple", "紫色", "8E7BAC"), ("blue", "蓝色", "677FAE"),
    ("brown", "棕色", "93755C"), ("green", "绿色", "758761"),
    ("red", "红色", "B46D67"), ("black", "黑色", "454B52"),
]


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")


def panel_id(color, framed=False):
    return f"{color}_pigmee_{'framed_' if framed else ''}building_panel"


def rgb(value):
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def shade(color, scale=1, add=0):
    return tuple(max(0, min(255, round(c * scale + add))) for c in color)


def face_pixel(color, framed, x, y, edges, corners):
    distance = 100
    for bit, d in enumerate((y + 1, 16 - x, 16 - y, x + 1)):
        if not edges & (1 << bit):
            distance = min(distance, d)
    quadrants = ((0, 3, max(x + 1, y + 1)), (0, 1, max(16 - x, y + 1)),
                 (2, 1, max(16 - x, 16 - y)), (2, 3, max(x + 1, 16 - y)))
    for q, (a, b, d) in enumerate(quadrants):
        if edges & (1 << a) and edges & (1 << b) and not corners & (1 << q):
            distance = min(distance, d)
    # Sparse 2px flecks, with only two levels of variation; no grout across a joined face.
    grain = ((x // 2) * 17 + (y // 2) * 31 + (x // 4) * (y // 4) * 7) % 11
    if distance == 1:
        return shade(color, .68 if framed else .955)
    if distance == 2 and framed:
        return shade(color, .88, 36)
    return shade(color, add=-2 if grain == 0 else 2 if grain == 3 else 0)


def texture(color, framed, ctm=False):
    size = 32 if ctm else 16
    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            if not ctm:
                row.append(face_pixel(color, framed, x, y, 0, 0))
                continue
            qx, qy = (x // 8) % 2, (y // 8) % 2
            px, py = qx * 8 + x % 8, qy * 8 + y % 8
            q = (0 if qx == 0 else 1) if qy == 0 else (3 if qx == 0 else 2)
            if x < 16 and y < 16:
                edges, corners = 15, 15
            elif y < 16:
                edges, corners = 1 << (0 if qy == 0 else 2), 0
            elif x < 16:
                edges, corners = 1 << (3 if qx == 0 else 1), 0
            else:
                edges, corners = 15, 15 ^ (1 << q)
            row.append(face_pixel(color, framed, px, py, edges, corners))
        pixels.append(row)
    return pixels


def write_png(path, pixels):
    """Lossless native rasterization of our pixel geometry; no image library required."""
    height, width = len(pixels), len(pixels[0])
    raw = b"".join(b"\0" + bytes(c for pixel in row for c in pixel) for row in pixels)
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    result = b"\x89PNG\r\n\x1a\n"
    result += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
    result += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(result)


def unlock(recipe_name, ingredient):
    write_json(DATA / f"advancement/recipes/pigmee_building/{recipe_name}.json", {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_material": {"trigger": "minecraft:inventory_changed", "conditions": {
                "items": [{"items": ingredient}]}},
            "has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {
                "recipe": f"ae2lt:{recipe_name}"}}},
        "requirements": [["has_material", "has_the_recipe"]],
        "rewards": {"recipes": [f"ae2lt:{recipe_name}"]}})


def stonecut(name, ingredient, output):
    write_json(DATA / f"recipe/{name}.json", {"type": "minecraft:stonecutting",
        "ingredient": {"tag": ingredient[1:]} if ingredient.startswith("#") else {"item": ingredient},
        "result": {"id": output, "count": 1}})
    unlock(name, ingredient)


def generate():
    langs = {locale: json.loads((ASSETS / f"lang/{locale}.json").read_text())
             for locale in ("en_us", "zh_cn")}
    all_ids = []
    for color, zh, hex_value in PALETTE:
        for framed in (False, True):
            name = panel_id(color, framed)
            identifier = f"ae2lt:{name}"
            all_ids.append(identifier)
            texture_path = f"ae2lt:block/pigmee_building/{name}"
            write_json(ASSETS / f"blockstates/{name}.json", {
                "variants": {"": {"model": f"ae2lt:block/{name}"}}})
            write_json(ASSETS / f"models/block/{name}.json", {
                "parent": "minecraft:block/block", "loader": "ae2lt:connected_texture",
                "render_type": "minecraft:solid", "connection": "ae2lt:same_block",
                "textures": {"base": texture_path, "ctm": texture_path + "_ctm", "particle": texture_path}})
            # Native cube transforms in hands/GUI; world-only connectivity has no meaning for an item.
            write_json(ASSETS / f"models/item/{name}.json", {
                "parent": "minecraft:block/cube_all", "textures": {"all": texture_path}})
            for ctm in (False, True):
                write_png(ASSETS / f"textures/block/pigmee_building/{name}{'_ctm' if ctm else ''}.png",
                          texture(rgb(hex_value), framed, ctm))
            write_json(DATA / f"loot_table/blocks/{name}.json", {"type": "minecraft:block",
                "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": identifier}],
                           "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
            stonecut(name + "_stonecutting", "#ae2lt:pigmee_building_materials", identifier)
            langs["en_us"][f"block.ae2lt.{name}"] = color.replace("_", " ").title() + (
                " Pigmee Framed Panel" if framed else " Pigmee Plain Panel")
            langs["zh_cn"][f"block.ae2lt.{name}"] = zh + ("猪咪框板" if framed else "猪咪素板")
    for framed in (False, True):
        tag = "pigmee_framed_building_panels" if framed else "pigmee_building_panels"
        ids = [f"ae2lt:{panel_id(color, framed)}" for color, _, _ in PALETTE]
        for registry in ("item", "block"):
            write_json(DATA / f"tags/{registry}/{tag}.json", {"replace": False, "values": ids})
    write_json(DATA / "tags/item/pigmee_building_materials.json", {"replace": False,
        "values": ["ae2lt:pigmee_building_block", "#ae2lt:pigmee_finished_building_panels"]})
    write_json(DATA / "tags/item/pigmee_finished_building_panels.json", {"replace": False,
        "values": ["#ae2lt:pigmee_building_panels", "#ae2lt:pigmee_framed_building_panels"]})
    restore_recipe = "pigmee_building_block_from_panels"
    write_json(DATA / f"recipe/{restore_recipe}.json", {"type": "minecraft:crafting_shapeless",
        "category": "building", "ingredients": [{"tag": "ae2lt:pigmee_finished_building_panels"}],
        "result": {"id": "ae2lt:pigmee_building_block", "count": 1}})
    unlock(restore_recipe, "#ae2lt:pigmee_finished_building_panels")
    pickaxe_path = RES / "data/minecraft/tags/block/mineable/pickaxe.json"
    pickaxe = json.loads(pickaxe_path.read_text())
    for identifier in all_ids:
        if identifier not in pickaxe["values"]:
            pickaxe["values"].append(identifier)
    write_json(pickaxe_path, pickaxe)
    langs["en_us"]["tooltip.ae2lt.pigmee_building_panel"] = (
        "Connects to panels of the same color and style. Stonecut 1:1 into any of the 32 colors and styles; no dye needed.")
    langs["zh_cn"]["tooltip.ae2lt.pigmee_building_panel"] = (
        "同色同款自动连纹；切石机可 1∶1 自由切换全部 32 种颜色与款式，无需染料。")
    langs["en_us"]["tooltip.ae2lt.pigmee_building_panel.restore"] = (
        "Craft 1 panel into 1 basic Pigmee Building Block in any crafting grid.")
    langs["zh_cn"]["tooltip.ae2lt.pigmee_building_panel.restore"] = (
        "在任意合成栏中放入 1 块成品，可还原为 1 个基础猪咪建材。")
    langs["en_us"]["tooltip.ae2lt.pigmee_building_block.styles"] = (
        "Stonecut 1:1 into any of the 32 colored plain or framed panels; no dye needed.")
    langs["zh_cn"]["tooltip.ae2lt.pigmee_building_block.styles"] = (
        "切石机可 1∶1 直接制作全部 32 种彩色素板与框板，无需染料。")
    for locale, values in langs.items():
        write_json(ASSETS / f"lang/{locale}.json", values)
    print("Generated 32 panel blocks, 64 textures, 32 dye-free stonecutting recipes, and 1 crafting return recipe.")


if __name__ == "__main__":
    generate()
