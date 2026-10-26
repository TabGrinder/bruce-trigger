#!/usr/bin/env python3
from pathlib import Path
import subprocess
r=Path(__file__).resolve().parents[1];out=r/'build/test';out.mkdir(parents=True,exist_ok=True)
sources=[r/'app/src/main/java/com/tabgrinder/brucetrigger'/x for x in ['FrameDecoder.java','FrameValidator.java','CommandPolicy.java','WindowBudget.java','DisplayText.java','ConsoleBuffer.java','RotaryGesture.java','NativeMirrorStream.java','NativeText.java','NativePowerDialog.java','StockGlyphs.java','TransportListener.java','SelectedTransportListener.java']]+[r/'tests/ProtocolTest.java',r/'tests/NativeTextTest.java']
subprocess.run(['java','com.sun.tools.javac.Main','--release','8','-d',str(out),*[str(x) for x in sources]],check=True)
subprocess.run(['java','-cp',str(out),'ProtocolTest'],check=True)
subprocess.run(['java','-cp',str(out),'com.tabgrinder.brucetrigger.NativeTextTest'],check=True)
subprocess.run(['java','com.sun.tools.javac.Main','--release','8','-cp',str(out),'-d',str(out),str(r/'tests/SelectedTransportTest.java')],check=True)
subprocess.run(['java','-cp',str(out),'com.tabgrinder.brucetrigger.SelectedTransportTest'],check=True)
subprocess.run(['java','com.sun.tools.javac.Main','--release','8','-cp',str(out),'-d',str(out),str(r/'tests/SafetyTest.java')],check=True)
subprocess.run(['java','-cp',str(out),'SafetyTest'],check=True)

subprocess.run(["java","com.sun.tools.javac.Main","--release","8","-cp",str(out),"-d",str(out),str(r/"tests/PresentationSafetyTest.java")],check=True)
subprocess.run(["java","-cp",str(out),"PresentationSafetyTest"],check=True)

subprocess.run(["java","com.sun.tools.javac.Main","--release","8","-cp",str(out),"-d",str(out),str(r/"tests/InteractionTest.java")],check=True)
subprocess.run(["java","-cp",str(out),"InteractionTest"],check=True)

subprocess.run(["java","com.sun.tools.javac.Main","--release","8","-cp",str(out),"-d",str(out),str(r/"tests/NativeMirrorStreamTest.java")],check=True)
subprocess.run(["java","-cp",str(out),"NativeMirrorStreamTest"],check=True)

subprocess.run(['java','com.sun.tools.javac.Main','--release','8','-cp',str(out),'-d',str(out),str(r/'tests/NativePowerDialogTest.java')],check=True)
subprocess.run(['java','-cp',str(out),'com.tabgrinder.brucetrigger.NativePowerDialogTest'],check=True)

subprocess.run(['java','com.sun.tools.javac.Main','--release','8','-cp',str(out),'-d',str(out),str(r/'tests/LineBoundaryTest.java')],check=True)
subprocess.run(['java','-cp',str(out),'LineBoundaryTest'],check=True)
