import com.tabgrinder.brucetrigger.*;
import java.util.*;

public final class NativeMirrorStreamTest {
  static int n;
  static void ok(boolean b,String msg){n++;if(!b)throw new AssertionError(msg);}
  static byte[] geo={(byte)170,8,99,1,64,0,(byte)170,1};
  static byte[] clear={(byte)170,5,0,0,0};
  static byte[] rect={(byte)170,13,2,0,0,0,0,0,10,0,10,(byte)255,(byte)255};
  public static void main(String[] args){
    NativeMirrorStream s=new NativeMirrorStream();
    ok(s.accept(rect,1)==null,"no assumed screen geometry");
    ok(s.accept(geo,2)!=null&&s.hasGeometry(),"native SCREEN_INFO accepted without BCM1");
    ok(s.accept(rect,3)==null&&!s.hasClear(),"drawing before initial clear is not a full screen");
    ok(s.accept(clear,4)!=null&&s.hasClear(),"real full screen clear starts image");
    ok(s.accept(rect,5)!=null&&s.lastDraw()==5,"ordinary drawing accepted");
    ok(s.hasClear()&&!s.broken(),"quiet static screen does not expire on invented heartbeat");
    byte[] retained=s.prepareNativeEntry();
    ok(Arrays.equals(retained,geo)&&s.hasGeometry()&&!s.hasClear()&&s.lastDraw()==0,"warm entry retains only measured geometry, never stale completeness");
    retained[3]=0;
    ok(Arrays.equals(s.prepareNativeEntry(),geo),"cached geometry cannot be changed by caller");
    ok(s.accept(rect,6)==null,"warm entry must receive a real clear before rendering deltas");
    s.accept(clear,6);
    s.invalidate();ok(!s.hasClear()&&s.accept(rect,6)==null,"loss hides subsequent partial image");
    ok(s.accept(clear,7)!=null&&!s.broken(),"real clear recovers stream without navigation injection");
    byte[] rotated=geo.clone();rotated[3]=0;rotated[4]=(byte)170;rotated[5]=1;rotated[6]=64;
    ok(s.accept(rotated,8)!=null&&!s.hasClear(),"geometry change requires new redraw");
    ok(s.accept(rect,9)==null,"new geometry cannot inherit old image completeness");
    s.accept(clear,10);ok(s.accept(rotated,11)!=null&&s.hasClear(),"duplicate geometry does not erase valid image");
    byte[] bcm={(byte)170,11,98,0,0,0,0,0,0,0,0};
    ok(s.accept(bcm,12)==null&&s.broken(),"BCM1 envelope never mistaken for native draw");
    long accepted=s.packets(), rejected=s.rejected();String opcodes=s.opcodeSummary();
    s.clearScreen();ok(!s.hasGeometry()&&!s.hasClear(),"disconnect erases stale screen completeness");
    ok(s.prepareNativeEntry()==null,"disconnect cannot resurrect cached geometry");
    ok(s.packets()==accepted&&s.rejected()==rejected&&opcodes.equals(s.opcodeSummary()),"disconnect retains accepted/rejected counts and opcode evidence");
    ok(opcodes.contains("99:")&&opcodes.contains("2:")&&opcodes.contains("0:"),"bounded histogram identifies received native commands");
    s.reset();ok(!s.hasGeometry()&&!s.hasClear()&&s.packets()==0&&s.rejected()==0&&"None".equals(s.opcodeSummary()),"new capture resets counters and state together");
    byte[] stream=new byte[geo.length+clear.length+rect.length];
    System.arraycopy(geo,0,stream,0,geo.length);System.arraycopy(clear,0,stream,geo.length,clear.length);System.arraycopy(rect,0,stream,geo.length+clear.length,rect.length);
    for(int cut=0;cut<=stream.length;cut++){
      NativeMirrorStream m=new NativeMirrorStream();
      FrameDecoder d=new FrameDecoder(new FrameDecoder.Sink(){public void frame(byte[] b){m.accept(b,100);}public void line(String t){}public void malformed(){m.invalidate();}});
      d.accept(Arrays.copyOfRange(stream,0,cut));d.accept(Arrays.copyOfRange(stream,cut,stream.length));
      ok(m.packets()==3&&m.hasClear()&&!m.broken(),"fragment split "+cut);
    }
    Random r=new Random(7);
    for(int i=0;i<10000;i++){byte[] b=new byte[r.nextInt(300)];r.nextBytes(b);s.accept(b,i);ok(s.broken(),"malformed packet safely rejected");}
    ok(!CommandPolicy.allowed("display start")&&!CommandPolicy.allowed("display stop"),"native lifecycle unavailable after physical reboot");
    for(String forbidden:new String[]{"mirror caps","mirror start","mirror stop","display dump","display status","display start\nreboot"})ok(!CommandPolicy.allowed(forbidden),"unsupported or hazardous command rejected: "+forbidden);
    System.out.println("PASS "+n+" native screen assertions (no device-radio claim).");
  }
}
