#!/usr/bin/env python3
"""Compare the native PNG theme capture with an independent Pillow/RGB565 reference."""
import argparse, io, zipfile
from pathlib import Path
import numpy as np
from PIL import Image

p=argparse.ArgumentParser()
p.add_argument('--firmware',required=True)
p.add_argument('--capture',required=True)
a=p.parse_args()
with zipfile.ZipFile(Path(a.firmware)/'sd_files/themes/example/Pirata_png.zip') as z:
    image=Image.open(io.BytesIO(z.read('Theme_162/105px/wifi.png'))).convert('RGBA')
source=np.asarray(image,dtype=np.uint16)
# Source-over on a black panel, then independent RGB565 quantization/expansion.
rgb=((source[:,:,:3]*source[:,:,3:]+127)//255).astype(np.uint8)
expected=rgb.copy()
expected[:,:,0]=(rgb[:,:,0]&248)|(rgb[:,:,0]>>5)
expected[:,:,1]=(rgb[:,:,1]&252)|(rgb[:,:,1]>>6)
expected[:,:,2]=(rgb[:,:,2]&248)|(rgb[:,:,2]>>5)
panel=Image.open(a.capture).convert('RGB')
assert panel.size==(320,170)
frame=np.asarray(panel,dtype=np.uint8)
x=(320-image.width)//2;y=(170-image.height)//2
actual=frame[y:y+image.height,x:x+image.width]
delta=np.abs(actual.astype(np.int16)-expected.astype(np.int16))
assert np.array_equal(expected,actual), 'PNG alpha/compositing/pixel mismatch: '+str(delta.max())
print('PASS independent Pillow vs native Android PNG rendering:',image.width*image.height,'pixels; maximum RGB difference',int(delta.max()))
print('The image is an original upstream example; the screenshot is an emulator test, not a physical LILYGO capture.')
