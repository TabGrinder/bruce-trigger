package com.tabgrinder.brucetrigger;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
public final class MegalodonRegressionTest {
 static int checks;
 static void ok(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
 public static void main(String[] args)throws Exception {
  Path root=Paths.get(System.getProperty("project.root"));
  String template=new String(Files.readAllBytes(root.resolve("app/src/main/res/raw/megalodon.txt")),StandardCharsets.US_ASCII);
  String nonce="0123456789abcdef",tag="BT_"+nonce+"_GAME|";
  for(boolean capture:new boolean[]{true,false}){
   String code=MegalodonScript.source(template,nonce,capture);
   ok(!code.contains("@@")&&code.length()+4<=PacedUsbWriter.MAX_WRITE_BYTES,"bounded complete pinned adapter");
   ok(!code.contains("storage")&&!code.contains("st.write")&&!code.contains("nav select"),"RAM adapter cannot modify stored games or select the native compiled game");
   Path out=root.resolve("build/megalodon");Files.createDirectories(out);Files.write(out.resolve(capture?"capture.js":"direct.js"),code.getBytes(StandardCharsets.US_ASCII));
   String records="# Entering interpreter...\n"+(capture?tag+"CAPTURE_OFF\n"+tag+"CAPTURE_OFF\n"+tag+"CAPTURE_OFF\n":"")+tag+"SEARCH\n"+tag+"ON\n";
   for(int cut=0;cut<=records.length();cut++){
    UsbSerialSession s=UsbMirrorRegressionTest.verified();s.mirror.active=capture;
    byte[] h=s.beginMegalodon(template,nonce,1000);ok(h!=null,"only verified compatible profile starts Megalodon");
    s.written(h.length,h.length,1001);s.receive("Reading input data from serial buffer until EOF\n".getBytes(StandardCharsets.US_ASCII),1002);
    byte[] body=s.takeScriptBody();ok(body!=null,"requires stock upload handshake");s.written(body.length,body.length,1003);
    s.receive(records.substring(0,cut).getBytes(StandardCharsets.US_ASCII),1100);s.receive(records.substring(cut).getBytes(StandardCharsets.US_ASCII),1101);
    ok(s.game.active&&s.verified&&!s.mirror.active&&s.game.filePath.equals("Megalodon (RAM)"),"handover safe at every USB split and explicit adaptation identity");
    ok(s.beginMegalodon(template,nonce,2000)==null&&s.beginFilesGame(template,nonce,"/game.js",2000)==null,"cannot overlap interpreter owners");
    s.receive((tag+"DONE\nExiting interpreter...\n").getBytes(StandardCharsets.US_ASCII),2100);
    ok(s.game.ended()&&s.beginMirror(true,nonce,2600)!=null,"confirmed completion permits native capture return");
   }
  }
  for(String bad:new String[]{template+" ",template.replace("fish", "FISH"),""}){
   boolean rejected=false;try{MegalodonScript.source(bad,nonce,true);}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"tampered resource rejected before upload");
  }
  UsbSerialSession s=UsbMirrorRegressionTest.verified();s.mirror.probeMirrorReady=false;ok(s.beginMegalodon(template,nonce,1000)==null,"firmware without capability proof cannot execute adapter");
  s=UsbMirrorRegressionTest.verified();s.version="1.17";ok(s.beginMegalodon(template,nonce,1000)!=null,"newer firmware with compatible proof can execute adapter");
  s=UsbMirrorRegressionTest.verified();s.externalInterpreter=true;ok(s.beginMegalodon(template,nonce,1000)==null,"external interpreter cannot be replaced");
  GameInputQueue q=new GameInputQueue();
  for(int i=0;i<GameInputQueue.CAPACITY;i++)ok(q.offer("nav next",false,"owned",i),"fill fresh movement queue");
  ok(q.offer("nav esc",false,"owned",10)&&q.size()==1&&"nav esc".equals(q.peek("owned",11)),"EXIT cancels not-yet-written movement even when queue was full");
  ok(q.cancelled==GameInputQueue.CAPACITY&&q.submitted==0,"cancellation cannot count as delivery or replay USB");
  System.out.println("PASS "+checks+" Megalodon lifecycle, splits, source integrity and profile assertions; USB doubled.");
 }
}
