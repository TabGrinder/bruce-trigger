package com.tabgrinder.brucetrigger;

import android.hardware.usb.UsbDeviceConnection;
import android.os.Handler;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Models USB packet completion, not the old idealized byte-array read double.
 * USBDEVFS_BULK returns a negative status without copying partially received
 * data on timeout. A full packet completes an endpoint-sized read immediately;
 * it does not complete a larger read until a short/ZLP, requested length, or error.
 * No claim that this reproduces a particular phone kernel or cable trace. */
public final class UsbPacketTimeoutRegressionTest {
  static int checks;
  static void ok(boolean b,String text){checks++;if(!b)throw new AssertionError(text);}
  static final String TAG="BT_0123456789abcdef_GAME|";
  static final byte[] TIMEOUT=new byte[0];
  static final class Packets implements UsbDeviceConnection.ReadSource {
    final BlockingQueue<byte[]> packets=new LinkedBlockingQueue<>();
    volatile int partialTimeouts,discarded,maxRequest,calls;
    public int read(byte[] buffer,int length,int timeout) {
      calls++;maxRequest=Math.max(maxRequest,length);
      ByteArrayOutputStream pending=new ByteArrayOutputStream();
      try {
        while(pending.size()<length) {
          byte[] p=packets.poll(15,TimeUnit.MILLISECONDS);
          if(p==null||p==TIMEOUT){
            if(pending.size()>0){partialTimeouts++;discarded+=pending.size();}
            return -1; // kernel does not expose the partial bytes on this path
          }
          if(p.length>64||pending.size()+p.length>length)throw new AssertionError("invalid USB packet request");
          pending.write(p,0,p.length);
          if(p.length<64||pending.size()==length)break;
        }
        byte[] out=pending.toByteArray();System.arraycopy(out,0,buffer,0,out.length);return out.length;
      } catch(InterruptedException e){Thread.currentThread().interrupt();return -1;}
    }
    void add(byte[] bytes,boolean terminate) {
      for(int at=0;at<bytes.length;at+=64)packets.add(Arrays.copyOfRange(bytes,at,Math.min(at+64,bytes.length)));
      if(terminate&&bytes.length%64==0)packets.add(new byte[0]); // actual ZLP, distinct from TIMEOUT
    }
  }
  static final class Listener extends UsbRegressionTest.Listener {
    UsbSerialSession session;long drawings;final java.util.zip.CRC32 drawingHash=new java.util.zip.CRC32();final ByteArrayOutputStream delivered=new ByteArrayOutputStream();
    public void data(byte[] b){super.data(b);delivered.write(b,0,b.length);if(session!=null){java.util.List<byte[]> frame;while((frame=session.game.screen.takeFrame())!=null){drawings++;for(byte[] packet:frame)drawingHash.update(packet);}}}
  }
  static void start(UsbRegressionTest.Fixture f,String template) {
    UsbSerialSession s=f.controller.session;long now=android.os.SystemClock.elapsedRealtime();
    byte[] h=s.beginFilesGame(template,"0123456789abcdef","/Games/Dino.js",now);
    ok(h!=null,"startup has a verified USB session");s.written(h.length,h.length,now+1);
    s.receive(UsbRegressionTest.bytes("Reading input data from serial buffer until EOF\n"),now+2);
    byte[] body=s.takeScriptBody();s.written(body.length,body.length,now+3);
    s.receive(UsbRegressionTest.bytes("# Entering interpreter...\n"+TAG+"SEARCH\n"+TAG+"ON\n"),now+4);
    ok(s.game.active,"owned JS startup (synthetic); reads below use the actual production pump");Handler.advance(200);
  }
  static byte[] wire(String records){StringBuilder b=new StringBuilder("\n");for(String line:records.split("\n"))b.append(TAG).append(line).append('\n');return UsbRegressionTest.bytes(b.toString());}
  static void packetPause(String template)throws Exception {
    Listener listener=new Listener();
    try(UsbRegressionTest.Fixture f=new UsbRegressionTest.Fixture(listener)) {
      f.open();f.verify();listener.session=f.controller.session;start(f,template);
      for(int i=0;i<24;i++){
        f.port().incoming.add(wire(GameRegressionTest.frame(i,i==0?GameRegressionTest.initial():UsbMirrorRegressionTest.CLEAR,i==0?2:1)));
        final int count=i+1;UsbRegressionTest.until(()->f.controller.session.game.screen.frames==count,"first 24 intact transactions");
      }
      ok(f.controller.command("nav select"),"real outbound SELECT before the problematic drawing");
      UsbRegressionTest.until(()->f.port().outgoing.size()==2,"SELECT host receipt");Handler.runCurrent();
      Packets source=new Packets();f.port().readSource=source;
      source.add(UsbRegressionTest.bytes("COMMAND: nav select\nSelect Pressed\n# \n"),true);
      byte[] data=new byte[500];for(int i=0;i<100;i++)System.arraycopy(UsbMirrorRegressionTest.CLEAR,0,data,i*5,5);
      String frame=GameRegressionTest.frame(24,data,100);int firstEnd=frame.indexOf('\n');
      byte[] first=wire(frame.substring(0,firstEnd));
      byte[] aligned=new byte[((first.length+63)/64)*64];Arrays.fill(aligned,(byte)'\n');
      System.arraycopy(first,0,aligned,aligned.length-first.length,first.length);
      source.add(aligned,false);source.packets.add(TIMEOUT); // full packets followed by a bus pause
      source.add(wire(frame.substring(firstEnd+1)),true);
      UsbRegressionTest.until(()->f.controller.session.game.screen.frames==25||f.controller.session.stage==UsbSerialSession.Stage.BLOCKED,"pause outcome");
      ok(source.maxRequest==64&&source.partialTimeouts==0&&source.discarded==0,"one-packet reads retain all received bytes across timeout");
      ok(f.controller.session.game.screen.frames==25&&listener.drawings==25&&f.controller.isReady(),"25 complete transactions, control and device game remain active");
      ok(f.port().outgoing.size()==2,"no cleanup, command replay or extra script issued");
      System.out.println("PASS packet pause: requested="+source.maxRequest+", partial timeouts="+source.partialTimeouts+", bytes discarded="+source.discarded+", frames="+f.controller.session.game.screen.frames);
      f.controller.disconnect("Test ended");
    }
  }
  static void fullTrace(String template,Path path,boolean losses)throws Exception {
    Listener listener=new Listener();
    try(UsbRegressionTest.Fixture f=new UsbRegressionTest.Fixture(listener)) {
      f.open();f.verify();listener.session=f.controller.session;start(f,template);
      int from=listener.delivered.size();long expectedFrames=Files.readAllLines(path).stream().filter(x->x.startsWith("R|")||x.startsWith("D|")).map(x->x.split("\\|",-1)[1]).distinct().count();
      StringBuilder transmission=new StringBuilder();java.util.zip.CRC32 expectedHash=new java.util.zip.CRC32();
      GameScreenStream reference=new GameScreenStream();
      int duplicateHeartbeat=0;long previousSequence=-1;
      for(String line:Files.readAllLines(path)){
        String[] fields=line.split("\\|",-1);long seq=Long.parseLong(fields[1]);
        ok(reference.line(line,seq*80),"reference transaction valid");
        java.util.List<byte[]> frame;while((frame=reference.takeFrame())!=null)for(byte[] packet:frame)expectedHash.update(packet);
        if(seq!=previousSequence){previousSequence=seq;duplicateHeartbeat=0;}
        int index=Integer.parseInt(fields[5])%10;
        if(losses){
          if(fields[2].equals("0")){if(duplicateHeartbeat++<2)continue;}
          else if(index==1)continue;
          else if(index==0)line=RecoveryGameRegressionTest.damage(line);
        }
        transmission.append(line).append('\n');
      }
      byte[] raw=wire(transmission.toString());
      Packets source=new Packets();
      // Bus pauses after full packets, including inside text records, need no parser workaround.
      for(int at=0,i=0;at<raw.length;at+=64,i++){
        source.packets.add(Arrays.copyOfRange(raw,at,Math.min(at+64,raw.length)));
        if(i%37==36)source.packets.add(TIMEOUT);
      }
      if(raw.length%64==0)source.packets.add(new byte[0]);
      f.port().readSource=source;Thread.sleep(100); // bounded consumer backlog, not a device-speed benchmark
      long deadline=System.nanoTime()+30_000_000_000L;
      while((f.controller.session.game.screen.frames<expectedFrames||listener.delivered.size()-from<raw.length)&&f.controller.session.stage!=UsbSerialSession.Stage.BLOCKED&&System.nanoTime()<deadline){Handler.advance(5);Thread.sleep(1);}
      ok(f.controller.session.game.screen.frames==expectedFrames&&f.controller.session.game.screen.rejected==0,"complete trace preserved: "+path.getFileName()+" "+f.controller.session.failure);
      byte[] all=listener.delivered.toByteArray();ok(Arrays.equals(raw,Arrays.copyOfRange(all,from,all.length)),"byte-exact stream including every timeout split");
      ok(source.maxRequest==64&&source.discarded==0,"no multi-packet partial timeout loss");
      ok(listener.drawingHash.getValue()==expectedHash.getValue(),"every rendered draw byte agrees with the intact reference");
      if(losses)ok(f.controller.session.game.screen.recovery.repairedShards>0&&f.controller.session.game.screen.recovery.damagedRecords>0,"real read pump exercised recovery and corruption");
      ok(f.port().outgoing.size()==1&&f.controller.isReady(),"no input replay, exit or interpreter restart during trace");
      System.out.println("PASS production USB pump "+(losses?"loss+corruption ":"intact ")+path.getFileName()+": "+expectedFrames+" transactions, "+raw.length+" identical bytes, partial timeout losses=0");
      f.controller.disconnect("Test ended");
    }
  }
  public static void main(String[] args)throws Exception {
    Path root=Paths.get(System.getProperty("project.root"));String template=new String(Files.readAllBytes(root.resolve("app/src/main/res/raw/files_bridge.txt")),StandardCharsets.US_ASCII);
    packetPause(template);
    if(Arrays.asList(args).contains("--catalog"))for(String name:new String[]{"dino_game","flappybird","highway_racer","pingpong","space_shooter","tamagochi"})for(boolean losses:new boolean[]{false,true})fullTrace(template,root.resolve("build/catalog-proof/"+name+"-wire.txt"),losses);
    System.out.println("PASS "+checks+" packet-level checks. USB/kernel behavior is modeled; no physical LilyGO or Android phone attached.");
  }
}
