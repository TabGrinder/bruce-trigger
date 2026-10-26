package com.tabgrinder.brucetrigger;

import java.nio.charset.StandardCharsets;

/** Fixed, RAM-only stock-JS startup. Never accepts caller-supplied scripts or paths. */
final class UsbMirrorBridge {
  enum Step { NONE, HEADER, INPUT, BODY, END }
  Step step = Step.NONE;
  boolean active, starting, prompt, marker, exited, failed;
  boolean syncing;
  final ThemeTransfer themes = new ThemeTransfer();
  private String expectedMarker;
  private byte[] body;
  private byte[] queued;
  long drainUntil;

  static boolean supports(String version, String board) {
    return "1.16.1".equals(version) && board != null
        && board.toLowerCase(java.util.Locale.ROOT).matches("lilygo t-embed cc1101( plus)?");
  }
  boolean busy() { return step != Step.NONE; }
  boolean draining(long now) { return now < drainUntil; }
  boolean acceptsFrames(long now) {
    return active || (!syncing && starting && step == Step.END) || draining(now);
  }
  byte[] begin(boolean start, String nonce) {
    if (busy() || start == active || nonce == null || !nonce.matches("[0-9a-f]{16}")) return null;
    syncing = false;
    starting = start; prompt = marker = exited = failed = false; drainUntil = 0;
    expectedMarker = "BT_" + nonce + (start ? "_ON" : "_OFF");
    // Stopping pauses the main UI in the interpreter and gives the async queue
    // time to drain. This is mitigation, not a fix for the stock logger's races.
    String script = "(function(){delay(" + (start ? "100" : "600")
        + ");var ok=serial.cmd(\"display " + (start ? "start" : "stop")
        + "\");for(var i=0;i<3;i++){serial.println(\"" + expectedMarker
        + "\"+(ok?\"\":\"_FAIL\"));delay(10);}})();\n";
    body = (script + "EOF\n").getBytes(StandardCharsets.US_ASCII);
    step = Step.HEADER;
    return ("js run_from_buffer " + script.length() + "\n").getBytes(StandardCharsets.US_ASCII);
  }
  byte[] beginThemeSync(String nonce) {
    return beginThemeSync(nonce, null);
  }
  byte[] beginThemeSync(String nonce, String observedKey) {
    if (busy() || active || nonce == null || !nonce.matches("[0-9a-f]{16}")) return null;
    if (observedKey != null && !ObservedThemeScript.validKey(observedKey)) return null;
    syncing = true; starting = prompt = marker = exited = failed = false;
    themes.begin(nonce);
    expectedMarker = "BT_" + nonce + "_THEME_DONE";
    String script = observedKey == null ? ThemeScript.source(nonce) : ObservedThemeScript.source(nonce, observedKey);
    body = (script + "EOF\n").getBytes(StandardCharsets.US_ASCII);
    step = Step.HEADER;
    return ("js run_from_buffer " + script.length() + "\n").getBytes(StandardCharsets.US_ASCII);
  }
  void written() {
    if (step == Step.HEADER) step = Step.INPUT;
    else if (step == Step.BODY) step = Step.END;
  }
  void line(String text, long now) {
    if (step == Step.INPUT && "Reading input data from serial buffer until EOF".equals(text)) {
      queued = body; body = null; step = Step.BODY;
      return;
    }
    if (step != Step.END) return;
    if (syncing && themes.line(text)) return;
    if ((expectedMarker + "_FAIL").equals(text)) failed = true;
    if ("# ".equals(text)) prompt = true;
    if (expectedMarker.equals(text)) marker = true;
    if ("Exiting interpreter...".equals(text)) exited = true;
    if (prompt && marker && exited) {
      if (syncing && !themes.finished()) { failed = true; return; }
      if (!syncing) active = starting;
      step = Step.NONE;
      if (!syncing && !active) drainUntil = now + 1000;
    }
  }
  void handoverStopped(long now) {active=false;drainUntil=now+1000;}
  String completionEvidence(){return "phase="+step+"; marker="+marker+"; interpreter exit="+exited+"; CLI prompt="+prompt;}
  byte[] takeBody() { byte[] result = queued; queued = null; return result; }
  void reset() {
    step = Step.NONE; active = starting = prompt = marker = exited = failed = false;
    syncing = false; themes.reset();
    body = queued = null; expectedMarker = null; drainUntil = 0;
  }
  String state() { return busy() ? (syncing ? "Syncing theme" : starting ? "Starting" : "Stopping") + " / " + step : active ? "Capture requested" : "Off"; }
}
