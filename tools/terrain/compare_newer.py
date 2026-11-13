"""Compares the newer vanilla oracles' biome tables (tools/terrain/oracle-newer) with each other and with 1.18.2's
FeatureTables.java: per-step feature order, per-biome feature lists. Usage: compare_newer.py <oracle.txt> [<oracle.txt>]
"""
import io
import os
import re
import sys

here = os.path.dirname(os.path.abspath(__file__))
core = os.path.join(here, '..', '..', 'src', 'main', 'java', 'com', 'exiledradio', 'cavesandcliffsbackport', 'terrain',
                    'core')


def read(path):
    d = {'step': {}, 'biome': {}, 'leaf': [], 'climate': {}}
    for line in io.open(path, encoding='utf-8'):
        line = line.rstrip('\n')
        head, _, rest = line.partition(' :')
        p = head.split()
        if p[0] == 'step':
            d['step'][(p[1], int(p[2]))] = rest.split()
        elif p[0] == 'biome':
            d['biome'].setdefault(p[1], {})[int(p[3])] = rest.split()
        elif p[0] == 'leaf':
            d['leaf'].append(p[1:])
        elif p[0] == 'climate':
            d['climate'][p[1]] = p[2:]
    return d


def tables_1182():
    s = io.open(os.path.join(core, 'FeatureTables.java'), encoding='utf-8').read()
    steps_src = s[s.index('STEPS = {'):s.index('};', s.index('STEPS = {'))]
    steps = [re.findall(r'"([^"]+)"', row) for row in re.findall(r'\{([^{}]*)\}', steps_src)]
    names = re.search(r'NAMES = \{(.*?)\};', io.open(os.path.join(core, 'Biomes.java'), encoding='utf-8').read(), re.S)
    biome_names = re.findall(r'"([^"]+)"', names.group(1))
    bsrc = s[s.index('BIOMES = {'):]
    biomes = {}
    for m in re.finditer(r'\{ // (\d+)\n(.*?)\n        \},', bsrc, re.S):
        rows = [re.findall(r'"([^"]+)"', r) for r in re.findall(r'\{([^{}]*)\}', m.group(2))]
        biomes[biome_names[int(m.group(1))]] = dict(enumerate(rows))
    return steps, biomes


def diff_lists(tag, a, b):
    if a == b:
        print('  %s: identical (%d)' % (tag, len(a)))
        return
    sa, sb = set(a), set(b)
    print('  %s: differ: only first %s, only second %s' % (tag, sorted(sa - sb), sorted(sb - sa)))
    common = [x for x in a if x in sb]
    common2 = [x for x in b if x in sa]
    if common != common2:
        print('    and the common features are in a different order')


steps1182, biomes1182 = tables_1182()
for path in sys.argv[1:]:
    d = read(path)
    print(path)
    for (tag, k), names in sorted(d['step'].items()):
        if tag in ('nowild', 'full') and k < len(steps1182):
            diff_lists('step %s %d vs 1.18.2' % (tag, k), steps1182[k], names)
    for b, st in sorted(d['biome'].items()):
        if b in biomes1182:
            for k in sorted(set(st) | set(biomes1182[b])):
                if st.get(k, []) != biomes1182[b].get(k, []):
                    diff_lists('biome %s step %d vs 1.18.2' % (b, k), biomes1182[b].get(k, []), st.get(k, []))
        else:
            print('  new biome', b, d['climate'].get(b))
