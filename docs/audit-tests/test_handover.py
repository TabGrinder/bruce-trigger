#!/usr/bin/env python3
"""Run the production-generated handover script on MicroQuickJS with strict native doubles."""
from pathlib import Path
import argparse,ast,json,subprocess
p=argparse.ArgumentParser();p.add_argument('--mqjs',required=True);a=p.parse_args()
r=Path(__file__).resolve().parents[2];out=r/'build/handover'
module=ast.parse((r/'docs/audit-tests/test_files_catalog.py').read_text())
fixture=next(ast.literal_eval(n.value) for n in module.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='fixture' for t in n.targets))
contract=r'''
var cli=[],reads=0,executed=false;
var originalRead=storage.read;storage.read=function(){reads++;return originalRead.apply(storage,arguments);};
serial.cmd=function(c){cli.push([c,clockMs]);if(c!=='display stop')throw 'unexpected CLI';return STOP_OK;};
'''
for label,capture,success in [('handover',True,True),('failed-stop',True,False),('direct',False,True)]:
    script=fixture.replace('SOURCE',json.dumps("executed=true;display.fill(31);"))+contract.replace('STOP_OK',str(success).lower())
    script+=(out/('capture.js' if capture else 'direct.js')).read_text()
    script+="\nprint('STATE:'+JSON.stringify([cli,reads,executed,clockMs]));"
    path=out/(label+'-test.js');path.write_text(script)
    run=subprocess.run([a.mqjs,'--memory-limit','256000',str(path)],capture_output=True,text=True,timeout=30)
    (out/(label+'.log')).write_text(run.stdout+run.stderr)
    assert run.returncode==0,run.stderr
    cli,reads,executed,elapsed=json.loads(next(x[6:] for x in run.stdout.splitlines() if x.startswith('STATE:')))
    wire=[x.split('_GAME|')[1] for x in run.stdout.splitlines() if '_GAME|' in x]
    assert cli==([['display stop',600]] if capture else []),(label,cli)
    if success:
        assert executed and reads==1 and wire[-1]=='DONE',(label,wire)
        assert wire.count('CAPTURE_OFF')==(3 if capture else 0)
        if capture:assert wire.index('CAPTURE_OFF')<wire.index('SEARCH')<wire.index('ON') and elapsed>=830
    else:
        assert not executed and reads==0 and 'SEARCH' not in wire and 'ON' not in wire
        assert wire[-2:]==['REASON|capture stop','UNSUPPORTED'],wire
    print('PASS',label,'one native stop' if capture else 'no native stop','; game executed:',executed)
print('PASS native drain precedes exactly one stop; no storage writes/flash/reset/replay; failed stop cannot read or execute the selected game. Native hardware is doubled.')
