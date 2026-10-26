package com.tabgrinder.brucetrigger;
import java.util.*;
/** Literal stock menu query, independent of Android JSON/Canvas (covered in native audit). */
public final class MenuSessionRegressionTest {
 static int checks;static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static byte[] b(String s){return UsbRegressionTest.bytes(s);}
 public static void main(String[] args)throws Exception {
  String json="{\"width\":320,\"height\":170,\"menu\":\"main_menu\",\"menu_title\":\"\",\"options\":[{\"n\":0,\"label\":\"WiFi\"}],\"active\":0}";
  for(int cut=0;cut<=json.length()+3;cut++) {
   UsbSerialSession s=UsbMirrorRegressionTest.verified();byte[] q=s.beginMenuQuery(1000);
   ok(Arrays.equals(q,b("optionsJSON\n")),"exact read-only command");
   ok(s.begin("nav next",1001)==null,"no overlapping input");s.written(q.length,q.length,1002);
   byte[] response=b(json+"\n# ");s.receive(Arrays.copyOfRange(response,0,cut),1003);s.receive(Arrays.copyOfRange(response,cut,response.length),1004);
   ok(s.menuReplies==1&&json.equals(s.menuJson)&&s.stage==UsbSerialSession.Stage.OPEN,"all fragmented response boundaries");
   s.close();ok(s.menuJson==null&&!s.verified,"close clears menu data");s.open(2000);ok(s.menuReplies==0,"new session clears reply count");
  }
  UsbSerialSession s=new UsbSerialSession();s.open(0);ok(s.beginMenuQuery(1000)==null,"unverified device cannot query menu");
  s=UsbMirrorRegressionTest.verified();s.mirror.active=true;byte[] mixed=s.beginMenuQuery(1000);ok(mixed!=null,"root verification may share the native stream");s.written(mixed.length,mixed.length,1001);s.receive(UsbMirrorRegressionTest.CLEAR,1002);s.receive(b(json+"\n# "),1003);ok(s.menuReplies==1&&s.mirror.active&&s.stage==UsbSerialSession.Stage.OPEN,"draw records and root JSON keep independent command semantics");s=UsbMirrorRegressionTest.verified();
  s.receive(b("Entering interpreter...\n"),1000);ok(s.externalInterpreter&&s.beginMenuQuery(2000)==null,"no query while stock script observed");
  s.receive(b("Exiting interpreter...\n"),2001);byte[] q=s.beginMenuQuery(3000);ok(q!=null,"read resumes after interpreter exit");s.written(q.length,q.length,3001);
  s.receive(b("# "),3002);ok(s.menuJson==null&&s.menuReplies==1,"prompt without JSON never creates a menu");
  s.receive(b(json+"\n"),3003);ok(s.menuJson==null,"unsolicited JSON cannot become menu state");
  q=s.beginMenuQuery(4000);s.written(q.length,q.length,4001);s.tick(11000);ok(s.stage==UsbSerialSession.Stage.BLOCKED&&s.beginMenuQuery(12000)==null,"timeout requires explicit reconnection, no replay");
  ThemeTransfer t=new ThemeTransfer();String p="BT_0123456789abcdef_IMG|";t.begin("0123456789abcdef");
  t.line(p+"M|wifi|0|/Theme/1.png");t.line(p+"M|config|2|/Theme/14.png");t.line(p+"C|96fe|0000");
  ok(t.mappingSnapshot().get("wifi").equals("0:/Theme/1.png")&&t.mappingSnapshot().get("config").equals("2:/Theme/14.png"),"exact theme identity maps SD and LittleFS paths");
  ok(t.primary==0x96fe&&t.background==0,"RGB565 theme colors parsed");
  t.line(p+"M|wifi|0|/Theme/other.png");ok(t.mappingSnapshot().get("wifi").equals("0:/Theme/1.png"),"duplicate mapping cannot overwrite original");
  for(String bad:new String[]{"M|unknown|0|/Theme/2.png","M|rf|1|/Theme/2.png","M|rf|0|/Theme/../2.png","M|rf|0|/bruce.conf","M|rf|0|/Theme/evil.js"})t.line(p+bad);
  ok(t.mappingSnapshot().size()==2,"invalid names, filesystem, traversal and non-image mappings rejected");t.line(p+"C|ffffffff|foo");ok(t.primary==0x96fe&&t.background==0,"invalid colors do not mutate theme");
  t.line(p+"Z");t.line(p+"M|ble|0|/Theme/2.png");ok(t.mappingSnapshot().size()==2,"completed transfer rejects late map");t.reset();ok(t.mappingSnapshot().isEmpty()&&t.primary==0xf81f,"disconnect resets mappings and colors");
  try(UsbRegressionTest.Fixture f=new UsbRegressionTest.Fixture()){
   f.open();f.verify();ok(f.controller.queryMenu(),"actual controller queues menu query");UsbRegressionTest.until(()->f.port().outgoing.size()==2,"menu write");f.port().incoming.add(b(json+"\n# "));UsbRegressionTest.until(()->f.controller.session.menuReplies==1,"menu response");ok(json.equals(f.controller.session.menuJson),"controller delivers JSON through production session");
  }
  // Metadata is read-only: no second 120-ms navigation penalty after its reply.
  s=UsbMirrorRegressionTest.verified();q=s.begin("nav next",1000);s.written(q.length,q.length,1001);s.receive(b("# "),1010);
  ok(s.beginMenuQuery(1049)==null,"root query does not race the immediate navigation response");
  q=s.beginMenuQuery(1050);ok(q!=null,"root query permitted 40 ms after acknowledged navigation");s.written(q.length,q.length,1051);s.receive(b(json+"\n# "),1060);
  ok(s.begin("nav next",1129)==null,"navigation still preserves its original 120-ms spacing");
  ok(s.begin("nav next",1130)!=null,"read-only metadata no longer adds another navigation cooldown");
  s=UsbMirrorRegressionTest.verified();s.mirror.active=true;
  ok(s.beginRootSelection(0,json,1000)==null,"no root selection without a just-completed verification");
  q=s.beginMenuQuery(1000);s.written(q.length,q.length,1001);s.receive(b(json+"\n# "),1002);
  ok(s.beginRootSelection(-1,json,1200)==null&&s.beginRootSelection(40,json,1200)==null,"root selection index bounded");
  ok(s.beginRootSelection(0,json+" ",1200)==null,"root selection bound to exact verified reply");
  s.externalInterpreter=true;ok(s.beginRootSelection(0,json,1200)==null,"game/script cannot receive forced root selection");s.externalInterpreter=false;
  q=s.beginRootSelection(0,json,1200);ok(Arrays.equals(q,b("option 0\n")),"stock option is an exact bounded integer command");
  ok(s.beginRootSelection(0,json,1201)==null,"no overlapping or repeated forced selection");s.written(q.length,q.length,1202);s.receive(b("# "),1203);
  ok(s.beginRootSelection(0,json,1400)==null,"old root verification cannot authorize another selection");
  ok(CommandPolicy.encode("option 0")==null,"generic command entry cannot bypass root verification");
  t=new ThemeTransfer();t.begin("0123456789abcdef");t.line(p+"M|wifi|0|/Theme/1.png");t.line(p+"B|0|/Theme/1.png|4");t.line(p+"D|2|ffff");
  for(int i=0;i<115;i++)t.line(p+"D|2|ffff");t.line(p+"Z");
  ok(t.count()==0&&t.missing()==1&&t.summary().contains("rejected=116")&&t.summary().contains("first rejection=Image offset/length mismatch at byte 0"),"116 rejected records identify one missing image and retain original cause");
  ok(t.summary().contains("0:/Theme/1.png"),"first rejection retains bounded image identity");
  System.out.println("PASS "+checks+" menu protocol, fragmented replies, interpreter guard, timeout/no-replay and bounded theme-mapping checks (host USB doubles).");
 }
}
