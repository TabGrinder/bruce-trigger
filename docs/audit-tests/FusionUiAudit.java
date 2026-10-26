package com.tabgrinder.brucetrigger.audit;
import android.app.Activity;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;

/** Exercises the installed native layout; screen data in connected images is a test fixture. */
final class FusionUiAudit {
  static void capture(AuditRunner a, Activity activity, String name) throws Exception {
    View root=(View)a.field(activity,"root");
    int w=root.getWidth(),h=root.getHeight();
    root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));
    root.layout(0,0,w,h);
    Bitmap image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
    root.draw(new Canvas(image));
    try(FileOutputStream f=new FileOutputStream(new File(a.getTargetContext().getExternalFilesDir(null),name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,f);}finally{image.recycle();}
  }
  static void enlargedEncoder(AuditRunner a) throws Exception {
    Class<?> type=a.targetClass("EncoderControl"),stepType=a.targetClass("EncoderControl$Step");
    int[] turns={0},selects={0};
    Object step=Proxy.newProxyInstance(stepType.getClassLoader(),new Class<?>[]{stepType},(proxy,method,args)->{if(method.getName().equals("turn")){turns[0]+=(Integer)args[0];return true;}return null;});
    Constructor<?> constructor=type.getDeclaredConstructor(android.content.Context.class,Runnable.class,stepType);constructor.setAccessible(true);
    View wheel=(View)constructor.newInstance(a.getTargetContext(),(Runnable)()->selects[0]++,step);
    FrameLayout host=new FrameLayout(a.getTargetContext());host.addView(wheel);
    float density=a.getTargetContext().getResources().getDisplayMetrics().density;int side=(int)(280*density);
    host.measure(View.MeasureSpec.makeMeasureSpec(side,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(side,View.MeasureSpec.EXACTLY));host.layout(0,0,side,side);
    float center=side/2f,radius=side*.45f;
    a.touch(host,center,center,radius,0,MotionEvent.ACTION_DOWN);a.touch(host,center,center,radius,20,MotionEvent.ACTION_MOVE);a.touch(host,center,center,radius,20,MotionEvent.ACTION_UP);
    a.ok(turns[0]==1&&selects[0]==0,"280dp encoder hit region scales with artwork; drag sends one next and no SELECT");
    a.touch(host,center,center,radius,20,MotionEvent.ACTION_DOWN);a.touch(host,center,center,radius,0,MotionEvent.ACTION_MOVE);a.touch(host,center,center,radius,0,MotionEvent.ACTION_UP);
    a.ok(turns[0]==0&&selects[0]==0,"enlarged ring reverses without a phantom selection");
    ((Button)((ViewGroup)wheel).getChildAt(0)).performClick();
    a.ok(selects[0]==1,"enlarged native centre button selects once");
  }
  static void run(AuditRunner a, Activity activity) throws Throwable {
    a.onUi(()->{
      enlargedEncoder(a);
      ViewGroup nav=(ViewGroup)a.field(activity,"nav");
      a.ok(nav.getChildCount()==2,"two primary sections, independent of firmware menu");
      a.ok(nav.getChildAt(0).isSelected(),"Remote is visibly and accessibly selected");
      String[] tabs={"Remote","Device"};
      for(int i=0;i<2;i++)a.ok(tabs[i].equals(nav.getChildAt(i).getContentDescription().toString()),"navigation icon has a spoken label: "+tabs[i]);
      a.ok(((TextView)a.field(activity,"screenText")).getText().toString().equals("STATUS"),"status row uses requested short title");
      a.ok(((TextView)a.field(activity,"usbStatusText")).getText().toString().equals("USB DISCONNECTED"),"no connected claim without open USB session");
      ViewGroup keys=(ViewGroup)a.field(activity,"remoteKeys");
      String[] labels={"PREV","NEXT","EXIT"};
      for(int i=0;i<3;i++)a.ok(labels[i].equals(((Button)keys.getChildAt(i)).getText().toString())&&!keys.getChildAt(i).isEnabled(),"distinct key label and disabled disconnected state: "+labels[i]);
      capture(a,activity,"fusion-disconnected");
      View wheel=(View)a.field(activity,"encoderControl");int[] wp=new int[2],kp=new int[2];wheel.getLocationOnScreen(wp);keys.getLocationOnScreen(kp);
      a.ok(kp[1]>=wp[1]+wheel.getHeight(),"three navigation keys are below rotary, never overlaid");
      a.ok(wheel.getWidth()>160*activity.getResources().getDisplayMetrics().density,"rotary enlarged beyond previous 160dp diameter");
      a.ok(Math.abs(wp[0]+wheel.getWidth()/2f-((View)a.field(activity,"root")).getWidth()/2f)<=2,"rotary centered in native viewport");
      nav.getChildAt(1).performClick();
      a.ok("Device".equals(a.field(activity,"active")),"new icon navigation opens Device");
      ((ViewGroup)a.field(activity,"nav")).getChildAt(0).performClick();
      a.ok("Remote".equals(a.field(activity,"active")),"return to Remote restores actual controls");
    });
  }
}
