package com.tabgrinder.brucetrigger;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Access to existing notes. Keep the sessions directory and schema for installed users. */
final class SessionStore {
  private static final int MAX_RECORD_BYTES = 150000;
  private final File dir, filesRoot;
  private static final Object LOCK = new Object();
  private static final java.util.regex.Pattern RECORD=java.util.regex.Pattern.compile("[a-fA-F0-9-]{36}[.]json");

  SessionStore(Context c) {
    filesRoot = c.getFilesDir();
    dir = new File(filesRoot, "sessions");
  }

  boolean hasSavedNotes() {
    synchronized (LOCK) {
      String[] names = dir.list();
      if (names == null) return false;
      for (String name : names) {
        if (name.endsWith(".json.bak")) name = name.substring(0, name.length() - 4);
        if (RECORD.matcher(name).matches()) return true;
      }
      return false;
    }
  }

  List<File> list() {
    synchronized (LOCK) {
      File[] all = dir.listFiles();
      if (all == null) return Collections.emptyList();
      Map<String, File> records = new HashMap<>();
      for (File candidate : all) {
        String name = candidate.getName();
        if (name.endsWith(".json.bak")) name = name.substring(0, name.length() - 4);
        if (RECORD.matcher(name).matches()) records.put(name, new File(dir, name));
      }
      List<File> result = new ArrayList<>(records.values());
      Map<File,Long> times=new HashMap<>();
      for(File f:result)times.put(f,recordTime(f));
      result.sort((a,b)->{int order=Long.compare(times.get(b),times.get(a));
        return order!=0?order:a.getName().compareTo(b.getName());});
      return result;
    }
  }

  private long recordTime(File base) {
    return Math.max(base.lastModified(), new File(base.getPath() + ".bak").lastModified());
  }

  private void validateRecord(File f) throws IOException {
    if (f == null
        || !RECORD.matcher(f.getName()).matches()
        || !f.getAbsoluteFile().getParentFile().equals(dir.getAbsoluteFile()))
      throw new IOException("Invalid session path");
    File canonicalDir = dir.getCanonicalFile();
    if (!canonicalDir.equals(new File(filesRoot.getCanonicalFile(), "sessions")))
      throw new IOException("Invalid session directory");
    for (String suffix : new String[] {"", ".bak", ".new"}) {
      File leaf = new File(dir, f.getName() + suffix);
      if (!leaf.getCanonicalFile().equals(new File(canonicalDir, leaf.getName())))
        throw new IOException("Session link rejected");
    }
  }

  JSONObject read(File f) throws Exception {
    synchronized (LOCK) {
      validateRecord(f);
      if (f.length() > MAX_RECORD_BYTES) throw new IOException("Invalid session");
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (InputStream in = new AtomicFile(f).openRead()) {
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
          if (out.size() + n > MAX_RECORD_BYTES) throw new IOException("Session too large");
          out.write(buf, 0, n);
        }
      }
      return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
    }
  }

  boolean delete(File f) throws IOException {
    synchronized (LOCK) {
      try {
        validateRecord(f);
      } catch (IOException invalid) {
        return false;
      }
      new AtomicFile(f).delete();
      return !f.exists()
          && !new File(f.getPath() + ".bak").exists()
          && !new File(f.getPath() + ".new").exists();
    }
  }
}
