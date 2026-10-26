package com.tabgrinder.brucetrigger.audit;

import android.content.Context;
import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import org.json.JSONObject;

/** Existing record compatibility, bounded reads, recovery and deletion containment. */
final class SavedNotesAudit {
  static void run(AuditRunner a) throws Exception {
    Context context = a.getTargetContext();
    Class<?> type = a.targetClass("SessionStore");
    Object store = a.construct(type);
    Method list = a.method(type, "list"), read = a.method(type, "read", File.class),
        delete = a.method(type, "delete", File.class), hasNotes = a.method(type, "hasSavedNotes");
    int baseline = ((List<?>)list.invoke(store)).size();
    String content = "Unicode ñ 日本語 and <script>literal</script>";
    File record = SavedNoteFixtures.write(context, UUID.randomUUID().toString(), "Saved note", content);
    try {
      a.ok(((List<?>)list.invoke(store)).contains(record), "existing note is listed");
      a.ok(Boolean.TRUE.equals(hasNotes.invoke(store)), "existing note enables Settings access");
      a.ok(content.equals(((JSONObject)read.invoke(store, record)).getString("notes")),
          "Unicode and markup remain literal saved notes");
      a.ok(Boolean.TRUE.equals(delete.invoke(store, record)), "explicit saved-note deletion succeeds");
      a.ok(((List<?>)list.invoke(store)).size() == baseline, "other records remain unchanged");
      try (FileOutputStream out = new FileOutputStream(record)) { out.write(new byte[150001]); }
      a.ok(a.fails(read, store, record), "oversized record is rejected");
      a.ok(record.length() == 150001, "rejected read retains the original record");
    } finally { delete.invoke(store, record); }

    File outside = new File(context.getFilesDir(), "runtime-audit-outside.json");
    File backup = new File(record.getPath() + ".bak");
    try {
      try (FileOutputStream out = new FileOutputStream(outside)) { out.write("{}".getBytes("UTF-8")); }
      a.ok(a.fails(read, store, outside), "read rejects an out-of-directory path");
      a.ok(Boolean.FALSE.equals(delete.invoke(store, outside)), "delete rejects an out-of-directory path");
      android.system.Os.symlink(outside.getAbsolutePath(), backup.getAbsolutePath());
      a.ok(a.fails(read, store, record), "backup symlink cannot escape the notes directory");
      a.ok(Boolean.FALSE.equals(delete.invoke(store, record)), "delete refuses an escaped backup symlink");
      a.ok(outside.exists(), "rejected delete retains the outside file");
    } finally { backup.delete(); outside.delete(); }

    File recovery = SavedNoteFixtures.write(context, UUID.randomUUID().toString(), "Backup", "recover me");
    File orphan = new File(recovery.getPath() + ".bak");
    File pending = new File(recovery.getPath() + ".new");
    try {
      a.ok(recovery.renameTo(orphan), "orphan AtomicFile backup fixture created");
      a.ok(Boolean.TRUE.equals(hasNotes.invoke(store)), "orphan backup enables saved-note access");
      a.ok(((List<?>)list.invoke(store)).contains(recovery), "orphan backup is listed once");
      a.ok("recover me".equals(((JSONObject)read.invoke(store, recovery)).getString("notes")),
          "AtomicFile recovers orphan backup content");
      a.ok(recovery.exists() && !orphan.exists(), "recovered backup becomes the base record");
      try (FileOutputStream out = new FileOutputStream(pending)) { out.write(1); }
      a.ok(((List<?>)list.invoke(store)).size() == baseline + 1, "incomplete .new file is ignored");
      a.ok(Boolean.TRUE.equals(delete.invoke(a.construct(type), recovery)), "another store instance can delete a record");
      a.ok(((List<?>)list.invoke(store)).size() == baseline, "deletion is visible across store instances");
      try (FileOutputStream out = new FileOutputStream(orphan)) { out.write(new byte[150001]); }
      a.ok(a.fails(read, store, recovery), "backup recovery also enforces the byte limit");
    } finally { delete.invoke(store, recovery); orphan.delete(); pending.delete(); }
  }
}
