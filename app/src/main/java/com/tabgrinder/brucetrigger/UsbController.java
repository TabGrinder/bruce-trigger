package com.tabgrinder.brucetrigger;

import android.app.PendingIntent;
import android.content.*;
import android.content.pm.PackageManager;
import android.hardware.usb.*;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Foreground-only native USB. No reset, DTR/RTS, bootloader, configuration or flash requests. */
final class UsbController {
  private final Context context;
  private final TransportListener listener;
  private final UsbManager manager;
  private final SharedPreferences recovery;
  private boolean recoveryOwned;
  private final Handler h = new Handler(Looper.getMainLooper());
  // One process-wide I/O owner also bounds workers across Activity recreation.
  private static final ThreadPoolExecutor io = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
      new ArrayBlockingQueue<Runnable>(1), r -> { Thread t = new Thread(r, "Bruce-USB"); t.setDaemon(true); return t; });
  private static java.lang.ref.WeakReference<UsbController> owner = new java.lang.ref.WeakReference<>(null);
  static { io.allowCoreThreadTimeOut(true); }
  final UsbSerialSession session = new UsbSerialSession();
  private int generation;
  private Run current;
  private UsbDevice pendingDevice;
  private BroadcastReceiver permissionReceiver;
  private PendingIntent permissionIntent;
  private boolean listening, destroyed;
  private boolean closing, destroyAfterClose;
  private long closeDeadline;
  private String closeReason;
  private String captureOutcome = "No capture request";
  private String lastThemeSummary = "No theme sync";
  private String lastGameSummary = "Not started";
  private String lastGameAttempt="None",interpreterFence="None";
  private int connectionNumber;
  private int lastGameConnection;
  private String lastReadMetrics="No reads",lastWriteMetrics="No writes";
  private String lastReadTransfers="No reads";
  private String presenceEvidence="No attachment loss observed";
  private boolean bootEvidence;
  private String portName = "None", lastClosure = "None", phase = "IDLE";
  private String claimNormal = "Not attempted", claimFallback = "Not attempted";
  private String profileDetails = "Not selected";
  private final ArrayDeque<String> trace = new ArrayDeque<>();
  private long started;
  private final Runnable permissionDeadline = () -> {
    if (pendingDevice != null) fail("USB permission request expired. Tap CONNECT to try again.");
  };
  private final BroadcastReceiver detach = new BroadcastReceiver() {
    public void onReceive(Context c, Intent intent) {
      // Re-query Android rather than trusting a broadcast-supplied Parcelable.
      if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) checkPresence("Android detach broadcast");
    }
  };
  private final Runnable ticker = new Runnable() {
    public void run() {
      if (destroyed || current == null) return;
      checkPresence("Periodic Android enumeration");
      String before = session.status;
      session.tick(SystemClock.elapsedRealtime());
      updateRecovery();
      if (!before.equals(session.status)) reportState();
      finishClose();
      if (current != null) h.postDelayed(this, 250);
    }
  };
  private static final class Run {
    final int token;
    final UsbDevice device;
    final UsbProfile profile;
    final AtomicBoolean cancelled = new AtomicBoolean();
    final AtomicReference<byte[]> outbound = new AtomicReference<>();
    final AtomicInteger pendingReads = new AtomicInteger();
    // Reserve capacity BEFORE reading. Small CDC packets are normal during redraws;
    // counting them as a flood can disconnect valid menus.
    final Semaphore readSlots = new Semaphore(8);
    final AtomicInteger peakReads = new AtomicInteger();
    final AtomicInteger readWaits = new AtomicInteger();
    final AtomicLong readCalls = new AtomicLong(), negativeReads = new AtomicLong(), zeroReads = new AtomicLong();
    final AtomicLong writeChunks = new AtomicLong(), writeBytes = new AtomicLong();
    Run(int t, UsbDevice d, UsbProfile p) { token = t; device = d; profile = p; }
  }
  UsbController(Context context, TransportListener listener) {
    this.context = context.getApplicationContext(); this.listener = listener;
    manager = (UsbManager) this.context.getSystemService(Context.USB_SERVICE);
    recovery = this.context.getSharedPreferences("usb_recovery", Context.MODE_PRIVATE);
    restoreRecovery();
  }
  boolean supported() { return manager != null && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST); }
  List<UsbDevice> devices() {
    if (!supported()) return Collections.emptyList();
    ArrayList<UsbDevice> result = new ArrayList<>();
    try { result.addAll(manager.getDeviceList().values()); } catch (RuntimeException ignored) { }
    result.sort((a, b) -> a.getDeviceName().compareTo(b.getDeviceName()));
    return result.size() > 32 ? new ArrayList<>(result.subList(0, 32)) : result;
  }
  boolean hasPendingConnection() { return current != null || pendingDevice != null; }
  boolean isOpen() { return current != null && session.stage != UsbSerialSession.Stage.CLOSED; }
  boolean isReady() { return !closing && isOpen() && session.verified && session.stage != UsbSerialSession.Stage.BLOCKED; }
  boolean isClosing() { return closing; }
  boolean hasCapture() { return session.mirror.active || (session.mirror.busy() && !session.mirror.syncing && !session.mirror.probing); }
  boolean captureDraining() { return session.mirror.draining(SystemClock.elapsedRealtime()); }
  boolean needsCaptureCleanup() { return session.mirror.busy() || hasCapture() || captureDraining() || session.game.engaged(); }
  boolean interpreterRecoveryRequired(){return !interpreterFence.equals("None");}
  void confirmInterpreterRestart(){if(current==null && pendingDevice==null)clearRecovery();}
  private void restoreRecovery() {
    interpreterFence=recovery.getBoolean("pending",false)?"Previous interpreter completion was not confirmed":"None";
  }
  private void rememberInterpreter() {
    recoveryOwned=true;
    if(!recovery.getBoolean("pending",false))recovery.edit().putBoolean("pending",true).apply();
  }
  private void clearRecovery() {
    interpreterFence="None";recoveryOwned=false;
    if(recovery.getBoolean("pending",false))recovery.edit().putBoolean("pending",false).apply();
  }
  private boolean interpreterUnconfirmed() {
    return session.game.engaged() || session.mirror.busy() || session.mirror.active || session.externalInterpreter;
  }
  private void updateRecovery() {
    if(session.bootObserved){bootEvidence=true;clearRecovery();}
    else if(recoveryOwned && !interpreterUnconfirmed() && session.stage==UsbSerialSession.Stage.OPEN
        && !session.mirror.draining(SystemClock.elapsedRealtime()))clearRecovery();
  }
  boolean commandIdle() { return isReady() && session.canControl(SystemClock.elapsedRealtime()); }
  int sessionToken() { return generation; }
  boolean matchesSession(int token) { return token == generation && isReady(); }
  String inputState() { return session.status; }
  String verifiedName() { return session.board; }
  String version() { return session.version; }
  private void event(String s) {
    if (trace.size() >= 24) trace.removeFirst();
    trace.addLast((SystemClock.elapsedRealtime() - started) + "ms " + s);
  }
  private boolean attached(UsbDevice d) {
    if (manager == null || d == null) return false;
    try {
      UsbDevice actual = manager.getDeviceList().get(d.getDeviceName());
      return actual != null && actual.getDeviceId() == d.getDeviceId()
          && actual.getVendorId() == d.getVendorId() && actual.getProductId() == d.getProductId();
    } catch (RuntimeException ex) { return false; }
  }
  void connect(UsbDevice device) {
    UsbController prior = owner.get();
    if (needsCaptureCleanup() && session.stage != UsbSerialSession.Stage.BLOCKED) {
      listener.state("USB capture active", "Stop capture before selecting another USB connection."); return;
    }
    if (prior != null && prior != this && prior.needsCaptureCleanup()) {
      prior.closeSafely("USB ownership change requested");
      listener.state("USB cleanup", "Wait for capture cleanup, then detect USB again."); return;
    }
    if (closing || (prior != null && prior != this && prior.closing)) {
      listener.state("USB cleanup", "Wait for capture to stop before reconnecting."); return;
    }
    disconnect("New USB selection");
    if (destroyed) return;
    UsbController previous = owner.get();
    if (previous != null && previous != this) previous.disconnect("USB ownership transferred");
    restoreRecovery();
    owner = new java.lang.ref.WeakReference<>(this);
    trace.clear(); started = SystemClock.elapsedRealtime(); captureOutcome = "No capture request";
    connectionNumber++;
    lastThemeSummary = "No theme sync";
    presenceEvidence="No attachment loss observed";bootEvidence=false;
    portName = "None"; claimNormal = claimFallback = "Not attempted";
    profileDetails = "Not selected";
    UsbProfile p = UsbProfile.find(device);
    if (!supported() || p == null || !attached(device)) {
      fail("No supported native Espressif CDC device is attached. Use a USB data cable and Android OTG/host."); return;
    }
    portName = UsbProfile.id(device);
    profileDetails = "control=" + p.control.getId() + " (not claimed), data=" + p.data.getId()
        + ", IN bulk maxPacket=" + p.input.getMaxPacketSize()
        + ", OUT bulk maxPacket=" + p.output.getMaxPacketSize();
    event("Selected native CDC " + portName);
    try {
      if (!listening) {
        register(detach, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)); listening = true;
      }
      if (manager.hasPermission(device)) { open(device); return; }
      pendingDevice = device;
      final int token = generation;
      final String action = context.getPackageName() + ".USB_PERMISSION." + UUID.randomUUID();
      permissionReceiver = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
          if (token != generation || !action.equals(i.getAction()) || pendingDevice == null) return;
          UsbDevice requested = pendingDevice;
          boolean granted;
          try { granted = attached(requested) && manager.hasPermission(requested); }
          catch (RuntimeException ex) { granted = false; }
          cancelPermission();
          if (granted) open(requested);
          else fail("USB access was not granted. Tap CONNECT when ready.");
        }
      };
      register(permissionReceiver, new IntentFilter(action));
      permissionIntent = PendingIntent.getBroadcast(context, 0,
          new Intent(action).setPackage(context.getPackageName()), PendingIntent.FLAG_IMMUTABLE);
      phase = "PERMISSION";
      listener.state("USB permission", "Allow Android to connect to the selected USB device.");
      h.postDelayed(permissionDeadline, 60000);
      manager.requestPermission(device, permissionIntent);
    } catch (RuntimeException ex) { fail("Android could not grant USB access. Disconnect and detect again."); }
  }
  private void register(BroadcastReceiver receiver, IntentFilter filter) {
    if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
    else context.registerReceiver(receiver, filter);
  }
  private void cancelPermission() {
    pendingDevice = null; h.removeCallbacks(permissionDeadline);
    if (permissionIntent != null) { permissionIntent.cancel(); permissionIntent = null; }
    if (permissionReceiver != null) {
      try { context.unregisterReceiver(permissionReceiver); } catch (RuntimeException ignored) { }
      permissionReceiver = null;
    }
  }
  private void open(UsbDevice device) {
    final Run run = new Run(generation, device, UsbProfile.find(device));
    if (run.profile == null || !attached(device)) { fail("USB device changed before opening."); return; }
    current = run; phase = "OPENING";
    listener.state("Opening USB", "Opening native CDC without reset signals…");
    try { io.execute(() -> pump(run)); }
    catch (RejectedExecutionException e) { fail("Previous USB close is still pending. Wait, then detect again."); }
  }
  private boolean valid(Run r) { return !destroyed && owner.get() == this && current == r && generation == r.token && !r.cancelled.get(); }
  private void claimResult(Run r, boolean fallback, String result) {
    h.post(() -> { if (valid(r)) {
      if (fallback) claimFallback = result; else claimNormal = result;
      event("CDC data interface " + r.profile.data.getId() + ", "
          + (fallback ? "Android driver handoff" : "normal claim") + ": " + result);
    }});
  }
  private void pump(Run r) {
    UsbDeviceConnection connection = null; boolean dClaim = false;
    try {
      if (r.cancelled.get()) return;
      connection = manager.openDevice(r.device);
      if (connection == null) throw new java.io.IOException("Android could not open the USB port.");
      if (r.cancelled.get()) return;
      // Only bulk IN/OUT are used. Leave CDC control and JTAG interfaces unclaimed.
      try {
        dClaim = connection.claimInterface(r.profile.data, false);
        claimResult(r, false, dClaim ? "Succeeded" : "Returned false");
      } catch (RuntimeException ex) {
        claimResult(r, false, "Android exception");
        throw ex;
      }
      if (!dClaim) {
        if (r.cancelled.get()) return;
        if (!attached(r.device) || !manager.hasPermission(r.device))
          throw new java.io.IOException("USB attachment or permission changed before interface claim.");
        // Android's documented force option permits a kernel-driver handoff if
        // necessary. It does not flash firmware or issue a device reset. One
        // fallback, only for this explicitly selected and validated CDC profile.
        try {
          dClaim = connection.claimInterface(r.profile.data, true);
          claimResult(r, true, dClaim ? "Succeeded" : "Returned false");
        } catch (RuntimeException ex) {
          claimResult(r, true, "Android exception");
          throw ex;
        }
      }
      if (!dClaim) throw new java.io.IOException("Android could not reserve CDC data interface 1,"
          + " including the driver-handoff attempt. The API does not identify the owner."
          + " Disconnect the cable, close any other USB terminal, then reconnect. No command was sent.");
      if (r.cancelled.get()) return;
      // Hardware USB Serial/JTAG does not need CDC DTR/RTS or baud changes.
      // No controlTransfer, JTAG claim, baud change, reset or configuration switch.
      h.post(() -> { if (valid(r)) {
        session.open(SystemClock.elapsedRealtime()); phase = "OPEN"; event("CDC open; no firmware command sent");
        reportState(); h.post(ticker);
      }});
      // USBDEVFS_BULK can return a negative timeout after consuming full USB
      // packets, without copying those partial bytes to Android. A 4096-byte
      // request could therefore silently lose data on our 64-byte endpoint.
      // Request exactly one endpoint packet: a full packet completes the read
      // immediately, as does a short packet. Never aggregate packets inside a
      // timed kernel read, and never inspect the buffer after a negative result.
      byte[] buffer = new byte[r.profile.input.getMaxPacketSize()];
      while (!r.cancelled.get()) {
        byte[] write = r.outbound.getAndSet(null);
        if (write != null) {
          if (r.cancelled.get()) break;
          final UsbDeviceConnection port = connection;
          int n = PacedUsbWriter.write(write, r.cancelled::get, chunk -> {
            int delivered = port.bulkTransfer(r.profile.output, chunk, chunk.length, 1000);
            r.writeChunks.incrementAndGet();
            if (delivered > 0 && delivered <= chunk.length) r.writeBytes.addAndGet(delivered);
            return delivered;
          }, Thread::sleep);
          if (r.cancelled.get()) break;
          final int count = n;
          h.post(() -> { if (valid(r)) { session.written(count, write.length, SystemClock.elapsedRealtime()); event(session.status); reportState(); } });
          if (n != write.length) throw new java.io.IOException("USB write incomplete; no automatic retry. Execution is unknown.");
        }
        if (r.cancelled.get()) break;
        if (!r.readSlots.tryAcquire(25, TimeUnit.MILLISECONDS)) {
          r.readWaits.incrementAndGet();
          continue; // Return to outbound/cancellation checks; never discard serial bytes.
        }
        long readStarted = System.nanoTime();
        r.readCalls.incrementAndGet();
        // A short single-packet wait lets pending controls reach the device promptly.
        // Never enlarge this read: partial multi-packet timeouts can discard data.
        int count = connection.bulkTransfer(r.profile.input, buffer, buffer.length, 12);
        if (count > 0) {
          if (count > buffer.length) throw new java.io.IOException("Invalid USB bulk-transfer length.");
          int pending = r.pendingReads.incrementAndGet();
          r.peakReads.accumulateAndGet(pending, Math::max);
          byte[] bytes = Arrays.copyOf(buffer, count);
          h.post(() -> {
            try {
            if (!valid(r)) return;
            receiveRead(r,bytes);
            } finally {
              r.pendingReads.decrementAndGet();
              r.readSlots.release();
            }
          });
        } else {
          r.readSlots.release();
          if (count < 0) {
            r.negativeReads.incrementAndGet();
            // Android uses -1 for both ordinary timeouts and errors. Check presence
            // on main rather than interpreting a quiet CLI as a disconnect.
            // A normal timed read already yielded the worker. Back off only on
            // immediate errors; an extra gap after every idle read stalls USB TX.
            if (System.nanoTime() - readStarted < 5_000_000L) {
              try { Thread.sleep(10); } catch (InterruptedException ex) { break; }
            }
          } else {
            r.zeroReads.incrementAndGet();
          }
        }
      }
    } catch (Exception ex) {
      String reason = ex instanceof java.io.IOException ? ex.getMessage() : "USB access failed or permission was revoked.";
      h.post(() -> { if (valid(r)) fail(reason); });
    } finally {
      if (connection != null) {
        try { if (dClaim) connection.releaseInterface(r.profile.data); } catch (RuntimeException ignored) { }
        try { connection.close(); } catch (RuntimeException ignored) { }
      }
    }
  }
  /** Keep the existing bounded parse/presentation steps for every accepted
   * endpoint size; a game record can span any number of these reads. */
  private void receiveRead(Run r,byte[] bytes) {
    for(int at=0;at<bytes.length && valid(r);at+=256) {
      byte[] part=bytes.length<=256?bytes:Arrays.copyOfRange(bytes,at,Math.min(at+256,bytes.length));
      boolean verified=session.verified;
      String before=session.status;
      session.receive(part,SystemClock.elapsedRealtime());
      updateRecovery();
      if("Capture stop acknowledged".equals(session.status))captureOutcome="Stop acknowledged by script and interpreter";
      else if("Capture script completed · waiting for screen data".equals(session.status))captureOutcome="Start acknowledged by script and interpreter";
      byte[] body=session.takeScriptBody();
      if(body!=null && !r.outbound.compareAndSet(null,body))session.block("USB worker busy during script upload. No replay.");
      listener.data(part);
      if(!valid(r))return;
      if(!before.equals(session.status)){event(session.status);reportState();}
      if(!verified && session.verified)listener.ready("usb:"+portName,session.board);
      finishClose();
    }
  }
  private void checkPresence(String source) {
    UsbDevice d = current == null ? pendingDevice : current.device;
    if(d==null)return;
    String evidence=null;
    try {
      UsbDevice actual=manager.getDeviceList().get(d.getDeviceName());
      if(actual==null)evidence="Selected device absent from Android enumeration";
      else if(actual.getDeviceId()!=d.getDeviceId() || actual.getVendorId()!=d.getVendorId()
          || actual.getProductId()!=d.getProductId())evidence="Android device identity changed";
      else if(current!=null && !manager.hasPermission(d))evidence="Android USB permission no longer granted";
    } catch(RuntimeException ex) { evidence="Android USB enumeration or permission query failed"; }
    if(evidence!=null){presenceEvidence=source+": "+evidence;
      fail(presenceEvidence+". Pending commands were discarded. Physical cause is unconfirmed.");}
  }
  boolean command(String command) {
    if (closing || current == null || !valid(current)) return false;
    if (command == null || (session.mirror.active && !command.startsWith("nav "))) return false;
    byte[] bytes = session.begin(command, SystemClock.elapsedRealtime());
    if (bytes == null) return false;
    if (!current.outbound.compareAndSet(null, bytes)) { session.block("USB worker busy; command not replayed."); return false; }
    event("Requested " + command); listener.sent(command); reportState(); return true;
  }
  void cancelQueuedInput() {
    // A covered touch or an app dialog cancels user movement, not a read-only
    // metadata query or the upload/cleanup needed to leave an interpreter.
    if (session.pending != null && session.pending.startsWith("nav ")) cancelQueuedCommands();
  }
  void cancelQueuedCommands() {
    if (session.mirror.busy() || session.game.uploading()) return;
    if (current != null && current.outbound.getAndSet(null) != null) {
      if ((session.mirror.active || session.game.active) && session.stage == UsbSerialSession.Stage.WRITING) {
        session.stage = UsbSerialSession.Stage.OPEN; session.pending = null;
        session.status = "Unsent input cancelled"; reportState(); return;
      }
      session.block("Queued input cancelled when the app lost focus. Reconnect before control.");
      reportState();
    }
  }
  private void reportState() {
    listener.state(closing ? "USB · Stopping capture" : session.stage == UsbSerialSession.Stage.BLOCKED ? "USB control stopped"
        : isReady() ? "USB · Bruce verified"
        : session.stage == UsbSerialSession.Stage.WRITING || session.stage == UsbSerialSession.Stage.WAITING
            ? "USB · Verifying Bruce" : "USB open · Verify Bruce", session.status);
  }
  private void fail(String message) {
    event(message); disconnect(message); phase = "ERROR";
    listener.state("Connection error", message);
  }
  boolean mirror(boolean start) { return !closing && launchMirror(start); }
  boolean probeCapabilities() {
    if (closing || interpreterRecoveryRequired() || current == null || !valid(current)) return false;
    String nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    byte[] header = session.beginProbe(nonce, SystemClock.elapsedRealtime());
    if (header == null) return false;
    rememberInterpreter();
    if (!current.outbound.compareAndSet(null, header)) {
      session.block("USB worker busy during capability check. No replay."); return false;
    }
    event(session.lastCommand);reportState();return true;
  }
  boolean menuQueryReady() { return isReady() && session.canQueryMenu(SystemClock.elapsedRealtime()); }
  boolean selectRoot(int index, String expectedJson) {
    if(closing || current==null || !valid(current))return false;
    byte[] bytes=session.beginRootSelection(index,expectedJson,SystemClock.elapsedRealtime());
    if(bytes==null)return false;
    if(!current.outbound.compareAndSet(null,bytes)){session.block("USB worker busy during root selection. No replay.");reportState();return false;}
    event("Requested "+session.lastCommand);listener.sent(session.lastCommand);reportState();return true;
  }
  boolean queryMenu() {
    if(closing || current==null || !valid(current))return false;
    byte[] bytes=session.beginMenuQuery(SystemClock.elapsedRealtime());if(bytes==null)return false;
    if(!current.outbound.compareAndSet(null,bytes)){session.block("USB worker busy during menu query. No replay.");reportState();return false;}
    event("Requested optionsJSON");reportState();return true;
  }
  boolean readFilesInfo(String expectedJson) {
    if(closing||current==null||!valid(current))return false;
    byte[] bytes=session.beginFilesInfo(expectedJson,SystemClock.elapsedRealtime());if(bytes==null)return false;
    if(!current.outbound.compareAndSet(null,bytes)){session.block("USB worker busy during File Info. No replay.");reportState();return false;}
    event("Reading selected File Info");reportState();return true;
  }
  boolean launchFilesGame(String template,String path) {
    if(closing || interpreterRecoveryRequired() || current==null || !valid(current))return false;
    String nonce=UUID.randomUUID().toString().replace("-", "").substring(0,16);
    byte[] header=session.beginFilesGame(template,nonce,path,SystemClock.elapsedRealtime());if(header==null)return false;
    rememberInterpreter();
    if(!current.outbound.compareAndSet(null,header)){session.block("USB worker busy during Files launch. No replay.");reportState();return false;}
    event(session.lastCommand);reportState();return true;
  }
  boolean launchMegalodon(String template) {
    if(closing || interpreterRecoveryRequired() || current==null || !valid(current))return false;
    String nonce=UUID.randomUUID().toString().replace("-", "").substring(0,16);
    byte[] header=session.beginMegalodon(template,nonce,SystemClock.elapsedRealtime());if(header==null)return false;
    rememberInterpreter();
    if(!current.outbound.compareAndSet(null,header)){session.block("USB worker busy during Megalodon launch. No replay.");reportState();return false;}
    event(session.lastCommand);reportState();return true;
  }
  private boolean stopGame() {
    if(current==null || !valid(current)) return false;
    byte[] command=session.beginGameStop(SystemClock.elapsedRealtime());
    if(command==null)return false;
    if(!current.outbound.compareAndSet(null,command)){session.block("USB worker busy during game stop. No replay.");reportState();return false;}
    event("Requested game exit");reportState();return true;
  }
  boolean syncTheme() {
    return syncTheme(null);
  }
  boolean syncTheme(String observedKey) {
    if (closing || interpreterRecoveryRequired() || current == null || !valid(current)) return false;
    String nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    byte[] header = session.beginThemeSync(nonce, observedKey, SystemClock.elapsedRealtime());
    if (header == null) return false;
    rememberInterpreter();
    if (!current.outbound.compareAndSet(null, header)) {
      session.block("USB worker busy during theme sync. No replay."); return false;
    }
    event(observedKey == null ? session.lastCommand : "Sync observed image folder · " + ObservedThemeScript.folder(observedKey));
    reportState(); return true;
  }
  private boolean launchMirror(boolean start) {
    if(interpreterRecoveryRequired())return false;
    if (current == null || !valid(current)) return false;
    String nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    byte[] header = session.beginMirror(start, nonce, SystemClock.elapsedRealtime());
    if (header == null) return false;
    rememberInterpreter();
    if (!current.outbound.compareAndSet(null, header)) {
      session.block("USB worker busy during capture setup. No replay."); return false;
    }
    captureOutcome = start ? "Start requested" : "Stop requested";
    event(session.lastCommand); reportState(); return true;
  }
  void closeSafely(String reason) {
    if (closing) { finishClose(); return; }
    if (current == null && pendingDevice == null && session.stage == UsbSerialSession.Stage.CLOSED) {
      finishDestruction();
      return; // Preserve the actual closure reason on repeated lifecycle callbacks.
    }
    if (!needsCaptureCleanup() || current == null) { disconnect(reason); return; }
    closing = true; closeReason = reason; closeDeadline = SystemClock.elapsedRealtime() + 15000;
    event("Bounded capture cleanup requested: " + reason);
    reportState();
    finishClose();
  }
  private void finishClose() {
    if (!closing) return;
    long now = SystemClock.elapsedRealtime();
    if(session.game.engaged()) {
      if(now>=closeDeadline) {
        disconnect(closeReason+"; game exit unconfirmed. Restart Bruce before reconnecting.");return;
      }
      if(session.stage==UsbSerialSession.Stage.BLOCKED) {
        if(stopGame())return;
        disconnect(closeReason+"; game exit unconfirmed. Restart Bruce before reconnecting.");return;
      }
      if(!session.game.uploading() && session.idle(now))stopGame();
      return;
    }
    if (session.externalInterpreter) {
      disconnect(closeReason + "; running script prevents capture cleanup. Restart Bruce before reconnecting.");
      return; // Never start a second stock interpreter over an observed game/script.
    }
    // elapsedRealtime advances through phone sleep, while Handler dispatch can pause.
    // An already observed stop acknowledgement is not undone by a delayed timer.
    if (session.stage == UsbSerialSession.Stage.OPEN && !hasCapture() && !session.mirror.busy()
        && now >= session.mirror.drainUntil) {
      disconnect(closeReason + (session.game.ended() ? "; game exit acknowledged" : session.mirror.syncing
          ? "; theme interpreter completion acknowledged" : "; capture stop acknowledged")); return;
    }
    if (session.stage == UsbSerialSession.Stage.BLOCKED || now >= closeDeadline) {
      disconnect(closeReason + (session.mirror.syncing?"; theme interpreter completion unconfirmed. Restart Bruce before reconnecting."
          :"; capture stop unconfirmed. Restart Bruce before reconnecting.")); return;
    }
    if (!session.mirror.busy() && session.idle(now)) {
      if (session.mirror.active) launchMirror(false);
      else if (now >= session.mirror.drainUntil) disconnect(closeReason + "; capture stop acknowledged");
    }
  }
  void disconnect(String reason) {
    if(current!=null) {
      lastReadMetrics=current.pendingReads.get()+"/8; peak="+current.peakReads.get()+"; waits="+current.readWaits.get();
      lastReadTransfers=readTransfers(current);
      lastWriteMetrics="chunks="+current.writeChunks.get()+"; host-accepted bytes="+current.writeBytes.get();
    }
    if(session.game.attempted) {
      lastGameAttempt=session.game.summary()+"; protocol failure="+session.failure+"; closure="+reason
          +"; USB queue="+lastReadMetrics
          +"\nGame connection events:\n"+String.join("\n",trace);
      lastGameConnection=connectionNumber;
    }
    if(session.bootObserved){bootEvidence=true;clearRecovery();}
    else if(interpreterUnconfirmed()){
      rememberInterpreter();recoveryOwned=false;
      interpreterFence="Previous interpreter completion was not confirmed";
    } else if(recoveryOwned && !session.mirror.draining(SystemClock.elapsedRealtime()))clearRecovery();
    if (session.stage != UsbSerialSession.Stage.CLOSED) {lastThemeSummary = session.mirror.themes.summary();lastGameSummary=session.game.summary();}
    boolean gameUnconfirmed = session.game.engaged();
    boolean captureUnconfirmed = hasCapture();
    if (captureUnconfirmed) captureOutcome = "Capture stop unconfirmed";
    generation++; cancelPermission(); h.removeCallbacks(ticker);
    if (current != null) { current.cancelled.set(true); current.outbound.set(null); current = null; }
    if (listening) { try { context.unregisterReceiver(detach); } catch (RuntimeException ignored) { } listening = false; }
    lastClosure = reason + (captureUnconfirmed && !reason.contains("capture stop unconfirmed")
        ? " · Capture stop unconfirmed; restart Bruce before reconnecting." : "")
        + (gameUnconfirmed && !reason.contains("game exit unconfirmed")
          ? " · Game exit unconfirmed; restart Bruce before reconnecting." : "")
        + (session.mirror.busy() && session.mirror.syncing?" · Theme script completion unconfirmed; restart Bruce before reconnecting.":"");
    closing = false; closeReason = null; phase = "IDLE"; session.close();
    listener.state("Disconnected", lastClosure);
    finishDestruction();
  }
  private void finishDestruction() {
    if (destroyAfterClose) {
      destroyed = true;
      if (owner.get() == this) owner = new java.lang.ref.WeakReference<>(null);
    }
  }
  void destroy() {
    destroyAfterClose = true;
    closeSafely("Activity destroyed");
  }
  private String readTransfers(Run r) {
    // Diagnostics must not interrupt cleanup of an incomplete connection.
    String size=r.profile==null||r.profile.input==null?"Unavailable":r.profile.input.getMaxPacketSize()+" bytes";
    return "single-packet; request="+size+"; calls="+r.readCalls.get()
        +"; negative="+r.negativeReads.get()+"; zero="+r.zeroReads.get();
  }
  String diagnostics() {
    return "Transport: native USB CDC\nAndroid API: " + Build.VERSION.SDK_INT
        + "\nUSB host supported: " + supported() + "\nUSB profile: " + portName
        + "\nUSB interfaces: " + profileDetails
        + "\nCDC data normal claim: " + claimNormal
        + "\nCDC data driver-handoff claim: " + claimFallback
        + "\nControl / JTAG interface claims: none"
        + "\nStage: " + phase + "/" + session.stage + "\nBruce response verified: " + session.verified
        + "\nReported firmware: " + session.version + "\nReported board: " + session.board
        + "\nWrite receipts: " + session.writes + "\nReceived bytes: " + session.receivedBytes
        + "\nFirmware prompts: " + session.prompts + "\nUnsolicited prompts: " + session.unsolicited
        + "\nLate prompts: " + session.late + "\nLast command: " + session.lastCommand
        + "\nFirmware capability negotiation: " + session.mirror.probeState()
        + "\nUSB mirror bridge: " + session.mirror.state() + "\nCapture cleanup pending: " + closing
        + "\nLast capture outcome: " + captureOutcome
        + "\nCommand status: " + session.status + "\nLast protocol failure: " + session.failure
        + "\nLast closure: " + lastClosure + "\nReset / DTR / RTS / flash requests: none"
        + "\nUSB loss evidence: " + presenceEvidence
        + "\nFirmware boot/panic text observed: " + bootEvidence
        + "\nUSB read queue: " + (current == null ? lastReadMetrics+" (last session, closed)" : current.pendingReads.get() + "/8; peak="
            + current.peakReads.get() + "; waits=" + current.readWaits.get())
        + "\nUSB input: " + (current==null?lastReadTransfers+" (last session)":readTransfers(current))
        + "\nUSB writes: 64-byte chunks / 25-ms gaps; chunks=" + (current==null?"Closed":current.writeChunks.get())
        + "; host-accepted bytes=" + (current==null?"Closed":current.writeBytes.get())
        + (current==null?"; retained "+lastWriteMetrics:"")
        + "\nUSB parsing: 256-byte slices; completed game transactions consumed before the next slice"
        + "\nHost write receipts do not verify complete firmware reception."
        + "\nTheme sync: " + (session.stage == UsbSerialSession.Stage.CLOSED ? lastThemeSummary + " (last session)" : session.mirror.themes.summary())
        + "\nGame mirror: " + (session.stage==UsbSerialSession.Stage.CLOSED?lastGameSummary:session.game.summary())
        + "\nMost recent game attempt: connection "+lastGameConnection+" (current="+connectionNumber+") · "+lastGameAttempt
        + "\nInterpreter recovery fence: "+interpreterFence
        + "\nExternal interpreter observed: " + session.externalInterpreter
        + "\n\nSession events:\n" + String.join("\n", trace)
        + "\n\nFirmware identification is self-reported, not cryptographic authentication."
        + " A prompt confirms CLI return, not a menu change.";
  }
}
