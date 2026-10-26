package com.tabgrinder.brucetrigger;

/** Stock Bruce draw stream. No CRC, sequence, frame-complete marker or heartbeat is claimed. */
public final class NativeMirrorStream {
  private boolean geometry, clear, interrupted, unsupportedSprites;
  private long packets, rejected, lastDraw;
  private int width, height;
  private byte[] panel;
  private final long[] opcodeCounts = new long[100];

  public void reset() {
    clearScreen();
    packets = rejected = 0;
    java.util.Arrays.fill(opcodeCounts, 0);
  }

  public void clearScreen() {
    geometry = clear = interrupted = unsupportedSprites = false;
    lastDraw = 0;
    width = height = 0;
    panel = null;
  }

  /** Keep only verified panel geometry when switching from a root snapshot.
   * A subsequent real full-screen fill is required before control is usable. */
  public byte[] prepareNativeEntry() {
    if (!geometry || panel == null || interrupted) return null;
    clear = unsupportedSprites = false;
    lastDraw = 0;
    return panel.clone();
  }

  public void invalidate() { interrupted = true; clear = false; }
  /** An owned JS dialog may draw over an already complete, same-size game frame. */
  public void beginOverlay(int w,int h) {
    clearScreen();
    if(w==320&&h==170){width=w;height=h;geometry=clear=true;}
  }
  public boolean unsupportedSprites() { return unsupportedSprites; }
  public boolean broken() { return interrupted; }
  public boolean hasGeometry() { return geometry; }
  public boolean hasClear() { return clear; }
  public long packets() { return packets; }
  public long rejected() { return rejected; }
  public long lastDraw() { return lastDraw; }
  public String opcodeSummary() {
    StringBuilder out = new StringBuilder();
    for(int i=0;i<opcodeCounts.length;i++)if(opcodeCounts[i]>0) {
      if(out.length()>0)out.append(", ");
      out.append(i).append(':').append(opcodeCounts[i]);
    }
    return out.length()==0?"None":out.toString();
  }

  public byte[] accept(byte[] record, long now) {
    if (!FrameValidator.valid(record) || record.length > 128 || (record[2] & 255) == 98) {
      rejected++;
      invalidate();
      return null;
    }
    int op = record[2] & 255;
    opcodeCounts[op]++;
    if (op == 99) {
      int w = FrameDecoder.u16(record, 3), h = FrameDecoder.u16(record, 5);
      if (geometry && (w != width || h != height)) invalidate();
      width = w; height = h;
      geometry = true;
      panel = record.clone();
      packets++;
      return record;
    }
    // Never invent panel dimensions. Recover only at an actual full-screen fill.
    if (!geometry) { rejected++; return null; }
    if (op == 0) { clear = true; interrupted = unsupportedSprites = false; }
    if (!clear || interrupted) { rejected++; return null; }
    // Stock Megalodon emits this exact centered marker instead of its sprite pixels.
    // Do not classify arbitrary text or silence as an unsupported game.
    if (op == 14 && FrameDecoder.u16(record,3) == width/2
        && FrameDecoder.u16(record,5) == height/2) {
      String text = new String(record,13,record.length-13,java.nio.charset.StandardCharsets.US_ASCII);
      if (text.equals("Not Supported") || text.equals("Not Supported\0")) unsupportedSprites = true;
    }
    packets++;
    lastDraw = now;
    return record;
  }
}
