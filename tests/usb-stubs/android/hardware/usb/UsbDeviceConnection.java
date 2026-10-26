package android.hardware.usb;
import java.util.*;import java.util.concurrent.*;
public class UsbDeviceConnection {
 public volatile boolean closed,claim=true,controlBusy=true,dataKernelBound,throwOnForce,throwOnNormal;
 public volatile int claims,releases,controlClaims,normalClaims,forceClaims,bulkCalls;
 public volatile int writeResult=Integer.MAX_VALUE;
 public final List<String> claimLog=Collections.synchronizedList(new ArrayList<>());
 public final List<Integer> released=Collections.synchronizedList(new ArrayList<>());
 public final BlockingQueue<byte[]> incoming=new LinkedBlockingQueue<>(),outgoing=new LinkedBlockingQueue<>();
 public volatile Runnable onClaim;
 public interface ReadSource { int read(byte[] buffer,int length,int timeout); }
 public volatile ReadSource readSource;
 private byte[] remainder; private int remainderAt;
 // Keep legacy tests at logical-command level; wireWrites records every actual bulk call.
 public final List<byte[]> wireWrites=Collections.synchronizedList(new ArrayList<>());
 public final List<Long> wireTimes=Collections.synchronizedList(new ArrayList<>());
 private int expectedBody;
 private java.io.ByteArrayOutputStream body=new java.io.ByteArrayOutputStream();
 private void recordWrite(byte[] data) {
  wireWrites.add(data);wireTimes.add(System.nanoTime());
  if(expectedBody>0) {
   body.write(data,0,data.length);
   if(body.size()>=expectedBody) {
    if(body.size()!=expectedBody)throw new AssertionError("script length mismatch");
    outgoing.add(body.toByteArray());body.reset();expectedBody=0;
   }
  } else {
   outgoing.add(data);
   String text=new String(data,java.nio.charset.StandardCharsets.US_ASCII);
   if(text.startsWith("js run_from_buffer "))expectedBody=Integer.parseInt(text.trim().substring(19))+4;
  }
 }
 public boolean claimInterface(UsbInterface i,boolean force){
  if(i.id>1)throw new AssertionError("JTAG/unknown interface must not be claimed");
  claims++;claimLog.add(i.id+":"+force);
  if(i.id==0){controlClaims++;if(force)throw new AssertionError("no forced control claim");return claim&&!controlBusy;}
  if(force)forceClaims++;else normalClaims++;
  Runnable hook=onClaim;if(hook!=null)hook.run();
  if(!force&&throwOnNormal)throw new SecurityException("fixture normal denial");
  if(force&&throwOnForce)throw new SecurityException("fixture denial");
  if(!claim)return false;
  if(dataKernelBound&&!force)return false;
  if(force)dataKernelBound=false;
  return true;
 }
 public boolean releaseInterface(UsbInterface i){releases++;released.add(i.id);return true;}
 public void close(){closed=true;}
 public int bulkTransfer(UsbEndpoint e,byte[] bytes,int count,int timeout){
  if(closed)throw new IllegalStateException("closed");if(timeout<=0||timeout>1000)throw new AssertionError("unbounded transfer");bulkCalls++;
  if(e.direction==0){recordWrite(Arrays.copyOf(bytes,count));return writeResult==Integer.MAX_VALUE?count:writeResult;}
  ReadSource source=readSource;if(source!=null)return source.read(bytes,count,timeout);
  try{
   if(remainder==null){remainder=incoming.poll(15,TimeUnit.MILLISECONDS);remainderAt=0;}
   if(remainder==null)return -1;
   int n=Math.min(count,remainder.length-remainderAt);
   System.arraycopy(remainder,remainderAt,bytes,0,n);remainderAt+=n;
   if(remainderAt==remainder.length){remainder=null;remainderAt=0;}return n;
  }catch(InterruptedException ex){return -1;}
 }
}
