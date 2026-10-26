#!/usr/bin/env python3
"""Verify the real bridge against a pinned drawing digest across clipping and mutable bitmap inputs."""
from pathlib import Path
from game_wire import decode
import argparse,subprocess,json,hashlib,zlib
p=argparse.ArgumentParser();p.add_argument('--mqjs',required=True);a=p.parse_args()
r=Path(__file__).resolve().parents[2];out=r/'build/xbm-parity';out.mkdir(parents=True,exist_ok=True)
game=r'''
var d=require('display'),s=d.createSprite(320,170,16),widths=[1,7,8,9,17,319,623],cases=0;
for(var wi=0;wi<widths.length;wi++)for(var shift=0;shift<8;shift++)for(var mode=0;mode<4;mode++){
 var w=widths[wi],h=3,n=((w+7)>>3)*h,raw=new Uint8Array(n),data;
 for(var i=0;i<n;i++)raw[i]=(i*71+shift*3+mode)&255;
 if(mode===0){data='';for(var i=0;i<n;i++)data+=String.fromCharCode(raw[i]);}
 else if(mode===1)data=raw;
 else if(mode===2){data=[];for(var i=0;i<n;i++)data.push(raw[i]);}
 else data=raw.buffer;
 for(var corner=0;corner<4;corner++){
  var x=corner<2?-shift:318,y=corner%2===0?-1:169;
  s.fill(31);s.drawXBitmap(x,y,data,w,h,65535,mode%2===0?undefined:63488);s.pushSprite();cases++;
 }
 // A mutable typed array must be read again, not cached using object identity.
 raw[0]=raw[0]^255;s.drawXBitmap(-shift,0,raw,w,h,2016);s.pushSprite();cases++;
}
print('CASES:'+cases);
'''
fixture=r'''
var gameCode=SOURCE,clockMs=0;
function now(){return clockMs;}function delay(ms){clockMs+=ms;}function noop(){}
function target(w,h){return {width:function(){return w;},height:function(){return h;},setTextAlign:noop,setTextSize:noop,setTextColor:noop,fill:noop,drawXBitmap:noop,pushSprite:noop,deleteSprite:noop};}
var display=target(320,170);display.createSprite=function(w,h){return target(w||320,h||170);};var keyboard={},dialog={},runtime={};
var storage={readdir:function(p){if(p.fs!=='sd')throw 'absent';return [{name:'bench.js',isDirectory:false,size:gameCode.length}];},read:function(){return gameCode;}};
var serial={print:function(s){print(s);}};function require(n){return globalThis[n];}
'''
baseline=None
for label,path in [('current','app/src/main/res/raw/files_bridge.txt')]:
 bridge=(r/path).read_text().replace('@@CAPTURE@@','false').replace('@@NONCE@@','0123456789abcdef').replace('@@PATH@@','/bench.js')
 f=out/(label+'.js');f.write_text(fixture.replace('SOURCE',json.dumps(game))+bridge)
 run=subprocess.run([a.mqjs,'--memory-limit','512000',str(f)],capture_output=True,text=True,timeout=60)
 assert run.returncode==0,run.stderr
 lines=[s.split('_GAME|',1)[1] for s in run.stdout.splitlines() if s.startswith('BT_')]
 assert lines[-1]=='DONE',lines[-5:]
 cases=int(next(s[6:] for s in run.stdout.splitlines() if s.startswith('CASES:')))
 assert cases==1120
 frames=decode(lines)
 normalized=b''.join(bytes.fromhex(packet) for frame in frames for packet in frame)
 baseline=normalized
 assert hashlib.sha256(normalized).hexdigest()=='b63a9d45c537fe9c35438ed752ef1f09555816380d8d5cbdd65fc1d492259059','bitmap pixels or operation order changed'
print('PASS 1120 monochrome scenes: every clipping bit offset, byte-boundary widths, all four screen corners, string/array/typed-array/ArrayBuffer inputs, opaque/transparent and mutated data; identical concatenated drawing records; each transport checksum also verified.')
print('SHA256',hashlib.sha256(baseline).hexdigest())
print('Native display and USB are doubles; no physical timing claim.')
