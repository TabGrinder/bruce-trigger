package com.tabgrinder.brucetrigger;

/** Display-only sanitation. Never treats a radio name as authenticated identity. */
public final class DisplayText {
  public static String deviceName(String raw) {
    if (raw == null) return "Bruce candidate";
    StringBuilder out = new StringBuilder();
    int count = 0;
    for (int i = 0; i < raw.length() && count < 64; ) {
      int cp = raw.codePointAt(i);
      i += Character.charCount(cp);
      int type = Character.getType(cp);
      if (Character.isISOControl(cp)
          || type == Character.FORMAT
          || type == Character.LINE_SEPARATOR
          || type == Character.PARAGRAPH_SEPARATOR
          || type == Character.SURROGATE) continue;
      out.appendCodePoint(cp);
      count++;
    }
    String result = out.toString().trim();
    return result.isEmpty() ? "Bruce candidate" : result;
  }

  private DisplayText() {}
}
