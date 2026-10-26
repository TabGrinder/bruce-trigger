package com.tabgrinder.brucetrigger;

/** Root metadata stays responsive after input without busy-polling a resting device. */
final class MenuRefreshPolicy {
  static long delay(long now, long lastNavigation) {
    return lastNavigation > 0 && now >= lastNavigation && now - lastNavigation < 2000 ? 350 : 1200;
  }
  /** USB completion/input posts immediately; the timer only covers a quiet link. */
  static long nextWake(long now,long due) {return Math.max(16,Math.min(1200,due-now));}
  private MenuRefreshPolicy() {}
}
