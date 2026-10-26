package com.tabgrinder.brucetrigger;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

/** One interpreter owns native-capture shutdown and the selected game. */
public final class HandoverRegressionTest {
  static int checks;
  static String template;
  static final String NONCE="0123456789abcdef", TAG="BT_"+NONCE+"_GAME|";
  static void ok(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
  static void feed(UsbSerialSession s,String line,long now){s.receive(line.getBytes(StandardCharsets.US_ASCII),now);}
  static UsbSerialSession start(boolean active,boolean splitNative) {
    UsbSerialSession s=UsbMirrorRegressionTest.verified();s.mirror.active=active;
    if(splitNative)s.receive(Arrays.copyOfRange(UsbMirrorRegressionTest.GEOMETRY,0,4),990);
    byte[] h=s.beginFilesGame(template,NONCE,"/Games/Dino.js",1000);
    ok(h!=null,"verified capture can hand over without a separate stop upload");
    ok(s.beginMirror(false,NONCE,1001)==null&&s.beginThemeSync(NONCE,1001)==null,"one interpreter owner during handover");
    if(splitNative)s.receive(Arrays.copyOfRange(UsbMirrorRegressionTest.GEOMETRY,4,8),1001);
    s.written(h.length,h.length,1002);
    feed(s,"Reading input data from serial buffer until EOF\n",1003);
    byte[] body=s.takeScriptBody();ok(body!=null,"native packet split preserves upload handshake");
    String source=new String(body,StandardCharsets.US_ASCII);
    ok(source.contains("capture="+active),"capture state is explicit in the pinned script");
    ok(!source.contains("@@")&&source.endsWith("EOF\n"),"all placeholders resolved in one bounded upload");
    s.written(body.length,body.length,1004);return s;
  }
  public static void main(String[] args)throws Exception {
    Path root=Paths.get(System.getProperty("project.root"));
    template=new String(Files.readAllBytes(root.resolve("app/src/main/res/raw/files_bridge.txt")),StandardCharsets.US_ASCII);
    String payload="# Entering interpreter...\n"+TAG+"CAPTURE_OFF\n"+TAG+"CAPTURE_OFF\n"+TAG+"CAPTURE_OFF\n"+TAG+"SEARCH\n"+TAG+"ON\n";
    for(int cut=0;cut<=payload.length();cut++) {
      UsbSerialSession s=start(true,true);feed(s,payload.substring(0,cut),1100);feed(s,payload.substring(cut),1101);
      ok(s.verified&&s.game.active&&!s.mirror.active&&s.stage==UsbSerialSession.Stage.OPEN,"every handover split starts only the owned game");
      ok(!s.game.screen.usable(1101)&&s.writes==3,"handover never invents pixels or sends extra commands");
      ok(s.beginFilesGame(template,NONCE,"/a.js",3000)==null,"cannot overlap another game interpreter");
    }
    for(int received=1;received<=3;received++) {
      UsbSerialSession s=start(true,false);feed(s,"# Entering interpreter...\n",1100);
      for(int n=0;n<received;n++)feed(s,TAG+"CAPTURE_OFF\n",1101+n);
      feed(s,TAG+"SEARCH\n"+TAG+"ON\n",1110);
      ok(s.game.active&&!s.mirror.active,"one or two lost lifecycle markers remain recoverable");
      for(String record:RecoveryGameRegressionTest.encode(0,GameRegressionTest.initial(),2))feed(s,TAG+record+"\n",1200);
      ok(s.game.screen.usable(1201),"only a complete checked game transaction creates fresh pixels");
      feed(s,TAG+"DONE\nExiting interpreter...\n",2200);
      ok(s.game.ended()&&s.verified&&s.beginMirror(true,NONCE,2500)!=null,"confirmed game completion releases native restoration");
    }
    UsbSerialSession s=start(true,false);feed(s,"# Entering interpreter...\n"+TAG+"SEARCH\n"+TAG+"ON\n",1100);
    ok(!s.verified&&!s.game.active&&s.mirror.active,"missing handover proof cannot start a game or clear native ownership");
    s=start(true,false);feed(s,"# Entering interpreter...\nBT_ffffffffffffffff_GAME|CAPTURE_OFF\n",1100);
    ok(!s.game.captureStopped&&s.mirror.active,"wrong nonce cannot stop native capture in session state");
    feed(s,TAG+"REASON|capture stop\n"+TAG+"UNSUPPORTED\nExiting interpreter...\n",1200);
    ok(s.game.ended()&&!s.game.active&&s.mirror.active&&s.game.outcome.contains("capture stop"),"failed handover preserves precise reason and capture ownership");
    s=start(false,false);feed(s,"# Entering interpreter...\n"+TAG+"SEARCH\n"+TAG+"ON\n",1100);
    ok(s.game.active&&!s.game.captureHandover,"capture-free launch does not require an irrelevant handover marker");
    s=start(true,false);s.block("Upload interrupted");ok(s.beginGameStop(1200)==null,"no exit bytes injected into an uncertain RAM upload");
    Path out=root.resolve("build/handover");Files.createDirectories(out);
    Files.write(out.resolve("capture.js"),FilesGameScript.source(template,NONCE,"/scripts/Games/flappybird.js",true).getBytes(StandardCharsets.US_ASCII));
    Files.write(out.resolve("direct.js"),FilesGameScript.source(template,NONCE,"/scripts/Games/flappybird.js",false).getBytes(StandardCharsets.US_ASCII));
    System.out.println("PASS "+checks+" single-interpreter handover, every split, native packet continuity, marker loss, ownership and return-to-capture checks; USB doubled.");
  }
}
