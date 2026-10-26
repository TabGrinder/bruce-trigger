package com.tabgrinder.brucetrigger.audit;
import android.app.*;
import java.util.concurrent.atomic.AtomicReference;

/** Verified Others selection launches the explicit RAM adaptation, never blind native SELECT. */
final class NativeGameAudit {
 static void run(AuditRunner a,Activity activity)throws Throwable {
  Object[] state=FileInfoFlowAudit.setup(a,activity,false,">Megalodon\n");
  FileInfoFlowAudit.awaitWrite(a,state,"optionsJSON\n");
  String menu="{\"width\":320,\"height\":170,\"menu\":\"sub_menu\",\"menu_title\":\"Others\",\"active\":1,\"options\":[{\"n\":0,\"label\":\"QRCodes\"},{\"n\":1,\"label\":\"Megalodon\"},{\"n\":2,\"label\":\"Main Menu\"}]}";
  FileInfoFlowAudit.feed(a,activity,state,UsbUiAudit.bytes(menu+"\n# "));
  FileInfoFlowAudit.awaitWrite(a,state,"nav esc\n");
  FileInfoFlowAudit.feed(a,activity,state,UsbUiAudit.bytes("# "));
  Thread.sleep(150);
  a.onUi(()->a.ok(((AtomicReference<?>)a.field(state[2],"outbound")).get()==null,"Megalodon cannot upload before native menu redraw"));
  FileInfoFlowAudit.feed(a,activity,state,a.hex("aa 05 00 00 00"));
  FileInfoFlowAudit.awaitWrite(a,state,"optionsJSON\n");
  FileInfoFlowAudit.feed(a,activity,state,UsbUiAudit.bytes(StockOptionsFixtures.ROOT+"\n# "));
  FileInfoFlowAudit.awaitWrite(a,state,"js run_from_buffer ");
  String body=FileInfoFlowAudit.uploadBody(a,activity,state);
  a.ok(body.contains("Megalodon RAM adaptation")&&body.contains("capture=true")&&!body.contains("st.read"),"verified root uploads the pinned RAM game without any stored-file execution");
  a.onUi(()->{
   a.ok(Boolean.TRUE.equals(a.field(activity,"gamePresentation")),"Megalodon uses the real game presentation route");
   a.ok("Megalodon (RAM)".equals(a.field(a.field(state[1],"game"),"filePath")),"session visibly identifies RAM adaptation");
   java.util.regex.Matcher nonce=java.util.regex.Pattern.compile("BT_([a-f0-9]{16})_GAME").matcher(body);
   a.ok(nonce.find(),"Megalodon upload is scoped to the current connection");
   String tag="BT_"+nonce.group(1)+"_GAME|";
   UsbUiAudit.receive(a,state[1],"# Entering interpreter...\n"+tag+"CAPTURE_OFF\n"+tag+"SEARCH\n"+tag+"ON\n");
   a.ok(Boolean.TRUE.equals(a.field(a.field(state[1],"game"),"active")),"Megalodon starts after native handover acknowledgement");
   UsbUiAudit.receive(a,state[1],tag+"DONE\nExiting interpreter...\n");
   a.ok(Boolean.TRUE.equals(a.method(a.field(state[1],"game").getClass(),"ended").invoke(a.field(state[1],"game"))),"Megalodon interpreter completion is confirmed before closing the test connection");
   a.method(state[0].getClass(),"disconnect",String.class).invoke(state[0],"Megalodon launch audit completed");
   a.ok(!Boolean.TRUE.equals(a.method(state[0].getClass(),"interpreterRecoveryRequired").invoke(state[0])),"a confirmed Megalodon exit does not contaminate the next independent game fixture");
  });
 }
}
