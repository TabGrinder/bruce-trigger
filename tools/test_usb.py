#!/usr/bin/env python3
"""Current USB production code against deterministic Android/USB doubles."""
from pathlib import Path
import subprocess,shutil,sys
r=Path(__file__).resolve().parents[1]
out=r/'build/test-usb'
shutil.rmtree(out,ignore_errors=True);out.mkdir(parents=True)
src=r/'app/src/main/java/com/tabgrinder/brucetrigger'
files=[src/n for n in ['UsbController.java','MenuRefreshPolicy.java','FilesScriptSelection.java','FilesGameScript.java','MegalodonScript.java','GameDrawValidator.java','UsbGameBridge.java','GameScreenStream.java','GameInputQueue.java','RecoverableGameStream.java','PacedUsbWriter.java','StockScriptText.java','TransportListener.java','UsbProfile.java','UsbSerialSession.java','UsbMirrorBridge.java','ThemeScript.java','ObservedThemeScript.java','ThemeTransfer.java','CommandPolicy.java','FrameDecoder.java','FrameValidator.java','WindowBudget.java','DisplayText.java']]
files+=sorted((r/'tests/usb-stubs').rglob('*.java'))
subprocess.run(['java','com.sun.tools.javac.Main','--release','8','-d',str(out),*map(str,files)],check=True)
if '--compile-only' in sys.argv:raise SystemExit(0)
subprocess.run(['java','-cp',str(out),'com.tabgrinder.brucetrigger.UsbRegressionTest'],check=True)
subprocess.run(['java','-cp',str(out),'com.tabgrinder.brucetrigger.UsbMirrorRegressionTest'],check=True)
subprocess.run(['java','-DexpectRecovery=true','-cp',str(out),'com.tabgrinder.brucetrigger.UsbClaimRegressionProbe'],check=True)

subprocess.run(['java','-cp',str(out),'com.tabgrinder.brucetrigger.ThemeRegressionTest'],check=True)

subprocess.run(['java','-cp',str(out),'com.tabgrinder.brucetrigger.UploadRegressionTest'],check=True)

subprocess.run(["java","-cp",str(out),"com.tabgrinder.brucetrigger.MenuSessionRegressionTest"],check=True)

subprocess.run(["java","-Dproject.root="+str(r),"-cp",str(out),"com.tabgrinder.brucetrigger.GameRegressionTest"],check=True)

subprocess.run(["java","-Dproject.root="+str(r),"-cp",str(out),"com.tabgrinder.brucetrigger.FilesGameRegressionTest"],check=True)

subprocess.run(["java","-cp",str(out),"com.tabgrinder.brucetrigger.FileInfoRegressionTest"],check=True)


subprocess.run(["java","-Dproject.root="+str(r),"-cp",str(out),"com.tabgrinder.brucetrigger.UsbPacketTimeoutRegressionTest"],check=True)

subprocess.run(["java","-Dproject.root="+str(r),"-cp",str(out),"com.tabgrinder.brucetrigger.RecoveryGameRegressionTest"],check=True)

subprocess.run(["java","-Dproject.root="+str(r),"-cp",str(out),"com.tabgrinder.brucetrigger.GameInputRegressionTest"],check=True)

subprocess.run(["java","-Dproject.root="+str(r),"-cp",str(out),"com.tabgrinder.brucetrigger.HandoverRegressionTest"],check=True)

subprocess.run(["java","-Dproject.root="+str(r),"-cp",str(out),"com.tabgrinder.brucetrigger.MegalodonRegressionTest"],check=True)

subprocess.run(["java","-cp",str(out),"com.tabgrinder.brucetrigger.CompressedGameRegressionTest"],check=True)
subprocess.run(["java","-cp",str(out),"com.tabgrinder.brucetrigger.PersistentRecoveryTest"],check=True)
subprocess.run(["java","-cp",str(out),"com.tabgrinder.brucetrigger.QueuedInputCancellationTest"],check=True)
