package com.tabgrinder.brucetrigger;

import java.util.Arrays;
import java.util.function.BooleanSupplier;

/** Bounded paced writes: Android completion is not an acknowledgement of firmware consumption. */
final class PacedUsbWriter {
  // Largest accepted logical write: fixed, SHA-pinned graphics bridge; selected source is read on Bruce.
  static final int MAX_WRITE_BYTES = 16384;
  static final int CHUNK_BYTES = 64;
  static final long GAP_MS = 25;
  interface Sender { int send(byte[] chunk); }
  interface Pause { void waitMs(long millis) throws InterruptedException; }

  static int write(byte[] data, BooleanSupplier cancelled, Sender sender, Pause pause)
      throws InterruptedException {
    if (data == null || data.length == 0 || data.length > MAX_WRITE_BYTES) return -1;
    int offset = 0;
    while (offset < data.length) {
      if (cancelled.getAsBoolean()) return offset;
      int length = Math.min(CHUNK_BYTES, data.length - offset);
      byte[] chunk = Arrays.copyOfRange(data, offset, offset + length);
      int delivered = sender.send(chunk);
      // Never retry a short write, including a zero or negative receipt. The
      // firmware may have consumed a prefix even when the host reports failure.
      if (delivered != length) {
        if (delivered < 0 || delivered > length) return offset == 0 ? -1 : offset;
        return offset + delivered;
      }
      offset += length;
      if (offset < data.length) pause.waitMs(GAP_MS);
    }
    return offset;
  }
  private PacedUsbWriter() {}
}
