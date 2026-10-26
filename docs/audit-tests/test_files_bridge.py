#!/usr/bin/env python3
"""Selected Files bridge on pinned MicroQuickJS; native hardware remains doubled."""
from pathlib import Path
from game_wire import decode
import argparse,json,subprocess,zlib
p=argparse.ArgumentParser();p.add_argument('--mqjs',required=True);p.add_argument('--game',required=True);p.add_argument('--script',required=True);a=p.parse_args()
r=Path(__file__).resolve().parents[2];out=r/'build/files-proof';out.mkdir(parents=True,exist_ok=True)
bridge=Path(a.script).read_text();game=Path(a.game).read_text()
fixture=r'''
var clockMs=0,stepNo=0,lastSel=-1,lastEsc=-1,scenario=SCENARIO,gameCode=SOURCE;
function now(){return clockMs;}
function delay(ms){clockMs+=ms;if(ms===25)stepNo++;}
function draw(name){return function(){var args=[];for(var i=0;i<arguments.length;i++)args.push(arguments[i]);print("PHYS:"+JSON.stringify([name,args]));};}
function target(w,h,isDisplay){var o={width:function(){return w;},height:function(){return h;},color:function(r,g,b){return((r>>3)<<11)|((g>>2)<<5)|(b>>3);}};
var names=isDisplay?["color", "fill", "setCursor", "print", "println", "setTextColor", "setTextSize", "setTextAlign", "drawText", "drawString", "drawPixel", "drawLine", "drawWideLine", "drawFastVLine", "drawFastHLine", "drawRect", "drawFillRect", "drawFillRectGradient", "drawRoundRect", "drawFillRoundRect", "drawTriangle", "drawFillTriangle", "drawCircle", "drawFillCircle", "drawBitmap", "drawXBitmap", "drawArc", "drawJpg", "drawGif", "gifOpen", "width", "height", "createSprite", "getRotation", "getBrightness", "setBrightness", "restoreBrightness"]:["setTextColor", "setTextSize", "setTextAlign", "drawText", "drawString", "drawPixel", "drawLine", "drawRect", "drawFillRect", "drawFillRectGradient", "drawRoundRect", "drawFillRoundRect", "drawCircle", "drawFillCircle", "drawBitmap", "drawXBitmap", "drawJpg", "width", "height", "setCursor", "print", "println", "fill", "color", "getRotation", "getBrightness", "setBrightness", "restoreBrightness", "pushSprite", "deleteSprite"];
for(var i=0;i<names.length;i++)if(!o[names[i]])o[names[i]]=draw(names[i]);
o.setTextAlign=function(a,b){if(typeof a!=='string'||typeof b!=='string')throw 'UNSAFE_NATIVE_ALIGN';};return o;}
var display=target(320,170,true);display.createSprite=function(){if(arguments.length!==0)throw 'UNSAFE_NATIVE_SPRITE';return target(320,170);};
var keyboard={getPrevPress:function(){return false;},getNextPress:function(){return false;},getSelPress:function(){if(lastSel!==stepNo&&(stepNo===1||stepNo%10===3)){lastSel=stepNo;return true;}return false;},getEscPress:function(){if(lastEsc!==stepNo&&stepNo===120){lastEsc=stepNo;return true;}return false;},getKeysPressed:function(){return[];}};
var storage={readdir:function(p,o){if(p.path!=='/scripts/Games'||p.fs!=='sd'&&scenario!==2)throw 'absent';return [{name:'flappybird.js',isDirectory:false,size:gameCode.length}];},read:function(p,bin){if(p.fs!=='sd'||p.path!=='/scripts/Games/flappybird.js'||bin)throw 'wrong path';return gameCode;}};
var runtime={main:function(f){throw 'unexpected timer';}};
var serial={print:function(s){if(s.length>256)throw 'UNBOUNDED_SERIAL_WRITE';var lines=s.split('\n');for(var li=0;li<lines.length;li++)if(lines[li])print('WIRE:'+lines[li]);}};
function require(n){return globalThis[n];}
'''
sprite="var d=require('display');var s=d.createSprite();s.fill(0);s.drawFillRect(2,2,5,5,63488);s.pushSprite(10,20,0);delay(25);s.drawFillCircle(9,5,3,2016);s.pushSprite(30,20,0);delay(25);s.deleteSprite();"
cases=[game,sprite,game,"require('display').drawJpg('/unsupported.jpg',0,0);", "require('display').drawString('selected file',1,1);delay(25);", "require('display').createSprite(20,10,8);"]
cases += ["require('display').createSprite(20,10,16);"]
cases += ["var d=require('display');d.drawXBitmap(1,2,new Uint8Array([5]),3,1,63488);d.drawBitmap(4,5,new Uint8Array([224,28,3]),3,1,8);delay(25);", "require('display').fill(31);delay(5000);"]
for scenario,code in enumerate(cases):
 f=out/f'case-{scenario}.js';f.write_text(fixture.replace('SCENARIO',str(scenario)).replace('SOURCE',json.dumps(code))+bridge)
 run=subprocess.run([a.mqjs,'--memory-limit','512000',str(f)],capture_output=True,text=True,timeout=45)
 assert run.returncode==0,(scenario,run.stderr[-2000:])
 wire=[line.split('_GAME|',1)[1] for line in run.stdout.splitlines() if line.startswith('WIRE:BT_0123456789abcdef_GAME|')]
 assert wire and wire[0]=='SEARCH',(scenario,run.stdout[-1000:],run.stderr)
 if scenario==2:assert wire[-1]=='AMBIGUOUS' and 'ON' not in wire;print('PASS duplicate-volume selection executes neither copy');continue
 if scenario in (3,5,6):assert wire[-1]=='UNSUPPORTED' and any(x.startswith('REASON|') for x in wire),(scenario,wire[-4:]);print('PASS unsupported API/depth has explicit terminal reason');continue
 assert wire[-1]=='DONE',(scenario,wire[-10:])
 frames=decode(wire);seq=len(frames)
 assert frames and frames[0][0]=='aa0863014000aa01'
 (out/f'frames-{scenario}.json').write_text(json.dumps(frames))
 (out/f'wire-{scenario}.txt').write_text('\n'.join(wire)+'\n')
 print('PASS selected source case',scenario,'frames',seq,'records',sum(map(len,frames)))
print('No game hash whitelist; the selected source is evaluated only on the device-side JS runtime. Native functions and USB are test doubles.')
