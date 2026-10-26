package com.tabgrinder.brucetrigger;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.ByteArrayOutputStream;

public final class UploadRegressionTest {
 static int checks;
 static void ok(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 static void writer()throws Exception {
  for(int length:new int[]{1,23,63,64,65,155,256,1024,2470,4096,8192,12288}) {
   byte[] source=new byte[length];new Random(length).nextBytes(source);
   ByteArrayOutputStream result=new ByteArrayOutputStream();int[] chunks={0},pauses={0};
   int written=PacedUsbWriter.write(source,()->false,chunk->{ok(chunk.length>0&&chunk.length<=64,"bounded USB chunk");result.write(chunk,0,chunk.length);chunks[0]++;return chunk.length;},ms->{ok(ms==25,"inter-chunk gap exactly 25 ms");pauses[0]++;});
   ok(written==length&&Arrays.equals(source,result.toByteArray()),"exact bytes and order for length "+length);
   ok(chunks[0]==(length+63)/64&&pauses[0]==chunks[0]-1,"no delay after final block; no duplicate suffix");
  }
  for(int receipt:new int[]{-1,0,1,63,65}) {
   int[] calls={0};int result=PacedUsbWriter.write(new byte[200],()->false,chunk->{calls[0]++;return receipt;},ms->{throw new AssertionError("failed write was paced/retried");});
   ok(calls[0]==1&&result!=200,"short/invalid receipt stops before any later bytes");
  }
  AtomicBoolean cancelled=new AtomicBoolean();int[] calls={0};
  int n=PacedUsbWriter.write(new byte[300],cancelled::get,chunk->{calls[0]++;return chunk.length;},ms->cancelled.set(true));
  ok(n==64&&calls[0]==1,"cancellation during gap cannot send script suffix or EOF");
  n=PacedUsbWriter.write(new byte[300],()->true,chunk->{throw new AssertionError("sent after cancel");},ms->{});
  ok(n==0,"cancel before transfer sends nothing");
  n=PacedUsbWriter.write(new byte[PacedUsbWriter.MAX_WRITE_BYTES+1],()->false,chunk->{throw new AssertionError("oversized request sent");},ms->{});
  ok(n==-1,"upload size bounded before first byte");
  try {PacedUsbWriter.write(new byte[300],()->false,chunk->chunk.length,ms->{throw new InterruptedException();});throw new AssertionError("interrupt ignored");}
  catch(InterruptedException expected){ok(true,"thread interruption cannot silently continue");}
 }
 static void text() {
  String input="(function(){var x=';{}\\\\\\\"',y=\";{}\";return x+y;})();\n";
  String formatted=StockScriptText.lines(input);
  ok(formatted.contains("var x=';{}\\\\\\\"',y=\";{}\";"),"quoted punctuation and escape bytes unchanged");
  String script=ThemeScript.source("0123456789abcdef");
  int lines=0;for(String line:script.split("\n")){ok(line.length()<=240,"theme source lines fit bounded serial-line envelope");lines++;}
  ok(lines>20&&script.length()<4096,"theme source uses many short lines and bounded total size");
 }
 static void errors() {
  for(String error:new String[]{"SyntaxError: expecting ')'","TypeError: not a function","ReferenceError: x is not defined","RangeError: invalid value","InternalError: out of memory"}) {
   UsbSerialSession s=UsbMirrorRegressionTest.verified();byte[] header=s.beginThemeSync("0123456789abcdef",1000);
   s.written(header.length,header.length,1001);s.receive(UsbRegressionTest.bytes("Reading input data from serial buffer until EOF\n"),1002);
   byte[] body=s.takeScriptBody();s.written(body.length,body.length,1003);s.receive(UsbRegressionTest.bytes(error+"\n"),1100);
   ok(s.stage==UsbSerialSession.Stage.BLOCKED&&!s.verified&&s.failure.contains("interpreter"),"script rejection reported immediately: "+error);
   ok(s.beginMirror(true,"0123456789abcdef",2000)==null,"rejected theme script cannot trigger capture");
  }
 }
 static void controller()throws Exception {
  try(UsbRegressionTest.Fixture f=new UsbRegressionTest.Fixture()) {
   f.open();f.verify();ok(f.controller.syncTheme(),"real controller starts theme synchronization");
   UsbRegressionTest.until(()->f.port().outgoing.size()==2,"theme header delivered");
   int offset=f.port().wireWrites.size();
   f.port().incoming.add(UsbRegressionTest.bytes("Reading input data from serial buffer until EOF\n"));
   UsbRegressionTest.until(()->f.port().outgoing.size()==3,"paced full theme source delivered");
   byte[][] chunks=f.port().wireWrites.toArray(new byte[0][]);
   Long[] times=f.port().wireTimes.toArray(new Long[0]);
   ByteArrayOutputStream joined=new ByteArrayOutputStream();
   for(int i=offset;i<chunks.length;i++) {
    ok(chunks[i].length<=64,"controller never sends a bulk body larger than 64 bytes");joined.write(chunks[i]);
    if(i>offset)ok(times[i]-times[i-1]>=20000000L,"controller actually pauses between body chunks");
   }
   ok(Arrays.equals(joined.toByteArray(),f.port().outgoing.toArray(new byte[0][])[2]),"logical receipt represents exact complete wire body");
   ok(chunks.length-offset>30,"theme body is not sent as a single bulk write");
   f.port().incoming.add(UsbRegressionTest.bytes("SyntaxError: expecting ')'\n at index.js:2:1\n"));
   UsbRegressionTest.until(()->f.controller.session.stage==UsbSerialSession.Stage.BLOCKED,"controller observes firmware interpreter error");
   ok(!f.controller.hasCapture()&&!f.controller.command("nav select"),"rejected theme cannot start capture or permit selection");
   f.controller.closeSafely("Theme script rejected");
   UsbRegressionTest.until(()->f.port().closed,"failed theme session closes without any stop-script upload");
   ok(f.port().outgoing.size()==3,"no implicit retry or capture command after syntax error");
  }
 }
 public static void main(String[] args)throws Exception {writer();text();errors();controller();System.out.println("PASS "+checks+" paced-upload, cancellation, short-write, fixed-line and interpreter-error checks.");}
}
