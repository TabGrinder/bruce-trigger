package com.tabgrinder.brucetrigger.audit;
import android.content.Context;
import java.lang.reflect.*;
final class RecoveryPersistenceAudit {
 static void run(AuditRunner a)throws Throwable {a.onUi(()->{
  Class<?> controller=a.targetClass("UsbController"),listener=a.targetClass("TransportListener");
  Object sink=Proxy.newProxyInstance(listener.getClassLoader(),new Class<?>[]{listener},(proxy,method,args)->null);
  Constructor<?> ctor=controller.getDeclaredConstructor(Context.class,listener);ctor.setAccessible(true);
  Object first=ctor.newInstance(a.getTargetContext(),sink);
  a.method(controller,"confirmInterpreterRestart").invoke(first);
  a.method(controller,"rememberInterpreter").invoke(first);
  Object replacement=ctor.newInstance(a.getTargetContext(),sink);
  a.ok(Boolean.TRUE.equals(a.method(controller,"interpreterRecoveryRequired").invoke(replacement)),"real Android preferences restore pending interpreter after controller replacement");
  Object session=a.field(first,"session");a.set(a.field(session,"mirror"),"active",true);
  a.method(controller,"disconnect",String.class).invoke(first,"Synthetic attachment loss");
  a.ok(Boolean.TRUE.equals(a.method(controller,"interpreterRecoveryRequired").invoke(first)),"active capture loss retains recovery fence on Android");
  a.method(controller,"confirmInterpreterRestart").invoke(replacement);
  Object clean=ctor.newInstance(a.getTargetContext(),sink);
  a.ok(Boolean.FALSE.equals(a.method(controller,"interpreterRecoveryRequired").invoke(clean)),"disconnected restart acknowledgement persists");
  for(Object c:new Object[]{first,replacement,clean})a.method(controller,"destroy").invoke(c);
 });}
}
