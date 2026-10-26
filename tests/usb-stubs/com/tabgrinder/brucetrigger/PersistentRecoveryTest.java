package com.tabgrinder.brucetrigger;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.Handler;

/** Loss of capture ownership must survive replacement of the Android controller. */
public final class PersistentRecoveryTest {
 static int checks;
 static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static UsbController replacement(UsbRegressionTest.Fixture f){return new UsbController(f.ctx,new UsbRegressionTest.Listener());}
 public static void main(String[] args)throws Exception {
  try(UsbRegressionTest.Fixture f=new UsbRegressionTest.Fixture()){
   f.open();f.verify();ok(f.controller.mirror(true),"start capture");UsbMirrorRegressionTest.finish(f,1,true);
   UsbController recovered=replacement(f);
   ok(recovered.interpreterRecoveryRequired(),"active capture checkpoint survives controller loss before disconnect callback");
   f.ctx.manager.devices.clear();f.ctx.broadcast(new Intent(UsbManager.ACTION_USB_DEVICE_DETACHED));
   ok(f.controller.interpreterRecoveryRequired(),"acknowledged startup is not acknowledged capture shutdown");
   ok(f.controller.diagnostics().contains("absent from Android enumeration"),"actual loss evidence retained");
   UsbRegressionTest.until(()->f.port().closed,"lost capture port closes");
   recovered.destroy();recovered=replacement(f);
   ok(recovered.interpreterRecoveryRequired(),"replacement cannot erase unconfirmed capture");
   ok(!recovered.mirror(true)&&!recovered.syncTheme(),"recovered session cannot inject another interpreter");
   recovered.confirmInterpreterRestart();ok(!replacement(f).interpreterRecoveryRequired(),"disconnected restart acknowledgement persists");
   recovered.destroy();
  }
  try(UsbRegressionTest.Fixture f=new UsbRegressionTest.Fixture()){
   f.open();f.verify();f.controller.mirror(true);UsbMirrorRegressionTest.finish(f,1,true);
   f.controller.confirmInterpreterRestart();ok(f.ctx.getSharedPreferences("usb_recovery",0).getBoolean("pending",false),"connected acknowledgement cannot clear active marker");
   ok(f.controller.mirror(false),"start safe stop");UsbMirrorRegressionTest.finish(f,3,false);Handler.advance(1500);
   ok(!replacement(f).interpreterRecoveryRequired(),"acknowledged completion and drain clear persistent marker");
  }
  for(int fault=0;fault<3;fault++)try(UsbRegressionTest.Fixture f=new UsbRegressionTest.Fixture()){
   f.open();f.verify();
   if(fault==0)f.ctx.manager.enumerationFailure=true;
   if(fault==1)f.ctx.manager.permission=false;
   if(fault==2){android.hardware.usb.UsbDevice changed=UsbRegressionTest.device();changed.id++;f.ctx.manager.devices.put(f.d.name,changed);}
   f.ctx.broadcast(new Intent(UsbManager.ACTION_USB_DEVICE_DETACHED));
   String expected=fault==0?"query failed":fault==1?"permission no longer granted":"identity changed";
   ok(!f.controller.isOpen()&&f.controller.diagnostics().contains(expected),"distinct loss evidence: "+expected);
   ok(!f.controller.interpreterRecoveryRequired(),"identity verification does not invent interpreter ownership");
  }
  try(UsbRegressionTest.Fixture f=new UsbRegressionTest.Fixture()){
   f.open();f.verify();f.controller.mirror(true);UsbMirrorRegressionTest.finish(f,1,true);
   f.port().incoming.add(UsbRegressionTest.bytes("ESP-ROM:esp32s3\n"));
   UsbRegressionTest.until(()->f.controller.session.bootObserved,"actual boot text observed");
   f.controller.disconnect("Observed restart");
   ok(!replacement(f).interpreterRecoveryRequired(),"observed restart clears previous interpreter uncertainty");
   ok(f.controller.diagnostics().contains("boot/panic text observed: true"),"boot evidence remains in closed report");
  }
  try(UsbRegressionTest.Fixture f=new UsbRegressionTest.Fixture()){
   f.open();f.verify();f.controller.mirror(true);UsbMirrorRegressionTest.finish(f,1,true);
   byte[] prefix=new byte[2049];java.util.Arrays.fill(prefix,(byte)'x');
   f.port().incoming.add(prefix);
   UsbRegressionTest.until(()->f.controller.session.stage==UsbSerialSession.Stage.BLOCKED,"oversized line blocks control");
   long before=f.controller.session.receivedBytes;
   f.port().incoming.add(UsbRegressionTest.bytes("ESP-ROM:esp32s3\n"));
   UsbRegressionTest.until(()->f.controller.session.receivedBytes>before,"rejected line suffix received");
   ok(!f.controller.session.bootObserved,"oversized line suffix cannot manufacture reboot evidence");
   f.controller.disconnect("Invalid serial line");
   ok(replacement(f).interpreterRecoveryRequired(),"unconfirmed capture remains fenced after oversized input");
  }
  System.out.println("PASS "+checks+" persistent capture ownership and USB loss classification assertions");
 }
}
