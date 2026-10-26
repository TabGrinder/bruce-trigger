package com.tabgrinder.brucetrigger;

/** Bounded transient diagnostic output, including line-rate limits before UI rendering. */
public final class ConsoleBuffer {
  private final StringBuilder text = new StringBuilder();
  private final WindowBudget lines = new WindowBudget(128, 16384, 1000);
  private boolean limited;
  private String cached="";

  public boolean append(String raw, long now) {
    if (raw == null) return false;
    int length = Math.min(raw.length(), 2048);
    if (!lines.allow(length, now)) {
      if(!limited){limited=true;cached=null;}
      return false;
    }
    for (int i = 0; i < length; ) {
      int cp = raw.codePointAt(i);
      i += Character.charCount(cp);
      int type = Character.getType(cp);
      if (type == Character.FORMAT
          || type == Character.LINE_SEPARATOR
          || type == Character.PARAGRAPH_SEPARATOR
          || (Character.isISOControl(cp) && cp != 9)) continue;
      text.appendCodePoint(cp);
    }
    text.append('\n');
    cached=null;
    if (text.length() > 8192) text.delete(0, text.length() - 8192);
    return true;
  }

  public String snapshot() {
    if(cached==null)cached=(limited ? "[Output limited]\n" : "") + text.toString();
    return cached;
  }

  public void clear() {
    text.setLength(0);
    limited = false;
    lines.reset();
    cached="";
  }
}
