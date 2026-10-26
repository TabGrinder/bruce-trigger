#!/usr/bin/env python3
"""Test doubles expose the pinned native API, including negative capability tests."""
from pathlib import Path
import argparse,ast,json,re,subprocess
p=argparse.ArgumentParser();p.add_argument('--mqjs',required=True);p.add_argument('--stock-source');a=p.parse_args()
r=Path(__file__).resolve().parents[2];api=json.loads((Path(__file__).parent/'firmware-api.json').read_text())
if a.stock_source:
 import hashlib
 c=Path(a.stock_source).read_bytes();assert hashlib.sha256(c).hexdigest()==api['sha256']
 for name,symbol in [('sprite','js_sprite_proto'),('display','js_display')]:
  methods=re.findall(r'JS_CFUNC_DEF\("([^"]+)"',c.decode().split('static const JSPropDef '+symbol+'[] = {')[1].split('};')[0]);assert methods==api[name]
m=ast.parse((Path(__file__).parent/'test_files_catalog.py').read_text());fixture=next(ast.literal_eval(n.value) for n in m.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='fixture' for t in n.targets))
source=fixture.replace('SOURCE','""')+"\nprint('API:'+JSON.stringify([Object.keys(display).sort(),Object.keys(display.createSprite()).sort()]));\nvar s=display.createSprite();if(typeof s.drawFillTriangle!=='undefined'||typeof display.drawFillTriangle!=='function')throw 'API_LEAK';"
out=r/'build/stock-api';out.mkdir(exist_ok=True);f=out/'api.js';f.write_text(source)
run=subprocess.run([a.mqjs,str(f)],capture_output=True,text=True,check=True)
actual=json.loads(next(x[4:] for x in run.stdout.splitlines() if x.startswith('API:')))
assert actual==[sorted(api['display']),sorted(api['sprite'])],actual
print('PASS exact display/sprite API lists and negative sprite triangle capability; pinned stock source contract.')
