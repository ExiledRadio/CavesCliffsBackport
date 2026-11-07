"""The shared language lines: the creative tab, and every config category and option.

Config lines are read from ModConfig.java, so a stream that adds an option only reruns this:

    python tools/common/generate.py

Each @Config.LangKey gets a name line and a ".tooltip" line (the option's @Config.Comment). Category names
are below; an option's name is its field name in words ("rawOreDrops" -> "Raw ore drops").
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

CATEGORY_NAMES = {
    "deepslate": "Deepslate and Stone",
    "copper": "Copper",
    "amethyst": "Amethyst",
    "dripstone": "Dripstone",
    "lush": "Lush Caves",
    "powderSnow": "Powder Snow",
    "candles": "Candles",
    "glowSquid": "Glow Squid",
    "axolotls": "Axolotls",
    "goats": "Goats",
    "blocks": "Other Blocks and Items",
    "music": "Music",
    "caves": "Cave Regions",
}

# Option names that words-from-camelCase would get wrong.
OPTION_NAMES = {
    "enabled": "Enabled",
    "minY": "Lowest height",
    "maxY": "Highest height",
    "geodeMinY": "Lowest geode height",
    "geodeMaxY": "Highest geode height",
}

SRC = os.path.join(assets.ROOT, "src", "main", "java", "com", "exiledradio", assets.MODID, "ModConfig.java")


def words(field):
    if field in OPTION_NAMES:
        return OPTION_NAMES[field]
    w = re.sub(r"(?<!^)(?=[A-Z])", " ", field).lower()
    return w[0].upper() + w[1:]


def comment_text(raw):
    parts = re.findall(r'"((?:[^"\\]|\\.)*)"', raw)
    return " ".join(p.replace('\\"', '"') for p in parts)


def config_lines():
    text = open(SRC, encoding="utf-8").read()
    lines = []
    # Every annotated declaration: a run of annotations, then a field.
    for block in re.finditer(r"((?:\s*@Config\.\w+(?:\((?:[^()]|\([^()]*\))*\))?)+)\s*public\s+(?:static\s+final\s+)?\w+\s+(\w+)\s*=", text):
        annotations, field = block.group(1), block.group(2)
        key = re.search(r'@Config\.LangKey\("([^"]+)"\)', annotations)
        com = re.search(r"@Config\.Comment\(((?:[^()]|\([^()]*\))*)\)", annotations)
        if not key:
            raise SystemExit("ModConfig field %s has no @Config.LangKey" % field)
        key = key.group(1)
        category = key.split(".")[2]
        if key.count(".") == 2:
            name = CATEGORY_NAMES.get(category)
            if name is None:
                raise SystemExit("no name for config category %s in tools/common/generate.py" % category)
        else:
            name = words(field)
        lines.append("%s=%s" % (key, name))
        if com:
            lines.append("%s.tooltip=%s" % (key, comment_text(com.group(1))))
    return lines


assets.merge_lang("common", ["itemGroup.%s=Caves & Cliffs Backport" % assets.MODID] + config_lines())
print("common: %d config lines" % (len(config_lines())))
