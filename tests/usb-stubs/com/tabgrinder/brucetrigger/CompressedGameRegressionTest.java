package com.tabgrinder.brucetrigger;
import java.util.*;
import java.io.*;
/** Independent encoder, exhaustive erasures and malformed compressed transactions. */
public final class CompressedGameRegressionTest {
 static int checks;
 static void ok(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
 static List<String> wire(int seq,byte[] data,boolean delta){
  List<String> out=new ArrayList<>();
  for(String line:RecoveryGameRegressionTest.encode(seq,data,1)){
   if(delta){String body="D"+line.substring(1,line.lastIndexOf('|'));line=body+"|"+RecoveryGameRegressionTest.sum(body.getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
   out.add(line);
  }return out;
 }
 static byte[] pack(byte[] before,byte[] after){
  ByteArrayOutputStream out=new ByteArrayOutputStream();int at=0;
  while(at<after.length){int v=(before[at]^after[at])&255,n=1;
   while(n<130&&at+n<after.length&&((before[at+n]^after[at+n])&255)==v)n++;
   if(n>=3){out.write(128+n-3);out.write(v);at+=n;}
   else{int start=at;at++;
    while(at<after.length&&at-start<128){if(at+2<after.length&&(before[at]^after[at])==(before[at+1]^after[at+1])&&(before[at]^after[at])==(before[at+2]^after[at+2]))break;at++;}
    out.write(at-start-1);for(int i=start;i<at;i++)out.write(before[i]^after[i]);
   }
  }return out.toByteArray();
 }
 static RecoverableGameStream.Frame feed(RecoverableGameStream s,List<String> wire,int eraseA,int eraseB){
  RecoverableGameStream.Frame frame=null;
  for(String line:wire){int id=Integer.parseInt(line.split("\\|")[5])%10;if(id==eraseA||id==eraseB)continue;
   RecoverableGameStream.Frame f=s.accept(line,1000);if(f!=null){ok(frame==null,"single presentation");frame=f;}}
  return frame;
 }
 static RecoverableGameStream seeded(byte[] bytes){RecoverableGameStream s=new RecoverableGameStream();ok(feed(s,wire(0,bytes,false),-1,-1)!=null,"valid base");return s;}
 public static void main(String[] args){
  Random random=new Random(21616);
  for(int size:new int[]{1,2,3,79,80,81,127,128,129,130,131,639,640,641,3800,4096}){
   byte[] a=new byte[size],b=new byte[size];random.nextBytes(a);System.arraycopy(a,0,b,0,size);for(int i=0;i<size;i+=31)b[i]^=7;
   byte[] packed=pack(a,b);if(packed.length>4096)continue;
   for(int e1=-1;e1<10;e1++)for(int e2=e1+1;e2<10;e2++){
    RecoverableGameStream s=seeded(a);RecoverableGameStream.Frame f=feed(s,wire(1,packed,true),e1,e2);
    ok(f!=null&&Arrays.equals(b,f.bytes)&&s.failure.equals("None"),"two losses preserve every decoded byte at size "+size);
    ok(s.compressedFrames==1&&s.wireBytes==size+packed.length&&s.decodedBytes==size*2,"honest payload accounting");
    // Heartbeat cannot destroy the compression base.
    List<String> heartbeat=RecoveryGameRegressionTest.encode(2,new byte[0],0);feed(s,heartbeat,-1,-1);
    f=feed(s,wire(3,pack(b,a),true),-1,-1);ok(f!=null&&Arrays.equals(a,f.bytes),"base survives heartbeat and parity duplicates");
   }
  }
  byte[] base=new byte[81];
  for(byte[] malformed:new byte[][]{{(byte)128},{0},{(byte)255,0},{(byte)128,0},{0,0,0},new byte[0]}){
   RecoverableGameStream s=seeded(base);feed(s,wire(1,malformed,true),-1,-1);ok(!s.failure.equals("None"),"reject truncation, under/overflow and empty delta");
  }
  RecoverableGameStream s=new RecoverableGameStream();feed(s,wire(0,pack(base,base),true),-1,-1);ok(s.failure.contains("missing base"),"cannot start with delta");
  s=seeded(base);s.reset();feed(s,wire(0,pack(base,base),true),-1,-1);ok(s.failure.contains("missing base"),"reset cannot reuse another session's base");
  s=seeded(base);s.accept(wire(1,new byte[81],true).get(0),1000);s.accept(wire(1,new byte[81],false).get(1),1000);ok(s.failure.contains("metadata"),"mixed encoding in one transaction rejected");
  s=seeded(base);feed(s,wire(2,pack(base,base),true),-1,-1);ok(s.failure.contains("capacity"),"missing delta never skipped");
  System.out.println("PASS "+checks+" compressed graphics boundary, corruption, erasure, heartbeat, duplicate, reset and byte-equivalence assertions.");
 }
}
