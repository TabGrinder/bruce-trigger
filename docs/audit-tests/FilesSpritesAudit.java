package com.tabgrinder.brucetrigger.audit;

import android.graphics.*;
import java.lang.reflect.*;
import java.util.*;

final class FilesSpritesAudit {
  static byte[] p(int op,int... words){byte[] b=new byte[3+2*words.length];b[0]=(byte)170;b[1]=(byte)b.length;b[2]=(byte)op;for(int i=0;i<words.length;i++){b[3+2*i]=(byte)(words[i]>>8);b[4+2*i]=(byte)words[i];}return b;}
  static void run(AuditRunner a)throws Throwable {a.onUi(()->{
    Object view=a.construct(a.targetClass("MirrorView"));Method draw=a.method(view.getClass(),"applyGameFrame",List.class);
    List<byte[]> initial=Arrays.asList(a.hex("aa 08 63 01 40 00 aa 01"),p(0,31),p(90,1,20,10,16),p(91,1),p(0,0),p(2,2,2,5,5,63488),p(92,1,10,20,0));
    a.ok(Boolean.TRUE.equals(draw.invoke(view,initial)),"Files sprites compose through the production renderer");
    Bitmap bitmap=(Bitmap)a.field(view,"bitmap");
    a.ok(bitmap.getPixel(12,22)==Color.RED,"sprite draws its red rectangle at the pushed offset");
    a.ok(bitmap.getPixel(10,20)==Color.BLUE,"transparent sprite pixels preserve the live screen background");
    Object sprites=a.field(view,"gameSprites");
    Bitmap mask=((Bitmap[])a.field(sprites,"transparentImages"))[1];
    a.ok(mask!=null,"transparent sprite mask prepared");
    a.ok(Boolean.TRUE.equals(draw.invoke(view,Arrays.asList(p(92,1,10,20,0)))),"immutable sprite can be pushed again");
    a.ok(((Bitmap[])a.field(sprites,"transparentImages"))[1]==mask,"unchanged sprite reuses its transparent mask without reading every pixel again");
    List<byte[]> bad=Arrays.asList(p(91,1),p(0,2016),p(92,1,30,20,0),new byte[]{(byte)170,3,89});
    a.ok(Boolean.FALSE.equals(draw.invoke(view,bad)),"invalid final record rejects the whole screen/sprite transaction");
    a.ok(((Bitmap)a.field(view,"bitmap")).getPixel(32,22)==Color.BLUE,"rejected sprite push never changes the front screen");
    a.ok(Boolean.TRUE.equals(draw.invoke(view,Arrays.asList(p(92,1,30,20,0)))),"sprite retained after rollback can be pushed again");
    a.ok(((Bitmap)a.field(view,"bitmap")).getPixel(32,22)==Color.RED,"sprite backing pixels were rolled back with the screen");
    a.ok(Boolean.TRUE.equals(draw.invoke(view,Arrays.asList(p(91,1),p(0,2016),p(92,1,60,20,0),p(0,63488),p(92,1,90,20,0)))),"two sprite edits and pushes in one transaction compose");
    bitmap=(Bitmap)a.field(view,"bitmap");
    a.ok(bitmap.getPixel(60,20)==Color.GREEN&&bitmap.getPixel(90,20)==Color.RED,"cached push invalidates after every mutation, including same-transaction mutation");
    a.ok(Boolean.TRUE.equals(draw.invoke(view,Arrays.asList(p(91,0),p(94,2,4,3,63488,2016,31)))),"bounded RGB pixel run accepted");
    bitmap=(Bitmap)a.field(view,"bitmap");a.ok(bitmap.getPixel(2,4)==Color.RED&&bitmap.getPixel(3,4)==Color.GREEN&&bitmap.getPixel(4,4)==Color.BLUE,"bitmap colors land at the exact pixel coordinates");
    byte[] mono=java.util.Arrays.copyOf(p(96,5,6,3,1,65535,0,0),18);mono[1]=18;mono[17]=5;
    a.ok(Boolean.TRUE.equals(draw.invoke(view,Arrays.asList(p(91,0),mono))),"compact monochrome bitmap accepts exact bounded payload");
    bitmap=(Bitmap)a.field(view,"bitmap");a.ok(bitmap.getPixel(5,6)==Color.WHITE&&bitmap.getPixel(6,6)==Color.BLUE&&bitmap.getPixel(7,6)==Color.WHITE,"compact bitmap preserves transparent pixels and bit order");
    a.ok(Boolean.TRUE.equals(draw.invoke(view,Arrays.asList(p(0,54938)))),"non-primary RGB565 background established");
    int untouched=((Bitmap)a.field(view,"bitmap")).getPixel(6,6);
    for(int pass=0;pass<12;pass++){
      a.ok(Boolean.TRUE.equals(draw.invoke(view,Arrays.asList(mono))),"repeated transparent mask composes");
      a.ok(((Bitmap)a.field(view,"bitmap")).getPixel(6,6)==untouched,"transparent masks never re-quantize untouched RGB565 pixels");
    }
    // Independent per-pixel oracle for the optimized row writer, including
    // negative origins, both edges, padding bits, opacity and narrow sprites.
    int[][] cases={{-7,-1,19,3},{315,169,19,3},{0,0,320,1},{-320,0,320,1},{320,0,17,2},{0,-3,17,2},{0,170,17,2},{1,1,9,4}};
    for(boolean inSprite:new boolean[]{false,true})for(boolean opaque:new boolean[]{false,true})for(int[] c:cases){
      int sw=inSprite?23:320,sh=inSprite?11:170;
      List<byte[]> setup=new ArrayList<>();setup.add(p(91,0));setup.add(p(0,31));
      if(inSprite){setup.add(p(90,2,sw,sh,16));setup.add(p(91,2));setup.add(p(0,31));}
      int x=c[0],y=c[1],w=c[2],h=c[3],stride=(w+7)/8;
      byte[] packet=Arrays.copyOf(p(96,x,y,w,h,65535,63488,opaque?1:0),17+stride*h);packet[1]=(byte)packet.length;
      for(int i=17;i<packet.length;i++)packet[i]=(byte)((i*37+91)&255);
      setup.add(packet);if(inSprite){setup.add(p(92,2,0,0,0x0120));setup.add(p(93,2));}
      a.ok(Boolean.TRUE.equals(draw.invoke(view,setup)),"bounded mono edge case composes, sprite="+inSprite+", opaque="+opaque+", x="+x+", y="+y);
      int[] expected=new int[320*170];Arrays.fill(expected,Color.BLUE);
      for(int sy=0;sy<h;sy++)for(int sx=0;sx<w;sx++){
        int dx=x+sx,dy=y+sy;
        if(dx<0||dx>=sw||dy<0||dy>=sh)continue;
        if((packet[17+sy*stride+sx/8]&(1<<(sx%8)))!=0)expected[dy*320+dx]=Color.WHITE;
        else if(opaque)expected[dy*320+dx]=Color.RED;
      }
      int[] actual=new int[320*170];((Bitmap)a.field(view,"bitmap")).getPixels(actual,0,320,0,0,320,170);
      a.ok(Arrays.equals(expected,actual),"row rendering equals independent pixel oracle, including untouched margins");
    }
    a.ok(Boolean.TRUE.equals(draw.invoke(view,Arrays.asList(p(91,0),p(90,3,23,11,16),p(91,3),p(0,54938)))),"pooled sprite fixture created");
    Bitmap original=((Bitmap[])a.field(sprites,"images"))[3];int preserved=original.getPixel(20,9);
    long allocated=(Long)a.field(sprites,"allocations"),reused=(Long)a.field(sprites,"reuses");
    java.util.Set<Bitmap> backings=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Bitmap,Boolean>());backings.add(original);
    for(int pass=0;pass<160;pass++) {
      a.ok(Boolean.TRUE.equals(draw.invoke(view,Arrays.asList(p(91,3),p(2,0,0,1,1,pass%2==0?63488:2016)))),"pooled sprite mutation commits");
      Bitmap b=((Bitmap[])a.field(sprites,"images"))[3];backings.add(b);
      a.ok(b.getPixel(20,9)==preserved,"pooled RGB565 copy preserves untouched exact pixel");
      a.ok(b.getPixel(0,0)==(pass%2==0?Color.RED:Color.GREEN),"pooled mutation paints current pixel");
      if(pass%11==0){
        a.ok(Boolean.FALSE.equals(draw.invoke(view,Arrays.asList(p(91,3),p(0,31),new byte[]{(byte)170,3,89}))),"pooled invalid transaction rejected");
        a.ok(((Bitmap[])a.field(sprites,"images"))[3]==b&&b.getPixel(20,9)==preserved,"pooled rollback preserves committed backing and pixels");
      }
    }
    a.ok(backings.size()==2&&(Long)a.field(sprites,"allocations")==allocated+1,"160 sprite edits use two backing images and one copy allocation");
    a.ok((Long)a.field(sprites,"reuses")>=reused+159,"bounded working image reused for warm sprite mutations");
    a.ok(Boolean.FALSE.equals(draw.invoke(view,Arrays.asList(p(93,3),p(90,3,7,3,16),p(91,3),p(0,31),new byte[]{(byte)170,3,89}))),"delete and resized recreate roll back atomically");
    a.ok(((Bitmap[])a.field(sprites,"images"))[3].getWidth()==23,"rollback restores original sprite dimensions");
    a.method(view.getClass(),"clearScreen").invoke(view);
    a.ok(a.field(view,"bitmap")==null,"screen clear releases game surfaces");
    for(Bitmap cached:(Bitmap[])a.field(sprites,"transparentImages"))a.ok(cached==null,"screen clear releases derived sprite masks");
    for(Bitmap spare:(Bitmap[])a.field(sprites,"spare"))a.ok(spare==null,"screen clear releases pooled working images");
  });}
}
