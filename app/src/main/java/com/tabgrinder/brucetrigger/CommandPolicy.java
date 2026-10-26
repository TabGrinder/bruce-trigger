package com.tabgrinder.brucetrigger;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** No arbitrary CLI/text injection. Each accepted command fits one conservative ATT write. */
public final class CommandPolicy {
  public static boolean allowed(String c) {
    return c != null
        && Arrays.asList(
                "nav prev",
                "nav next",
                "nav esc",
                "nav select",
                "nav up",
                "nav down",
                "nav nextpage",
                "nav prevpage",
                "info",
                "free",
                "storage free",
                "uptime",
                "reboot",
                "poweroff",
                "sleep")
            .contains(c);
  }

  public static byte[] encode(String c) {
    if (!allowed(c)) return null;
    byte[] bytes = (c + "\n").getBytes(StandardCharsets.US_ASCII);
    return bytes.length <= 20 ? bytes : null;
  }

  private CommandPolicy() {}
}
