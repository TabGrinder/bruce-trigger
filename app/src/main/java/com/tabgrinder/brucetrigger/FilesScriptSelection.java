package com.tabgrinder.brucetrigger;

import java.nio.charset.StandardCharsets;

/** Bounded File Info acquisition from stock drawing records, not terminal lines. */
final class FilesScriptSelection {
  private static final int PATH_LIMIT = 180;
  private static final int STOCK_LINE_CHARS = 50; // (320 - two 10px borders) / GLCD width 6
  String path, infoPath;
  boolean observed, menuVisible, infoReading, infoTitle, collecting, infoPathEnded, infoComplete;
  boolean nativeGameMenuVisible;
  String status = "No Files launch requested";
  String lastAttempt = "None";
  private long connection, attemptConnection;
  long revision, clears, queries, infoRecords;
  private int pathY = -1, lastLineChars;
  private final StringBuilder pathBuffer = new StringBuilder();

  void clear() {
    path = infoPath = null;
    observed = menuVisible = nativeGameMenuVisible = infoReading = infoTitle = collecting = infoPathEnded = infoComplete = false;
    pathBuffer.setLength(0);
    pathY = -1;
    lastLineChars = 0;
    revision++;
  }

  void reset() {
    clear();
    connection++;
    queries = infoRecords = clears = 0;
    status = "No Files launch requested";
    // A new USB session must not erase the outcome of the previous Files attempt.
  }

  String summary() {
    return status + "; SELECT checks=" + queries + "; File Info draw records=" + infoRecords
        + "; title=" + infoTitle + "; path chars=" + pathBuffer.length()
        + "; metadata complete=" + infoComplete;
  }

  void mark(String value) { status = value; lastAttempt = summary(); attemptConnection=connection; }

  String historySummary() {
    return lastAttempt.equals("None") ? lastAttempt
        : (attemptConnection==connection?"Current connection: ":"Previous connection: ")+lastAttempt;
  }

  void beginInfo() {
    infoReading = true;
    infoTitle = collecting = infoPathEnded = infoComplete = false;
    infoPath = null;
    pathBuffer.setLength(0);
    pathY = -1;
    lastLineChars = 0;
    mark("Reading selected File Info");
  }

  void abort(String reason) {
    mark(reason);
    clear();
  }

  void frame(byte[] b) {
    if (!FrameValidator.valid(b) || b.length > 128) return;
    int op = b[2] & 255;
    if (op == 0) { clears++; menuVisible = nativeGameMenuVisible = false; if (infoReading) beginInfo(); return; }
    if (op < 14 || op > 17) return;
    String text = new String(b, 13, b.length - 13, StandardCharsets.UTF_8).replace("\0", "");
    String label = text.trim();
    if (label.startsWith(">")) label = label.substring(1).trim();
    if (label.equals("JS Script Run")) menuVisible = true;
    if (label.equals("Megalodon")) nativeGameMenuVisible = true;
    if (!infoReading || infoComplete) return;
    infoRecords++;
    if (label.equals("FILE INFO")) { infoTitle = true; mark("File Info title received"); return; }
    if (!infoTitle || op != 17) return;
    int x = FrameDecoder.u16(b, 3), y = FrameDecoder.u16(b, 5);
    if (x != 10 || FrameDecoder.u16(b, 7) != 1) return;
    String line = text.replace("\r", "").replace("\n", "");

    // An empty print can be absent from a draw stream. Bytes is the explicit
    // next field; its row is one blank GLCD line below the final path row.
    if (pathY >= 0 && y == pathY + 16 && line.matches("Bytes: [0-9]{1,10}")) {
      infoPathEnded = infoComplete = true;
      collecting = false;
      infoPath = pathBuffer.toString();
      if (!valid(infoPath)) { infoPath = null; mark("Unsupported File Info path"); }
      else mark("Selected path and byte count received");
      return;
    }
    if (line.isEmpty()) return; // Layout whitespace is never protocol framing.
    if (pathY < 0) {
      if (!line.startsWith("Path: ")) return;
      collecting = true;
      lastLineChars = line.length();
      line = line.substring(6);
    } else {
      // Stock padprintln wraps at 50 characters. Reject missing rows rather
      // than silently concatenating a different, truncated file path.
      if (!collecting || lastLineChars != STOCK_LINE_CHARS || y != pathY + 8) return;
      lastLineChars = line.length();
    }
    if (lastLineChars > STOCK_LINE_CHARS || pathBuffer.length() + line.length() > PATH_LIMIT) {
      infoReading = false;
      mark("File Info path exceeds bridge bounds");
      return;
    }
    pathBuffer.append(line);
    pathY = y;
    mark("File Info path received; waiting for byte count");
  }

  void observe(String line) {
    int separator = line.indexOf(" --> ");
    if (separator < 0 || infoReading) return;
    clear();
    observed = true;
    String candidate = line.substring(0, separator);
    if (valid(candidate) && line.substring(separator + 5).equals(candidate.substring(candidate.lastIndexOf('/') + 1)))
      path = candidate;
  }

  static boolean valid(String path) {
    if (path == null || path.length() < 5 || path.length() > PATH_LIMIT || path.charAt(0) != '/'
        || path.contains("//") || path.contains("/../") || path.contains("/./")
        || !(path.endsWith(".js") || path.endsWith(".bjs"))) return false;
    for (int i = 0; i < path.length(); i++) {
      char c = path.charAt(i);
      if (c < 32 || c > 126 || c == '\\' || c == '|' || c == '\"' || c == '\'') return false;
    }
    return true;
  }
}
