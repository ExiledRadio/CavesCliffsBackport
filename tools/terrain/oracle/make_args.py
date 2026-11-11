"""Writes vanilla.args (a java @argfile with the oracle project's runtime classpath) from cp.log."""
import io

cp = [l for l in io.open('cp.log', encoding='utf-8', errors='replace') if l.startswith('CP=')][0][3:].strip()
with io.open('vanilla.args', 'w', encoding='utf-8', newline='') as f:
    f.write('-Xmx3G\n-cp\n"' + cp.replace('\\', '/') + '"\n')
print(len(cp))
