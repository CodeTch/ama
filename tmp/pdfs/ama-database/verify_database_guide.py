from pathlib import Path
import re,json,hashlib
from PIL import Image,ImageDraw
import pdfplumber
from pypdf import PdfReader
BASE=Path(__file__).resolve().parent
ROOT=BASE.parents[2]
PDF=ROOT/'output/pdf/企业智能会议助手-数据库设计与逐表应用详解.pdf'
META=json.loads((BASE/'verification.json').read_text(encoding='utf-8'))
raw=(ROOT/'docs/database/ama-data-dictionary.md').read_text(encoding='utf-8')
sections={c.splitlines()[0]:c for c in re.split(r'^## ',raw,flags=re.M)[2:]}
reader=PdfReader(PDF)
texts=[re.sub(r'\s+','',p.extract_text()) for p in reader.pages]
heads=[h for h in META['headings'] if re.match(r'^\d{2} [a-z_]+｜',h['title'])]
missing=[]
for i,h in enumerate(heads):
    name=re.match(r'^\d{2} ([a-z_]+)｜',h['title']).group(1)
    end=heads[i+1]['page'] if i+1<len(heads) else len(texts)
    portion=''.join(texts[h['page']-1:end])
    marker=re.sub(r'\s+','',h['title'])
    portion=portion.split(marker,1)[1]
    if i+1<len(heads):
        portion=portion.split(re.sub(r'\s+','',heads[i+1]['title']),1)[0]
    fields=re.findall(r'^\|([a-z][a-z0-9_]*)\|',sections[name],re.M)
    absent=[f for f in fields if f not in portion]
    if absent:missing.append({'table':name,'fields':absent})
    for token in ['用在什么地方','何时写入或更新','谁读取、读取后做什么','为什么这样设计','省略或混用的后果','完整字段与业务含义','关联、索引与完整性','更新、删除与历史边界']:
        if token not in portion:missing.append({'table':name,'section':token})
issues=[];stats=[]
with pdfplumber.open(PDF) as doc:
    for num,p in enumerate(doc.pages,1):
        bad=[c for c in p.chars if c['x0']<-.5 or c['x1']>p.width+.5 or c['top']<-.5 or c['bottom']>p.height+.5]
        if bad:issues.append({'page':num,'outside':len(bad)})
        body=[c for c in p.chars if c['top']>39 and c['bottom']<p.height-39]
        stats.append({'page':num,'body_chars':len(body),'bottom':round(max([c['bottom'] for c in body] or [0]),1)})
images=sorted((BASE/'final-render').glob('page-*.png'))
assert len(images)==len(reader.pages),(len(images),len(reader.pages))
for group in range((len(images)+11)//12):
    sheet=Image.new('RGB',(1280,1410),'#DCE5E9')
    for j,src in enumerate(images[group*12:group*12+12]):
        im=Image.open(src).convert('RGB');im.thumbnail((300,426))
        x=10+(j%4)*320;y=12+(j//4)*470
        sheet.paste(im,(x+(300-im.width)//2,y))
        ImageDraw.Draw(sheet).text((x+12,y+438),src.stem,fill='#16364A')
    sheet.save(BASE/f'contact-{group+1:02d}.jpg',quality=90)
res={'pages':len(reader.pages),'table_count':len(heads),'field_count':META['field_count'],'missing_content':missing,'overflow':issues,
 'sparse':[s for s in stats if s['body_chars']<180 and s['page']>1], 'pages_stats':stats,
 'sha256':hashlib.sha256(PDF.read_bytes()).hexdigest()}
(BASE/'qa.json').write_text(json.dumps(res,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in res.items() if k!='pages_stats'},ensure_ascii=False))
print('\n'.join(str(h['page'])+' '+h['title'] for h in META['headings'] if h['level']==0))
assert not missing and not issues
