#!/usr/bin/env python3
"""Ten complete host/engine regression passes. No physical-device claim."""
from pathlib import Path
import argparse,subprocess,json,time,hashlib,sys
p=argparse.ArgumentParser();p.add_argument('--mqjs',required=True);a=p.parse_args()
r=Path(__file__).resolve().parents[2];mq=str(Path(a.mqjs).resolve());out=r/'build/ten-passes';out.mkdir(parents=True,exist_ok=True)
sys.path.insert(0,str(r/'tools'))
from inventory import source_inventory,source_digest
tested_source=source_digest(source_inventory(r))
(out/'result.json').write_text(json.dumps({'source_sha256':tested_source,'passes':[],'physical_hardware_tested':False},indent=2)+'\n')
base=[['python3','tools/test.py'],['python3','tools/test_usb.py'],['java','-Dproject.root=.','-cp','build/test-usb','com.tabgrinder.brucetrigger.FilesGameRegressionTest','build/files-bridge-1.0.js']]
engine=['check_stock_api','test_compression','test_files_bridge','test_waiting_games','check_bridge_native_contract','verify_xbm_bridge','dino_endurance','test_game_input_capture','test_handover']
results=[]
for number in range(1,11):
 start=time.monotonic();memory=256000 if number%2 else 512000;seed=1085 if number==10 else number*217
 commands=list(base)
 commands+=[['python3','docs/audit-tests/test_megalodon.py','--mqjs',mq,'--memory-limit',str(memory),'--seed',str(seed)],['python3','docs/audit-tests/test_files_catalog.py','--mqjs',mq,'--script','build/files-bridge-1.0.js','--memory-limit',str(memory)]]
 for name in engine:
  c=['python3','docs/audit-tests/'+name+'.py','--mqjs',mq]
  if name=='test_files_bridge':c+=['--game','docs/game-fixtures/flappybird.js','--script','build/files-bridge-1.0.js']
  commands.append(c)
 commands += [['python3','docs/audit-tests/export_game_replay.py'],['python3','tools/test_usb_packet_loss.py','--catalog']]
 log=out/f'pass-{number}.txt'
 with log.open('w') as f:
  for command in commands:
   f.write('\nRUN '+' '.join(command)+'\n');f.flush()
   status=subprocess.run(command,cwd=r,stdout=f,stderr=subprocess.STDOUT,timeout=360)
   if status.returncode:raise SystemExit(f'FAIL pass {number}: {command}; see {log}')
   print('PASS',number,command[1],flush=True)
 assert source_digest(source_inventory(r))==tested_source,'Source changed during validation'
 results.append({'pass':number,'status':'PASS','memory_limit':memory,'seed':seed,'commands':len(commands),'elapsed_seconds':round(time.monotonic()-start,2),'log_sha256':hashlib.sha256(log.read_bytes()).hexdigest()})
 (out/'result.json').write_text(json.dumps({'source_sha256':tested_source,'passes':results,'physical_hardware_tested':False},indent=2)+'\n')
 print('COMPLETE PASS',number,flush=True)
print('ALL TEN PASSES PASS. USB and native hardware functions are test doubles.',flush=True)
