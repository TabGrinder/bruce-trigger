package com.tabgrinder.brucetrigger;

import java.nio.charset.StandardCharsets;

/** Protocol-negotiation tests using deterministic firmware replies; no hardware claim. */
public final class CapabilityProbeRegressionTest {
  private static final String NONCE = "0123456789abcdef";
  private static int checks;
  private static void ok(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }
  private static byte[] ascii(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
  private static UsbSerialSession open(String version, String board) {
    UsbSerialSession session = new UsbSerialSession();
    session.open(0);
    byte[] info = session.begin("info", 600);
    ok(info != null, "information request starts");
    session.written(info.length, info.length, 601);
    session.receive(ascii("Bruce v" + version + "\nDevice: " + board + "\n# "), 602);
    ok(session.verified, "firmware identity parsed");
    return session;
  }
  private static UsbSerialSession probe(String version, String verdict) {
    UsbSerialSession session = open(version, "LILYGO T-Embed CC1101 Plus");
    ok(!session.mirror.probeMirrorReady, "identity alone cannot enable scripts");
    ok(session.beginMirror(true, NONCE, 1000) == null, "capture blocked until capability proof");
    byte[] header = session.beginProbe(NONCE, 1000);
    ok(header != null, "capability probe allowed after identity");
    ok(new String(header, StandardCharsets.US_ASCII).startsWith("js run_from_buffer "), "only bounded stock JS command issued");
    session.written(header.length, header.length, 1001);
    session.receive(ascii("Reading input data from serial buffer until EOF\n"), 1002);
    byte[] body = session.takeScriptBody();
    ok(body != null && body.length < 512, "probe body is bounded");
    String payload = new String(body, StandardCharsets.US_ASCII);
    ok(payload.contains("typeof serial.cmd") && payload.contains("typeof require") && payload.contains("st.readdir"), "probe inspects JS and read-only storage APIs");
    ok(!payload.contains("display start") && !payload.contains("storage.write") && !payload.contains("nav select"), "probe has no capture or command side effects");
    ok(payload.endsWith("})();\nEOF\n"), "probe script is fixed and EOF framed");
    session.written(body.length, body.length, 1003);
    session.receive(ascii("BT_" + NONCE + "_PROBE_" + verdict + "\nExiting interpreter...\n# "), 1010);
    ok(session.stage == UsbSerialSession.Stage.OPEN && session.verified, "complete probe leaves verified link open");
    ok(session.mirror.probeFinished, "capability result is retained per session");
    return session;
  }
  public static void main(String[] args) {
    for (String v : new String[] {"1.16.1", "1.17", "1.18", "2.0.0", "23.7.1-beta"}) {
      ok(FirmwareCompatibility.supportsIdentity(v, "Lilygo T-Embed CC1101 Plus"), "future version identity " + v);
      UsbSerialSession s = probe(v, "FULL");
      ok(s.mirror.probeMirrorReady && s.mirror.probeThemeReady, "full capabilities across " + v);
      ok(s.beginThemeSync(NONCE, 1500) != null, "theme path allowed after full proof " + v);
    }
    UsbSerialSession mirrorOnly = probe("1.17", "MIRROR");
    ok(mirrorOnly.mirror.probeMirrorReady && !mirrorOnly.mirror.probeThemeReady, "separate mirror/theme capabilities");
    ok(mirrorOnly.beginThemeSync(NONCE, 1500) == null, "no storage API blocks only theme sync");
    ok(mirrorOnly.beginMirror(true, NONCE, 1500) != null, "native mirror still allowed");
    UsbSerialSession navigationOnly = probe("1.17", "FAIL");
    ok(!navigationOnly.mirror.probeMirrorReady && !navigationOnly.mirror.probeThemeReady, "unsupported JS cannot mirror");
    ok(navigationOnly.beginMirror(true, NONCE, 1500) == null, "no risky capture after failed probe");
    ok(navigationOnly.beginThemeSync(NONCE, 1500) == null, "no theme script after failed probe");
    ok(navigationOnly.begin("nav next", 1500) != null, "navigation remains available after safely completed failure");
    UsbSerialSession replay = probe("1.17", "FULL");
    ok(replay.beginProbe(NONCE, 1500) == null, "no redundant probe in a verified session");
    replay.close();
    ok(!replay.mirror.probeMirrorReady && !replay.mirror.probeFinished, "new session forgets capability proof");
    ok(!FirmwareCompatibility.supportsIdentity("Unknown", "LILYGO T-Embed CC1101"), "missing firmware rejected");
    ok(!FirmwareCompatibility.supportsIdentity("1.17", "Other ESP32"), "unreviewed board rejected");
    ok(!FirmwareCompatibility.supportsIdentity("1.17", "LILYGO T-Embed CC1101\nreboot"), "newlines rejected");
    UsbSerialSession stale = open("1.17", "LILYGO T-Embed CC1101");
    byte[] h = stale.beginProbe(NONCE, 1000);
    stale.written(h.length, h.length, 1001);
    stale.receive(ascii("Reading input data from serial buffer until EOF\n"), 1002);
    byte[] b = stale.takeScriptBody();
    stale.written(b.length, b.length, 1003);
    stale.receive(ascii("BT_ffffffffffffffff_PROBE_FULL\nExiting interpreter...\n# "), 1010);
    ok(!stale.mirror.probeFinished, "stale nonce never authorizes a script");
    stale.tick(14000);
    ok(stale.stage == UsbSerialSession.Stage.BLOCKED, "incomplete probe fails closed without replay");
    System.out.println("PASS " + checks + " dynamic firmware-compatibility and JS probe assertions (deterministic host fixtures)");
  }
}
