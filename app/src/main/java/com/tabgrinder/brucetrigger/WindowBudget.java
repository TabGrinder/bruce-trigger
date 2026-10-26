package com.tabgrinder.brucetrigger;

/** Fixed-window processing budget. Clock is supplied for deterministic tests. */
public final class WindowBudget {
  private final long window;
  private final int maxCount, maxBytes;
  private long start = -1;
  private int count, bytes;

  public WindowBudget(int maxCount, int maxBytes, long window) {
    if (maxCount < 1 || maxBytes < 1 || window < 1) throw new IllegalArgumentException();
    this.maxCount = maxCount;
    this.maxBytes = maxBytes;
    this.window = window;
  }

  public synchronized boolean allow(int size, long now) {
    if (size < 0 || size > maxBytes) return false;
    if (start < 0 || now < start || now - start >= window) {
      start = now;
      count = 0;
      bytes = 0;
    }
    if (count >= maxCount || size > maxBytes - bytes) return false;
    count++;
    bytes += size;
    return true;
  }

  public synchronized void reset() {
    start = -1;
    count = 0;
    bytes = 0;
  }
}
