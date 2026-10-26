package com.tabgrinder.brucetrigger.audit;

import android.app.*;
import android.graphics.Bitmap;
import android.os.SystemClock;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Real Android receiver/renderer/UI with the engine-produced game transcript and USB doubles. */
final class GameMirrorAudit {
  static void run(AuditRunner a,Activity activity)throws Throwable {a.onUi(()->{
    Class<?> ac=activity.getClass();Object usb=a.field(activity,"usb"),session=a.field(usb,"session");
    Class<?> rc=a.targetClass("UsbController$Run"),profile=a.targetClass("UsbProfile");
    Constructor<?> ctor=rc.getDeclaredConstructor(int.class,android.hardware.usb.UsbDevice.class,profile);ctor.setAccessible(true);
    Object run=ctor.newInstance((Integer)a.method(usb.getClass(),"sessionToken").invoke(usb),null,null);
    a.set(usb,"current",run);Field owner=usb.getClass().getDeclaredField("owner");owner.setAccessible(true);owner.set(null,new java.lang.ref.WeakReference<Object>(usb));
    a.method(session.getClass(),"open",long.class).invoke(session,SystemClock.elapsedRealtime()-1000);
    a.set(session,"verified",true);a.set(session,"version","1.16.1");a.set(session,"board","Lilygo T-Embed CC1101");
    a.set(activity,"foreground",true);a.set(activity,"resumed",true);a.set(activity,"active","Remote");a.method(ac,"render").invoke(activity);
    a.set(activity,"filesRootVerified",true); // Isolated post-root rendering/lifecycle fixture.
      a.ok(Boolean.TRUE.equals(a.method(ac,"prepareFilesGame",String.class).invoke(activity,"/scripts/Games/flappybird.js")),"selected Files path prepares a bounded game launch");
    });
    // AlertDialog invokes its listener through Handler; let the real callback and
    // posted connection step run before observing the queued header.
    a.onUi(()->{
    Class<?> ac=activity.getClass();Object usb=a.field(activity,"usb"),session=a.field(usb,"session"),run=a.field(usb,"current");
    if((Integer)a.field(activity,"connectStep")==16)a.method(ac,"advanceConnect").invoke(activity);
    a.ok(Boolean.TRUE.equals(a.field(activity,"gamePresentation")),"reviewed launch starts only on idle capture-free USB session");
    byte[] header=UsbUiAudit.takeWrite(a,run,session);
    a.ok(new String(header,"US-ASCII").startsWith("js run_from_buffer "),"game uses stock RAM uploader");
    UsbUiAudit.receive(a,session,"Reading input data from serial buffer until EOF\n");
    byte[] body=(byte[])a.method(session.getClass(),"takeScriptBody").invoke(session);
    a.ok(body!=null&&body.length<16000,"only bounded approved game bridge is uploaded");
    String source=new String(body,"US-ASCII");java.util.regex.Matcher m=java.util.regex.Pattern.compile("BT_([a-f0-9]{16})_GAME").matcher(source);
    a.ok(m.find(),"game upload contains scoped random nonce");String prefix="BT_"+m.group(1)+"_GAME|";
    a.method(session.getClass(),"written",int.class,int.class,long.class).invoke(session,body.length,body.length,SystemClock.elapsedRealtime());
    UsbUiAudit.receive(a,session,"# Entering interpreter...\n");
    Object game=a.field(session,"game"),screen=a.field(game,"screen"),view=a.field(activity,"mirror");
    Method advance=a.method(ac,"advanceGame");int frame=0;long firstPixels=0,lastPixels=0;
    for(String line:GameDrawFixtures.wire().split("\n")) {
      if(line.equals("DONE"))continue;
      UsbUiAudit.receive(a,session,prefix+line+"\n");
      if((Long)a.field(screen,"frames")>frame) {
        advance.invoke(activity);frame++;
        a.ok(Boolean.TRUE.equals(a.method(ac,"screenUsable").invoke(activity)),"validated complete game frame enables controls "+frame);
        if(frame==16 || frame==60){Bitmap bitmap=(Bitmap)a.field(view,"bitmap");int[] pixels=new int[320*170];bitmap.getPixels(pixels,0,320,0,0,320,170);long sum=Arrays.hashCode(pixels);if(frame==16){firstPixels=sum;MenuViewAudit.screenshot(a,view,"flappy-usb-bridge");}else lastPixels=sum;}
        if(frame==16) {
          a.set(session,"nextAt",0L);a.method(ac,"send",String.class).invoke(activity,"nav select");
          a.ok(new String(UsbUiAudit.takeWrite(a,run,session),"US-ASCII").equals("nav select\n"),"SELECT sends a single game input without restarting capture");UsbUiAudit.receive(a,session,"# ");
        }
      }
    }
    a.ok(frame==108&&(Long)a.field(screen,"frames")==108L,"all 108 engine-produced frames received");
    a.ok((Long)a.field(screen,"records")==3352L&&(Long)a.field(screen,"rejected")==0L,"3350 physical draws plus geometry/clear validated");
    a.ok(firstPixels!=lastPixels,"actual Android bitmap changes as the game progresses");
    a.ok((Integer)a.method(view.getClass(),"rejected").invoke(view)==0,"native Android renderer accepts every game operation");
    a.ok(((AtomicReference<?>)a.field(run,"outbound")).get()==null,"game does not poll optionsJSON or restart native capture");
    a.set(session,"nextAt",0L);a.method(ac,"send",String.class).invoke(activity,"nav esc");a.ok(new String(UsbUiAudit.takeWrite(a,run,session),"US-ASCII").equals("nav esc\n"),"Back exits with one normal navigation command");UsbUiAudit.receive(a,session,"# ");
    UsbUiAudit.receive(a,session,prefix+"DONE\nExiting interpreter...\n");a.set(session,"nextAt",0L);advance.invoke(activity);
    a.ok(!Boolean.TRUE.equals(a.field(activity,"gamePresentation"))&&Boolean.TRUE.equals(a.field(activity,"menuMode")),"acknowledged game exit returns to menu mode");
    a.ok(a.field(view,"bitmap")==null,"ended game image is cleared before root reconstruction");
    a.method(usb.getClass(),"disconnect",String.class).invoke(usb,"Game audit completed");
    a.ok(!Boolean.TRUE.equals(a.field(activity,"gamePresentation")),"disconnect clears game UI state");
  });}
}
