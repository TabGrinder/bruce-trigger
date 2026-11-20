package com.tabgrinder.brucetrigger;

import java.util.Locale;

/** Transport identity policy. Firmware release numbers are not a capability signal. */
final class FirmwareCompatibility {
  private FirmwareCompatibility() {}

  /** Release version or development-build commit SHA (seven to forty hexadecimal characters). */
  static boolean supportsVersion(String version) {
    return version != null &&
        (version.matches("[0-9][A-Za-z0-9._+\\-]{0,47}")
            || version.matches("[a-fA-F][0-9a-fA-F]{6,39}"));
  }

  /** Only the reviewed CC1101 hardware can use the native graphics bridge. */
  static boolean supportsIdentity(String version, String board) {
    if (!supportsVersion(version)) return false;
    if (board == null) return false;
    return board.trim().toLowerCase(Locale.ROOT)
        .replaceAll("\\s+", " ").matches("lilygo t-embed cc1101( plus)?");
  }
}
