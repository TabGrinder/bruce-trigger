#!/usr/bin/env python3
"""Production worker against USB packet, timeout and corruption semantics."""
from pathlib import Path
import argparse,subprocess
p=argparse.ArgumentParser();p.add_argument('--catalog',action='store_true');a=p.parse_args()
r=Path(__file__).resolve().parents[1];classes=r/'build/test-usb'
assert classes.is_dir(),'Run tools/test_usb.py first'
subprocess.run(['java','-Dproject.root='+str(r),'-cp',str(classes),'com.tabgrinder.brucetrigger.UsbPacketTimeoutRegressionTest',*(['--catalog'] if a.catalog else [])],check=True)
