package com.tabgrinder.brucetrigger.audit;
import android.graphics.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Android Canvas: transmitted assets/positions match an independent native pixel reference. */
final class MegalodonRenderAudit {
 static List<byte[]> packets(JSONArray values)throws Exception {
  List<byte[]> result=new ArrayList<>();for(int j=0;j<values.length();j++){
   String h=values.getString(j);byte[] p=new byte[h.length()/2];for(int k=0;k<p.length;k++)p[k]=(byte)Integer.parseInt(h.substring(k*2,k*2+2),16);result.add(p);
  }return result;
 }
 static void run(AuditRunner a)throws Throwable {
  JSONObject data=new JSONObject(MegalodonFixtures.json());JSONArray frames=data.getJSONArray("frames"),physical=data.getJSONArray("physical");
  Object[] views=new Object[2];a.onUi(()->{views[0]=a.construct(a.targetClass("MirrorView"));views[1]=a.construct(a.targetClass("MirrorView"));});
  Object phone=views[0],device=views[1];Method apply=a.method(phone.getClass(),"applyGameFrame",List.class);
  int[] state={0,0,0}; // next reference, distinct animations, last pixel hash
  for(int i=0;i<frames.length();i++){
   final int index=i;List<byte[]> frame=packets(frames.getJSONArray(i));
   // Yield between transactions so a large independent reference never stalls
   // Android's main loop. Every actual phone frame is still applied atomically.
   a.onUi(()->{
    a.ok(Boolean.TRUE.equals(apply.invoke(phone,frame)),"Megalodon complete phone frame "+index);
    if(state[0]<physical.length()&&physical.getJSONObject(state[0]).getInt("index")==index){
     List<byte[]> nativeDraw=packets(physical.getJSONObject(state[0]).getJSONArray("native"));if(state[0]>0)nativeDraw.remove(0);
     // Only the reference expands XBitmap into individual pixels. It must obey
     // the same bounded renderer interface without weakening production limits.
     for(int begin=0;begin<nativeDraw.size();begin+=240)
      a.ok(Boolean.TRUE.equals(apply.invoke(device,nativeDraw.subList(begin,Math.min(begin+240,nativeDraw.size())))),"Megalodon native pixel reference accepted: "+a.method(device.getClass(),"lastRejection").invoke(device));
     int[] p=new int[320*170],d=new int[p.length];((Bitmap)a.field(phone,"bitmap")).getPixels(p,0,320,0,0,320,170);((Bitmap)a.field(device,"bitmap")).getPixels(d,0,320,0,0,320,170);
     if(!Arrays.equals(p,d)){
      StringBuilder detail=new StringBuilder();int differences=0;
      for(int pixel=0;pixel<p.length;pixel++)if(p[pixel]!=d[pixel]){
       if(differences++<6)detail.append(" ").append(pixel%320).append(",").append(pixel/320).append(":").append(Integer.toHexString(p[pixel])).append("/").append(Integer.toHexString(d[pixel]));
      }
      MenuViewAudit.screenshot(a,phone,"fusion-connected");MenuViewAudit.screenshot(a,device,"fusion-disconnected");
      throw new AssertionError("Megalodon reference mismatch frame="+index+" pixels="+differences+" phone/native"+detail);
     }
     a.ok(true,"Megalodon asset transport matches complete device drawing at frame "+index);
     int h=Arrays.hashCode(p);if(state[0]>0&&h!=state[2])state[1]++;state[2]=h;state[0]++;
     if(index==100||index==200)MenuViewAudit.screenshot(a,phone,"megalodon-"+index);
    }
   });
   // The oracle expands one cached bitmap into hundreds of individual pixels.
   // Replay at the game's 50-ms cadence; an unpaced offline loop can exceed
   // the real 8192-operations/s limit despite each phone frame using few calls.
   // Both production renderers retain their normal limits. Flood behavior is
   // tested separately; no budget reset or relaxed app threshold is used here.
   Thread.sleep(50);
  }
  a.onUi(()->{
   a.ok(state[0]==physical.length()&&state[1]>30,"Megalodon animation, up/down and pause render across the full trace");
   a.ok((Integer)a.method(phone.getClass(),"rejected").invoke(phone)==0,"no rejected Megalodon render operations");
  });
 }
}
