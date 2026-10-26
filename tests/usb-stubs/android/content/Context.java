package android.content;
import android.content.pm.PackageManager;import android.hardware.usb.UsbManager;import java.util.*;
public class Context {
 public static final String USB_SERVICE="usb";public static final int RECEIVER_NOT_EXPORTED=4,MODE_PRIVATE=0;
 private final Map<String,SharedPreferences> preferences=new HashMap<>();
 public SharedPreferences getSharedPreferences(String name,int mode){return preferences.computeIfAbsent(name,k->new SharedPreferences());}
 public final UsbManager manager=new UsbManager();public final PackageManager pm=new PackageManager();
 public final Map<BroadcastReceiver,IntentFilter> receivers=new LinkedHashMap<>();public int nonExported;
 public Context getApplicationContext(){return this;}public Object getSystemService(String s){return manager;}
 public PackageManager getPackageManager(){return pm;}public String getPackageName(){return "com.test.bruce";}
 public Intent registerReceiver(BroadcastReceiver r,IntentFilter f){receivers.put(r,f);return null;}
 public Intent registerReceiver(BroadcastReceiver r,IntentFilter f,int flags){if(flags!=RECEIVER_NOT_EXPORTED)throw new AssertionError();nonExported++;return registerReceiver(r,f);}
 public void unregisterReceiver(BroadcastReceiver r){receivers.remove(r);}
 public void broadcast(Intent i){for(Map.Entry<BroadcastReceiver,IntentFilter> e:new ArrayList<>(receivers.entrySet()))if(e.getValue().action.equals(i.action))e.getKey().onReceive(this,i);}
}
