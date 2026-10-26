package com.tabgrinder.brucetrigger.audit;

import android.app.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Continuous eight-read pipeline, production scheduling and real Android window screenshots. */
final class GameAnimationAudit {
  static void requireFocusedWindow(AuditRunner a, Activity activity) throws Throwable {
    // Software-only emulator boot can show a System UI ANR. Dismiss only that
    // identified OS dialog; never dismiss a Bruce trigger crash or ANR.
    AccessibilityNodeInfo root=a.getUiAutomation().getRootInActiveWindow();
    if(root!=null){
      List<AccessibilityNodeInfo> system=root.findAccessibilityNodeInfosByText("System UI");
      boolean systemAnr=false;
      for(AccessibilityNodeInfo node:system)if(node.getText()!=null&&node.getText().toString().contains("responding"))systemAnr=true;
      if(systemAnr) {
        List<AccessibilityNodeInfo> wait=root.findAccessibilityNodeInfosByText("Wait");
        a.ok(wait.size()==1&&wait.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK),"dismissed identified emulator System UI ANR; not the target app");
        Thread.sleep(300);
      }
    }
    long deadline=SystemClock.elapsedRealtime()+5000;
    boolean[] focused={false};
    do {
      a.onUi(()->{focused[0]=activity.hasWindowFocus();});
      if(focused[0])break;
      Thread.sleep(100);
    } while(SystemClock.elapsedRealtime()<deadline);
    if(!focused[0]) {
      a.log.append("Active accessibility root: ").append(root).append('\n');
      try(android.os.ParcelFileDescriptor fd=a.getUiAutomation().executeShellCommand("dumpsys window windows");
          BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(fd.getFileDescriptor())))) {
        String line;while((line=reader.readLine())!=null)
          if(line.contains("mCurrentFocus")||line.contains("mFocusedApp")||line.contains("Application Error")||line.contains("Not Responding"))a.log.append(line).append('\n');
      }
    }
    a.ok(focused[0],"actual game window has focus and no covering dialog");
  }

  static void burst(AuditRunner a, Activity activity, Object serial, byte[] bytes) throws Throwable {
    Handler handler=new Handler(Looper.getMainLooper());
    AtomicInteger cursor=new AtomicInteger(),workers=new AtomicInteger(8);
    AtomicReference<Throwable> error=new AtomicReference<>();
    CountDownLatch ended=new CountDownLatch(1);
    Method data=a.method(activity.getClass(),"data",byte[].class);
    Method receive=a.method(serial.getClass(),"receive",byte[].class,long.class);
    Object usb=a.field(activity,"usb"),run=a.field(usb,"current");
    Method production=null;
    try{production=a.method(usb.getClass(),"receiveRead",run.getClass(),byte[].class);}catch(NoSuchMethodException priorVersion){}
    final Method dispatch=production;
    Runnable reader=new Runnable(){public void run(){
      if(error.get()!=null){if(workers.decrementAndGet()==0)ended.countDown();return;}
      int offset=cursor.getAndAdd(4096);
      if(offset>=bytes.length){if(workers.decrementAndGet()==0)ended.countDown();return;}
      try {
        byte[] chunk=Arrays.copyOfRange(bytes,offset,Math.min(offset+4096,bytes.length));
        if(dispatch!=null)dispatch.invoke(usb,run,chunk);
        else {receive.invoke(serial,chunk,SystemClock.elapsedRealtime());data.invoke(activity,(Object)chunk);}
      } catch(Throwable t){error.compareAndSet(null,t);}
      // Retain eight pending read callbacks as the bounded real USB producer can.
      handler.post(this);
    }};
    a.runOnMainSync(()->{for(int i=0;i<8;i++)handler.post(reader);});
    if(!ended.await(30,TimeUnit.SECONDS))throw new AssertionError("Continuous USB fixture did not drain");
    a.waitForIdleSync();
    if(error.get()!=null)throw error.get();
  }

  static void run(AuditRunner a,Activity activity)throws Throwable {
    Object[] state=new Object[3];String[] prefix={null};
    a.onUi(()->{
      Class<?> ac=activity.getClass();Object usb=a.field(activity,"usb"),serial=a.field(usb,"session");
      Class<?> rc=a.targetClass("UsbController$Run"),profile=a.targetClass("UsbProfile");
      Constructor<?> ctor=rc.getDeclaredConstructor(int.class,android.hardware.usb.UsbDevice.class,profile);ctor.setAccessible(true);
      Object run=ctor.newInstance((Integer)a.method(usb.getClass(),"sessionToken").invoke(usb),null,null);
      a.set(usb,"current",run);Field owner=usb.getClass().getDeclaredField("owner");owner.setAccessible(true);owner.set(null,new java.lang.ref.WeakReference<Object>(usb));
      a.method(serial.getClass(),"open",long.class).invoke(serial,SystemClock.elapsedRealtime()-1000);
      a.set(serial,"verified",true);a.set(serial,"version","1.16.1");a.set(serial,"board","Lilygo T-Embed CC1101");
      a.set(activity,"foreground",true);a.set(activity,"resumed",true);a.set(activity,"menuMode",false);a.set(activity,"active","Remote");a.method(ac,"render").invoke(activity);
      String template;
      try(InputStream in=activity.getResources().openRawResource(activity.getResources().getIdentifier("files_bridge","raw",activity.getPackageName()))){
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1)out.write(b,0,n);template=new String(out.toByteArray(),"US-ASCII");
      }
      a.ok(Boolean.TRUE.equals(a.method(usb.getClass(),"launchFilesGame",String.class,String.class).invoke(usb,template,"/scripts/Games/flappybird.js")),"live animation uses the production Files bridge launch");
      a.set(activity,"gamePresentation",true);a.set(activity,"mirrorRequested",false);
      a.method(a.field(activity,"mirror").getClass(),"reset").invoke(a.field(activity,"mirror"));
      UsbUiAudit.takeWrite(a,run,serial);UsbUiAudit.receive(a,serial,"Reading input data from serial buffer until EOF\n");
      byte[] body=(byte[])a.method(serial.getClass(),"takeScriptBody").invoke(serial);
      a.ok(body!=null,"live animation upload owns the script handshake");
      java.util.regex.Matcher match=java.util.regex.Pattern.compile("BT_([a-f0-9]{16})_GAME").matcher(new String(body,"US-ASCII"));a.ok(match.find(),"live animation nonce extracted from actual upload");prefix[0]="BT_"+match.group(1)+"_GAME|";
      a.method(serial.getClass(),"written",int.class,int.class,long.class).invoke(serial,body.length,body.length,SystemClock.elapsedRealtime());
      UsbUiAudit.receive(a,serial,"# Entering interpreter...\n"+prefix[0]+"SEARCH\n"+prefix[0]+"ON\n");
      state[0]=usb;state[1]=serial;state[2]=run;
    });
    List<String> frames=new ArrayList<>();StringBuilder frame=null;long frameSequence=-1;
    for(String line:GameDrawFixtures.wire().split("\n")) {
      if(!(line.startsWith("R|")||line.startsWith("D|")))continue;
      String[] fields=line.split("\\|",-1);long sequence=Long.parseLong(fields[1]);
      if(sequence!=frameSequence){if(frame!=null)frames.add(frame.toString());frame=new StringBuilder();frameSequence=sequence;}
      int shard=Integer.parseInt(fields[5]);
      // Exercise actual Android animation with two missing data shards in three
      // transactions, including 24. Original draw bytes remain the expected image.
      if((sequence==16||sequence==24||sequence==60)&&(shard==0||shard==1))continue;
      frame.append(prefix[0]).append(line).append('\n');
    }
    if(frame!=null)frames.add(frame.toString());
    a.ok(frames.size()==108,"live replay contains all original engine-produced frames");
    Set<Integer> fingerprints=new HashSet<>();int begin=0;
    for(int end:new int[]{24,48,72,108}){
      StringBuilder wire=new StringBuilder();for(int i=begin;i<end;i++)wire.append(frames.get(i));
      burst(a,activity,state[1],wire.toString().getBytes("US-ASCII"));
      final int expected=end;
      a.onUi(()->{
        Object game=a.field(state[1],"game"),screen=a.field(game,"screen");
        String failure=(String)a.field(screen,"failure");
        a.ok("None".equals(failure),"continuous USB must not starve game rendering: "+failure);
        a.ok((Long)a.field(screen,"frames")==expected,"continuous stream validates frames through "+expected);
        a.ok(a.method(screen.getClass(),"takeFrame").invoke(screen)==null,"production scheduler drained complete frames without a direct audit render call");
      });
      // Wait for the actual window traversal, not a software View.draw fixture.
      Thread.sleep(180);
      requireFocusedWindow(a,activity);
      Bitmap screenshot=a.getUiAutomation().takeScreenshot();a.ok(screenshot!=null,"actual Android window screenshot available");
      int[] bounds=new int[4];
      a.onUi(()->{View mirror=(View)a.field(activity,"mirror");int[] xy=new int[2];mirror.getLocationOnScreen(xy);bounds[0]=xy[0];bounds[1]=xy[1];bounds[2]=mirror.getWidth();bounds[3]=mirror.getHeight();});
      Bitmap crop=Bitmap.createBitmap(screenshot,bounds[0],bounds[1],bounds[2],bounds[3]);int[] pixels=new int[crop.getWidth()*crop.getHeight()];crop.getPixels(pixels,0,crop.getWidth(),0,0,crop.getWidth(),crop.getHeight());
      fingerprints.add(Arrays.hashCode(pixels));int colored=0;for(int pixel:pixels)if((pixel&0xffffff)!=0)colored++;
      a.ok(colored>pixels.length/4,"window displays game colors instead of a black mirror");
      try(FileOutputStream f=new FileOutputStream(new File(a.getTargetContext().getExternalFilesDir(null),"game-live-"+end+".png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,f);}crop.recycle();screenshot.recycle();
      begin=end;
    }
    a.ok(fingerprints.size()>=3,"actual displayed game image changes across animation stages");
    a.onUi(()->{
      Object mirror=a.field(activity,"mirror");
      Object recovered=a.field(a.field(a.field(state[1],"game"),"screen"),"recovery");
      a.ok((Long)a.field(recovered,"repairedShards")==6L,"actual Android receiver repairs all six erased graphics shards");

      // The final frame in the original engine transcript is an empty heartbeat.
      // It keeps input freshness but has no drawing batch to publish.
      a.ok((Long)a.method(mirror.getClass(),"gameFramesComposed").invoke(mirror)==107,"all 107 nonempty game frames composed without delta loss");
      a.ok((Long)a.method(mirror.getClass(),"presentedGameFrame").invoke(mirror)==107,"latest complete drawing frame reached actual View.onDraw");
      a.ok((Long)a.method(mirror.getClass(),"presentedUpdates").invoke(mirror)>=4,"multiple Android screen presentations observed");
      Object serial=state[1];UsbUiAudit.receive(a,serial,prefix[0]+"DONE\nExiting interpreter...\n");a.set(serial,"nextAt",0L);
      a.method(activity.getClass(),"data",byte[].class).invoke(activity,(Object)new byte[0]);
    });
    a.onUi(()->{
      a.ok(!Boolean.TRUE.equals(a.field(activity,"gamePresentation")),"actual completion event returns to menu through the scheduler");
      a.method(state[0].getClass(),"disconnect",String.class).invoke(state[0],"Animation audit completed");
      a.ok(!Boolean.TRUE.equals(a.field(activity,"gameRefreshPending")),"disconnect cancels the render signal");
    });
  }
}
