package com.tabgrinder.brucetrigger.audit;
import android.app.*;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.zip.Adler32;
import org.json.*;

/** Actual engine-generated intro -> production receiver -> Canvas -> enabled SELECT. */
final class WaitingGameAudit {
  static void run(AuditRunner a,Activity activity)throws Throwable {
    JSONObject fixtures=new JSONObject(WaitingGameFixtures.json());
    for(Iterator<String> names=fixtures.keys();names.hasNext();){String name=names.next();
      a.onUi(()->{
        Class<?> ac=activity.getClass();Object usb=a.field(activity,"usb"),serial=a.field(usb,"session");
        UsbUiAudit.restartedFixture(a,usb);
        Class<?> rc=a.targetClass("UsbController$Run");
        Constructor<?> ctor=rc.getDeclaredConstructor(int.class,android.hardware.usb.UsbDevice.class,a.targetClass("UsbProfile"));ctor.setAccessible(true);
        Object run=ctor.newInstance((Integer)a.method(usb.getClass(),"sessionToken").invoke(usb),null,null);
        a.set(usb,"current",run);Field owner=usb.getClass().getDeclaredField("owner");owner.setAccessible(true);owner.set(null,new java.lang.ref.WeakReference<Object>(usb));
        a.method(serial.getClass(),"open",long.class).invoke(serial,SystemClock.elapsedRealtime()-1000);
        a.set(serial,"verified",true);a.set(serial,"version","1.16.1");a.set(serial,"board","Lilygo T-Embed CC1101");
        a.set(activity,"foreground",true);a.set(activity,"resumed",true);a.set(activity,"menuMode",false);a.set(activity,"active","Remote");a.method(ac,"render").invoke(activity);
        String template;
        try(InputStream in=activity.getResources().openRawResource(activity.getResources().getIdentifier("files_bridge","raw",activity.getPackageName()))){
          ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[2048];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);template=new String(out.toByteArray(),"US-ASCII");
        }
        a.ok(Boolean.TRUE.equals(a.method(usb.getClass(),"launchFilesGame",String.class,String.class).invoke(usb,template,"/GAMES/"+name+".js")),name+" uses current production launch");
        a.set(activity,"gamePresentation",true);a.set(activity,"mirrorRequested",false);a.method(a.field(activity,"mirror").getClass(),"reset").invoke(a.field(activity,"mirror"));
        UsbUiAudit.takeWrite(a,run,serial);UsbUiAudit.receive(a,serial,"Reading input data from serial buffer until EOF\n");
        byte[] body=(byte[])a.method(serial.getClass(),"takeScriptBody").invoke(serial);
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("BT_([a-f0-9]{16})_GAME").matcher(new String(body,"US-ASCII"));a.ok(m.find(),"current intro uses owned nonce");String prefix="BT_"+m.group(1)+"_GAME|";
        a.method(serial.getClass(),"written",int.class,int.class,long.class).invoke(serial,body.length,body.length,SystemClock.elapsedRealtime());
        UsbUiAudit.receive(a,serial,"# Entering interpreter...\n"+prefix+"SEARCH\n"+prefix+"ON\n");
        a.ok(!Boolean.TRUE.equals(a.method(ac,"screenUsable").invoke(activity)),"no blind input before actual intro");
        JSONArray packets=fixtures.getJSONArray(name);ByteArrayOutputStream raw=new ByteArrayOutputStream();
        for(int i=0;i<packets.length();i++){
          String hex=packets.getString(i);a.ok(hex.length()%2==0,"engine intro contains complete bytes");
          for(int at=0;at<hex.length();at+=2)raw.write(Integer.parseInt(hex.substring(at,at+2),16));
        }
        String wire=RecoveryWire.encode(prefix,0,raw.toByteArray(),packets.length());
        UsbUiAudit.receive(a,serial,wire.toString());a.method(ac,"advanceGame").invoke(activity);
        a.ok(Boolean.TRUE.equals(a.method(ac,"screenUsable").invoke(activity)),name+" intro enables phone SELECT before the game starts");
        MenuViewAudit.screenshot(a,a.field(activity,"mirror"),"waiting-"+name);
        a.set(serial,"nextAt",0L);a.method(ac,"send",String.class).invoke(activity,"nav select");
        a.ok(new String(UsbUiAudit.takeWrite(a,run,serial),"US-ASCII").equals("nav select\n"),name+" accepts one real app SELECT after intro");
        a.method(usb.getClass(),"disconnect",String.class).invoke(usb,"Intro audit completed");
      });
    }
  }
}
