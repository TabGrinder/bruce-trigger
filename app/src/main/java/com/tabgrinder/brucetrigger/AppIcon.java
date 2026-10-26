package com.tabgrinder.brucetrigger;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small, consistent app-section icons; no firmware shortcut or connectivity inference. */
final class AppIcon extends Drawable {
  private final String kind;
  private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect=new RectF();
  private int color,alpha=255;
  AppIcon(String kind,int color){this.kind=kind;this.color=color;}
  @Override public void draw(Canvas c){
    Rect b=getBounds();c.save();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);
    p.setColor(color);p.setAlpha(alpha);p.setStrokeWidth(1.45f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
    if(kind.equals("Device")){
      rect.set(6,6,18,18);c.drawRoundRect(rect,1.2f,1.2f,p);
      for(int i=8;i<=16;i+=4){c.drawLine(i,3,i,6,p);c.drawLine(i,18,i,21,p);c.drawLine(3,i,6,i,p);c.drawLine(18,i,21,i,p);}
    }else if(kind.equals("Remote")){
      c.drawCircle(12,12,2,p);for(int r=5;r<=9;r+=4){rect.set(12-r,12-r,12+r,12+r);c.drawArc(rect,-50,100,false,p);c.drawArc(rect,130,100,false,p);}
    }else{
      Path gear=new Path();for(int i=0;i<32;i++){double a=-Math.PI/2+i*Math.PI/16;float r=i%4==0||i%4==3?9.5f:7.5f;float x=12+(float)Math.cos(a)*r,y=12+(float)Math.sin(a)*r;if(i==0)gear.moveTo(x,y);else gear.lineTo(x,y);}gear.close();c.drawPath(gear,p);c.drawCircle(12,12,3,p);
    }
    c.restore();
  }
  @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
  @Override public void setColorFilter(ColorFilter value){p.setColorFilter(value);invalidateSelf();}
  @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
