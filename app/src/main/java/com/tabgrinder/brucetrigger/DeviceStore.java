package com.tabgrinder.brucetrigger;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Private non-backed-up locator, not a cryptographic identity or OS bond. No credentials stored.
 */
final class DeviceStore {
  private final SharedPreferences p;

  DeviceStore(Context c) {
    p = c.getSharedPreferences("device", Context.MODE_PRIVATE);
    p.edit().remove("address").remove("name").remove("reconnect").remove("preferUsb").apply();
  }

  String usbName() { return p.getString("usbName", null); }
  void rememberUsb(String name) { p.edit().putString("usbName", DisplayText.deviceName(name)).apply(); }
  void forgetUsb() { p.edit().remove("usbName").apply(); }
}
