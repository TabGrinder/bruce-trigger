package com.tabgrinder.brucetrigger;

/** Reject malformed lengths and unbounded framebuffer dimensions before Android rendering. */
public final class FrameValidator {
  private static final int[] LENGTH = {
    5, 13, 13, 15, 15, 11, 11, 17, 17, 13, 13, 13, 19, 17, 13, 13, 13, 13, 12, 0, 11, 11
  };

  /** Structural header validation is possible as soon as the third byte arrives. */
  public static boolean validHeader(int length, int opcode) {
    if (length < 3 || length > 255) return false;
    if (opcode == 98) return length >= 11 && length <= 139;
    if (opcode == 99) return length == 8;
    if (opcode < 0 || opcode >= LENGTH.length || opcode == 19) return false;
    return opcode >= 14 && opcode <= 18 ? length >= LENGTH[opcode] : length == LENGTH[opcode];
  }

  public static boolean valid(byte[] b) {
    if (b == null
        || b.length < 3
        || b.length > 255
        || b[0] != (byte) 170
        || (b[1] & 255) != b.length) return false;
    int op = b[2] & 255;
    if (!validHeader(b.length, op)) return false;
    if (op == 99) {
      int w = FrameDecoder.u16(b, 3), h = FrameDecoder.u16(b, 5);
      return w > 0 && h > 0 && w <= 1024 && h <= 1024 && (long) w * h <= 1048576;
    }
    return true;
  }

  /** Stock1.16.1 sends each log record in one notification, without transport chunking. */
  public static boolean exceedsNotificationPayload(byte[] bytes, int mtu) {
    int capacity = mtu - 3;
    return capacity >= 20
        && bytes != null
        && bytes.length == capacity
        && bytes.length >= 3
        && (bytes[0] & 255) == 170
        && validHeader(bytes[1] & 255, bytes[2] & 255)
        && (bytes[1] & 255) > capacity;
  }

  private FrameValidator() {}
}
