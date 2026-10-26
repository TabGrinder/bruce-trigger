package com.tabgrinder.brucetrigger;

/** Main-thread USB command state. One request, no replay, no inference from USB receipts. */
final class UsbSerialSession {
  enum Stage { CLOSED, OPEN, WRITING, WAITING, BLOCKED }
  Stage stage = Stage.CLOSED;
  boolean verified;
  boolean externalInterpreter;
  boolean bootObserved;
  private boolean cleanupOnly,cleanupPrompt,writeUncertain;
  String menuJson;
  long menuReplies;
  String version = "Unknown", board = "Unknown", pending, lastCommand = "None";
  String status = "USB disconnected", failure = "None";
  long receivedBytes, writes, prompts, unsolicited, late, openedAt, deadline, nextAt;
  final UsbMirrorBridge mirror = new UsbMirrorBridge();
  final UsbGameBridge game = new UsbGameBridge();
  final FilesScriptSelection files = new FilesScriptSelection();
  private long receivedAt;
  private boolean completed, completedReadOnly;
  private long queryAt;
  private boolean versionSeen, boardSeen;
  private final FrameDecoder decoder = new FrameDecoder(new FrameDecoder.Sink() {
    public void frame(byte[] b) {
      if(cleanupOnly)return;
      if (!mirror.acceptsFrames(receivedAt) && !(game.active && game.nativeUi)) block("Unexpected screen stream. Stop capture on the device before reconnecting.");
      else if (!FrameValidator.valid(b) || b.length > 128 || (b[2] & 255) == 98)
        block("Invalid native screen packet. Capture state is unknown; no replay.");
      else if(verified && !game.engaged() && !mirror.busy() && !externalInterpreter)files.frame(b);
    }
    public void malformed() { if(!cleanupOnly)block("Malformed serial response. Commands stopped; no replay."); }
    public void line(String line) { consume(line); }
  });

  void open(long now) {
    close(); stage = Stage.OPEN; openedAt = now; nextAt = now + 500;
    receivedBytes = writes = prompts = unsolicited = late = 0;
    failure = "None"; lastCommand = "None"; version = board = "Unknown";
    status = "USB open · verify Bruce before control";
    menuJson=null;menuReplies=0;queryAt=0;files.reset();
    bootObserved=false;
  }
  void close() {
    menuJson=null;
    stage = Stage.CLOSED; verified = false; externalInterpreter = false; pending = null;
    mirror.reset(); game.reset(); files.clear();
    cleanupOnly=cleanupPrompt=writeUncertain=false;
    versionSeen = boardSeen = false; decoder.reset(); status = "USB disconnected";
  }
  boolean idle(long now) { return stage == Stage.OPEN && now >= nextAt && !mirror.draining(now); }
  boolean canControl(long now) { return verified && idle(now); }
  byte[] begin(String command, long now) {
    byte[] bytes = CommandPolicy.encode(command);
    if (bytes == null || !idle(now) || (!verified && !"info".equals(command))) return null;
    if (game.engaged()) { if (!game.active || game.awaitingExit() || !command.startsWith("nav ")) return null; }
    else if (mirror.active) decoder.discardText(); else decoder.reset();
    versionSeen = boardSeen = false;
    if ("nav esc".equals(command)) files.clear();
    pending = lastCommand = command;
    stage = Stage.WRITING; deadline = now + 6500;
    status = "Sending " + command;
    return bytes;
  }
  boolean canQueryMenu(long now) {
    return verified && !game.engaged() && !externalInterpreter && !mirror.busy() && stage==Stage.OPEN
        && !mirror.draining(now) && now>=queryAt;
  }
  byte[] beginRootSelection(int index, String expectedJson, long now) {
    // Only the just-verified root can use the stock forceMenuOption path. Generic
    // CommandPolicy intentionally does not accept option commands.
    if(index<0 || index>=40 || !verified || externalInterpreter || !mirror.active || mirror.busy()
        || !idle(now) || !"optionsJSON".equals(lastCommand) || expectedJson==null
        || !expectedJson.equals(menuJson))return null;
    decoder.discardText();menuJson=null;
    pending=lastCommand="option "+index;stage=Stage.WRITING;deadline=now+6500;
    status="Sending confirmed root selection";
    return (pending+"\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
  }
  byte[] beginMenuQuery(long now) {
    if(!canQueryMenu(now))return null;
    if(mirror.active)decoder.discardText();else decoder.reset();menuJson=null;
    pending=lastCommand="optionsJSON";stage=Stage.WRITING;deadline=now+6500;
    status="Reading Bruce menu state";
    return "optionsJSON\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
  }
  byte[] beginFilesInfo(String expectedJson,long now) {
    if(!verified||externalInterpreter||game.engaged()||!mirror.active||mirror.busy()||!idle(now)
        || !"optionsJSON".equals(lastCommand)||expectedJson==null||!expectedJson.equals(menuJson)
        || !files.menuVisible)return null;
    files.beginInfo();decoder.discardText();menuJson=null;
    pending=lastCommand="option 2";stage=Stage.WRITING;deadline=now+6500;
    status="Reading selected File Info";
    return "option 2\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
  }
  void written(int actual, int expected, long now) {
    if (stage != Stage.WRITING) return;
    if (actual != expected) {
      writeUncertain=true;
      block("USB write was incomplete. Execution is unknown; disconnect before retrying."); return;
    }
    if (!game.engaged()) decoder.discardText();
    writes++; stage = Stage.WAITING;
    if(game.active && pending!=null && pending.startsWith("nav "))game.lastInput=pending;
    deadline = now + (game.uploading() ? 45000 : mirror.busy() && mirror.syncing && mirror.step == UsbMirrorBridge.Step.BODY ? 90000 : mirror.busy() ? 12000 : 6000);
    if (game.uploading()) game.written();
    else if (game.engaged() && "js exit".equals(pending)) {
      game.expectExit(now);
      if ("js exit".equals(pending)) game.stopRequested = true;
    }
    else if (mirror.busy()) mirror.written();
    status = "USB delivered " + pending + " · waiting for firmware";
  }
  void receive(byte[] bytes, long now) {
    if (stage == Stage.CLOSED || bytes == null) return;
    receivedBytes += bytes.length;
    if (expirePartialFrame(now)) return;
    receivedAt = now; completed = completedReadOnly = false;
    if (stage == Stage.OPEN && !mirror.acceptsFrames(now) && !game.engaged()) nextAt = Math.max(nextAt, now + 120);
    // A worker posts each write receipt before its subsequent reads.
    // Bytes captured before that write cannot acknowledge the new command.
    if (stage == Stage.WRITING && !mirror.acceptsFrames(now) && !game.engaged()) return;
    decoder.accept(bytes);
    if (stage == Stage.OPEN && !completedReadOnly && (completed || !mirror.acceptsFrames(now) && !game.engaged()))
      nextAt = Math.max(nextAt, now + (game.active && !game.nativeUi ? 20 : 120));
    if(completed)queryAt=now+40; // Read-only root metadata does not add another navigation cooldown.
  }
  byte[] beginMirror(boolean start, String nonce, long now) {
    if (!verified || game.engaged() || externalInterpreter || !idle(now) || now < mirror.drainUntil || !UsbMirrorBridge.supports(version, board)) return null;
    byte[] header = mirror.begin(start, nonce);
    if (header == null) return null;
    if (start) decoder.reset(); else decoder.discardText();
    pending = lastCommand = start ? "USB mirror start (stock JS)" : "USB mirror stop (stock JS)";
    stage = Stage.WRITING; deadline = now + 6500; status = "Sending " + pending;
    return header;
  }
  byte[] takeScriptBody() {
    if (stage != Stage.WRITING || !verified) return null;
    return game.bodyQueued() ? game.takeBody() : mirror.takeBody();
  }
  byte[] beginFilesGame(String template,String nonce,String path,long now) {
    if(!verified || externalInterpreter || !idle(now) || mirror.busy()
        || game.engaged() || !UsbMirrorBridge.supports(version,board))return null;
    byte[] header=game.beginFiles(template,nonce,path,mirror.active);if(header==null)return null;
    files.mark("Selected JS bridge upload started");files.clear();if(mirror.active)decoder.discardText();else decoder.reset();pending=lastCommand="Files JS mirror start";
    stage=Stage.WRITING;deadline=now+6500;status="Starting the selected Files script";return header;
  }
  byte[] beginMegalodon(String template,String nonce,long now) {
    if(!verified || externalInterpreter || !idle(now) || mirror.busy()
        || game.engaged() || !UsbMirrorBridge.supports(version,board))return null;
    byte[] header=game.beginMegalodon(template,nonce,mirror.active);if(header==null)return null;
    files.clear();if(mirror.active)decoder.discardText();else decoder.reset();pending=lastCommand="Megalodon RAM start";
    stage=Stage.WRITING;deadline=now+6500;status="Starting Megalodon RAM adaptation";return header;
  }
  byte[] beginGameStop(long now) {
    boolean emergency=stage==Stage.BLOCKED && !bootObserved && game.active && !game.uploading() && !writeUncertain
        && !game.stopRequested && !game.awaitingExit() && UsbMirrorBridge.supports(version,board);
    if (!emergency && (!verified || !idle(now) || !game.engaged() || game.uploading() || game.stopRequested || game.awaitingExit())) return null;
    if(emergency){cleanupOnly=true;cleanupPrompt=false;decoder.reset();}
    pending=lastCommand="js exit";
    stage=Stage.WRITING;deadline=now+6500;status="Stopping selected JS";
    return "js exit\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
  }
  byte[] beginThemeSync(String nonce, long now) {
    return beginThemeSync(nonce, null, now);
  }
  byte[] beginThemeSync(String nonce, String observedKey, long now) {
    if (!verified || game.engaged() || externalInterpreter || !idle(now) || !UsbMirrorBridge.supports(version, board)) return null;
    byte[] header = mirror.beginThemeSync(nonce, observedKey);
    if (header == null) return null;
    decoder.reset(); pending = lastCommand = "Sync theme images (read-only stock JS)";
    stage = Stage.WRITING; deadline = now + 6500; status = "Reading theme images…";
    return header;
  }
  private void consume(String line) {
    if (line.contains("Guru Meditation") || line.startsWith("ESP-ROM:")
        || line.trim().equals("Rebooting...") || line.startsWith("rst:")) {
      bootObserved=true;
      block("Device boot/reset output detected. Reconnect and verify Bruce again."); return;
    }
    if(cleanupOnly) {
      // After a graphics failure, consume only the owned exit acknowledgement.
      // A damaged draw stream cannot replace the failure or unlock normal input.
      if("# ".equals(line)){cleanupPrompt=true;prompts++;}
      else if("Exiting interpreter...".equals(line) || game.marker(line,"STOP") || game.marker(line,"DONE")
          || game.marker(line,"ERROR") || game.marker(line,"IDLE"))game.line(line,receivedAt);
      if(cleanupPrompt && game.ended()) {
        pending=null;stage=Stage.OPEN;completed=true;status="Game cleanup exit acknowledged";
      }
      return;
    }
    boolean ownedGame = game.engaged();
    if(!ownedGame && line.matches("BT_[0-9a-f]{16}_GAME\\|.*"))externalInterpreter=true;
    if(verified && !ownedGame && !mirror.busy() && !externalInterpreter) files.observe(line);
    if (ownedGame) {
      if (!line.startsWith("BT_") && (line.contains("SyntaxError:") || line.contains("TypeError:")
          || line.contains("ReferenceError:") || line.contains("RangeError:") || line.contains("InternalError:"))) {
        block("Game interpreter error. Control stopped; no replay."); return;
      }
      boolean record=game.line(line,receivedAt);
      if(game.captureHandover && game.captureStopped && mirror.active)mirror.handoverStopped(receivedAt);
      if (!game.screen.failure.equals("None")) {block(game.screen.failure+". Game mirror stopped; no replay.");return;}
      if(stage==Stage.WAITING && ("Files JS mirror start".equals(pending) || "Megalodon RAM start".equals(pending))) {
        if(line.equals("# "))prompts++;
        if(game.bodyQueued()){stage=Stage.WRITING;deadline=receivedAt+15000;status="Uploading reviewed game bridge";}
        else if(!game.uploading()){stage=Stage.OPEN;pending=null;completed=true;status=game.outcome;}
        return;
      }
      if(record) return;
    }
    // The built-in display JS bindings bypass tft_logger. This is only a
    // firmware status observation, never a claim to detect all native games.
    // Ignore our own short startup/cleanup scripts while the bridge is busy.
    if (verified && !mirror.busy() && !ownedGame) {
      if ("Entering interpreter...".equals(line)) externalInterpreter = true;
      else if ("Exiting interpreter...".equals(line)) externalInterpreter = false;
    }
    if (stage == Stage.WRITING) return;
    if (stage == Stage.WAITING && mirror.busy() && !line.startsWith("BT_")
        && (line.contains("SyntaxError:") || line.contains("TypeError:")
            || line.contains("ReferenceError:") || line.contains("RangeError:")
            || line.contains("InternalError:"))) {
      block("Bruce interpreter rejected the script. Restart Bruce, then reconnect. No replay.");
      return;
    }
    if (stage == Stage.WAITING && mirror.busy()) {
      if ("# ".equals(line)) prompts++;
      mirror.line(line, receivedAt);
      if (mirror.failed) { block("Bruce rejected the capture command. No retry was sent."); return; }
      if (mirror.step == UsbMirrorBridge.Step.BODY) {
        stage = Stage.WRITING; deadline = receivedAt + 6500; status = "Transferring fixed capture script to RAM";
      } else if (!mirror.busy()) {
        pending = null; stage = Stage.OPEN; completed = true;
        status = mirror.syncing ? "Theme sync complete · " + mirror.themes.summary()
            : mirror.active ? "Capture script completed · waiting for screen data" : "Capture stop acknowledged";
      }
      return;
    }
    if ("# ".equals(line)) {
      prompts++;
      if (stage == Stage.BLOCKED) { late++; return; }
      if (stage != Stage.WAITING) { unsolicited++; return; }
      if ("info".equals(pending)) {
        verified = versionSeen && boardSeen;
        if (!verified) { block("The response did not identify Bruce and its board. Control remains locked."); return; }
      }
      if("optionsJSON".equals(pending)){menuReplies++;completedReadOnly=true;}
      status = "Firmware replied to " + pending;
      pending = null; stage = Stage.OPEN; completed = true;
      return;
    }
    if(stage==Stage.WAITING && "optionsJSON".equals(pending) && line.startsWith("{") && line.endsWith("}"))menuJson=line;
    if (stage == Stage.WAITING && "info".equals(pending)) {
      if (line.matches("Bruce v[0-9][A-Za-z0-9._+\\-]{0,47}")) {
        versionSeen = true; version = line.substring(7);
      }
      if (line.startsWith("Device: ") && line.length() > 8 && line.length() <= 104) {
        boardSeen = true; board = DisplayText.deviceName(line.substring(8));
      }
    }
  }
  void tick(long now) {
    if (expirePartialFrame(now)) return;
    if (game.exitTimedOut(now)) {
      block("Game interpreter exit was not confirmed. No exit or input replay; reconnect before control.");
      return;
    }
    if (game.active && !game.nativeUi && !game.awaitingExit() && now - Math.max(game.activeAt, game.screen.lastFrame) >= 3000)
      block("Game frames stopped arriving. No input replay; reconnect before control.");
    if ((stage == Stage.WRITING || stage == Stage.WAITING) && now >= deadline)
      block("Firmware reply timed out after " + lastCommand
          + ". No replay." + (mirror.busy()?" Capture evidence: "+mirror.completionEvidence():"")
          + " Reconnect after checking the device state.");
  }
  private boolean expirePartialFrame(long now) {
    if (stage != Stage.CLOSED && stage != Stage.BLOCKED && decoder.isPartial()
        && now - receivedAt >= FrameDecoder.PARTIAL_FRAME_TIMEOUT_MS) {
      decoder.reset();
      block("Incomplete screen packet expired. Reconnect before control; no replay.");
      return true;
    }
    return false;
  }
  void block(String reason) {
    if (stage == Stage.CLOSED || stage == Stage.BLOCKED) return;
    stage = Stage.BLOCKED; verified = false; pending = null;
    if(failure.equals("None"))failure=reason;
    status = "USB control stopped · " + reason;
  }
}
