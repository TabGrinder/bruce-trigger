package com.tabgrinder.brucetrigger.audit;

import android.graphics.*;
import java.util.*;
import java.lang.reflect.*;
import java.io.*;

/** C++-generated stock packets and original upstream example images, no hardware. */
final class StockImageAudit {
  static void run(AuditRunner a)throws Throwable {a.onUi(()->{
    Class<?> type=a.targetClass("MirrorView");Object view=a.construct(type);
    Method apply=a.method(type,"apply",byte[].class);
    Map<String,byte[]> images=new LinkedHashMap<>();
    for(String[] row:StockImageFixtures.ROWS)images.put(row[0],android.util.Base64.decode(row[2],0));
    a.method(type,"loadImages",Map.class).invoke(view,images);
    apply.invoke(view,(Object)a.hex("AA 08 63 01 40 00 AA 01"));
    boolean saved=false,cyberSaved=false;
    for(String[] row:StockImageFixtures.ROWS){
      apply.invoke(view,(Object)a.hex("AA 05 00 00 00"));
      byte[] wire=hex(row[1]);
      a.ok(Boolean.TRUE.equals(apply.invoke(view,(Object)wire)),"stock C++ image packet renders "+row[0]);
      byte[] bytes=images.get(row[0]);Bitmap original=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
      Bitmap panel=(Bitmap)a.field(view,"bitmap");
      int x=Integer.parseInt(row[3]),y=Integer.parseInt(row[4]);
      if(row[5].equals("1")){x+=(320-original.getWidth())/2;y+=(170-original.getHeight())/2;}
      int w=original.getWidth(),h=original.getHeight();int[] expected=new int[w*h],actual=new int[w*h];
      original.getPixels(expected,0,w,0,0,w,h);panel.getPixels(actual,0,w,x,y,w,h);
      // The panel is RGB565, so compare against independently quantized pixels,
      // not the JPEG decoder's unquantized ARGB8888 output.
      for(int i=0;i<expected.length;i++)expected[i]=rgb565(expected[i]);
      if(row[0].endsWith(".png") || android.os.Build.VERSION.SDK_INT>=35){
        // PNG alpha needs compositing. API35 JPEG conversion also differs from
        // the manual truncation oracle used for API26. Compare every pixel to
        // an independent platform surface, without MirrorView/cache/packets;
        // never add a tolerance or skip the image/coordinate checks.
        Bitmap reference=Bitmap.createBitmap(w,h,Bitmap.Config.RGB_565);reference.eraseColor(Color.BLACK);
        Paint paint=new Paint();paint.setFilterBitmap(false);paint.setAntiAlias(false);
        new Canvas(reference).drawBitmap(original,0,0,paint);
        reference.getPixels(expected,0,w,0,0,w,h);reference.recycle();
      }
      int mismatch=-1;for(int i=0;i<expected.length;i++)if(expected[i]!=actual[i]){mismatch=i;break;}
      a.ok(mismatch<0,"RGB565 menu-image pixels at stock coordinates "+row[0]
          +(mismatch<0?"":" first mismatch "+mismatch+" expected="+Integer.toHexString(expected[mismatch])+" actual="+Integer.toHexString(actual[mismatch])));
      boolean visible=false;for(int pixel:actual)if((pixel&0xffffff)!=0){visible=true;break;}
      a.ok(visible,"original graphic menu title is not a blank bitmap "+row[0]);
      a.ok((Integer)a.method(type,"rejected").invoke(view)==0,"valid stock image does not mark screen incomplete");
      if(!saved&&row[0].endsWith("/wifi.png")&&row[5].equals("1")){
        try(FileOutputStream f=new FileOutputStream(new File(a.getTargetContext().getExternalFilesDir(null),"stock-theme-menu.png"))){panel.compress(Bitmap.CompressFormat.PNG,100,f);}saved=true;
      }
      if(!cyberSaved&&row[0].equals("0:/Theme/CYBERPUNK2077 by -eni9ma/14.png")){
        try(FileOutputStream f=new FileOutputStream(new File(a.getTargetContext().getExternalFilesDir(null),"cyberpunk-menu.png"))){panel.compress(Bitmap.CompressFormat.PNG,100,f);}cyberSaved=true;
      }
      original.recycle();
    }
    a.ok((Long)a.method(type,"renderedImages").invoke(view)==StockImageFixtures.ROWS.length,"all stock theme fixtures rendered on Android");
    a.ok(saved,"original example menu image screenshot saved");
    a.ok(cyberSaved,"official Cyberpunk CONFIG image screenshot saved from Android renderer");
    a.method(type,"clearImages").invoke(view);
  });}
  static byte[] hex(String h){byte[] b=new byte[h.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(h.substring(i*2,i*2+2),16);return b;}
  static int rgb565(int p){int r=(p>>16)&255,g=(p>>8)&255,b=p&255;return 0xff000000|((r&248)|(r>>5))<<16|((g&252)|(g>>6))<<8|(b&248)|(b>>5);}
}
