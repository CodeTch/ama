from pathlib import Path
import json, math, re
from PIL import Image, ImageDraw, ImageFont
from pypdf import PdfReader
import pdfplumber

ROOT=Path(__file__).resolve().parents[2]
PDF=ROOT/'output/pdf/2026-10-26-企业智能会议助手项目简要方案.pdf'
QA=ROOT/'tmp/pdfs/ama-review-qa-release'
reader=PdfReader(PDF)
page_text=[p.extract_text() or '' for p in reader.pages]
dictionary=(ROOT/'docs/database/ama-data-dictionary.md').read_text(encoding='utf-8')
tables=re.findall(r'^## ([a-z][a-z0-9_]*)$',dictionary,re.M)
joined=re.sub(r'\s+','', ''.join(page_text))
missing=[t for t in tables if t not in joined]
assert len(tables)==99 and not missing,missing
unsafe=[]; font_info={}
for number,page in enumerate(reader.pages,1):
    for annot in page.get('/Annots',[]):
        obj=annot.get_object(); action=obj.get('/A')
        if action and action.get('/S') in ['/Launch','/GoToR','/URI']:
            unsafe.append({'page':number,'action':str(action)})
        assert obj.get('/Subtype')!='/FileAttachment'
    for resource in page['/Resources'].get('/Font',{}).values():
        font=resource.get_object(); name=str(font.get('/BaseFont'))
        descriptor=font.get('/FontDescriptor')
        embedded=False
        if descriptor:
            d=descriptor.get_object(); embedded=any(k in d for k in ['/FontFile','/FontFile2','/FontFile3'])
        font_info[name]=embedded
assert not unsafe,unsafe
assert all(v for k,v in font_info.items() if 'MicrosoftYaHei' in k or 'YaHei' in k),font_info
assert not reader.is_encrypted
assert '\u5df2\u4ea4\u4ed8' not in joined
assert '12\u201414' not in joined
assert '\u4eba\u5458\u914d\u7f6e' not in joined

overflows=[]
with pdfplumber.open(PDF) as pdf:
    for number,page in enumerate(pdf.pages,1):
        for char in page.chars:
            if char['x0'] < 20 or char['x1'] > float(page.width)-20 or char['top'] < 6 or char['bottom'] > float(page.height)-6:
                overflows.append({'page':number,'text':char['text'],'bounds':[char['x0'],char['top'],char['x1'],char['bottom']]})
assert not overflows,overflows[:20]

font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',19)
files=sorted(QA.glob('page-*.png'))
assert len(files)==len(reader.pages)
for batch_start in range(0,len(files),6):
    sheet=Image.new('RGB',(1340,1510),'#E7ECF3'); draw=ImageDraw.Draw(sheet)
    for slot,path in enumerate(files[batch_start:batch_start+6]):
        im=Image.open(path).convert('RGB'); im.thumbnail((640,452))
        x=20+(slot%2)*670; y=15+(slot//2)*500
        draw.text((x,y),f'Page {batch_start+slot+1}',fill='#14243D',font=font)
        sheet.paste(im,(x,y+32))
    sheet.save(QA/f'contact-{batch_start//6+1:02}.png')

summary={'page_count':len(reader.pages),'file_bytes':PDF.stat().st_size,'table_names_verified':len(tables),
         'external_file_actions':unsafe,'fonts':font_info,'content_out_of_page_bounds':overflows,
         'pages':[{'page':i+1,'characters':len(t),'first_text':t.splitlines()[:4]} for i,t in enumerate(page_text)]}
(QA/'verification.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in summary.items() if k!='pages'},ensure_ascii=True,indent=2))
for i,t in enumerate(page_text):
    print(f'Page {i+1:02} | chars={len(t):4} | '+ ' / '.join(t.splitlines()[3:5]).encode('ascii','backslashreplace').decode())
