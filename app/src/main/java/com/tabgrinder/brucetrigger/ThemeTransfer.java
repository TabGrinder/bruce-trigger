package com.tabgrinder.brucetrigger;

import java.util.*;
import java.util.zip.Adler32;

/** Bounded, nonce-scoped image transfer. Paths are cache keys, never local file paths or commands. */
final class ThemeTransfer {
  static final int MAX_FILE = 131072, MAX_TOTAL = 1048576, MAX_FILES = 18;
  private final Map<String, byte[]> images = new LinkedHashMap<>();
  private final Map<String,String> mappings = new LinkedHashMap<>();
  int primary=0xf81f, background=0;
  private String prefix, key;
  private byte[] pending;
  private int offset, total, rejected, skipped, starts;
  private boolean finished;
  private String firstFailure="None";
  void begin(String nonce) { reset(); prefix = "BT_" + nonce + "_IMG|"; }
  void reset() {
    images.clear(); mappings.clear(); primary=0xf81f;background=0;prefix = key = null; pending = null;
    offset = total = rejected = skipped = starts = 0; finished = false; firstFailure="None";
  }
  static boolean validPath(String path) {
    if (path == null || path.length() < 3 || path.length() > 115 || !path.startsWith("/")
        || path.endsWith("/") || path.contains("//") || path.contains("\\") || path.contains("|")) return false;
    for (int i=0;i<path.length();i++) if (path.charAt(i)<32 || path.charAt(i)>126) return false;
    for (String part : path.split("/")) if (part.equals(".") || part.equals("..")) return false;
    String p=path.toLowerCase(Locale.ROOT);
    return p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".png") || p.endsWith(".bmp") || p.endsWith(".gif");
  }
  boolean line(String line) {
    if (prefix == null || !line.startsWith(prefix)) return false;
    if (finished || line.length()>600) { reject("Late or oversized record"); return true; }
    String[] fields=line.substring(prefix.length()).split("\\|",-1);
    try {
      if (fields.length==4 && fields[0].equals("M")) {
        if (!Arrays.asList("wifi","ble","ethernet","rf","rfid","fm","ir","files","gps","nrf","interpreter","clock","others","connect","config","boot_img","lora").contains(fields[1])
            || !(fields[2].equals("0")||fields[2].equals("2")) || !validPath(fields[3]) || mappings.containsKey(fields[1])) { reject(); return true; }
        mappings.put(fields[1],fields[2]+":"+fields[3]);
      } else if(fields.length==3 && fields[0].equals("C")) {
        if(fields[1].matches("[0-9a-fA-F]{1,4}"))primary=Integer.parseInt(fields[1],16);
        if(fields[2].matches("[0-9a-fA-F]{1,4}"))background=Integer.parseInt(fields[2],16);
      } else if (fields.length==4 && fields[0].equals("B")) {
        if (pending!=null) reject("New image before previous image completed");
        int fs=Integer.parseInt(fields[1]), length=Integer.parseInt(fields[3]);
        if (++starts>MAX_FILES || (fs!=0 && fs!=2) || !validPath(fields[2])
            || length<1 || length>MAX_FILE || length>MAX_TOTAL-total) { reject(); return true; }
        key=fs+":"+fields[2];
        if(images.containsKey(key)) { reject("Duplicate image"); return true; }
        pending=new byte[length]; offset=0;
        total+=length; // Charge rejected/incomplete transfers too; no allocation loop bypass.
      } else if(fields.length==3 && fields[0].equals("D")) {
        if(pending==null || Integer.parseInt(fields[1])!=offset || fields[2].isEmpty()
            || fields[2].length()>256 || (fields[2].length()&1)!=0
            || fields[2].length()/2>pending.length-offset) { reject(pending==null?"Data without an image header":"Image offset/length mismatch at byte "+offset); return true; }
        for(int i=0;i<fields[2].length();i+=2) {
          int a=Character.digit(fields[2].charAt(i),16), b=Character.digit(fields[2].charAt(i+1),16);
          if(a<0 || b<0) { reject("Invalid image hex data"); return true; }
          pending[offset++]=(byte)((a<<4)|b);
        }
      } else if(fields.length==2 && fields[0].equals("E")) {
        if(pending==null || offset!=pending.length) { reject("Incomplete image at byte "+offset); return true; }
        Adler32 checksum=new Adler32(); checksum.update(pending);
        if(checksum.getValue()!=Long.parseLong(fields[1])) { reject("Image checksum mismatch"); return true; }
        images.put(key,pending); pending=null; key=null; offset=0;
      } else if(fields.length==1 && fields[0].equals("S")) { skipped++; }
      else if(fields.length==1 && fields[0].equals("Z")) {
        if(pending!=null) reject("Transfer ended before image completed"); finished=true;
      } else reject();
    } catch(NumberFormatException ex) { reject(); }
    return true;
  }
  private void reject() { reject("Invalid image metadata or limits"); }
  private void reject(String reason) {
    if(rejected==0)firstFailure=reason+(key==null?"":" · "+key);
    rejected++;pending=null;key=null;offset=0;
  }
  int missing() {
    Set<String> needed=new HashSet<>(mappings.values());needed.removeAll(images.keySet());return needed.size();
  }
  Map<String,byte[]> snapshot() { return Collections.unmodifiableMap(new LinkedHashMap<>(images)); }
  Map<String,String> mappingSnapshot() { return Collections.unmodifiableMap(new LinkedHashMap<>(mappings)); }
  boolean finished() { return finished; }
  int count() { return images.size(); }
  String summary() { return images.size()+" images; "+total+" bytes; skipped="+skipped+"; rejected="+rejected+(finished?"; transfer ended":"; not complete")+"; missing="+missing()+"; first rejection="+firstFailure; }
}
