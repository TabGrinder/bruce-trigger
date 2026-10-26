package com.tabgrinder.brucetrigger.audit;

import android.app.*;
import android.os.SystemClock;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicReference;

/** Exercise SELECT in the native Files action menu, not a hidden launcher. */
final class FilesLaunchAudit {
  static final String MENU="{\"width\":320,\"height\":170,\"menu\":\"regular_menu\",\"menu_title\":\"Files\",\"active\":0,\"options\":[{\"n\":0,\"label\":\"JS Script Run\"},{\"n\":1,\"label\":\"View File\"},{\"n\":2,\"label\":\"File Info\"},{\"n\":3,\"label\":\"Rename\"},{\"n\":4,\"label\":\"Copy\"},{\"n\":5,\"label\":\"Delete\"},{\"n\":6,\"label\":\"New Folder\"}]}";
  static byte[] ptext(String text,int op,int y)throws Exception {
    byte[] raw=text.getBytes("UTF-8"),b=new byte[13+raw.length];b[0]=(byte)170;b[1]=(byte)b.length;b[2]=(byte)op;
    b[4]=10;b[6]=(byte)y;b[8]=1;b[9]=(byte)255;b[10]=(byte)255;System.arraycopy(raw,0,b,13,raw.length);return b;
  }
  static void run(AuditRunner a,Activity activity)throws Throwable {
    Object[] state=new Object[4];
    a.onUi(()->{
      Object usb=a.field(activity,"usb"),serial=a.field(usb,"session");
      UsbUiAudit.restartedFixture(a,usb);
      Class<?> rc=a.targetClass("UsbController$Run"),profile=a.targetClass("UsbProfile");
      Constructor<?> ctor=rc.getDeclaredConstructor(int.class,android.hardware.usb.UsbDevice.class,profile);ctor.setAccessible(true);
      Object run=ctor.newInstance((Integer)a.method(usb.getClass(),"sessionToken").invoke(usb),null,null);
      a.set(usb,"current",run);Field owner=usb.getClass().getDeclaredField("owner");owner.setAccessible(true);owner.set(null,new java.lang.ref.WeakReference<Object>(usb));
      a.method(serial.getClass(),"open",long.class).invoke(serial,SystemClock.elapsedRealtime()-1000);
      a.set(serial,"verified",true);a.set(serial,"version","1.16.1");a.set(serial,"board","Lilygo T-Embed CC1101");
      a.set(a.field(serial,"mirror"),"active",true);a.set(activity,"mirrorRequested",true);
      a.set(activity,"foreground",true);a.set(activity,"resumed",true);a.set(activity,"menuMode",false);a.set(activity,"nativeFromTheme",false);
      a.set(activity,"active","Remote");a.method(activity.getClass(),"render").invoke(activity);
      a.method(a.field(activity,"mirror").getClass(),"reset").invoke(a.field(activity,"mirror"));
      a.method(a.field(activity,"mirrorStream").getClass(),"reset").invoke(a.field(activity,"mirrorStream"));
      byte[] nativeData=a.hex("aa 08 63 01 40 00 aa 01 aa 05 00 00 00");
      a.method(serial.getClass(),"receive",byte[].class,long.class).invoke(serial,nativeData,SystemClock.elapsedRealtime());
      a.method(activity.getClass(),"data",byte[].class).invoke(activity,(Object)nativeData);
      UsbUiAudit.draw(a,activity,serial,ptext("JS Script Run\n",17,40));a.set(serial,"nextAt",0L);
      a.ok(!Boolean.TRUE.equals(a.field(a.field(serial,"files"),"observed")),"Files launch regression contains no debug path line");
      a.ok(Boolean.TRUE.equals(a.method(activity.getClass(),"screenUsable").invoke(activity)),"Files selection starts from a usable native screen");
      a.method(activity.getClass(),"send",String.class).invoke(activity,"nav select");
      a.method(activity.getClass(),"advanceConnect").invoke(activity);
      a.ok(new String(UsbUiAudit.takeWrite(a,run,serial),"US-ASCII").equals("optionsJSON\n"),"SELECT checks the Files action before running any interpreter");
      a.method(activity.getClass(),"send",String.class).invoke(activity,"nav select");
      a.ok(((AtomicReference<?>)a.field(run,"outbound")).get()==null,"double SELECT cannot execute an unwrapped script");
      UsbUiAudit.receive(a,serial,MENU+"\n# ");a.set(serial,"nextAt",0L);
      a.method(activity.getClass(),"advanceConnect").invoke(activity);
      a.ok(new String(UsbUiAudit.takeWrite(a,run,serial),"US-ASCII").equals("option 2\n"),"verified Files action reads File Info without executing source");
      UsbUiAudit.receive(a,serial,"# ");
      UsbUiAudit.draw(a,activity,serial,a.hex("aa 05 00 00 00"));
      UsbUiAudit.draw(a,activity,serial,ptext("FILE INFO\n",17,30));
      UsbUiAudit.draw(a,activity,serial,ptext("Path: /Games/My game.js\n",17,50));
      UsbUiAudit.draw(a,activity,serial,ptext("\n",17,58));
      UsbUiAudit.draw(a,activity,serial,ptext("Bytes: 8192\n",17,66));
      a.set(serial,"nextAt",0L);a.set(activity,"filesRevision",SystemClock.elapsedRealtime()-300);a.set(activity,"lastNativeDrawAt",SystemClock.elapsedRealtime()-200);
      a.method(activity.getClass(),"advanceConnect").invoke(activity);
      a.ok(new String(UsbUiAudit.takeWrite(a,run,serial),"US-ASCII").equals("nav esc\n"),"File Info closes once before the game launch");
      UsbUiAudit.receive(a,serial,"# ");a.set(serial,"nextAt",0L);
      a.method(activity.getClass(),"advanceConnect").invoke(activity);
      a.ok(((AtomicReference<?>)a.field(run,"outbound")).get()==null,"prompt alone does not prove File Info returned");
      UsbUiAudit.draw(a,activity,serial,a.hex("aa 05 00 00 00"));
      a.method(activity.getClass(),"advanceConnect").invoke(activity);
      a.method(activity.getClass(),"advanceConnect").invoke(activity);
      state[0]=usb;state[1]=serial;state[2]=run;state[3]=activity;
    });
    FileInfoFlowAudit.finishLaunch(a,activity,state,"/Games/My game.js");
  }
}
