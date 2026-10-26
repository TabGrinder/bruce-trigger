package com.tabgrinder.brucetrigger;

public final class SelectedTransportTest {
  static int checks;
  static void ok(boolean value, String label) {
    checks++;
    if (!value) throw new AssertionError(label);
  }
  static final class Target implements TransportListener {
    int events;
    String state, name, command;
    byte[] bytes;
    public void state(String s, String d) { events++; state = s; }
    public void ready(String a, String n) { events++; name = n; }
    public void data(byte[] b) { events++; bytes = b; }
    public void sent(String c) { events++; command = c; }
    public void loss() { events++; }
  }
  static void emit(TransportListener listener, String name) {
    listener.state(name, "state");
    listener.ready("locator", name);
    listener.data(new byte[] {1, 2});
    listener.sent("nav next");
    listener.loss();
  }
  public static void main(String[] args) {
    Target target = new Target();
    final boolean[] alive = {true};
    TransportListener cable = new SelectedTransportListener(target, () -> alive[0]);
    emit(cable, "USB");
    ok(target.events == 5 && "USB".equals(target.name), "live owner receives every USB event once");
    ok(target.bytes.length == 2 && "nav next".equals(target.command), "data and command payloads are preserved");
    alive[0] = false;
    emit(cable, "destroyed");
    ok(target.events == 5 && "USB".equals(target.state), "destroyed owner receives no late USB callback");
    ok("USB".equals(target.name), "late callbacks cannot overwrite device identity");
    System.out.println("PASS " + checks + " transport-event ownership assertions.");
  }
}
