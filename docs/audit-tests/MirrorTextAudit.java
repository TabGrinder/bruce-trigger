package com.tabgrinder.brucetrigger.audit;

import android.graphics.*;
import android.view.View;
import java.io.*;
import java.lang.reflect.Method;

/** Synthetic stock-protocol fixtures rendered by the actual Android Canvas. */
final class MirrorTextAudit {
  static byte[] text(int op,int x,int y,int size,int fg,int bg,String value)throws Exception {
    byte[] s=value.getBytes("UTF-8"),b=new byte[13+s.length];b[0]=(byte)170;b[1]=(byte)b.length;b[2]=(byte)op;
    int[] fields={x,y,size,fg,bg};for(int i=0;i<fields.length;i++){b[3+i*2]=(byte)(fields[i]>>8);b[4+i*2]=(byte)fields[i];}
    System.arraycopy(s,0,b,13,s.length);return b;
  }
  static void run(AuditRunner a)throws Throwable {
    a.onUi(()->{
      Class<?> c=a.targetClass("MirrorView");View view=(View)a.construct(c);Method apply=a.method(c,"apply",byte[].class);
      apply.invoke(view,(Object)a.hex("AA 08 63 01 40 00 AA 01"));
      apply.invoke(view,(Object)a.hex("AA 05 00 00 1F"));
      a.ok(Boolean.TRUE.equals(apply.invoke(view,(Object)text(16,12,4,2,0xffff,0xffff,"A"))),"transparent stock glyph accepted");
      Bitmap pixels=(Bitmap)a.field(view,"bitmap");
      int[] expectedA={0x7c,0x12,0x11,0x12,0x7c,0};
      for(int y=0;y<16;y++)for(int x=0;x<12;x++)
        a.ok(pixels.getPixel(12+x,4+y)==(((expectedA[x/2]&(1<<(y/2)))!=0)?Color.WHITE:Color.BLUE),"transparent A pixel "+x+","+y);
      a.ok(pixels.getPixel(11,4)==Color.BLUE&&pixels.getPixel(24,4)==Color.BLUE,"glyph rendering preserves pixels outside its cell");
      apply.invoke(view,(Object)text(16,40,4,2,0xffff,0x07e0,"A"));
      for(int y=0;y<16;y++)for(int x=0;x<12;x++)
        a.ok(pixels.getPixel(40+x,4+y)==(((expectedA[x/2]&(1<<(y/2)))!=0)?Color.WHITE:Color.GREEN),"opaque A pixel "+x+","+y);
      apply.invoke(view,(Object)text(14,160,40,2,0xffff,0xffff,"ABC"));
      a.ok(pixels.getPixel(142,44)==Color.WHITE&&pixels.getPixel(140,44)==Color.BLUE,"centered label begins at six-column stock position");
      apply.invoke(view,(Object)text(17,12,80,1,0xffff,0xffff,"A"));
      a.ok(pixels.getPixel(12,82)==Color.WHITE&&pixels.getPixel(11,82)==Color.BLUE,"PRINT honors clock/title left cursor instead of prior text alignment");
      view.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(170,View.MeasureSpec.EXACTLY));view.layout(0,0,320,170);
      Bitmap composed=Bitmap.createBitmap(320,170,Bitmap.Config.ARGB_8888);view.draw(new Canvas(composed));
      a.ok(composed.getPixel(12,8)==Color.WHITE&&composed.getPixel(12,4)==Color.BLUE,"View paint does not tint the native framebuffer");
      composed.recycle();
      // An incomplete image is recorded, but never opened or fetched as a path.
      apply.invoke(view,(Object)a.hex("AA 0C 12 00 00 00 00 00 00 00 00 00"));
      a.ok((Long)a.method(c,"imagePackets").invoke(view)==1&&(Long)a.method(c,"totalRejected").invoke(view)==1,"image rejection retained as distinct diagnostic");
      a.method(c,"clearScreen").invoke(view);
      a.ok((Long)a.method(c,"lastFrame").invoke(view)==0&&(Long)a.method(c,"totalRejected").invoke(view)==1,"lifecycle clears stale pixels without clearing rejection evidence");
      a.method(c,"reset").invoke(view);
      a.ok((Long)a.method(c,"totalRejected").invoke(view)==0&&(Long)a.method(c,"imagePackets").invoke(view)==0,"new capture resets diagnostic totals");
      // Synthetic demonstration of default-font menu; not a physical-device screenshot.
      apply.invoke(view,(Object)a.hex("AA 08 63 01 40 00 AA 01"));
      apply.invoke(view,(Object)a.hex("AA 05 00 00 00"));
      apply.invoke(view,(Object)text(17,12,4,1,0xffff,0xffff,"12:34"));
      apply.invoke(view,(Object)text(15,308,4,1,0xffff,0xffff,"USB"));
      apply.invoke(view,(Object)text(14,160,32,2,0xffff,0xffff,"Display Settings"));
      apply.invoke(view,(Object)text(14,160,64,2,0xb81f,0xb81f,"Brightness"));
      apply.invoke(view,(Object)text(14,160,88,2,0xffff,0xffff,"Dim Time"));
      apply.invoke(view,(Object)text(14,160,112,2,0xffff,0xffff,"Orientation"));
      apply.invoke(view,(Object)text(14,160,146,1,0xffff,0xffff,"SYNTHETIC CANVAS QA FIXTURE"));
      File output=new File(a.getTargetContext().getExternalFilesDir(null),"stock-text-fixture.png");
      try(FileOutputStream file=new FileOutputStream(output)){((Bitmap)a.field(view,"bitmap")).compress(Bitmap.CompressFormat.PNG,100,file);}
      a.ok(output.length()>100,"native Canvas fixture exported for visual inspection");
    });
  }
}
