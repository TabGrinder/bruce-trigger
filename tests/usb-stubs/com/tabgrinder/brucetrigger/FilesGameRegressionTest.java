package com.tabgrinder.brucetrigger;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.Adler32;

public final class FilesGameRegressionTest {
  static int checks;
  static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
  static UsbSerialSession running(String template) {
    UsbSerialSession s=UsbMirrorRegressionTest.verified();
    byte[] h=s.beginFilesGame(template,"0123456789abcdef","/Games/one.js",1000);
    s.written(h.length,h.length,1001);
    s.receive(UsbRegressionTest.bytes("Reading input data from serial buffer until EOF\n"),1002);
    byte[] b=s.takeScriptBody();s.written(b.length,b.length,1003);
    s.receive(UsbRegressionTest.bytes("# Entering interpreter...\nBT_0123456789abcdef_GAME|SEARCH\nBT_0123456789abcdef_GAME|ON\n"),1004);
    return s;
  }
  static void performanceAccounting(String template) {
    UsbSerialSession s=running(template);
    byte[] nav=s.begin("nav select",2000);
    ok("None".equals(s.game.lastInput),"queued input is not reported as delivered");
    s.written(nav.length,nav.length,2001);s.receive(UsbRegressionTest.bytes("# "),2002);
    ok("nav select".equals(s.game.lastInput),"complete host write records last game input sent");
    ok(s.begin("nav next",2021)==null,"game input keeps a bounded 20 ms cooldown");
    nav=s.begin("nav next",2022);ok(nav!=null,"game input resumes after 20 ms instead of 120 ms");
    s.written(nav.length,nav.length,2023);s.receive(UsbRegressionTest.bytes("# "),2024);
    s.receive(UsbRegressionTest.bytes("BT_0123456789abcdef_GAME|UI_ON\n"),2100);
    nav=s.begin("nav select",2200);s.written(nav.length,nav.length,2201);s.receive(UsbRegressionTest.bytes("# "),2202);
    ok(s.begin("nav next",2321)==null&&s.begin("nav next",2322)!=null,"native script dialog retains 120 ms cooldown");
    s=UsbMirrorRegressionTest.verified();nav=s.begin("nav next",2000);s.written(nav.length,nav.length,2001);s.receive(UsbRegressionTest.bytes("# "),2002);
    ok(s.begin("nav next",2121)==null&&s.begin("nav next",2122)!=null,"ordinary menus retain 120 ms cooldown");
    s=running(template);nav=s.begin("nav next",2000);s.written(nav.length-1,nav.length,2001);
    ok("None".equals(s.game.lastInput)&&s.stage==UsbSerialSession.Stage.BLOCKED,"short write cannot claim complete input delivery");
    s=running(template);s.receive(UsbRegressionTest.bytes("BT_0123456789abcdef_GAME|DONE\nExiting interpreter...\n"),2000);
    ok("DONE".equals(s.game.termination),"interpreter exit retains explicit bridge completion marker");
    s=running(template);s.receive(UsbRegressionTest.bytes("Exiting interpreter...\n"),2000);
    ok(s.game.termination.contains("without final bridge marker"),"exit without marker stays distinguishable, with no invented cause");
    s.game.reset();ok("None".equals(s.game.lastInput)&&"None".equals(s.game.termination),"new game clears old input and completion evidence");
    GameScreenStream screen=new GameScreenStream();
    GameRegressionTest.feed(screen,GameRegressionTest.frame(0,GameRegressionTest.initial(),2),0);screen.takeFrame();
    GameRegressionTest.feed(screen,GameRegressionTest.frame(1,new byte[0],0),40);
    GameRegressionTest.feed(screen,GameRegressionTest.frame(2,UsbMirrorRegressionTest.CLEAR,1),100);screen.takeFrame();
    GameRegressionTest.feed(screen,GameRegressionTest.frame(3,UsbMirrorRegressionTest.CLEAR,1),200);screen.takeFrame();
    ok(screen.throughput().contains("batches/s=10.0")&&screen.throughput().contains("gap=100 ms"),"rate uses drawing batches only, including first timestamp zero");
    screen.reset();ok(screen.throughput().contains("unavailable")&&screen.throughput().contains("gap=0 ms"),"new game resets timing metrics");
  }
  static byte[] text(String s,int op,int x,int y,int scale,int fg)throws Exception {
    byte[] raw=s.getBytes("UTF-8"),b=new byte[13+raw.length];b[0]=(byte)170;b[1]=(byte)b.length;b[2]=(byte)op;
    int[] w={x,y,scale,fg,0};for(int i=0;i<w.length;i++){b[3+2*i]=(byte)(w[i]>>8);b[4+2*i]=(byte)w[i];}System.arraycopy(raw,0,b,13,raw.length);return b;
  }
  public static void main(String[] args)throws Exception {
    FilesScriptSelection selection=new FilesScriptSelection();
    selection.frame(text("JS Script Run\n",17,30,40,2,65535));ok(selection.menuVisible&&!selection.observed,"native Files label works without debug Serial path");
    selection.frame(text("Path: /stale.js\n",17,10,50,1,65535));ok(selection.infoPath==null,"unrequested File Info cannot select a file");
    selection.beginInfo();selection.frame(text("FILE INFO\n",17,10,30,1,65535));
    String wrapped="/Games/"+String.join("",Collections.nCopies(40,"x"))+" with spaces.js";
    String row="Path: "+wrapped;
    selection.frame(text(row.substring(0,50)+"\n",17,10,50,1,65535));
    selection.frame(text(row.substring(50)+"\n",17,10,58,1,65535));selection.frame(text("\n",17,10,66,1,65535));
    ok(!selection.infoComplete,"path requires the following Bytes record");
    selection.frame(text("Bytes: 1245\n",17,10,74,1,65535));
    ok(selection.infoComplete&&wrapped.equals(selection.infoPath),"stock 50-column wrapped File Info path preserves spaces");
    selection.clear();ok(!selection.menuVisible&&!selection.infoComplete,"navigation clears stale File Info context");
    selection.frame(text("Megalodon.js\n",17,10,40,1,65535));ok(!selection.nativeGameMenuVisible,"a file basename is not the native game menu");
    selection.frame(text(">Megalodon\n",17,10,40,1,65535));ok(selection.nativeGameMenuVisible,"exact native menu label can request a capability check");
    selection.frame(UsbMirrorRegressionTest.CLEAR);ok(!selection.nativeGameMenuVisible,"screen clear drops the native game menu context");
    selection.observe("/Games/one.js --> one.js");ok("/Games/one.js".equals(selection.path),"stock Files path observed");
    selection.observe("/Games/two.txt --> two.txt");ok(selection.path==null,"non-script selection clears stale path");
    for(String path:new String[]{"../a.js","/../a.js","/./a.js","/a//b.js","/a'bad.js","/a\"bad.js","/a\nb.js","/a\\b.js","/a|b.js","/a.js/"})
      ok(!FilesScriptSelection.valid(path),"path injection/traversal rejected: "+path);
    selection.observe("/a.js --> b.js");ok(selection.path==null,"basename mismatch rejected");
    String template=new String(Files.readAllBytes(Paths.get(System.getProperty("project.root"),"app/src/main/res/raw/files_bridge.txt")),StandardCharsets.US_ASCII);
    performanceAccounting(template);
    for(String literal:new String[]{"/Games/@@CAPTURE@@.js","/@@NONCE@@/@@PATH@@.bjs"}) {
      for(boolean capture:new boolean[]{false,true}) {
        String selected=FilesGameScript.source(template,"0123456789abcdef",literal,capture);
        ok(selected.contains("file='"+literal+"'"),"selected placeholder-like filename remains literal");
        ok(selected.contains("capture="+capture+";"),"capture setting is independent of literal filename");
      }
    }
    String path="/"+String.join("",Collections.nCopies(175,"x"))+".js";
    String source=FilesGameScript.source(template,"0123456789abcdef",path);
    ok(source.length()+4<=PacedUsbWriter.MAX_WRITE_BYTES,"longest selected path fits bounded uploader");
    for(String line:source.split("\n"))ok(line.length()<=240&&!line.equals("EOF"),"fixed script line bounded");
    try{FilesGameScript.source(template+" ","0123456789abcdef","/a.js");throw new AssertionError("changed template allowed");}catch(IllegalArgumentException expected){checks++;}
    UsbSerialSession s=UsbMirrorRegressionTest.verified();
    byte[] header=s.beginFilesGame(template,"0123456789abcdef","/Games/one.js",1000);ok(header!=null,"explicit Files launch accepted");
    s.written(header.length,header.length,1001);s.receive(UsbRegressionTest.bytes("Reading input data from serial buffer until EOF\n"),1002);
    byte[] body=s.takeScriptBody();ok(new String(body,StandardCharsets.US_ASCII).contains("file='/Games/one.js'"),"only the selected path enters fixed bridge");
    s.written(body.length,body.length,1003);s.receive(UsbRegressionTest.bytes("# Entering interpreter...\nBT_0123456789abcdef_GAME|SEARCH\nBT_0123456789abcdef_GAME|ON\n"),1004);
    ok(s.stage==UsbSerialSession.Stage.OPEN,"Files receiver owns interpreter after verified start");
    ok(s.game.summary().contains("file=one.js; framing=recoverable (8+2)")&&!s.game.summary().contains("/Games/"),"game report identifies selected basename and exact wire format without the folder path");
    s.receive(UsbRegressionTest.bytes("BT_0123456789abcdef_GAME|UI_ON\n"),1005);
    ok(s.game.nativeUi,"owned native dialog marker accepted");
    s.receive(new byte[]{(byte)170,8,99,1,64,0,(byte)170,1,(byte)170,5,0,0,0},1006);
    ok(s.stage==UsbSerialSession.Stage.OPEN,"dialog native draw packets accepted only during owned UI");
    s.tick(5000);ok(s.stage==UsbSerialSession.Stage.OPEN,"blocking native dialog does not require JS heartbeats");
    s.receive(UsbRegressionTest.bytes("BT_0123456789abcdef_GAME|UI_OFF\n"),5001);
    ok(!s.game.nativeUi&&s.game.takeNativeBoundary()==0,"UI exit restores game watchdog and exposes ordered drawing fence");
    byte[] exit=s.begin("nav esc",5200);s.written(exit.length,exit.length,5201);s.receive(UsbRegressionTest.bytes("# "),5202);
    ok(!s.game.awaitingExit(),"generic game back may open a pause/menu; never assumes termination");
    s.receive(UsbRegressionTest.bytes("BT_0123456789abcdef_GAME|REASON|drawJpg\nBT_0123456789abcdef_GAME|UNSUPPORTED\n"),5300);
    ok(s.game.awaitingExit()&&!s.game.ended(),"unsupported operation waits for interpreter exit");
    s.receive(UsbRegressionTest.bytes("Exiting interpreter...\n"),5301);ok(s.game.ended()&&s.game.outcome.contains("drawJpg"),"reason retained and exit confirmed");
    byte[] create={(byte)170,11,90,0,1,1,64,0,(byte)170,0,16};
    ok(GameDrawValidator.valid(create),"bounded 320x170 RGB565 sprite accepted");
    create[10]=8;ok(!GameDrawValidator.valid(create),"unsupported sprite depth rejected before allocation");
    Random random=new Random(15);
    for(int i=0;i<10000;i++){byte[] p=new byte[random.nextInt(255)];random.nextBytes(p);try{GameDrawValidator.valid(p);}catch(RuntimeException e){throw new AssertionError("draw validation crashed",e);}checks++;}
    if(args.length==1)Files.write(Paths.get(args[0]),FilesGameScript.source(template,"0123456789abcdef","/scripts/Games/flappybird.js").getBytes(StandardCharsets.US_ASCII));
    System.out.println("PASS "+checks+" Files path, fixed template, generic exit, explicit unsupported and malformed graphics checks.");
  }
}
