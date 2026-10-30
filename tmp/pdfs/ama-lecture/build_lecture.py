from pathlib import Path
import re, json, html, sys, hashlib
from reportlab.pdfgen import canvas
from reportlab.platypus import BaseDocTemplate, PageTemplate, Frame, Paragraph, Spacer, PageBreak, Table, TableStyle, Image, KeepTogether
from reportlab.platypus.tableofcontents import TableOfContents
from reportlab.lib import colors
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.enums import TA_LEFT, TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from pypdf import PdfReader
from PIL import Image as PILImage, ImageOps, ImageDraw
from table_explanations import WHY

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / 'output/pdf'
TMP = Path(__file__).resolve().parent
SOURCE = ROOT / 'docs/reviews/2026-10-26-企业智能会议助手项目简要方案.md'
MD = OUT / '企业智能会议助手-内部宣讲详细讲解稿.md'
PDF = OUT / '企业智能会议助手-内部宣讲详细讲解稿.pdf'
FONTDIR = Path('C:/Windows/Fonts')
pdfmetrics.registerFont(TTFont('Song', str(FONTDIR / 'simsun.ttc'), subfontIndex=0))
pdfmetrics.registerFont(TTFont('Hei', str(FONTDIR / 'simhei.ttf')))
pdfmetrics.registerFontFamily('Song',normal='Song',bold='Hei',italic='Song',boldItalic='Hei')
pdfmetrics.registerFontFamily('Hei',normal='Hei',bold='Hei',italic='Hei',boldItalic='Hei')
W,H=A4
M=49
CW=W-2*M
NAVY=colors.HexColor('#173247')
TEAL=colors.HexColor('#007F86')
INK=colors.HexColor('#253744')
MUTED=colors.HexColor('#647781')
LIGHT=colors.HexColor('#EEF5F6')

styles={
'body':ParagraphStyle('body',fontName='Song',fontSize=10.7,leading=17,textColor=INK,spaceAfter=6,wordWrap='CJK',splitLongWords=True,allowWidows=0,allowOrphans=0),
'h1':ParagraphStyle('h1',fontName='Hei',fontSize=21,leading=30,textColor=NAVY,spaceBefore=4,spaceAfter=16,keepWithNext=True,wordWrap='CJK'),
'h2':ParagraphStyle('h2',fontName='Hei',fontSize=14,leading=22,textColor=TEAL,spaceBefore=15,spaceAfter=9,keepWithNext=True,wordWrap='CJK'),
'h3':ParagraphStyle('h3',fontName='Hei',fontSize=11.8,leading=19,textColor=NAVY,spaceBefore=12,spaceAfter=7,keepWithNext=True,wordWrap='CJK'),
'small':ParagraphStyle('small',fontName='Song',fontSize=9,leading=14,textColor=MUTED,spaceAfter=8,wordWrap='CJK'),
'source':ParagraphStyle('source',fontName='Song',fontSize=9,leading=13,textColor=MUTED,spaceAfter=7,wordWrap='CJK',keepWithNext=True),
'cell':ParagraphStyle('cell',fontName='Song',fontSize=9.3,leading=14.4,textColor=INK,wordWrap='CJK',splitLongWords=True),
'thead':ParagraphStyle('thead',fontName='Hei',fontSize=9.4,leading=14.5,textColor=colors.white,wordWrap='CJK'),
'caption':ParagraphStyle('caption',fontName='Song',fontSize=9,leading=14,textColor=MUTED,spaceBefore=5,spaceAfter=12,wordWrap='CJK'),
'qa':ParagraphStyle('qa',fontName='Song',fontSize=10.7,leading=17,textColor=INK,spaceAfter=4,wordWrap='CJK',allowWidows=0,allowOrphans=0),
'qh2':ParagraphStyle('qh2',fontName='Hei',fontSize=13.5,leading=21,textColor=TEAL,spaceBefore=10,spaceAfter=7,keepWithNext=True,wordWrap='CJK'),
}

def inline(s):
    s=html.escape(s)
    s=re.sub(r'\*\*(.+?)\*\*',r'<b>\1</b>',s)
    s=re.sub(r'`([^`]+)`',r'\1',s)
    return s

def para(s,style='body'):
    return Paragraph(inline(s),styles[style])

def catalog():
    raw=SOURCE.read_text(encoding='utf-8')
    chunk=raw.split('### 4.4 ')[1].split('### 4.5 ')[0]
    groups=[]
    for block in chunk.split('<details>')[1:]:
        title=re.search(r'<strong>(.*?)</strong>',block).group(1)
        rows=[]
        for line in block.splitlines():
            if line.startswith('| `'):
                cols=[c.strip() for c in line.strip('|').split('|')]
                name=cols[0].strip('`')
                rows.append((name,cols[1].replace('`',''),cols[2]))
        groups.append((title,rows))
    names=[x[0] for _,rs in groups for x in rs]
    assert len(names)==99 and len(set(names))==99
    assert set(names)==set(WHY), (set(names)-set(WHY),set(WHY)-set(names))
    sourcepdf=PdfReader('D:/Desktop/2026-10-26-企业智能会议助手项目简要方案.pdf')
    srctext=''.join(p.extract_text() for p in sourcepdf.pages)
    flat=re.sub(r'\s+','',srctext)
    assert all(n in flat for n in names),[n for n in names if n not in flat]
    lines=['# 附录一：99张表的逐表讲解','',
      '对应A第18至26页。按A的八领域顺序逐表保留关键字段和业务作用，并追加“为什么这样设计”。字段为概要选录，非完整DDL；例子解释业务意义，不表示现有功能已全部实现。',
      '', '主讲第四章只需按八领域解释，技术答疑时可按表名查询本附录。表名使用项目原名，方便研发与设计对照。', '']
    idx=0
    for title,rows in groups:
        lines += ['## '+title,'']
        for name,fields,role in rows:
            idx+=1
            lines+= [f'### {idx:02d}  {name}','',f'**关键字段：**{fields}。','',f'**业务作用：**{role}','',f'**设计目的与示例：**{WHY[name]}','']
    return '\n'.join(lines),names,groups

appendix,names,groups=catalog()
md=MD.read_text(encoding='utf-8')
if '<!-- APPENDIX_TABLES -->' in md:
    md=md.replace('<!-- APPENDIX_TABLES -->',appendix)
    MD.write_text(md,encoding='utf-8')

class Doc(BaseDocTemplate):
    def __init__(self,path):
        super().__init__(str(path),pagesize=A4,leftMargin=M,rightMargin=M,topMargin=61,bottomMargin=48,
          title='企业智能会议助手｜内部宣讲详细讲解稿',author='项目内部宣讲资料',subject='依据概要方案及具体设计，60分钟主讲、深入解释与问答')
        frame=Frame(M,48,CW,H-109,id='normal',leftPadding=0,rightPadding=0,topPadding=0,bottomPadding=0)
        self.addPageTemplates(PageTemplate(id='main',frames=frame,onPage=self.decorate))
        self.section='使用说明'
        self.records=[]
    def beforeDocument(self):
        self.records=[]
        self.section='使用说明'
    def decorate(self,c,d):
        if d.page==1:
            c.saveState();c.setFillColor(NAVY);c.rect(0,0,W,H,fill=1,stroke=0);c.restoreState();return
        c.saveState()
        c.setFillColor(NAVY);c.setFont('Hei',8.5)
        c.drawString(M,H-29,'企业智能会议助手  /  内部宣讲详细讲解稿')
        c.setFillColor(MUTED);c.setFont('Song',8)
        c.drawRightString(W-M,H-29,'主讲 · 设计目的 · 场景答疑')
        c.setStrokeColor(colors.HexColor('#D6E3E7'));c.setLineWidth(.5);c.line(M,H-39,W-M,H-39)
        c.line(M,36,W-M,36)
        c.setFont('Song',8);c.setFillColor(MUTED)
        c.drawString(M,23,'内部交流资料  |  2026.10.27  |  设计目标须以验证结果确认')
        c.drawRightString(W-M,23,f'{d.page}')
        c.restoreState()
    def afterFlowable(self,f):
        if hasattr(f,'toclevel'):
            key=f.destkey
            self.canv.bookmarkPage(key)
            self.canv.addOutlineEntry(f.getPlainText(),key,level=f.toclevel,closed=False)
            if not re.match(r'^Q\d+',f.getPlainText()):
                self.notify('TOCEntry',(f.toclevel,f.getPlainText(),self.page,key))
            self.records.append({'title':f.getPlainText(),'page':self.page,'level':f.toclevel})

class Cover(Spacer):
    def __init__(self): super().__init__(0,H-120)
    def draw(self):
        c=self.canv
        c.saveState()
        # Origin is bottom of cover flowable.
        c.setFillColor(NAVY);c.rect(-M,-13,W,H,fill=1,stroke=0)
        c.setFillColor(TEAL);c.rect(-M,H-205,10,145,fill=1,stroke=0)
        c.setFillColor(colors.HexColor('#6DD1CF'));c.setFont('Hei',11)
        c.drawString(0,H-151,'AMA  /  INTERNAL PRESENTATION')
        c.setFillColor(colors.white);c.setFont('Hei',30)
        c.drawString(0,H-218,'企业智能会议助手')
        c.setFont('Hei',24);c.drawString(0,H-263,'内部宣讲详细讲解稿')
        c.setFillColor(colors.HexColor('#CCE1E7'));c.setFont('Song',12)
        c.drawString(0,H-305,'依据概要方案与项目具体设计')
        c.drawString(0,H-331,'主讲约60分钟 · 混合观众 · 全流程讲解与答疑')
        c.setStrokeColor(colors.HexColor('#55727F'));c.line(0,H-365,CW,H-365)
        c.setFillColor(colors.white);c.setFont('Hei',14)
        c.drawString(0,H-408,'为什么这样设计，目的是什么？')
        c.setFont('Song',11.5)
        for i,s in enumerate(['功能概要 → 架构设计 → 技术选型','数据库设计 → 分阶段交付','七张原图 · 99张表逐项说明 · 集中问答']):
            c.drawString(0,H-442-27*i,s)
        c.setFont('Song',10);c.setFillColor(colors.HexColor('#CCE1E7'))
        c.drawString(0,77,'编制日期  2026年10月27日')
        c.drawString(0,53,'公司内部交流  /  目标、规则与验证边界分别说明')
        c.restoreState()

toc=TableOfContents()
toc.levelStyles=[
ParagraphStyle('toc0',fontName='Hei',fontSize=12,leading=19,leftIndent=0,firstLineIndent=0,spaceBefore=6,textColor=NAVY,wordWrap='CJK'),
ParagraphStyle('toc1',fontName='Song',fontSize=9.8,leading=15,leftIndent=14,firstLineIndent=0,spaceBefore=1,textColor=INK,wordWrap='CJK')]

story=[Cover(),PageBreak(),para('阅读目录','h1'),para('章节顺序与文档A一致。主讲、深入讲解、现场问答分层阅读；附录支持技术查阅和会后复习。','small'),toc,PageBreak()]
lines=md.splitlines()
start=next(i for i,l in enumerate(lines) if l.startswith('# 使用说明'))
i=start; hid=0; first=True; section=''
while i<len(lines):
    line=lines[i].strip()
    if not line or line.startswith('<!--'):
        i+=1;continue
    if line.startswith('# '):
        if not first:story.append(PageBreak())
        first=False;hid+=1;section=line[2:]
        p=para(line[2:],'h1');p.toclevel=0;p.destkey=f'h{hid}';story.append(p);i+=1;continue
    if line.startswith('## '):
        if section.startswith('附录一') and re.match(r'^## [2-8]\. ',line):story.append(PageBreak())
        hid+=1;p=para(line[3:],'qh2' if section.startswith('附录二') else 'h2');p.toclevel=1;p.destkey=f'h{hid}';story.append(p);i+=1;continue
    if line.startswith('### '):
        if re.match(r'^### \d{2}\s',line):
            block=[para(line[4:],'h3')];i+=1
            while i<len(lines) and not lines[i].startswith('#'):
                content=lines[i].strip()
                if content and not content.startswith('<!--'):block.append(para(content))
                i+=1
            story.append(KeepTogether(block));continue
        story.append(para(line[4:],'h3'));i+=1;continue
    if line.startswith('!['):
        m=re.match(r'!\[(.*?)\]\((.*?)\)',line)
        ip=(MD.parent/m.group(2)).resolve()
        with PILImage.open(ip) as im:iw,ih=im.size
        dw=CW;dh=dw*ih/iw
        if dh>325:dw*=325/dh;dh=325
        story.append(KeepTogether([Image(str(ip),width=dw,height=dh),para(m.group(1)+'。沿用A原图；请结合本节读图讲稿。','caption')]))
        i+=1;continue
    if line.startswith('|'):
        rows=[]
        while i<len(lines) and lines[i].strip().startswith('|'):
            cells=[x.strip() for x in lines[i].strip().strip('|').split('|')]
            if not all(re.fullmatch(r'[:\-\s]+',x) for x in cells):rows.append(cells)
            i+=1
        n=len(rows[0]);widths=[CW/n]*n
        if n==3:widths=[CW*.24,CW*.34,CW*.42]
        if n==2:widths=[CW*.34,CW*.66]
        # Specific source list uses a compact reference code and ample path space.
        if rows[0][0]=='编号':widths=[CW*.07,CW*.59,CW*.34]
        if rows[0][0]=='环节':widths=[CW*.30,CW*.13,CW*.57]
        if rows[0][0]=='领域' and n==3:widths=[CW*.27,CW*.08,CW*.65]
        data=[[para(c,'thead' if ri==0 else 'cell') for c in row] for ri,row in enumerate(rows)]
        tab=Table(data,colWidths=widths,repeatRows=1,hAlign='LEFT',splitByRow=1)
        tab.setStyle(TableStyle([
           ('BACKGROUND',(0,0),(-1,0),NAVY),('VALIGN',(0,0),(-1,-1),'TOP'),
           ('LEFTPADDING',(0,0),(-1,-1),8),('RIGHTPADDING',(0,0),(-1,-1),8),
           ('TOPPADDING',(0,0),(-1,-1),8),('BOTTOMPADDING',(0,0),(-1,-1),8),
           ('ROWBACKGROUNDS',(0,1),(-1,-1),[colors.HexColor('#F0F5F7'),colors.white]),
           ('LINEBELOW',(0,0),(-1,0),.6,TEAL),
           ('LINEBELOW',(0,1),(-1,-1),.3,colors.HexColor('#D9E3E7'))]))
        if rows[0][0]=='环节':
            tab.setStyle(TableStyle([('TOPPADDING',(0,0),(-1,-1),4),('BOTTOMPADDING',(0,0),(-1,-1),4)]))
        story.extend([tab,Spacer(1,10)]);continue
    buf=[line];i+=1
    while i<len(lines) and lines[i].strip() and not lines[i].startswith(('#','|','![','<!--')):
        buf.append(lines[i].strip());i+=1
    text=' '.join(buf)
    st='source' if text.startswith('对应A') else ('qa' if section.startswith('附录二') else 'body')
    story.append(para(text,st))

doc=Doc(PDF)
doc.multiBuild(story)
pdf=PdfReader(str(PDF));alltext='\n'.join(p.extract_text() for p in pdf.pages)
flat=re.sub(r'\s+','',alltext)
missing=[name for name in names if name not in flat]
assert not missing,missing
assert 'APPENDIX_TABLES' not in alltext and '\ufffd' not in alltext
meta={'pages':len(pdf.pages),'chinese_characters':len(re.findall(r'[\u4e00-\u9fff]',md)),
      'tables':len(names),'domains':{title:len(rows) for title,rows in groups},
      'figures':len(re.findall(r'^!\[',md,re.M)), 'central_qa':len(re.findall(r'^## Q\d+',md,re.M)),
      'source_pdf_pages':31,'missing_table_names':missing,'sections':doc.records,
      'pdf_sha256':hashlib.sha256(PDF.read_bytes()).hexdigest()}
(TMP/'verification.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in meta.items() if k not in ('sections','domains')},ensure_ascii=False))
print(str(PDF))

# Compact source diagram sheet, for direct visual comparison.
paths=sorted((ROOT/'docs/reviews/assets/ama-project-brief').glob('*.png'))
thumbs=[]
for path in paths:
    im=PILImage.open(path).convert('RGB');im.thumbnail((560,360))
    tile=PILImage.new('RGB',(600,400),'white');tile.paste(im,((600-im.width)//2,22));ImageDraw.Draw(tile).text((18,380),path.stem,fill='#173247');thumbs.append(tile)
sheet=PILImage.new('RGB',(1200,400*((len(thumbs)+1)//2)),'#dde5e9')
for idx,im in enumerate(thumbs):sheet.paste(im,((idx%2)*600,(idx//2)*400))
sheet.save(TMP/'source-diagrams.jpg')
