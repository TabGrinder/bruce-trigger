"""Additional exact package checks. No radio or Android runtime assertions."""
import subprocess,re,sys,zipfile,json
from pathlib import Path
r=Path(__file__).resolve().parents[2]
apk=r/'build/Bruce-trigger-1.0.apk'
aapt=Path(sys.argv[1])/'aapt2'
def out(*args):return subprocess.check_output([str(aapt),*map(str,args)],text=True)
p=out('dump','permissions',apk)
actual=set(re.findall(r"uses-permission: name='([^']+)'",p))
expected=set()
assert actual==expected,(actual,expected)
m=out('dump','xmltree','--file','AndroidManifest.xml',apk)
assert 'com.tabgrinder.brucetrigger' in m
assert 'com.tabgrinder.brucetrigger.MainActivity' in m
assert 'launchMode(0x0101001d)=2' in m
assert 'versionCode(0x0101021b)=100' in m
assert '1.0' in m
assert 'Bruce Trigger' in m
assert re.search(r'enableOnBackInvokedCallback\([^)]*\)=false',m),'legacy Activity Back routing must remain enabled'
assert 'debuggable(0x0101000f)=true' not in m
assert m.count('E: activity ')==1
assert not any('E: '+c+' ' in m for c in ['service','receiver','provider'])
for name in ['backup_rules','data_extraction_rules']:
 s=out('dump','xmltree','--file','res/xml/'+name+'.xml',apk)
 for domain in ['root','file','database','sharedpref','external','device_root','device_file','device_database','device_sharedpref']:
  assert '="'+domain+'"' in s,domain
 if name=='data_extraction_rules':assert 'cloud-backup' in s and 'device-transfer' in s
with zipfile.ZipFile(apk) as z:
 assert not any(n.endswith(('.so','.keystore','.jks','.html','.js')) for n in z.namelist())
 license_text=z.read('res/raw/glcd_license.txt').decode('utf-8')
 assert 'Copyright (c) 2012 Adafruit Industries' in license_text and 'Redistribution and use in source and binary forms' in license_text
 record=json.loads(z.read('assets/BUILD-RECORD.json'))
 assert len(record['tools'])==5
 assert all(len(h)==64 for h in record['files'].values())
print('PASS: zero manifest permissions; version100/1.0; singleTask; non-debuggable; one Activity and no services/providers/receivers; backup+transfer domain exclusions; no native libraries/Web assets/keys; build record.')
