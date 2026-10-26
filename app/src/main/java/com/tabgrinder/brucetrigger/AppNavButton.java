package com.tabgrinder.brucetrigger;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;

/** Primary navigation for the Remote and Device screens. */
final class AppNavButton extends LinearLayout {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final float density;
  AppNavButton(Context context,String label,boolean selected){
    super(context);density=getResources().getDisplayMetrics().density;
    setOrientation(VERTICAL);setGravity(Gravity.CENTER);setPadding(0,(int)(7*density),0,(int)(8*density));setWillNotDraw(false);
    setClickable(true);setFocusable(true);setSelected(selected);setFilterTouchesWhenObscured(true);setContentDescription(label);
    ImageView icon=new ImageView(context);icon.setImageDrawable(new AppIcon(label,selected?FusionTheme.CYAN:FusionTheme.MUTED));
    icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(icon,new LayoutParams((int)(24*density),(int)(24*density)));
    TextView text=new TextView(context);text.setText(label.toUpperCase(java.util.Locale.ROOT));text.setTextSize(10);text.setTypeface(Typeface.MONOSPACE);text.setLetterSpacing(.07f);
    text.setTextColor(selected?FusionTheme.CYAN:FusionTheme.MUTED);text.setGravity(Gravity.CENTER);text.setPadding(0,(int)(4*density),0,0);text.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(text);
  }
  @Override protected void onDraw(Canvas c){
    super.onDraw(c);if(isPressed()||isFocused()){paint.setColor(0xff10272f);c.drawRect(0,0,getWidth(),getHeight(),paint);}
    if(isSelected()){paint.setColor(FusionTheme.CYAN);paint.setStrokeWidth(2*density);paint.setStrokeCap(Paint.Cap.ROUND);c.drawLine(getWidth()*.26f,getHeight()-2*density,getWidth()*.74f,getHeight()-2*density,paint);}
  }
  @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(Button.class.getName());info.setSelected(isSelected());}
}
