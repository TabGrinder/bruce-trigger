package com.tabgrinder.brucetrigger.audit;
import android.graphics.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;
/** Real Canvas replay of pinned engine traces; inputs and USB remain doubles. */
final class FilesCatalogAudit {
  static void run(AuditRunner a)throws Throwable {a.onUi(()->{
    JSONObject catalog=new JSONObject(CatalogFixtures.json());
    for(Iterator<String> names=catalog.keys();names.hasNext();){String name=names.next();
      Object view=a.construct(a.targetClass("MirrorView"));Method draw=a.method(view.getClass(),"applyGameFrame",List.class);
      JSONArray frames=catalog.getJSONArray(name);int changing=0,last=0;
      for(int i=0;i<frames.length();i++){JSONArray frame=frames.getJSONArray(i);List<byte[]> packets=new ArrayList<>();
        for(int j=0;j<frame.length();j++){String h=frame.getString(j);byte[] b=new byte[h.length()/2];for(int k=0;k<b.length;k++)b[k]=(byte)Integer.parseInt(h.substring(k*2,k*2+2),16);packets.add(b);}
        if(packets.isEmpty())continue;
        a.ok(Boolean.TRUE.equals(draw.invoke(view,packets)),name+" real Canvas frame "+i);
        Bitmap b=(Bitmap)a.field(view,"bitmap");int[] pixels=new int[320*170];b.getPixels(pixels,0,320,0,0,320,170);int hash=Arrays.hashCode(pixels);
        if(i>0&&hash!=last)changing++;last=hash;

      }
      MenuViewAudit.screenshot(a,view,"catalog-"+name);
      a.ok(changing>=2,name+" visibly changes across complete frames");
      a.ok((Integer)a.method(view.getClass(),"rejected").invoke(view)==0,name+" has no rejected renderer operations");
    }
  });}
}
