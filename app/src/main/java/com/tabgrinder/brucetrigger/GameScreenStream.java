package com.tabgrinder.brucetrigger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Adler32;

/** Sequenced complete game frames; no partially received update is presented. */
final class GameScreenStream {
  private final ArrayDeque<List<byte[]>> ready = new ArrayDeque<>();
  private final WindowBudget budget = new WindowBudget(262144, 262144, 1000);
  private boolean initialized;
  final RecoverableGameStream recovery = new RecoverableGameStream();
  private int readyBytes;
  long frames, records, rejected, lastFrame;
  long drawingFrames, heartbeatFrames, deliveredFrames;
  private long firstDrawingAt,lastDrawingAt,maxDrawingGap;
  private int peakQueuedFrames;
  String failure = "None";

  boolean line(String record, long now) {
    if (!failure.equals("None")) return false;
    if (record == null || record.length() > 544) return reject("Game record exceeds its line bound");
    if (!record.equals("R") && !record.startsWith("R|") && !record.startsWith("D|")) return reject("Unknown recovery record");
    RecoverableGameStream.Frame frame = recovery.accept(record, now);
    if (!recovery.failure.equals("None")) return reject(recovery.failure);
    return frame == null || complete(frame.bytes, frame.count, frame.checksum, now);
  }

  private boolean complete(byte[] bytes, long expectedCount, long expectedChecksum, long now) {
    Adler32 checksum = new Adler32();
    checksum.update(bytes);
    if (expectedChecksum != checksum.getValue() || !budget.allow(bytes.length + 16, now))
      return reject("Game frame checksum or rate rejected");
    List<byte[]> packets = new ArrayList<>();
    for (int at = 0; at < bytes.length;) {
      if (at + 3 > bytes.length) return reject("Truncated game draw record");
      int size = bytes[at + 1] & 255;
      if (size < 3 || size > 96 || at + size > bytes.length || packets.size() >= 256)
        return reject("Invalid game draw length");
      byte[] packet = java.util.Arrays.copyOfRange(bytes, at, at + size);
      if (!valid(packet, initialized || !packets.isEmpty())) return reject("Unsupported game draw record");
      packets.add(packet);
      at += size;
    }
    if (packets.size() != expectedCount) return reject("Incomplete game operation count");
    if (!initialized && (packets.size() < 2 || (packets.get(0)[2] & 255) != 99
        || (packets.get(1)[2] & 255) != 0)) return reject("Game screen has no initial clear");
    if (!packets.isEmpty()) {
      if (ready.size() >= 16 || readyBytes + bytes.length > 32768)
        return reject("Game renderer queue exceeded bounds");
      ready.addLast(packets);
      readyBytes += bytes.length;
      if(drawingFrames==0)firstDrawingAt=now;
      else maxDrawingGap=Math.max(maxDrawingGap,Math.max(0,now-lastDrawingAt));
      lastDrawingAt=now;drawingFrames++;
      peakQueuedFrames = Math.max(peakQueuedFrames, ready.size());
    } else {
      heartbeatFrames++;
    }
    initialized = true;
    frames++;
    records += packets.size();
    lastFrame = now;
    return true;
  }

  private boolean valid(byte[] packet, boolean hasGeometry) {
    if (packet.length < 3) return false;
    if ((packet[2] & 255) == 99) return !hasGeometry && FrameValidator.valid(packet)
        && FrameDecoder.u16(packet, 3) == 320 && FrameDecoder.u16(packet, 5) == 170 && packet[7] == 1;
    return hasGeometry && GameDrawValidator.valid(packet);
  }

  private boolean reject(String reason) {
    rejected++;
    failure = reason;
    ready.clear();
    readyBytes = 0;
    return false;
  }

  List<byte[]> takeFrame() {
    List<byte[]> frame = ready.pollFirst();
    if (frame != null) {
      for (byte[] p : frame) readyBytes -= p.length;
      deliveredFrames++;
    }
    return frame;
  }

  int queuedFrames() { return ready.size(); }
  int peakQueuedFrames() { return peakQueuedFrames; }

  boolean usable(long now) { return initialized && failure.equals("None") && now - lastFrame < 2500; }

  String throughput() {
    long elapsed=lastDrawingAt-firstDrawingAt;
    String rate=drawingFrames<2||elapsed<=0?"unavailable"
        :String.format(java.util.Locale.ROOT,"%.1f",(drawingFrames-1)*1000.0/elapsed);
    return "received drawing batches/s="+rate+"; longest drawing gap="+maxDrawingGap+" ms (includes pauses)";
  }

  void reset() {
    ready.clear(); budget.reset(); readyBytes = 0;
    initialized = false;
    recovery.reset();
    frames = records = rejected = lastFrame = 0;
    drawingFrames = heartbeatFrames = deliveredFrames = 0;
    firstDrawingAt=lastDrawingAt=maxDrawingGap=0;
    peakQueuedFrames = 0;
    failure = "None";
  }
}
