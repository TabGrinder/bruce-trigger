#!/usr/bin/env python3
"""Host parser benchmark. Reports allocated bytes and time, never physical FPS."""
from pathlib import Path
import argparse,json,subprocess,statistics,hashlib,re
p=argparse.ArgumentParser();p.add_argument('--reference-source',type=Path);a=p.parse_args()
r=Path(__file__).resolve().parents[2];out=r/'build/receiver-benchmark';out.mkdir(parents=True,exist_ok=True)
trace=r/'build/catalog-proof/highway_racer-wire.txt'
assert trace.is_file(),'Run catalog engine tests first'
driver=r'''
package com.tabgrinder.brucetrigger;
import java.nio.file.*;import java.util.*;import java.lang.management.ManagementFactory;
public class ReceiverBench {
 public static void main(String[] args)throws Exception {
  List<String> wire=Files.readAllLines(Paths.get(args[0]));
  com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
  bean.setThreadAllocatedMemoryEnabled(true);long id=Thread.currentThread().getId();
  for(int round=0;round<12;round++) {
   long allocated=bean.getThreadAllocatedBytes(id),start=System.nanoTime(),frames=0,bytes=0;
   for(int rep=0;rep<4;rep++) {
    RecoverableGameStream s=new RecoverableGameStream();
    for(String line:wire) {
     long seq=Long.parseLong(line.split("\\|",3)[1]);
     RecoverableGameStream.Frame f=s.accept(line,seq*100);
     if(!s.failure.equals("None"))throw new AssertionError(s.failure);
     if(f!=null){frames++;bytes+=f.bytes.length;}
    }
   }
   long ns=System.nanoTime()-start,used=bean.getThreadAllocatedBytes(id)-allocated;
   if(round>=5)System.out.println(ns+","+used+","+frames+","+bytes);
  }
 }
}
'''
results={}
paths={'current':r/'app/src/main/java/com/tabgrinder/brucetrigger/RecoverableGameStream.java'}
if a.reference_source:paths={'reference':a.reference_source,**paths}
for label,path in paths.items():
 d=out/label;d.mkdir(exist_ok=True)
 source=re.sub(r'^package [^;]+;', 'package com.tabgrinder.brucetrigger;',path.read_text(),count=1,flags=re.M)
 (d/'RecoverableGameStream.java').write_text(source);(d/'ReceiverBench.java').write_text(driver)
 subprocess.run(['java','com.sun.tools.javac.Main','-cp',str(r/'build/test-usb'),'-d',str(d),str(d/'RecoverableGameStream.java'),str(d/'ReceiverBench.java')],check=True)
 run=subprocess.run(['java','-Xms128m','-Xmx128m','-cp',str(d)+':'+str(r/'build/test-usb'),'com.tabgrinder.brucetrigger.ReceiverBench',str(trace)],capture_output=True,text=True,check=True)
 rows=[[int(x) for x in line.split(',')] for line in run.stdout.splitlines()]
 assert len(rows)==7 and len({(x[2],x[3]) for x in rows})==1
 results[label]={'source_sha256':hashlib.sha256(source.encode()).hexdigest(),'median_ms':statistics.median(x[0] for x in rows)/1e6,'median_allocated_bytes':statistics.median(x[1] for x in rows),'transactions_per_sample':rows[0][2],'drawing_bytes_per_sample':rows[0][3],'samples':rows}
if 'reference' in results:
 assert results['current']['drawing_bytes_per_sample']==results['reference']['drawing_bytes_per_sample']
 results['allocation_reduction_percent']=round(100*(1-results['current']['median_allocated_bytes']/results['reference']['median_allocated_bytes']),1)
results['scope']='Host JVM parser only; same intact Highway Racer draw transcript. Wall times include host scheduling; not Android or LilyGO FPS.'
(out/'result.json').write_text(json.dumps(results,indent=2)+'\n');print(json.dumps(results,indent=2))
