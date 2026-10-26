#!/usr/bin/env python3
"""Repeated release checks against native contracts."""
from pathlib import Path
import argparse, ast, json, subprocess

p=argparse.ArgumentParser();p.add_argument('--mqjs',required=True);a=p.parse_args()
r=Path(__file__).resolve().parents[2];out=r/'build/native-contract';out.mkdir(parents=True,exist_ok=True)
module=ast.parse((r/'docs/audit-tests/test_files_catalog.py').read_text())
fixture=next(ast.literal_eval(n.value) for n in module.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='fixture' for t in n.targets))
new=(r/'app/src/main/res/raw/files_bridge.txt').read_text()
bridge=new.replace('@@CAPTURE@@','false').replace('@@NONCE@@','0123456789abcdef').replace('@@PATH@@','/scripts/Games/flappybird.js')
code="var d=require('display');var s=d.createSprite();s.setTextAlign(2);s.fill(0);s.pushSprite();"
for mode in ['normal','stop','explicit-delete']:
    source=code+({'stop':'exit();','explicit-delete':'s.deleteSprite();'}.get(mode,''))
    f=out/(mode+'.js')
    f.write_text(fixture.replace('SOURCE',json.dumps(source))+bridge+"\nprint('AUDIT:'+JSON.stringify([nativeSprites,nativeDeletes,frames,maxPrint]));")
    for attempt in range(25):
        run=subprocess.run([a.mqjs,'--memory-limit','512000',str(f)],capture_output=True,text=True,timeout=20)
        assert run.returncode==0,(mode,run.stderr)
        stats=json.loads(next(x[6:] for x in run.stdout.splitlines() if x.startswith('AUDIT:')))
        assert stats[0:3]==[0,1,1] and stats[3]<=256,(mode,stats)
        assert '_GAME|'+('STOP' if mode=='stop' else 'DONE') in run.stdout,(mode,run.stdout)
    print('PASS 25 '+mode+' runs: one physical push, one native release, no oversized serial call')
print('MicroQuickJS engine plus strict native doubles; not physical repeated-play testing.')
