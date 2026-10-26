#!/usr/bin/env python3
"""Run the separate audit APK in an isolated emulator; no physical USB assertions."""
from pathlib import Path
import argparse,os,subprocess,zipfile,time,shutil,tempfile,hashlib,sys
p=argparse.ArgumentParser();p.add_argument('--port',type=int,default=5556);p.add_argument('--adb-port',type=int,default=5038);p.add_argument('--build-only',action='store_true');p.add_argument('--sdk',required=True);p.add_argument('--avd-home',required=True);p.add_argument('--avd',default='trigger26');p.add_argument('--animation-only',action='store_true');p.add_argument('--inspect-boot',action='store_true',help='Inspect this isolated emulator before tests; never dismiss a target-app failure');p.add_argument('--wait-on-failure',action='store_true',help='Keep only the emulator open for an explicit retry after reviewing a failed audit');a=p.parse_args()
r=Path(__file__).resolve().parents[2];sdk=Path(a.sdk).resolve();bt=sdk/'android-15';jar=sdk/'android-35/android.jar';out=r/('build/android-audit' if a.avd=='trigger26' else 'build/android-audit-'+a.avd);out.mkdir(parents=True,exist_ok=True)
env={**os.environ,'ANDROID_I_WANT_MY_TCG':'yes','ANDROID_SDK_ROOT':str(sdk),'ANDROID_AVD_HOME':str(Path(a.avd_home).resolve()),'ANDROID_ADB_SERVER_PORT':str(a.adb_port)}
for directory in [sdk/'emulator',sdk/'platform-tools',bt]:
 for f in directory.rglob('*'):
  if f.is_file() and (f.suffix in ('','.so','.sh') or '.so.' in f.name):f.chmod(f.stat().st_mode|0o111)
def run(*args,**kw):
 kw.setdefault('timeout',180)
 kw.setdefault('stdin',subprocess.DEVNULL)
 return subprocess.run(list(map(str,args)),check=True,env=env,**kw)
def build_audit():
 classes=out/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
 run('java','com.sun.tools.javac.Main','--release','8','-cp',jar,'-d',classes,*sorted((r/'docs/audit-tests').glob('*.java')))
 with zipfile.ZipFile(out/'classes.jar','w',zipfile.ZIP_DEFLATED) as z:
  for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes))
 dex=out/'dex';dex.mkdir(exist_ok=True)
 run('java','-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--lib',jar,'--min-api','26','--output',dex,out/'classes.jar')
 run(bt/'aapt2','link','-I',jar,'--manifest',r/'docs/audit-tests/AndroidManifest-qa.xml','-o',out/'base.apk')
 with zipfile.ZipFile(out/'base.apk','a',zipfile.ZIP_DEFLATED) as z:z.write(dex/'classes.dex','classes.dex')
 run(bt/'zipalign','-f','4',out/'base.apk',out/'aligned.apk')
 sys.path.insert(0,str(r/'tools'))
 from signing import sign
 sign(r,bt,out/'aligned.apk',out/'audit.apk')
if a.build_only:
 build_audit()
 raise SystemExit(0)
log=(out/'emulator.log').open('w');emu=subprocess.Popen([str(sdk/'emulator/emulator'),'-avd',a.avd,'-port',str(a.port),'-accel','off','-gpu','swiftshader','-feature','-Vulkan','-cores','1','-no-window','-no-audio','-no-boot-anim','-no-snapshot','-no-metrics','-read-only'],stdout=log,stderr=subprocess.STDOUT,env=env)
adb_temp=tempfile.TemporaryDirectory(prefix='bruce-audit-adb-')
adb_bin=Path(adb_temp.name)/'adb';shutil.copyfile(sdk/'platform-tools/adb',adb_bin);adb_bin.chmod(0o700)
adb=[str(adb_bin),'-P',str(a.adb_port),'-s','127.0.0.1:'+str(a.port+1)]
def adb_run(*args,**kw):return run(*adb,*args,**kw)
def inspect_emulator(next_action):
 # Operates only this harness's isolated 360x640 AVD. A screenshot must ground
 # taps used to dismiss identified boot-time OS dialogs, never target-app ANRs.
 while True:
  command=input('Emulator inspection: screen / tap X Y / '+next_action+' / stop: ').strip().split()
  if command==[next_action]:return
  if command==['stop']:raise AssertionError('Audit stopped after inspection')
  if command==['screen']:
   with (out/'inspection.png').open('wb') as f:adb_run('exec-out','screencap','-p',stdout=f)
   print(out/'inspection.png',flush=True)
  elif len(command)==3 and command[0]=='tap' and all(x.isdigit() for x in command[1:]):
   x,y=map(int,command[1:])
   if 0<=x<360 and 0<=y<640:adb_run('shell','input','tap',x,y)
  else:print('Unknown inspection command',flush=True)
try:
 run(adb_bin,'-P',str(a.adb_port),'start-server')
 deadline=time.monotonic()+900;last=0
 while time.monotonic()<deadline:
  if emu.poll() is not None:raise RuntimeError('Emulator exited; inspect emulator.log')
  try:subprocess.run([str(adb_bin),'-P',str(a.adb_port),'connect','127.0.0.1:'+str(a.port+1)],env=env,capture_output=True,timeout=15)
  except subprocess.TimeoutExpired:
   print('ADB connection still pending during emulator boot',flush=True)
   continue
  try:boot=subprocess.run(adb+['shell','getprop','sys.boot_completed'],env=env,capture_output=True,text=True,timeout=15)
  except subprocess.TimeoutExpired:continue
  if boot.returncode==0 and boot.stdout.strip()=='1':break
  if time.monotonic()-last>30:print('Waiting for '+a.avd+' boot: '+boot.stdout.strip()+' '+boot.stderr.strip(),flush=True);last=time.monotonic()
  time.sleep(3)
 else:raise TimeoutError('Emulator boot deadline')
 api=adb_run('shell','getprop','ro.build.version.sdk',capture_output=True,text=True).stdout.strip()
 assert api.isdigit(),'Missing emulator API level'
 print('Android API '+api+' booted',flush=True)
 adb_run('shell','input','keyevent','82')
 if a.inspect_boot:inspect_emulator('continue')
 # Freeze the final generated fixtures after boot inspection, immediately
 # before installation. Engine validation may finish while this AVD boots.
 build_audit()
 attempt=0
 while True:
  attempt+=1
  tested_apk=out/'tested-Bruce-trigger-1.0.apk'
  shutil.copyfile(r/'build/Bruce-trigger-1.0.apk',tested_apk)
  tested_hash=hashlib.sha256(tested_apk.read_bytes()).hexdigest()
  print('APK under test SHA256: '+tested_hash,flush=True)
  adb_run('install','-r',tested_apk)
  adb_run('install','-r','-t',out/'audit.apk')
  # This is our isolated emulator package, never a physical/user device.
  adb_run('shell','pm','clear','com.tabgrinder.brucetrigger')
  animation_args=['-e','animationOnly','1'] if a.animation_only else []
  result=adb_run('shell','am','instrument','-w',*animation_args,'com.tabgrinder.brucetrigger.audit/com.tabgrinder.brucetrigger.audit.AuditRunner',capture_output=True,text=True,timeout=420)
  try:
   with (r/'docs'/('ANDROID-API'+api+'-attempt'+str(attempt)+'-logcat.txt')).open('w') as log_file:
    adb_run('logcat','-d','-t','3000',stdout=log_file,timeout=30)
  except (subprocess.TimeoutExpired,subprocess.CalledProcessError):print('Error-log collection unavailable',flush=True)
  evidence='APK under test SHA256: '+tested_hash+'\nAndroid API: '+api+'\n'+result.stdout+result.stderr
  evidence_name=('ANIMATION-' if a.animation_only else 'NATIVE-')+('1.0' if api=='26' else 'API'+api)+'.txt'
  (r/'docs'/evidence_name).write_text(evidence)
  (out/('attempt-'+str(attempt)+'.txt')).write_text(evidence)
  print('\n'.join(line for line in result.stdout.splitlines() if line.startswith('INSTRUMENTATION_RESULT:') and not line.startswith('INSTRUMENTATION_RESULT: details=')),flush=True)
  if 'INSTRUMENTATION_RESULT: status=PASS' in result.stdout:break
  if not a.wait_on_failure:raise AssertionError('Android audit did not pass; inspect docs/NATIVE-1.0.txt')
  for name in ['stock-text-fixture','remote-synthetic','fusion-disconnected','fusion-connected']:
   subprocess.run(adb+['pull','/sdcard/Android/data/com.tabgrinder.brucetrigger/files/'+name+'.png',str(out/(name+'.png'))],env=env,capture_output=True,timeout=30)
  inspect_emulator('retry')
  build_audit()
 for name in ['power-confirmation','megalodon-100','megalodon-200']+['catalog-'+name for name in ['dino_game','flappybird','highway_racer','pingpong','space_shooter','tamagochi']]:
  adb_run('pull','/sdcard/Android/data/com.tabgrinder.brucetrigger/files/'+name+'.png',out/(name+'.png'))
 for name in (['game-live-24','game-live-48','game-live-72','game-live-108'] if a.animation_only else ['waiting-highway_racer','waiting-pingpong','waiting-space_shooter','fusion-disconnected','fusion-connected','game-live-24','game-live-48','game-live-72','game-live-108']):
  adb_run('pull','/sdcard/Android/data/com.tabgrinder.brucetrigger/files/'+name+'.png',out/(name+'.png'))
 if a.animation_only:raise SystemExit(0)
 adb_run('pull','/sdcard/Android/data/com.tabgrinder.brucetrigger/files/stock-text-fixture.png',out/'stock-text-fixture.png')
 adb_run('pull','/sdcard/Android/data/com.tabgrinder.brucetrigger/files/remote-synthetic.png',out/'remote-synthetic.png')
 adb_run('pull','/sdcard/Android/data/com.tabgrinder.brucetrigger/files/stock-theme-menu.png',out/'stock-theme-menu.png')
 adb_run('pull','/sdcard/Android/data/com.tabgrinder.brucetrigger/files/cyberpunk-menu.png',out/'cyberpunk-menu.png')
 adb_run('pull','/sdcard/Android/data/com.tabgrinder.brucetrigger/files/hybrid-root-config.png',out/'hybrid-root-config.png')
 adb_run('pull','/sdcard/Android/data/com.tabgrinder.brucetrigger/files/hybrid-config-native.png',out/'hybrid-config-native.png')
 adb_run('pull','/sdcard/Android/data/com.tabgrinder.brucetrigger/files/hybrid-root-vector.png',out/'hybrid-root-vector.png')
 adb_run('pull','/sdcard/Android/data/com.tabgrinder.brucetrigger/files/flappy-usb-bridge.png',out/'flappy-usb-bridge.png')
 try:
  with (out/'logcat.txt').open('w') as f:adb_run('logcat','-d',stdout=f,timeout=30)
 except (subprocess.TimeoutExpired,subprocess.CalledProcessError):print('Optional logcat collection unavailable',flush=True)
 adb_run('shell','am','start','-n','com.tabgrinder.brucetrigger/com.tabgrinder.brucetrigger.MainActivity')
 time.sleep(2)
 with (out/'installed.png').open('wb') as f:adb_run('exec-out','screencap','-p',stdout=f)
finally:
 try:subprocess.run(adb+['emu','kill'],env=env,capture_output=True,timeout=10)
 except Exception:pass
 emu.terminate()
 try:emu.wait(timeout=15)
 except subprocess.TimeoutExpired:emu.kill()
 log.close();adb_temp.cleanup()
