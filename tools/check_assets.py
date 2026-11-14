"""Checks that every block state and item of this mod can be drawn and named, without a client.

A headless server cannot see a missing model or texture. The self-test (any -Pselftest run) writes
eclipse/selftest-states.txt and eclipse/selftest-items.txt from the real registries; this script checks
them against the resources:

  * every valid state of every block has a variant (plain "variants" files, vanilla format, default
    state mapping) or at least one applying part (multipart files; the client maps every state to one
    model name and picks parts from the state, so every state must match some part);
  * a variants file lists nothing the block cannot be in;
  * every model named exists, ours or 1.12.2's, and so does every parent up the chain;
  * every texture an element uses resolves and the file exists;
  * every item model request resolves the same way, and every item of the mod has one;
  * every item has a language entry;
  * every sound event in sounds.json points at files that exist;
  * each of the family's 18 other languages (Rule 4) has a lang file with every en_us key, no key en_us
    lacks and no empty value, and pack.mcmeta says pack_format 3 (2 would make 1.12.2 rewrite them).

Vanilla parents and textures come from the 1.12.2 client jar in the Gradle cache.

    python tools/check_assets.py
"""
import json
import os
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODID = "cavesandcliffsbackport"
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets")
MC_JAR = os.path.join(os.path.expanduser("~"), ".gradle", "caches", "minecraft", "net", "minecraft",
                      "minecraft", "1.12.2", "minecraft-1.12.2.jar")

jar = zipfile.ZipFile(MC_JAR)
jar_names = set(jar.namelist())
errors = []
model_cache = {}


def split(name):
    ns, _, path = name.rpartition(":")
    return (ns or "minecraft"), path


def read_asset(ns, path):
    if ns == "minecraft":
        key = "assets/minecraft/" + path
        return jar.read(key) if key in jar_names else None
    full = os.path.join(ASSETS, ns, *path.split("/"))
    if os.path.exists(full):
        with open(full, "rb") as f:
            return f.read()
    return None


def model_chain(ns, path, where):
    chain = []
    seen = set()
    while True:
        raw = read_asset(ns, "models/" + path + ".json")
        if raw is None:
            errors.append("%s: missing model %s:%s" % (where, ns, path))
            return None
        data = json.loads(raw.decode("utf-8"))
        chain.append(data)
        parent = data.get("parent")
        if not parent or parent.startswith("builtin/"):
            return chain
        ns, path = split(parent)
        if (ns, path) in seen:
            errors.append("%s: parent loop at %s" % (where, parent))
            return None
        seen.add((ns, path))


def check_model(name, where, default_dir):
    """default_dir is prefixed to blockstate model names ("block"), as 1.12.2's loader always does."""
    ns, path = split(name)
    if default_dir:
        path = default_dir + "/" + path
    key = (ns, path)
    if key in model_cache:
        if model_cache[key]:
            errors.append("%s: %s" % (where, model_cache[key]))
        return
    before = len(errors)
    chain = model_chain(ns, path, where)
    if chain is not None:
        textures = {}
        for data in reversed(chain):
            textures.update(data.get("textures", {}))
        used = set()
        elements = None
        for data in chain:
            if "elements" in data:
                elements = data["elements"]
                break
        for element in elements or []:
            for face in element.get("faces", {}).values():
                used.add(face["texture"])
        if any(d.get("parent") in ("item/generated", "builtin/generated") for d in chain):
            for k in textures:
                if k.startswith("layer"):
                    used.add("#" + k)
        if "particle" in textures:
            used.add("#particle")
        for ref in used:
            value = ref
            hops = 0
            while value is not None and value.startswith("#") and hops < 10:
                value = textures.get(value[1:])
                hops += 1
            if value is None or value.startswith("#"):
                errors.append("%s: model %s leaves %s unresolved" % (where, name, ref))
                continue
            tns, tpath = split(value)
            if read_asset(tns, "textures/" + tpath + ".png") is None:
                errors.append("%s: model %s needs missing texture %s" % (where, name, value))
    model_cache[key] = None if len(errors) == before else "(see first report of %s)" % name


def when_matches(when, props):
    if "OR" in when:
        return any(when_matches(w, props) for w in when["OR"])
    for k, v in when.items():
        if props.get(k) not in str(v).lower().split("|"):
            return False
    return True


def check_blocks(lines, lang):
    blocks = {}
    for registry, variant, kind in lines:
        blocks.setdefault(registry, (kind, []))[1].append(variant)
    checked = 0
    for registry, (kind, variants) in sorted(blocks.items()):
        ns, path = split(registry)
        raw = read_asset(ns, "blockstates/%s.json" % path)
        if raw is None:
            errors.append("%s: no blockstate file" % registry)
            continue
        data = json.loads(raw.decode("utf-8"))
        if "forge_marker" in data:
            errors.append("%s: expected a vanilla-format blockstate file" % registry)
            continue
        if kind == "multipart":
            parts = data.get("multipart")
            if not parts or "variants" in data:
                errors.append("%s: declared multipart, but the file is not multipart-only" % registry)
                continue
            for part in parts:
                applies = part.get("apply")
                for entry in applies if isinstance(applies, list) else [applies]:
                    check_model(entry["model"], "%s part" % registry, "block")
            for variant in variants:
                props = dict(p.split("=") for p in variant.split(",")) if variant != "normal" else {}
                if not any(when_matches(p.get("when", {}), props) for p in parts):
                    errors.append("%s[%s]: no multipart part applies" % (registry, variant))
                checked += 1
        else:
            listed = data.get("variants")
            if listed is None or "multipart" in data:
                errors.append("%s: expected a plain variants file" % registry)
                continue
            for variant in variants:
                if variant not in listed:
                    errors.append("%s: blockstate has no variant '%s'" % (registry, variant))
                    continue
                entries = listed[variant]
                for entry in entries if isinstance(entries, list) else [entries]:
                    check_model(entry["model"], "%s[%s]" % (registry, variant), "block")
                checked += 1
            extra = set(listed) - set(variants)
            if extra:
                errors.append("%s: blockstate lists states the block cannot be in: %s" % (registry, sorted(extra)[:5]))
        if "tile.%s.%s.name=" % (ns, path) not in lang:
            errors.append("%s: no block language entry" % registry)
    return len(blocks), checked


def check_items(lines, lang):
    for registry, meta, model, key in lines:
        if model == "NO_MODEL_REQUEST":
            errors.append("%s: item has no model request" % registry)
            continue
        check_model("%s:item/%s" % (MODID, model), "%s@%s item" % (registry, meta), "")
        if key + "=" not in lang:
            errors.append("%s@%s: no language entry %s" % (registry, meta, key))
    return len(lines)


_INDEX_112 = None


def index_112():
    """@return 1.12's asset index (the launcher's copy the generators also read), or {} when it is not there."""
    global _INDEX_112
    if _INDEX_112 is None:
        path = "G:/Sort this shit/Minecraft/Install/assets/indexes/1.12.json"
        _INDEX_112 = json.load(open(path, encoding="utf-8"))["objects"] if os.path.exists(path) else {}
    return _INDEX_112


def check_sounds():
    sounds = json.loads(open(os.path.join(ASSETS, MODID, "sounds.json"), encoding="utf-8").read())
    count = 0
    for event, spec in sounds.items():
        for sound in spec.get("sounds", []):
            name = sound["name"] if isinstance(sound, dict) else sound
            if isinstance(sound, dict) and sound.get("type") == "event":
                continue
            sns, spath = split(name)
            # 1.12.2's own sounds are not in its jar but in its asset index, which every game has.
            vanilla = sns == "minecraft" and "minecraft/sounds/%s.ogg" % spath in index_112()
            if not vanilla and read_asset(sns, "sounds/%s.ogg" % spath) is None:
                errors.append("sound %s: missing %s" % (event, name))
            count += 1
        if "subtitle" in spec and spec["subtitle"] + "=" not in LANG and spec["subtitle"] not in OLD_LANG:
            errors.append("sound %s: no subtitle entry %s" % (event, spec["subtitle"]))
    return count


OLD_LANG = set(l.split("=", 1)[0] for l in jar.read("assets/minecraft/lang/en_us.lang").decode("utf-8").splitlines() if "=" in l)
LANG = open(os.path.join(ASSETS, MODID, "lang", "en_us.lang"), encoding="utf-8").read()


# Rule 4 of the family plan: the eighteen languages besides English (1.12.2's lowercase file names).
LANGUAGES = ["es_es", "es_mx", "es_ar", "es_uy", "es_ve", "pt_br", "fr_fr", "de_de", "ru_ru", "zh_cn",
             "ja_jp", "ko_kr", "it_it", "pl_pl", "tr_tr", "uk_ua", "zh_tw", "nl_nl"]


def lang_keys(path):
    """@return {key: value} of a lang file, adding an error for each duplicate key."""
    out = {}
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\r\n")
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key in out:
            errors.append("%s: duplicate key %s" % (os.path.basename(path), key))
        out[key] = value
    return out


def check_languages():
    lang_dir = os.path.join(ASSETS, MODID, "lang")
    english = lang_keys(os.path.join(lang_dir, "en_us.lang"))
    mcmeta = os.path.join(ROOT, "src", "main", "resources", "pack.mcmeta")
    if not os.path.exists(mcmeta):
        errors.append("pack.mcmeta missing: 1.12.2 would look for en_US.lang and find none of the lang files")
    elif json.load(open(mcmeta, encoding="utf-8"))["pack"].get("pack_format") != 3:
        errors.append("pack.mcmeta: pack_format must be 3 for lowercase lang file names")
    for code in LANGUAGES:
        path = os.path.join(lang_dir, code + ".lang")
        if not os.path.exists(path):
            errors.append("lang %s: no file" % code)
            continue
        values = lang_keys(path)
        for key in english:
            if key not in values:
                errors.append("lang %s: missing %s" % (code, key))
            elif not values[key].strip():
                errors.append("lang %s: empty %s" % (code, key))
        for key in values:
            if key not in english:
                errors.append("lang %s: %s is not in en_us" % (code, key))
    return len(LANGUAGES), len(english)


def main():
    run = os.path.join(ROOT, "eclipse")
    states = [l.rstrip("\n").split("\t") for l in open(os.path.join(run, "selftest-states.txt"), encoding="utf-8") if l.strip()]
    items = [l.rstrip("\n").split("\t") for l in open(os.path.join(run, "selftest-items.txt"), encoding="utf-8") if l.strip()]
    nblocks, nstates = check_blocks(states, LANG)
    nitems = check_items(items, LANG)
    nsounds = check_sounds()
    nlangs, nkeys = check_languages()
    for e in errors:
        print("FAIL", e)
    print("%d blocks, %d states, %d item models, %d sound files, %d languages x %d keys checked, %d errors"
          % (nblocks, nstates, nitems, nsounds, nlangs, nkeys, len(errors)))
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
