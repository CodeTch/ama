#!/usr/bin/env python3
"""Exact full-image comparison; never masks, crops, resizes, or applies a tolerance."""
import argparse,json
from pathlib import Path
from PIL import Image,ImageChops

def compare(reference,actual,diff_path):
 with Image.open(reference) as r,Image.open(actual) as a:
  r=r.convert('RGB');a=a.convert('RGB');same_size=r.size==a.size
  size=(max(r.width,a.width),max(r.height,a.height));rc=Image.new('RGB',size,(255,0,255));ac=Image.new('RGB',size,(0,255,255));rc.paste(r,(0,0));ac.paste(a,(0,0));diff=ImageChops.difference(rc,ac)
  changed=sum(1 for pixel in diff.getdata() if pixel!=(0,0,0));hist=diff.histogram();total=sum((i%256)*count for i,count in enumerate(hist));diff.point(lambda v:min(255,v*4)).save(diff_path)
  return {'pass':same_size and changed==0,'referenceSize':r.size,'actualSize':a.size,'sameSize':same_size,'differentPixels':changed,'totalPixels':size[0]*size[1],'differentRatio':changed/(size[0]*size[1]),'absoluteChannelError':total,'threshold':0,'maskedRegions':[],'diff':str(diff_path)}

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('reference');p.add_argument('actual');p.add_argument('diff');p.add_argument('--report');a=p.parse_args();result=compare(a.reference,a.actual,a.diff);text=json.dumps(result)
 if a.report:Path(a.report).write_text(json.dumps(result,indent=2)+'\n')
 print(text);return 0 if result['pass'] else 1
if __name__=='__main__':raise SystemExit(main())
