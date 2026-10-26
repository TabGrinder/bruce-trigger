#!/usr/bin/env python3
"""Static checks of the packaged APK. Does not install or run Android."""
from pathlib import Path
import argparse,subprocess,zipfile,hashlib,json,re
from inventory import source_inventory,source_digest
p=argparse.ArgumentParser();p.add_argument('--build-tools',required=True);a=p.parse_args()
r=Path(__file__).resolve().parents[1];apk=r/'build/Bruce-trigger-1.0.apk';tools=Path(a.build_tools).resolve()
def run(*args):return subprocess.check_output([str(x) for x in args],text=True)
with zipfile.ZipFile(apk) as z:
 assert z.testzip() is None,'ZIP integrity'
 record=json.loads(z.read('assets/BUILD-RECORD.json'))
 current=source_inventory(r)
 assert record['files']==current and record['source_sha256']==source_digest(current),'build-input inventory mismatch'
 assert {'AndroidManifest.xml','classes.dex','resources.arsc'}<=set(z.namelist()),'required APK entries'
 assert not any(x.endswith(('.keystore','.jks','.p12')) or Path(x).name=='credentials.json'
                or '.signing' in Path(x).parts for x in z.namelist()),'private signing material in APK'
manifest=run(tools/'aapt2','dump','xmltree','--file','AndroidManifest.xml',apk)
permissions=run(tools/'aapt2','dump','permissions',apk)
assert not re.search(r'uses-permission(?:-sdk-\d+)?:',permissions),'unexpected Android permission'
assert 'debuggable(0x0101000f)=true' not in manifest,'debuggable release APK'
assert 'allowBackup(0x01010280)=false' in manifest,'backup unexpectedly enabled'
assert 'dataExtractionRules' in manifest,'missing modern backup policy'
assert 'launchMode' in manifest,'missing single task launcher'
for name in ['backup_rules','data_extraction_rules']:
 rules=run(tools/'aapt2','dump','xmltree','--file','res/xml/'+name+'.xml',apk)
 assert 'exclude' in rules,'empty backup exclusions'
assert 'usesCleartextTraffic(0x010104ec)=false' in manifest,'cleartext policy'
assert manifest.count('exported(0x01010010)=true')==1,'exported component count'
assert 'android.intent.action.MAIN' in manifest and 'android.intent.category.LAUNCHER' in manifest
sig=run('java','-jar',tools/'lib/apksigner.jar','verify','--verbose','--print-certs',apk)
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in sig
assert 'Verified using v3 scheme (APK Signature Scheme v3): true' in sig
latest=max(f.stat().st_mtime for f in (r/'app/src/main').rglob('*') if f.is_file())
assert apk.stat().st_mtime>=latest,'APK predates source modifications'
print('PASS: package ZIP, required entries, permission policy, backup, cleartext, exported launcher, v2/v3 signature, source freshness and embedded build-input inventory.')
print('APK bytes:',apk.stat().st_size)
print('APK SHA256:',hashlib.sha256(apk.read_bytes()).hexdigest())
print(sig)
