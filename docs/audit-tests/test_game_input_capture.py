#!/usr/bin/env python3
"""Real fixed wrapper on MicroQuickJS; stock consuming 75-ms flags, timed USB cost.
This tests actual returned keys and original game movement, not just CLI prompts.
"""
from pathlib import Path
import argparse,ast,json,subprocess,hashlib
p=argparse.ArgumentParser();p.add_argument('--mqjs',required=True);args=p.parse_args()
r=Path(__file__).resolve().parents[2];out=r/'build/input-capture';out.mkdir(parents=True,exist_ok=True)
module=ast.parse((r/'docs/audit-tests/test_files_catalog.py').read_text())
fixture=next(ast.literal_eval(n.value) for n in module.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='fixture' for t in n.targets))
contract=r'''
var pulses=[],consumed=0,missed=0,printCost=PRINT_COST,drawCost=0,physicalFrames=0,paddle=[],jumps=0,armed=false,ended=false,introSelected=false,limit=20000;
function arm(key,at){pulses.push({key:key,at:at,used:false,counted:false});}
function readFlag(key){if(GAME==='pingpong'&&key==='select'&&!introSelected){introSelected=true;consumed++;clockMs+=10;return true;}for(var i=0;i<pulses.length;i++){var p=pulses[i];
if(!p.used&&!p.counted&&clockMs>=p.at+75){p.counted=true;missed++;}
if(!p.used&&p.key===key&&clockMs>=p.at&&clockMs<p.at+75){p.used=true;consumed++;if(key==='select'||key==='esc'||key==='any')clockMs+=10;return true;}}
return false;}
keyboard.getPrevPress=function(){return readFlag('prev');};keyboard.getNextPress=function(){return readFlag('next');};
keyboard.getSelPress=function(){return readFlag('select');};keyboard.getEscPress=function(){return readFlag('esc');};keyboard.getAnyPress=function(){return readFlag('any');};
var savedPrint=serial.print;serial.print=function(s){savedPrint(s);clockMs+=printCost;if(clockMs>limit&&!ended){ended=true;throw new Error('Script exited');}};
delay=function(ms){clockMs+=ms;if(clockMs>limit&&!ended){ended=true;throw new Error('Script exited');}};
var savedTarget=target;target=function(w,h,isDisplay){var o=savedTarget(w,h,isDisplay),fill=o.fill,rect=o.drawFillRect,push=o.pushSprite;
o.fill=function(c){physicalFrames++;if(GAME==='pingpong'&&physicalFrames>=5&&physicalFrames<=9)arm('next',clockMs+8);if(GAME==='dino_game'&&physicalFrames===2)arm('select',clockMs+8);fill(c);clockMs+=drawCost;};
o.drawFillRect=function(x,y,w,h,c){if(GAME==='pingpong'&&y===150&&w===40&&h===6)paddle.push(x);rect.apply(o,arguments);clockMs+=drawCost;};
o.pushSprite=function(){push.apply(o,arguments);if(physicalFrames>12)throw new Error('Script exited');};return o;};
display=target(320,170,true);display.createSprite=function(){nativeSprites++;return target(320,170);};
audio.tone=function(f){if(f===494)jumps++;};
'''
cases={
'pressed_before_flush':"arm('select',clockMs);display.fill(31);print('RESULT:'+JSON.stringify([keyboard.getSelPress(),keyboard.getSelPress()]));",
'during_wait':"display.fill(31);delay(1);arm('select',clockMs+15);delay(180);print('RESULT:'+JSON.stringify([keyboard.getSelPress(),keyboard.getSelPress()]));",
'stale_input':"display.fill(31);delay(1);arm('select',clockMs+15);delay(500);print('RESULT:'+JSON.stringify([keyboard.getSelPress()]));",
'dialog_boundary':"display.fill(31);delay(1);arm('select',clockMs);delay(15);require('dialog').message('test');print('RESULT:'+JSON.stringify([keyboard.getSelPress()]));",
'rapid_events':"display.fill(31);delay(1);arm('select',clockMs+10);arm('select',clockMs+100);delay(190);print('RESULT:'+JSON.stringify([keyboard.getSelPress(),keyboard.getSelPress(),keyboard.getSelPress()]));",
}
for name,code in cases.items():
 for label,bridge in [('new',r/'app/src/main/res/raw/files_bridge.txt')]:
  cost=30 if name=='pressed_before_flush' else 1
  script=fixture.replace('SOURCE',json.dumps(code))+contract.replace('PRINT_COST',str(cost)).replace('GAME',json.dumps(name))
  script+=bridge.read_text().replace('@@CAPTURE@@','false').replace('@@NONCE@@','0123456789abcdef').replace('@@PATH@@','/scripts/Games/flappybird.js')
  path=out/(name+'-'+label+'.js');path.write_text(script)
  run=subprocess.run([args.mqjs,'--memory-limit','512000',str(path)],capture_output=True,text=True,timeout=30)
  (out/(name+'-'+label+'.log')).write_text(run.stdout+run.stderr)
  assert run.returncode==0,(name,label,run.stderr)
  got=json.loads(next(x[7:] for x in run.stdout.splitlines() if x.startswith('RESULT:')))
  expected={'pressed_before_flush':[label=='new',False],'during_wait':[label=='new',False],'stale_input':[False],'dialog_boundary':[False],'rapid_events':[label=='new',label=='new',False]}[name]
  assert got==expected,(name,label,got,expected)
  print('PASS',name,label,got,flush=True)
for game in ('dino_game','pingpong'):
 for label,bridge in [('new',r/'app/src/main/res/raw/files_bridge.txt')]:
  source=(r/'docs/game-fixtures'/(game+'.js')).read_text()
  setup=fixture.replace('SOURCE',json.dumps(source))+contract.replace('PRINT_COST','8').replace('GAME',json.dumps(game))
  # First SELECT is sent while waiting for the original Ping Pong instructions.
  setup+='\nlimit=5000;drawCost=2;'+''+'\n'
  script=setup+bridge.read_text().replace('@@CAPTURE@@','false').replace('@@NONCE@@','0123456789abcdef').replace('@@PATH@@','/scripts/Games/flappybird.js')
  script+="\nprint('OUTCOME:'+JSON.stringify([jumps,paddle,consumed,missed,physicalFrames]));"
  path=out/(game+'-'+label+'.js');path.write_text(script)
  run=subprocess.run([args.mqjs,'--memory-limit','512000',str(path)],capture_output=True,text=True,timeout=30)
  (out/(game+'-'+label+'.log')).write_text(run.stdout+run.stderr)
  assert run.returncode==0,(game,label,run.stderr)
  result=json.loads(next(x[8:] for x in run.stdout.splitlines() if x.startswith('OUTCOME:')))
  print('ORIGINAL GAME',game,label,result,flush=True)
  if game=='dino_game':assert result[0]==(1 if label=='new' else 0),(game,label,result)
  else:
   assert len(result[1])>=6,(game,label,result)
   assert max(result[1][:11])-min(result[1][:11])==(20 if label=='new' else 0),(game,label,result)
print('PASS timed stock-flag capture, TTL/no-duplicate/dialog isolation, actual original Dino jump and Ping Pong paddle movement. Timing/hardware are modeled, not physical FPS.')
