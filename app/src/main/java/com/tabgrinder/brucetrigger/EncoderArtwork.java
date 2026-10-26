package com.tabgrinder.brucetrigger;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Procedural clear-shell artwork. The index reflects local touch rotation, never device LEDs. */
final class EncoderArtwork {
  private static final int[] ARC_STARTS={-140,-40,80};
  private static final int[] ARROW_ENDS={192,-12};
  private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF circle=new RectF();
  private final float[] ticks=new float[96*4];
  private Shader glass;
  private float x,y,r,d;
  void size(int width,int height,float density){
    d=density;x=width/2f;y=height/2f;r=Math.min(width,height)*.445f;
    glass=new SweepGradient(x,y,new int[]{0xff273944,0xffa7cbd5,0xff13242d,0xff668490,0xff111d23,0xffa9c6cd,0xff273944},null);
    for(int i=0;i<96;i++){
      double a=i*Math.PI/48;float in=r*(i%8==0?.76f:.82f);
      ticks[i*4]=x+(float)Math.cos(a)*in;ticks[i*4+1]=y+(float)Math.sin(a)*in;
      ticks[i*4+2]=x+(float)Math.cos(a)*r*.88f;ticks[i*4+3]=y+(float)Math.sin(a)*r*.88f;
    }
  }
  private void ring(Canvas c,float radius,float stroke,int color){p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(stroke);p.setColor(color);c.drawCircle(x,y,radius,p);}
  void draw(Canvas c,float angle){
    p.setStyle(Paint.Style.FILL);p.setShader(null);p.setColor(0xff071014);c.drawCircle(x,y,r,p);
    p.setStyle(Paint.Style.STROKE);p.setShader(glass);p.setStrokeWidth(r*.16f);c.drawCircle(x,y,r*.91f,p);p.setShader(null);
    ring(c,r,1.2f*d,0xff92afba);ring(c,r*.973f,d,0xff20333e);ring(c,r*.947f,.8f*d,0xffcae3e9);
    ring(c,r*.905f,.8f*d,0xff1e3642);ring(c,r*.86f,d,0xff93aeb8);ring(c,r*.80f,.8f*d,0xff244e5d);
    ring(c,r*.66f,2*d,0xff557a87);ring(c,r*.64f,d,FusionTheme.CYAN);ring(c,r*.62f,d,0xffa7e7eb);
    c.save();c.rotate(angle,x,y);
    p.setColor(0xff698e9e);p.setStrokeWidth(.7f*d);c.drawLines(ticks,p);
    // Sparse clear-shell ribs are intentionally lower contrast than actionable labels.
    p.setColor(0xff8cabb7);p.setStrokeWidth(d);
    for(int i=0;i<12;i++){
      c.save();c.rotate(i*30,x,y);c.drawLine(x-1.8f*d,y-r*.98f,x-1.8f*d,y-r*.84f,p);c.drawLine(x+1.8f*d,y-r*.98f,x+1.8f*d,y-r*.84f,p);c.restore();
    }
    circle.set(x-r*.91f,y-r*.91f,x+r*.91f,y+r*.91f);
    p.setColor(0x5539e5f5);p.setStrokeWidth(7*d);for(int a:ARC_STARTS)c.drawArc(circle,a,12,false,p);
    p.setColor(FusionTheme.CYAN);p.setStrokeWidth(2.5f*d);for(int a:ARC_STARTS)c.drawArc(circle,a,12,false,p);
    p.setStyle(Paint.Style.FILL);p.setColor(0x55b4f455);c.drawRoundRect(x-4*d,y-r*.995f,x+4*d,y-r*.79f,2*d,2*d,p);
    p.setColor(FusionTheme.LIME);c.drawRoundRect(x-1.8f*d,y-r*.97f,x+1.8f*d,y-r*.82f,d,d,p);
    c.restore();
    // Direction cues sit outside the rotating ring and do not indicate telemetry.
    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d);p.setColor(0xff63c9d5);
    float arrow=r*1.09f;circle.set(x-arrow,y-arrow,x+arrow,y+arrow);
    c.drawArc(circle,192,37,false,p);c.drawArc(circle,-49,37,false,p);
    for(float angleDegrees:ARROW_ENDS){
      double a=Math.toRadians(angleDegrees);float ax=x+(float)Math.cos(a)*arrow,ay=y+(float)Math.sin(a)*arrow;
      c.save();c.translate(ax,ay);c.rotate(angleDegrees+90);c.drawLine(0,0,-3*d,-4*d,p);c.drawLine(0,0,3*d,-4*d,p);c.restore();
    }
    p.setShader(null);
  }
  static Drawable centre(float density){return new Centre(density);}
  private static final class Centre extends Drawable {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;private Shader shader;private boolean pressed;private int alpha=255;
    Centre(float density){d=density;}
    @Override protected void onBoundsChange(Rect b){shader=new LinearGradient(0,0,b.width(),b.height(),new int[]{0xff27343b,0xff10191f,0xff050c10},null,Shader.TileMode.CLAMP);}
    @Override public void draw(Canvas c){Rect b=getBounds();float x=b.exactCenterX(),y=b.exactCenterY(),r=Math.min(b.width(),b.height())/2f-2*d;
      p.setStyle(Paint.Style.FILL);p.setShader(shader);p.setAlpha(alpha);c.drawCircle(x,y,r,p);p.setShader(null);
      p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d);p.setColor(pressed?FusionTheme.CYAN:0xff7d969f);p.setAlpha(alpha);c.drawCircle(x,y,r,p);
      p.setColor(0xff02080a);c.drawCircle(x,y,r-2*d,p);
      if(pressed){p.setStyle(Paint.Style.FILL);p.setColor(0x1939e5f5);c.drawCircle(x,y,r-3*d,p);}
    }
    @Override public boolean isStateful(){return true;}
    @Override protected boolean onStateChange(int[] states){boolean down=false;for(int s:states)if(s==android.R.attr.state_pressed||s==android.R.attr.state_focused)down=true;if(down==pressed)return false;pressed=down;invalidateSelf();return true;}
    @Override public void setAlpha(int a){alpha=a;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
  }
}
