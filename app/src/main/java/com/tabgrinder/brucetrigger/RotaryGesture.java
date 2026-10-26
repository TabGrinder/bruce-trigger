package com.tabgrinder.brucetrigger;

/** Bounded angular detents. No momentum, queued catch-up or centre-press inference. */
public final class RotaryGesture {
  private boolean active;
  private double previous, remainder;

  public void begin(double degrees) {
    active = Double.isFinite(degrees);
    previous = degrees;
    remainder = 0;
  }

  public int move(double degrees) {
    if (!active || !Double.isFinite(degrees)) return 0;
    double delta = degrees - previous;
    previous = degrees;
    while (delta > 180) delta -= 360;
    while (delta < -180) delta += 360;
    if (Math.abs(delta) > 90) {
      remainder = 0;
      return 0;
    }
    remainder += delta;
    if (Math.abs(remainder) < 15) return 0;
    int detents = (int) (remainder / 15);
    remainder -= detents * 15;
    return detents;
  }

  public void cancel() {
    active = false;
    remainder = 0;
  }
}
