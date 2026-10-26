package com.tabgrinder.brucetrigger.audit;

import android.app.*;
import android.os.SystemClock;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Real Handler flow: no manual advance/cooldown bypass to escape File Info. USB is doubled. */
final class FileInfoFlowAudit {
  static Object[] setup(AuditRunner a,Activity activity)throws Throwable { return setup(a,activity,false); }
  static Object[] setup(AuditRunner a,Activity activity,boolean physical)throws Throwable {return setup(a,activity,physical,">JS Script Run\n");}
  static Object[] setup(AuditRunner a,Activity activity,boolean physical,String label)throws Throwable {
    Object[] state=new Object[4];
    a.onUi(()->{
      Object usb=a.field(activity,"usb"),serial=a.field(usb,"session");
      UsbUiAudit.restartedFixture(a,usb);
      Class<?> runClass=a.targetClass("UsbController$Run");
      Constructor<?> ctor=runClass.getDeclaredConstructor(int.class,android.hardware.usb.UsbDevice.class,a.targetClass("UsbProfile"));ctor.setAccessible(true);
      Object run=ctor.newInstance((Integer)a.method(usb.getClass(),"sessionToken").invoke(usb),null,null);
      a.set(usb,"current",run);Field owner=usb.getClass().getDeclaredField("owner");owner.setAccessible(true);owner.set(null,new java.lang.ref.WeakReference<Object>(usb));
      a.method(serial.getClass(),"open",long.class).invoke(serial,SystemClock.elapsedRealtime()-1000);
      a.set(serial,"verified",true);a.set(serial,"version","1.16.1");a.set(serial,"board","Lilygo T-Embed CC1101");
      a.set(a.field(serial,"mirror"),"active",true);
      a.set(activity,"foreground",true);a.set(activity,"resumed",true);a.set(activity,"mirrorRequested",true);a.set(activity,"deviceScreenControl",physical);
      a.set(activity,"menuMode",false);a.set(activity,"nativeFromTheme",false);a.set(activity,"active","Remote");
      a.method(activity.getClass(),"render").invoke(activity);
      a.method(a.field(activity,"mirror").getClass(),"reset").invoke(a.field(activity,"mirror"));
      a.method(a.field(activity,"mirrorStream").getClass(),"reset").invoke(a.field(activity,"mirrorStream"));
      UsbUiAudit.draw(a,activity,serial,a.hex("aa 08 63 01 40 00 aa 01 aa 05 00 00 00"));
      UsbUiAudit.draw(a,activity,serial,FilesLaunchAudit.ptext(label,17,40));
      a.method(activity.getClass(),"send",String.class).invoke(activity,"nav select");
      state[0]=usb;state[1]=serial;state[2]=run;state[3]=activity;
    });
    return state;
  }
  static byte[] awaitWrite(AuditRunner a,Object[] state,String expected)throws Throwable {
    byte[][] result={null};long deadline=SystemClock.elapsedRealtime()+10000;
    do{
      a.onUi(()->{
        if(((AtomicReference<?>)a.field(state[2],"outbound")).get()!=null)result[0]=UsbUiAudit.takeWrite(a,state[2],state[1]);
      });
      if(result[0]!=null)break;
      Thread.sleep(30);
    }while(SystemClock.elapsedRealtime()<deadline);
    String[] diagnostic={""};
    if(result[0]==null)a.onUi(()->diagnostic[0]="; step="+a.field(state[3],"connectStep")+"; status="+a.field(state[1],"status")+"; failure="+a.field(state[1],"failure")+"; deadline left="+((Long)a.field(state[3],"connectDeadline")-SystemClock.elapsedRealtime())+"; foreground="+a.field(state[3],"foreground")+"; resumed="+a.field(state[3],"resumed")+"; close="+a.field(state[0],"lastClosure"));
    a.ok(result[0]!=null,"production Handler schedules "+expected+diagnostic[0]);
    a.ok(new String(result[0],"US-ASCII").startsWith(expected),"only expected command: "+expected);
    return result[0];
  }
  static void feed(AuditRunner a,Activity activity,Object[] state,byte[] raw)throws Throwable {
    a.onUi(()->UsbUiAudit.draw(a,activity,state[1],raw));
  }
  static void menu(AuditRunner a,Activity activity,Object[] state)throws Throwable {
    awaitWrite(a,state,"optionsJSON\n");
    a.onUi(()->{
      if(Boolean.TRUE.equals(a.field(activity,"deviceScreenControl")))a.set(state[1],"nextAt",SystemClock.elapsedRealtime()+450);
      UsbUiAudit.draw(a,activity,state[1],UsbUiAudit.bytes(FilesLaunchAudit.MENU+"\n# "));
    });
    awaitWrite(a,state,"option 2\n");
  }
  static String uploadBody(AuditRunner a,Activity activity,Object[] state)throws Throwable {
    feed(a,activity,state,UsbUiAudit.bytes("Reading input data from serial buffer until EOF\n"));
    String[] text={null};
    a.onUi(()->{
      byte[] body=(byte[])a.method(state[1].getClass(),"takeScriptBody").invoke(state[1]);
      a.ok(body!=null,"owned buffer released after the stock handshake");text[0]=new String(body,"US-ASCII");
      a.method(state[1].getClass(),"written",int.class,int.class,long.class).invoke(state[1],body.length,body.length,SystemClock.elapsedRealtime());
    });return text[0];
  }
  static void finishLaunch(AuditRunner a,Activity activity,Object[] state,String path)throws Throwable {
    // File Info returned to loopSD, not to loop(). Its global options stay stale.
    // Do not acknowledge any interpreter upload until two folder exits reach root.
    for(int level=0;level<2;level++) {
      awaitWrite(a,state,"optionsJSON\n");
      feed(a,activity,state,UsbUiAudit.bytes(FilesLaunchAudit.MENU+"\n# "));
      awaitWrite(a,state,"nav esc\n");
      feed(a,activity,state,UsbUiAudit.bytes("# "));
      Thread.sleep(level==0?350:150);
      a.onUi(()->a.ok(((AtomicReference<?>)a.field(state[2],"outbound")).get()==null,"an acknowledged Escape cannot skip the actual folder redraw"));
      feed(a,activity,state,a.hex("aa 05 00 00 00"));
    }
    awaitWrite(a,state,"optionsJSON\n");
    feed(a,activity,state,UsbUiAudit.bytes(StockOptionsFixtures.ROOT+"\n# "));
    awaitWrite(a,state,"js run_from_buffer ");String game=uploadBody(a,activity,state);
    a.ok(game.contains("capture=true"),"single upload transfers native capture ownership inside the selected script");
    a.ok(!game.contains("_OFF\""),"no separate mirror-stop interpreter was uploaded");
    a.ok(game.contains("file='"+path+"'"),"root traversal preserves the exact selected file path");
    a.onUi(()->{
      a.ok(Boolean.TRUE.equals(a.field(activity,"gamePresentation")),"Files interpreter starts only after verified root through production scheduling");
      a.method(state[0].getClass(),"disconnect",String.class).invoke(state[0],"Timed Files test complete");
    });
  }
  static void run(AuditRunner a,Activity activity)throws Throwable {
    Object[] state=setup(a,activity);menu(a,activity,state);
    java.io.ByteArrayOutputStream raw=new java.io.ByteArrayOutputStream();
    raw.write(UsbUiAudit.bytes("# "));raw.write(a.hex("aa 05 00 00 00"));
    raw.write(FilesLaunchAudit.ptext("FILE INFO\n",17,25));
    raw.write(FilesLaunchAudit.ptext("Path: /GAMES/Dino.js\n",17,49));
    raw.write(FilesLaunchAudit.ptext("Bytes: 23949\n",17,65));
    raw.write(FilesLaunchAudit.ptext("Size: 23.95 kB\n",17,81));
    feed(a,activity,state,raw.toByteArray()); // no blank drawing records
    awaitWrite(a,state,"nav esc\n");
    feed(a,activity,state,UsbUiAudit.bytes("# "));
    Thread.sleep(200);
    a.onUi(()->a.ok(((AtomicReference<?>)a.field(state[2],"outbound")).get()==null,"prompt alone cannot run a script while File Info is still shown"));
    feed(a,activity,state,a.hex("aa 05 00 00 00"));
    finishLaunch(a,activity,state,"/GAMES/Dino.js");

    // One expired ESC recovers only after a fresh snapshot and actual redraw.
    Object[] recovered=setup(a,activity);menu(a,activity,recovered);
    feed(a,activity,recovered,raw.toByteArray());
    awaitWrite(a,recovered,"nav esc\n");feed(a,activity,recovered,UsbUiAudit.bytes("# "));
    awaitWrite(a,recovered,"optionsJSON\n");feed(a,activity,recovered,UsbUiAudit.bytes(FilesLaunchAudit.MENU+"\n# "));
    awaitWrite(a,recovered,"nav esc\n");feed(a,activity,recovered,UsbUiAudit.bytes("# "));
    feed(a,activity,recovered,a.hex("aa 05 00 00 00"));
    finishLaunch(a,activity,recovered,"/GAMES/Dino.js");

    // Reproduce the stock 75-ms input lifetime / 100-ms File Info poll gap.
    Object[] expired=setup(a,activity);menu(a,activity,expired);
    feed(a,activity,expired,raw.toByteArray());
    for(int attempt=0;attempt<3;attempt++) {
      awaitWrite(a,expired,"nav esc\n");
      feed(a,activity,expired,UsbUiAudit.bytes("# "));
      // No screen redraw: the remote ESC expired before the native poll.
      awaitWrite(a,expired,"optionsJSON\n");
      feed(a,activity,expired,UsbUiAudit.bytes(FilesLaunchAudit.MENU+"\n# "));
    }
    long recoveryDeadline=SystemClock.elapsedRealtime()+4000;
    boolean[] stopped={false};
    do {a.onUi(()->stopped[0]=(Integer)a.field(activity,"connectStep")==0);if(stopped[0])break;Thread.sleep(30);}while(SystemClock.elapsedRealtime()<recoveryDeadline);
    a.onUi(()->{
      a.ok(stopped[0],"three expired EXITs end bounded read-only preparation");
      a.ok(((AtomicReference<?>)a.field(expired[2],"outbound")).get()==null,"no fourth EXIT, SELECT or executable replay after retry budget");
      a.ok(Boolean.TRUE.equals(a.method(expired[0].getClass(),"isOpen").invoke(expired[0])),"expired inputs leave the USB link open");
      a.ok(!Boolean.TRUE.equals(a.field(activity,"gamePresentation")),"unverified root never starts a game");
      for(Object item:new ArrayList<Object>((Set<?>)a.field(activity,"ownedDialogs")))((AlertDialog)item).dismiss();
      a.method(expired[0].getClass(),"disconnect",String.class).invoke(expired[0],"Expired input regression completed");
    });

    Object[] stalled=setup(a,activity);menu(a,activity,stalled);
    feed(a,activity,stalled,UsbUiAudit.bytes("# "));
    feed(a,activity,stalled,a.hex("aa 05 00 00 00"));
    feed(a,activity,stalled,FilesLaunchAudit.ptext("FILE INFO\n",17,25));
    feed(a,activity,stalled,FilesLaunchAudit.ptext("Path: /GAMES/Dino.js\n",17,49));
    a.onUi(()->{
      a.set(activity,"connectDeadline",SystemClock.elapsedRealtime()-1);
      a.method(activity.getClass(),"advanceConnect").invoke(activity);
      a.ok(Boolean.TRUE.equals(a.method(stalled[0].getClass(),"isOpen").invoke(stalled[0]))&&!Boolean.TRUE.equals(a.method(stalled[0].getClass(),"isClosing").invoke(stalled[0])),"incomplete read-only File Info keeps USB open");
      a.ok((Integer)a.field(activity,"connectStep")==0,"failed read-only workflow releases navigation controls");
      a.ok(((AtomicReference<?>)a.field(stalled[2],"outbound")).get()==null,"timeout does not replay SELECT or start an interpreter");
      boolean expected=false;
      for(Object item:new ArrayList<Object>((Set<?>)a.field(activity,"ownedDialogs"))){AlertDialog d=(AlertDialog)item;android.widget.TextView m=d.findViewById(android.R.id.message);if(d.isShowing()&&m!=null&&m.getText().toString().contains("USB is still connected")){expected=true;d.dismiss();}}
      a.ok(expected,"metadata failure gives an actionable notice without requiring a firmware restart");
    });
    Thread.sleep(200);
    a.onUi(()->a.method(activity.getClass(),"send",String.class).invoke(activity,"nav esc"));
    awaitWrite(a,stalled,"nav esc\n");
    a.onUi(()->{
      a.method(stalled[0].getClass(),"disconnect",String.class).invoke(stalled[0],"Files timeout test complete");
      a.method(stalled[1].getClass(),"open",long.class).invoke(stalled[1],SystemClock.elapsedRealtime());
      a.ok(((String)a.field(a.field(stalled[1],"files"),"lastAttempt")).contains("metadata was incomplete"),"new connection retains the prior Files failure");
      a.method(stalled[1].getClass(),"close").invoke(stalled[1]);
      a.ok(((String)a.method(a.field(stalled[1],"files").getClass(),"historySummary").invoke(a.field(stalled[1],"files"))).startsWith("Previous connection:"),"retained attempt is explicitly labeled as previous connection");
    });
    Object[] physical=setup(a,activity,true);
    menu(a,activity,physical);
    a.onUi(()->{
      a.ok("option 2".equals(a.field(physical[1],"lastCommand")),"physical-screen fallback still intercepts Files Run after a quiet cooldown");
      a.method(physical[0].getClass(),"disconnect",String.class).invoke(physical[0],"Physical fallback regression completed");
      a.set(activity,"deviceScreenControl",false);
    });

  }
}
