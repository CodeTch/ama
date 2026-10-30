from pathlib import Path
from PIL import Image,ImageOps,ImageDraw
import pdfplumber,json,re
base=Path(__file__).resolve().parent
root=base.parents[2]
pdf=root/'output/pdf/企业智能会议助手-内部宣讲详细讲解稿.pdf'
issues=[];stats=[]
with pdfplumber.open(pdf) as p:
    for n,page in enumerate(p.pages,1):
        chars=page.chars
        overflow=[c for c in chars if c['x0'] < -1 or c['x1'] > page.width+1 or c['top'] < -1 or c['bottom']>page.height+1]
        if overflow:issues.append({'page':n,'overflow_chars':len(overflow)})
        body=[c for c in chars if c['top']>43 and c['bottom']<page.height-40]
        stats.append({'page':n,'chars':len(chars),'body_bottom':round(max([c['bottom'] for c in body] or [0]),1)})
    images=sorted(base.glob('release-page-*.png'))
    assert len(images)==len(p.pages),(len(images),len(p.pages))
for group in range((len(images)+7)//8):
    sheet=Image.new('RGB',(1280,940),'#DCE5EA')
    for k,src in enumerate(images[group*8:group*8+8]):
        im=Image.open(src).convert('RGB');im.thumbnail((300,427))
        x=10+(k%4)*320;y=12+(k//4)*470
        sheet.paste(im,(x+(300-im.width)//2,y))
        ImageDraw.Draw(sheet).text((x+8,y+438),src.stem,fill='#173247')
    sheet.save(base/f'release-contact-{group+1}.jpg',quality=90)
(base/'layout-check.json').write_text(json.dumps({'overflow':issues,'pages':stats},ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'pages':len(images),'overflow':issues,'sparse_pages':[s for s in stats if s['chars']<200]},ensure_ascii=False))
