package com.tabgrinder.brucetrigger;
import android.os.Handler;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Adler32;

public final class ThemeRegressionTest {
  static int n;
  static String nonce="0123456789abcdef", prefix="BT_"+nonce+"_IMG|";
  static void ok(boolean value,String message){n++;if(!value)throw new AssertionError(message);}
  static byte[] b(String s){return UsbRegressionTest.bytes(s);}
  static void transfer(ThemeTransfer t,byte[] bytes,String path) {
    t.line(prefix+"B|0|"+path+"|"+bytes.length);
    for(int i=0;i<bytes.length;i+=128) {
      StringBuilder hex=new StringBuilder();for(int j=i;j<Math.min(i+128,bytes.length);j++)hex.append(String.format("%02x",bytes[j]&255));
      t.line(prefix+"D|"+i+"|"+hex);
    }
    Adler32 a=new Adler32();a.update(bytes);t.line(prefix+"E|"+a.getValue());
  }
  static void parser() {
    byte[] image=new byte[4097];new Random(42).nextBytes(image);
    ThemeTransfer t=new ThemeTransfer();t.begin(nonce);transfer(t,image,"/Themes/test/wifi.png");
    ok(Arrays.equals(image,t.snapshot().get("0:/Themes/test/wifi.png")),"exact binary content survives chunked hex + Adler32");
    ok(!t.line("BT_ffffffffffffffff_IMG|Z")&&!t.finished(),"stale nonce rejected");
    t.line(prefix+"Z");ok(t.finished()&&t.count()==1,"terminator preserves complete asset");
    t.line(prefix+"B|0|/Themes/extra.png|1");ok(t.count()==1,"late asset cannot mutate completed sync");
    for(String path:new String[]{"/Themes/../secret.png","/Themes/a/./x.png","/Themes//x.png","/Themes/x\\x.png","/Themes/x|z.png","/Themes/x\nx.png","/bruce.conf","http://site/x.png","x.png","/Themes/x.js"})
      ok(!ThemeTransfer.validPath(path),"unsafe/non-image key rejected: "+path);
    for(String malformed:new String[]{"B|0|/Themes/x.png|2147483647","B|1|/Themes/x.png|1","D|0|ff","E|1","X","B|0|/Themes/x.png|-1"}) {
      t.begin(nonce);t.line(prefix+malformed);ok(t.count()==0,"malformed transfer cannot create asset");
    }
    t.begin(nonce);t.line(prefix+"B|0|/Themes/x.png|2");t.line(prefix+"D|1|ffff");t.line(prefix+"E|393729");ok(t.count()==0,"out-of-order bytes rejected");
    t.begin(nonce);t.line(prefix+"B|0|/Themes/x.png|1");t.line(prefix+"D|0|ff");t.line(prefix+"E|1");ok(t.count()==0,"checksum mismatch rejected");
    t.begin(nonce);t.line(prefix+"B|0|/Themes/x.png|1");t.line(prefix+"D|0|fg");ok(t.count()==0,"invalid hex rejected");
    t.begin(nonce);byte[] max=new byte[ThemeTransfer.MAX_FILE];
    for(int i=0;i<8;i++)transfer(t,max,"/Themes/"+i+".bmp");
    ok(t.count()==8,"one MiB assets admitted exactly");transfer(t,new byte[]{1},"/Themes/overflow.png");ok(t.count()==8,"one MiB cap cannot overflow");
    t.begin(nonce);for(int i=0;i<ThemeTransfer.MAX_FILES;i++)transfer(t,new byte[]{1},"/Themes/"+i+".png");
    transfer(t,new byte[]{2},"/Themes/overflow.png");ok(t.count()==18,"asset-count cap cannot overflow");
    t.reset();ok(t.count()==0&&!t.finished(),"connection reset clears image bytes");
    Random rand=new Random(37);
    for(int i=0;i<10000;i++){t.begin(nonce);StringBuilder bad=new StringBuilder(prefix);for(int j=0;j<rand.nextInt(600);j++)bad.append((char)rand.nextInt(128));t.line(bad.toString());ok(t.count()==0,"malformed theme fuzz "+i);}
  }
  static void observedRequest() {
    String good="0:/Theme/CYBERPUNK2077 by -eni9ma/14.png";
    ok(ObservedThemeScript.validKey(good),"live numeric Cyberpunk filename accepted");
    ok(ObservedThemeScript.folder(good).equals("0:/Theme/CYBERPUNK2077 by -eni9ma/"),"exact filesystem and folder scope");
    for(String bad:new String[]{"1:/Theme/a.png","0:/Theme/../a.png","0:/bruce.conf","0:/a.png\nreboot","0:/a|b.png","http://example/a.png"}){
      ok(!ObservedThemeScript.validKey(bad),"untrusted image key rejected");
      try {ObservedThemeScript.source(nonce,bad);throw new AssertionError("unsafe script generated");}catch(IllegalArgumentException expected){n++;}
    }
    UsbSerialSession s=UsbMirrorRegressionTest.verified();s.externalInterpreter=true;
    ok(s.beginThemeSync(nonce,good,1000)==null,"theme recovery cannot start inside observed game");s.externalInterpreter=false;
    s.mirror.active=true;ok(s.beginThemeSync(nonce,good,1000)==null,"capture must stop before theme recovery");s.mirror.active=false;
    s.mirror.drainUntil=2000;ok(s.beginThemeSync(nonce,good,1000)==null,"theme recovery waits for final draw drain");
    ok(s.beginThemeSync(nonce,good,2500)!=null,"observed path recovery allowed after drain");
  }
  static void session() {
    UsbSerialSession s=UsbMirrorRegressionTest.verified();
    byte[] h=s.beginThemeSync(nonce,1000);ok(h!=null,"verified profile starts readonly sync");
    ok(s.begin("nav select",1001)==null,"navigation blocked during theme sync");
    s.written(h.length,h.length,1001);s.receive(b("Reading input data from serial buffer until EOF\n"),1002);
    byte[] body=s.takeScriptBody();ok(body!=null&&body.length<4096,"fixed readonly script bounded below 4K");
    String text=new String(body,java.nio.charset.StandardCharsets.US_ASCII);
    ok(!text.contains("display ")&&!text.contains("storage.write")&&!text.contains("serial.cmd"),"theme sync cannot activate capture/write flash or issue CLI commands");
    s.written(body.length,body.length,1003);s.tick(20000);ok(s.stage==UsbSerialSession.Stage.WAITING,"theme transfer gets bounded long deadline");
    s.receive(b(prefix+"Z\nBT_"+nonce+"_THEME_DONE\nExiting interpreter...\n# "),20001);
    ok(s.verified&&s.stage==UsbSerialSession.Stage.OPEN&&!s.mirror.active&&s.mirror.themes.finished(),"theme sync completion never starts capture itself");
    ok(s.beginMirror(true,nonce,21000)!=null,"separate capture starts only after confirmed theme sync");
    s=UsbMirrorRegressionTest.verified();h=s.beginThemeSync(nonce,1000);s.written(h.length,h.length,1001);s.receive(b("Reading input data from serial buffer until EOF\n"),1002);body=s.takeScriptBody();s.written(body.length,body.length,1003);
    s.receive(b("BT_"+nonce+"_THEME_DONE\nExiting interpreter...\n# "),2000);
    ok(s.stage==UsbSerialSession.Stage.BLOCKED,"missing theme terminator cannot report complete");
  }
  static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
  static void burst()throws Exception {
    try(UsbRegressionTest.Fixture f=new UsbRegressionTest.Fixture()) {
      f.open();f.verify();f.controller.mirror(true);UsbMirrorRegressionTest.finish(f,1,true);
      long before=f.controller.session.receivedBytes;
      int bytes=0;
      for(int i=0;i<1000;i++){byte[] packet=UsbMirrorRegressionTest.CLEAR;f.port().incoming.add(packet);bytes+=packet.length;}
      Thread.sleep(150); // Deliberately stall the test UI; the USB worker must wait, not close.
      Object run=field(f.controller,"current");
      ok(run!=null&&!f.port().closed,"1000 small redraw packets cannot force a disconnect while UI stalled");
      ok(((AtomicInteger)field(run,"pendingReads")).get()<=8,"queued reads stay bounded at eight");
      ok(((AtomicInteger)field(run,"readWaits")).get()>0,"backpressure observed while test UI stalled");
      final long expected=before+bytes;
      UsbRegressionTest.until(()->f.controller.session.receivedBytes==expected,"complete redraw burst");
      ok(f.controller.isReady()&&!f.port().closed,"all 1000 records delivered and connection remains ready");
      ok(((AtomicInteger)field(run,"peakReads")).get()<=8,"peak queue bounded over full burst");
      ok(f.controller.command("nav select"),"SELECT works after burst");
      UsbRegressionTest.until(()->f.port().outgoing.size()==4,"post-burst nav write");f.port().incoming.add(b("# "));
      UsbRegressionTest.until(()->f.controller.session.prompts==3,"post-burst nav response");Handler.advance(200);
      f.controller.mirror(false);UsbMirrorRegressionTest.finish(f,4,false);Handler.advance(1200);
    }
    try(UsbRegressionTest.Fixture f=new UsbRegressionTest.Fixture()) {
      f.open();f.verify();ok(f.controller.syncTheme(),"controller readonly sync start");
      UsbRegressionTest.until(()->f.port().outgoing.size()==2,"theme header");
      f.port().incoming.add(b("Reading input data from serial buffer until EOF\n"));UsbRegressionTest.until(()->f.port().outgoing.size()==3,"theme body");
      ok(!f.controller.hasCapture()&&f.controller.needsCaptureCleanup(),"theme read owns an interpreter even without native capture");
      f.controller.closeSafely("App left foreground");
      ok(!f.port().closed&&f.controller.isClosing(),"foreground exit waits for the owned theme interpreter");
      String body=new String(f.port().outgoing.toArray(new byte[0][])[2],java.nio.charset.StandardCharsets.US_ASCII);
      java.util.regex.Matcher m=java.util.regex.Pattern.compile("BT_([0-9a-f]{16})_IMG").matcher(body);ok(m.find(),"theme cleanup nonce comes from its actual upload");
      f.port().incoming.add(b("# BT_"+m.group(1)+"_IMG|Z\nBT_"+m.group(1)+"_THEME_DONE\nExiting interpreter...\n"));
      UsbRegressionTest.until(()->f.port().closed,"theme completion permits clean USB closure");
      ok(!f.controller.interpreterRecoveryRequired(),"confirmed theme completion requires no restart fence");
      ok(!f.controller.diagnostics().contains("Capture stop unconfirmed"),"cancelled theme read cannot falsely demand capture reset");
    }
    try(UsbRegressionTest.Fixture f=new UsbRegressionTest.Fixture()) {
      f.open();f.verify();ok(f.controller.syncTheme(),"unconfirmed theme fixture starts");
      UsbRegressionTest.until(()->f.port().outgoing.size()==2,"theme header reaches device");
      f.controller.closeSafely("App left foreground");Handler.advance(16000);
      UsbRegressionTest.until(()->f.port().closed,"unconfirmed theme cleanup is bounded");
      ok(f.controller.interpreterRecoveryRequired(),"unconfirmed theme interpreter is fenced even without a game");
      f.ctx.manager.connection=new android.hardware.usb.UsbDeviceConnection();f.open();f.verify();
      ok(!f.controller.syncTheme()&&f.port().outgoing.size()==1,"reconnect cannot overwrite unconfirmed theme script");
    }
  }
  static void retainedDiagnostics()throws Exception {
    try(UsbRegressionTest.Fixture f=new UsbRegressionTest.Fixture()){
      f.open();f.verify();ThemeTransfer t=f.controller.session.mirror.themes;t.begin(nonce);
      transfer(t,new byte[]{1,2,3},"/Theme/CYBERPUNK2077 by -eni9ma/14.png");t.line(prefix+"Z");
      f.ctx.manager.devices.clear();Handler.advance(300);
      UsbRegressionTest.until(()->f.port().closed,"detach close");
      ok(f.controller.diagnostics().contains("1 images; 3 bytes; skipped=0; rejected=0; transfer ended; missing=0; first rejection=None (last session)"),"physical-style detach retains sync evidence instead of reporting false zero");
      ok(f.controller.session.mirror.themes.count()==0,"diagnostic retention does not retain image bytes after disconnect");
      ok(f.port().outgoing.size()==1,"detach never replays info or input");
    }
  }
  public static void main(String[] a)throws Exception {parser();observedRequest();session();burst();retainedDiagnostics();System.out.println("PASS "+n+" theme transfer, one-MiB bounds, corruption, cancellation and 1000-packet USB burst checks (host doubles).");}
}
