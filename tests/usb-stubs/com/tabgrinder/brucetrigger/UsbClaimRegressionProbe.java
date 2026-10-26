package com.tabgrinder.brucetrigger;
/** Same host fixtures executed against both old and corrected production controllers. */
public class UsbClaimRegressionProbe {
 public static void main(String[] args)throws Exception {
  boolean expect=Boolean.parseBoolean(System.getProperty("expectRecovery","true"));
  for(boolean[] scenario:new boolean[][]{{true,false},{false,true},{true,true}})try(UsbRegressionTest.Fixture f=new UsbRegressionTest.Fixture()){
   boolean controlBusy=scenario[0],kernelBound=scenario[1];
   f.port().controlBusy=controlBusy;f.port().dataKernelBound=kernelBound;f.controller.connect(f.d);
   UsbRegressionTest.until(()->f.controller.isOpen()||f.port().closed,"claim outcome");
   boolean actual=f.controller.isOpen();
   if(actual!=expect)throw new AssertionError("Expected recovered="+expect+", actual="+actual);
   if(!actual&&(f.port().bulkCalls!=0||!f.port().outgoing.isEmpty()))throw new AssertionError("failure must precede I/O");
   System.out.println("PASS old-vs-new probe: control busy="+controlBusy+", data kernel-bound="+kernelBound+", recovered="+actual+", claim calls="+f.port().claimLog);
  }
 }
}
