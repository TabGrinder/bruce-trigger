package com.tabgrinder.brucetrigger.audit;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Adler32;

/** Native Activity with synthetic USB responses; no physical USB claims. */
final class UsbUiAudit {
  // Independent synthetic-device cases must not inherit a deliberately
  // unacknowledged interpreter from the previous case. Exercise the production
  // disconnected restart acknowledgement, never overwrite its private fence.
  static void restartedFixture(AuditRunner a,Object usb)throws Exception {
    try {
      if(Boolean.TRUE.equals(a.method(usb.getClass(),"interpreterRecoveryRequired").invoke(usb))) {
        a.ok(!Boolean.TRUE.equals(a.method(usb.getClass(),"hasPendingConnection").invoke(usb)),"synthetic restart is acknowledged only after disconnection");
        a.method(usb.getClass(),"confirmInterpreterRestart").invoke(usb);
        a.ok(!Boolean.TRUE.equals(a.method(usb.getClass(),"interpreterRecoveryRequired").invoke(usb)),"explicit simulated device restart clears recovery fence for independent fixture");
      }
    } catch(NoSuchMethodException previousRelease) { /* historical negative baseline */ }
  }
  static byte[] bytes(String s)throws Exception{return s.getBytes("UTF-8");}
  static void receive(AuditRunner a,Object serial,String s)throws Exception {
    a.method(serial.getClass(),"receive",byte[].class,long.class).invoke(serial,bytes(s),SystemClock.elapsedRealtime());
  }
  static byte[] takeWrite(AuditRunner a,Object run,Object serial)throws Exception {
    byte[] data=(byte[])((AtomicReference<?>)a.field(run,"outbound")).getAndSet(null);
    a.ok(data!=null,"single expected USB write queued");
    a.method(serial.getClass(),"written",int.class,int.class,long.class).invoke(serial,data.length,data.length,SystemClock.elapsedRealtime());return data;
  }
  static String startScript(AuditRunner a,Object run,Object serial)throws Exception {
    takeWrite(a,run,serial);receive(a,serial,"Reading input data from serial buffer until EOF\n");
    byte[] body=(byte[])a.method(serial.getClass(),"takeScriptBody").invoke(serial);
    a.ok(body!=null&&body.length<4096,"bounded stock script released after input handshake");
    a.method(serial.getClass(),"written",int.class,int.class,long.class).invoke(serial,body.length,body.length,SystemClock.elapsedRealtime());
    String text=new String(body,"UTF-8");java.util.regex.Matcher m=java.util.regex.Pattern.compile("BT_([a-f0-9]{16})_").matcher(text);
    a.ok(m.find(),"script includes session-scoped nonce");return m.group(1);
  }
  static byte[] imageRecord(String path,int x,int y,int center,int fs)throws Exception {
    byte[] name=bytes(path),record=new byte[12+name.length];record[0]=(byte)170;record[1]=(byte)record.length;record[2]=18;
    record[3]=(byte)(x>>8);record[4]=(byte)x;record[5]=(byte)(y>>8);record[6]=(byte)y;record[8]=(byte)center;record[11]=(byte)fs;
    System.arraycopy(name,0,record,12,name.length);return record;
  }
  static void fixtureImage(AuditRunner a,Object serial,String nonce,byte[] data)throws Exception {
    fixtureImage(a,serial,nonce,data,"0:/Themes/fixture/wifi.png");
  }
  static void fixtureImage(AuditRunner a,Object serial,String nonce,byte[] data,String key)throws Exception {
    String prefix="BT_"+nonce+"_IMG|";receive(a,serial,prefix+"B|"+key.charAt(0)+"|"+key.substring(2)+"|"+data.length+"\n");
    for(int i=0;i<data.length;i+=128){StringBuilder hex=new StringBuilder();for(int j=i;j<Math.min(i+128,data.length);j++)hex.append(String.format(java.util.Locale.ROOT,"%02x",data[j]&255));receive(a,serial,prefix+"D|"+i+"|"+hex+"\n");}
    Adler32 sum=new Adler32();sum.update(data);receive(a,serial,prefix+"E|"+sum.getValue()+"\n"+prefix+"Z\nBT_"+nonce+"_THEME_DONE\nExiting interpreter...\n# ");
  }
  static void draw(AuditRunner a,Activity activity,Object serial,byte[] data)throws Exception {
    a.method(serial.getClass(),"receive",byte[].class,long.class).invoke(serial,data,SystemClock.elapsedRealtime());
    a.method(activity.getClass(),"data",byte[].class).invoke(activity,(Object)data);
  }
  static void run(AuditRunner a,Activity activity)throws Throwable {
    a.onUi(()->{
      Class<?> ac=activity.getClass();Object usb=a.field(activity,"usb"),serial=a.field(usb,"session");
      a.ok("CONNECT".equals(((Button)a.field(activity,"connectAction")).getText().toString()),"one primary action for detect, verify, images and mirror");
      android.content.pm.PackageInfo info=activity.getPackageManager().getPackageInfo(activity.getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS);
      a.ok(info.requestedPermissions==null||info.requestedPermissions.length==0,"installed app has no Bluetooth/location/Internet manifest permissions");
      boolean hasBle=false;try{a.targetClass("BleController");hasBle=true;}catch(ClassNotFoundException expected){}
      a.ok(!hasBle,"Bluetooth controller removed from installed DEX");
      a.ok(!((ViewGroup)a.field(activity,"encoderControl")).getChildAt(0).isEnabled(),"disconnected SELECT disabled");
      // A native USB Run with no endpoint: real UI/session methods, synthetic responses only.
      Class<?> runClass=a.targetClass("UsbController$Run"),profileClass=a.targetClass("UsbProfile");
      Constructor<?> rc=runClass.getDeclaredConstructor(int.class,android.hardware.usb.UsbDevice.class,profileClass);rc.setAccessible(true);
      int token=(Integer)a.method(usb.getClass(),"sessionToken").invoke(usb);Object run=rc.newInstance(token,null,null);a.set(usb,"current",run);
      Field owner=usb.getClass().getDeclaredField("owner");owner.setAccessible(true);owner.set(null,new java.lang.ref.WeakReference<Object>(usb));
      a.method(serial.getClass(),"open",long.class).invoke(serial,SystemClock.elapsedRealtime()-1000);
      a.set(activity,"resumed",true);a.set(activity,"foreground",true);
      ((Button)a.field(activity,"connectAction")).performClick();
      a.method(ac,"advanceConnect").invoke(activity);
      a.ok(new String(takeWrite(a,run,serial),"UTF-8").equals("info\n"),"single button sends only info before identity validation");
      a.ok(!((Button)a.field(activity,"connectAction")).isEnabled(),"connection button disabled during setup; no duplicate workflow");
      a.method(ac,"send",String.class).invoke(activity,"nav select");
      a.ok(((AtomicReference<?>)a.field(run,"outbound")).get()==null,"navigation cannot interleave with setup");
      receive(a,serial,"Bruce v1.16.1\nDevice: Lilygo T-Embed CC1101\n# ");a.set(serial,"nextAt",0L);a.method(ac,"advanceConnect").invoke(activity);
      a.ok((Integer)a.field(activity,"connectStep")==3,"verification automatically advances to image sync");
      String nonce=startScript(a,run,serial);
      a.ok(!Boolean.TRUE.equals(a.method(usb.getClass(),"hasCapture").invoke(usb)),"image download cannot enable device capture");
      Bitmap asset=Bitmap.createBitmap(16,10,Bitmap.Config.ARGB_8888);asset.eraseColor(Color.GREEN);
      ByteArrayOutputStream png=new ByteArrayOutputStream();asset.compress(Bitmap.CompressFormat.PNG,100,png);asset.recycle();
      fixtureImage(a,serial,nonce,png.toByteArray());a.set(serial,"nextAt",0L);a.method(ac,"advanceConnect").invoke(activity);
      a.ok((Integer)a.field(activity,"connectStep")==4,"verified complete images automatically advance to capture");
      nonce=startScript(a,run,serial);
      byte[] geo=a.hex("AA 08 63 01 40 00 AA 01"),clear=a.hex("AA 05 00 00 00");
      draw(a,activity,serial,geo);a.ok(!Boolean.TRUE.equals(a.method(ac,"screenUsable").invoke(activity)),"geometry alone cannot authorize SELECT");
      receive(a,serial,"BT_"+nonce+"_ON\nExiting interpreter...\n# ");a.set(serial,"nextAt",0L);a.method(ac,"advanceConnect").invoke(activity);
      a.ok((Integer)a.field(activity,"connectStep")==0,"single-button workflow ends after capture confirmation");
      a.ok("DISCONNECT".equals(((Button)a.field(activity,"connectAction")).getText().toString()),"same button becomes Disconnect");
      draw(a,activity,serial,clear);draw(a,activity,serial,imageRecord("/Themes/fixture/wifi.png",5,7,0,0));
      a.method(ac,"updateScreenStatus").invoke(activity);
      Object mirror=a.field(activity,"mirror");Bitmap panel=(Bitmap)a.field(mirror,"bitmap");
      a.ok(panel.getPixel(5,7)==Color.GREEN&&panel.getPixel(20,16)==Color.GREEN,"transferred PNG rendered at exact stock x/y coordinates");
      a.ok(panel.getPixel(4,7)==Color.BLACK&&panel.getPixel(21,16)==Color.BLACK,"image bounds do not paint unrelated pixels");
      a.ok((Long)a.method(mirror.getClass(),"renderedImages").invoke(mirror)==1,"image rendering recorded independently of received paths");
      a.ok(!Boolean.TRUE.equals(a.method(ac,"screenUsable").invoke(activity)) && (Integer)a.field(activity,"connectStep")==7 && Boolean.TRUE.equals(a.field(activity,"menuRequired")),"first native image schedules root verification and blocks SELECT before another image can use defective logger");
      a.ok(((TextView)a.field(activity,"inputText")).getText().toString().contains("Checking"),"native image transition is explicitly pending");
      // Remaining assertions isolate old renderer/interpreter diagnostics; a separate
      // MenuViewAudit exercises the new presentation without native capture.
      a.method(ac,"cancelConnect").invoke(activity);a.set(activity,"menuRequired",false);
      receive(a,serial,"Entering interpreter...\n");a.method(ac,"updateScreenStatus").invoke(activity);
      a.ok(!Boolean.TRUE.equals(a.method(ac,"screenUsable").invoke(activity)),"external JS graphics cannot authorize SELECT from an incomplete mirror");
      a.ok(((TextView)a.field(activity,"screenText")).getContentDescription().toString().contains("outside the app Files launch flow"),"script graphics limitation visible in Remote");
      a.ok(((TextView)a.field(activity,"inputText")).getText().toString().equals("Script running · screen incomplete"),"blank script screen is not described as ready");
      receive(a,serial,"Exiting interpreter...\n");a.method(ac,"updateScreenStatus").invoke(activity);
      a.ok(!Boolean.TRUE.equals(a.field(serial,"externalInterpreter")),"script exit clears detected interpreter state");
      a.set(activity,"connectStep",4);
      draw(a,activity,serial,clear);draw(a,activity,serial,imageRecord("/Themes/fixture/wifi.png",0,0,1,0));
      a.ok(panel.getPixel(152,80)==Color.GREEN&&panel.getPixel(151,80)==Color.BLACK,"centered image uses stock panel geometry");
      a.set(activity,"connectStep",4); // isolate renderer checks; new mode is tested separately
      draw(a,activity,serial,imageRecord("/Themes/fixture/wifi.png",0,0,0,2));
      a.ok(!Boolean.TRUE.equals(a.method(ac,"screenUsable").invoke(activity)),"SD image cannot stand in for same path on LittleFS");
      draw(a,activity,serial,clear);
      draw(a,activity,serial,imageRecord("/Themes/fixture/missing.png",0,0,0,0));
      a.ok(!Boolean.TRUE.equals(a.method(ac,"screenUsable").invoke(activity)),"missing pixels do not claim a usable screen");
      a.ok(Boolean.TRUE.equals(a.method(usb.getClass(),"isReady").invoke(usb)),"missing image does not disconnect USB");
      a.set(activity,"connectStep",0);
      a.set(activity,"menuRequired",false); // native-only fixture; menu-state path covered separately
      draw(a,activity,serial,clear);
      // Real widgets distinguish PrevPress from EscPress. No aliasing of the actions.
      View wheel=(View)a.field(activity,"encoderControl");ViewGroup arrows=(ViewGroup)a.field(activity,"remoteKeys");
      String[] commands={"nav prev\n","nav next\n","nav esc\n"};
      for(int i=0;i<3;i++) {
        a.set(serial,"nextAt",0L);((Button)arrows.getChildAt(i)).performClick();
        a.ok(new String(takeWrite(a,run,serial),"UTF-8").equals(commands[i]),"navigation button "+i+" sends distinct firmware command");receive(a,serial,"# ");
      }
      a.set(serial,"nextAt",0L);a.set(activity,"lastNavigationRequestAt",0L);draw(a,activity,serial,clear);
      // Vector-only fixture: an image would intentionally start the new mode transition.
      draw(a,activity,serial,MirrorTextAudit.text(14,160,16,2,0xffff,0xffff,"TEXT FIXTURE"));a.method(ac,"updateScreenStatus").invoke(activity);
      View root=(View)a.field(activity,"root");int w=root.getWidth(),h=root.getHeight();
      root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);
      android.graphics.Rect visible=new android.graphics.Rect();
      a.ok(wheel.getGlobalVisibleRect(visible)&&visible.height()==wheel.getHeight(),"connected rotary fully visible on small native viewport");
      a.ok(arrows.getChildAt(2).getGlobalVisibleRect(visible)&&visible.height()==arrows.getChildAt(2).getHeight(),"EXIT fully visible without scrolling");
      Bitmap screen=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);root.draw(new Canvas(screen));
      try(FileOutputStream file=new FileOutputStream(new File(a.getTargetContext().getExternalFilesDir(null),"remote-synthetic.png"))){screen.compress(Bitmap.CompressFormat.PNG,100,file);}screen.recycle();
      a.ok(root.getKeepScreenOn(),"foreground capture keeps phone screen on");
      ((Button)a.field(activity,"connectAction")).performClick();
      a.ok(Boolean.TRUE.equals(a.method(usb.getClass(),"isClosing").invoke(usb)),"same button requests bounded capture stop before disconnect");
      a.ok(!((Button)a.field(activity,"connectAction")).isEnabled(),"disconnect cannot restart while cleanup is pending");
      a.method(usb.getClass(),"disconnect",String.class).invoke(usb,"Synthetic audit closed; no physical capture");
      a.ok(a.method(usb.getClass(),"diagnostics").invoke(usb).toString().contains("1 images;")&&a.method(usb.getClass(),"diagnostics").invoke(usb).toString().contains("(last session)"),"native disconnect retains theme count while releasing bytes");
      a.ok(!root.getKeepScreenOn()&&!Boolean.TRUE.equals(a.field(activity,"deviceScreenControl")),"disconnect clears wake request and physical-screen consent");
      a.ok(!Boolean.TRUE.equals(a.method(ac,"linkMatches",int.class).invoke(activity,token)),"old connection cannot authorize new actions");
      a.ok(((Map<?,?>)a.field(a.field(mirror,"images"),"source")).isEmpty(),"disconnect releases transferred images");
      Object store=a.field(activity,"store");a.method(store.getClass(),"rememberUsb",String.class).invoke(store,"T-Embed test");
      a.ok("T-Embed test".equals(a.method(store.getClass(),"usbName").invoke(store)),"USB label remembered");
      a.method(store.getClass(),"forgetUsb").invoke(store);a.ok(a.method(store.getClass(),"usbName").invoke(store)==null,"Forget removes USB label");
      a.ok(Boolean.TRUE.equals(a.method(usb.getClass(),"interpreterRecoveryRequired").invoke(usb)),"forced close during stop upload retains interpreter recovery fence");
      restartedFixture(a,usb);
      // Physical testing reported this error. A script rejection must release the
      // setup UI and stop before capture; it must never silently run a retry.
      token=(Integer)a.method(usb.getClass(),"sessionToken").invoke(usb);
      run=rc.newInstance(token,null,null);a.set(usb,"current",run);
      a.method(serial.getClass(),"open",long.class).invoke(serial,SystemClock.elapsedRealtime()-1000);
      ((Button)a.field(activity,"connectAction")).performClick();a.method(ac,"advanceConnect").invoke(activity);
      takeWrite(a,run,serial);receive(a,serial,"Bruce v1.16.1\nDevice: Lilygo T-Embed CC1101\n# ");
      a.set(serial,"nextAt",0L);a.method(ac,"advanceConnect").invoke(activity);startScript(a,run,serial);
      receive(a,serial,"SyntaxError: expecting ')'\n at index.js:2:1\n");a.method(usb.getClass(),"reportState").invoke(usb);a.method(ac,"advanceConnect").invoke(activity);
      a.ok((Integer)a.field(activity,"connectStep")==0,"interpreter error cancels single-button setup immediately");
      a.ok(((Button)a.field(activity,"connectAction")).isEnabled(),"failed script does not leave connection button disabled");
      a.ok(!Boolean.TRUE.equals(a.method(usb.getClass(),"isOpen").invoke(usb)),"failed theme session closes before capture");
      a.ok(((AtomicReference<?>)a.field(run,"outbound")).get()==null,"script rejection cannot queue retry or mirror startup");
      a.ok(a.method(usb.getClass(),"diagnostics").invoke(usb).toString().contains("interpreter rejected"),"connection report retains interpreter failure");
      a.ok(Boolean.TRUE.equals(a.method(usb.getClass(),"interpreterRecoveryRequired").invoke(usb)),"failed theme completion fences subsequent interpreter uploads");
      restartedFixture(a,usb);
    });
  }
}
