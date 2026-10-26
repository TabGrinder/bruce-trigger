#!/usr/bin/env python3
"""Identical ASCII stream allocation comparison; never hardware FPS."""
from pathlib import Path
import argparse,subprocess,json,statistics,hashlib
p=argparse.ArgumentParser();p.add_argument('--reference-source',type=Path);a=p.parse_args()
r=Path(__file__).resolve().parents[2];out=r/'build/decoder-benchmark';out.mkdir(exist_ok=True)
trace=r/'build/catalog-proof/highway_racer-wire.txt'
driver=r'''
package com.tabgrinder.brucetrigger;
import java.nio.file.*;import java.util.*;import java.lang.management.ManagementFactory;
public class DecoderBench {
 static long count,hash;
 public static void main(String[] args)throws Exception {
  byte[] wire=Files.readAllBytes(Paths.get(args[0]));List<byte[]> chunks=new ArrayList<>();
  for(int i=0;i<wire.length;i+=64)chunks.add(Arrays.copyOfRange(wire,i,Math.min(i+64,wire.length)));
  FrameDecoder parser=new FrameDecoder(new FrameDecoder.Sink(){
   public void frame(byte[] b){throw new AssertionError("ASCII fixture");}
   public void line(String s){count++;hash=hash*31+s.hashCode();}
   public void malformed(){throw new AssertionError("Malformed fixture");}
  });
  com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
  bean.setThreadAllocatedMemoryEnabled(true);long id=Thread.currentThread().getId();
  for(int round=0;round<12;round++) {
   count=hash=0;parser.reset();long bytes=bean.getThreadAllocatedBytes(id),start=System.nanoTime();
   for(int rep=0;rep<20;rep++)for(byte[] chunk:chunks)parser.accept(chunk);
   long ns=System.nanoTime()-start,allocated=bean.getThreadAllocatedBytes(id)-bytes;
   if(round>=5)System.out.println(ns+","+allocated+","+count+","+hash);
  }
 }
}
'''
paths={'current':r/'app/src/main/java/com/tabgrinder/brucetrigger/FrameDecoder.java'}
if a.reference_source:paths={'reference':a.reference_source,**paths}
results={}
for name,path in paths.items():
 d=out/name;d.mkdir(exist_ok=True);source=path.read_bytes();(d/'FrameDecoder.java').write_bytes(source);(d/'DecoderBench.java').write_text(driver)
 subprocess.run(['java','com.sun.tools.javac.Main','-d',str(d),str(d/'FrameDecoder.java'),str(d/'DecoderBench.java'),str(r/'app/src/main/java/com/tabgrinder/brucetrigger/FrameValidator.java')],check=True)
 run=subprocess.run(['java','-Xms128m','-Xmx128m','-cp',str(d),'com.tabgrinder.brucetrigger.DecoderBench',str(trace)],capture_output=True,text=True,check=True)
 rows=[[int(x) for x in line.split(',')] for line in run.stdout.splitlines()]
 assert len(rows)==7 and len({tuple(row[2:]) for row in rows})==1
 results[name]={'source_sha256':hashlib.sha256(source).hexdigest(),'median_ms':statistics.median(row[0] for row in rows)/1e6,'median_allocated_bytes':statistics.median(row[1] for row in rows),'lines':rows[0][2],'output_hash':rows[0][3]}
if a.reference_source:
 assert results['reference']['lines']==results['current']['lines'] and results['reference']['output_hash']==results['current']['output_hash']
 results['allocation_reduction_percent']=round(100*(1-results['current']['median_allocated_bytes']/results['reference']['median_allocated_bytes']),1)
results['scope']='Host JVM only, identical ASCII game transcript in 64-byte chunks; no claim about phone/LilyGO FPS.'
(out/'result.json').write_text(json.dumps(results,indent=2)+'\n');print(json.dumps(results,indent=2))
