import com.tabgrinder.brucetrigger.FrameDecoder;
import com.tabgrinder.brucetrigger.FrameValidator;
import com.tabgrinder.brucetrigger.CommandPolicy;
import java.util.*;
public final class ProtocolTest {
 static int assertions;static void ok(boolean x,String why){assertions++;if(!x)throw new AssertionError(why);}
 static byte[] hex(String s){String[] p=s.split(" ");byte[] b=new byte[p.length];for(int i=0;i<p.length;i++)b[i]=(byte)Integer.parseInt(p[i],16);return b;}
 static class Sink implements FrameDecoder.Sink{ArrayList<byte[]> frames=new ArrayList<>();ArrayList<String> lines=new ArrayList<>();int bad;public void frame(byte[] b){frames.add(b);}public void line(String s){lines.add(s);}public void malformed(){bad++;}}
 public static void main(String[] args){byte[][] vectors={hex("AA 08 63 01 40 00 AA 01"),hex("AA 05 00 00 00"),hex("AA 0D 02 00 0A 00 14 00 64 00 32 A8 0F"),hex("AA 12 10 00 0A 00 14 00 01 A8 0F 00 00 42 72 75 63 65"),hex("AA 0D 0B FF FF 00 00 00 0A 00 14 FF FF")};for(byte[] vector:vectors){ok(FrameValidator.valid(vector),"valid fixture");for(int split=0;split<=vector.length;split++){Sink s=new Sink();FrameDecoder d=new FrameDecoder(s);d.accept(Arrays.copyOfRange(vector,0,split));d.accept(Arrays.copyOfRange(vector,split,vector.length));ok(s.frames.size()==1&&Arrays.equals(s.frames.get(0),vector),"split "+split);}}
 Sink s=new Sink();FrameDecoder d=new FrameDecoder(s);d.accept("hello\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));for(byte[] v:vectors)d.accept(v);d.accept("done\n# ".getBytes(java.nio.charset.StandardCharsets.UTF_8));ok(s.frames.size()==5&&s.lines.size()==3,"text/binary demultiplex");
 ok(!FrameValidator.valid(hex("AA 08 63 FF FF FF FF 01")),"reject allocation bomb");ok(!FrameValidator.valid(hex("AA 03 10")),"reject short string");ok(!FrameValidator.valid(hex("AA 05 7F 00 00")),"unknown opcode");ok(!FrameValidator.valid(hex("AA 08 63 00 00 00 AA 01")),"zero width");
 Sink bounded=new Sink();FrameDecoder b=new FrameDecoder(bounded);byte[] huge=new byte[1000000];Arrays.fill(huge,(byte)'x');b.accept(huge);ok(bounded.bad>0&&bounded.frames.isEmpty(),"bounded text memory");b.accept(hex("AA 00 AA 01 AA 02"));ok(bounded.bad>1,"invalid header sizes");
 Sink reset=new Sink();FrameDecoder r=new FrameDecoder(reset);r.accept(hex("AA 08 63"));r.reset();r.accept(vectors[1]);ok(reset.frames.size()==1&&reset.frames.get(0).length==5,"reset partial on disconnect");
 java.io.ByteArrayOutputStream joined=new java.io.ByteArrayOutputStream();for(byte[] v:vectors)joined.write(v,0,v.length);Sink co=new Sink();new FrameDecoder(co).accept(joined.toByteArray());ok(co.frames.size()==vectors.length,"coalesced frames");
 ok(!CommandPolicy.allowed("display start"),"mirror startup removed after physical reboot");ok(!CommandPolicy.allowed("display stop"),"mirror shutdown removed after physical reboot");
 for(String cmd:new String[]{"nav next","info","poweroff","storage free"}){byte[] wire=CommandPolicy.encode(cmd);ok(wire!=null&&wire.length<=20&&wire[wire.length-1]==10,"single complete ATT write");}
 for(String attack:new String[]{"info\npoweroff","info\r","nav next; reboot","wifi add name secret","info\u0000","你好",null,""})ok(CommandPolicy.encode(attack)==null,"reject unapproved text or command chaining");

 // Invalid structural headers must not swallow the valid frame that follows them.
 for(String recovery:new String[]{"AA FE 7F AA 05 00 00 00","AA FE AA 05 00 00 00","AA AA 05 00 00 00","AA 00 AA 05 00 00 00","AA 03 00 AA 05 00 00 00"}){
  byte[] stream=hex(recovery);for(int split=0;split<=stream.length;split++){Sink rec=new Sink();FrameDecoder dec=new FrameDecoder(rec);dec.accept(Arrays.copyOfRange(stream,0,split));dec.accept(Arrays.copyOfRange(stream,split,stream.length));ok(rec.bad>0&&rec.frames.size()==1&&Arrays.equals(rec.frames.get(0),vectors[1]),"early header recovery "+recovery+" / "+split);ok(!dec.isPartial(),"recovered parser complete");}}
 Sink partial=new Sink();FrameDecoder pd=new FrameDecoder(partial);pd.accept(hex("AA 12 10 00"));ok(pd.isPartial(),"partial frame exposed for timeout");pd.reset();ok(!pd.isPartial(),"timeout reset clears partial");pd.accept(vectors[0]);ok(partial.frames.size()==1,"timeout recovery");
 final int[] delivered={0},reported={0};FrameDecoder throwing=new FrameDecoder(new FrameDecoder.Sink(){public void frame(byte[] bytes){if(delivered[0]++==0)throw new IllegalArgumentException("synthetic renderer failure");}public void line(String x){throw new IllegalStateException("synthetic line sink failure");}public void malformed(){reported[0]++;}});throwing.accept(joined.toByteArray());throwing.accept("a\nb\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));ok(delivered[0]==vectors.length&&reported[0]==3&&!throwing.isPartial(),"sink exception isolation and progress");
 FrameDecoder failingReporter=new FrameDecoder(new FrameDecoder.Sink(){public void frame(byte[] bytes){throw new IllegalStateException();}public void line(String x){throw new IllegalStateException();}public void malformed(){throw new IllegalStateException();}});failingReporter.accept(hex("AA 00 AA 05 00 00 00"));ok(!failingReporter.isPartial(),"reporting exceptions cannot wedge parser");
 for(int length=0;length<=255;length++)for(int op=0;op<=255;op++)if(FrameValidator.validHeader(length,op))ok(length>=5&&op!=19,"header length/opcode range");
 Random chunks=new Random(31);for(int trial=0;trial<200;trial++){Sink fragmented=new Sink();FrameDecoder fd=new FrameDecoder(fragmented);byte[] stream=joined.toByteArray();int pos=0;while(pos<stream.length){int end=Math.min(stream.length,pos+1+chunks.nextInt(17));fd.accept(Arrays.copyOfRange(stream,pos,end));pos=end;}ok(fragmented.frames.size()==vectors.length&&!fd.isPartial(),"random notification boundaries");for(int i=0;i<vectors.length;i++)ok(Arrays.equals(vectors[i],fragmented.frames.get(i)),"fragmented payload preserved");}
 Random random=new Random(42);for(int n=0;n<10000;n++){byte[] fuzz=new byte[random.nextInt(256)];random.nextBytes(fuzz);FrameValidator.valid(fuzz);if(n%100==0){Sink f=new Sink();FrameDecoder fd=new FrameDecoder(f);fd.accept(fuzz);for(byte[] out:f.frames)ok(out.length>=3&&out.length<=255&&FrameValidator.validHeader(out.length,out[2]&255),"bounded structurally-valid frame");}}
 for(int size:new int[]{2047,2048,2049,4098}){
   Sink boundary=new Sink();FrameDecoder parser=new FrameDecoder(boundary);byte[] payload=new byte[size+1];Arrays.fill(payload,0,size,(byte)'x');payload[size]=10;parser.accept(payload);
   ok(boundary.bad==(size>2048?1:0),"one report when a line exceeds 2048 bytes");
   ok(boundary.lines.size()==(size>2048?0:1),"reject the whole oversized line including its suffix");
   if(size<=2048)ok(boundary.lines.get(0).length()==size,"valid boundary line remains complete");
 }
 byte[] unicode="ñéΩ\n# ".getBytes(java.nio.charset.StandardCharsets.UTF_8);
 for(int split=0;split<=unicode.length;split++){Sink u=new Sink();FrameDecoder parser=new FrameDecoder(u);parser.accept(Arrays.copyOfRange(unicode,0,split));parser.accept(Arrays.copyOfRange(unicode,split,unicode.length));ok(u.lines.equals(Arrays.asList("ñéΩ","# ")),"UTF-8 without reserved binary marker survives every split");}
 System.out.println("PASS "+assertions+" assertions; 10,000 malformed-frame cases; all fixture split points.");}
}
