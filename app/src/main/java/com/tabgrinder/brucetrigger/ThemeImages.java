package com.tabgrinder.brucetrigger;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.util.*;

/** Private per-connection cache. Decodes only verified transferred bytes, never a wire-supplied file path. */
final class ThemeImages {
  private Map<String, byte[]> source = Collections.emptyMap();
  private final LinkedHashMap<String,Bitmap> decoded = new LinkedHashMap<>(16,.75f,true);
  private final Set<String> invalid = new HashSet<>();
  private int bytes;
  private String failure = "None";
  void load(Map<String,byte[]> images) { clear(); source = images; }
  void merge(Map<String,byte[]> images) {
    LinkedHashMap<String,byte[]> next = new LinkedHashMap<>(source);
    for (Map.Entry<String,byte[]> entry : images.entrySet()) {
      next.remove(entry.getKey()); next.put(entry.getKey(),entry.getValue());
    }
    long size=0; for(byte[] data:next.values())size+=data.length;
    while((size>ThemeTransfer.MAX_TOTAL || next.size()>72) && !next.isEmpty()) {
      String key=next.keySet().iterator().next();size-=next.remove(key).length;
    }
    load(Collections.unmodifiableMap(next));
  }
  String missingKey(byte[] record) {
    String key=recordKey(record);
    return key!=null && !source.containsKey(key) ? key : null;
  }
  void clear() {
    for(Bitmap b:decoded.values())b.recycle();
    decoded.clear(); invalid.clear(); source=Collections.emptyMap(); bytes=0; failure="None";
  }
  static String recordKey(byte[] record) {
    return NativeImageRecord.key(record);
  }
  String failure() { return failure; }
  private Bitmap unavailable(String reason) { failure=reason; return null; }
  Bitmap get(byte[] record) {
    failure="None";
    String key=recordKey(record);
    if(key==null)return unavailable("Invalid stock image record");
    return getKey(key);
  }
  Bitmap getKey(String key) {
    failure="None";
    if(!ObservedThemeScript.validKey(key))return unavailable("Invalid theme image key");
    if(invalid.contains(key))return unavailable("Theme image decoding rejected · "+key);
    Bitmap cached=decoded.get(key); if(cached!=null)return cached;
    byte[] data=source.get(key);if(data==null)return unavailable("Theme asset not cached · "+key);
    Bitmap candidate=null;
    try {
      BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
      BitmapFactory.decodeByteArray(data,0,data.length,bounds);
      if(bounds.outWidth<1 || bounds.outHeight<1 || bounds.outWidth>1024 || bounds.outHeight>1024
          || (long)bounds.outWidth*bounds.outHeight>262144) { invalid.add(key);return unavailable("Unsupported image or dimensions · "+key); }
      BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;
      candidate=BitmapFactory.decodeByteArray(data,0,data.length,options);
      if(candidate==null) { invalid.add(key);return unavailable("Theme image decoding failed · "+key); }
      int size=candidate.getAllocationByteCount();
      while(bytes+size>4194304 && !decoded.isEmpty()) {
        Map.Entry<String,Bitmap> first=decoded.entrySet().iterator().next();
        bytes-=first.getValue().getAllocationByteCount();first.getValue().recycle();decoded.remove(first.getKey());
      }
      decoded.put(key,candidate);bytes+=size;return candidate;
    } catch(RuntimeException | OutOfMemoryError e) {
      if(candidate!=null)candidate.recycle();invalid.add(key);return unavailable("Theme image allocation or decoding failed · "+key);
    }
  }
  int count() { return source.size(); }
}
