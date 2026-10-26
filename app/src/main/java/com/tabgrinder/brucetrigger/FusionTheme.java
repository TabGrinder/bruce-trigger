package com.tabgrinder.brucetrigger;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Shared native surfaces for the approved Clear Core / Pixel Noir fusion. */
final class FusionTheme {
  static final int BACKGROUND=0xff060c0f, PANEL=0xff0c171d, TEXT=0xffe5f9ff,
      MUTED=0xff91a9b5, CYAN=0xff39e5f5, LIME=0xffb4f455, EDGE=0xff405966;
  static Drawable surface(float density, boolean key, boolean glass) {
    return new Surface(density,key,glass);
  }
  private static final class Surface extends Drawable {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box=new RectF();
    private final float d;
    private final boolean key,glass;
    private boolean pressed,enabled=true;
    private int alpha=255;
    private Shader outer,inner;
    Surface(float density,boolean key,boolean glass){d=density;this.key=key;this.glass=glass;}
    @Override protected void onBoundsChange(Rect bounds) {
      outer=new LinearGradient(0,0,0,Math.max(1,bounds.height()),
          glass?new int[]{0xff69818b,0xff15252d,0xff354c56,0xff0d191f}:new int[]{0xff465a64,0xff17252e,0xff283941},null,Shader.TileMode.CLAMP);
      inner=new LinearGradient(0,0,0,Math.max(1,bounds.height()),new int[]{0xff14242c,0xff060e13},null,Shader.TileMode.CLAMP);
    }
    @Override public void draw(Canvas c) {
      Rect b=getBounds();float x=b.left,y=b.top,w=b.width(),h=b.height(),r=(glass?13:9)*d;
      p.setAlpha(enabled?alpha:alpha*55/100);p.setStyle(Paint.Style.FILL);
      box.set(x+d,y+d,x+w-d,y+h-d);p.setShader(outer);c.drawRoundRect(box,r,r,p);
      p.setShader(null);p.setColor(0xff071014);p.setAlpha(enabled?alpha:alpha*55/100);
      float inset=(key?4:glass?4:1.5f)*d;box.inset(inset,inset);c.drawRoundRect(box,Math.max(2*d,r-inset),Math.max(2*d,r-inset),p);
      p.setShader(inner);box.inset(d,d);c.drawRoundRect(box,6*d,6*d,p);p.setShader(null);
      p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d);p.setColor(pressed?CYAN:glass?0xffa8c8d0:key?0xff536e7b:0xff258794);p.setAlpha(enabled?alpha:alpha*45/100);
      box.set(x+2*d,y+2*d,x+w-2*d,y+h-2*d);c.drawRoundRect(box,r-2*d,r-2*d,p);
      if(glass){
        p.setColor(0xff3b5965);box.inset(3*d,3*d);c.drawRoundRect(box,8*d,8*d,p);
        p.setColor(0xff829ca5);c.drawLine(x+20*d,y+4*d,x+w*.55f,y+4*d,p);
        c.drawLine(x+w-4*d,y+18*d,x+w-4*d,y+h*.4f,p);
        p.setColor(0xff338b98);c.drawLine(x+w*.66f,y+h-4*d,x+w-20*d,y+h-4*d,p);
      }
      if(key){
        p.setStyle(Paint.Style.FILL);p.setColor(enabled?CYAN:MUTED);p.setAlpha(enabled?alpha:alpha*35/100);
        box.set(x+15*d,y+10*d,x+w-15*d,y+12*d);c.drawRoundRect(box,d,d,p);
      }
      if(pressed){p.setStyle(Paint.Style.FILL);p.setColor(0x1939e5f5);box.set(x+5*d,y+5*d,x+w-5*d,y+h-5*d);c.drawRoundRect(box,6*d,6*d,p);}
      p.setShader(null);p.setAlpha(255);
    }
    @Override public boolean isStateful(){return true;}
    @Override protected boolean onStateChange(int[] state){
      boolean down=false,on=false;for(int s:state){if(s==android.R.attr.state_pressed||s==android.R.attr.state_focused)down=true;if(s==android.R.attr.state_enabled)on=true;}
      if(down==pressed&&on==enabled)return false;pressed=down;enabled=on;invalidateSelf();return true;
    }
    @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter value){p.setColorFilter(value);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
  }
  private FusionTheme(){}
}
