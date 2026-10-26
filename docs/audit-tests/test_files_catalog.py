#!/usr/bin/env python3
"""Run the six reported sources unchanged on MicroQuickJS with explicit hardware doubles."""
from pathlib import Path
from game_wire import decode
import argparse,json,subprocess,zlib,gzip,base64
p=argparse.ArgumentParser();p.add_argument('--mqjs',required=True);p.add_argument('--script',required=True);p.add_argument('--memory-limit',default='512000');a=p.parse_args()
r=Path(__file__).resolve().parents[2];out=r/'build/catalog-proof';out.mkdir(parents=True,exist_ok=True)
bridge=Path(a.script).read_text()
fixture=r'''
var gameCode=SOURCE,clockMs=0,frames=0,draws=0,choiceCount=0,selected=false,stored=null,nextCalled=false,nativeSprites=0,nativeDeletes=0,maxPrint=0;
var auditRandomState=21616;Math.random=function(){auditRandomState^=auditRandomState<<13;auditRandomState^=auditRandomState>>>17;auditRandomState^=auditRandomState<<5;return (auditRandomState>>>0)/4294967296;};
function now(){return clockMs;}
function delay(ms){clockMs+=ms;if(clockMs>12000)throw new Error('Script exited');}
function random(a,b){return b===undefined?0:Math.floor((a+b)/2);}
function exit(){throw new Error('Script exited');}
function draw(name){return function(){draws++;if(name==='pushSprite')frames++;if(name==='deleteSprite'){nativeSprites--;nativeDeletes++;}};}
function target(w,h,isDisplay){var o={width:function(){return w;},height:function(){return h;},color:function(r,g,b){return((r&248)<<8)|((g&252)<<3)|(b>>3);}};
var names=isDisplay?["color", "fill", "setCursor", "print", "println", "setTextColor", "setTextSize", "setTextAlign", "drawText", "drawString", "drawPixel", "drawLine", "drawWideLine", "drawFastVLine", "drawFastHLine", "drawRect", "drawFillRect", "drawFillRectGradient", "drawRoundRect", "drawFillRoundRect", "drawTriangle", "drawFillTriangle", "drawCircle", "drawFillCircle", "drawBitmap", "drawXBitmap", "drawArc", "drawJpg", "drawGif", "gifOpen", "width", "height", "createSprite", "getRotation", "getBrightness", "setBrightness", "restoreBrightness"]:["setTextColor", "setTextSize", "setTextAlign", "drawText", "drawString", "drawPixel", "drawLine", "drawRect", "drawFillRect", "drawFillRectGradient", "drawRoundRect", "drawFillRoundRect", "drawCircle", "drawFillCircle", "drawBitmap", "drawXBitmap", "drawJpg", "width", "height", "setCursor", "print", "println", "fill", "color", "getRotation", "getBrightness", "setBrightness", "restoreBrightness", "pushSprite", "deleteSprite"];
for(var i=0;i<names.length;i++)if(!o[names[i]])o[names[i]]=draw(names[i]);
o.setTextAlign=function(a,b){if(typeof a!=='string'||typeof b!=='string')throw 'UNSAFE_NATIVE_ALIGN';};return o;}
var display=target(320,170,true);display.createSprite=function(){if(arguments.length!==0)throw 'UNSAFE_NATIVE_SPRITE';nativeSprites++;return target(320,170);};
var keyboard={getPrevPress:function(){return frames>30;},getNextPress:function(){if(clockMs>7000&&!nextCalled){nextCalled=true;return true;}return false;},getSelPress:function(){if(!selected){selected=true;return true;}return false;},getEscPress:function(){return false;},getAnyPress:function(){return true;},getKeysPressed:function(){return[];},setLongPress:function(){},keyboard:function(){clockMs+=400;return 'Test';}};
var audio={tone:function(){},play:function(){},playFile:function(){}};
var dialog={choice:function(v){clockMs+=500;choiceCount++;return choiceCount===1?'cat':'exit';},message:function(){clockMs+=500;return 'right';},error:function(){clockMs+=500;}};
var storage={readdir:function(p,o){if(p.fs!=='sd'||p.path!=='/scripts/Games')throw 'absent';return [{name:'flappybird.js',isDirectory:false,size:gameCode.length}];},read:function(p,bin){if(p.path==='/pet.json')return stored;if(p.fs!=='sd'||p.path!=='/scripts/Games/flappybird.js'||bin)throw 'wrong path';return gameCode;},write:function(p,s){if(p.path!=='/pet.json')throw 'unexpected write';stored=s;return true;}};
var runtime={main:function(f){f();}};
var serial={print:function(s){maxPrint=Math.max(maxPrint,s.length);if(s.length>256)throw 'UNBOUNDED_SERIAL_WRITE';var lines=s.split('\n');for(var li=0;li<lines.length;li++)if(lines[li])print('WIRE:'+lines[li]);},cmd:function(c){if(c!=='display start'&&c!=='display stop')throw 'unexpected CLI';return true;}};
function require(n){return globalThis[n];}
'''
for game in sorted((r/'docs/game-fixtures').glob('*.js')):
 code=game.read_text();f=out/game.name
 f.write_text(fixture.replace('SOURCE',json.dumps(code))+bridge+"\nprint('AUDIT:'+JSON.stringify([nativeSprites,nativeDeletes,frames,maxPrint]));")
 run=subprocess.run([a.mqjs,'--memory-limit',a.memory_limit,str(f)],capture_output=True,text=True,timeout=60)
 (out/(game.stem+'.stdout')).write_text(run.stdout+'\n'+run.stderr)
 assert run.returncode==0,(game.name,run.stderr[-1000:])
 stats=json.loads(next(x[6:] for x in run.stdout.splitlines() if x.startswith('AUDIT:')))
 assert stats[0]==0 and stats[3]<=256,(game.name,stats)
 if game.stem=='dino_game':assert stats[1]==1 and stats[2]>20,(game.name,stats)
 wire=[x.split('_GAME|',1)[1] for x in run.stdout.splitlines() if x.startswith('WIRE:BT_0123456789abcdef_GAME|')]
 assert wire and wire[-1] in ('DONE','STOP'),(game.name,wire[-5:])
 frames=decode(wire,allow_partial=wire[-1]=='STOP');seq=len(frames);ui=0;open_ui=False
 for line in wire:
  if line=='UI_ON':assert not open_ui;open_ui=True;ui+=1
  elif line=='UI_OFF':assert open_ui;open_ui=False
 assert not open_ui and len(frames)>2
 assert sum(map(len,frames))>10
 if game.stem=='tamagochi':assert any(bytes.fromhex(p)[13:].startswith(b'-----') for frame in frames for p in frame if int(p[4:6],16)==16),'fixture must exercise the original blinking animation'
 (out/(game.stem+'-frames.json')).write_text(json.dumps(frames))
 (out/(game.stem+'-wire.txt')).write_text('\n'.join(x for x in wire if x.startswith(('R|','D|')) and int(x.split('|')[1])<seq)+'\n')
 print('PASS',game.name,'frames',seq,'draw records',sum(map(len,frames)),'native UI visits',ui,'termination',wire[-1],'native pushes',stats[2],'sprites released',stats[1],'largest print',stats[3],flush=True)
print('Native hardware/storage/input/dialogs are doubles; no physical LILYGO/FPS claim.')

samples={f.name.removesuffix('-frames.json'):json.loads(f.read_text())[:50] for f in sorted(out.glob('*-frames.json'))}
encoded=base64.b64encode(gzip.compress(json.dumps(samples).encode(),mtime=0)).decode()
chunks=' +\n'.join('"'+encoded[i:i+120]+'"' for i in range(0,len(encoded),120))
(r/'docs/audit-tests/CatalogFixtures.java').write_text('package com.tabgrinder.brucetrigger.audit;\n/** Generated by test_files_catalog.py from unchanged pinned sources. */\nfinal class CatalogFixtures {\nstatic String json()throws Exception{String encoded='+chunks+';\njava.util.zip.GZIPInputStream in=new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(android.util.Base64.decode(encoded,0)));java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);in.close();return new String(out.toByteArray(),"UTF-8");}}\n')
