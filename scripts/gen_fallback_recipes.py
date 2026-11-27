"""Builds the local fallback recipe + tag files from the vanilla 1.21.1 data in the mapped Minecraft jar.

Keeps crafting (shaped/shapeless) and cooking recipes whose output the bot can obtain
(items referenced in TaskCatalogue.java / ItemHelper.java, plus catalogue name strings).
Usage: python3 -I scripts/gen_fallback_recipes.py <mapped minecraft jar from .gradle/loom-cache> .
"""
import json
import re
import sys
import zipfile
from pathlib import Path

jar_path, repo = sys.argv[1], Path(sys.argv[2])
src = repo / "src/main/java/adris/altoclef"
code = (src / "TaskCatalogue.java").read_text() + (src / "util/helpers/ItemHelper.java").read_text()

wanted = {"minecraft:" + m.lower() for m in re.findall(r"Items\.([A-Z0-9_]+)", code)}
wanted |= {"minecraft:" + s for s in re.findall(r'"([a-z0-9_]+)"', code)}

TYPES = {"minecraft:crafting_shaped", "minecraft:crafting_shapeless", "minecraft:smelting",
         "minecraft:blasting", "minecraft:smoking", "minecraft:campfire_cooking"}

def result_id(r):
    res = r["result"]
    return res if isinstance(res, str) else res.get("id", res.get("item"))

def tags_in(node, out):
    if isinstance(node, dict):
        if "tag" in node:
            out.add(node["tag"])
        for v in node.values():
            tags_in(v, out)
    elif isinstance(node, list):
        for v in node:
            tags_in(v, out)
    elif isinstance(node, str) and node.startswith("#"):
        out.add(node[1:])

recipes, used_tags = {}, set()
with zipfile.ZipFile(jar_path) as jar:
    names = jar.namelist()
    for name in sorted(n for n in names if n.startswith("data/minecraft/recipe/") and n.endswith(".json")):
        r = json.loads(jar.read(name))
        if r.get("type") not in TYPES or result_id(r) not in wanted:
            continue
        r.pop("category", None); r.pop("group", None); r.pop("show_notification", None)
        recipes["altoclef:" + Path(name).stem] = r
        tags_in({k: v for k, v in r.items() if k not in ("result", "pattern")}, used_tags)

    tags, todo = {}, sorted(used_tags)
    while todo:
        tag = todo.pop()
        key = tag if ":" in tag else "minecraft:" + tag
        if key in tags:
            continue
        path = "data/minecraft/tags/item/" + key.split(":", 1)[1] + ".json"
        values = []
        for v in json.loads(jar.read(path))["values"]:
            v = v["id"] if isinstance(v, dict) else v
            values.append(v)
            if v.startswith("#"):
                todo.append(v[1:])
        tags[key] = values

out = repo / "src/main/resources/assets/altoclef/recipes"
out.mkdir(parents=True, exist_ok=True)
header = "Generated from vanilla 1.21.1 data (see docs/recipe-model.md). Used only when the game provides no recipe."
(out / "fallback_recipes.json").write_text(json.dumps({"_comment": header, "recipes": recipes}, indent=1, sort_keys=True) + "\n")
(out / "fallback_tags.json").write_text(json.dumps({"_comment": header, "tags": dict(sorted(tags.items()))}, indent=1) + "\n")

outputs = {result_id(r) for r in recipes.values()}
print(f"wanted items: {len(wanted)}, recipes: {len(recipes)}, outputs covered: {len(outputs)}, tags: {len(tags)}")
by_type = {}
for r in recipes.values():
    by_type[r["type"]] = by_type.get(r["type"], 0) + 1
print(by_type)
for must in ["minecraft:crafting_table", "minecraft:stick", "minecraft:wooden_pickaxe", "minecraft:furnace",
             "minecraft:iron_ingot", "minecraft:torch", "minecraft:white_bed", "minecraft:oak_planks",
             "minecraft:charcoal", "minecraft:bucket", "minecraft:diamond_pickaxe", "minecraft:glass"]:
    print(("ok   " if must in outputs else "MISS ") + must)
