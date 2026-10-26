package com.tabgrinder.brucetrigger.audit;
import android.graphics.*;
import java.lang.reflect.*;
final class PowerDialogAudit {
  static byte[] text(String s,int x,int y)throws Exception {
    byte[] b=FilesLaunchAudit.ptext(s+"\0",16,y);b[3]=(byte)(x>>8);b[4]=(byte)x;b[8]=2;b[9]=(byte)248;b[10]=0;return b;
  }
  static void run(AuditRunner a)throws Throwable {a.onUi(()->{
    Object view=a.construct(a.targetClass("MirrorView"));Method draw=a.method(view.getClass(),"apply",byte[].class);
    for(byte[] p:new byte[][]{a.hex("aa 08 63 01 40 00 aa 01"),FilesSpritesAudit.p(0,0),text("Power Off Device?",160,65),text("No",53,165),text("Yes",266,165)})
      a.ok(Boolean.TRUE.equals(draw.invoke(view,(Object)p)),"stock power confirmation record renders");
    Bitmap b=(Bitmap)a.field(view,"bitmap");int left=320,right=-1,top=170,bottom=-1,question=0,no=0,yes=0;
    for(int y=0;y<170;y++)for(int x=0;x<320;x++)if(b.getPixel(x,y)!=Color.BLACK){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);if(y<100)question++;else if(x<160)no++;else yes++;}
    a.ok(left>0&&right<319&&top==65&&bottom<169,"question and button glyphs remain inside the entire mirror");
    a.ok(question>200&&no>30&&yes>50,"question, No and Yes are all visible");
    MenuViewAudit.screenshot(a,view,"power-confirmation");
  });}
}
