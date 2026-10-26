package com.tabgrinder.brucetrigger.audit;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;
public final class AuditRunner extends Instrumentation {
 boolean animationOnly,filesOnly;
 int checks;StringBuilder log=new StringBuilder();
 void ok(boolean x,String label){checks++;if(!x)throw new AssertionError(label);log.append("PASS ").append(label).append('\n');if(checks%100==0){Bundle b=new Bundle();b.putString("progress",checks+" "+label);sendStatus(0,b);}}
 Class<?> targetClass(String n)throws Exception{return getTargetContext().getClassLoader().loadClass("com.tabgrinder.brucetrigger."+n);}
 Method method(Class<?> c,String n,Class<?>...args)throws Exception{Method m=c.getDeclaredMethod(n,args);m.setAccessible(true);return m;}
 Object construct(Class<?> c)throws Exception{Constructor<?> k=c.getDeclaredConstructor(Context.class);k.setAccessible(true);return k.newInstance(getTargetContext());}
 Object field(Object x,String n)throws Exception{Field f=x.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(x);}void set(Object x,String n,Object v)throws Exception{Field f=x.getClass().getDeclaredField(n);f.setAccessible(true);f.set(x,v);}boolean fails(Method m,Object x,Object...args)throws Exception{try{m.invoke(x,args);return false;}catch(InvocationTargetException e){return e.getCause() instanceof IOException;}}
 byte[] hex(String s){String[] p=s.split(" ");byte[] out=new byte[p.length];for(int i=0;i<p.length;i++)out[i]=(byte)Integer.parseInt(p[i],16);return out;}
 void touch(View host,float cx,float cy,float r,double angle,int action){long t=SystemClock.uptimeMillis();double a=Math.toRadians(angle);MotionEvent e=MotionEvent.obtain(t,t,action,cx+(float)Math.cos(a)*r,cy+(float)Math.sin(a)*r,0);host.dispatchTouchEvent(e);e.recycle();}
 void flaggedTouch(View host,float x,float y,int action,int flags){
  MotionEvent.PointerProperties pointer=new MotionEvent.PointerProperties();pointer.id=0;pointer.toolType=MotionEvent.TOOL_TYPE_FINGER;
  MotionEvent.PointerCoords coords=new MotionEvent.PointerCoords();coords.x=x;coords.y=y;coords.pressure=1;coords.size=1;
  long now=SystemClock.uptimeMillis();
  MotionEvent event=MotionEvent.obtain(now,now,action,1,new MotionEvent.PointerProperties[]{pointer},new MotionEvent.PointerCoords[]{coords},0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,flags);
  host.dispatchTouchEvent(event);event.recycle();
 }
 void flaggedTouch(Activity host,float x,float y,int action,int flags){
  MotionEvent.PointerProperties pointer=new MotionEvent.PointerProperties();pointer.id=0;pointer.toolType=MotionEvent.TOOL_TYPE_FINGER;
  MotionEvent.PointerCoords coords=new MotionEvent.PointerCoords();coords.x=x;coords.y=y;coords.pressure=1;coords.size=1;
  long now=SystemClock.uptimeMillis();
  MotionEvent event=MotionEvent.obtain(now,now,action,1,new MotionEvent.PointerProperties[]{pointer},new MotionEvent.PointerCoords[]{coords},0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,flags);
  try{host.dispatchTouchEvent(event);}finally{event.recycle();}
 }
 interface CheckedAction { void run() throws Throwable; }
 void onUi(CheckedAction action) throws Throwable {
  final Throwable[] failure={null};
  runOnMainSync(()->{try{action.run();}catch(Throwable t){failure[0]=t;}});
  waitForIdleSync();
  if(failure[0]!=null)throw failure[0];
 }
 byte[] envelope(long seq,byte[] draw){byte[] b=new byte[draw.length+11];b[0]=(byte)0xaa;b[1]=(byte)b.length;b[2]=98;for(int i=0;i<4;i++)b[3+i]=(byte)(seq>>(24-8*i));System.arraycopy(draw,0,b,7,draw.length);java.util.zip.CRC32 crc=new java.util.zip.CRC32();crc.update(b,0,b.length-4);long value=crc.getValue();for(int i=0;i<4;i++)b[b.length-4+i]=(byte)(value>>(24-8*i));return b;}
 @Override public void onCreate(Bundle args){super.onCreate(args);animationOnly=args!=null&&"1".equals(args.getString("animationOnly"));filesOnly=args!=null&&"1".equals(args.getString("filesOnly"));start();}
 @Override public void onStart(){
  final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();Thread.setDefaultUncaughtExceptionHandler((thread,error)->{StringWriter w=new StringWriter();error.printStackTrace(new PrintWriter(w));Bundle b=new Bundle();b.putString("uncaught_audit_error",w.toString());sendStatus(-1,b);if(previous!=null)previous.uncaughtException(thread,error);});
  Bundle result=new Bundle();try{
  if(animationOnly||filesOnly){
    Activity animation=startActivitySync(new Intent().setClassName("com.tabgrinder.brucetrigger","com.tabgrinder.brucetrigger.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
    if(filesOnly)FileInfoFlowAudit.run(this,animation);else GameAnimationAudit.run(this,animation);runOnMainSync(animation::finish);
    result.putString("status","PASS");result.putInt("checks",checks);result.putString("details",log.toString());finish(Activity.RESULT_OK,result);return;
  }
  SavedNotesAudit.run(this);
  Context ctx=getTargetContext();Class<?> sc=targetClass("SessionStore");Object store=construct(sc);Method read=method(sc,"read",File.class),delete=method(sc,"delete",File.class);
  final Throwable[] error={null};runOnMainSync(()->{try{Class<?> mc=targetClass("MirrorView");View view=(View)construct(mc);Method apply=method(mc,"apply",byte[].class),last=method(mc,"lastFrame");ok(Boolean.TRUE.equals(apply.invoke(view,(Object)hex("AA 08 63 01 40 00 AA 01"))),"SCREEN_INFO accepted in native renderer");ok(((Long)last.invoke(view))==0L,"metadata alone is not a fresh drawing");ok(Boolean.TRUE.equals(apply.invoke(view,(Object)hex("AA 05 00 F8 00"))),"native FILLSCREEN applies without exception");view.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(170,View.MeasureSpec.EXACTLY));view.layout(0,0,320,170);Bitmap output=Bitmap.createBitmap(320,170,Bitmap.Config.ARGB_8888);view.draw(new Canvas(output));ok(output.getPixel(160,85)==Color.RED,"native Canvas reproduces RGB565 red fill");long fresh=(Long)last.invoke(view);ok(fresh>0,"rendered draw records freshness");ok(Boolean.FALSE.equals(apply.invoke(view,(Object)hex("AA 08 63 FF FF FF FF 01"))),"native renderer rejects oversized geometry");ok(((Long)last.invoke(view))==fresh,"invalid frame does not renew freshness");ok(Boolean.TRUE.equals(apply.invoke(view,(Object)hex("AA 08 63 00 AA 01 40 00"))),"native geometry change accepted");ok(((Long)last.invoke(view))==0L,"new empty geometry resets freshness");Method reset=method(mc,"reset"),rejected=method(mc,"rejected"),partial=method(mc,"partial");int rejectedBefore=(Integer)rejected.invoke(view);ok(Boolean.FALSE.equals(apply.invoke(view,(Object)hex("AA 13 0C 00 00 00 00 00 01 00 02 00 03 00 04 00 05 00 06"))),"invalid inner radius is rejected");ok((Integer)rejected.invoke(view)==rejectedBefore+1&&Boolean.TRUE.equals(partial.invoke(view)),"invalid primitive marks incomplete frame");ok(Boolean.FALSE.equals(apply.invoke(view,(Object)hex("AA 0C 12 00 00 00 00 00 00 00 00 00"))),"remote image primitive is rejected");reset.invoke(view);ok(Boolean.TRUE.equals(apply.invoke(view,(Object)hex("AA 08 63 01 40 00 AA 01"))),"fresh budget geometry accepted");Object productionBudget=field(view,"draws");ok(((Long)field(productionBudget,"window"))==1000L&&((Integer)field(productionBudget,"maxCount"))==8192&&((Integer)field(productionBudget,"maxBytes"))==8192,"production draw budget is 8192 operations per 1000 ms");/* Keep one logical window for the Canvas integration test on slow software CPUs. Exact reset/deadline behavior is checked with supplied timestamps in SafetyTest. */Constructor<?> budgetConstructor=productionBudget.getClass().getDeclaredConstructor(int.class,int.class,long.class);set(view,"draws",budgetConstructor.newInstance(512,512,60000L));byte[] fillRecord=hex("AA 05 00 F8 00");int accepted=0;for(int i=0;i<600;i++)if(Boolean.TRUE.equals(apply.invoke(view,(Object)fillRecord)))accepted++;ok(accepted==512,"native draw flood limited to 512 in one logical test window");ok((Integer)rejected.invoke(view)==88,"native draw flood records 88 rejections");reset.invoke(view);int geometryAccepted=0;for(int i=0;i<6;i++){byte[] frame=hex("AA 08 63 01 40 00 AA 01");frame[4]=(byte)(64+i);if(Boolean.TRUE.equals(apply.invoke(view,(Object)frame)))geometryAccepted++;}ok(geometryAccepted==4,"geometry churn limited to four allocations");}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw error[0];
  RecoveryPersistenceAudit.run(this);
  MirrorTextAudit.run(this);
  PowerDialogAudit.run(this);
  ThemeImageAudit.run(this);
  StockImageAudit.run(this);
  String exportId=UUID.randomUUID().toString();File exportRecord=new File(new File(ctx.getFilesDir(),"sessions"),exportId+".json");android.net.Uri pipeUri=android.net.Uri.parse("content://com.tabgrinder.brucetrigger.audit.pipe/blocked");ContentResolver resolver=ctx.getContentResolver();try{char[] exportText=new char[28000];Arrays.fill(exportText,'\u65e5');SavedNoteFixtures.write(ctx,exportId,"bounded export fixture",new String(exportText));Class<?> ac=targetClass("MainActivity");Method export=method(ac,"submitExport",ContentResolver.class,sc,File.class,android.net.Uri.class,java.lang.ref.WeakReference.class);java.lang.ref.WeakReference<Activity> noOwner=new java.lang.ref.WeakReference<>(null);ok(Boolean.TRUE.equals(export.invoke(null,resolver,store,exportRecord,pipeUri,noOwner)),"first export accepted by bounded real worker");long deadline=SystemClock.elapsedRealtime()+15000;while(!resolver.call(pipeUri,"status",null,null).getBoolean("blocked")&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(50);ok(resolver.call(pipeUri,"status",null,null).getBoolean("blocked"),"real ContentProvider pipe blocks export write");ok(Boolean.FALSE.equals(export.invoke(null,resolver,store,exportRecord,pipeUri,noOwner)),"second export rejected while worker blocked");resolver.call(pipeUri,"release",null,null);boolean acceptedAgain=false;deadline=SystemClock.elapsedRealtime()+10000;android.net.Uri failure=android.net.Uri.parse("content://com.tabgrinder.brucetrigger.audit.pipe/failure");while(SystemClock.elapsedRealtime()<deadline){if(Boolean.TRUE.equals(export.invoke(null,resolver,store,exportRecord,failure,noOwner))){acceptedAgain=true;break;}Thread.sleep(50);}ok(acceptedAgain,"bounded export worker reusable after pipe failure");ok(((JSONObject)read.invoke(store,exportRecord)).getString("notes").length()==28000,"provider failure preserves saved local record");}finally{resolver.call(pipeUri,"release",null,null);delete.invoke(store,exportRecord);}
  final Throwable[] wheelError={null};runOnMainSync(()->{try{
 Class<?> ec=targetClass("EncoderControl"),stepType=targetClass("EncoderControl$Step");final int[] steps={0},clicks={0};final boolean[] accept={true};Object callback=java.lang.reflect.Proxy.newProxyInstance(stepType.getClassLoader(),new Class<?>[]{stepType},(proxy,m,a)->{if(m.getName().equals("turn")){if(!accept[0])return false;steps[0]+=(Integer)a[0];return true;}return null;});Constructor<?> constructor=ec.getDeclaredConstructor(Context.class,Runnable.class,stepType);constructor.setAccessible(true);View wheel=(View)constructor.newInstance(getTargetContext(),(Runnable)()->clicks[0]++,callback);android.widget.FrameLayout host=new android.widget.FrameLayout(getTargetContext());host.addView(wheel);float density=getTargetContext().getResources().getDisplayMetrics().density;int w=(int)(200*density),h=(int)(160*density);host.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));host.layout(0,0,w,h);wheel.layout(0,0,w,h);long time=SystemClock.uptimeMillis();float cx=w/2f,cy=h/2f,radius=72*density;MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,cx+radius,cy,0);host.dispatchTouchEvent(down);down.recycle();double angle=Math.toRadians(20);MotionEvent move=MotionEvent.obtain(time,time+20,MotionEvent.ACTION_MOVE,cx+(float)Math.cos(angle)*radius,cy+(float)Math.sin(angle)*radius,0);host.dispatchTouchEvent(move);move.recycle();MotionEvent up=MotionEvent.obtain(time,time+40,MotionEvent.ACTION_UP,cx+(float)Math.cos(angle)*radius,cy+(float)Math.sin(angle)*radius,0);host.dispatchTouchEvent(up);up.recycle();ok(steps[0]==1,"native clockwise ring touch emits one next input");ok(clicks[0]==0,"ring drag never selects centre");ok(wheel.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,null),"rotary accessible previous action accepted");ok(steps[0]==0,"accessible previous emits one inverse step");android.widget.Button centre=(android.widget.Button)((android.view.ViewGroup)wheel).getChildAt(0);centre.performClick();ok(clicks[0]==1,"centre select remains native click");
 method(ec,"setAvailable",boolean.class).invoke(wheel,false);
 ok(!centre.isEnabled()&&!wheel.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,null),"disabled rotary blocks centre and accessibility input");
 method(ec,"setAvailable",boolean.class).invoke(wheel,true);
 ok(centre.isEnabled(),"rotary re-enables after transport verification");
 int baseline=steps[0];touch(host,cx,cy,72*density,0,MotionEvent.ACTION_DOWN);touch(host,cx,cy,100*density,20,MotionEvent.ACTION_MOVE);ok(steps[0]==baseline+1,"radial finger drift keeps rotary gesture active");touch(host,cx,cy,14*density,80,MotionEvent.ACTION_MOVE);touch(host,cx,cy,72*density,90,MotionEvent.ACTION_MOVE);ok(steps[0]==baseline+1,"crossing centre rebases without jump or selection");touch(host,cx,cy,72*density,110,MotionEvent.ACTION_MOVE);ok(steps[0]==baseline+2,"rotation resumes after centre drift");touch(host,cx,cy,72*density,110,MotionEvent.ACTION_UP);ok(clicks[0]==1,"centre crossing during drag never clicks Select");
 accept[0]=false;baseline=steps[0];touch(host,cx,cy,72*density,0,MotionEvent.ACTION_DOWN);touch(host,cx,cy,72*density,20,MotionEvent.ACTION_MOVE);ok((Integer)field(wheel,"pendingSteps")==1,"busy transport retains one bounded fresh detent");accept[0]=true;method(ec,"flushPending").invoke(wheel);ok(steps[0]==baseline+1,"fresh detent dispatches once when transport becomes idle");touch(host,cx,cy,72*density,20,MotionEvent.ACTION_UP);
 accept[0]=false;baseline=steps[0];touch(host,cx,cy,72*density,0,MotionEvent.ACTION_DOWN);touch(host,cx,cy,72*density,60,MotionEvent.ACTION_MOVE);ok((Integer)field(wheel,"pendingSteps")==2,"fast rotation bounds pending detents at two");touch(host,cx,cy,72*density,60,MotionEvent.ACTION_UP);accept[0]=true;method(ec,"flushPending").invoke(wheel);ok(steps[0]==baseline&&(Integer)field(wheel,"pendingSteps")==0,"release cancels pending inputs without catch-up");
 accept[0]=false;touch(host,cx,cy,72*density,0,MotionEvent.ACTION_DOWN);touch(host,cx,cy,72*density,30,MotionEvent.ACTION_MOVE);wheel.onWindowFocusChanged(false);ok((Integer)field(wheel,"pendingSteps")==0&&!((Boolean)field(wheel,"dragging")),"focus loss cancels held rotary input");

 for(int flags:new int[]{MotionEvent.FLAG_WINDOW_IS_OBSCURED,2}){
  accept[0]=false;baseline=steps[0];
  touch(host,cx,cy,72*density,0,MotionEvent.ACTION_DOWN);touch(host,cx,cy,72*density,20,MotionEvent.ACTION_MOVE);
  ok((Integer)field(wheel,"pendingSteps")==1,"covered-input fixture has a pending rotary detent");
  flaggedTouch(host,cx+72*density,cy,MotionEvent.ACTION_MOVE,flags);
  ok((Integer)field(wheel,"pendingSteps")==0&&!((Boolean)field(wheel,"dragging")),"obscured gesture cancels pending rotary input (flags="+flags+")");
  accept[0]=true;method(ec,"flushPending").invoke(wheel);
  ok(steps[0]==baseline,"covered touch cannot release a deferred rotary input");
  touch(host,cx,cy,72*density,20,MotionEvent.ACTION_UP);
  int beforeClicks=clicks[0];flaggedTouch(host,cx,cy,MotionEvent.ACTION_DOWN,flags);flaggedTouch(host,cx,cy,MotionEvent.ACTION_UP,flags);
  ok(clicks[0]==beforeClicks,"covered centre tap cannot select (flags="+flags+")");
 }
 accept[0]=false;baseline=steps[0];touch(host,cx,cy,72*density,0,MotionEvent.ACTION_DOWN);touch(host,cx,cy,72*density,20,MotionEvent.ACTION_MOVE);
 ok((Integer)field(wheel,"pendingSteps")==1,"Android cancellation fixture has pending input");
 wheel.cancelPendingInputEvents();accept[0]=true;method(ec,"flushPending").invoke(wheel);
 ok((Integer)field(wheel,"pendingSteps")==0&&!((Boolean)field(wheel,"dragging"))&&steps[0]==baseline,"Android cancelPendingInputEvents cancels the custom rotary queue");
 touch(host,cx,cy,72*density,20,MotionEvent.ACTION_UP);

 }catch(Throwable t){wheelError[0]=t;}});if(wheelError[0]!=null)throw wheelError[0];
 Intent launch=new Intent().setClassName("com.tabgrinder.brucetrigger","com.tabgrinder.brucetrigger.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
 Activity activity=startActivitySync(launch);
 waitForIdleSync();
 ok(activity!=null&&!activity.isFinishing(),"MainActivity launch under instrumentation");
 final Throwable[] activityError={null};
 FusionUiAudit.run(this,activity);
 ReleaseNavigationAudit.run(this,activity);
 UsbUiAudit.run(this,activity);
 MenuViewAudit.run(this,activity);
 FilesLaunchAudit.run(this,activity);
 FileInfoFlowAudit.run(this,activity);
 FilesSpritesAudit.run(this);
 FilesCatalogAudit.run(this);
 WaitingGameAudit.run(this,activity);
 NativeGameAudit.run(this,activity);
 MegalodonRenderAudit.run(this);
 GameMirrorAudit.run(this,activity);
 GameAnimationAudit.run(this,activity);
 GameInputAudit.run(this,activity);
 GameLifecycleAudit.run(this,activity);
 GamePresentationAudit.run(this,activity);
 onUi(()->{
  ((ViewGroup)field(activity,"nav")).getChildAt(1).performClick();
  method(activity.getClass(),"openSection",String.class).invoke(activity,"Diagnostics");
  method(activity.getClass(),"help").invoke(activity);
 });
 final java.util.List<AlertDialog> previousDialogs=new java.util.ArrayList<>();
 onUi(()->previousDialogs.addAll((java.util.Set<AlertDialog>)field(activity,"ownedDialogs")));
 ActivityMonitor monitor=addMonitor("com.tabgrinder.brucetrigger.MainActivity",null,false);runOnMainSync(()->activity.recreate());Activity recreated=monitor.waitForActivityWithTimeout(45000);removeMonitor(monitor);ok(recreated!=null&&recreated!=activity,"Activity really recreated");boolean dialogsClosed=true;for(AlertDialog dialog:previousDialogs)if(dialog.isShowing())dialogsClosed=false;ok(dialogsClosed,"all Activity dialogs close during recreation without leaked windows");waitForIdleSync();runOnMainSync(()->{try{ok("Diagnostics".equals(field(recreated,"active")),"secondary page survives recreation");ok("Device".equals(field(recreated,"mainSection")),"secondary page keeps its primary return destination");recreated.onBackPressed();ok("Device".equals(field(recreated,"active")),"Back after recreation returns to Device");recreated.finish();}catch(Throwable e){activityError[0]=e;}});if(activityError[0]!=null)throw activityError[0];result.putString("status","PASS");result.putInt("checks",checks);result.putString("details",log.toString());finish(Activity.RESULT_OK,result);
 }catch(Throwable e){StringWriter trace=new StringWriter();e.printStackTrace(new PrintWriter(trace));result.putString("status","FAIL");result.putInt("checks",checks);result.putString("details",log.toString());result.putString("failure",trace.toString());finish(Activity.RESULT_CANCELED,result);}}
}
