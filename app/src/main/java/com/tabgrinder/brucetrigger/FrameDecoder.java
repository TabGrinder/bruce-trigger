package com.tabgrinder.brucetrigger;

import java.nio.charset.StandardCharsets;

/**
 * Bounded binary/line demultiplexer. Native draw header: AA,totalLength,opcode. Recognized extension envelopes are
 * kept opaque at the transport layer and rejected by NativeMirrorStream.
 */
public final class FrameDecoder {
  public static final long PARTIAL_FRAME_TIMEOUT_MS = 2000;
  public interface Sink {
    void frame(byte[] bytes);

    void line(String text);

    void malformed();
  }

  private final Sink sink;
  private final byte[] frame = new byte[255];
  private int used = 0, expected = 0;
  private final byte[] text = new byte[2048];
  private int textLength;
  private boolean discardLine;

  public FrameDecoder(Sink sink) {
    if (sink == null) throw new IllegalArgumentException("sink");
    this.sink = sink;
  }

  public void reset() {
    used = 0;
    expected = 0;
    textLength=0;
    discardLine=false;
  }

  /** Caller can expire a stalled binary frame without treating a quiet static screen as loss. */
  public boolean isPartial() {
    return used > 0;
  }

  /** Clear prompt fragments, preserving any in-flight drawing or rejected-line quarantine. */
  public void discardText() { textLength=0; }

  public void accept(byte[] input) {
    if (input == null) {
      malformed();
      return;
    }
    for (byte b : input) {
      int v = b & 255;
      if (used == 0) {
        if (v == 170) {
          used = 1;
          frame[0] = b;
        } else textByte(v);
        continue;
      }
      if (used == 1) {
        if (v < 3) {
          used = 0;
          expected = 0;
          malformed();
        } else {
          expected = v;
          frame[used++] = b;
        }
        continue;
      }
      if (used == 2 && !FrameValidator.validHeader(expected, v)) {
        // Recover a possible overlapping AA marker; never resync inside a valid payload.
        int previousLength = frame[1] & 255;
        used = 0;
        expected = 0;
        malformed();
        if (previousLength == 170 && v >= 3) {
          frame[0] = (byte) 170;
          frame[1] = b;
          used = 2;
          expected = v;
        } else if (v == 170) {
          frame[0] = b;
          used = 1;
        }
        continue;
      }
      frame[used++] = b;
      if (used == expected) {
        byte[] out = new byte[used];
        System.arraycopy(frame, 0, out, 0, used);
        used = 0;
        expected = 0;
        try {
          sink.frame(out);
        } catch (RuntimeException e) {
          malformed();
        }
      }
    }
  }

  private void emitText(String line) {
    try {
      sink.line(line);
    } catch (RuntimeException e) {
      malformed();
    }
  }

  private void malformed() {
    try {
      sink.malformed();
    } catch (RuntimeException ignored) {
      /* Keep parser bounded even if reporting fails. */
    }
  }

  private void textByte(int v) {
    // A rejected line is one record. Its tail must never become a standalone
    // prompt, boot message or interpreter marker, even across USB reads.
    if (discardLine) {
      if (v == 10) discardLine=false;
      return;
    }
    if (v == 32 && textLength == 1 && text[0] == 35) {
      textLength=0;
      emitText("# ");
      return;
    }
    if (v == 10) {
      int length=textLength;textLength=0;
      if (length > 0) emitText(new String(text,0,length,StandardCharsets.UTF_8));
    } else if (v == 13) {
    } else if (v >= 32 || v == 9) {
      if (textLength >= text.length) {
        textLength=0;
        discardLine=true;
        malformed();
      } else text[textLength++]=(byte)v;
    }
  }

  public static int u16(byte[] b, int p) {
    return ((b[p] & 255) << 8) | (b[p + 1] & 255);
  }
}
