#!/usr/bin/env python3
"""Longer original-Dino run; keeps the real game source and strict API doubles."""
from pathlib import Path
from game_wire import decode
import argparse, ast, json, subprocess, zlib
p=argparse.ArgumentParser();p.add_argument('--mqjs',required=True);a=p.parse_args()
r=Path(__file__).resolve().parents[2];out=r/'build/dino-endurance';out.mkdir(parents=True,exist_ok=True)
module=ast.parse((r/'docs/audit-tests/test_files_catalog.py').read_text())
fixture=next(ast.literal_eval(n.value) for n in module.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='fixture' for t in n.targets))
fixture=fixture.replace('clockMs>12000','clockMs>180000').replace('frames>30','frames>=3000')
fixture=fixture.replace("if(!selected){selected=true;return true;}return false;","return frames%40===0;")
fixture=fixture.replace("if(clockMs>7000&&!nextCalled){nextCalled=true;return true;}return false;","return false;")
bridge=(r/'app/src/main/res/raw/files_bridge.txt').read_text().replace('@@CAPTURE@@','false').replace('@@NONCE@@','0123456789abcdef').replace('@@PATH@@','/scripts/Games/flappybird.js')
code=(r/'docs/game-fixtures/dino_game.js').read_text()
f=out/'run.js';f.write_text(fixture.replace('SOURCE',json.dumps(code))+bridge+"\nprint('AUDIT:'+JSON.stringify([nativeSprites,nativeDeletes,frames,maxPrint]));")
run=subprocess.run([a.mqjs,'--memory-limit','512000',str(f)],capture_output=True,text=True,timeout=90)
assert run.returncode==0,run.stderr
stats=json.loads(next(x[6:] for x in run.stdout.splitlines() if x.startswith('AUDIT:')))
assert stats[0:3]==[0,1,3000] and stats[3]<=256,stats
lines=[x.split('_GAME|',1)[1] for x in run.stdout.splitlines() if x.startswith('WIRE:BT_0123456789abcdef_GAME|')]
assert lines[-1]=='DONE',lines[-10:]
frames=decode(lines);seq=len(frames);records=sum(map(len,frames));largest=max(sum(len(bytes.fromhex(p)) for p in f) for f in frames)
(out/'wire.txt').write_text('\n'.join(x for x in lines if x.startswith(('R|','D|')))+'\n')
print('PASS original Dino: 3000 native push calls; one native sprite released; '+str(seq)+' checked transactions; '+str(records)+' draw records; largest transaction '+str(largest)+' bytes; largest serial.print '+str(stats[3])+' characters.')
print('Strict native/USB doubles; simulated clock, not device speed or physical endurance.')
