package com.tabgrinder.brucetrigger;
public final class NativePowerDialogTest {
  static int checks;
  static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
  static byte[] text(String s,int x,int y,int scale,int fg)throws Exception {
    byte[] t=s.getBytes("US-ASCII"),b=new byte[13+t.length];b[0]=(byte)170;b[1]=(byte)b.length;b[2]=16;
    int[] w={x,y,scale,fg,fg};for(int i=0;i<5;i++){b[3+2*i]=(byte)(w[i]>>8);b[4+2*i]=(byte)w[i];}System.arraycopy(t,0,b,13,t.length);return b;
  }
  public static void main(String[] args)throws Exception {
    NativePowerDialog d=new NativePowerDialog();byte[] no=text("No",53,165,2,0);
    ok(d.text(no,320,170)==no,"unrelated No text unchanged without power dialog");
    byte[] question=text("Power Off Device?",160,65,2,0xf800),fixed=d.text(question,320,170);
    ok(FrameDecoder.u16(fixed,3)==58&&FrameDecoder.u16(fixed,5)==65,"power question is top-centered exactly as TC_DATUM");
    fixed=d.text(no,320,170);ok(FrameDecoder.u16(fixed,3)==41&&FrameDecoder.u16(fixed,5)==149,"No sits inside the bottom button");
    byte[] yes=text("Yes",266,165,2,0xf800);fixed=d.text(yes,320,170);
    ok(FrameDecoder.u16(fixed,3)==248&&FrameDecoder.u16(fixed,5)==149,"Yes sits inside the bottom button");
    ok(FrameDecoder.u16(fixed,9)==0xf800,"stock red theme color retained");
    d.text(new byte[]{(byte)170,5,0,0,0},320,170);ok(d.text(no,320,170)==no,"full clear ends datum reconstruction");
    ok(d.text(question,240,135)==question,"unreviewed board geometry unchanged");
    System.out.println("PASS "+checks+" stock power-dialog datum regressions.");
  }
}
