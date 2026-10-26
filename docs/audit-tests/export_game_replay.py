#!/usr/bin/env python3
"""Export the complete validated bridge transcript, including its startup handshake."""
from pathlib import Path
from game_wire import decode
import base64, gzip
r=Path(__file__).resolve().parents[2]
wire=(r/'build/files-proof/wire-0.txt').read_text()
assert wire.startswith('SEARCH\nON\n') and wire.endswith('DONE\n')
assert len(decode(wire.splitlines()))==108
encoded=base64.b64encode(gzip.compress(wire.encode(),mtime=0)).decode()
chunks=' +\n'.join('"'+encoded[i:i+120]+'"' for i in range(0,len(encoded),120))
source='''package com.tabgrinder.brucetrigger.audit;
/** Current Files bridge + pinned unchanged Flappy; includes owned SEARCH/ON handshake.
 * Native display, USB and controls are doubles; see test_files_bridge.py. */
final class GameDrawFixtures {
static String wire() throws Exception {
String encoded='''+chunks+''';
java.util.zip.GZIPInputStream in=new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(android.util.Base64.decode(encoded,0)));
java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);in.close();return new String(out.toByteArray(),"US-ASCII");
}}
'''
target=r/'docs/audit-tests/GameDrawFixtures.java'
if target.read_text()!=source:target.write_text(source)
print('PASS exported actual SEARCH/ON handshake, 108 transactions and terminal marker')
