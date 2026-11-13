"""Prints each has_structure biome tag of a Mojang client jar, resolved (nested #tags expanded), and what changed
between jars. Usage: structure_biome_tags.py <client.jar> [<client.jar> ...]
"""
import json
import sys
import zipfile

PREFIX = 'data/minecraft/tags/worldgen/biome/'


def resolve(z, name, seen=None):
    seen = seen or set()
    data = json.loads(z.read(PREFIX + name + '.json'))
    out = []
    for v in data['values']:
        v = v if isinstance(v, str) else v['id']
        if v.startswith('#'):
            t = v[1:].split(':')[-1]
            if t not in seen:
                seen.add(t)
                out += [x for x in resolve(z, t, seen) if x not in out]
        else:
            b = v.split(':')[-1]
            if b not in out:
                out.append(b)
    return out


tables = []
for jar in sys.argv[1:]:
    z = zipfile.ZipFile(jar)
    tags = {}
    for n in z.namelist():
        if n.startswith(PREFIX + 'has_structure/') and n.endswith('.json'):
            t = n[len(PREFIX):-5]
            tags[t] = resolve(z, t)
    tables.append((jar, tags))
prev = None
for jar, tags in tables:
    print(jar)
    for t in sorted(tags):
        if prev is None:
            print('  %s: %s' % (t, ' '.join(tags[t])))
        elif t not in prev:
            print('  new %s: %s' % (t, ' '.join(tags[t])))
        elif set(tags[t]) != set(prev[t]):
            print('  %s: +%s -%s' % (t, sorted(set(tags[t]) - set(prev[t])), sorted(set(prev[t]) - set(tags[t]))))
    prev = tags
