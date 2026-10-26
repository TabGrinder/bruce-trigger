package com.tabgrinder.brucetrigger;

import java.nio.charset.StandardCharsets;

/** Owns one explicitly selected script session; cannot attach to an already running interpreter. */
final class UsbGameBridge {
  private enum Upload { NONE, HEADER, INPUT, BODY, START }
  private Upload upload = Upload.NONE;
  private String prefix, inputPrefix;
  private byte[] body, queued;
  private boolean prompt, searched, on, terminal, exited;
  boolean active, stopRequested, attempted, nativeUi;
  boolean captureHandover, captureStopped;
  long nativeUiAt;
  private final java.util.ArrayDeque<Long> nativeBoundaries=new java.util.ArrayDeque<>();
  long takeNativeBoundary(){return nativeBoundaries.isEmpty()?-1:nativeBoundaries.removeFirst();}
  String filePath, reason;
  String outcome = "Not started";
  String lastInput="None", termination="None";
  long activeAt, keyReads;
  String lastKeyRead="None";
  String inputScope(){return prefix;}
  private boolean exitExpected;
  private long exitDeadline;
  final GameScreenStream screen = new GameScreenStream();

  boolean uploading() { return upload != Upload.NONE; }
  boolean engaged() { return uploading() || active || attempted && !exited; }
  boolean ended() { return attempted && exited && terminal; }
  boolean awaitingExit() { return engaged() && exitExpected; }
  void expectExit(long now) {
    if (!exitExpected) { exitExpected = true; exitDeadline = now + 6500; }
  }
  boolean exitTimedOut(long now) { return awaitingExit() && now >= exitDeadline; }

  byte[] beginFiles(String template,String nonce,String path) {
    return beginFiles(template,nonce,path,false);
  }
  byte[] beginFiles(String template,String nonce,String path,boolean capture) {
    if(engaged())return null;
    return beginSource(FilesGameScript.source(template,nonce,path,capture),nonce,path,capture);
  }

  byte[] beginMegalodon(String template,String nonce,boolean capture) {
    if(engaged())return null;
    return beginSource(MegalodonScript.source(template,nonce,capture),nonce,"Megalodon (RAM)",capture);
  }

  private byte[] beginSource(String script,String nonce,String path,boolean capture) {
    reset();
    captureHandover=capture;captureStopped=!capture;filePath=path;
    prefix = "BT_" + nonce + "_GAME|"; inputPrefix="BT_"+nonce+"_KEY|";
    attempted = true;
    body = (script + "EOF\n").getBytes(StandardCharsets.US_ASCII);
    upload = Upload.HEADER;
    outcome = "Megalodon (RAM)".equals(path)?"Starting Megalodon RAM adaptation":"Starting selected Files script";
    return ("js run_from_buffer " + script.length() + "\n").getBytes(StandardCharsets.US_ASCII);
  }

  void written() {
    if (upload == Upload.HEADER) upload = Upload.INPUT;
    else if (upload == Upload.BODY) upload = Upload.START;
  }

  boolean line(String text, long now) {
    if (!engaged()) return false;
    // Optional read telemetry is isolated from recoverable graphics and CLI receipts.
    // Truncated/malformed telemetry cannot stop a game or acknowledge a command.
    if(inputPrefix!=null && text.startsWith(inputPrefix)) {
      String value=text.substring(inputPrefix.length());
      if(active && !terminal && value.matches("(prev|next|select|esc|any)\\|[1-9][0-9]{0,8}")) {
        String[] key=value.split("\\|");long count=Long.parseLong(key[1]);
        if(count>keyReads){keyReads=count;lastKeyRead=key[0];}
      }
      return true;
    }
    if (upload == Upload.INPUT && text.equals("Reading input data from serial buffer until EOF")) {
      queued = body; body = null; upload = Upload.BODY; return true;
    }
    if (text.equals("# ") && upload == Upload.START) prompt = true;
    if (text.equals("Exiting interpreter...")) {
      exited = true; active = false; nativeUi=false;
      if(!terminal)termination="Interpreter returned without final bridge marker";
      if (!terminal) { terminal = true; outcome = on ? "Game exited" : "Interpreter exited before a final game marker"; }
    }
    boolean record = prefix != null && text.startsWith(prefix);
    if (record) {
      String value = text.substring(prefix.length());
      if ((upload != Upload.START && upload != Upload.NONE) || terminal) {
        screen.line("INVALID", now); return true;
      }
      if(value.equals("CAPTURE_OFF") && captureHandover && !on){captureStopped=true;}
      else if (value.equals("SEARCH") && captureStopped && !searched && !on) {
        searched = true; outcome = "Checking the selected file";
      }
      else if (value.equals("ON") && captureStopped && searched && !on) {
        on = active = true; activeAt = now; outcome = runningLabel();

      } else if(active && value.equals("UI_ON") && !nativeUi) {
        if(nativeBoundaries.size()>=8){screen.line("INVALID",now);return true;}
        nativeBoundaries.addLast(screen.drawingFrames);nativeUi=true;nativeUiAt=now;outcome="Script dialog running on Bruce";
      } else if(active && value.equals("UI_OFF") && nativeUi) {
        nativeUi=false;activeAt=now;outcome=runningLabel();
      } else if(value.startsWith("REASON|") && value.length()<=67) {
        reason=DisplayText.deviceName(value.substring(7));
      } else if (value.equals("ERROR") || value.equals("UNSUPPORTED") || value.equals("AMBIGUOUS")
          || searched && !on && value.equals("NOT_FOUND")
          || on && (value.equals("DONE") || value.equals("IDLE") || value.equals("STOP"))) {
        terminal = true; active = false;
        termination=value;
        expectExit(now);
        outcome = value.equals("UNSUPPORTED") ? "Unsupported JS operation: "+reason
            : value.equals("AMBIGUOUS") ? "Same path exists on SD and LittleFS; no file was executed"
            : value.equals("DONE") ? "Game exited"
            : value.equals("IDLE") ? "Game ended after inactivity or the session limit"
            : value.equals("NOT_FOUND") ? "Selected JS file was not found"
            : value.equals("STOP") ? "Game stopped" : on ? "Game bridge error: "+(reason==null?"unknown":reason) : "Game bridge could not start: "+(reason==null?"unknown":reason);
      } else if (active) screen.line(value, now);
      else screen.line("INVALID", now);
    }
    if (upload == Upload.START && prompt && (on || terminal && exited)) upload = Upload.NONE;
    return record;
  }

  private String runningLabel(){return "Megalodon (RAM)".equals(filePath)?"Megalodon RAM adaptation running on Bruce":"Selected JS running on Bruce";}

  boolean marker(String text,String value){return prefix!=null&&text.equals(prefix+value);}

  byte[] takeBody() { byte[] result = queued; queued = null; return result; }
  boolean bodyQueued() { return upload == Upload.BODY; }
  String summary() {
    return outcome + "; file="+(filePath==null?"None":DisplayText.deviceName(filePath.substring(filePath.lastIndexOf('/')+1))) + "; framing="+"recoverable (8+2)" + "; frames=" + screen.frames + "; draw records=" + screen.records
        + "; rejected=" + screen.rejected + "; failure=" + screen.failure
        + "; drawing frames=" + screen.drawingFrames + "; heartbeats=" + screen.heartbeatFrames
        + "; delivered=" + screen.deliveredFrames + "; queued=" + screen.queuedFrames()
        + "; peak queued=" + screen.peakQueuedFrames()
        + "; waiting for interpreter exit=" + awaitingExit()+"; native dialog="+nativeUi
        + "; last game input sent="+lastInput+"; termination evidence="+termination
        + "; native capture handover="+(captureHandover?(captureStopped?"confirmed":"pending"):"not needed")
        + "; JS key reads="+keyReads+"; last JS key="+lastKeyRead+"; key reads do not confirm a game action"
        + "; repaired shards="+screen.recovery.repairedShards+"; repaired transactions="+screen.recovery.repairedFrames
        + "; compressed transactions="+screen.recovery.compressedFrames+"; drawing payload bytes="+screen.recovery.wireBytes+"/"+screen.recovery.decodedBytes
        + "; damaged records="+screen.recovery.damagedRecords+"; "+screen.throughput();
  }

  void reset() {
    upload = Upload.NONE; prefix = inputPrefix = null; body = queued = null;
    prompt = searched = on = terminal = exited = active = stopRequested = attempted = false;
    captureHandover=captureStopped=false;nativeUi=false;nativeUiAt=0;nativeBoundaries.clear();filePath=reason=null;
    exitExpected = false; exitDeadline = 0;
    activeAt = 0; outcome = "Not started"; screen.reset();
    lastInput=termination=lastKeyRead="None";keyReads=0;
  }
}
