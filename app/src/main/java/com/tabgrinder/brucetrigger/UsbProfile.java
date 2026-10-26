package com.tabgrinder.brucetrigger;

import android.hardware.usb.*;

/** Only the native Espressif Serial/JTAG CDC profile is enabled in this release. */
final class UsbProfile {
  final UsbInterface control, data;
  final UsbEndpoint input, output;
  private UsbProfile(UsbInterface c, UsbInterface d, UsbEndpoint i, UsbEndpoint o) {
    control = c; data = d; input = i; output = o;
  }
  static UsbProfile find(UsbDevice device) {
    if (device == null || device.getVendorId() != 0x303a || device.getProductId() != 0x1001
        || device.getInterfaceCount() > 8) return null;
    UsbInterface control = null, data = null;
    UsbEndpoint input = null, output = null;
    for (int n = 0; n < device.getInterfaceCount(); n++) {
      UsbInterface itf = device.getInterface(n);
      if (itf.getAlternateSetting() != 0 || itf.getEndpointCount() > 8) return null;
      if (itf.getInterfaceClass() == 2 && itf.getInterfaceSubclass() == 2) {
        if (control != null) return null;
        control = itf;
      }
      if (itf.getInterfaceClass() != 10) continue;
      if (data != null || itf.getEndpointCount() != 2) return null;
      data = itf;
      for (int e = 0; e < itf.getEndpointCount(); e++) {
        UsbEndpoint ep = itf.getEndpoint(e);
        if (ep.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK
            || ep.getMaxPacketSize() < 1 || ep.getMaxPacketSize() > 512) return null;
        if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
          if (input != null) return null;
          input = ep;
        } else {
          if (output != null) return null;
          output = ep;
        }
      }
    }
    // Exact known profile avoids binding a data channel to an unrelated control interface.
    if (control == null || data == null || input == null || output == null
        || control.getId() != 0 || data.getId() != 1) return null;
    return new UsbProfile(control, data, input, output);
  }
  static String id(UsbDevice d) {
    return String.format(java.util.Locale.ROOT, "%04X:%04X", d.getVendorId(), d.getProductId());
  }
}
