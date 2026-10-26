package com.tabgrinder.brucetrigger;

/** USB events delivered on the main thread. */
interface TransportListener {
  void state(String state, String detail);
  void ready(String address, String name);
  void data(byte[] bytes);
  void sent(String command);
  void loss();
}
