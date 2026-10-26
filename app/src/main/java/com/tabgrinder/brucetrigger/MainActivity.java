package com.tabgrinder.brucetrigger;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.usb.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONObject;

public final class MainActivity extends Activity implements TransportListener {
  private static final int BG = FusionTheme.BACKGROUND,
      PANEL = FusionTheme.PANEL,
      INK = FusionTheme.TEXT,
      MUTED = FusionTheme.MUTED,
      ACCENT = FusionTheme.CYAN;
  private DeviceStore store;
  private SessionStore sessions;
  private UsbController usb;
  private EncoderControl encoderControl;
  private final GameInputQueue gameInputs = new GameInputQueue();
  private final Runnable gameInputTick = this::drainGameInput;
  private final GameControlButton.Input gameButtons = new GameControlButton.Input() {
    public boolean enabled(){return gameInputMode();}
    public boolean press(String command,boolean repeat){return queueGameInput(command,repeat);}
    public void release(String command){gameInputs.release(command);}
  };
  private boolean linkReady() { return usb.isReady(); }
  private boolean linkIdle() { return usb.commandIdle(); }
  private boolean linkCommand(String c) {
    boolean sent=usb.command(c);
    if(sent && menuMode && c.startsWith("nav ")) {
      menuSnapshot=null;menuDue=0;menuNavigationAt=SystemClock.elapsedRealtime();
      ui.removeCallbacks(menuTick);ui.post(menuTick);updateScreenStatus();
    }
    return sent;
  }
  private int linkToken() { return usb.sessionToken(); }
  private boolean linkMatches(int t) { return usb.matchesSession(t); }
  private void closeLinks(String reason) { cancelConnect(); usb.closeSafely(reason); }
  private static final int FLOW_IDLE = 0,
      FLOW_CONNECT = 1,
      FLOW_VERIFY = 2,
      FLOW_THEME = 3,
      FLOW_NATIVE = 4,
      FLOW_CHECK_ROOT = 7,
      FLOW_STOP_CAPTURE = 8,
      FLOW_RETURN_ROOT = 9,
      FLOW_PREPARE_SELECTION = 10,
      FLOW_WAIT_CAPTURE = 11,
      FLOW_VERIFY_SELECTION = 12,
      FLOW_SELECTION_SENT = 13,
      FLOW_WAIT_ROOT = 14,
      FLOW_STOP_FOR_GAME = 15,
      FLOW_START_GAME = 16,
      FLOW_RESUME_NATIVE = 17,
      FLOW_FILES_CHECK = 18, FLOW_FILES_WAIT = 19, FLOW_FILE_INFO = 20, FLOW_FILE_RETURN = 21,
      FLOW_FILES_ROOT_QUERY = 22, FLOW_FILES_ROOT_WAIT = 23;
  private int connectStep, connectToken;
  private long connectDeadline;
  private final Runnable connectTick = this::advanceConnect;
  private boolean menuMode, menuRequired, menuImageShown;
  private boolean gamePresentation, gameNativePresentation;
  private boolean gameReturnToNative;
  private String pendingGameTemplate, pendingFilesPath;
  private boolean pendingMegalodon;
  private long filesRevision;
  private long filesRedrawAt;
  private int filesExitCommands, filesExitLimit;
  private long filesEscapeAt;
  private int filesExitRetries;
  private boolean filesExitUnconfirmed;
  private boolean filesRootVerified;
  private boolean gameRefreshPending;
  private final Runnable gameTick = () -> { gameRefreshPending = false; advanceGame(); };

  /** Do not debounce a continuous USB stream: keep the earliest queued render. */
  private void scheduleGameRefresh(long delay) {
    if (!foreground || !gamePresentation || gameRefreshPending) return;
    gameRefreshPending = true;
    ui.postDelayed(gameTick, delay);
  }

  private void cancelGameRefresh() {
    ui.removeCallbacks(gameTick);
    gameRefreshPending = false;
  }
  private boolean nativeFromTheme, nativeEntrySeen, themeSelectSent;
  private boolean warmRootSelection;
  private long warmRootEntries;
  private RemoteMenu pendingRootSelection;
  private String rootCaptureKey, returnImageKey, menuProblem="";
  private long lastNativeDrawAt, selectionQuerySeen;
  private long menuNavigationAt, menuLatencyMs=-1, openingAt, openingMs=-1;
  private RemoteMenu menuSnapshot;
  private Map<String,String> menuMappings=Collections.emptyMap();
  private int menuForeground=0xf81f,menuBackground;
  private long menuDue,menuReceivedAt,menuReplySeen;
  private final Runnable menuTick=this::advanceMenu;

  private MirrorView mirror;
  private final NativeMirrorStream mirrorStream = new NativeMirrorStream();
  private FrameDecoder decoder;
  private LinearLayout root, page, nav;
  private TextView connectionText, screenText, consoleText, inputText, usbStatusText, deviceSubtitle;
  private LinearLayout remoteKeys;
  private Button connectAction;
  private AlertDialog controlDialog;
  private final Set<AlertDialog> ownedDialogs =
      Collections.newSetFromMap(new WeakHashMap<AlertDialog, Boolean>());
  private final Handler ui = new Handler(Looper.getMainLooper());
  private String mainSection = "Remote";
  private String active = "Remote",
      status = "Disconnected",
      detail = "Connect a Bruce device to begin.";
  private final ConsoleBuffer console = new ConsoleBuffer();
  private boolean foreground, mirrorRequested;
  private static final int NOTES_PAGE_SIZE = 20, EXPORT_NOTE_REQUEST = 200;
  private int sessionOffset = 0, renderGeneration = 0, noteRequestId = 0;
  private boolean resumed = false;
  private boolean deviceScreenControl = false;
  private int malformed = 0;
  private int screenPackets = 0, geometryPackets = 0;
  private long lastNotification = 0;
  private String pendingExportId;
  private static final java.util.concurrent.ExecutorService exportIO =
      new java.util.concurrent.ThreadPoolExecutor(
          1,
          1,
          0,
          java.util.concurrent.TimeUnit.MILLISECONDS,
          new java.util.concurrent.SynchronousQueue<Runnable>());
  private static final Handler EXPORT_UI = new Handler(Looper.getMainLooper());
  private static final java.util.concurrent.ExecutorService notesIO =
      new java.util.concurrent.ThreadPoolExecutor(
          1,
          1,
          0,
          java.util.concurrent.TimeUnit.MILLISECONDS,
          new java.util.concurrent.ArrayBlockingQueue<Runnable>(8));
  private final Runnable tick =
      new Runnable() {
        public void run() {
          updateScreenStatus();
          if (foreground) ui.postDelayed(this, 1000);
        }
      };

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    if (state != null) {
      pendingExportId = state.getString("pendingExportId");
      String restored = state.getString("activeTab", "Remote");
      mainSection = "Device".equals(state.getString("mainSection")) ? "Device" : "Remote";
      if (Arrays.asList("Device", "Remote", "Diagnostics", "Saved notes").contains(restored))
        active = restored;
      if (active.equals("Remote") || active.equals("Device")) mainSection = active;
      sessionOffset = Math.max(0, state.getInt("sessionOffset", 0));
    }
    store = new DeviceStore(this);
    sessions = new SessionStore(this);
    mirror = new MirrorView(this);
    usb = new UsbController(this, new SelectedTransportListener(this, () -> !isDestroyed()));
    decoder =
        new FrameDecoder(
            new FrameDecoder.Sink() {
              public void frame(byte[] packet) {
                if (!usb.isReady() || !(gameNativePresentation && gamePresentation)
                    && (!mirrorRequested || !usb.session.mirror.acceptsFrames(SystemClock.elapsedRealtime()))) return;
                byte[] accepted = mirrorStream.accept(packet, SystemClock.elapsedRealtime());
                if (accepted == null) return;
                // The stock image logger reuses image slots. A root snapshot
                // owns presentation here; background records cannot overwrite it.
                if (menuMode) {
                  lastNativeDrawAt=SystemClock.elapsedRealtime();
                  if ((accepted[2]&255)==0) menuDue=0;
                  return;
                }
                if (mirror.apply(accepted)) {
                  if ((accepted[2] & 255) == 99) geometryPackets++;
                  else screenPackets++;
                }
                int opcode=accepted[2]&255;
                lastNativeDrawAt=SystemClock.elapsedRealtime();
                if(opcode==0 && themeSelectSent)nativeEntrySeen=true;
                if(opcode==18 && !gamePresentation && !usb.session.externalInterpreter) {
                  String key=NativeImageRecord.key(accepted);
                  if(connectStep == FLOW_WAIT_CAPTURE || connectStep == FLOW_VERIFY_SELECTION)rootCaptureKey=key;
                  else if(!nativeFromTheme || nativeEntrySeen) {
                    returnImageKey=key;menuRequired=true;
                    if(connectStep == FLOW_IDLE || connectStep == FLOW_SELECTION_SENT)beginConnectFlow(FLOW_CHECK_ROOT);
                  }
                }
                scheduleOutput();
              }

              public void line(String t) {
                if(gamePresentation && usb.session.game.marker(t,"UI_ON")) {
                  if(!presentGameFrames(usb.session.game.takeNativeBoundary()))return;
                  cancelGameInputs();gameNativePresentation=true;mirrorStream.beginOverlay(320,170);
                } else if(gamePresentation && usb.session.game.marker(t,"UI_OFF")) {
                  gameNativePresentation=false;mirrorStream.clearScreen();
                }
                if (t.contains("Guru Meditation") || t.trim().equals("Rebooting...")) {
                  mirrorRequested = false;
                  mirrorStream.invalidate();
                  mirror.clearScreen();
                }
                if (!t.startsWith("BT_")) appendConsole(t);
              }

              public void malformed() {
                mirrorStream.invalidate();
                malformed++;
              }
            });
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(BG);
    root.setOnApplyWindowInsetsListener(
        (v, in) -> {
          v.setPadding(
              in.getSystemWindowInsetLeft(),
              in.getSystemWindowInsetTop(),
              in.getSystemWindowInsetRight(),
              in.getSystemWindowInsetBottom());
          return in.consumeSystemWindowInsets();
        });
    setContentView(root);
    render();
  }

  @Override
  protected void onStart() {
    super.onStart(); foreground = true; ui.post(tick);
    if ("Saved notes".equals(active)) render();
  }

  @Override
  protected void onResume() {
    super.onResume();
    resumed = true;
  }

  @Override
  protected void onPause() {
    resumed = false;
    cancelUserInput();
    pendingRootSelection=null;
    usb.cancelQueuedCommands();
    super.onPause();
  }

  @Override
  protected void onStop() {
    foreground = false;
    root.setKeepScreenOn(false);
    ui.removeCallbacks(tick);
    ui.removeCallbacks(menuTick);
    cancelGameRefresh();
    ui.removeCallbacks(refreshOutput);
    refreshPending = false;
    cancelConnect();
    usb.closeSafely("App left foreground");
    mirror.clearImages();
    mirrorStream.clearScreen();
    decoder.reset();
    mirror.clearScreen();
    mirrorRequested = false;
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    foreground = resumed = false;
    for (AlertDialog dialog : new ArrayList<>(ownedDialogs)) dialog.dismiss();
    ownedDialogs.clear();
    cancelConnect(); mirror.clearImages();
    usb.destroy();
    ui.removeCallbacksAndMessages(null);
    super.onDestroy();
  }

  void trackDialog(AlertDialog dialog) {
    ownedDialogs.add(dialog);
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    if (pendingExportId != null) out.putString("pendingExportId", pendingExportId);
    out.putString("activeTab", active);
    out.putString("mainSection", mainSection);
    out.putInt("sessionOffset", sessionOffset);
  }

  @Override
  public void onBackPressed() {
    if (active.equals("Diagnostics") || active.equals("Saved notes")) {
      active = mainSection;
      render();
    } else if (active.equals("Device")) {
      active = mainSection = "Remote";
      render();
    } else super.onBackPressed();
  }

  private void openSection(String section) {
    if (active.equals("Remote") || active.equals("Device")) mainSection = active;
    active = section;
    render();
  }

  private void secondaryBack() {
    action("Back to " + mainSection, "", this::onBackPressed);
  }

  private int dp(float x) {
    return Math.round(x * getResources().getDisplayMetrics().density);
  }

  private GradientDrawable background(int c, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(c);
    d.setCornerRadius(dp(radius));
    return d;
  }

  private TextView text(String value, int sp, int color) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(sp);
    t.setTextColor(color);
    t.setPadding(0, dp(5), 0, dp(5));
    return t;
  }

  private Button button(String label, Runnable run) {
    Button b = new Button(this);
    b.setText(label);
    b.setAllCaps(false);
    b.setFilterTouchesWhenObscured(true);
    b.setTextSize(14);
    b.setTextColor(INK);
    b.setMinHeight(dp(48));
    b.setBackground(FusionTheme.surface(getResources().getDisplayMetrics().density, false, false));
    b.setPadding(dp(8), dp(4), dp(8), dp(4));
    b.setOnClickListener(v -> run.run());
    return b;
  }

  private LinearLayout row() {
    LinearLayout r = new LinearLayout(this);
    r.setOrientation(LinearLayout.HORIZONTAL);
    r.setGravity(Gravity.CENTER_VERTICAL);
    r.setPadding(0, dp(5), 0, dp(5));
    return r;
  }

  private void weighted(LinearLayout r, View v) {
    LinearLayout.LayoutParams p =
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    p.setMargins(dp(3), dp(3), dp(3), dp(3));
    r.addView(v, p);
  }

  private void gap() {
    page.addView(new View(this), new LinearLayout.LayoutParams(1, dp(12)));
  }

  private void note(String s) {
    page.addView(text(s, 13, MUTED));
  }

  private void action(String title, String subtitle, Runnable run) {
    Button b = button(title, run);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(0, dp(7), 0, 0);
    page.addView(b, p);
    if (!subtitle.isEmpty()) note(subtitle);
  }

  private void render() {
    cancelGameInputs();
    renderGeneration++;
    connectionText = null;
    connectAction = null;
    screenText = null;
    consoleText = null;
    inputText = null;
    encoderControl = null;
    remoteKeys = null;
    usbStatusText = null;
    deviceSubtitle = null;
    root.removeAllViews();
    LinearLayout top = row();
    top.setPadding(dp(16), dp(6), dp(10), dp(6));
    LinearLayout identity = new LinearLayout(this);
    identity.setOrientation(LinearLayout.VERTICAL);
    identity.setGravity(Gravity.CENTER_VERTICAL);
    identity.addView(new BrandView(this), new LinearLayout.LayoutParams(-1, dp(28)));
    deviceSubtitle = text("USB device", 11, MUTED);
    deviceSubtitle.setPadding(0, dp(2), 0, 0);
    deviceSubtitle.setSingleLine(true);
    deviceSubtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
    deviceSubtitle.setTypeface(Typeface.MONOSPACE);
    identity.addView(deviceSubtitle, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout.LayoutParams titleSpace = new LinearLayout.LayoutParams(0, -2, 1);
    titleSpace.setMargins(0, 0, dp(8), 0);
    top.addView(identity, titleSpace);
    if (active.equals("Remote")) {
      connectAction = button("CONNECT", this::connectUsb);
      connectAction.setTextSize(11);
      connectAction.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
      connectAction.setTextColor(ACCENT);
      connectAction.setMinWidth(0);
      top.addView(connectAction, new LinearLayout.LayoutParams(dp(100), -2));
    }
    ImageButton settings = new ImageButton(this);
    settings.setImageDrawable(new AppIcon("Settings", INK));
    settings.setScaleType(ImageView.ScaleType.FIT_CENTER);
    settings.setPadding(dp(13), dp(13), dp(13), dp(13));
    settings.setBackground(FusionTheme.surface(getResources().getDisplayMetrics().density, false, false));
    settings.setContentDescription("Settings");
    settings.setFilterTouchesWhenObscured(true);
    settings.setOnClickListener(v -> settings());
    LinearLayout.LayoutParams gear = new LinearLayout.LayoutParams(dp(48), dp(48));
    gear.setMargins(dp(6), 0, 0, 0);
    top.addView(settings, gear);
    root.addView(top);
    ScrollView scroll = new RemoteLayout.ViewportScroll(this);
    page = active.equals("Remote") ? new RemoteLayout(this) : new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    page.setPadding(dp(12), dp(4), dp(12), dp(8));
    scroll.addView(page);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (active.equals("Remote")) remote();
    else if (active.equals("Device")) devicePage();
    else if (active.equals("Saved notes")) savedNotesPage();
    else diagnosticsPage();
    View divider = new View(this);
    divider.setBackgroundColor(FusionTheme.EDGE);
    root.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
    nav = row();
    nav.setPadding(dp(4), 0, dp(4), 0);
    nav.setBackgroundColor(BG);
    for (String tab : new String[] {"Remote", "Device"}) {
      AppNavButton item = new AppNavButton(this, tab, tab.equals(mainSection));
      item.setOnClickListener(v -> { active = mainSection = tab; render(); });
      nav.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
    }
    int navHeight = dp(60 + 16 * Math.max(0, getResources().getConfiguration().fontScale - 1));
    root.addView(nav, new LinearLayout.LayoutParams(-1, navHeight));
    updateScreenStatus();
  }

  private void connectRow() {
    LinearLayout r = row();
    LinearLayout labels = new LinearLayout(this);
    labels.setOrientation(LinearLayout.VERTICAL);
    labels.addView(text("Bruce Device", 18, INK));
    connectionText = text(status, 12, MUTED); labels.addView(connectionText);
    r.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    connectAction = button("CONNECT", this::connectUsb);
    r.addView(connectAction); page.addView(r);
    updateScreenStatus();
  }

  private void remote() {
    if (mirror.getParent() instanceof ViewGroup) ((ViewGroup) mirror.getParent()).removeView(mirror);
    FrameLayout bezel = new FrameLayout(this);
    bezel.setBackground(FusionTheme.surface(getResources().getDisplayMetrics().density, false, true));
    bezel.setPadding(dp(8), dp(8), dp(8), dp(8));
    bezel.addView(mirror, new FrameLayout.LayoutParams(-1, -1));
    page.addView(bezel, new LinearLayout.LayoutParams(-1, dp(180)));
    LinearLayout statusRow = row();
    statusRow.setPadding(dp(10), 0, dp(10), 0);
    statusRow.setBackground(FusionTheme.surface(getResources().getDisplayMetrics().density, false, false));
    statusRow.setClickable(true);
    statusRow.setFocusable(true);
    statusRow.setFilterTouchesWhenObscured(true);
    statusRow.setOnClickListener(v -> screenStatus());
    screenText = text("STATUS", 11, MUTED);
    screenText.setTypeface(Typeface.MONOSPACE);
    screenText.setLetterSpacing(.12f);
    statusRow.addView(screenText, new LinearLayout.LayoutParams(0, -2, 1));
    usbStatusText = text("USB DISCONNECTED", 11, MUTED);
    usbStatusText.setTypeface(Typeface.MONOSPACE);
    usbStatusText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    statusRow.addView(usbStatusText, new LinearLayout.LayoutParams(-2, -2));
    LinearLayout.LayoutParams statusSpace = new LinearLayout.LayoutParams(-1, dp(48));
    statusSpace.setMargins(0, dp(6), 0, 0);
    page.addView(statusRow, statusSpace);
    encoderControl = new EncoderControl(this, () -> send("nav select"), this::turnEncoder);
    encoderControl.configureGameInput(gameButtons);
    LinearLayout.LayoutParams dialSpace = new LinearLayout.LayoutParams(dp(260), dp(260));
    dialSpace.gravity = Gravity.CENTER_HORIZONTAL;
    page.addView(encoderControl, dialSpace);
    remoteKeys = row();
    remoteKeys.setPadding(0, 0, 0, 0);
    String[] labels = {"PREV", "NEXT", "EXIT"};
    String[] commands = {"nav prev", "nav next", "nav esc"};
    for (int i = 0; i < labels.length; i++) {
      final String command = commands[i];
      GameControlButton key = new GameControlButton(this);
      key.setText(labels[i]);key.setTextColor(INK);key.setAllCaps(false);key.setFilterTouchesWhenObscured(true);
      key.setMinHeight(dp(48));key.setPadding(dp(8),dp(4),dp(8),dp(4));
      key.setOnClickListener(v -> send(command));
      key.configureGameInput(gameButtons,command,i<2);
      key.setTextSize(13);
      key.setTypeface(Typeface.MONOSPACE);
      key.setLetterSpacing(.12f);
      key.setBackground(FusionTheme.surface(getResources().getDisplayMetrics().density, true, false));
      LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, -1, 1);
      cell.setMargins(dp(3), dp(3), dp(3), dp(3));
      remoteKeys.addView(key, cell);
    }
    page.addView(remoteKeys, new LinearLayout.LayoutParams(-1, dp(58)));
    inputText = text("", 12, MUTED);
    inputText.setGravity(Gravity.CENTER);
    inputText.setMaxLines(2);
    page.addView(inputText, new LinearLayout.LayoutParams(-1, -2));
    ((RemoteLayout) page).bind(bezel, statusRow, encoderControl, remoteKeys, inputText);
    updateScreenStatus();
  }

  private void screenStatus() {
    new GuardedDialogBuilder(this)
        .setTitle("Screen status")
        .setMessage(inputStatus() + "\n\n" + presentationStatus())
        .setNegativeButton("Close", null)
        .setNeutralButton("Screen controls", (d, w) -> controlMode())
        .show();
  }

  private void devicePage() {
    connectRow(); note(detail); gap();
    page.addView(text(store.usbName() == null ? "USB connection" : store.usbName(), 22, INK));
    note("LILYGO T-Embed CC1101 / Plus · Bruce 1.16.1");
    note("Use a USB data cable. Turn off the Bruce BLE API and stop other scripts before connecting.");
    action("Getting started", "Connection and controls", this::gettingStarted);
    action("Diagnostics", "Connection report and device queries", () -> openSection("Diagnostics"));
  }

  private boolean liveUI() {
    return foreground && !isFinishing() && !isDestroyed();
  }

  private long lastNavigationRequestAt;

  private boolean notesJob(Runnable job) {
    try {
      notesIO.execute(job);
      return true;
    } catch (java.util.concurrent.RejectedExecutionException e) {
      if (liveUI())
        explain(
            "Storage busy",
            "Wait for pending storage work, then try again. No note was deleted.");
      return false;
    }
  }

  private void savedNotesPage() {
    secondaryBack();
    page.addView(text("Saved notes", 24, INK));
    note("Read, export or delete notes already stored on this phone.");
    TextView loading = text("Loading saved notes…", 13, MUTED);
    page.addView(loading);
    final int generation = renderGeneration;
    final LinearLayout destination = page;
    final int requested = sessionOffset;
    boolean accepted = notesJob(
        () -> {
          java.util.List<File> all = sessions.list();
          int total = all.size(),
              start = total == 0 ? 0 : Math.min(requested, ((total - 1) / NOTES_PAGE_SIZE) * NOTES_PAGE_SIZE),
              end = Math.min(start + NOTES_PAGE_SIZE, total);
          java.util.List<File> files = new ArrayList<>();
          java.util.List<String> titles = new ArrayList<>(), dates = new ArrayList<>();
          java.text.DateFormat dateFormat =
              new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
          for (int i = start; i < end; i++) {
            File f = all.get(i);
            files.add(f);
            try {
              JSONObject x = sessions.read(f);
              titles.add(x.optString("title", "Saved note"));
              dates.add(dateFormat.format(new Date(x.optLong("created"))));
            } catch (Exception e) {
              titles.add("Unreadable note");
              dates.add("Record retained. Open to inspect the read error.");
            }
          }
          ui.post(
              () -> {
                if (!liveUI() || renderGeneration != generation || page != destination) return;
                sessionOffset = start;
                destination.removeView(loading);
                if (total == 0) note("No saved notes.");
                else note((start + 1) + "–" + end + " of " + total + " saved notes");
                for (int i = 0; i < files.size(); i++) {
                  final File f = files.get(i);
                  action(titles.get(i), dates.get(i), () -> openSavedNote(f));
                }
                LinearLayout controls = row();
                if (start > 0)
                  weighted(
                      controls,
                      button(
                          "Previous",
                          () -> {
                            sessionOffset = Math.max(0, start - NOTES_PAGE_SIZE);
                            render();
                          }));
                if (end < total)
                  weighted(
                      controls,
                      button(
                          "Next",
                          () -> {
                            sessionOffset = start + NOTES_PAGE_SIZE;
                            render();
                          }));
                page.addView(controls);
              });
        });
    if (!accepted) loading.setText("Storage busy. Reopen Saved notes to retry.");
  }

  private boolean notesPageIsCurrent(int generation) {
    return liveUI() && renderGeneration == generation && active.equals("Saved notes");
  }

  private void openSavedNote(File file) {
    final int generation = renderGeneration, request = ++noteRequestId;
    notesJob(() -> {
      try {
        JSONObject data = sessions.read(file);
        ui.post(() -> {
          if (!notesPageIsCurrent(generation) || request != noteRequestId) return;
          new GuardedDialogBuilder(this)
              .setTitle(data.optString("title", "Saved note"))
              .setMessage(data.optString("notes"))
              .setPositiveButton("Export JSON", (d, w) -> exportNote(file))
              .setNeutralButton("Delete", (d, w) -> confirmDeleteNote(file))
              .setNegativeButton("Close", null)
              .show();
        });
      } catch (Exception e) {
        ui.post(() -> {
          if (notesPageIsCurrent(generation) && request == noteRequestId)
            new GuardedDialogBuilder(this)
                .setTitle("Cannot read note")
                .setMessage("The stored record was retained. You can close this message or delete the unreadable note.")
                .setNeutralButton("Delete", (d, w) -> confirmDeleteNote(file))
                .setNegativeButton("Close", null)
                .show();
        });
      }
    });
  }

  private void exportNote(File file) {
    try {
      pendingExportId = file.getName();
      Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
      intent.setType("application/json");
      intent.addCategory(Intent.CATEGORY_OPENABLE);
      intent.putExtra(Intent.EXTRA_TITLE, "bruce-note.json");
      startActivityForResult(intent, EXPORT_NOTE_REQUEST);
    } catch (Exception e) {
      pendingExportId = null;
      explain("Export unavailable", "No document provider is available.");
    }
  }

  private void confirmDeleteNote(File file) {
    new GuardedDialogBuilder(this)
        .setTitle("Delete note?")
        .setMessage("This deletes only this saved note.")
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Delete", (d, w) -> deleteNote(file))
        .show();
  }

  private void deleteNote(File file) {
    final int generation = renderGeneration;
    notesJob(() -> {
      boolean deleted = false;
      try {
        deleted = sessions.delete(file);
      } catch (IOException ignored) {
      }
      final boolean done = deleted;
      ui.post(() -> {
        if (!notesPageIsCurrent(generation)) return;
        if (done) render();
        else explain("Not deleted", "Could not remove the note. The record has been retained.");
      });
    });
  }

  private void diagnosticsPage() {
    secondaryBack();
    page.addView(text("Diagnostics", 24, INK));
    note("Diagnostics may leave the current firmware screen.");
    action(
        "Connection report",
        "Transport receipts do not confirm firmware execution.",
        this::connectionReport);
    LinearLayout r = row();
    weighted(r, button("Info", () -> diagnostic("info")));
    weighted(r, button("Memory", () -> diagnostic("free")));
    weighted(r, button("Storage", () -> diagnostic("storage free")));
    page.addView(r);
    String output = console.snapshot();
    consoleText = text(output.isEmpty() ? "No device response." : output, 12, INK);
    consoleText.setTypeface(Typeface.MONOSPACE);
    consoleText.setTextIsSelectable(true);
    consoleText.setPadding(dp(10), dp(10), dp(10), dp(10));
    consoleText.setBackground(background(PANEL, 10));
    page.addView(consoleText);
    action(
        "Clear view",
        "Responses are not written to Android logs or saved notes.",
        () -> {
          console.clear();
          render();
        });
  }

  private void diagnostic(String c) {
    if (usb.needsCaptureCleanup()) { explain("Stop mirror first", "Stop capture before running diagnostics."); return; }
    final int session = linkToken();
    if (!requireControlLink()) return;
    new GuardedDialogBuilder(this)
        .setTitle("Run diagnostic?")
        .setMessage("Bruce may return to its main menu. This can interrupt the current screen.")
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Run",
            (d, w) -> {
              if (!requireSession(session) || !linkIdle()) return;
              mirrorRequested = false;
              mirror.clearScreen();
              decoder.reset();
              send(c);
            })
        .show();
  }

  private boolean refreshPending;
  private final Runnable refreshOutput =
      () -> {
        refreshPending = false;
        if (!foreground) return;
        if (consoleText != null) setLabel(consoleText,console.snapshot());
        updateScreenStatus();
      };

  private void scheduleOutput() {
    if (foreground && !refreshPending) {
      refreshPending = true;
      ui.postDelayed(refreshOutput, 250);
    }
  }

  private void appendConsole(String s) {
    console.append(s, SystemClock.elapsedRealtime());
    scheduleOutput();
  }

  private void settings() {
    final List<String> rows = new ArrayList<>(Arrays.asList(
        "Diagnostics", "Power & restart", "Additional controls", "Help & about"));
    if (store.usbName() != null) rows.add("Clear saved device name");
    if (sessions.hasSavedNotes()) rows.add("Saved notes");
    new GuardedDialogBuilder(this)
        .setTitle("Settings")
        .setItems(rows.toArray(new String[0]), (d, i) -> {
          switch (rows.get(i)) {
            case "Diagnostics": openSection("Diagnostics"); break;
            case "Power & restart": power(); break;
            case "Additional controls": inputs(); break;
            case "Help & about": help(); break;
            case "Clear saved device name": forget(); break;
            case "Saved notes": openSection("Saved notes"); break;
            default: break;
          }
        })
        .setNegativeButton("Close", null).show();
  }

  private void gettingStarted() {
    explain("Getting started", "1. Start Bruce and turn off its BLE API. Stop other scripts."
        + "\n2. Connect a USB data cable. Enable OTG if your phone requires it."
        + "\n3. Tap CONNECT and grant Android USB access. Wait for screen preparation."
        + "\n4. Turn the ring to navigate; tap its center to select. PREV/NEXT move between items. EXIT goes back."
        + "\n5. Tap DISCONNECT when finished."
        + "\n\nKeep the app in the foreground. If asked to restart Bruce, restart the physical device before reconnecting.");
  }

  private void help() {
    String[] rows = {"Getting started", "Themes & compatibility", "Scripts from Files",
        "Screen status", "About Bruce Trigger", "Open-source notices", "Privacy & permissions"};
    new GuardedDialogBuilder(this).setTitle("Help & about").setItems(rows, (d, i) -> {
      switch (i) {
        case 0: gettingStarted(); break;
        case 1:
          explain("Themes & compatibility", "Bruce 1.16.1 · LILYGO T-Embed CC1101 / Plus · Android 8.0+ with USB host / OTG."
              + "\n\nBruce Trigger works with custom themes, but the default theme provides smoother performance. For the most responsive experience, use Bruce's default theme."
              + "\n\nAnimated theme GIFs show the first frame. The mirror supports drawing operations, not every firmware pixel. Other compiled games and unsupported JavaScript graphics may not appear."
              + "\n\nUSB only. Firmware updates, remote hardware BOOT/RST, physical long-press emulation and LED telemetry are not provided. Use the physical device for those functions.");
          break;
        case 2: gameMirror(); break;
        case 3: screenStatus(); break;
        case 4:
          explain("Bruce Trigger " + appVersion(), "Independent Android companion by TabGrinder. Inspired by using Bruce firmware on the LILYGO T-Embed CC1101."
              + "\n\nCredit to the Bruce firmware developers and contributors, LILYGO, IncursioHack for Megalodon, and Adafruit Industries / Bodmer's TFT_eSPI for the classic font."
              + "\n\nNot an official BruceDevices or LILYGO application. Full component notices are available under Open-source notices.");
          break;
        case 5: openSourceNotices(); break;
        case 6:
          explain("Privacy & permissions", "USB access requires Android consent. No Internet, Bluetooth or location permission is requested. No analytics or background service is included."
              + "\n\nTheme images stay in memory for the current connection. Configuration and Wi-Fi credentials are not transferred."
              + "\n\nThe saved USB name and any existing notes remain in private app storage. Copy and export are explicit actions. Uninstalling removes this local data. A USB name is not cryptographic device authentication.");
          break;
        default: break;
      }
    }).setNegativeButton("Close", null).show();
  }

  private void openSourceNotices() {
    try (InputStream in = getResources().openRawResource(
        getResources().getIdentifier("glcd_license", "raw", getPackageName()))) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] buffer = new byte[2048]; int count;
      while ((count = in.read(buffer)) != -1 && bytes.size() < 16384) bytes.write(buffer, 0, count);
      explain("Open-source notices", new String(bytes.toByteArray(), StandardCharsets.UTF_8));
    } catch (IOException | RuntimeException e) {
      explain("Open-source notices", "The complete third-party notices and licenses accompany the source distribution.");
    }
  }

  private void gameMirror() {
    explain("Scripts from Files", "Open Files on the mirrored Bruce screen, choose a .js or .bjs file and select JS Script Run. The app prepares the selected file with a temporary graphics bridge. Megalodon is available from Others as an explicitly separate RAM adaptation: PREV/NEXT move, SELECT pauses, EXIT returns. The bridge does not rewrite firmware or the selected source. EXIT sends the script's normal back key. Unsupported drawing operations are reported explicitly.");
  }

  private boolean prepareFilesGame(String path) {
    // Stock loopSD ignores interpreter_state. A serial RAM script cannot run
    // there, even after File Info has closed. Only the main loop starts it.
    if (!filesRootVerified) return false;
    try(InputStream in=getResources().openRawResource(getResources().getIdentifier(pendingMegalodon?"megalodon":"files_bridge","raw",getPackageName()))) {
      ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[2048];int n;
      while((n=in.read(buffer))!=-1){if(bytes.size()+n>16000)throw new IOException("Files bridge too large");bytes.write(buffer,0,n);}
      String template=new String(bytes.toByteArray(),StandardCharsets.US_ASCII);
      if(pendingMegalodon)MegalodonScript.source(template,"0000000000000000",usb.session.mirror.active);
      else FilesGameScript.source(template,"0000000000000000",path);
      gameReturnToNative=usb.session.mirror.active && !menuMode && !nativeFromTheme
          && usb.session.mirror.themes.mappingSnapshot().isEmpty();
      pendingGameTemplate=template;pendingFilesPath=path;menuMode=false;menuSnapshot=null;ui.removeCallbacks(menuTick);
      beginConnectFlow(usb.session.mirror.active?FLOW_STOP_FOR_GAME:FLOW_START_GAME);return true;
    }catch(IOException|IllegalArgumentException|android.content.res.Resources.NotFoundException e){
      explain("Files bridge unavailable","The bundled Files bridge could not be verified. The file was not executed.");return false;
    }
  }

  private boolean presentGameFrames(long limit) {
    List<byte[]> frame;
    while(usb.session.game.screen.deliveredFrames<limit && (frame=usb.session.game.screen.takeFrame())!=null) {
      if(!mirror.applyGameFrame(frame)) {
        String rejection="Game drawing rejected: "+mirror.lastRejection();
        usb.session.block(rejection+". Control stopped; no replay.");closeLinks(rejection);return false;
      }
    }
    return true;
  }

  private void advanceGame() {
    cancelGameRefresh();
    if(!gamePresentation || !foreground || isDestroyed() || !usb.isReady())return;
    if(!presentGameFrames(Long.MAX_VALUE))return;
    if(usb.session.game.ended() && usb.commandIdle()) {
      cancelGameInputs();String outcome=usb.session.game.outcome;gamePresentation=false;gameNativePresentation=false;
      boolean restoreNative=gameReturnToNative;gameReturnToNative=false;
      if(usb.session.game.captureHandover && !usb.session.game.captureStopped) {
        usb.session.block("Native capture handover was not confirmed. No script was started or replayed.");
        closeLinks("Native capture handover failed");
        if(liveUI())explain("Script mirror",outcome+". Restart Bruce before reconnecting.");
        return;
      }
      if(restoreNative) {
        menuMode=false;menuRequired=false;menuSnapshot=null;nativeFromTheme=false;
        deviceScreenControl=false;mirror.clearScreen();decoder.reset();mirrorStream.reset();
        beginConnectFlow(FLOW_RESUME_NATIVE);
      } else startMenuView();
      if(!outcome.equals("Game exited") && !outcome.equals("Game stopped") && liveUI())explain("Script mirror",outcome);
      return;
    }
    updateScreenStatus();
    // Input cooldown can delay menu return; it must not schedule a permanent render poll.
    if (usb.session.game.ended()) scheduleGameRefresh(50);
  }

  private void forget() {
    new GuardedDialogBuilder(this)
        .setTitle("Clear saved device name?")
        .setMessage(
            "Disconnect and remove the USB name saved on this phone. This does not reset the LILYGO or revoke Android USB access. Saved notes remain.")
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Clear name",
            (d, w) -> {
              closeLinks("Saved device name cleared");
              decoder.reset();
              mirror.clearScreen();
              mirrorRequested = false;
              store.forgetUsb();
              render();
            })
        .show();
  }

  private void inputs() {
    String[] options = {"Up", "Down", "Previous page", "Next page"};
    new GuardedDialogBuilder(this).setTitle("Additional controls")
        .setItems(options, (d, i) -> send(new String[] {
            "nav up", "nav down", "nav prevpage", "nav nextpage"}[i]))
        .setNegativeButton("Close", null).show();
  }

  private void power() {
    if (usb.needsCaptureCleanup()) { explain("Stop mirror first", "Stop capture before power actions."); return; }
    final int session = linkToken();
    new GuardedDialogBuilder(this)
        .setTitle("Power & restart")
        .setItems(
            new String[] {"Restart", "Sleep", "Power off"},
            (d, i) -> {
              if (!requireControlLink()) return;
              String cmd = new String[] {"reboot", "sleep", "poweroff"}[i];
              new GuardedDialogBuilder(this)
                  .setTitle("Confirm " + new String[] {"restart", "sleep", "power off"}[i] + "?")
                  .setMessage(
                      "The link may end. Disconnection does not confirm the device state. Physical"
                          + " wake-up or power-on may be required. The action will not be replayed"
                          + " automatically.")
                  .setNegativeButton("Cancel", null)
                  .setPositiveButton(
                      "Send request",
                      (dd, ww) -> {
                        if (requireSession(session)) send(cmd);
                      })
                  .show();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private boolean gameInputMode() {
    return foreground && resumed && connectStep==FLOW_IDLE && gamePresentation
        && usb!=null && usb.isReady() && usb.session.game.active
        && !usb.session.game.nativeUi && !usb.session.game.awaitingExit() && gameInputReady();
  }

  private boolean queueGameInput(String command, boolean repeat) {
    if(!gameInputMode())return false;
    boolean accepted=gameInputs.offer(command,repeat,usb.session.game.inputScope(),SystemClock.elapsedRealtime());
    if(accepted)drainGameInput();
    return accepted;
  }

  private void drainGameInput() {
    ui.removeCallbacks(gameInputTick);
    if(gameInputs.size()==0)return;
    if(!gameInputMode()){gameInputs.clear();return;}
    long now=SystemClock.elapsedRealtime();
    String command=gameInputs.peek(usb.session.game.inputScope(),now);
    if(command!=null && linkIdle() && linkCommand(command)) {
      gameInputs.submitted(now);lastNavigationRequestAt=now;
    }
    if(gameInputs.size()>0)ui.postDelayed(gameInputTick,10);
  }

  private void cancelGameInputs(){ui.removeCallbacks(gameInputTick);gameInputs.clear();}

  private void cancelUserInput() {
    // Activity-level filtering runs before child touch filters. Cancel both
    // widget callbacks and not-yet-written commands when an input is obscured.
    if (root != null) root.cancelPendingInputEvents();
    cancelGameInputs();
    if (usb != null) usb.cancelQueuedInput();
  }

  @Override public void onWindowFocusChanged(boolean focused) {
    if(!focused)cancelUserInput();
    super.onWindowFocusChanged(focused);
  }

  private boolean turnEncoder(int direction) {
    if(gameInputMode())return queueGameInput(direction>0?"nav next":"nav prev",false);
    if (!resumed || !foreground || connectStep != FLOW_IDLE || !linkIdle()) return false;
    if(gamePresentation && (!usb.session.game.active || usb.session.game.awaitingExit()
        || !gameInputReady()))return false;
    if(menuMode && menuSnapshot==null && !deviceScreenControl)return false;
    boolean accepted = linkCommand(direction > 0 ? "nav next" : "nav prev");
    if (accepted) lastNavigationRequestAt = SystemClock.elapsedRealtime();
    return accepted;
  }

  private boolean requireControlLink() {
    if (linkReady()) return true;
    if (connectStep == FLOW_IDLE) connectUsb();
    return false;
  }

  private void controlMode() {
    if(gamePresentation) { gameMirror();return; }
    if(menuMode && screenUsable() && !deviceScreenControl) {
      explain("Theme menu", "Only the main menu uses reported selection and original theme images. SELECT prepares native capture, verifies the same selection, then enters once. Submenus use the real draw stream. Back returns to this view without downloading the theme again. JS files use the Files graphics bridge; native unlogged graphics remain unsupported.");return;
    }
    if (screenUsable() && !deviceScreenControl) {
      explain("Screen control", "Use the ring, EXIT and SELECT with the received screen. Rendering is approximate; a CLI reply alone does not confirm a screen change."); return;
    }
    if (deviceScreenControl) {
      new GuardedDialogBuilder(this)
          .setTitle("Device-screen control")
          .setMessage(
              "SELECT is allowed using the physical device screen, even when the USB mirror is unavailable or incomplete.")
          .setNegativeButton("Close", null)
          .setPositiveButton(
              "Disable",
              (d, w) -> {
                deviceScreenControl = false;
                render();
              })
          .show();
      return;
    }
    if (!requireControlLink()) return;
    final int token = linkToken();
    if (controlDialog != null && controlDialog.isShowing()) return;
    controlDialog =
        new GuardedDialogBuilder(this)
            .setTitle("Use the device screen?")
            .setMessage(
                "A usable mirrored screen is not available. Keep the physical device screen in view while using SELECT."
                    + " Enable controls, then tap SELECT to choose an item. This mode ends when the"
                    + " connection closes.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton(
                "Enable controls",
                (d, w) -> {
                  if (requireSession(token)) {
                    deviceScreenControl = true;
                    render();
                  }
                })
            .show();
  }

  private void send(String c) {
    if (!resumed || !foreground || connectStep != FLOW_IDLE) {
      return;
    }
    if (GameInputQueue.supports(c) && gameInputMode()) { queueGameInput(c,false); return; }
    if (c.startsWith("nav ") && !requireControlLink()) return;
    if (c.startsWith("nav ") && !linkIdle()) {
      updateScreenStatus();
      return;
    }
    if(gamePresentation && (!usb.session.game.active || usb.session.game.awaitingExit()
        || !c.equals("nav esc") && !gameInputReady()))return;
    // Back/Exit must remain available when a game suppresses menu snapshots.
    if(c.startsWith("nav ") && !c.equals("nav esc") && menuMode
        && menuSnapshot==null && !deviceScreenControl)return;
    if ((c.equals("reboot") || c.equals("sleep") || c.equals("poweroff")) && !linkIdle()) {
      explain("Action blocked", "Wait for the command queue to become idle, then confirm again.");
      return;
    }
    if(c.equals("nav select") && menuMode && !deviceScreenControl && screenUsable()) {
      pendingRootSelection=menuSnapshot;menuMode=false;menuSnapshot=null;
      ui.removeCallbacks(menuTick);nativeFromTheme=true;nativeEntrySeen=false;themeSelectSent=false;
      rootCaptureKey=returnImageKey=null;menuProblem="";
      warmRootSelection=usb.session.mirror.active && !usb.session.mirror.busy()
          && mirrorStream.hasGeometry() && !mirrorStream.broken();
      if(warmRootSelection) {
        byte[] panel=mirrorStream.prepareNativeEntry();
        mirror.reset();mirror.apply(panel);
      } else {decoder.reset();mirrorStream.reset();mirror.reset();}
      openingAt=SystemClock.elapsedRealtime();beginConnectFlow(FLOW_PREPARE_SELECTION);return;
    }
    if(c.equals("nav select") && !gamePresentation && !menuMode
        && !usb.session.externalInterpreter && (usb.session.files.menuVisible || usb.session.files.observed
        || usb.session.files.nativeGameMenuVisible)) {
      pendingFilesPath=null;filesRootVerified=false;filesExitCommands=filesExitRetries=0;filesExitUnconfirmed=false;
      usb.session.files.queries++;usb.session.files.mark("Checking SELECT action");
      beginConnectFlow(FLOW_FILES_CHECK);return;
    }
    if (c.equals("nav select") && !deviceScreenControl && !screenUsable()) {
      controlMode();
      return;
    }
    if (!linkCommand(c)) {
      appendConsole("Not sent. Check the device connection.");
      if(inputText!=null){inputText.setText("Not sent · check the connection");inputText.setVisibility(View.VISIBLE);}
    }
    else if (c.startsWith("nav ")) lastNavigationRequestAt = SystemClock.elapsedRealtime();
  }

  private boolean requireSession(int session) {
    if (resumed && foreground && linkMatches(session)) return true;
    if (resumed)
      explain(
          "Connection changed",
          "The original connection is no longer active. Review the connected device and try"
              + " again.");
    return false;
  }

  private void cancelConnect() { connectStep = FLOW_IDLE; pendingRootSelection=null; pendingGameTemplate=null; pendingFilesPath=null; pendingMegalodon=false; filesRootVerified=false; ui.removeCallbacks(connectTick); }

  private boolean filesReadOnlyFlow() { return connectStep>=FLOW_FILES_CHECK && connectStep<=FLOW_FILES_ROOT_WAIT; }

  private void stopFilesPreparation(String reason) {
    usb.session.files.abort(reason);cancelConnect();updateScreenStatus();
    if(liveUI())explain("Script not started",reason+". USB is still connected. Use EXIT to return, then select the file again.");
  }

  private boolean leaveFilesLevel() {
    // Stock File Info polls at 100 ms, but input flags can expire after 75 ms.
    // Only this read-only traversal may repeat ESC, after a fresh menu query.
    // Game inputs, SELECT and executable uploads are never replayed here.
    boolean retry=filesExitUnconfirmed;
    if(retry && filesExitRetries>=2) {stopFilesPreparation("Bruce did not leave this Files screen after three bounded EXIT attempts");return false;}
    if(!retry && filesExitCommands>=filesExitLimit) {stopFilesPreparation("Main menu was not reached within the selected path depth");return false;}
    long revision=usb.session.files.clears;
    if(!usb.command("nav esc"))return false;
    if(retry)filesExitRetries++;else{filesExitCommands++;filesExitRetries=0;}
    filesRevision=revision;filesEscapeAt=SystemClock.elapsedRealtime();connectStep=FLOW_FILE_RETURN;
    usb.session.files.mark("Leaving Files level "+filesExitCommands+"; EXIT attempt "+(filesExitRetries+1)+"; waiting for redraw");
    return true;
  }

  private void connectUsb() {
    if (!resumed || !foreground || connectStep != FLOW_IDLE || usb.isClosing() || usb.captureDraining()) return;
    if (usb.session.mirror.active || menuMode || usb.session.game.engaged()) { closeLinks("Disconnected by user"); return; }
    if (usb.session.stage == UsbSerialSession.Stage.BLOCKED) {
      closeLinks("Reconnect requested after blocked session");
      explain("Restart Bruce first", "The previous capture state is unknown. Restart Bruce, then tap CONNECT.");
      return;
    }
    if (usb.isReady()) { beginConnectFlow(FLOW_VERIFY); return; }
    if (usb.isOpen()) { beginConnectFlow(FLOW_CONNECT); return; }
    if (!usb.supported()) { explain("USB host unavailable", "This phone does not report USB host/OTG support."); return; }
    List<UsbDevice> candidates = new ArrayList<>();
    for(UsbDevice d:usb.devices()) if(UsbProfile.find(d)!=null) candidates.add(d);
    if(candidates.isEmpty()) {
      explain("Connect your Bruce device", "Use a USB-C data cable, power on Bruce and enable phone OTG if needed. This build supports native Espressif CDC (303A:1001).");return;
    }
    if(candidates.size()==1) { openAndMirror(candidates.get(0));return; }
    String[] names=new String[candidates.size()];
    for(int i=0;i<names.length;i++) names[i]="Bruce USB · "+UsbProfile.id(candidates.get(i))+" · "+(i+1);
    new GuardedDialogBuilder(this).setTitle("Choose USB device")
        .setItems(names,(d,i)->openAndMirror(candidates.get(i))).setNegativeButton("Cancel",null).show();
  }

  private void openAndMirror(UsbDevice device) {
    menuRequired=false;
    if(!resumed || !foreground || connectStep != FLOW_IDLE || usb.needsCaptureCleanup())return;
    mirror.clearImages();mirror.reset();mirrorStream.reset();decoder.reset();
    screenPackets=geometryPackets=malformed=0;lastNotification=lastNavigationRequestAt=0;
    usb.connect(device);
    if(usb.hasPendingConnection()) beginConnectFlow(FLOW_CONNECT);
  }

  private void beginConnectFlow(int step) {
    connectStep=step; connectToken=usb.sessionToken();
    connectDeadline=SystemClock.elapsedRealtime()+(step>=FLOW_PREPARE_SELECTION?15000:120000);
    ui.removeCallbacks(connectTick); ui.post(connectTick);
    updateScreenStatus();
  }

  private void advanceConnect() {
    ui.removeCallbacks(connectTick);
    if(connectStep == FLOW_IDLE)return;
    if(!foreground || isDestroyed() || usb.sessionToken()!=connectToken) { cancelConnect();updateScreenStatus();return; }
    if(SystemClock.elapsedRealtime()>=connectDeadline && usb.session.stage==UsbSerialSession.Stage.OPEN
        && filesReadOnlyFlow()) {
      // Nothing executable has been sent in this read-only phase. Keep the
      // verified link and native mirror available so EXIT can leave File Info.
      String reason=connectStep>=FLOW_FILE_RETURN?"Return to the main menu was not confirmed":"Selected file metadata was incomplete";
      stopFilesPreparation(reason);
      return;
    }
    if(usb.session.stage==UsbSerialSession.Stage.BLOCKED || SystemClock.elapsedRealtime()>=connectDeadline) {
      String reason=usb.session.stage==UsbSerialSession.Stage.BLOCKED?usb.inputState()
          :"Connection setup timed out. Restart Bruce, then reconnect. No replay.";
      cancelConnect(); usb.closeSafely(reason); updateScreenStatus();
      if(liveUI())explain("Connection stopped",reason);return;
    }
    if((connectStep == FLOW_CHECK_ROOT || connectStep == FLOW_WAIT_ROOT) && usb.session.externalInterpreter) {
      menuRequired=false;returnImageKey=null;cancelConnect();updateScreenStatus();return;
    }
    // Android's permission dialog may pause this Activity. Resume the same explicit
    // request after it closes; never send commands while the app is in the background.
    if(resumed) {
      if(connectStep == FLOW_CONNECT && usb.isOpen() && usb.session.idle(SystemClock.elapsedRealtime())) {
        if(usb.command("info"))connectStep = FLOW_VERIFY;
      } else if(connectStep == FLOW_VERIFY && usb.commandIdle()) {
        if(usb.interpreterRecoveryRequired() || usb.session.externalInterpreter) {
          cancelConnect();
          new GuardedDialogBuilder(this).setTitle("Previous script not confirmed stopped")
              .setMessage("Restart Bruce before reconnecting. The app will not upload another script over a potentially running interpreter. If you already restarted Bruce, confirm below.")
              .setNegativeButton("Cancel",null)
              .setPositiveButton("Bruce restarted",(d,w)->{
                usb.disconnect("User confirmed Bruce restart");usb.confirmInterpreterRestart();connectUsb();
              }).show();
          updateScreenStatus();return;
        }
        if(!UsbMirrorBridge.supports(usb.version(),usb.verifiedName())) {
          cancelConnect();explain("Mirror profile unavailable","This capture path supports Bruce 1.16.1 on LILYGO T-Embed CC1101 / Plus.");updateScreenStatus();return;
        }
        if(usb.syncTheme())connectStep = FLOW_THEME;
      } else if(connectStep == FLOW_THEME && usb.commandIdle() && usb.session.mirror.themes.finished()) {
        mirror.loadImages(usb.session.mirror.themes.snapshot());
        if(menuRequired || !usb.session.mirror.themes.mappingSnapshot().isEmpty()) { startMenuView();return; }
        decoder.reset();mirrorStream.reset();mirror.reset();
        screenPackets=geometryPackets=malformed=0;lastNavigationRequestAt=0;
        if(usb.mirror(true)) { mirrorRequested=true;deviceScreenControl=false;connectStep = FLOW_NATIVE; }
      } else if(connectStep == FLOW_NATIVE && usb.session.mirror.active && !usb.session.mirror.busy()) {
        if(menuRequired) {beginConnectFlow(FLOW_CHECK_ROOT);return;}
        cancelConnect();updateScreenStatus();return;
      } else if(connectStep == FLOW_RESUME_NATIVE && usb.commandIdle() && usb.session.game.ended()
          && !usb.session.externalInterpreter && !usb.session.mirror.active) {
        if(usb.mirror(true)) {
          mirrorRequested=true;lastNavigationRequestAt=0;
          connectStep=FLOW_NATIVE;
        }
      } else if(connectStep == FLOW_STOP_FOR_GAME && usb.commandIdle()) {
        usb.session.files.mark("Main menu verified; preparing the selected script");connectStep = FLOW_START_GAME;
      } else if(connectStep == FLOW_START_GAME && usb.commandIdle() && !usb.session.mirror.busy()) {
        if(pendingGameTemplate!=null && (pendingMegalodon?usb.launchMegalodon(pendingGameTemplate):pendingFilesPath!=null && usb.launchFilesGame(pendingGameTemplate,pendingFilesPath))) {
          cancelGameInputs();gameInputs.reset();gamePresentation=true;gameNativePresentation=false;mirrorRequested=false;deviceScreenControl=false;
          decoder.reset();mirror.reset();mirrorStream.clearScreen();
          cancelConnect();scheduleGameRefresh(0);updateScreenStatus();return;
        }
      } else if(connectStep == FLOW_FILES_CHECK && usb.menuQueryReady()) {
        selectionQuerySeen=usb.session.menuReplies;if(usb.queryMenu())connectStep=FLOW_FILES_WAIT;
      } else if(connectStep == FLOW_FILES_WAIT && usb.commandIdle() && usb.session.menuReplies!=selectionQuerySeen) {
        RemoteMenu m=RemoteMenu.parse(usb.session.menuJson);
        if(m!=null && m.isMegalodon() && usb.session.files.nativeGameMenuVisible) {
          pendingMegalodon=true;pendingFilesPath="Megalodon (RAM)";filesExitLimit=2;
          filesExitCommands=filesExitRetries=0;
          leaveFilesLevel();
        } else if(m!=null && m.isFilesRun()) {
          if(!usb.session.files.menuVisible){
            usb.session.files.mark("Files action not visible in current screen");
            cancelConnect();explain("Files selection unavailable","Open the JS file again in Files. Its Run action must be visible before launch.");return;
          }
          if(usb.readFilesInfo(usb.session.menuJson)){connectStep=FLOW_FILE_INFO;filesRevision=SystemClock.elapsedRealtime();}
          // A navigation cooldown can still be active after this read-only
          // reply. Keep the scheduled tick even when USB is now silent.
        } else if((usb.session.files.menuVisible || usb.session.files.nativeGameMenuVisible) && m==null){
          usb.session.files.mark("Files menu response unavailable");cancelConnect();
          explain("Selection unavailable","The current menu could not be verified. Nothing was started.");return;
        } else {
          usb.session.files.clear();cancelConnect();
          if(linkCommand("nav select"))lastNavigationRequestAt=SystemClock.elapsedRealtime();
          updateScreenStatus();return;
        }
      } else if(connectStep == FLOW_FILE_INFO && usb.commandIdle() && usb.session.files.infoComplete
          && SystemClock.elapsedRealtime()-filesRevision>=200
          && SystemClock.elapsedRealtime()-lastNativeDrawAt>=150) {
        pendingFilesPath=usb.session.files.infoPath;
        if(pendingFilesPath==null){cancelConnect();explain("File path unsupported","The selected file path could not be read safely. No script was started.");return;}
        filesExitLimit=2;
        for(int i=0;i<pendingFilesPath.length();i++)if(pendingFilesPath.charAt(i)=='/')filesExitLimit++;
        leaveFilesLevel();
      } else if(connectStep == FLOW_FILE_RETURN && usb.commandIdle() && usb.session.files.clears>filesRevision) {
        filesExitUnconfirmed=false;filesRedrawAt=SystemClock.elapsedRealtime();connectStep=FLOW_FILES_ROOT_QUERY;
      } else if(connectStep == FLOW_FILE_RETURN && usb.commandIdle()
          && SystemClock.elapsedRealtime()-filesEscapeAt>=600) {
        filesExitUnconfirmed=true;filesRedrawAt=SystemClock.elapsedRealtime();connectStep=FLOW_FILES_ROOT_QUERY;
        usb.session.files.mark("EXIT redraw missing; checking current menu before any retry");
      } else if(connectStep == FLOW_FILES_ROOT_QUERY && usb.menuQueryReady()
          && SystemClock.elapsedRealtime()-filesRedrawAt>=100) {
        selectionQuerySeen=usb.session.menuReplies;
        if(usb.queryMenu())connectStep=FLOW_FILES_ROOT_WAIT;
      } else if(connectStep == FLOW_FILES_ROOT_WAIT && usb.commandIdle() && usb.session.menuReplies!=selectionQuerySeen) {
        RemoteMenu current=RemoteMenu.parse(usb.session.menuJson);
        if(RemoteMenu.parseRoot(usb.session.menuJson)!=null) {
          filesRootVerified=true;
          usb.session.files.mark("Main menu verified; preparing selected JS");
          if(!prepareFilesGame(pendingFilesPath)) {usb.session.files.mark("Bundled Files bridge could not be prepared");cancelConnect();}
          return;
        }
        if(current==null){stopFilesPreparation("Menu response could not be verified");return;}
        leaveFilesLevel();
      } else if(connectStep == FLOW_CHECK_ROOT && usb.menuQueryReady()) {
        if(usb.session.externalInterpreter){cancelConnect();return;}
        selectionQuerySeen=usb.session.menuReplies;
        if(usb.queryMenu())connectStep = FLOW_WAIT_ROOT;
      } else if(connectStep == FLOW_WAIT_ROOT && usb.session.menuReplies!=selectionQuerySeen) {
        RemoteMenu root=RemoteMenu.parseRoot(usb.session.menuJson);
        String expected=root==null?null:root.imageKey(menuMappings);
        boolean knownImage=returnImageKey!=null && menuMappings.containsValue(returnImageKey);
        if(root!=null && (!knownImage || returnImageKey.equals(expected))) {
          if(!menuMappings.isEmpty() && (returnImageKey==null || menuMappings.containsValue(returnImageKey))) {
            startMenuView();return;
          }
          beginConnectFlow(FLOW_STOP_CAPTURE);return;
        }
        // An image inside a tool is not a return to the root. Starting our
        // cleanup interpreter here would interrupt the user's tool or game.
        menuRequired=false;returnImageKey=null;cancelConnect();updateScreenStatus();return;
      } else if(connectStep == FLOW_STOP_CAPTURE && usb.commandIdle()) {
        if(usb.session.externalInterpreter) {cancelConnect();return;}
        if(usb.mirror(false))connectStep = FLOW_RETURN_ROOT;
      } else if(connectStep == FLOW_RETURN_ROOT && usb.commandIdle() && !usb.session.mirror.active) {
        // Returning from Config does not change the theme. Re-read only when an
        // actual image path is absent from the current theme map.
        if(menuMappings.isEmpty() || returnImageKey!=null && !menuMappings.containsValue(returnImageKey)) {
          if(usb.syncTheme())connectStep = FLOW_THEME;
        } else {startMenuView();return;}
      } else if(connectStep == FLOW_PREPARE_SELECTION && usb.commandIdle()) {
        if(warmRootSelection && usb.session.mirror.active && usb.menuQueryReady()) {
          selectionQuerySeen=usb.session.menuReplies;
          if(usb.queryMenu())connectStep=FLOW_VERIFY_SELECTION;
        } else if(!usb.session.mirror.active && usb.mirror(true)) {
          mirrorRequested=true;deviceScreenControl=false;connectStep = FLOW_WAIT_CAPTURE;
        }
      } else if(connectStep == FLOW_WAIT_CAPTURE && usb.session.mirror.active && !usb.session.mirror.busy()) {
        long now=SystemClock.elapsedRealtime();
        boolean nativeScreenReady=mirrorStream.hasGeometry() && mirrorStream.hasClear()
            && !mirrorStream.broken() && mirror.rejected()==0 && mirror.lastFrame()>0;
        if(nativeScreenReady && now-lastNativeDrawAt>=40 && usb.menuQueryReady()) {
          selectionQuerySeen=usb.session.menuReplies;
          if(usb.queryMenu())connectStep = FLOW_VERIFY_SELECTION;
        }
      } else if(connectStep == FLOW_VERIFY_SELECTION && usb.commandIdle() && usb.session.menuReplies!=selectionQuerySeen) {
        RemoteMenu check=RemoteMenu.parseRoot(usb.session.menuJson);
        boolean unchanged=check!=null && pendingRootSelection!=null
            && check.active==pendingRootSelection.active && check.identity().equals(pendingRootSelection.identity())
            && (rootCaptureKey==null || rootCaptureKey.equals(check.imageKey(menuMappings)));
        if(!unchanged) {
          pendingRootSelection=null;menuProblem="Selection changed during screen setup. Select again.";
          if(warmRootSelection) {startMenuView();return;}
          beginConnectFlow(FLOW_STOP_CAPTURE);return;
        }
        if(usb.selectRoot(check.active,usb.session.menuJson)) {
          if(warmRootSelection)warmRootEntries++;
          pendingRootSelection=null;themeSelectSent=true;nativeEntrySeen=false;
          lastNavigationRequestAt=SystemClock.elapsedRealtime();connectStep = FLOW_SELECTION_SENT;
        }
      } else if(connectStep == FLOW_SELECTION_SENT && usb.commandIdle()) {
        openingMs=SystemClock.elapsedRealtime()-openingAt;cancelConnect();updateScreenStatus();return;
      }
    }
    updateScreenStatus(); ui.postDelayed(connectTick,20);
  }

  private boolean gameInputReady() {
    return usb.session.game.nativeUi ? gameNativePresentation && mirrorStream.hasGeometry() && mirrorStream.hasClear() && !mirrorStream.broken() && mirrorStream.lastDraw()>=usb.session.game.nativeUiAt
        : usb.session.game.screen.usable(SystemClock.elapsedRealtime());
  }

  private boolean screenUsable() {
    if(gamePresentation)return connectStep == FLOW_IDLE && usb.isReady() && usb.session.game.active
        && !usb.session.game.awaitingExit() && gameInputReady()
        && mirror.lastFrame()>0 && mirror.rejected()==0;
    if(menuMode)return connectStep == FLOW_IDLE && menuSnapshot!=null && usb.isReady()
        && !usb.session.externalInterpreter && SystemClock.elapsedRealtime()-menuReceivedAt<2500
        && (!usb.session.mirror.active || mirrorStream.hasGeometry() && !mirrorStream.broken());
    return connectStep == FLOW_IDLE && mirrorRequested && usb.isReady() && usb.session.mirror.active && !usb.session.externalInterpreter
        && mirrorStream.hasGeometry() && mirrorStream.hasClear() && !mirrorStream.broken() && !mirrorStream.unsupportedSprites()
        && mirror.rejected() == 0 && mirrorStream.lastDraw() >= lastNavigationRequestAt;
  }

  private void startMenuView() {
    cancelConnect();menuMode=true;menuRequired=false;mirrorRequested=usb.session.mirror.active;deviceScreenControl=false;
    menuMappings=usb.session.mirror.themes.mappingSnapshot();
    menuForeground=usb.session.mirror.themes.primary;menuBackground=usb.session.mirror.themes.background;
    menuSnapshot=null;nativeFromTheme=false;nativeEntrySeen=false;themeSelectSent=false;rootCaptureKey=null;menuReplySeen=usb.session.menuReplies;menuDue=0;
    mirror.clearScreen();
    // Preserve the live decoder, including a partially received native record.
    // Only theme changes or connection cleanup stop the owned capture.
    if(!usb.session.mirror.active){decoder.reset();mirrorStream.clearScreen();}
    ui.removeCallbacks(menuTick);ui.post(menuTick);updateScreenStatus();
  }

  private void advanceMenu() {
    ui.removeCallbacks(menuTick);
    if(!menuMode || !foreground || isDestroyed() || !usb.isReady())return;
    long now=SystemClock.elapsedRealtime();
    if(usb.session.externalInterpreter) {
      menuSnapshot=null;menuImageShown=false;mirror.clearScreen();
      updateScreenStatus();ui.postDelayed(menuTick,1200);return;
    }
    if(usb.session.menuReplies!=menuReplySeen) {
      menuReplySeen=usb.session.menuReplies;
      RemoteMenu next=RemoteMenu.parseRoot(usb.session.menuJson);
      menuSnapshot=next;menuReceivedAt=now;
      if(menuNavigationAt>0){menuLatencyMs=now-menuNavigationAt;menuNavigationAt=0;}
      if(next!=null)menuImageShown=mirror.showMenu(next,menuMappings,menuForeground,menuBackground);
      else {menuImageShown=false;mirror.clearScreen();menuProblem="Device is outside the main menu. Use Back / Exit to return.";}
      menuDue=now+MenuRefreshPolicy.delay(now,lastNavigationRequestAt);
    }
    if(resumed && !usb.session.externalInterpreter && usb.menuQueryReady() && now>=menuDue) {
      if(usb.queryMenu())menuDue=now+7000;
    }
    updateScreenStatus();ui.postDelayed(menuTick,MenuRefreshPolicy.nextWake(now,menuDue));
  }

  private String inputStatus() {
    if (!gamePresentation && mirrorStream.unsupportedSprites())
      return "Megalodon sprites are not sent by stock Bruce";
    if (usb.isClosing()) return "Stopping device session…";
    if(gamePresentation && !usb.isReady())return usb.inputState();
    if(gamePresentation && usb.session.game.awaitingExit())return "Leaving game · waiting for Bruce…";
    if(gamePresentation)return usb.session.game.active?gameInputReady()?"JS running · use the screen controls · EXIT to return":"Waiting for game frames…":usb.session.game.outcome;
    if(connectStep == FLOW_FILE_INFO)return "Reading selected file…";
    if(connectStep >= FLOW_FILE_RETURN && connectStep <= FLOW_FILES_ROOT_WAIT)return "Leaving Files to start the game…";
    if(connectStep == FLOW_RESUME_NATIVE)return "Restoring live screen…";
    if(connectStep == FLOW_STOP_FOR_GAME || connectStep == FLOW_START_GAME)return "Preparing selected JS mirror…";
    if(connectStep == FLOW_CHECK_ROOT||connectStep == FLOW_WAIT_ROOT)return "Checking image menu…";
    if(connectStep>=FLOW_PREPARE_SELECTION)return "Preparing live screen before SELECT…";
    if(connectStep == FLOW_STOP_CAPTURE||connectStep == FLOW_RETURN_ROOT)return "Returning to theme menu…";
    if(menuMode) return usb.session.externalInterpreter?"Script running · no live menu view"
        :menuSnapshot==null?menuProblem.isEmpty()?"Reading main menu…":menuProblem:"Selected: "+menuSnapshot.selected();
    if (usb.captureDraining()) return "Finishing stop…";
    if (!usb.isReady() || usb.session.mirror.busy()) return usb.inputState();
    if (!usb.commandIdle()) return "Waiting for Bruce…";
    if (deviceScreenControl) return "Ready · use the device screen";
    if (usb.session.externalInterpreter) return "Script running · screen incomplete";
    if (mirrorRequested && usb.session.mirror.active) {
      if (screenUsable()) return "Ready for input";
      if (mirror.rejected() > 0 || mirrorStream.broken()) return "Screen incomplete · check screen status";
      return mirrorStream.hasClear() ? "Waiting for updated screen" : "Waiting for screen data · try Next";
    }
    return "Tap CONNECT";
  }

  private static void setLabel(TextView view, String value) {
    if (!value.contentEquals(view.getText())) view.setText(value);
  }

  private void updateScreenStatus() {
    if (root != null) root.setKeepScreenOn(foreground && usb.isReady() && (gamePresentation || menuMode || mirrorRequested && usb.session.mirror.active));
    if (inputText != null) {
      setLabel(inputText, inputStatus());
      boolean message = usb.session.externalInterpreter || usb.session.game.awaitingExit()
          || mirrorStream.unsupportedSprites() || !menuProblem.isEmpty()
          || linkReady() && (mirror.rejected() > 0 || mirrorStream.broken());
      inputText.setVisibility(message ? View.VISIBLE : View.GONE);
    }
    boolean controlsAvailable=linkReady() && connectStep == FLOW_IDLE && !usb.session.game.awaitingExit();
    if (remoteKeys != null) for (int i = 0; i < remoteKeys.getChildCount(); i++)
      remoteKeys.getChildAt(i).setEnabled(controlsAvailable);
    if (usbStatusText != null) {
      setLabel(usbStatusText, usb.isOpen() ? "● USB CONNECTED" : "USB DISCONNECTED");
      int color = usb.isOpen() ? FusionTheme.LIME : MUTED;
      if (usbStatusText.getCurrentTextColor() != color) usbStatusText.setTextColor(color);
    }
    if (deviceSubtitle != null) {
      String name = linkReady() ? usb.verifiedName() : store.usbName();
      setLabel(deviceSubtitle, name == null || name.isEmpty() ? "USB device" : name.replace("Lilygo ", "").replace("LILYGO ", ""));
    }
    if (encoderControl != null) encoderControl.setAvailable(controlsAvailable);
    if (connectAction != null) {
      boolean busy=connectStep != FLOW_IDLE || usb.isClosing() || usb.captureDraining() || usb.session.mirror.busy() || usb.session.game.uploading();
      setLabel(connectAction, usb.isClosing()||usb.captureDraining()?"Closing…":connectStep == FLOW_CONNECT?"Connecting…"
          :filesReadOnlyFlow()?"Preparing game…"
          :connectStep == FLOW_RESUME_NATIVE?"Restoring screen…"
          :connectStep == FLOW_STOP_FOR_GAME||connectStep == FLOW_START_GAME||usb.session.game.uploading()?"Starting game…":connectStep == FLOW_CHECK_ROOT||connectStep == FLOW_WAIT_ROOT?"Checking menu…":connectStep>=FLOW_PREPARE_SELECTION?"Opening menu…":connectStep == FLOW_STOP_CAPTURE||connectStep == FLOW_RETURN_ROOT?"Returning…"
          :connectStep == FLOW_VERIFY?"Verifying…":connectStep == FLOW_THEME?"Syncing theme…":connectStep == FLOW_NATIVE?"Starting mirror…"
          :usb.session.mirror.active||menuMode||usb.session.game.engaged()?"DISCONNECT":"CONNECT");
      connectAction.setEnabled(!busy);
    }
    if (screenText != null) {
      String description="Screen status. " + presentationStatus();
      if(!android.text.TextUtils.equals(screenText.getContentDescription(),description))
        screenText.setContentDescription(description);
    }
  }

  private String presentationStatus() {
    return !gamePresentation && mirrorStream.unsupportedSprites()
        ? "Bruce reported Not Supported for Megalodon: its compiled game does not send sprite pixels over USB. The Files bridge handles JavaScript drawing calls; it cannot capture this compiled game."
        :gamePresentation?usb.session.game.awaitingExit()?"Game exit requested · waiting for interpreter completion":"Selected Files script · USB graphics bridge"
        :connectStep == FLOW_RESUME_NATIVE?"Restoring the native screen after the confirmed game exit…"
        :connectStep == FLOW_STOP_FOR_GAME||connectStep == FLOW_START_GAME?"Preparing a new mirrored game…":connectStep == FLOW_CHECK_ROOT||connectStep == FLOW_WAIT_ROOT?"Checking whether Bruce returned to the main menu…":connectStep>=FLOW_PREPARE_SELECTION?"Preparing native screen capture…"
        :connectStep == FLOW_STOP_CAPTURE||connectStep == FLOW_RETURN_ROOT?"Stopping capture for the image menu…"
        :connectStep == FLOW_THEME?"Reading current theme images from Bruce…"
        :menuMode?usb.session.externalInterpreter?"Game/script screen unavailable":"Main menu · "+(menuImageShown?"original theme image":"reported root selection")
        :usb.session.externalInterpreter?"This script was started outside the app Files launch flow. Exit it, then choose Files → your JS file → JS Script Run using the app controls."
        :usb.session.mirror.busy()?"USB mirror · "+usb.session.mirror.state()
        :!mirrorRequested || !usb.session.mirror.active?"USB mirror · stopped"
        :mirror.rejected()>0 || mirrorStream.broken()?"Screen incomplete · open Screen status"
        :!mirrorStream.hasClear()?"Waiting for screen data · try NEXT"
        :mirror.gifPreviews()>0?"USB screen · GIF first-frame preview":"USB screen · approximate native rendering";
  }

  @Override
  public void state(String s, String d) {
    if (isDestroyed()) return;
    status = s;
    detail = d;
    if ("Capture stop acknowledged".equals(d)) {
      mirrorRequested = false; mirrorStream.clearScreen(); mirror.clearScreen();
    }
    if (s.equals("Disconnected") || s.equals("Connection error") || s.equals("USB control stopped")) {
      cancelGameInputs();ui.removeCallbacks(menuTick);cancelGameRefresh();gamePresentation=false;gameNativePresentation=false;gameReturnToNative=false;menuMode=false;menuSnapshot=null;menuMappings=Collections.emptyMap();nativeFromTheme=false;themeSelectSent=false;returnImageKey=null;menuProblem="";
      cancelConnect(); mirror.clearImages();
      console.clear();
      if (consoleText != null) consoleText.setText("Link closed. Previous output cleared.");
      decoder.reset();
      mirror.clearScreen();
      mirrorRequested = false;
      mirrorStream.clearScreen();
      deviceScreenControl = false;
      if (controlDialog != null) controlDialog.dismiss();
      if(s.equals("USB control stopped"))usb.closeSafely(d);
    }
    if (connectionText != null) connectionText.setText(status);
    updateScreenStatus();
    if (s.equals("Connection error") && resumed && foreground)
      new GuardedDialogBuilder(this)
          .setTitle("Connection interrupted")
          .setMessage(d)
          .setNegativeButton("Close", null)
          .setNeutralButton("Report", (dialog, which) -> connectionReport())
          .show();
  }

  @Override
  public void ready(String address, String name) {
    if(isDestroyed())return;
    store.rememberUsb(name); render();
  }

  @Override
  public void data(byte[] bytes) {
    if (!foreground) return;
    long now = SystemClock.elapsedRealtime();
    if (now - lastNotification >= FrameDecoder.PARTIAL_FRAME_TIMEOUT_MS) {
      if (decoder.isPartial()) {
        mirrorStream.invalidate();
        malformed++;
      }
      decoder.reset();
    }
    lastNotification = now;
    decoder.accept(bytes);
    scheduleOutput();
    // Respond to actual USB completion; periodic ticks only cover quiet links.
    if(connectStep != FLOW_IDLE){ui.removeCallbacks(connectTick);ui.post(connectTick);}
    else if(gamePresentation) {
      // Consume complete transactions in this bounded read slice, before another
      // queued USB callback. Canvas presentation still uses the next vsync.
      if(!presentGameFrames(Long.MAX_VALUE))return;
      scheduleGameRefresh(0);
      drainGameInput();
    }
    else if(menuMode){ui.removeCallbacks(menuTick);ui.post(menuTick);}
  }

  @Override
  public void loss() {
    mirrorStream.invalidate();
    decoder.reset();
    malformed++;
    updateScreenStatus();
  }

  @Override
  public void sent(String command) {
    if (command.startsWith("nav ") || command.startsWith("option ")) lastNavigationRequestAt = SystemClock.elapsedRealtime();
    scheduleOutput();
    if (foreground) updateScreenStatus();
  }

  private String appVersion() {
    try {
      return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
    } catch (PackageManager.NameNotFoundException e) {
      return "Unknown build";
    }
  }

  private void connectionReport() {
    String report =
        "Bruce trigger " + appVersion() + "\n"
            + usb.diagnostics()
            + "\nPhone game inputs: " + gameInputs.summary()
            + "\nPresentation: " + (gamePresentation?"Files JS graphics bridge":menuMode?usb.session.mirror.active?"Root theme view; native capture retained":"Root theme view; native capture starts on SELECT":usb.isReady()?"Native draw stream":"Disconnected")
            + "\nRoot selections without capture restart: " + warmRootEntries
            + "\nPresentation note: " + (menuProblem.isEmpty()?"None":menuProblem)
            + "\nLast root navigation to metadata: " + menuLatencyMs + " ms"
            + "\nLast capture preparation and root selection: " + openingMs + " ms"
            + "\nFiles launch: " + usb.session.files.summary()
            + "\nMost recent Files attempt: " + usb.session.files.historySummary()
            + "\nMenu snapshot responses: " + usb.session.menuReplies
            + "\nMenu snapshot selected: " + (menuSnapshot==null?"Unavailable":menuSnapshot.selected())
            + "\nNative screen packets accepted: " + mirrorStream.packets()
            + "\nNative records rejected: " + mirrorStream.rejected()
            + "\nNative opcodes observed: " + mirrorStream.opcodeSummary()
            + "\nNative unsupported sprite marker: " + mirrorStream.unsupportedSprites()
            + "\nAndroid screen updates presented: " + mirror.presentedUpdates()
            + "\nComplete game frames composed: " + mirror.gameFramesComposed()
            + "\nLatest game frame presented: " + mirror.presentedGameFrame()
            + "\nLast Android content change age: " + mirror.presentationAge(SystemClock.elapsedRealtime()) + " ms"
            + "\nPresented updates are View draws, not firmware framebuffer frames."
            + "\nScreen capture: " + usb.session.mirror.state()
            + "\nNative display calls use the stock JS interpreter, never the serial task."
            + "\nDecoded screen packets: "
            + screenPackets
            + "\nScreen geometry packets: "
            + geometryPackets
            + "\nScreen packets rejected: "
            + (malformed + mirror.totalRejected())
            + "\nImage records received: " + mirror.imagePackets()
            + "\nTheme images rendered: " + mirror.renderedImages()
            + "\nGIF first-frame previews: " + mirror.gifPreviews()
            + "\nLast render rejection: " + mirror.lastRejection()
            + "\nScreen counters describe the last requested capture and survive disconnect."
            + "\nText renderer: stock GLCD 6x8; font/datum changes are not transmitted.";
    AlertDialog dialog =
        new GuardedDialogBuilder(this)
            .setTitle("Connection report")
            .setMessage(report)
            .setNegativeButton("Close", null)
            .setPositiveButton(
                "Copy",
                (d, w) -> {
                  ClipboardManager clipboard =
                      (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                  if (clipboard != null)
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("Bruce connection report", report));
                })
            .show();
    TextView message = dialog.findViewById(android.R.id.message);
    if (message != null) message.setTextIsSelectable(true);
  }

  private void explain(String title, String body) {
    new GuardedDialogBuilder(this)
        .setTitle(title)
        .setMessage(body)
        .setPositiveButton("OK", null)
        .show();
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent event) {
    if (GuardedDialogBuilder.obscured(event)) {
      cancelUserInput();
      return false;
    }
    return super.dispatchTouchEvent(event);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request != EXPORT_NOTE_REQUEST) return;
    final String id = pendingExportId;
    pendingExportId = null;
    if (result != RESULT_OK) return;
    if (data == null
        || data.getData() == null
        || id == null
        || !id.matches("[a-fA-F0-9-]{36}\\.json")) {
      explain("Export interrupted", "The note remains saved. Select Export again.");
      return;
    }
    final Uri uri = data.getData();
    if (!"content".equals(uri.getScheme())) {
      explain(
          "Export rejected", "A system document provider is required. Your note remains saved.");
      return;
    }
    final ContentResolver resolver = getApplicationContext().getContentResolver();
    Toast.makeText(this, "Exporting note…", Toast.LENGTH_SHORT).show();
    final File record = new File(new File(getFilesDir(), "sessions"), id);
    if (!submitExport(
        resolver, sessions, record, uri, new java.lang.ref.WeakReference<MainActivity>(this)))
      explain(
          "Export busy",
          "A document provider is still handling the previous export. No additional work was"
              + " queued. Your local note remains saved.");
  }

  private static boolean submitExport(
      ContentResolver resolver,
      SessionStore sessions,
      File record,
      Uri uri,
      java.lang.ref.WeakReference<MainActivity> target) {
    try {
      exportIO.execute(
          () -> {
            boolean success = false;
            try {
              byte[] bytes = sessions.read(record).toString(2).getBytes(StandardCharsets.UTF_8);
              try (OutputStream out = resolver.openOutputStream(uri, "wt")) {
                if (out == null) throw new IOException();
                out.write(bytes);
              }
              success = true;
            } catch (Exception ignored) {
            }
            final boolean done = success;
            EXPORT_UI.post(
                () -> {
                  MainActivity owner = target.get();
                  if (owner == null || !owner.liveUI()) return;
                  if (done) Toast.makeText(owner, "Note exported", Toast.LENGTH_SHORT).show();
                  else owner.explain("Export failed", "The local note is still saved.");
                });
          });
      return true;
    } catch (java.util.concurrent.RejectedExecutionException busy) {
      return false;
    }
  }
}
