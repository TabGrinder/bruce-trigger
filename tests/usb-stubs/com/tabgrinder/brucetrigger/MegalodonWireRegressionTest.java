package com.tabgrinder.brucetrigger;
import java.nio.file.*;import java.util.*;import java.io.*;
public final class MegalodonWireRegressionTest {
 public static void main(String[] args)throws Exception {
  List<String> wire=Files.readAllLines(Paths.get(args[0]));byte[] reference=null;long frames=0;int checks=0;
  for(int fault=0;fault<4;fault++){
   GameScreenStream screen=new GameScreenStream();ByteArrayOutputStream all=new ByteArrayOutputStream();
   for(String line:wire){String[] parts=line.split("\\|");long seq=Long.parseLong(parts[1]);int id=Integer.parseInt(parts[5])%10;
    if(fault==1&&(id==0||id==1))continue;
    if(fault==2&&(id==0||id==9))line=RecoveryGameRegressionTest.damage(line);
    if(fault==3&&(id==8||id==9))continue;
    if(!screen.line(line,1000+seq*55))throw new AssertionError(screen.failure);checks++;
    List<byte[]> frame;while((frame=screen.takeFrame())!=null)for(byte[] p:frame)all.write(p);
   }
   if(fault==0){reference=all.toByteArray();frames=screen.frames;}
   else if(!Arrays.equals(reference,all.toByteArray())||screen.frames!=frames)throw new AssertionError("damaged Megalodon transaction changed drawings");
   if(screen.frames<200||screen.rejected!=0)throw new AssertionError("incomplete game stream");
  }
  System.out.println("PASS "+checks+" real Megalodon wire assertions; "+frames+" complete frames; every byte survives two erased/corrupt shards.");
 }
}
