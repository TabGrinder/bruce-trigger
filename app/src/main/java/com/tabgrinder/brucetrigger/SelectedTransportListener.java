package com.tabgrinder.brucetrigger;

import java.util.function.BooleanSupplier;

/** Callbacks cannot clear the screen or change identity after their owner is destroyed. */
final class SelectedTransportListener implements TransportListener {
  private final TransportListener target;
  private final BooleanSupplier selected;

  SelectedTransportListener(TransportListener target, BooleanSupplier selected) {
    this.target = target;
    this.selected = selected;
  }

  public void state(String state, String detail) {
    if (selected.getAsBoolean()) target.state(state, detail);
  }
  public void ready(String address, String name) {
    if (selected.getAsBoolean()) target.ready(address, name);
  }
  public void data(byte[] bytes) {
    if (selected.getAsBoolean()) target.data(bytes);
  }
  public void sent(String command) {
    if (selected.getAsBoolean()) target.sent(command);
  }
  public void loss() {
    if (selected.getAsBoolean()) target.loss();
  }
}
