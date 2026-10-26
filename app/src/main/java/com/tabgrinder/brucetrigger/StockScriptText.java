package com.tabgrinder.brucetrigger;

/** Adds safe line boundaries to fixed ASCII JS without splitting quoted strings. */
final class StockScriptText {
  static String lines(String script) {
    StringBuilder output = new StringBuilder(script.length() + 128);
    char quote = 0;
    boolean escaped = false;
    for (int i = 0; i < script.length(); i++) {
      char c = script.charAt(i);
      if (c > 127 || c == '\r') throw new IllegalArgumentException("Fixed script must be ASCII/LF");
      output.append(c);
      if (quote != 0) {
        if (escaped) escaped = false;
        else if (c == '\\') escaped = true;
        else if (c == quote) quote = 0;
      } else if (c == '\'' || c == '"') quote = c;
      else if (c == ';' || c == '{' || c == '}') output.append('\n');
    }
    if (quote != 0) throw new IllegalArgumentException("Unclosed fixed string");
    String result = output.toString();
    for (String line : result.split("\n", -1)) {
      if (line.length() > 240 || line.equals("EOF"))
        throw new IllegalArgumentException("Fixed script line exceeds reviewed serial input size");
    }
    return result.endsWith("\n") ? result : result + "\n";
  }
  private StockScriptText() {}
}
