package android.content;
import java.util.HashMap;
import java.util.Map;
public final class SharedPreferences {
 private final Map<String,Boolean> values=new HashMap<>();
 public boolean getBoolean(String key,boolean fallback){return values.getOrDefault(key,fallback);}
 public Editor edit(){return new Editor();}
 public final class Editor {
  private final Map<String,Boolean> pending=new HashMap<>();
  public Editor putBoolean(String key,boolean value){pending.put(key,value);return this;}
  public void apply(){values.putAll(pending);}
 }
}
