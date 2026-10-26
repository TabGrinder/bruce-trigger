#!/usr/bin/env python3
"""Original games must send their intro BEFORE a phone can deliver SELECT.

Inputs are delayed so the test actually exercises tight waiting loops.
Native API/USB/input are strict doubles; no physical-device claim.
"""
from pathlib import Path
from game_wire import decode
import argparse, ast, json, subprocess, zlib, gzip, base64
p=argparse.ArgumentParser();p.add_argument('--mqjs',required=True);a=p.parse_args()
r=Path(__file__).resolve().parents[2];out=r/'build/waiting-games';out.mkdir(parents=True,exist_ok=True)
module=ast.parse((r/'docs/audit-tests/test_files_catalog.py').read_text())
fixture=next(ast.literal_eval(n.value) for n in module.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='fixture' for t in n.targets))
contract=r'''
var phonePresses=0,inputChecks=0,firstFrameAt=-1,lastFrameAt=-1,wireFrames=0,initialPolls=0;
var auditPrint=serial.print,lastWireSeq=-1;
serial.print=function(s){auditPrint(s);var f=s.substring(s.indexOf('_GAME|')+6).trim().split('|');
if(f[0]==='E'||(f[0]==='R'||f[0]==='D')&&Number(f[1])!==lastWireSeq&&(Number(f[2])===0||Number(f[5])===Math.floor((Number(f[2])-1)/640)*10+9)){
lastWireSeq=Number(f[1]);wireFrames++;lastFrameAt=clockMs;if(firstFrameAt<0)firstFrameAt=clockMs;}};
delay=function(ms){clockMs+=ms;if(clockMs>=4000)throw new Error('Script exited');};
keyboard.getSelPress=function(){inputChecks++;if(inputChecks>200000)throw new Error('TEST_STALLED_WITHOUT_VISIBLE_INTRO');
if(phonePresses===0 && !NEVER_PRESS && clockMs>=1500 && lastFrameAt>=0 && clockMs-lastFrameAt<2500){initialPolls=inputChecks;phonePresses++;return true;}return false;};
keyboard.getPrevPress=function(){return false;};keyboard.getNextPress=function(){return false;};keyboard.getEscPress=function(){return false;};keyboard.getAnyPress=function(){return false;};
'''
samples={}
for game in ['highway_racer','pingpong','space_shooter']:
    code=(r/'docs/game-fixtures'/(game+'.js')).read_text()
    for label,source,never in [('new','app/src/main/res/raw/files_bridge.txt',False),('cancel','app/src/main/res/raw/files_bridge.txt',True)]:
        bridge=(r/source).read_text().replace('@@CAPTURE@@','false').replace('@@NONCE@@','0123456789abcdef').replace('@@PATH@@','/scripts/Games/flappybird.js')
        f=out/(game+'-'+label+'.js')
        f.write_text(fixture.replace('SOURCE',json.dumps(code))+contract.replace('NEVER_PRESS',str(never).lower())+bridge+"\nprint('STATS:'+JSON.stringify([phonePresses,inputChecks,firstFrameAt,wireFrames,clockMs,initialPolls]));")
        run=subprocess.run([a.mqjs,'--memory-limit','512000',str(f)],capture_output=True,text=True,timeout=45)
        assert run.returncode==0,(game,label,run.stderr)
        wire=[x.split('_GAME|',1)[1] for x in run.stdout.splitlines() if x.startswith('WIRE:BT_0123456789abcdef_GAME|')]
        stats=json.loads(next(x[6:] for x in run.stdout.splitlines() if x.startswith('STATS:')))
        assert wire[-1]=='STOP' and 0<=stats[2]<250 and stats[3]>5,(game,label,stats,wire[-5:])
        assert stats[0]==(0 if never else 1),(game,label,stats)
        if not never:assert stats[5]>1000,'must exercise delayed input, not immediate synthetic SELECT'
        frames=decode(wire,allow_partial=True);seq=len(frames);heartbeats=sum(not frame for frame in frames)
        # Cancellation may interrupt a transport record; an incomplete frame
        # is never presented. Owned STOP still has to be emitted.
        texts=[bytes.fromhex(x)[13:].rstrip(b'\0') for x in frames[0] if int(x[4:6],16)==16]
        assert b'Press Select to Start' in texts,(game,texts)
        assert heartbeats>=5,(game,label,heartbeats)
        if not never:
            assert any(frame and frame!=frames[0] for frame in frames[1:]),game
            samples[game]=frames[0]
        (out/(game+'-'+label+'-wire.txt')).write_text('\n'.join(wire)+'\n')
        print('PASS',label,game,'intro at',stats[2],'simulated ms; SELECT count',stats[0],'; transactions',seq,'; idle heartbeats',heartbeats,'; cancellation STOP',flush=True)
encoded=base64.b64encode(gzip.compress(json.dumps(samples).encode(),mtime=0)).decode()
chunks=' +\n'.join('"'+encoded[i:i+120]+'"' for i in range(0,len(encoded),120))
(r/'docs/audit-tests/WaitingGameFixtures.java').write_text('package com.tabgrinder.brucetrigger.audit;\nfinal class WaitingGameFixtures {static String json()throws Exception{String encoded='+chunks+';\njava.util.zip.GZIPInputStream in=new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(android.util.Base64.decode(encoded,0)));java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);in.close();return new String(out.toByteArray(),"UTF-8");}}\n')
print('Engine simulation with delayed/no input and cooperative cancellation; native hardware/USB are doubles.')
