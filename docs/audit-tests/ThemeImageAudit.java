package com.tabgrinder.brucetrigger.audit;
import android.graphics.*;import android.view.*;import java.util.*;import java.lang.reflect.*;
final class ThemeImageAudit {
 static void run(AuditRunner a)throws Throwable {a.onUi(()->{
  Class<?> type=a.targetClass("MirrorView");Object view=a.construct(type);Method apply=a.method(type,"apply",byte[].class);
  Map<String,byte[]> images=new LinkedHashMap<>();
  images.put("0:/Themes/test/a.png",android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAQAAAAECAIAAAAmkwkpAAAAEklEQVR4nGNk+M8AB0wMDMRwADLSAQfvSjVpAAAAAElFTkSuQmCC",0));
  images.put("0:/Themes/test/a.jpg",android.util.Base64.decode("/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAAEAAQDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDWooor80PyA//Z",0));
  images.put("0:/Themes/test/a.bmp",android.util.Base64.decode("Qk1mAAAAAAAAADYAAAAoAAAABAAAAAQAAAABABgAAAAAADAAAADEDgAAxA4AAAAAAAAAAAAAAP8AAP8AAP8AAP8AAP8AAP8AAP8AAP8AAP8AAP8AAP8AAP8AAP8AAP8AAP8AAP8A",0));
  images.put("0:/Themes/test/a.gif",android.util.Base64.decode("R0lGODdhBAAEAIEAAAD/AAAAAAAAAAAAACwAAAAABAAEAAAICQABCBxIsCCAgAA7",0));

  images.put("0:/Themes/test/bad.png",new byte[]{1,2,3});
  a.method(type,"loadImages",Map.class).invoke(view,images);
  apply.invoke(view,(Object)a.hex("AA 08 63 00 20 00 20 01"));
  for(String ext:new String[]{"png","jpg","bmp","gif"}) {
    apply.invoke(view,(Object)a.hex("AA 05 00 00 00"));
    a.ok(Boolean.TRUE.equals(apply.invoke(view,(Object)UsbUiAudit.imageRecord("/Themes/test/a."+ext,2,3,0,0))),"native decoder supports transferred "+ext);
    Bitmap bitmap=(Bitmap)a.field(view,"bitmap");int pixel=bitmap.getPixel(3,4);
    a.ok(Color.green(pixel)>240&&Color.red(pixel)<10&&Color.blue(pixel)<10,"native "+ext+" decode produces expected image pixels");
  }
  a.ok((Long)a.method(type,"gifPreviews").invoke(view)==1,"GIF first-frame preview explicitly counted");
  a.ok(Boolean.FALSE.equals(apply.invoke(view,(Object)UsbUiAudit.imageRecord("/Themes/test/bad.png",0,0,0,0))),"invalid encoded image safely rejected");
  a.ok(Boolean.FALSE.equals(apply.invoke(view,(Object)UsbUiAudit.imageRecord("/Themes/test/../a.png",0,0,0,0))),"traversal cannot resolve cached assets");
  byte[] noPath=new byte[]{(byte)170,13,18,0,0,0,0,0,0,0,0,0,0};
  a.ok(Boolean.FALSE.equals(apply.invoke(view,(Object)noPath)),"truncated image metadata cannot render or read a file");
  apply.invoke(view,(Object)a.hex("AA 05 00 00 00"));
  Map<String,byte[]> many=new LinkedHashMap<>();for(int i=0;i<9;i++)many.put("0:/Theme/"+i+".png",new byte[131072]);
  a.method(type,"mergeImages",Map.class).invoke(view,many);
  Map<?,?> cached=(Map<?,?>)a.field(a.field(view,"images"),"source");long total=0;for(Object b:cached.values())total+=((byte[])b).length;
  a.ok(total<=1048576&&cached.size()==8,"theme changes share one MiB encoded cache, not one allocation per folder");
  a.ok(!cached.containsKey("0:/Theme/0.png")&&cached.containsKey("0:/Theme/8.png"),"cache evicts older images while retaining newest downloaded assets");
  a.method(type,"clearImages").invoke(view);
  a.ok(Boolean.FALSE.equals(apply.invoke(view,(Object)UsbUiAudit.imageRecord("/Themes/test/a.png",0,0,0,0))),"cleared asset cannot leak into later session");
 });}
}
