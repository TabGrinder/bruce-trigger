package com.tabgrinder.brucetrigger;
import android.os.Handler;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public final class UsbMirrorRegressionTest {
  static int checks;
  static final String NONCE="0123456789abcdef";
  static final byte[] GEOMETRY={(byte)0xaa,8,99,1,64,0,(byte)170,1};
  static final byte[] CLEAR={(byte)0xaa,5,0,0,0};
  static void ok(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
  static byte[] bytes(String s){return s.getBytes(StandardCharsets.US_ASCII);}
  static byte[] join(byte[]... chunks){int n=0;for(byte[] c:chunks)n+=c.length;byte[] all=new byte[n];int p=0;for(byte[] c:chunks){System.arraycopy(c,0,all,p,c.length);p+=c.length;}return all;}
  static UsbSerialSession verified(){UsbSerialSession s=new UsbSerialSession();s.open(0);s.begin("info",600);s.written(5,5,601);s.receive(bytes(UsbRegressionTest.identity),602);return s;}
  static byte[] body(UsbSerialSession s,boolean start,long now){
    byte[] h=s.beginMirror(start,NONCE,now);ok(h!=null,"fixed capture header accepted");
    s.written(h.length,h.length,now+1);
    s.receive(bytes("Reading input data from serial buffer until EOF\r\n"),now+2);
    byte[] b=s.takeScriptBody();ok(b!=null&&b.length<512,"bounded body released only after input handshake");
    ok(s.takeScriptBody()==null,"body dequeued once");
    int declared=Integer.parseInt(new String(h,StandardCharsets.US_ASCII).trim().substring(19));
    ok(declared==b.length-4,"declared script size excludes EOF terminator");
    ok(new String(b,StandardCharsets.US_ASCII).endsWith("})();\nEOF\n"),"stock USB EOF framing");
    s.written(b.length,b.length,now+3);return b;
  }
  static byte[] reply(boolean start){return join(bytes("# Entering interpreter...\r\n"),start?GEOMETRY:new byte[0],bytes("BT_"+NONCE+(start?"_ON":"_OFF")+"\r\nExiting interpreter...\r\n"),start?CLEAR:new byte[0]);}
  static void direct(){
    ok(UsbMirrorBridge.supports("1.16.1","Lilygo T-Embed CC1101"),"reported board supported");
    ok(UsbMirrorBridge.supports("1.16.1","LILYGO T-Embed CC1101 Plus"),"Plus identity supported");
    ok(!UsbMirrorBridge.supports("1.16","Lilygo T-Embed CC1101")&&!UsbMirrorBridge.supports("1.16.1","Other ESP32"),"unreviewed targets do not get scripts");
    UsbSerialSession unverified=new UsbSerialSession();unverified.open(0);
    ok(unverified.beginMirror(true,NONCE,1000)==null,"no script before verification");
    UsbMirrorBridge bridge=new UsbMirrorBridge();ok(bridge.begin(true,"x\nreboot")==null,"nonce rejects command injection");
    byte[] transcript=reply(true);
    for(int split=0;split<=transcript.length;split++){
      UsbSerialSession s=verified();byte[] b=body(s,true,1000);
      ok(new String(b,StandardCharsets.US_ASCII).contains("serial.cmd(\"display start\")"),"capture invoked from stock JS only");
      ok(s.begin("nav next",1004)==null,"navigation blocked during interpreter setup");
      s.receive(Arrays.copyOfRange(transcript,0,split),1100);s.receive(Arrays.copyOfRange(transcript,split,transcript.length),1101);
      ok(s.verified&&s.stage==UsbSerialSession.Stage.OPEN&&s.mirror.active,"fragmented confirmation and native packets accepted at split "+split);
      ok(s.beginMirror(true,NONCE,1500)==null,"duplicate capture startup prohibited");
      // Continuous screen data must not starve the navigation pacing window.
      for(int n=0;n<30;n++)s.receive(CLEAR,1300+n*20);
      ok(s.canControl(2000),"draw traffic cannot indefinitely block controls");
      // A packet split across a navigation write must remain framed correctly.
      s.receive(Arrays.copyOf(CLEAR,2),2000);byte[] nav=s.begin("nav next",2001);
      ok(nav!=null,"navigation allowed with partial drawing packet");s.written(nav.length,nav.length,2002);
      s.receive(join(Arrays.copyOfRange(CLEAR,2,CLEAR.length),bytes("# ")),2003);
      ok(s.verified&&s.stage==UsbSerialSession.Stage.OPEN,"binary fragment preserved through write receipt");
      body(s,false,2500);s.receive(reply(false),2600);
      ok(!s.mirror.active&&s.stage==UsbSerialSession.Stage.OPEN,"stop requires marker, prompt and interpreter exit");
      s.receive(CLEAR,2650);ok(s.verified,"bounded post-stop tail accepted");
      ok(s.begin("nav next",2800)==null&&!s.canControl(2800),"navigation waits for capture worker drain");
      ok(s.beginMirror(true,NONCE,3100)==null,"cannot restart capture while previous worker may be draining");
      s.receive(CLEAR,3700);ok(s.stage==UsbSerialSession.Stage.BLOCKED,"stream persisting past stop grace is rejected");
    }
    UsbSerialSession s=verified();byte[] h=s.beginMirror(true,NONCE,1000);s.written(h.length-1,h.length,1001);
    ok(s.stage==UsbSerialSession.Stage.BLOCKED&&s.takeScriptBody()==null,"partial header never releases script");
    s=verified();h=s.beginMirror(true,NONCE,1000);s.written(h.length,h.length,1001);s.receive(bytes("# "),1002);
    ok(s.takeScriptBody()==null&&!s.mirror.active,"generic prompt is not input readiness");s.tick(14000);ok(s.stage==UsbSerialSession.Stage.BLOCKED,"missing input handshake expires");
    s=verified();body(s,true,1000);s.receive(bytes("# BT_ffffffffffffffff_ON\nExiting interpreter...\n"),1100);s.tick(14000);
    ok(!s.verified&&!s.mirror.active,"stale marker cannot activate capture");
    s=verified();body(s,true,1000);s.receive(bytes("# BT_"+NONCE+"_ON\n"),1100);s.tick(14000);
    ok(!s.mirror.active,"interpreter exit required before navigation unlocks");
    s=verified();body(s,true,1000);s.receive(bytes("BT_"+NONCE+"_ON_FAIL\n"),1100);
    ok(s.stage==UsbSerialSession.Stage.BLOCKED,"explicit parser rejection fails closed");
    s=verified();body(s,true,1000);s.receive(bytes("Guru Meditation Error\n"),1100);s.receive(reply(true),1101);
    ok(!s.verified&&s.stage==UsbSerialSession.Stage.BLOCKED,"panic revokes operation despite later markers");
    s=verified();body(s,true,1000);s.receive(reply(true),1100);s.tick(15000);
    ok(s.verified&&s.stage==UsbSerialSession.Stage.OPEN,"quiet complete screen has no invented heartbeat deadline");
    s.receive(Arrays.copyOf(CLEAR,2),15100);s.tick(17099);
    ok(s.verified,"binary fragment can complete before gap deadline");
    s.tick(17100);
    ok(s.stage==UsbSerialSession.Stage.BLOCKED&&s.failure.contains("Incomplete screen packet"),"stalled binary record explicitly blocks control");
    s=verified();body(s,true,1000);s.receive(reply(true),1100);
    s.receive(Arrays.copyOf(CLEAR,2),1200);s.receive(Arrays.copyOfRange(CLEAR,2,CLEAR.length),3200);
    ok(s.stage==UsbSerialSession.Stage.BLOCKED,"late fragment cannot become fresh screen evidence");
  }
  static String marker(byte[] body){Matcher m=Pattern.compile("BT_[a-f0-9]{16}_(ON|OFF)").matcher(new String(body,StandardCharsets.US_ASCII));if(!m.find())throw new AssertionError("marker");return m.group();}
  static void finish(UsbRegressionTest.Fixture f,int headerIndex,boolean start)throws Exception{
    UsbRegressionTest.until(()->f.port().outgoing.size()>headerIndex,"script header write");
    f.port().incoming.add(bytes("Reading input data from serial buffer until EOF\n"));
    UsbRegressionTest.until(()->f.port().outgoing.size()>headerIndex+1,"script body write");
    String marker=marker(f.port().outgoing.toArray(new byte[0][])[headerIndex+1]);
    f.port().incoming.add(join(bytes("# Entering interpreter...\n"),start?GEOMETRY:new byte[0],bytes(marker+"\nExiting interpreter...\n"),start?CLEAR:new byte[0]));
    UsbRegressionTest.until(()->!f.controller.session.mirror.busy(),"script acknowledgement");Handler.advance(200);
  }
  static void controller()throws Exception{
    try(UsbRegressionTest.Fixture f=new UsbRegressionTest.Fixture()){
      f.open();f.verify();f.controller.mirror(true);finish(f,1,true);
      ok(!f.controller.session.externalInterpreter,"own mirror bootstrap is not an external game");
      f.port().incoming.add(bytes("Entering interpreter...\n"));
      UsbRegressionTest.until(()->f.controller.session.externalInterpreter,"external interpreter status observed");
      ok(!f.controller.mirror(false),"cannot inject cleanup script over a running interpreter");
      f.controller.closeSafely("background while external script runs");
      UsbRegressionTest.until(()->f.port().closed,"close running script without overwriting it");
      ok(f.port().outgoing.size()==3,"no stop script or replay sent over running game");
      ok(f.controller.diagnostics().contains("running script prevents capture cleanup")&&f.controller.diagnostics().contains("unconfirmed"),"running game closure states cleanup was unconfirmed");
    }
    try(UsbRegressionTest.Fixture f=new UsbRegressionTest.Fixture()){
      f.open();f.verify();f.controller.mirror(true);finish(f,1,true);
      ok(f.controller.mirror(false),"explicit stop starts");finish(f,3,false);
      int token=f.controller.sessionToken();int opens=f.ctx.manager.opens;
      f.controller.connect(f.d);
      ok(f.controller.sessionToken()==token&&f.ctx.manager.opens==opens,"same owner cannot reopen during acknowledged stop drain");
      f.controller.closeSafely("background during drain");
      ok(f.controller.isClosing()&&f.controller.isOpen(),"foreground exit waits for stop drain even after acknowledgement");
      Handler.advance(1200);UsbRegressionTest.until(()->f.port().closed,"drain close completes");
      String closed=f.controller.diagnostics();f.controller.closeSafely("second stop");
      ok(closed.equals(f.controller.diagnostics()),"idle lifecycle callback preserves first closure diagnostics");
      ok(f.port().outgoing.size()==5,"explicit stop followed by background does not repeat script");
    }
    try(UsbRegressionTest.Fixture f=new UsbRegressionTest.Fixture()){
      f.open();f.verify();f.controller.mirror(true);finish(f,1,true);
      f.controller.mirror(false);finish(f,3,false);
      android.content.Context next=new android.content.Context();next.manager.devices.put(f.d.name,f.d);
      UsbController replacement=new UsbController(next,new UsbRegressionTest.Listener());
      replacement.connect(f.d);
      ok(next.manager.opens==0&&f.controller.isClosing(),"new Activity owner waits for prior capture drain");
      Handler.advance(1200);UsbRegressionTest.until(()->f.port().closed,"previous owner drain finishes");
      replacement.destroy();
    }
    try(UsbRegressionTest.Fixture f=new UsbRegressionTest.Fixture()){
      f.open();f.verify();ok(f.controller.mirror(true),"controller starts reviewed RAM script");
      finish(f,1,true);ok(f.controller.isReady()&&f.controller.hasCapture(),"controller ready after completed script");
      int token=f.controller.sessionToken();f.controller.connect(f.d);
      ok(f.controller.sessionToken()==token&&f.controller.hasCapture(),"reselect cannot replace an active capture");
      ok(!f.controller.command("info")&&!f.controller.command("reboot"),"diagnostics/power blocked until capture stops");
      ok(f.controller.command("nav next"),"navigation still supported during capture");
      UsbRegressionTest.until(()->f.port().outgoing.size()==4,"nav delivered");f.port().incoming.add(bytes("# "));
      UsbRegressionTest.until(()->f.controller.session.stage==UsbSerialSession.Stage.OPEN,"nav replied");Handler.advance(200);
      f.controller.closeSafely("background fixture");ok(f.controller.isClosing()&&!f.controller.command("nav prev"),"background cleanup rejects fresh input");
      finish(f,4,false);Handler.advance(1500);UsbRegressionTest.until(()->f.port().closed,"stop then close");
      ok(!f.controller.isOpen()&&f.controller.diagnostics().contains("capture stop acknowledged"),"cleanup records acknowledged stop");
      ok(f.port().outgoing.size()==6,"one start, one nav, one stop; no replay");
    }
    try(UsbRegressionTest.Fixture f=new UsbRegressionTest.Fixture()){
      f.open();f.verify();f.controller.mirror(true);finish(f,1,true);
      f.controller.closeSafely("paused lifecycle fixture");
      finish(f,3,false);
      ok(f.controller.isClosing(),"confirmed stop retains bounded worker drain grace");
      Handler.suspend(20000);Handler.dispatchOverdue();
      UsbRegressionTest.until(()->f.port().closed,"delayed handler finishes confirmed stop");
      ok(f.controller.diagnostics().contains("paused lifecycle fixture; capture stop acknowledged"),"received stop acknowledgement wins over delayed deadline callback");
      ok(!f.controller.diagnostics().contains("Restart Bruce"),"confirmed stop never emits restart warning after sleep");
      ok(f.port().outgoing.size()==5,"suspended cleanup does not replay stop script");
    }
    try(UsbRegressionTest.Fixture f=new UsbRegressionTest.Fixture()){
      f.open();f.verify();f.controller.mirror(true);finish(f,1,true);
      f.controller.closeSafely("first lifecycle event");
      f.controller.closeSafely("second lifecycle event");
      ok(f.controller.isClosing()&&f.controller.hasCapture(),"repeated lifecycle event does not discard pending cleanup");
      finish(f,3,false);Handler.advance(1500);
      UsbRegressionTest.until(()->f.port().closed,"repeated lifecycle cleanup completes");
      ok(f.controller.diagnostics().contains("first lifecycle event; capture stop acknowledged"),"original closure reason retained with stop evidence");
      ok(f.port().outgoing.size()==5,"repeated lifecycle events send one stop script");
    }
    try(UsbRegressionTest.Fixture f=new UsbRegressionTest.Fixture()){
      f.open();f.verify();f.controller.mirror(true);finish(f,1,true);f.controller.destroy();
      Handler.suspend(17000);Handler.dispatchOverdue();UsbRegressionTest.until(()->f.port().closed,"cleanup bounded despite missing reply and suspended handler");
      ok(f.controller.diagnostics().contains("unconfirmed"),"failed cleanup never claims stopped capture");
    }
  }
  public static void main(String[] args)throws Exception{direct();controller();System.out.println("PASS "+checks+" mirror bridge assertions; host/USB doubles only, no physical LILYGO.");}
}
