package com.tabgrinder.brucetrigger;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class NativeTextTest {
  static int n;static void ok(boolean b,String s){n++;if(!b)throw new AssertionError(s);}
  static byte[] record(int op,int x,int y,int scale,int fg,int bg,String text){
    byte[] str=text.getBytes(StandardCharsets.UTF_8),b=new byte[13+str.length];b[0]=(byte)170;b[1]=(byte)b.length;b[2]=(byte)op;
    int[] values={x,y,scale,fg,bg};for(int i=0;i<values.length;i++){b[3+2*i]=(byte)(values[i]>>8);b[4+2*i]=(byte)values[i];}System.arraycopy(str,0,b,13,str.length);return b;
  }
  static List<int[]> render(byte[] b,boolean complete){List<int[]> cells=new ArrayList<>();boolean result=NativeText.render(b,320,(g,x,y,s,fg,bg,o)->cells.add(new int[]{g,x,y,s,fg,bg,o?1:0}));ok(result==complete,"render completeness");return cells;}
  public static void main(String[] args){
    List<int[]> a=render(record(14,160,30,2,0xffff,0xffff,"Display Settings"),true);
    ok(a.size()==16&&a.get(0)[1]==64&&a.get(15)[1]==244,"centered stock six-column metrics");
    for(int[] c:a)ok(c[6]==0&&c[2]==30&&c[3]==2,"equal colors mean transparent text, without rectangle fill");
    a=render(record(16,12,4,1,0xffff,0,"A"),true);ok(a.get(0)[1]==12&&a.get(0)[6]==1,"unequal colors use an opaque six-by-eight cell");
    a=render(record(15,320,4,2,0xffff,0xffff,"12:34"),true);ok(a.get(0)[1]==260,"right alignment uses 60 pixel width");
    a=render(record(17,312,10,1,0xffff,0xffff,"AB\r\nC"),true);ok(a.size()==3&&a.get(0)[1]==312&&a.get(1)[1]==0&&a.get(1)[2]==18&&a.get(2)[1]==0&&a.get(2)[2]==26,"PRINT wrap, CR and LF reproduce default cursor behavior");
    a=render(record(14,160,10,1,0xffff,0xffff,"é"),true);ok(a.size()==1&&a.get(0)[1]==154,"center width matches UTF-8 byte count in stock textWidth");
    a=render(record(16,-5,-7,1,0xffff,0xffff,"A\u0000ignored"),true);ok(a.size()==1&&a.get(0)[1]==-5&&a.get(0)[2]==-7,"signed coordinates and C string termination");
    a=render(record(17,2,4,1,0xffff,0xffff,"A\u0000B"),true);ok(a.size()==2&&a.get(1)[1]==8,"PRINT ignores NUL without cursor advance");
    render(record(16,0,0,0,0,0,"A"),false);render(record(16,0,0,9,0,0,"A"),false);
    render(record(16,0,0,1,0,0,"漢"),false);render(record(16,0,0,1,0,0,"ÿ"),false);
    int[] capitalA={0x7c,0x12,0x11,0x12,0x7c};for(int col=0;col<5;col++)ok(StockGlyphs.column(65,col)==capitalA[col],"stock A bitmap column "+col);
    ok(StockGlyphs.column(-1,0)==0&&StockGlyphs.column(256,0)==0&&StockGlyphs.column(65,5)==0,"glyph bounds include the blank spacing column");
    ok(StockGlyphs.glyph(175)==175&&StockGlyphs.glyph(176)==177&&StockGlyphs.glyph(255)==-1,"legacy mapping is bounded, including last code point");
    Random r=new Random(22);for(int i=0;i<10000;i++){byte[] b=new byte[r.nextInt(300)];r.nextBytes(b);final int[] count={0};NativeText.render(b,320,(g,x,y,s,fg,bg,o)->{count[0]++;if(g<0||g>255||s<1||s>8)throw new AssertionError("unbounded glyph");});ok(count[0]<=115,"bounded malformed text rendering");}
    System.out.println("PASS "+n+" default stock font/layout assertions (wire omits custom font and datum state).");
  }
}
