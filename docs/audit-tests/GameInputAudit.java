package com.tabgrinder.brucetrigger.audit;

import android.app.Activity;
import android.os.SystemClock;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicReference;

/** Real production touch widgets, MainActivity queue and USB session; endpoint is doubled. */
final class GameInputAudit {
  final AuditRunner a;final Activity activity;
  Object usb,session,run,queue;String prefix;View select,prev,next,exit;int sequence;
  GameInputAudit(AuditRunner a,Activity activity){this.a=a;this.activity=activity;}
  void touch(View v,int action)throws Exception {
    long t=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(t,t,action,v.getWidth()/2f,v.getHeight()/2f,0);
    v.dispatchTouchEvent(e);e.recycle();
  }
  int queued()throws Exception{return (Integer)a.method(queue.getClass(),"size").invoke(queue);}
  boolean outbound()throws Exception{return ((AtomicReference<?>)a.field(run,"outbound")).get()!=null;}
  void write(String expected)throws Exception {
    a.ok(new String(UsbUiAudit.takeWrite(a,run,session),"US-ASCII").equals(expected+"\n"),"actual input USB write is exactly "+expected);
  }
  void ack()throws Exception {UsbUiAudit.receive(a,session,"# ");a.set(session,"nextAt",0L);}
  void fresh()throws Exception {
    StringBuilder wire=new StringBuilder();
    for(String line:GameDrawFixtures.wire().split("\n"))if((line.startsWith("R|"+sequence+"|")||line.startsWith("D|"+sequence+"|")))wire.append(prefix).append(line).append('\n');
    a.ok(wire.length()>0,"input test uses a complete current engine graphics transaction "+sequence++);
    UsbUiAudit.receive(a,session,wire.toString());a.method(activity.getClass(),"data",byte[].class).invoke(activity,(Object)new byte[0]);
    a.method(activity.getClass(),"updateScreenStatus").invoke(activity);
    a.ok(Boolean.TRUE.equals(a.method(activity.getClass(),"gameInputMode").invoke(activity)),"validated live game admits controls even during a pending CLI command");
  }
  void setup()throws Exception {
    Class<?> ac=activity.getClass();usb=a.field(activity,"usb");session=a.field(usb,"session");
    UsbUiAudit.restartedFixture(a,usb);
    Class<?> rc=a.targetClass("UsbController$Run"),profile=a.targetClass("UsbProfile");
    Constructor<?> ctor=rc.getDeclaredConstructor(int.class,android.hardware.usb.UsbDevice.class,profile);ctor.setAccessible(true);
    run=ctor.newInstance((Integer)a.method(usb.getClass(),"sessionToken").invoke(usb),null,null);
    a.set(usb,"current",run);Field owner=usb.getClass().getDeclaredField("owner");owner.setAccessible(true);owner.set(null,new java.lang.ref.WeakReference<Object>(usb));
    a.method(session.getClass(),"open",long.class).invoke(session,SystemClock.elapsedRealtime()-1000);
    a.set(session,"verified",true);a.set(session,"version","1.16.1");a.set(session,"board","Lilygo T-Embed CC1101");
    a.set(activity,"foreground",true);a.set(activity,"resumed",true);a.set(activity,"menuMode",false);a.set(activity,"connectStep",0);a.set(activity,"active","Remote");a.method(ac,"render").invoke(activity);
    String template;
    try(InputStream in=activity.getResources().openRawResource(activity.getResources().getIdentifier("files_bridge","raw",activity.getPackageName()))){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[2048];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);template=new String(out.toByteArray(),"US-ASCII");}
    a.ok(Boolean.TRUE.equals(a.method(usb.getClass(),"launchFilesGame",String.class,String.class).invoke(usb,template,"/scripts/Games/flappybird.js")),"input test launches actual fixed Files bridge");
    a.set(activity,"gamePresentation",true);a.set(activity,"mirrorRequested",false);a.method(a.field(activity,"mirror").getClass(),"reset").invoke(a.field(activity,"mirror"));
    UsbUiAudit.takeWrite(a,run,session);UsbUiAudit.receive(a,session,"Reading input data from serial buffer until EOF\n");
    byte[] body=(byte[])a.method(session.getClass(),"takeScriptBody").invoke(session);
    java.util.regex.Matcher m=java.util.regex.Pattern.compile("BT_([a-f0-9]{16})_GAME").matcher(new String(body,"US-ASCII"));a.ok(m.find(),"input session nonce from actual uploaded wrapper");prefix="BT_"+m.group(1)+"_GAME|";
    a.method(session.getClass(),"written",int.class,int.class,long.class).invoke(session,body.length,body.length,SystemClock.elapsedRealtime());
    UsbUiAudit.receive(a,session,"# Entering interpreter...\n"+prefix+"SEARCH\n"+prefix+"ON\n");
    a.set(session,"nextAt",0L);queue=a.field(activity,"gameInputs");a.method(queue.getClass(),"reset").invoke(queue);
    select=((ViewGroup)a.field(activity,"encoderControl")).getChildAt(0);
    ViewGroup keys=(ViewGroup)a.field(activity,"remoteKeys");prev=keys.getChildAt(0);next=keys.getChildAt(1);exit=keys.getChildAt(2);
    fresh();
  }
  static void run(AuditRunner a,Activity activity)throws Throwable {
    GameInputAudit t=new GameInputAudit(a,activity);a.onUi(t::setup);
    GameAnimationAudit.requireFocusedWindow(a,activity);
    a.onUi(()->{
      t.fresh();t.touch(t.select,MotionEvent.ACTION_DOWN);t.write("nav select");
      a.ok(t.queued()==0,"SELECT is submitted on touch DOWN, before release");
      t.touch(t.select,MotionEvent.ACTION_UP);a.ok(!t.outbound(),"release does not generate a duplicate SELECT");
      t.touch(t.select,MotionEvent.ACTION_DOWN);t.touch(t.select,MotionEvent.ACTION_UP);
      a.ok(t.queued()==1&&!t.outbound(),"second tap survives the first outstanding command");
      t.ack();a.method(activity.getClass(),"drainGameInput").invoke(activity);t.write("nav select");t.ack();
      a.ok(t.queued()==0,"queued second tap is submitted once after prompt");
      t.touch(t.next,MotionEvent.ACTION_DOWN);t.write("nav next");t.ack();
    });
    Thread.sleep(165);
    a.onUi(()->{
      a.ok(t.outbound(),"held NEXT posts a real timed repeat without another touch");t.write("nav next");
      t.touch(t.next,MotionEvent.ACTION_UP);a.ok(t.queued()==0,"releasing NEXT discards unsent hold samples");t.ack();
    });
    Thread.sleep(180);
    a.onUi(()->{
      a.ok(!t.outbound()&&t.queued()==0,"no delayed NEXT repeats after release");t.fresh();
      t.touch(t.prev,MotionEvent.ACTION_DOWN);t.write("nav prev");t.ack();
    });
    Thread.sleep(165);
    a.onUi(()->{
      a.ok(t.outbound(),"held PREV repeats too");t.write("nav prev");t.touch(t.prev,MotionEvent.ACTION_CANCEL);t.ack();
      t.fresh();t.touch(t.select,MotionEvent.ACTION_DOWN);t.write("nav select");t.ack();
    });
    Thread.sleep(180);
    a.onUi(()->{
      a.ok(!t.outbound(),"holding SELECT never auto-repeats");t.touch(t.select,MotionEvent.ACTION_UP);
      t.touch(t.exit,MotionEvent.ACTION_DOWN);t.write("nav esc");t.ack();
    });
    Thread.sleep(180);
    a.onUi(()->{
      a.ok(!t.outbound(),"holding EXIT never auto-repeats");t.touch(t.exit,MotionEvent.ACTION_UP);t.fresh();
      for(int flags:new int[]{MotionEvent.FLAG_WINDOW_IS_OBSCURED,2}) {
        a.flaggedTouch(t.next,t.next.getWidth()/2f,t.next.getHeight()/2f,MotionEvent.ACTION_DOWN,flags);
        a.flaggedTouch(t.next,t.next.getWidth()/2f,t.next.getHeight()/2f,MotionEvent.ACTION_UP,flags);
        a.ok(!t.outbound()&&t.queued()==0,"covered game button cannot inject input, flags="+flags);
      }
      for(int flags:new int[]{MotionEvent.FLAG_WINDOW_IS_OBSCURED,2}) {
        t.touch(t.next,MotionEvent.ACTION_DOWN);
        a.ok(t.outbound()&&Boolean.TRUE.equals(a.field(t.next,"holding")),"held input exists before Activity-level rejection");
        a.flaggedTouch(activity,0,0,MotionEvent.ACTION_MOVE,flags);
        a.ok(!t.outbound()&&t.queued()==0,"Activity rejects a covered touch and cancels unsent input, flags="+flags);
        a.ok(Boolean.FALSE.equals(a.field(t.next,"holding")),"Activity-level rejection reaches the held widget");
        t.touch(t.next,MotionEvent.ACTION_UP);
        a.ok(!t.outbound(),"release after Activity-level cancellation cannot become a normal click");
      }
      t.touch(t.select,MotionEvent.ACTION_DOWN);t.write("nav select");t.touch(t.select,MotionEvent.ACTION_UP);
      t.touch(t.next,MotionEvent.ACTION_DOWN);a.ok(t.queued()==1,"unsent direction exists for focus cancellation");
      activity.onWindowFocusChanged(false);t.next.onWindowFocusChanged(false);
      a.ok(t.queued()==0,"focus loss clears unsent game input");t.touch(t.next,MotionEvent.ACTION_UP);t.ack();activity.onWindowFocusChanged(true);t.next.onWindowFocusChanged(true);
      a.ok(!t.outbound(),"UP after a cancelled game gesture cannot become a normal click");
      t.fresh();t.touch(t.select,MotionEvent.ACTION_DOWN);t.write("nav select");t.touch(t.select,MotionEvent.ACTION_UP);
      a.method(activity.getClass(),"turnEncoder",int.class).invoke(activity,1);
      a.ok(t.queued()==1,"actual rotary route preserves a detent while CLI is busy");
      t.ack();a.method(activity.getClass(),"drainGameInput").invoke(activity);t.write("nav next");t.ack();
      t.touch(t.select,MotionEvent.ACTION_DOWN);t.write("nav select");t.touch(t.select,MotionEvent.ACTION_UP);
      t.touch(t.next,MotionEvent.ACTION_DOWN);t.touch(t.next,MotionEvent.ACTION_UP);a.ok(t.queued()==1,"pending input before native dialog");
      UsbUiAudit.receive(a,t.session,t.prefix+"UI_ON\n");a.method(activity.getClass(),"data",byte[].class).invoke(activity,(Object)new byte[0]);
      a.ok(t.queued()==0,"native dialog transition cancels pending game movement");t.ack();
      UsbUiAudit.receive(a,t.session,t.prefix+"UI_OFF\n"+t.prefix+"DONE\nExiting interpreter...\n");
      a.method(t.usb.getClass(),"disconnect",String.class).invoke(t.usb,"Input audit complete");
      a.ok(t.queued()==0,"ended session has no deferred input");
    });
  }
}
