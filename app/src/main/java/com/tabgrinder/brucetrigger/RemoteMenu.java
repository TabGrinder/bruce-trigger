package com.tabgrinder.brucetrigger;

import org.json.*;
import java.util.*;

/** Bounded stock optionsJSON snapshot. It describes menus, not a live framebuffer or tool output. */
final class RemoteMenu {
  final int width,height,active;
  final String kind,title;
  final List<String> labels;
  private RemoteMenu(int w,int h,int a,String k,String t,List<String> l) {
    width=w;height=h;active=a;kind=k;title=t;labels=Collections.unmodifiableList(l);
  }
  static RemoteMenu parseRoot(String text) {
    RemoteMenu m=parse(text);
    // Stock getOptionsJSON reads GLOBAL options. Local submenu lists are not
    // represented, even though menu/menu_title change. Never render them as true.
    return m!=null && m.kind.equals("main_menu") && m.title.equals("Main Menu") ? m : null;
  }
  static RemoteMenu parse(String text) {
    if(text==null || text.length()>2048)return null;
    try {
      JSONTokener tokener=new JSONTokener(text);
      Object root=tokener.nextValue();
      if(!(root instanceof JSONObject)||tokener.nextClean()!=0)return null;
      JSONObject o=(JSONObject)root;
      int w=integer(o,"width"),h=integer(o,"height"),a=integer(o,"active");
      String k=string(o,"menu"),t=string(o,"menu_title");
      if(w<1||h<1||w>1024||h>1024||(long)w*h>1048576
          || !Arrays.asList("main_menu","sub_menu","regular_menu").contains(k))return null;
      JSONArray options=o.getJSONArray("options");
      if(options.length()<1||options.length()>40||a<0||a>=options.length())return null;
      ArrayList<String> labels=new ArrayList<>();
      for(int i=0;i<options.length();i++) {
        JSONObject item=options.getJSONObject(i);
        if(integer(item,"n")!=i)return null;
        labels.add(string(item,"label"));
      }
      return new RemoteMenu(w,h,a,k,t,labels);
    } catch(JSONException|IllegalArgumentException ex){return null;}
  }
  private static int integer(JSONObject o,String name)throws JSONException {
    Object value=o.get(name);
    if(!(value instanceof Integer))throw new IllegalArgumentException("Integer required");
    return (Integer)value;
  }
  private static String string(JSONObject o,String name)throws JSONException {
    Object v=o.get(name);if(!(v instanceof String))throw new IllegalArgumentException("Text required");
    String s=(String)v;if(s.length()>80)throw new IllegalArgumentException("Text too long");
    for(int i=0;i<s.length();i++)if(s.charAt(i)<32 || s.charAt(i)==127)throw new IllegalArgumentException("Control character");
    return s;
  }
  boolean isFilesRun() {
    String[] expected={"JS Script Run","View File","File Info","Rename","Copy","Delete","New Folder"};
    if(active!=0 || labels.size()<expected.length || kind.equals("main_menu"))return false;
    for(int i=0;i<expected.length;i++)if(!expected[i].equals(labels.get(i)))return false;
    return true;
  }
  boolean isMegalodon() {
    return kind.equals("sub_menu") && title.equals("Others") && active==1 && labels.size()>=2
        && labels.get(0).equals("QRCodes") && labels.get(1).equals("Megalodon");
  }
  String identity(){return kind+"\n"+title+"\n"+String.join("\n",labels);}
  String selected(){return labels.get(active);}
  String imageKey(Map<String,String> mapping) {
    if(!kind.equals("main_menu"))return null;
    String[] names={"WiFi","BLE","Ethernet","RF","RFID","FM","IR","Files","GPS","NRF24","NRF24L01","JS Interpreter","Clock","Others","Connect","Config","LoRa"};
    String[] keys={"wifi","ble","ethernet","rf","rfid","fm","ir","files","gps","nrf","nrf","interpreter","clock","others","connect","config","lora"};
    for(int i=0;i<names.length;i++)if(selected().equals(names[i]))return mapping.get(keys[i]);
    return null;
  }
}
