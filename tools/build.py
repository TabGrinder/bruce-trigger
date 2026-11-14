#!/usr/bin/env python3
"""Build a non-debuggable APK with the official Android SDK; no Gradle or third-party app libraries."""
from pathlib import Path
import argparse, subprocess, zipfile, shutil, os
import xml.etree.ElementTree as ET
import json,hashlib
from inventory import source_inventory,source_digest
p=argparse.ArgumentParser();p.add_argument('--android-jar',required=True);p.add_argument('--build-tools',required=True);p.add_argument('--new-signing-key',action='store_true',help='Explicitly create a new local signing identity if no key exists; incompatible with prior signed installs');a=p.parse_args()
root=Path(__file__).resolve().parents[1];build=root/'build';build.mkdir(exist_ok=True)
jar=Path(a.android_jar).resolve();tools=Path(a.build_tools).resolve()
def run(*args): subprocess.run([str(x) for x in args],check=True)
def tool(name):
 f=tools/name;f.chmod(f.stat().st_mode|0o111);return f
classes=build/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
sources=sorted((root/'app/src/main/java').rglob('*.java'))
run('java','com.sun.tools.javac.Main','--release','8','-Xlint:all,-deprecation,-options','-Werror','-classpath',jar,'-d',classes,*sources)
run(tool('aapt2'),'compile','--dir',root/'app/src/main/res','-o',build/'resources.zip')
ET.register_namespace('android','http://schemas.android.com/apk/res/android')
manifest=ET.parse(root/'app/src/main/AndroidManifest.xml');manifest.getroot().set('package','com.tabgrinder.brucetrigger');manifest.write(build/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
run(tool('aapt2'),'link','--auto-add-overlay','-I',jar,'--manifest',build/'AndroidManifest.xml','-R',build/'resources.zip','-o',build/'base.apk','--min-sdk-version','26','--target-sdk-version','35')
with zipfile.ZipFile(build/'classes.jar','w',zipfile.ZIP_DEFLATED) as z:
 for f in classes.rglob('*.class'): z.write(f,f.relative_to(classes))
dex=build/'dex';shutil.rmtree(dex,ignore_errors=True);dex.mkdir()
run('java','-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--lib',jar,'--min-api','26','--output',dex,build/'classes.jar')
inputs=source_inventory(root)
record={'format':1,'claim':'Local build input record, not an independent attestation','source_sha256':source_digest(inputs),'files':inputs,'java':subprocess.check_output(['java','-version'],stderr=subprocess.STDOUT,text=True),'tools':{str(f.name):hashlib.sha256(f.read_bytes()).hexdigest() for f in [jar,tools/'aapt2',tools/'zipalign',tools/'lib/d8.jar',tools/'lib/apksigner.jar']}}
(root/'docs/SOURCE-HASHES.json').write_text(json.dumps(inputs,indent=2)+'\n')
(build/'BUILD-RECORD.json').write_text(json.dumps(record,indent=2)+'\n')
shutil.copyfile(build/'base.apk',build/'unsigned.apk')
with zipfile.ZipFile(build/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
 for f in dex.glob('*.dex'):z.write(f,f.name)
 z.write(build/'BUILD-RECORD.json','assets/BUILD-RECORD.json')
run(tool('zipalign'),'-f','4',build/'unsigned.apk',build/'aligned.apk')
from signing import sign
out=build/'Bruce-trigger-1.0.1.apk'
sign(root,tools,build/'aligned.apk',out,a.new_signing_key)
run('java','-jar',tools/'lib/apksigner.jar','verify','--verbose',out)
print(out)
