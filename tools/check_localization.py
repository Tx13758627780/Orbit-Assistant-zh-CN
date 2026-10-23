#!/usr/bin/env python3
"""Check paired resources, formatting arguments, and Java resource references (stdlib only)."""
from pathlib import Path
import re
import json
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'

def strings(directory):
    result = {}
    for path in (RES / directory).glob('*.xml'):
        for node in ET.parse(path).getroot().findall('string'):
            name = node.attrib['name']
            if name in result:
                raise ValueError(f'Duplicate string {name} in {directory}')
            result[name] = ''.join(node.itertext())
    return result

base, chinese = strings('values'), strings('values-zh-rCN')
base_ui = {k for k in base if k.startswith('ui_')}
chinese_ui = {k for k in chinese if k.startswith('ui_')}
assert base_ui == chinese_ui, (base_ui - chinese_ui, chinese_ui - base_ui)
format_arg = re.compile(r'%(?:\d+\$)?\d*(?:\.\d+)?[sdfox]')
for name in base_ui:
    assert format_arg.findall(base[name]) == format_arg.findall(chinese[name]), name
    assert re.search(r'[\u3400-\u9fff]', chinese[name]) or not re.search(r'[A-Za-z]', chinese[name]), f'Untranslated: {name}'
def android_text(value):
    if value.startswith('"') and value.endswith('"'):
        value = value[1:-1]
    return re.sub(r'\\(.)', lambda m: {'n': '\n', 'r': '\r', 't': '\t'}.get(m[1], m[1]), value)

resource_call = re.compile(r'OrbitText\.get\(R\.string\.(ui_\w+),\s*("(?:\\.|[^"\\])*")\)')
references = set()
fallbacks = 0
for path in (ROOT / 'app/src/main/java').rglob('*.java'):
    source = path.read_text()
    references.update(re.findall(r'R\.string\.(ui_\w+)', source))
    for name, literal in resource_call.findall(source):
        assert android_text(base[name]) == json.loads(literal), f'Incorrect English fallback: {path.name} / {name}'
        fallbacks += 1
assert references <= base_ui, f'Missing resources: {references - base_ui}'
assert base.keys() <= chinese.keys(), f'Missing platform labels: {base.keys() - chinese.keys()}'
print(f'OK: {len(base_ui)} paired UI strings; {fallbacks} English fallbacks, Java references and format arguments match.')
