#!/usr/bin/env python3
"""Pinned RAM game on MicroQuickJS; native graphics/clock/keys are strict doubles."""
from pathlib import Path
import argparse,ast,json,subprocess,gzip,base64
from game_wire import decode
p=argparse.ArgumentParser();p.add_argument('--mqjs',required=True);p.add_argument('--memory-limit',default='256000');p.add_argument('--seed',type=int,default=1);a=p.parse_args()
r=Path(__file__).resolve().parents[2];out=r/'build/megalodon';out.mkdir(exist_ok=True)
fixture=r'''
var clockMs=0,draws=[],nativeSprites=0,nativeDeletes=0,maxPrint=0,frameCount=0,stopOK=STOP_OK,cli=[],pushed=[],keyOnce={};
var rng=SEED;Math.random=function(){rng^=rng<<13;rng^=rng>>>17;rng^=rng<<5;return (rng>>>0)/4294967296;};
function now(){return clockMs;}
function delay(ms){clockMs+=ms;if(clockMs>20000)throw new Error('Script exited');}
function key(name,t){if(clockMs>=t&&!keyOnce[name]){keyOnce[name]=true;return true;}return false;}
var keyboard={getPrevPress:function(){return key('up',3000);},getNextPress:function(){return key('down',5000);},getSelPress:function(){return key('pause',6000)||key('resume',7000);},getEscPress:function(){return clockMs>=14000;}};
function packet(op,words,text){var p=[170,0,op];for(var i=0;i<words.length;i++){p.push((words[i]>>>8)&255,words[i]&255);}if(text!==undefined){for(var j=0;j<text.length;j++)p.push(text.charCodeAt(j));p.push(0);}p[1]=p.length;return p;}
var display={width:function(){return 320;},height:function(){return 170;},createSprite:function(){if(arguments.length)throw 'UNSAFE_SPRITE_ARGS';nativeSprites++;return {width:function(){return 320;},height:function(){return 170;},
setTextAlign:function(a,b){if(a!=='left'||b!=='top')throw 'alignment';},setTextColor:function(){},setTextSize:function(){},
fill:function(c){draws=[packet(99,[320,170])];draws[0]=[170,8,99,1,64,0,170,1];draws.push(packet(0,[c]));},
drawFillRect:function(x,y,w,h,c){draws.push(packet(2,[x,y,w,h,c]));},drawFillCircle:function(x,y,v,c){draws.push(packet(6,[x,y,v,c]));},
drawXBitmap:function(x,y,bits,w,h,c){if(!(bits instanceof Uint8Array)||bits.length!==Math.ceil(w/8)*h)throw 'NATIVE_BITMAP_TYPE';
for(var yy=0;yy<h;yy++)for(var xx=0;xx<w;xx++)if(bits[yy*Math.ceil(w/8)+(xx>>3)]&(1<<(xx&7)))draws.push(packet(2,[x+xx,y+yy,1,1,c]));},
drawString:function(s,x,y){draws.push(packet(16,[x,y,2,65535,0],s));},
pushSprite:function(){frameCount++;if(frameCount%5===0)print('NATIVE:'+JSON.stringify([frameCount,draws]));},
deleteSprite:function(){nativeSprites--;nativeDeletes++;}};}};
var serial={print:function(s){maxPrint=Math.max(maxPrint,s.length);if(s.length>256)throw 'UNBOUNDED_PRINT';var lines=s.split('\n');for(var i=0;i<lines.length;i++)if(lines[i])print('WIRE:'+lines[i]);},cmd:function(s){cli.push(s);if(s!=='display stop')throw 'unexpected command';return stopOK;}};
function require(n){if(n==='display')return display;if(n==='keyboard')return keyboard;throw 'UNEXPECTED_MODULE';}
'''
for label,capture,success in [('direct',False,True),('handover',True,True),('failed-stop',True,False)]:
 source=fixture.replace('SEED',str(a.seed)).replace('STOP_OK',str(success).lower())+(out/('capture.js' if capture else 'direct.js')).read_text()+"\nprint('RESULT:'+JSON.stringify([frameCount,nativeSprites,nativeDeletes,maxPrint,cli,clockMs]));"
 f=out/(label+'-test.js');f.write_text(source)
 run=subprocess.run([a.mqjs,'--memory-limit',a.memory_limit,str(f)],capture_output=True,text=True,timeout=90)
 (out/(label+'.log')).write_text(run.stdout+run.stderr);assert run.returncode==0,run.stderr
 result=json.loads(next(x[7:] for x in run.stdout.splitlines() if x.startswith('RESULT:')))
 count,live,released,maxprint,cli,elapsed=result
 wire=[x.split('_GAME|',1)[1] for x in run.stdout.splitlines() if x.startswith('WIRE:BT_0123456789abcdef_GAME|')]
 assert live==0 and maxprint<=256 and cli==(['display stop'] if capture else []),result
 if not success:
  assert count==0 and released==0 and 'ON' not in wire and wire[-1]=='ERROR';print('PASS Megalodon failed handover does not allocate/start/read storage');continue
 assert count>=200 and released==1 and wire[-1]=='DONE',result
 frames=decode(wire);assert len(frames)==count+2,(len(frames),count)
 y=[int.from_bytes(bytes.fromhex(p)[7:9],'big',signed=True) for f in frames for p in f if p[4:6]=='5c' and p[6:10]=='0001']
 assert min(y)<73 and y[-1]==73,'up/down must affect real emitted shark position'
 assert any(b'PAUSED' in bytes.fromhex(p) for f in frames for p in f),'pause must be visible'
 lengths=[sum(len(bytes.fromhex(p)) for p in f) for f in frames[1:-1]]
 assert max(lengths)<128,(min(lengths),max(lengths))
 samples=[]
 for line in run.stdout.splitlines():
  if line.startswith('NATIVE:'):
   index,native=json.loads(line[7:]);samples.append({'index':index,'native':[''.join(f'{b:02x}' for b in p) for p in native]})
 if label=='direct':
  (out/'frames.json').write_text(json.dumps(frames));(out/'wire.txt').write_text('\n'.join(x for x in wire if x.startswith(('R|','D|')))+'\n')
  obj={'frames':frames,'physical':samples};encoded=base64.b64encode(gzip.compress(json.dumps(obj).encode(),mtime=0)).decode();chunks='\n'.join('.append("'+encoded[i:i+120]+'")' for i in range(0,len(encoded),120))
  (r/'docs/audit-tests/MegalodonFixtures.java').write_text('package com.tabgrinder.brucetrigger.audit;\nfinal class MegalodonFixtures { static String json()throws Exception {String s=new StringBuilder()'+chunks+'.toString(); java.util.zip.GZIPInputStream in=new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(android.util.Base64.decode(s,0)));java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);in.close();return new String(out.toByteArray(),"UTF-8");}}\n')
 print('PASS Megalodon',label,'frames=',count,'drawing bytes/frame=',min(lengths),'..',max(lengths),'memory quota=',a.memory_limit,'seed=',a.seed)
subprocess.run(['java','-cp',str(r/'build/test-usb'),'com.tabgrinder.brucetrigger.MegalodonWireRegressionTest',str(out/'wire.txt')],check=True)
print('Physical hardware doubled; these are traffic/state assertions, not measured hardware FPS.')
