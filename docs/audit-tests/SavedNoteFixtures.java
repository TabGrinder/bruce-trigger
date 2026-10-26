package com.tabgrinder.brucetrigger.audit;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Test-only records representing data already present before the first public release. */
final class SavedNoteFixtures {
  static File write(Context context, String id, String title, String notes) throws Exception {
    if (!id.matches("[a-fA-F0-9-]{36}")) throw new IllegalArgumentException("Fixture ID");
    File directory = new File(context.getFilesDir(), "sessions");
    if (!directory.isDirectory() && !directory.mkdirs())
      throw new java.io.IOException("Cannot create fixture directory");
    File file = new File(directory, id + ".json");
    JSONObject data = new JSONObject().put("title", title).put("notes", notes)
        .put("created", System.currentTimeMillis()).put("schema", 1);
    try (FileOutputStream out = new FileOutputStream(file)) {
      out.write(data.toString().getBytes(StandardCharsets.UTF_8));
    }
    return file;
  }
}
