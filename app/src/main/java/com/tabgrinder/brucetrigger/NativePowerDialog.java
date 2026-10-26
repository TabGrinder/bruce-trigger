package com.tabgrinder.brucetrigger;

/** Reconstruct only the stock 1.16.1 power confirmation's omitted text datum. */
final class NativePowerDialog {
  private boolean power;
  void reset(){power=false;}
  byte[] text(byte[] b,int width,int height) {
    int op=b[2]&255;
    if(op==0||op==99){reset();return b;}
    if(op!=16||width!=320||height!=170)return b;
    int x=FrameDecoder.u16(b,3),y=FrameDecoder.u16(b,5),scale=FrameDecoder.u16(b,7);
    String value=new String(b,13,b.length-13,java.nio.charset.StandardCharsets.US_ASCII).replace("\0","");
    boolean title=value.equals("Power Off Device?")&&x==width/2&&y==height/2-20&&scale==2&&FrameDecoder.u16(b,9)==0xf800;
    if(title)power=true;
    int buttonWidth=width/3-10;
    boolean button=power&&scale==2&&y==height-5
        &&(value.equals("No")&&x==5+buttonWidth/2 || value.equals("Yes")&&x==width*2/3+5+buttonWidth/2);
    if(!title&&!button)return b;
    byte[] out=b.clone();x-=value.length()*3*scale;if(button)y-=8*scale;
    out[3]=(byte)(x>>8);out[4]=(byte)x;out[5]=(byte)(y>>8);out[6]=(byte)y;
    return out;
  }
}
