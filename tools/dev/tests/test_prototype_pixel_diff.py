"""Negative controls ensure a one-channel/one-pixel change cannot pass the pixel gate."""
import tempfile,unittest
from pathlib import Path
from PIL import Image
from tools.dev.prototype_pixel_diff import compare

class ExactPixelGateTest(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name)
 def run_comparison(self,a,b):
  r=self.root/'reference.png';p=self.root/'actual.png';a.save(r);b.save(p);return compare(r,p,self.root/'diff.png')
 def test_identical_images_pass(self):
  a=Image.new('RGB',(12,8),'white');r=self.run_comparison(a,a.copy());self.assertTrue(r['pass']);self.assertEqual(r['differentPixels'],0)
 def test_one_level_in_one_channel_of_one_pixel_fails(self):
  a=Image.new('RGB',(12,8),'white');b=a.copy();b.putpixel((7,3),(254,255,255));r=self.run_comparison(a,b);self.assertFalse(r['pass']);self.assertEqual(r['differentPixels'],1);self.assertEqual(r['absoluteChannelError'],1);self.assertEqual(r['threshold'],0);self.assertEqual(r['maskedRegions'],[])
 def test_different_dimensions_fail_without_resizing(self):
  r=self.run_comparison(Image.new('RGB',(12,8),'white'),Image.new('RGB',(12,9),'white'));self.assertFalse(r['pass']);self.assertFalse(r['sameSize']);self.assertEqual(r['referenceSize'],(12,8));self.assertEqual(r['actualSize'],(12,9))
if __name__=='__main__':unittest.main()
