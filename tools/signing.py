"""Private local signing identity. Never included in the public source archive."""
from pathlib import Path
import json,os,secrets,subprocess

def identity(root, create=False):
    directory=Path(root)/'.signing';key=directory/'release.p12';config=directory/'credentials.json'
    if not key.exists() or not config.exists():
        if key.exists() or config.exists():raise RuntimeError('Incomplete signing backup. Restore both private files.')
        if not create:raise RuntimeError('Signing identity missing. Restore .signing or explicitly choose --new-signing-key.')
        directory.mkdir(mode=0o700,exist_ok=True)
        password=secrets.token_urlsafe(40)
        env={**os.environ,'BRUCE_SIGNING_PASSWORD':password}
        subprocess.run(['keytool','-genkeypair','-keystore',str(key),'-storetype','PKCS12',
            '-storepass:env','BRUCE_SIGNING_PASSWORD','-keypass:env','BRUCE_SIGNING_PASSWORD',
            '-alias','brucetrigger','-dname','CN=Bruce trigger,OU=Community,O=TabGrinder',
            '-keyalg','RSA','-keysize','3072','-validity','10000'],env=env,check=True)
        config.write_text(json.dumps({'alias':'brucetrigger','password':password})+'\n')
        key.chmod(0o600);config.chmod(0o600)
    values=json.loads(config.read_text())
    if not isinstance(values.get('password'),str) or not values.get('alias'):raise RuntimeError('Invalid signing configuration')
    return key,values['alias'],{**os.environ,'BRUCE_SIGNING_PASSWORD':values['password']}

def sign(root, tools, source, output, create=False):
    key,alias,env=identity(root,create)
    subprocess.run(['java','-jar',str(Path(tools)/'lib/apksigner.jar'),'sign','--ks',str(key),
        '--ks-key-alias',alias,'--ks-pass','env:BRUCE_SIGNING_PASSWORD','--key-pass','env:BRUCE_SIGNING_PASSWORD',
        '--out',str(output),str(source)],env=env,check=True)
