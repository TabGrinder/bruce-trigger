"""Local build-input inventory; not an independent reproducible-build attestation."""
from pathlib import Path
import hashlib,json

def source_inventory(root):
    files=[]
    for dirname in ('app/src/main','tools','tests'):
        files.extend(p for p in (root/dirname).rglob('*') if p.is_file() and '__pycache__' not in p.parts)
    files.extend(p for p in root.glob('*.gradle') if p.is_file())
    files.append(root/'app/build.gradle')
    return {str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(set(files))}

def source_digest(files):
    return hashlib.sha256(json.dumps(files,sort_keys=True,separators=(',',':')).encode()).hexdigest()
