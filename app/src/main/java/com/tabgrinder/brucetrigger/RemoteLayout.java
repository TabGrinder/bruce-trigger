package com.tabgrinder.brucetrigger;

import android.content.Context;
import android.view.*;
import android.widget.*;

/** Fits the real screen and enlarged encoder into the viewport, with scrolling as an accessibility fallback. */
final class RemoteLayout extends LinearLayout {
  private int viewport;
  private View screen,encoder,status,keys,message;
  private final float density;
  RemoteLayout(Context c){super(c);density=getResources().getDisplayMetrics().density;setOrientation(VERTICAL);setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);}
  void bind(View screen,View status,View encoder,View keys,View message){this.screen=screen;this.status=status;this.encoder=encoder;this.keys=keys;this.message=message;}
  void viewport(int height){viewport=height;}
  @Override protected void onMeasure(int width,int height){
    if(screen!=null){
      int w=MeasureSpec.getSize(width)-getPaddingLeft()-getPaddingRight();
      int room=viewport-getPaddingTop()-getPaddingBottom();
      float font=getResources().getConfiguration().fontScale;
      int fixed=(int)((48*Math.max(1,font)+58+16)*density);
      if(message.getVisibility()!=GONE)fixed+=(int)(40*Math.max(1,font)*density);
      int idealScreen=(int)((w-16*density)*170f/320f+16*density);
      int dial=Math.min((int)(280*density),Math.min(w,(int)Math.max(180*density,room-fixed-idealScreen)));
      int panel=Math.max((int)(104*density),Math.min(idealScreen,room-fixed-dial));
      screen.getLayoutParams().height=panel;
      encoder.getLayoutParams().width=dial;encoder.getLayoutParams().height=dial;
      status.getLayoutParams().height=(int)(48*Math.max(1,font)*density);
      keys.getLayoutParams().height=(int)(58*Math.max(1,Math.min(font,1.5f))*density);
    }
    super.onMeasure(width,height);
  }
  static final class ViewportScroll extends ScrollView {
    ViewportScroll(Context c){super(c);setFillViewport(false);setClipToPadding(false);setVerticalScrollBarEnabled(false);}
    @Override protected void onMeasure(int width,int height){
      if(getChildCount()>0&&getChildAt(0) instanceof RemoteLayout)((RemoteLayout)getChildAt(0)).viewport(MeasureSpec.getSize(height));
      super.onMeasure(width,height);
    }
  }
}
