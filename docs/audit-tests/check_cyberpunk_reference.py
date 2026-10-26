#!/usr/bin/env python3
"""Independent Pillow/RGB565 check of original store CONFIG image vs Android capture."""
from PIL import Image
from pathlib import Path
import argparse,numpy as np
p=argparse.ArgumentParser();p.add_argument('--original',required=True);p.add_argument('--capture',required=True);a=p.parse_args()
im=Image.open(a.original).convert('RGBA');base=Image.new('RGBA',im.size,(0,0,0,255));base.alpha_composite(im)
v=np.asarray(base.convert('RGB')).astype(np.uint16)
expected=v.copy();expected[:,:,0]=((v[:,:,0]&248)|(v[:,:,0]>>5));expected[:,:,1]=((v[:,:,1]&252)|(v[:,:,1]>>6));expected[:,:,2]=((v[:,:,2]&248)|(v[:,:,2]>>5))
capture=Image.open(a.capture).convert('RGB');x=(capture.width-im.width)//2;y=(capture.height-im.height)//2
actual=np.asarray(capture.crop((x,y,x+im.width,y+im.height))).astype(np.int32)
diff=np.abs(expected.astype(np.int32)-actual)
assert diff.max()==0, int(diff.max())
print('PASS original Cyberpunk CONFIG:',im.width*im.height,'pixels; max RGB difference',int(diff.max()),'; centered x=',x,'y=',y)
print('Android emulator image; no physical hardware equality claim.')
