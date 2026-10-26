package com.tabgrinder.brucetrigger;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/** Production controller with a paused outbound slot; no physical USB endpoint. */
public final class QueuedInputCancellationTest {
  private static int checks;
  private static void ok(boolean value, String label) {
    checks++;
    if (!value) throw new AssertionError(label);
  }
  private static final class Fixture implements AutoCloseable {
    final UsbController controller;
    final AtomicReference<byte[]> outbound;
    @SuppressWarnings("unchecked") Fixture() throws Exception {
      Handler.reset();
      controller = new UsbController(new Context(), new UsbRegressionTest.Listener());
      Class<?> type = Class.forName("com.tabgrinder.brucetrigger.UsbController$Run");
      Constructor<?> constructor = type.getDeclaredConstructors()[0]; constructor.setAccessible(true);
      android.hardware.usb.UsbDevice device = UsbRegressionTest.device();
      Object run = constructor.newInstance(controller.sessionToken(), device, UsbProfile.find(device));
      Field current = UsbController.class.getDeclaredField("current"); current.setAccessible(true); current.set(controller, run);
      Field owner = UsbController.class.getDeclaredField("owner"); owner.setAccessible(true); owner.set(null, new WeakReference<>(controller));
      Field slot = type.getDeclaredField("outbound"); slot.setAccessible(true);
      outbound = (AtomicReference<byte[]>)slot.get(run);
      controller.session.open(SystemClock.elapsedRealtime()-1000);
      controller.session.verified = true;
      controller.session.version = "1.16.1";
      controller.session.board = "LILYGO T-Embed CC1101";
      controller.session.mirror.active = true;
    }
    public void close() { controller.disconnect("Test complete"); controller.destroy(); }
  }
  public static void main(String[] args) throws Exception {
    for (String command : new String[]{"nav prev", "nav next", "nav select", "nav esc"}) {
      try (Fixture f = new Fixture()) {
        ok(f.controller.command(command), "queue navigation before worker takes it");
        f.controller.cancelQueuedInput();
        ok(f.outbound.get() == null, "covered touch removes unsent " + command);
        ok(f.controller.isReady() && f.controller.session.stage == UsbSerialSession.Stage.OPEN,
            "cancelling unsent captured input preserves verified connection");
      }
    }
    try (Fixture f = new Fixture()) {
      ok(f.controller.queryMenu(), "queue read-only metadata query");
      byte[] pending = f.outbound.get(); f.controller.cancelQueuedInput();
      ok(f.outbound.get() == pending && "optionsJSON\n".equals(new String(pending, StandardCharsets.US_ASCII)),
          "touch cancellation preserves metadata query bytes");
      ok(f.controller.session.stage == UsbSerialSession.Stage.WRITING, "metadata request still awaits its receipt");
    }
    try (Fixture f = new Fixture()) {
      f.controller.session.mirror.active = false;
      ok(f.controller.syncTheme(), "queue theme upload header");
      byte[] pending = f.outbound.get(); f.controller.cancelQueuedInput();
      ok(f.outbound.get() == pending && f.controller.session.mirror.busy(), "touch cancellation preserves interpreter upload");
    }
    try (Fixture f = new Fixture()) {
      ok(f.controller.command("nav next"), "queue input for in-flight test");
      byte[] bytes = f.outbound.getAndSet(null);
      f.controller.session.written(bytes.length, bytes.length, SystemClock.elapsedRealtime());
      f.controller.cancelQueuedInput();
      ok(f.outbound.get() == null && f.controller.session.stage == UsbSerialSession.Stage.WAITING,
          "in-flight write is not cancelled or replayed as an unsent input");
    }
    System.out.println("PASS " + checks + " queued-input cancellation assertions (USB worker slot doubled).");
  }
}
