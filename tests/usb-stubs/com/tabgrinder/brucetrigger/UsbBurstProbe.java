package com.tabgrinder.brucetrigger;
import android.os.Handler;
/** Same submitted failure trigger applied to the old and corrected real controllers. */
public final class UsbBurstProbe {
 public static void main(String[] args)throws Exception {
  boolean expected=Boolean.parseBoolean(args[0]);
  UsbRegressionTest.Fixture f=new UsbRegressionTest.Fixture();
  try {
   f.open();f.verify();f.controller.mirror(true);UsbMirrorRegressionTest.finish(f,1,true);
   for(int i=0;i<1000;i++)f.port().incoming.add(UsbMirrorRegressionTest.CLEAR);
   Thread.sleep(200);Handler.runCurrent();
   boolean ready=f.controller.isReady();
   if(ready!=expected)throw new AssertionError("Unexpected burst outcome: ready="+ready);
   System.out.println("PASS 1000 small packets, paused UI: ready="+ready+" (expected "+expected+")");
   if(!ready)System.out.println(f.controller.diagnostics());
  }finally{f.controller.disconnect("Probe ended");f.close();}
 }
}
