package com.tabgrinder.brucetrigger.audit;

import android.app.*;
import android.os.SystemClock;
import android.widget.TextView;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Production launch/exit controls and restoration of the previously active native stream. */
final class GameLifecycleAudit {
  static AlertDialog dialog(AuditRunner a, Activity activity, String text) throws Exception {
    for(Object item:(Set<?>)a.field(activity,"ownedDialogs")) {
      AlertDialog d=(AlertDialog)item;TextView m=d.findViewById(android.R.id.message);
      if(d.isShowing()&&m!=null&&m.getText().toString().startsWith(text))return d;
    }
    return null;
  }
  static void run(AuditRunner a,Activity activity)throws Throwable {
    Object[] state=new Object[3];String[] prefix={null};
    a.onUi(()->{
      Object usb=a.field(activity,"usb"),serial=a.field(usb,"session");
      Class<?> runClass=a.targetClass("UsbController$Run"),profile=a.targetClass("UsbProfile");
      Constructor<?> ctor=runClass.getDeclaredConstructor(int.class,android.hardware.usb.UsbDevice.class,profile);ctor.setAccessible(true);
      Object run=ctor.newInstance((Integer)a.method(usb.getClass(),"sessionToken").invoke(usb),null,null);
      a.set(usb,"current",run);Field owner=usb.getClass().getDeclaredField("owner");owner.setAccessible(true);owner.set(null,new java.lang.ref.WeakReference<Object>(usb));
      a.method(serial.getClass(),"open",long.class).invoke(serial,SystemClock.elapsedRealtime()-1000);
      a.set(serial,"verified",true);a.set(serial,"version","1.16.1");a.set(serial,"board","Lilygo T-Embed CC1101");
      a.set(a.field(serial,"mirror"),"active",true);a.set(activity,"mirrorRequested",true);
      a.set(activity,"foreground",true);a.set(activity,"resumed",true);a.set(activity,"menuMode",false);
      a.set(activity,"nativeFromTheme",false);a.set(activity,"active","Remote");a.method(activity.getClass(),"render").invoke(activity);
      a.set(activity,"filesRootVerified",true); // Isolated post-root rendering/lifecycle fixture.
      a.ok(Boolean.TRUE.equals(a.method(activity.getClass(),"prepareFilesGame",String.class).invoke(activity,"/scripts/Games/flappybird.js")),"selected Files script prepares launch from native capture");
      state[0]=usb;state[1]=serial;state[2]=run;
    });
    a.onUi(()->{
      Object serial=state[1],run=state[2];
      a.ok(Boolean.TRUE.equals(a.field(activity,"gameReturnToNative")),"launch remembers the prior native screen mode");
      if((Integer)a.field(activity,"connectStep")==15)a.method(activity.getClass(),"advanceConnect").invoke(activity);
      a.method(activity.getClass(),"advanceConnect").invoke(activity);
      UsbUiAudit.takeWrite(a,run,serial);UsbUiAudit.receive(a,serial,"Reading input data from serial buffer until EOF\n");
      byte[] body=(byte[])a.method(serial.getClass(),"takeScriptBody").invoke(serial);
      a.ok(body!=null,"single game bridge owns native capture handover");
      java.util.regex.Matcher m=java.util.regex.Pattern.compile("BT_([a-f0-9]{16})_GAME").matcher(new String(body,"US-ASCII"));a.ok(m.find(),"restoration fixture uses the actual owned game nonce");prefix[0]="BT_"+m.group(1)+"_GAME|";
      a.method(serial.getClass(),"written",int.class,int.class,long.class).invoke(serial,body.length,body.length,SystemClock.elapsedRealtime());
      UsbUiAudit.receive(a,serial,"# Entering interpreter...\n"+prefix[0]+"CAPTURE_OFF\n"+prefix[0]+"SEARCH\n"+prefix[0]+"ON\n");
      for(String line:GameDrawFixtures.wire().split("\n")) {
        if(!(line.startsWith("R|")||line.startsWith("D|")))continue;
        if(!line.startsWith("R|0|"))break;
        UsbUiAudit.receive(a,serial,prefix[0]+line+"\n");
      }
      a.set(a.field(serial,"mirror"),"drainUntil",0L);
      a.method(activity.getClass(),"advanceGame").invoke(activity);a.set(serial,"nextAt",0L);
      UsbUiAudit.draw(a,activity,serial,UsbUiAudit.bytes(prefix[0]+"UI_ON\n"));
      a.ok(Boolean.TRUE.equals(a.field(activity,"gameNativePresentation")),"owned JS native dialog switches the live receiver");
      UsbUiAudit.draw(a,activity,serial,FilesSpritesAudit.p(2,20,20,100,30,63488));
      a.ok(Boolean.TRUE.equals(a.method(activity.getClass(),"screenUsable").invoke(activity)),"native dialog overlay accepts controls after actual pixels");
      a.ok(((android.graphics.Bitmap)a.field(a.field(activity,"mirror"),"bitmap")).getPixel(22,22)==android.graphics.Color.RED,"native dialog updates the same live mirror");
      a.set(serial,"nextAt",0L);a.method(activity.getClass(),"send",String.class).invoke(activity,"nav select");
      a.ok(new String(UsbUiAudit.takeWrite(a,run,serial),"US-ASCII").equals("nav select\n"),"native JS dialog SELECT remains one physical input");
      UsbUiAudit.receive(a,serial,"# ");
      UsbUiAudit.draw(a,activity,serial,UsbUiAudit.bytes(prefix[0]+"UI_OFF\n"));
      a.ok(!Boolean.TRUE.equals(a.field(activity,"gameNativePresentation")),"native dialog exit restores the sequenced JS receiver");
      a.set(serial,"nextAt",0L);a.method(activity.getClass(),"send",String.class).invoke(activity,"nav esc");
    });
    a.onUi(()->{
      Object serial=state[1],run=state[2];
      a.ok(new String(UsbUiAudit.takeWrite(a,run,serial),"US-ASCII").equals("nav esc\n"),"EXIT sends exactly one normal script back input");
      UsbUiAudit.receive(a,serial,"# ");a.set(serial,"nextAt",0L);
      a.ok(!Boolean.TRUE.equals(a.method(a.field(serial,"game").getClass(),"awaitingExit").invoke(a.field(serial,"game"))),"generic back input may be handled by a script menu; no forced exit deadline");
      UsbUiAudit.receive(a,serial,prefix[0]+"DONE\n");a.method(activity.getClass(),"advanceGame").invoke(activity);
      a.ok(Boolean.TRUE.equals(a.field(activity,"gamePresentation"))&&((AtomicReference<?>)a.field(run,"outbound")).get()==null,"terminal marker alone cannot start a replacement interpreter");
      UsbUiAudit.receive(a,serial,"Exiting interpreter...\n");a.set(serial,"nextAt",0L);a.method(activity.getClass(),"advanceGame").invoke(activity);
      a.ok(!Boolean.TRUE.equals(a.field(activity,"gamePresentation"))&&!Boolean.TRUE.equals(a.field(activity,"menuMode")),"confirmed game exit chooses native restoration rather than forced menu reconstruction");
      a.method(activity.getClass(),"advanceConnect").invoke(activity);
      String start=UsbUiAudit.startScript(a,run,serial);
      UsbUiAudit.draw(a,activity,serial,a.hex("AA 08 63 01 40 00 AA 01"));
      UsbUiAudit.draw(a,activity,serial,a.hex("AA 05 00 00 00"));
      UsbUiAudit.receive(a,serial,"BT_"+start+"_ON\nExiting interpreter...\n# ");a.set(serial,"nextAt",0L);
      a.method(activity.getClass(),"advanceConnect").invoke(activity);
      a.ok((Integer)a.field(activity,"connectStep")==0&&Boolean.TRUE.equals(a.field(activity,"mirrorRequested")),"native restart completes without USB reconnection");
      a.ok(Boolean.TRUE.equals(a.method(activity.getClass(),"screenUsable").invoke(activity)),"actual new geometry and clear restore usable native screen control");
      a.ok((Long)a.method(a.field(activity,"mirror").getClass(),"gameFramesComposed").invoke(a.field(activity,"mirror"))==1,"native restoration retains the completed game counters");
      a.method(state[0].getClass(),"disconnect",String.class).invoke(state[0],"Game lifecycle audit completed");
      a.ok(!Boolean.TRUE.equals(a.field(activity,"gameReturnToNative")),"disconnect clears the previous-mode intent");
      // A failed native stop must not enter the successful restoration loop.
      Class<?> failedRunClass=a.targetClass("UsbController$Run");
      Constructor<?> failedCtor=failedRunClass.getDeclaredConstructor(int.class,android.hardware.usb.UsbDevice.class,a.targetClass("UsbProfile"));failedCtor.setAccessible(true);
      Object failedRun=failedCtor.newInstance((Integer)a.method(state[0].getClass(),"sessionToken").invoke(state[0]),null,null);
      a.set(state[0],"current",failedRun);
      a.method(serial.getClass(),"open",long.class).invoke(serial,SystemClock.elapsedRealtime()-1000);
      a.set(serial,"verified",true);a.set(a.field(serial,"mirror"),"active",true);
      Object game=a.field(serial,"game");a.set(game,"captureHandover",true);a.set(game,"captureStopped",false);
      a.set(game,"attempted",true);a.set(game,"exited",true);a.set(game,"terminal",true);
      a.set(game,"outcome","Unsupported JS operation: capture stop");
      a.set(activity,"gamePresentation",true);a.set(activity,"gameReturnToNative",true);
      a.method(activity.getClass(),"advanceGame").invoke(activity);
      a.ok(!Boolean.TRUE.equals(a.field(activity,"gamePresentation"))&&(Integer)a.field(activity,"connectStep")==0,"failed handover does not wait for a second interpreter or restoration timeout");
      a.ok(a.field(state[0],"current")==null,"failed capture ownership closes control without replay");
      for(Object item:new ArrayList<Object>((Set<?>)a.field(activity,"ownedDialogs")))((AlertDialog)item).dismiss();

    });
  }
}
