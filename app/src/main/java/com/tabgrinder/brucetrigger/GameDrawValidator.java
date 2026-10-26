package com.tabgrinder.brucetrigger;

/** Additional opcodes are confined to owned Files-game frames, never native packets. */
final class GameDrawValidator {
  static boolean valid(byte[] p) {
    if(p==null || p.length<5 || p.length>96 || p[0]!=(byte)170 || (p[1]&255)!=p.length)return false;
    int op=p[2]&255;
    if(op==90)return p.length==11 && id(p,true) && u(p,5)>0 && u(p,5)<=320 && u(p,7)>0 && u(p,7)<=170 && u(p,9)==16;
    if(op==91)return p.length==5 && id(p,false);
    if(op==92)return p.length==11 && id(p,true) && coordinate(p,5) && coordinate(p,7);
    if(op==93)return p.length==5 && id(p,true);
    if(op==94)return p.length>=11 && coordinate(p,3) && coordinate(p,5) && u(p,7)>=1 && u(p,7)<=40 && p.length==9+2*u(p,7);
    if(op==95)return p.length==17 && coordinate(p,3) && coordinate(p,5) && u(p,7)<=2048 && u(p,9)<=2048 && u(p,15)<=1;
    if(op==96)return p.length>=18 && coordinate(p,3) && coordinate(p,5) && u(p,7)>0 && u(p,7)<=320 && u(p,9)>0 && u(p,9)<=170 && u(p,15)<=1 && p.length==17+((u(p,7)+7)/8)*u(p,9);
    if(!FrameValidator.valid(p))return false;
    if(op==0)return true;
    if(op==16) {
      if(p.length<14||!coordinate(p,3)||!coordinate(p,5)||u(p,7)<1||u(p,7)>8||p[p.length-1]!=0)return false;
      for(int i=13;i<p.length-1;i++)if(p[i]<32||p[i]>126)return false;
      return true;
    }
    if(op<1||op>13||op==9||op==10)return false;
    if(!coordinate(p,3)||!coordinate(p,5))return false;
    if(op<=4)return u(p,7)<=2048 && u(p,9)<=2048 && (op<=2 || u(p,11)<=2048);
    if(op==5||op==6)return u(p,7)<=2048;
    if(op==7||op==8)return coordinate(p,7)&&coordinate(p,9)&&coordinate(p,11)&&coordinate(p,13);
    if(op==11)return coordinate(p,7)&&coordinate(p,9);
    if(op==12)return u(p,7)<=2048&&u(p,9)<=u(p,7)&&u(p,11)<=3600&&u(p,13)<=3600;
    if(op==13)return coordinate(p,7)&&coordinate(p,9)&&u(p,11)<=1024;
    return true;
  }
  private static int u(byte[] p,int at){return FrameDecoder.u16(p,at);}
  private static boolean coordinate(byte[] p,int at){return Math.abs((short)u(p,at))<=2048;}
  private static boolean id(byte[] p,boolean required){return u(p,3)>=(required?1:0)&&u(p,3)<=8;}
  private GameDrawValidator(){}
}
