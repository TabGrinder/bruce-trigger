#!/usr/bin/env python3
"""Exact draw replay through engine compression and independent wire decoder.
Numbers describe identical recorded draw transactions, not hardware FPS.
"""
from pathlib import Path
from game_wire import decode
import argparse,json,subprocess,time,zlib
p=argparse.ArgumentParser();p.add_argument('--mqjs',required=True);p.add_argument('--frames-dir',type=Path);a=p.parse_args()
r=Path(__file__).resolve().parents[2];out=r/'build/compression';out.mkdir(exist_ok=True)
root=a.frames_dir or r/'build/catalog-proof'
source=(r/'app/src/main/res/raw/files_bridge.txt').read_text();encoder=source[source.index('var pairs='):source.index('function packed(')]
results=[]
for path in sorted(root.glob('*-frames.json')):
 frames=json.loads(path.read_text())[:50]
 cases=[[''.join(f),len(f)] for f in frames]
 fixture="var bytes=[],ops=0,seq=0,flushed=0,clockMs=0,tag='BT_0123456789abcdef_GAME|';function clock(){return clockMs;}function wait(n){clockMs+=n;}function sampleKeys(){}function out(s){print(s.trim());}\n"
 fixture+=encoder+'\nvar cases='+json.dumps(cases,separators=(',',':'))+";for(var i=0;i<cases.length;i++){bytes=[];var s=cases[i][0];for(var j=0;j<s.length;j+=2)bytes.push(parseInt(s.substring(j,j+2),16));ops=cases[i][1];flush(true);}\n"
 file=out/(path.stem+'.js');file.write_text(fixture)
 run=subprocess.run([a.mqjs,'--memory-limit','512000',str(file)],capture_output=True,text=True,check=True,timeout=90)
 lines=[x.split('_GAME|',1)[1] for x in run.stdout.splitlines() if '_GAME|' in x]
 assert decode(lines)==frames,path
 baseline=0;current=sum(len(x)+1 for x in run.stdout.splitlines() if '_GAME|' in x)
 for seq,frame in enumerate(frames):
  raw=bytes.fromhex(''.join(frame));n=(len(raw)+79)//80
  ids=[g*10+i for g in range((n+7)//8) for i in list(range(min(8,n-g*8)))+[8,9]] if n else [0,0,0]
  for id in ids:
   # Exact old record size: checksum decimal digit count can differ, bounded by 9 bytes/transaction.
   chunk=(raw[(id//10*8+id%10)*80:][:80]+b'\0'*80)[:80] if n else b''
   body=f'R|{seq}|{len(raw)}|{len(frame)}|{zlib.adler32(raw)}|{id}|'+chunk.hex()
   baseline+=len('BT_0123456789abcdef_GAME|'+body+'|'+str(zlib.adler32(body.encode())))+1
 compressed=len({x.split('|')[1] for x in lines if x.startswith('D|')})
 row={'game':path.stem.removesuffix('-frames'),'transactions':len(frames),'compressed_transactions':compressed,'wire_bytes':current,'uncompressed_wire_bytes_approx':baseline,'reduction_percent':round(100*(1-current/baseline),1)};results.append(row);print('PASS identical complete draws',json.dumps(row),flush=True)
 assert current<=baseline*1.02,'compression cannot increase transport by another shard'
(out/'results.json').write_text(json.dumps(results,indent=2)+'\n')
assert sum(x['compressed_transactions'] for x in results)>50
print('No discarded draw operations, no simulated game logic, no hardware FPS claim.')
