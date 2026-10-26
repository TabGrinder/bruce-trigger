package com.tabgrinder.brucetrigger.audit;
import android.app.*;import android.graphics.*;import android.os.*;import java.util.*;import java.lang.reflect.*;import java.util.concurrent.atomic.AtomicReference;import java.util.zip.Adler32;import java.io.*;
/** Root-only metadata plus actual native draw rendering; USB/filesystem are doubles. */
final class MenuViewAudit {
 static final String FOLDER="/Theme/CYBERPUNK2077 by -eni9ma/";
 static void transfer(AuditRunner a,Object session,String nonce,String key,byte[] data)throws Exception {
  String p="BT_"+nonce+"_IMG|";UsbUiAudit.receive(a,session,p+"B|0|"+key+"|"+data.length+"\n");char[] hex="0123456789abcdef".toCharArray();
  for(int i=0;i<data.length;i+=128){StringBuilder s=new StringBuilder();for(int j=i;j<data.length&&j<i+128;j++){int v=data[j]&255;s.append(hex[v>>4]).append(hex[v&15]);}UsbUiAudit.receive(a,session,p+"D|"+i+"|"+s+"\n");}
  Adler32 sum=new Adler32();sum.update(data);UsbUiAudit.receive(a,session,p+"E|"+sum.getValue()+"\n");
 }
 static void query(AuditRunner a,Activity activity,Object run,Object session,String json)throws Exception {
  a.set(session,"nextAt",0L);a.set(session,"queryAt",0L);a.set(activity,"menuDue",0L);a.method(activity.getClass(),"advanceMenu").invoke(activity);
  byte[] q=UsbUiAudit.takeWrite(a,run,session);a.ok(new String(q,"UTF-8").equals("optionsJSON\n"),"root view reads stock optionsJSON");
  UsbUiAudit.receive(a,session,json+"\n# ");a.set(session,"nextAt",0L);a.set(session,"queryAt",0L);a.method(activity.getClass(),"advanceMenu").invoke(activity);
 }
 static void beginEntry(AuditRunner a,Activity activity,Object run,Object session)throws Exception {beginEntry(a,activity,run,session,FOLDER+"14.png");}
 static void beginEntry(AuditRunner a,Activity activity,Object run,Object session,String imagePath)throws Exception {
  Class<?> ac=activity.getClass();a.set(session,"nextAt",0L);a.set(session,"queryAt",0L);a.method(ac,"send",String.class).invoke(activity,"nav select");
  a.ok((Integer)a.field(activity,"connectStep")==10 && a.field(activity,"pendingRootSelection")!=null,"SELECT records one intention before capture; no premature selection");
  a.ok(((AtomicReference<?>)a.field(run,"outbound")).get()==null,"nothing selected while capture is unprepared");
  if(Boolean.TRUE.equals(a.field(a.field(session,"mirror"),"active"))) {
   a.method(ac,"advanceConnect").invoke(activity);
   a.ok(new String(UsbUiAudit.takeWrite(a,run,session),"UTF-8").equals("optionsJSON\n"),"retained capture verifies SELECT directly without uploading an interpreter");
   a.ok(!Boolean.TRUE.equals(a.method(a.field(activity,"mirrorStream").getClass(),"hasClear").invoke(a.field(activity,"mirrorStream"))),"retained capture cannot expose stale root pixels as a new tool");
   return;
  }
  a.method(ac,"advanceConnect").invoke(activity);String nonce=UsbUiAudit.startScript(a,run,session);
  UsbUiAudit.draw(a,activity,session,a.hex("AA 08 63 01 40 00 AA 01"));
  UsbUiAudit.receive(a,session,"BT_"+nonce+"_ON\nExiting interpreter...\n# ");a.set(session,"nextAt",0L);a.set(session,"queryAt",0L);
  UsbUiAudit.draw(a,activity,session,a.hex("AA 05 00 00 00"));
  if(imagePath!=null)UsbUiAudit.draw(a,activity,session,UsbUiAudit.imageRecord(imagePath,0,0,1,0));
  a.method(ac,"advanceConnect").invoke(activity);
  a.ok(((AtomicReference<?>)a.field(run,"outbound")).get()==null,"query waits for the current draw burst to settle");
  a.set(activity,"lastNativeDrawAt",0L);a.method(ac,"advanceConnect").invoke(activity);
  a.ok(new String(UsbUiAudit.takeWrite(a,run,session),"UTF-8").equals("optionsJSON\n"),"capture start verifies the original root selection once");
 }
 static void stopToRoot(AuditRunner a,Activity activity,Object run,Object session)throws Exception {
  a.ok(Boolean.TRUE.equals(a.field(activity,"menuMode"))&&Boolean.TRUE.equals(a.field(a.field(session,"mirror"),"active")),"verified root reuses capture without a stop/start interpreter cycle");
  a.ok(((AtomicReference<?>)a.field(run,"outbound")).get()==null,"Back from Config queues neither script nor theme download");
 }
 static void screenshot(AuditRunner a,Object view,String name)throws Exception {
  android.view.View v=(android.view.View)view;v.measure(android.view.View.MeasureSpec.makeMeasureSpec(320,1073741824),android.view.View.MeasureSpec.makeMeasureSpec(170,1073741824));v.layout(0,0,320,170);
  Bitmap image=Bitmap.createBitmap(320,170,Bitmap.Config.ARGB_8888);v.draw(new Canvas(image));
  int[] pixels=new int[54400];image.getPixels(pixels,0,320,0,0,320,170);boolean nonblack=false;for(int pixel:pixels)if((pixel&0xffffff)!=0)nonblack=true;
  a.ok(nonblack,"actual View.onDraw is visible, not only its backing bitmap");
  try(FileOutputStream f=new FileOutputStream(new File(a.getTargetContext().getExternalFilesDir(null),name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,f);}image.recycle();
 }
 static void run(AuditRunner a,Activity activity)throws Throwable {a.onUi(()->{
  Class<?> ac=activity.getClass(),menuType=a.targetClass("RemoteMenu");Method parse=a.method(menuType,"parseRoot",String.class);
  a.ok(parse.invoke(null,StockOptionsFixtures.ROOT)!=null,"unchanged stock root getter accepted");
  a.ok(parse.invoke(null,StockOptionsFixtures.CONFIG)==null,"stock Config JSON with stale root options cannot reconstruct a local submenu");
  Object usb=a.field(activity,"usb"),session=a.field(usb,"session");Class<?> runClass=a.targetClass("UsbController$Run"),profile=a.targetClass("UsbProfile");Constructor<?> rc=runClass.getDeclaredConstructor(int.class,android.hardware.usb.UsbDevice.class,profile);rc.setAccessible(true);
  UsbUiAudit.restartedFixture(a,usb);
  int token=(Integer)a.method(usb.getClass(),"sessionToken").invoke(usb);Object run=rc.newInstance(token,null,null);a.set(usb,"current",run);Field owner=usb.getClass().getDeclaredField("owner");owner.setAccessible(true);owner.set(null,new java.lang.ref.WeakReference<Object>(usb));
  a.method(session.getClass(),"open",long.class).invoke(session,SystemClock.elapsedRealtime()-1000);a.set(session,"verified",true);a.set(session,"version","1.16.1");a.set(session,"board","Lilygo T-Embed CC1101");a.set(activity,"resumed",true);a.set(activity,"foreground",true);a.set(activity,"menuRequired",false);
  a.method(ac,"beginConnectFlow",int.class).invoke(activity,2);a.method(ac,"advanceConnect").invoke(activity);String nonce=UsbUiAudit.startScript(a,run,session),prefix="BT_"+nonce+"_IMG|";
  String[] keys={"wifi","ble","config"},files={"1.png","2.png","14.png"};
  UsbUiAudit.receive(a,session,prefix+"C|0000|0000\n");
  for(int i=0;i<files.length;i++){
   byte[] data=null;for(String[] row:StockImageFixtures.ROWS)if(row[0].equals("0:"+FOLDER+files[i]))data=android.util.Base64.decode(row[2],0);
   a.ok(data!=null,"original theme asset available");UsbUiAudit.receive(a,session,prefix+"M|"+keys[i]+"|0|"+FOLDER+files[i]+"\n");transfer(a,session,nonce,FOLDER+files[i],data);
  }
  UsbUiAudit.receive(a,session,prefix+"Z\nBT_"+nonce+"_THEME_DONE\nExiting interpreter...\n# ");a.set(session,"nextAt",0L);a.set(session,"queryAt",0L);a.method(ac,"advanceConnect").invoke(activity);
  Object view=a.field(activity,"mirror"),bridge=a.field(session,"mirror");
  query(a,activity,run,session,StockOptionsFixtures.ROOT);screenshot(a,view,"hybrid-root-config");
  FusionUiAudit.capture(a,activity,"fusion-connected");
  a.ok(Boolean.TRUE.equals(a.field(activity,"menuMode"))&&!Boolean.TRUE.equals(a.field(bridge,"active")),"root image navigation avoids one-slot native logger");
  Object sameBitmap=a.field(view,"bitmap");long renders=(Long)a.method(view.getClass(),"renderedImages").invoke(view);
  query(a,activity,run,session,StockOptionsFixtures.ROOT);
  a.ok(a.field(view,"bitmap")==sameBitmap&&(Long)a.method(view.getClass(),"renderedImages").invoke(view)==renders,"unchanged root response reuses composition without bitmap churn");
  query(a,activity,run,session,StockOptionsFixtures.ROOT.replace("\"active\":2","\"active\":0"));
  a.ok(a.field(view,"bitmap")==sameBitmap&&(Long)a.method(view.getClass(),"renderedImages").invoke(view)>renders,"changed root selection reuses surface and paints new image");
  query(a,activity,run,session,StockOptionsFixtures.ROOT);
  a.set(session,"nextAt",0L);a.method(ac,"send",String.class).invoke(activity,"nav next");
  a.ok(a.field(view,"bitmap")==sameBitmap&&a.field(activity,"menuSnapshot")==null,"navigation keeps last image visible but cannot select its stale state");
  a.ok(new String(UsbUiAudit.takeWrite(a,run,session),"UTF-8").equals("nav next\n"),"root navigation remains one direct input");UsbUiAudit.receive(a,session,"# ");
  query(a,activity,run,session,StockOptionsFixtures.ROOT);
  beginEntry(a,activity,run,session);UsbUiAudit.receive(a,session,StockOptionsFixtures.ROOT+"\n# ");a.set(session,"nextAt",0L);a.set(session,"queryAt",0L);a.method(ac,"advanceConnect").invoke(activity);
  a.ok(new String(UsbUiAudit.takeWrite(a,run,session),"UTF-8").equals("option 2\n"),"confirmed root uses stock option once without the extra 700-ms wait");UsbUiAudit.receive(a,session,"# ");
  UsbUiAudit.draw(a,activity,session,a.hex("AA 05 00 FB C0"));
  UsbUiAudit.draw(a,activity,session,MirrorTextAudit.text(14,160,32,1,0,0xfbc0,"Main Menu"));
  UsbUiAudit.draw(a,activity,session,MirrorTextAudit.text(14,160,70,2,0,0xfbc0,"Display & UI"));
  UsbUiAudit.draw(a,activity,session,MirrorTextAudit.text(14,160,118,1,0,0xfbc0,"LED Config"));
  a.set(session,"nextAt",0L);a.set(session,"queryAt",0L);a.method(ac,"advanceConnect").invoke(activity);
  a.ok(!Boolean.TRUE.equals(a.field(activity,"menuMode"))&&Boolean.TRUE.equals(a.field(bridge,"active")),"Config uses real native draw stream");
  a.ok(Boolean.TRUE.equals(a.method(ac,"screenUsable").invoke(activity)),"received local-menu draws allow normal screen control");screenshot(a,view,"hybrid-config-native");
  long replies=(Long)a.field(session,"menuReplies");UsbUiAudit.receive(a,session,StockOptionsFixtures.CONFIG+"\n");
  for(int i=0;i<12;i++)a.method(ac,"advanceMenu").invoke(activity);
  a.ok((Long)a.field(session,"menuReplies")==replies&&((AtomicReference<?>)a.field(run,"outbound")).get()==null,"no optionsJSON polling in local submenus; stale global Config cannot replace native screen");
  a.set(session,"nextAt",0L);a.set(session,"queryAt",0L);a.method(ac,"send",String.class).invoke(activity,"nav next");a.ok(new String(UsbUiAudit.takeWrite(a,run,session),"UTF-8").equals("nav next\n"),"local navigation sends one direct command, no capture restart");UsbUiAudit.receive(a,session,"# ");
  UsbUiAudit.draw(a,activity,session,UsbUiAudit.imageRecord(FOLDER+"1.png",0,0,1,0));
  a.ok((Integer)a.field(activity,"connectStep")==7,"native tool image is checked rather than treated as root");
  a.set(session,"nextAt",0L);a.set(session,"queryAt",0L);a.method(ac,"advanceConnect").invoke(activity);UsbUiAudit.takeWrite(a,run,session);
  UsbUiAudit.receive(a,session,StockOptionsFixtures.CONFIG+"\n# ");a.method(ac,"advanceConnect").invoke(activity);
  a.ok((Integer)a.field(activity,"connectStep")==0&&Boolean.TRUE.equals(a.field(bridge,"active"))&&((AtomicReference<?>)a.field(run,"outbound")).get()==null,"local image cannot launch stop interpreter or exit the user's tool");
  a.set(session,"nextAt",0L);a.set(session,"queryAt",0L);a.method(ac,"send",String.class).invoke(activity,"nav esc");UsbUiAudit.takeWrite(a,run,session);UsbUiAudit.receive(a,session,"# ");
  UsbUiAudit.draw(a,activity,session,a.hex("AA 05 00 00 00"));UsbUiAudit.draw(a,activity,session,UsbUiAudit.imageRecord(FOLDER+"14.png",0,0,1,0));
  a.ok((Integer)a.field(activity,"connectStep")==7,"image first verifies root before stopping capture");
  a.set(session,"nextAt",0L);a.set(session,"queryAt",0L);a.method(ac,"advanceConnect").invoke(activity);a.ok(new String(UsbUiAudit.takeWrite(a,run,session),"UTF-8").equals("optionsJSON\n"),"image verification is read-only");
  UsbUiAudit.receive(a,session,StockOptionsFixtures.ROOT+"\n# ");a.method(ac,"advanceConnect").invoke(activity);
  a.ok((Integer)a.field(activity,"connectStep")==0,"verified unchanged theme returns immediately to root presentation");stopToRoot(a,activity,run,session);
  query(a,activity,run,session,StockOptionsFixtures.ROOT);
  Object cachedRoot=a.field(view,"bitmap");long shown=(Long)a.method(view.getClass(),"renderedImages").invoke(view);
  UsbUiAudit.draw(a,activity,session,a.hex("AA 05 00 F8 00"));
  UsbUiAudit.draw(a,activity,session,UsbUiAudit.imageRecord(FOLDER+"1.png",0,0,1,0));
  a.ok(a.field(view,"bitmap")==cachedRoot&&(Long)a.method(view.getClass(),"renderedImages").invoke(view)==shown,"background logger cannot replace verified root image with a recycled image slot");
  a.ok((Integer)a.field(activity,"connectStep")==0,"background root images do not start nested root checks");
  // Physical/menu changes while the fixed capture script ran invalidate deferred SELECT.
  beginEntry(a,activity,run,session);String changed=StockOptionsFixtures.ROOT.replace("\"active\":2","\"active\":0");a.ok(!changed.equals(StockOptionsFixtures.ROOT),"changed-root fixture differs");
  UsbUiAudit.receive(a,session,changed+"\n# ");a.set(session,"nextAt",0L);a.set(session,"queryAt",0L);a.method(ac,"advanceConnect").invoke(activity);
  a.ok((Integer)a.field(activity,"connectStep")==0&&((AtomicReference<?>)a.field(run,"outbound")).get()==null,"changed selection cancels intent without selecting, replaying or restarting capture");stopToRoot(a,activity,run,session);
  query(a,activity,run,session,StockOptionsFixtures.CONFIG);a.ok(a.field(activity,"menuSnapshot")==null&&a.field(view,"bitmap")==null,"local Config metadata never becomes a fabricated root screen");
  a.method(ac,"send",String.class).invoke(activity,"nav select");a.ok(((AtomicReference<?>)a.field(run,"outbound")).get()==null,"invalid local snapshot cannot select");
  a.set(session,"nextAt",0L);a.set(session,"queryAt",0L);a.method(ac,"send",String.class).invoke(activity,"nav esc");a.ok(new String(UsbUiAudit.takeWrite(a,run,session),"UTF-8").equals("nav esc\n"),"Back remains available with no valid root snapshot");UsbUiAudit.receive(a,session,"# ");
  query(a,activity,run,session,StockOptionsFixtures.ROOT);
  a.method(ac,"send",String.class).invoke(activity,"nav select");a.method(ac,"cancelConnect").invoke(activity);a.ok(a.field(activity,"pendingRootSelection")==null,"cancelled setup cannot replay deferred selection");
  // Partial image themes commonly omit Ethernet/LoRa. A native vector root
  // must not wait forever for an image record that the firmware never sends.
  a.method(ac,"startMenuView").invoke(activity);String vectorRoot=StockOptionsFixtures.ROOT.replace("Config","Ethernet");query(a,activity,run,session,vectorRoot);
  screenshot(a,view,"hybrid-root-vector");beginEntry(a,activity,run,session,null);
  UsbUiAudit.receive(a,session,vectorRoot+"\n# ");a.set(session,"nextAt",0L);a.set(session,"queryAt",0L);a.method(ac,"advanceConnect").invoke(activity);
  a.ok(new String(UsbUiAudit.takeWrite(a,run,session),"UTF-8").equals("option 2\n"),"partial image theme can enter vector-only root item without a nonexistent image packet");UsbUiAudit.receive(a,session,"# ");
  a.method(usb.getClass(),"disconnect",String.class).invoke(usb,"Hybrid audit completed");a.ok(!Boolean.TRUE.equals(a.field(activity,"menuMode"))&&a.field(activity,"menuSnapshot")==null,"disconnect clears presentation and polling");
 });}
}
