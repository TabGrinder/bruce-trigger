package com.tabgrinder.brucetrigger;

import java.nio.charset.StandardCharsets;

/** Fixed, RAM-only stock-JS startup. Never accepts caller-supplied scripts or paths. */
final class UsbMirrorBridge {
  enum Step { NONE, HEADER, INPUT, BODY, END }
  Step step = Step.NONE;
  boolean active, starting, prompt, marker, exited, failed;
  boolean syncing, probing;
  boolean probeFinished, probeMirrorReady, probeThemeReady;
  final ThemeTransfer themes = new ThemeTransfer();
  private String expectedMarker;
  private byte[] body;
  private byte[] queued;
  long drainUntil;

  static boolean supports(String version, String board) {
    return FirmwareCompatibility.supportsIdentity(version, board);
  }

  /** Minimal, fixed RAM-only capability handshake. No display or storage commands. */
  byte[] beginProbe(String nonce) {
    if (busy() || active || probeFinished || nonce == null || !nonce.matches("[0-9a-f]{16}")) return null;
    probing = true; syncing = starting = prompt = marker = exited = failed = false;
    probeMirrorReady = probeThemeReady = false;
    expectedMarker = "BT_" + nonce + "_PROBE";
    String script = "(function(){var s=typeof serial!=='undefined'&&typeof serial.println==='function';"
        + "var m=s&&typeof serial.cmd==='function'&&typeof delay==='function';"
        + "var t=false;try{if(m&&typeof require==='function'){var st=require('storage');"
        + "t=!!st&&typeof st.read==='function'&&typeof st.readdir==='function';}}catch(e){}"
        + "if(s)serial.println('" + expectedMarker + "'+(m?(t?'_FULL':'_MIRROR'):'_FAIL'));})();\n";
    body = (script + "EOF\n").getBytes(StandardCharsets.US_ASCII);
    step = Step.HEADER;
    return ("js run_from_buffer " + script.length() + "\n").getBytes(StandardCharsets.US_ASCII);
  }
  boolean busy() { return step != Step.NONE; }
  boolean draining(long now) { return now < drainUntil; }
  boolean acceptsFrames(long now) {
    return active || (!syncing && starting && step == Step.END) || draining(now);
  }
  byte[] begin(boolean start, String nonce) {
    if (busy() || start == active || nonce == null || !nonce.matches("[0-9a-f]{16}")) return null;
    syncing = probing = false;
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
    probing = false; syncing = true; starting = prompt = marker = exited = failed = false;
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
    if (probing) {
      if ((expectedMarker + "_FULL").equals(text)) {
        marker = true; probeMirrorReady = probeThemeReady = true;
      } else if ((expectedMarker + "_MIRROR").equals(text)) {
        marker = true; probeMirrorReady = true;
      } else if ((expectedMarker + "_FAIL").equals(text)) {
        marker = true;
      }
    } else {
      if ((expectedMarker + "_FAIL").equals(text)) failed = true;
      if (expectedMarker.equals(text)) marker = true;
    }
    if ("# ".equals(text)) prompt = true;
    if ("Exiting interpreter...".equals(text)) exited = true;
    if (prompt && marker && exited) {
      boolean wasProbe = probing;
      if (syncing && !themes.finished()) { failed = true; return; }
      if (probing) {
        probeFinished = true;
        probing = false;
      } else if (!syncing) {
        active = starting;
      }
      step = Step.NONE;
      if (!syncing && !active && !wasProbe) drainUntil = now + 1000;
    }
  }
  void handoverStopped(long now) {active=false;drainUntil=now+1000;}
  String completionEvidence(){return "phase="+step+"; marker="+marker+"; interpreter exit="+exited+"; CLI prompt="+prompt;}
  byte[] takeBody() { byte[] result = queued; queued = null; return result; }
  void reset() {
    step = Step.NONE; active = starting = prompt = marker = exited = failed = false;
    syncing = probing = probeFinished = probeMirrorReady = probeThemeReady = false;
    themes.reset();
    body = queued = null; expectedMarker = null; drainUntil = 0;
  }
  String probeState() {
    return probing ? "Checking JavaScript bridge" : !probeFinished ? "Not checked"
        : probeThemeReady ? "Mirror and theme transfer available"
        : probeMirrorReady ? "Mirror available; theme transfer not detected"
        : "No compatible JavaScript bridge";
  }
  String state() { return busy() ? (probing ? "Probing" : syncing ? "Syncing theme" : starting ? "Starting" : "Stopping") + " / " + step : active ? "Capture requested" : "Off"; }
}
