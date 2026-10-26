package com.tabgrinder.brucetrigger;
import android.content.*;import android.hardware.usb.*;import android.os.*;import android.app.PendingIntent;
import java.nio.charset.StandardCharsets;import java.util.*;import java.util.function.BooleanSupplier;
public class UsbRegressionTest {
 static int checks;
 static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static byte[] bytes(String s){return s.getBytes(StandardCharsets.US_ASCII);}
 static String identity="info\r\nBruce v1.16.1\r\nSDK: 5.5.4\r\nDevice: LILYGO T-Embed CC1101\r\n# ";
 static UsbDevice device(){return new UsbDevice(new UsbInterface(0,2,2,new UsbEndpoint(128,3,16)),new UsbInterface(1,10,0,new UsbEndpoint(128,2,64),new UsbEndpoint(0,2,64)),new UsbInterface(2,255,255,new UsbEndpoint(128,2,64),new UsbEndpoint(0,2,64)));}
 static void until(BooleanSupplier b,String label)throws Exception {long end=System.nanoTime()+3_000_000_000L;while(System.nanoTime()<end){Handler.runCurrent();if(b.getAsBoolean())return;Thread.sleep(5);}throw new AssertionError("Timed out: "+label);}
 static class Listener implements TransportListener {String state,detail;int ready,received,sent;public void state(String s,String d){state=s;detail=d;}public void ready(String a,String n){ready++;}public void data(byte[] b){received+=b.length;}public void sent(String c){sent++;}public void loss(){}}
 static class Fixture implements AutoCloseable {
  Context ctx=new Context();UsbDevice d=device();Listener listener=new Listener();UsbController controller;
  Fixture(){this(new Listener());}
  Fixture(Listener l){listener=l;Handler.reset();ctx.manager.devices.put(d.name,d);controller=new UsbController(ctx,listener);}
  UsbDeviceConnection port(){return ctx.manager.connection;}
  void open()throws Exception{controller.connect(d);until(()->controller.isOpen(),"open");Handler.advance(600);}
  void verify()throws Exception {ok(controller.command("info"),"explicit info accepted");until(()->port().outgoing.size()==1,"one write");port().incoming.add(bytes(identity));until(()->controller.isReady(),"firmware verification");Handler.advance(200);}
  public void close()throws Exception {controller.destroy();until(()->ctx.manager.opens==0||port().closed,"worker releases connection");Handler.runCurrent();ok(ctx.receivers.isEmpty(),"all dynamic receivers removed");}
 }
 static void sessionTests(){
  byte[] id=bytes(identity);
  for(int split=0;split<=id.length;split++){
   UsbSerialSession s=new UsbSerialSession();s.open(0);
   ok(s.begin("nav next",600)==null,"unverified navigation blocked");
   ok(s.begin("info",600)!=null,"info allowed once quiet");s.written(5,5,601);
   s.receive(Arrays.copyOfRange(id,0,split),602);s.receive(Arrays.copyOfRange(id,split,id.length),603);
   ok(s.verified&&s.stage==UsbSerialSession.Stage.OPEN,"identity and prompt split at "+split);
   ok(!s.canControl(604)&&s.canControl(724),"response pacing");
   ok(s.begin("display start",800)==null&&s.begin("js run_from_buffer 1",800)==null,"no mirror/script path");
  }
  UsbSerialSession s=new UsbSerialSession();s.open(0);s.receive(bytes("# "),100);ok(!s.verified&&s.unsolicited==1,"prompt alone cannot verify device");
  ok(s.begin("info",600)!=null,"info start");s.receive(bytes(identity),601);ok(!s.verified&&s.stage==UsbSerialSession.Stage.WRITING,"pre-write data cannot ack");s.written(5,5,602);s.receive(bytes("Bruce v1.16.1\n# "),603);ok(!s.verified&&s.stage==UsbSerialSession.Stage.BLOCKED,"board field required");
  s.open(0);s.begin("info",600);s.written(5,5,600);s.tick(6600);ok(s.stage==UsbSerialSession.Stage.BLOCKED,"deadline stops requests");s.receive(bytes(identity),6700);ok(!s.verified&&s.late==1,"late identity/prompt cannot unblock");ok(s.begin("info",7000)==null,"timeout not replayed");
  s.open(0);s.begin("info",600);s.written(4,5,601);ok(s.stage==UsbSerialSession.Stage.BLOCKED,"partial write stops session");
  s.open(0);s.begin("info",600);s.written(-1,5,601);ok(s.stage==UsbSerialSession.Stage.BLOCKED,"failed write stops session");
  s.open(0);s.begin("info",600);s.written(5,5,601);s.receive(bytes(identity),602);s.receive(bytes("Guru Meditation Error: panic\n"),603);ok(!s.verified&&s.stage==UsbSerialSession.Stage.BLOCKED,"panic revokes controls");
  for(String reset:new String[]{"ESP-ROM:esp32s3\n","Rebooting...\r\n","rst:0x1\n"}){s.open(0);s.receive(bytes(reset),600);ok(s.stage==UsbSerialSession.Stage.BLOCKED,"boot output revoked "+reset);}
  s.open(0);s.receive(new byte[]{(byte)0xaa,5,0,0,0},600);ok(s.stage==UsbSerialSession.Stage.BLOCKED,"unexpected screen stream blocked");
  s.open(0);s.receive(new byte[]{(byte)0xaa,1},600);ok(s.stage==UsbSerialSession.Stage.BLOCKED,"malformed serial rejected");
  s.close();s.receive(bytes(identity),10000);ok(!s.verified&&s.stage==UsbSerialSession.Stage.CLOSED,"closed session ignores bytes");
  Random random=new Random(5631);for(int n=0;n<10000;n++){s.open(0);s.begin("info",600);s.written(5,5,601);byte[] junk=new byte[random.nextInt(512)];random.nextBytes(junk);s.receive(junk,602);s.tick(7000);ok(!s.verified&&s.stage==UsbSerialSession.Stage.BLOCKED,"random untrusted bytes do not verify");}
 }
 static void profileTests(){
  UsbDevice d=device();UsbProfile p=UsbProfile.find(d);ok(p!=null&&p.data.id==1&&p.control.id==0,"native CDC selected without JTAG");
  d.vid=0x10c4;ok(UsbProfile.find(d)==null,"USB UART vendor rejected");d=device();d.pid=2;ok(UsbProfile.find(d)==null,"unknown Espressif product rejected");
  d=device();d.interfaces[1].eps[0].type=3;ok(UsbProfile.find(d)==null,"non-bulk data rejected");d=device();d.interfaces[1].eps[0].size=0;ok(UsbProfile.find(d)==null,"zero endpoint size rejected");
  d=device();d.interfaces[1].eps[1].direction=128;ok(UsbProfile.find(d)==null,"missing OUT rejected");d=device();d.interfaces[1].alt=1;ok(UsbProfile.find(d)==null,"alternate setting rejected");
  d=device();d.interfaces[2]=new UsbInterface(2,10,0,new UsbEndpoint(128,2,64),new UsbEndpoint(0,2,64));ok(UsbProfile.find(d)==null,"ambiguous multi-CDC rejected");d=device();d.interfaces[0].id=3;ok(UsbProfile.find(d)==null,"wrong control association rejected");ok(UsbProfile.find(null)==null,"missing descriptor rejected");
 }
 static void controllerTests()throws Exception {
  try(Fixture f=new Fixture()){
   f.open();ok(!f.controller.isReady()&&f.port().outgoing.isEmpty(),"connect emits no CLI and no ready control");ok(f.port().claims==1&&f.port().controlClaims==0,"only CDC data interface claimed");ok(f.ctx.nonExported>0,"API35 receiver not exported");
   ok("USB open · Verify Bruce".equals(f.listener.state),"open unverified USB is not labelled disconnected");
   ok(!f.controller.command("nav next"),"unverified actual controller rejects navigation");f.verify();ok(f.listener.ready==1,"exactly one verified callback");
   ok("USB · Bruce verified".equals(f.listener.state),"identity response updates verified status");
   ok(f.controller.command("nav next"),"navigation accepted after identity");ok(!f.controller.command("nav select"),"no second in-flight request");until(()->f.port().outgoing.size()==2,"nav write");f.port().incoming.add(bytes("#"));f.port().incoming.add(bytes(" "));until(()->f.controller.session.stage==UsbSerialSession.Stage.OPEN,"fragmented prompt");Handler.advance(200);ok(f.controller.commandIdle(),"USB control unblocks after prompt and pacing");
   ok(!f.controller.command("display start"),"actual controller rejects display");ok(!f.controller.command("info\nreboot"),"command injection rejected");
   ok(f.controller.command("nav next"),"second navigation starts");until(()->f.port().outgoing.size()==3,"pending nav");Handler.advance(7000);ok(!f.controller.isReady(),"timeout disables control");f.port().incoming.add(bytes("# "));until(()->f.controller.session.late==1,"late response classified");ok(f.port().outgoing.size()==3&&!f.controller.command("nav next"),"late prompt no replay or unlock");
  }
  try(Fixture f=new Fixture()){
   f.ctx.manager.permission=false;f.controller.connect(f.d);PendingIntent request=f.ctx.manager.requested;ok(request!=null&&f.ctx.manager.opens==0,"permission requested before open");ok(f.ctx.receivers.size()==2,"permission and detach listeners registered");
   f.ctx.manager.permission=true;f.ctx.broadcast(request.intent);until(()->f.controller.isOpen(),"grant via Android permission state");ok(request.cancelled&&f.ctx.receivers.size()==1,"one-shot permission token removed");
  }
  try(Fixture f=new Fixture()){
   f.ctx.manager.permission=false;f.controller.connect(f.d);PendingIntent request=f.ctx.manager.requested;f.ctx.broadcast(request.intent);ok(f.ctx.manager.opens==0&&!f.controller.isOpen(),"denied permission no open");ok(f.ctx.receivers.isEmpty(),"denial unregisters receivers");
  }
  try(Fixture f=new Fixture()){
   f.ctx.manager.permission=false;f.controller.connect(f.d);PendingIntent request=f.ctx.manager.requested;f.controller.disconnect("foreground ended");f.ctx.manager.permission=true;f.ctx.broadcast(request.intent);ok(f.ctx.manager.opens==0,"stale permission grant after disconnect ignored");ok(request.cancelled,"pending permission cancelled on disconnect");
  }
  try(Fixture f=new Fixture()){
   f.ctx.manager.permission=false;f.controller.connect(f.d);Handler.advance(60000);ok(f.ctx.manager.opens==0&&f.ctx.receivers.isEmpty(),"permission deadline cleans all registrations");
  }
  try(Fixture f=new Fixture()){
   f.open();f.verify();f.ctx.manager.devices.clear();f.ctx.broadcast(new Intent(UsbManager.ACTION_USB_DEVICE_DETACHED));ok(!f.controller.isReady()&&!f.controller.isOpen(),"unplug clears verified state immediately");until(()->f.port().closed,"unplug close");ok(f.port().releases==1&&f.port().released.equals(Arrays.asList(1)),"only claimed data interface released");
  }
  try(Fixture f=new Fixture()){
   f.open();f.ctx.broadcast(new Intent(UsbManager.ACTION_USB_DEVICE_DETACHED));ok(f.controller.isOpen(),"unrelated/spoofed detach cannot close present device");
  }
  try(Fixture f=new Fixture()){
   f.port().claim=false;f.controller.connect(f.d);until(()->f.port().closed,"failed claim close");Handler.runCurrent();ok(!f.controller.isReady()&&f.port().outgoing.isEmpty(),"unavailable data interface stops without writes");
  }
  try(Fixture f=new Fixture()){
   f.port().controlBusy=true;f.open();
   ok(f.port().controlClaims==0&&f.port().normalClaims==1&&f.port().forceClaims==0,"busy unused control interface does not block bulk channel");
   ok(f.controller.diagnostics().contains("CDC data normal claim: Succeeded")&&f.controller.diagnostics().contains("CDC data driver-handoff claim: Not attempted"),"normal-only result reported accurately");
   f.verify();
  }
  try(Fixture f=new Fixture()){
   f.port().dataKernelBound=true;f.open();
   ok(f.port().claimLog.equals(Arrays.asList("1:false","1:true")),"kernel-owned data channel uses one scoped driver-handoff fallback");
   ok(f.port().controlClaims==0&&f.port().outgoing.isEmpty(),"fallback does not claim control or send firmware commands");
   ok(f.controller.diagnostics().contains("CDC data normal claim: Returned false")&&f.controller.diagnostics().contains("CDC data driver-handoff claim: Succeeded"),"fallback outcome and initial rejection retained");
   f.verify();
  }
  try(Fixture f=new Fixture()){
   f.port().claim=false;f.controller.connect(f.d);
   until(()->f.port().closed,"both claim attempts denied");Handler.runCurrent();
   ok(f.port().claimLog.equals(Arrays.asList("1:false","1:true")),"unresolved claim has no retry loop");
   ok(f.port().releases==0&&f.port().bulkCalls==0,"failed claims do not release unowned interfaces or start bulk I/O");
   ok(f.controller.diagnostics().contains("CDC data driver-handoff claim: Returned false"),"failed fallback remains visible after close");
  }
  try(Fixture f=new Fixture()){
   f.port().throwOnNormal=true;f.controller.connect(f.d);
   until(()->f.port().closed,"normal claim exception close");Handler.runCurrent();
   ok(f.port().forceClaims==0&&f.port().bulkCalls==0,"normal claim exception does not attempt fallback or I/O");
   ok(f.controller.diagnostics().contains("CDC data normal claim: Android exception"),"normal exception remains visible after close");
  }
  try(Fixture f=new Fixture()){
   f.port().dataKernelBound=true;f.port().throwOnForce=true;f.controller.connect(f.d);
   until(()->f.port().closed,"driver handoff exception close");Handler.runCurrent();
   ok(f.port().releases==0&&f.port().bulkCalls==0,"permission exception closes without I/O or release of unowned data");
   ok(f.controller.diagnostics().contains("CDC data driver-handoff claim: Android exception"),"handoff exception reported");
  }
  try(Fixture f=new Fixture()){
   f.port().dataKernelBound=true;f.port().onClaim=()->f.ctx.manager.permission=false;
   f.controller.connect(f.d);until(()->f.port().closed,"permission removed before fallback");Handler.runCurrent();
   ok(f.port().forceClaims==0&&f.port().bulkCalls==0,"no handoff after permission revoked");
  }
  try(Fixture f=new Fixture()){
   f.port().dataKernelBound=true;f.port().onClaim=()->f.ctx.manager.devices.clear();
   f.controller.connect(f.d);until(()->f.port().closed,"device removed before fallback");Handler.runCurrent();
   ok(f.port().forceClaims==0&&f.port().bulkCalls==0,"no handoff after device removed");
  }
  for(boolean claimSucceeds:new boolean[]{false,true})try(Fixture f=new Fixture()){
   java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),resume=new java.util.concurrent.CountDownLatch(1);
   f.port().claim=claimSucceeds;f.port().onClaim=()->{entered.countDown();try{if(!resume.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("claim gate timeout");}catch(InterruptedException e){throw new AssertionError(e);}};
   f.controller.connect(f.d);ok(entered.await(3,java.util.concurrent.TimeUnit.SECONDS),"claim starts before cancellation test");
   f.controller.disconnect("cancelled during claim");resume.countDown();until(()->f.port().closed,"cancelled claim worker closes");Handler.runCurrent();
   ok(!f.controller.isOpen()&&f.port().forceClaims==0&&f.port().bulkCalls==0,"cancelled claim cannot fallback or begin reads");
   ok(f.port().releases==(claimSucceeds?1:0),"cancelled success releases exactly its acquired data interface");
  }
  try(Fixture f=new Fixture()){
   f.d.pid=2;f.controller.connect(f.d);
   ok(f.ctx.manager.opens==0&&f.port().claims==0,"unsupported profile never reaches driver handoff");
  }
  try(Fixture f=new Fixture()){
   f.open();f.port().writeResult=2;ok(f.controller.command("info"),"partial-write fixture");until(()->f.port().closed,"partial write close");Handler.runCurrent();ok(f.port().outgoing.size()==1&&!f.controller.isReady(),"partial write closes without suffix retry");
  }
  try(Fixture f=new Fixture()){
   f.open();f.port().incoming.add(bytes("Rebooting...\r\n"));until(()->f.controller.session.stage==UsbSerialSession.Stage.BLOCKED,"device reset detection");ok(!f.controller.command("info"),"reset stops commands without replay");
  }
  try(Fixture f=new Fixture()){
   f.ctx.pm.host=false;ok(!f.controller.supported()&&f.controller.devices().isEmpty(),"USB host absence reported");f.controller.connect(f.d);ok(f.ctx.manager.opens==0,"unsupported host never opens");
  }
  try(Fixture f=new Fixture()){
   f.open();int oldToken=f.controller.sessionToken();
   Context next=new Context();UsbDevice second=device();next.manager.devices.put(second.name,second);
   UsbController replacement=new UsbController(next,new Listener());
   replacement.connect(second);
   until(()->replacement.isOpen(),"replacement controller open");
   ok(f.port().closed&&!f.controller.isOpen(),"new controller serializes opening after old owner closes");
   ok(!f.controller.matchesSession(oldToken),"previous owner session token revoked");
   replacement.destroy();until(()->next.manager.connection.closed,"replacement cleanup");
   ok(next.receivers.isEmpty(),"replacement receiver lifecycle bounded");
  }
  try(Fixture f=new Fixture()){
   Build.VERSION.SDK_INT=26;f.open();ok(f.ctx.nonExported==0,"API26 uses compatible receiver overload");Build.VERSION.SDK_INT=35;
  }
 }
 public static void main(String[] args)throws Exception{sessionTests();profileTests();controllerTests();System.out.println("PASS "+checks+" USB assertions, including 10,000 malformed response cases, all identity split points, actual controller with Android/USB doubles. No physical USB test.");}
}
