package com.tabgrinder.brucetrigger;

import java.nio.charset.StandardCharsets;

/** Default T-Embed GLCD metrics. The stock wire format omits font/datum/wrap state. */
final class NativeText {
  interface Sink {
    void glyph(int index, int x, int y, int scale, int foreground, int background, boolean opaque);
  }
  static boolean render(byte[] b, int panelWidth, Sink sink) {
    if (!FrameValidator.valid(b) || b.length > 128 || panelWidth < 1 || panelWidth > 1024) return false;
    int op=b[2]&255;
    if (op<14 || op>17) return false;
    int scale=FrameDecoder.u16(b,7);
    if (scale<1 || scale>8) return false;
    int x=(short)FrameDecoder.u16(b,3), y=(short)FrameDecoder.u16(b,5);
    int fg=FrameDecoder.u16(b,9), bg=FrameDecoder.u16(b,11);
    int length=b.length-13;
    if(op!=17)for(int i=0;i<length;i++)if(b[13+i]==0){length=i;break;}
    // Stock TFT_eSPI textWidth(font=1) counts bytes, six columns per byte.
    int width=length*6*scale;
    if(op==14)x-=width/2;
    else if(op==15)x-=width;
    String text=new String(b,13,length,StandardCharsets.UTF_8);
    boolean complete=true;
    for(int i=0;i<text.length();) {
      int cp=text.codePointAt(i); i+=Character.charCount(cp);
      if(cp==0)continue;
      if(op==17 && cp=='\r')continue;
      if(op==17 && cp=='\n'){x=0;y+=8*scale;continue;}
      if(op==17 && x+6*scale>panelWidth){x=0;y+=8*scale;}
      int glyph=StockGlyphs.glyph(cp);
      if(glyph<0)complete=false;
      else sink.glyph(glyph,x,y,scale,fg,bg,fg!=bg);
      x+=6*scale;
    }
    return complete;
  }
  private NativeText(){}
}
