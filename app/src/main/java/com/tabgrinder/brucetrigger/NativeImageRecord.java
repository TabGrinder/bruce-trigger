package com.tabgrinder.brucetrigger;

import java.nio.charset.StandardCharsets;

/** Bruce 1.16.1 transmitted DRAWIMAGE layout, not its internal tftLog layout. */
final class NativeImageRecord {
  static final int PATH_OFFSET = 12;

  static String key(byte[] record) {
    if (!FrameValidator.valid(record) || record.length < PATH_OFFSET + 3
        || record.length > 128 || (record[2] & 255) != 18
        || FrameDecoder.u16(record, 7) > 1) return null;
    int fs = record[11] & 255;
    if (fs != 0 && fs != 2) return null;
    // asyncSerialTaskFunc/getBinLog remove the internal slot byte at index 12.
    // The transmitted path starts at 12, including its leading slash.
    String path = new String(record, PATH_OFFSET, record.length - PATH_OFFSET,
        StandardCharsets.US_ASCII);
    return ThemeTransfer.validPath(path) ? fs + ":" + path : null;
  }
  private NativeImageRecord() {}
}
