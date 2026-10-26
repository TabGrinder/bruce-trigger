package com.tabgrinder.brucetrigger;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.Adler32;
import android.os.Handler;

/** Adversarial receiver and ownership/lifecycle regressions for the fixed game bridge. */
public final class GameRegressionTest {
  static int checks;
  static final String NONCE="0123456789abcdef", PREFIX="BT_"+NONCE+"_GAME|";
  static String template;
  static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
  static byte[] bytes(String text){return text.getBytes(StandardCharsets.US_ASCII);}
  static byte[] initial(){return UsbMirrorRegressionTest.join(UsbMirrorRegressionTest.GEOMETRY,UsbMirrorRegressionTest.CLEAR);}
  static String frame(long sequence, byte[] data, int count){
    return String.join("\n",RecoveryGameRegressionTest.encode(sequence,data,count))+"\n";
  }

  static String wire(String payload){return PREFIX+payload.replace("\n","\n"+PREFIX).replaceAll(java.util.regex.Pattern.quote(PREFIX)+"$","");}
  static boolean feed(GameScreenStream screen,String frames,long now){for(String line:frames.split("\n"))if(!screen.line(line,now))return false;return true;}
  static UsbSerialSession starting(){
    UsbSerialSession s=UsbMirrorRegressionTest.verified();byte[] header=s.beginFilesGame(template,NONCE,"/Games/Dino.js",1000);
    ok(header!=null,"verified supported profile may start fixed game bridge");s.written(header.length,header.length,1001);
    s.receive(bytes("Reading input data from serial buffer until EOF\n"),1002);byte[] body=s.takeScriptBody();
    ok(body!=null&&s.takeScriptBody()==null,"game body released exactly once after input readiness");
    ok(body.length-4==Integer.parseInt(new String(header,StandardCharsets.US_ASCII).trim().substring(19)),"upload declares exact script bytes excluding EOF");
    for(String line:new String(body,StandardCharsets.US_ASCII).split("\n"))ok(line.length()<=240,"fixed script respects CLI line bounds");
    s.written(body.length,body.length,1003);return s;
  }
  static UsbSerialSession running(){UsbSerialSession s=starting();s.receive(bytes("# Entering interpreter...\n"+PREFIX+"SEARCH\n"+PREFIX+"ON\n"+wire(frame(0,initial(),2))),1100);ok(s.game.active&&!s.game.uploading()&&s.stage==UsbSerialSession.Stage.OPEN,"owned running game needs nonce marker and prompt");return s;}
  static void receiver(){
    GameScreenStream s=new GameScreenStream();ok(feed(s,frame(0,initial(),2),1000),"initial clear and geometry accepted");ok(s.usable(1001)&&!s.usable(3500),"freshness has strict lifetime");ok(s.takeFrame().size()==2&&s.takeFrame()==null,"complete draw frame delivered once");
    ok(feed(s,frame(1,new byte[0],0),1200)&&s.lastFrame==1200&&s.takeFrame()==null,"empty heartbeat preserves freshness without fake drawings");
    for(String bad:new String[]{"B|0","D|aa","F|0|0|aa","R|0|999999|0|1|0|aa|1",new String(new char[545]).replace('\0','x')}){
      s=new GameScreenStream();s.line(bad,1000);ok(!s.usable(1000)&&s.takeFrame()==null,"unsupported/incomplete record never initializes graphics");
    }
    String good=frame(0,initial(),2);
    for(String bad:new String[]{frame(0,initial(),3),frame(0,UsbMirrorRegressionTest.CLEAR,1),frame(0,new byte[0],0)}){
      s=new GameScreenStream();ok(!feed(s,bad,1000)&&s.takeFrame()==null,"incomplete frame never published");
    }
    s=new GameScreenStream();feed(s,good,1000);ok(!feed(s,frame(1,UsbMirrorRegressionTest.GEOMETRY,1),1001),"repeated geometry cannot reset the sequence");
    s=new GameScreenStream();feed(s,good,1000);for(int n=1;n<16;n++)ok(feed(s,frame(n,UsbMirrorRegressionTest.CLEAR,1),1000+n),"bounded queue accepts frame");ok(!feed(s,frame(16,UsbMirrorRegressionTest.CLEAR,1),1016)&&s.takeFrame()==null,"overfull queue invalidates, does not silently drop deltas");
    s=new GameScreenStream();feed(s,good,0);boolean rateStopped=false;for(int n=1;n<17000;n++)if(!feed(s,frame(n,new byte[0],0),0)){rateStopped=true;break;}ok(rateStopped,"heartbeat rate bounded");
    Random random=new Random(211);for(int n=0;n<10000;n++){s=new GameScreenStream();byte[] data=new byte[random.nextInt(200)];random.nextBytes(data);feed(s,frame(0,data,random.nextInt(10)),1000);ok(!s.usable(1000)&&s.takeFrame()==null,"random data never initializes a screen");}
  }
  static void sessions(){
    UsbSerialSession stale=new UsbSerialSession();stale.open(0);
    stale.receive(bytes("BT_ffffffffffffffff_GAME|B|105\n"),100);
    byte[] identify=stale.begin("info",600);stale.written(identify.length,identify.length,601);
    stale.receive(bytes(UsbRegressionTest.identity),602);
    ok(stale.verified&&stale.externalInterpreter,"bridge output before identity reveals an existing interpreter without an Entering banner");
    ok(stale.beginThemeSync(NONCE,1000)==null&&stale.beginMirror(true,NONCE,1000)==null&&stale.beginFilesGame(template,NONCE,"/Games/Dino.js",1000)==null,"late game stream cannot be overwritten by theme, mirror or game upload");
    UsbSerialSession damaged=running();
    damaged.game.screen.line("INVALID",1300);damaged.block("Fixture corrupted game graphics");
    ok(damaged.begin("nav select",1400)==null,"graphics failure never authorizes normal input");
    byte[] emergency=damaged.beginGameStop(1400);
    ok(Arrays.equals(emergency,bytes("js exit\n")),"owned active interpreter gets one bounded exit after graphics failure");
    damaged.written(emergency.length,emergency.length,1401);
    damaged.receive(bytes(PREFIX+"B|999\nBT_ffffffffffffffff_GAME|STOP\n# "),1402);
    ok(damaged.game.engaged()&&!damaged.verified,"damaged graphics and other nonce cannot acknowledge cleanup");
    damaged.receive(bytes(PREFIX+"STOP\nExiting interpreter...\n"),1403);
    ok(damaged.game.ended()&&damaged.stage==UsbSerialSession.Stage.OPEN&&!damaged.verified,"exit acknowledgement closes owned script without unlocking control");
    ok(damaged.failure.equals("Fixture corrupted game graphics")&&damaged.beginGameStop(1500)==null,"cleanup preserves root failure and never repeats the exit");
    damaged=running();byte[] input=damaged.begin("nav select",1400);damaged.written(input.length-1,input.length,1401);
    ok(damaged.beginGameStop(1500)==null,"partial write cannot append cleanup to an uncertain CLI input buffer");
    damaged=starting();damaged.block("upload interrupted");
    ok(damaged.beginGameStop(1500)==null,"no exit bytes injected into a pending script upload");
    try{FilesGameScript.source(template+" ",NONCE,"/Games/Dino.js");throw new AssertionError("altered bridge accepted");}catch(IllegalArgumentException expected){checks++;}
    for(String nonce:new String[]{"x\nreboot","FFFFFFFFFFFFFFFF","0123456789abcde"})try{FilesGameScript.source(template,nonce,"/Games/Dino.js");throw new AssertionError("bad nonce");}catch(IllegalArgumentException expected){checks++;}
    String transcript="# Entering interpreter...\n"+PREFIX+"SEARCH\n"+PREFIX+"ON\n"+wire(frame(0,initial(),2));
    for(int split=0;split<=transcript.length();split++){
      UsbSerialSession s=starting();s.receive(bytes(transcript.substring(0,split)),1100);s.receive(bytes(transcript.substring(split)),1101);
      ok(s.verified&&s.game.active&&s.game.screen.frames==1&&!s.externalInterpreter,"every fragmented launch/frame boundary accepted");
    }
    for(int split=1;split<wire(frame(1,UsbMirrorRegressionTest.CLEAR,1)).length();split++){
      UsbSerialSession s=running();String delta=wire(frame(1,UsbMirrorRegressionTest.CLEAR,1));s.receive(bytes(delta.substring(0,split)),1300);
      byte[] nav=s.begin("nav select",1301);ok(nav!=null,"navigation allowed while game frame fragmented");s.written(nav.length,nav.length,1302);
      s.receive(bytes(delta.substring(split)+"# "),1303);ok(s.verified&&s.game.screen.frames==2&&s.stage==UsbSerialSession.Stage.OPEN,"partial text frame preserved across navigation write");
    }
    UsbSerialSession s=running();ok(s.beginMenuQuery(1400)==null&&s.beginThemeSync(NONCE,1400)==null&&s.beginMirror(true,NONCE,1400)==null,"no interpreter replacement or root polling over owned game");
    ok(s.begin("reboot",1400)==null&&s.begin("info",1400)==null,"game session accepts only navigation");
    byte[] stop=s.beginGameStop(1400);ok(Arrays.equals(stop,bytes("js exit\n")),"bounded cleanup uses exact stock exit command");s.written(stop.length,stop.length,1401);s.receive(bytes("# "),1402);ok(s.game.engaged(),"CLI prompt alone cannot confirm game exit");s.receive(bytes(PREFIX+"STOP\nExiting interpreter...\n"),1403);ok(s.game.ended()&&!s.game.engaged()&&s.stage==UsbSerialSession.Stage.OPEN,"own terminal and interpreter exit release ownership");ok(s.beginGameStop(1700)==null,"no repeated exit after completion");
    s=running();s.receive(bytes(PREFIX+"ERROR\nExiting interpreter...\n"),1400);ok(s.game.ended()&&s.game.outcome.contains("error"),"runtime failure cannot be described as normal exit");
    s=running();s.tick(4100);ok(s.stage==UsbSerialSession.Stage.BLOCKED&&s.begin("nav select",4200)==null,"lost heartbeats block input, no replay");
    s=starting();s.receive(bytes(PREFIX+"ON\n# "),1100);ok(s.stage==UsbSerialSession.Stage.BLOCKED,"ON without SEARCH rejected");
    s=UsbMirrorRegressionTest.verified();byte[] h=s.beginFilesGame(template,NONCE,"/Games/Dino.js",1000);s.receive(bytes(PREFIX+"SEARCH\n"),1001);ok(s.stage==UsbSerialSession.Stage.BLOCKED,"premature game markers rejected");
    s=running();s.receive(bytes(PREFIX+"ON\n"),1300);ok(s.stage==UsbSerialSession.Stage.BLOCKED,"duplicate start marker rejected");
    s=starting();s.receive(bytes("# Entering interpreter...\n"+PREFIX+"SEARCH\n"+PREFIX+"NOT_FOUND\nExiting interpreter...\n"),1100);ok(s.game.ended()&&s.game.outcome.contains("not found")&&s.stage==UsbSerialSession.Stage.OPEN,"missing approved file exits without screen or navigation unlock");
    s=starting();s.receive(bytes("# SyntaxError: bad\n"),1100);ok(s.stage==UsbSerialSession.Stage.BLOCKED,"interpreter error stops upload");
    s=starting();s.receive(bytes("BT_ffffffffffffffff_GAME|ON\n# "),1100);s.tick(50000);ok(s.stage==UsbSerialSession.Stage.BLOCKED&&!s.game.active,"stale nonce never starts game, deadline closes it");
    s=UsbMirrorRegressionTest.verified();h=s.beginFilesGame(template,NONCE,"/Games/Dino.js",1000);s.written(h.length,h.length,1001);s.receive(bytes("Reading input data from serial buffer until EOF\nRebooting...\n"),1002);ok(s.stage==UsbSerialSession.Stage.BLOCKED&&s.takeScriptBody()==null,"panic after handshake cannot release a queued body");
    s=running();s.receive(bytes("Rebooting...\n"),1300);ok(s.stage==UsbSerialSession.Stage.BLOCKED&&!s.verified,"device reset immediately revokes game input");ok(s.beginGameStop(1400)==null,"observed reboot revokes ownership and cannot authorize emergency exit into the new boot");s.close();ok(!s.game.engaged()&&s.game.screen.frames==0,"disconnect clears game buffers");
  }
  static void completionAndAccounting(){
    GameScreenStream screen=new GameScreenStream();
    for(int i=0;i<239;i++) {
      byte[] data=i==0?initial():i<219?UsbMirrorRegressionTest.CLEAR:new byte[0];
      ok(feed(screen,frame(i,data,i==0?2:i<219?1:0),1000+i),"positive-report sized sequence accepted");
      if(i<219)ok(screen.takeFrame()!=null,"nonempty frame handed to renderer once");
      else ok(screen.takeFrame()==null,"heartbeat does not invent another image");
    }
    ok(screen.frames==239&&screen.drawingFrames==219&&screen.heartbeatFrames==20,"received frames reconcile as drawings plus heartbeats");
    ok(screen.deliveredFrames==219&&screen.queuedFrames()==0&&screen.peakQueuedFrames()==1,"render backlog and handoff count are explicit");
    screen.reset();ok(screen.frames==0&&screen.drawingFrames==0&&screen.heartbeatFrames==0&&screen.deliveredFrames==0&&screen.peakQueuedFrames()==0,"new game resets all frame accounting");

    UsbSerialSession s=running();byte[] exit=s.beginGameStop(1400);
    ok(exit!=null&&!s.game.awaitingExit(),"queued but unwritten exit does not claim delivery");
    s.written(exit.length,exit.length,1401);s.receive(bytes("# "),1402);
    ok(s.game.awaitingExit()&&s.game.engaged(),"escape receipt and CLI prompt do not confirm interpreter exit");
    ok(s.begin("nav esc",1600)==null&&s.begin("nav select",1600)==null&&s.beginGameStop(1600)==null,"no duplicate exit or inputs while exit confirmation is outstanding");
    s.tick(7900);ok(s.stage==UsbSerialSession.Stage.OPEN,"exit grace interval permits final markers after drawing stops");
    s.tick(7901);ok(s.stage==UsbSerialSession.Stage.BLOCKED&&s.failure.contains("exit was not confirmed"),"missing interpreter exit has a bounded failure without replay");
    s=running();s.receive(bytes(PREFIX+"DONE\n"),1400);ok(s.game.awaitingExit()&&!s.game.ended(),"terminal marker alone cannot release interpreter ownership");
    s.tick(7900);ok(s.stage==UsbSerialSession.Stage.BLOCKED,"missing interpreter exit also bounded after spontaneous completion");
    s=running();s.receive(bytes(PREFIX+"DONE\n"),1400);s.receive(bytes("Exiting interpreter...\n"),1500);s.tick(9000);
    ok(s.game.ended()&&!s.game.awaitingExit()&&s.verified,"confirmed completion cancels the exit deadline");
    for(int round=0;round<25;round++) {
      long at=10000+round*2000;String nonce=String.format(Locale.ROOT,"%016x",round+100);String prefix="BT_"+nonce+"_GAME|";
      byte[] h=s.beginFilesGame(template,nonce,"/Games/Dino.js",at);ok(h!=null,"next game starts on the same verified USB session");
      s.written(h.length,h.length,at+1);s.receive(bytes("Reading input data from serial buffer until EOF\n"),at+2);
      byte[] body=s.takeScriptBody();s.written(body.length,body.length,at+3);
      s.receive(bytes("# Entering interpreter...\n"+prefix+"SEARCH\n"+prefix+"ON\n"),at+4);
      ok(s.game.active&&!s.game.awaitingExit()&&s.game.screen.frames==0,"new nonce clears prior game exit and counters");
      s.receive(bytes(prefix+"DONE\nExiting interpreter...\n"),at+5);
      ok(s.game.ended()&&s.verified,"repeat completion preserves verified USB and releases ownership");
    }
  }
  static void controller()throws Exception{
    for(int scenario=0;scenario<3;scenario++)try(UsbRegressionTest.Fixture f=new UsbRegressionTest.Fixture()){
      f.open();f.verify();ok(f.controller.launchFilesGame(template,"/Games/Dino.js"),"actual controller queues fixed game header");
      UsbRegressionTest.until(()->f.port().outgoing.size()==2,"game header write");f.port().incoming.add(bytes("Reading input data from serial buffer until EOF\n"));
      // Paced production uploader needs about 2.5 seconds for this fixed bridge.
      long deadline=System.nanoTime()+8_000_000_000L;
      while(System.nanoTime()<deadline && !(f.port().outgoing.size()==3 && f.controller.session.stage==UsbSerialSession.Stage.WAITING)) {Handler.runCurrent();Thread.sleep(5);}
      ok(f.port().outgoing.size()==3 && f.controller.session.stage==UsbSerialSession.Stage.WAITING,"complete paced game body write: "+f.controller.session.status);
      String body=new String(f.port().outgoing.toArray(new byte[0][])[2],StandardCharsets.US_ASCII);
      java.util.regex.Matcher m=java.util.regex.Pattern.compile("BT_([a-f0-9]{16})_GAME").matcher(body);ok(m.find(),"fresh game nonce in fixed bridge");String prefix="BT_"+m.group(1)+"_GAME|";
      f.port().incoming.add(bytes("# Entering interpreter...\n"+prefix+"SEARCH\n"+prefix+"ON\n"));UsbRegressionTest.until(()->f.controller.session.game.active,"game started");Handler.advance(200);
      if(scenario>0){f.controller.session.game.screen.line("INVALID",2000);f.controller.session.block("Fixture renderer saturation");}
      f.controller.closeSafely("App left foreground");UsbRegressionTest.until(()->f.port().outgoing.size()==4,"one exit command");ok(new String(f.port().outgoing.toArray(new byte[0][])[3],StandardCharsets.US_ASCII).equals("js exit\n"),"background exits only owned game");
      if(scenario==2) {
        Handler.advance(16000);UsbRegressionTest.until(()->f.port().closed,"missing exit acknowledgement has bounded closure");
        ok(f.controller.interpreterRecoveryRequired(),"unconfirmed owned interpreter fences future uploads");
        f.ctx.manager.connection=new android.hardware.usb.UsbDeviceConnection();f.open();f.verify();
        ok(!f.controller.syncTheme()&&!f.controller.mirror(true)&&!f.controller.launchFilesGame(template,"/Games/Dino.js"),"new info reply cannot authorize a second script over unconfirmed ownership");
        ok(f.port().outgoing.size()==1,"reconnect sends only identity query while fenced");
        f.controller.confirmInterpreterRestart();ok(f.controller.interpreterRecoveryRequired(),"restart acknowledgement cannot alter an active connection");
        f.controller.disconnect("Restart confirmation fixture");f.controller.confirmInterpreterRestart();
        ok(!f.controller.interpreterRecoveryRequired(),"explicit disconnected restart acknowledgement clears fence");
        continue;
      }
      f.port().incoming.add(bytes("# "+prefix+"STOP\nExiting interpreter...\n"));UsbRegressionTest.until(()->f.port().closed,"game shutdown releases USB");ok(f.controller.diagnostics().contains("game exit acknowledged"),"game exit accurately reported");
      ok(!f.controller.interpreterRecoveryRequired(),"confirmed game exit leaves no upload fence");
      if(scenario==1) {
        f.ctx.manager.connection=new android.hardware.usb.UsbDeviceConnection();f.open();f.verify();
        ok(f.controller.diagnostics().contains("protocol failure=Fixture renderer saturation"),"new connection retains the original game's failure evidence");
        ok(!f.controller.session.game.attempted,"retained game evidence cannot become current-session state");
      }
    }
  }
  public static void main(String[] args)throws Exception{
    template=new String(Files.readAllBytes(Paths.get(System.getProperty("project.root"),"app/src/main/res/raw/files_bridge.txt")),StandardCharsets.US_ASCII);
    ok(MenuRefreshPolicy.delay(1000,0)==1200,"idle menu reduces polling");
    ok(MenuRefreshPolicy.delay(1500,1000)==350&&MenuRefreshPolicy.delay(2999,1000)==350,"recent input stays responsive");
    ok(MenuRefreshPolicy.delay(3000,1000)==1200&&MenuRefreshPolicy.delay(500,1000)==1200,"idle or reset clock uses bounded slow poll");
    ok(MenuRefreshPolicy.nextWake(1000,2200)==1200&&MenuRefreshPolicy.nextWake(1000,8000)==1200,"quiet menu does not wake 25 times each second");
    ok(MenuRefreshPolicy.nextWake(1000,0)==16&&MenuRefreshPolicy.nextWake(1000,1010)==16,"pending input retains a bounded short wake without busy spinning");
    ok(MenuRefreshPolicy.nextWake(1000,1350)==350,"recent menu wake follows its actual query deadline");
    Files.createDirectories(Paths.get(System.getProperty("project.root"),"build/game-proof"));
    Files.write(Paths.get(System.getProperty("project.root"),"build/game-proof/approved-script.js"),bytes(FilesGameScript.source(template,NONCE,"/Games/Dino.js")));
    receiver();sessions();completionAndAccounting();controller();System.out.println("PASS "+checks+" game framing, bounded resources, approval, fragmentation, ownership, timeout and lifecycle checks (USB doubles).");
  }
}
