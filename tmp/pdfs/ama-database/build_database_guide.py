from pathlib import Path
import re,sys,json,html,hashlib
from collections import Counter,defaultdict
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.platypus import BaseDocTemplate,Frame,PageTemplate,Paragraph,Spacer,PageBreak,Table,TableStyle,KeepTogether,CondPageBreak
from reportlab.platypus.tableofcontents import TableOfContents
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from pypdf import PdfReader
from table_usage import USAGE

TMP=Path(__file__).resolve().parent
ROOT=TMP.parents[2]
sys.path.insert(0,str(TMP.parent/'ama-lecture'))
from table_explanations import WHY
DICT=ROOT/'docs/database/ama-data-dictionary.md'
BRIEF=ROOT/'docs/reviews/2026-10-26-企业智能会议助手项目简要方案.md'
OUT=ROOT/'output/pdf'
PDF=OUT/'企业智能会议助手-数据库设计与逐表应用详解.pdf'
MD=OUT/'企业智能会议助手-数据库设计与逐表应用详解.md'
raw=DICT.read_text(encoding='utf-8')
blocks=re.split(r'^## ',raw,flags=re.M)[2:]
tables={}
for chunk in blocks:
    name=chunk.splitlines()[0].strip()
    fields=[]
    for line in chunk.splitlines():
        if re.match(r'^\|[a-z][a-z0-9_]*\|',line):
            vals=line.strip('|').split('|')
            assert len(vals)==5,(name,line)
            fields.append([v.strip().replace('`','') for v in vals])
    pk=re.search(r'主键：`([^`]+)`',chunk).group(1)
    constraints=[line[2:] for line in chunk.splitlines() if line.startswith('- ')]
    life=chunk.split('更新/删除与完整性：',1)[1].strip()
    desc=chunk.splitlines()[2].strip()
    tables[name]={'fields':fields,'pk':pk,'constraints':constraints,'life':life,'desc':desc}
assert set(tables)==set(USAGE)==set(WHY) and len(tables)==99
source=BRIEF.read_text(encoding='utf-8').split('### 4.4 ')[1].split('### 4.5 ')[0]
groups=[]
for block in source.split('<details>')[1:]:
    title=re.search(r'<strong>(.*?)</strong>',block).group(1)
    names=re.findall(r'^\| `([^`]+)`',block,re.M)
    groups.append((title,names))
assert set(n for _,ns in groups for n in ns)==set(tables)

incoming=defaultdict(set)
for name,t in tables.items():
    for c in t['constraints']:
        if c.startswith('FK '):
            match=re.search(r'→ `([a-z_]+)\(',c)
            if match:incoming[match.group(1)].add(name)

def compact_type(s):
    if 'CHARACTER SET ascii COLLATE ascii_bin' in s:return s.replace(' CHARACTER SET ascii COLLATE ascii_bin',' / A')
    if 'COLLATE utf8mb4_0900_bin' in s:return s.replace(' COLLATE utf8mb4_0900_bin',' / B')
    if 'COLLATE utf8mb4_0900_ai_ci' in s:return s.replace(' COLLATE utf8mb4_0900_ai_ci',' / C')
    if re.match(r'(?:longtext|text|varchar|char)',s):return s+' / C'
    return s

add=[];idx=0
for gi,(title,names) in enumerate(groups,1):
    add+=['# '+str(4+gi)+'. '+title.split('. ',1)[-1],'']
    add+=['本领域包含：'+'、'.join(names)+'。','']
    for n in names:
        idx+=1;t=tables[n];u=USAGE[n]
        add += [f'## {idx:02d} {n}｜{u["title"]}','',
           '**功能与设计归属：**'+t['desc'],'',
           '**记录粒度：**主键为 '+t['pk']+'；该主键组合唯一识别一行，详细重复边界由下文唯一约束补充。','',
           '**用在什么地方：**'+u['where'],'',
           '**何时写入或更新：**'+u['write'],'',
           '**谁读取、读取后做什么：**'+u['read'],'',
           '**为什么这样设计：**'+WHY[n],'',
           '**省略或混用的后果：**'+u['risk'],'',
           '### 完整字段与业务含义','',
           '| 字段 | 类型 | 空／默认 | 含义 |','| --- | --- | --- | --- |']
        for f in t['fields']:
            add.append('| '+f[0]+' | '+compact_type(f[1])+' | '+('可空' if f[2]=='是' else '非空')+'／'+f[3]+' | '+f[4]+' |')
        add+=['','### 关联、索引与完整性','',
          '**主键：**'+t['pk']+'。','']
        if incoming[n]:add+=['**被哪些表直接引用：**'+'、'.join(sorted(incoming[n]))+'。此列表仅由字典的显式外键反向整理，不包含所有逻辑指针。','']
        else:add+=['**直接反向引用：**当前字典未声明其他表指向本表的物理外键；业务读取或逻辑引用仍以本节使用流程为准。','']
        for c in t['constraints']:
            add += [c,'']
        if not t['constraints']:add+=['当前字典未列其他显式索引、唯一键、CHECK或外键；仍须遵守所属领域的权限、状态和逻辑完整性约束。','']
        add+=['### 更新、删除与历史边界','',t['life'],'']
intro=(TMP/'guide.md').read_text(encoding='utf-8')
md=intro.replace('<!-- TABLES -->','\n'.join(add))
MD.write_text(md,encoding='utf-8')

F=Path('C:/Windows/Fonts')
pdfmetrics.registerFont(TTFont('DBSong',str(F/'simsun.ttc'),subfontIndex=0))
pdfmetrics.registerFont(TTFont('DBHei',str(F/'simhei.ttf')))
pdfmetrics.registerFontFamily('DBSong',normal='DBSong',bold='DBHei',italic='DBSong',boldItalic='DBHei')
pdfmetrics.registerFontFamily('DBHei',normal='DBHei',bold='DBHei',italic='DBHei',boldItalic='DBHei')
W,H=A4;M=45;CW=W-2*M
NAVY=colors.HexColor('#16364A');TEAL=colors.HexColor('#007D86');INK=colors.HexColor('#223540');GREY=colors.HexColor('#627681')
ST={
'p':ParagraphStyle('p',fontName='DBSong',fontSize=10.5,leading=16.5,spaceAfter=6,textColor=INK,wordWrap='CJK',allowWidows=0,allowOrphans=0),
'small':ParagraphStyle('small',fontName='DBSong',fontSize=9,leading=13.5,spaceAfter=6,textColor=GREY,wordWrap='CJK'),
'h1':ParagraphStyle('h1',fontName='DBHei',fontSize=20,leading=28,spaceAfter=14,keepWithNext=True,textColor=NAVY,wordWrap='CJK'),
'h2':ParagraphStyle('h2',fontName='DBHei',fontSize=14,leading=21,spaceBefore=12,spaceAfter=10,keepWithNext=True,textColor=TEAL,wordWrap='CJK'),
'h3':ParagraphStyle('h3',fontName='DBHei',fontSize=11.5,leading=18,spaceBefore=11,spaceAfter=6,keepWithNext=True,textColor=NAVY,wordWrap='CJK'),
'cell':ParagraphStyle('cell',fontName='DBSong',fontSize=9,leading=13.2,textColor=INK,wordWrap='CJK',splitLongWords=True),
'th':ParagraphStyle('th',fontName='DBHei',fontSize=9,leading=13.2,textColor=colors.white,wordWrap='CJK'),
'constraint':ParagraphStyle('constraint',fontName='DBSong',fontSize=9.1,leading=14,spaceAfter=6,textColor=INK,wordWrap='CJK',splitLongWords=True),
}

def inline(s):
    s=html.escape(s)
    s=re.sub(r'\*\*(.+?)\*\*',r'<b>\1</b>',s)
    s=re.sub(r'`([^`]+)`',r'<font color="#285667">\1</font>',s)
    return s
def P(s,st='p'):return Paragraph(inline(s),ST[st])

class Doc(BaseDocTemplate):
    def __init__(self):
        super().__init__(str(PDF),pagesize=A4,leftMargin=M,rightMargin=M,topMargin=55,bottomMargin=47,
          title='企业智能会议助手数据库设计与逐表应用详解',author='项目内部技术讲解资料')
        frame=Frame(M,47,CW,H-102,id='main',leftPadding=0,rightPadding=0,topPadding=0,bottomPadding=0)
        self.addPageTemplates(PageTemplate(id='main',frames=frame,onPage=self.decor))
        self.records=[];self.current_table=''
    def beforeDocument(self):self.records=[];self.current_table=''
    def decor(self,c,d):
        c.saveState()
        if d.page==1:
            c.setFillColor(NAVY);c.rect(0,0,W,H,fill=1,stroke=0)
            c.setFillColor(TEAL);c.rect(0,H-275,10,190,fill=1,stroke=0)
            c.setFillColor(colors.HexColor('#85D4D3'));c.setFont('DBHei',11);c.drawString(M,H-115,'AMA  /  DATABASE DESIGN')
            c.setFillColor(colors.white);c.setFont('DBHei',28);c.drawString(M,H-179,'企业智能会议助手')
            c.setFont('DBHei',26);c.drawString(M,H-225,'数据库设计与逐表应用详解')
            c.setFillColor(colors.HexColor('#D8E6EC'));c.setFont('DBSong',12)
            for y,s in [(277,'99张表的功能、使用位置、读写流程与设计理由'),(309,'完整字段 · 关联与约束 · 业务链路 · 故障案例')]:c.drawString(M,H-y,s)
            c.setStrokeColor(colors.HexColor('#6B8794'));c.line(M,H-349,W-M,H-349)
            c.setFillColor(colors.white);c.setFont('DBHei',16);c.drawString(M,H-395,'每张表在哪里用，为什么需要它？')
            c.setFont('DBSong',11.5)
            for j,s in enumerate(['从页面操作讲到后台处理，从业务事实讲到数据约束。','逐表讲清创建、读取、更新、失效及相邻表的协作关系。','设计基线：B00-1.7-publication-submission']):c.drawString(M,H-436-j*27,s)
            c.setFillColor(colors.HexColor('#C4D8E1'));c.setFont('DBSong',10)
            c.drawString(M,104,'2026年10月27日  |  公司内部讲解与研发评审')
            c.drawString(M,79,'完整设计模型，不等同于当前生产部署或验收结论。')
        else:
            c.setFont('DBHei',8.3);c.setFillColor(NAVY);c.drawString(M,H-26,'企业智能会议助手  /  数据库设计与逐表应用详解')
            c.setFont('DBSong',8);c.setFillColor(GREY)
            label='99表 · 用途 · 流程 · 设计理由'
            c.drawRightString(W-M,H-26,label)
            c.setStrokeColor(colors.HexColor('#D8E3E8'));c.line(M,H-35,W-M,H-35);c.line(M,35,W-M,35)
            c.setFont('DBSong',8);c.drawString(M,22,'内部技术资料  |  B00-1.7设计口径  |  2026.10.27');c.drawRightString(W-M,22,str(d.page))
        c.restoreState()
    def afterFlowable(self,f):
        if hasattr(f,'anchor'):
            self.canv.bookmarkPage(f.anchor)
            self.canv.addOutlineEntry(f.getPlainText(),f.anchor,level=f.level,closed=f.level==0)
            self.notify('TOCEntry',(f.level,f.getPlainText(),self.page,f.anchor))
            self.records.append({'title':f.getPlainText(),'page':self.page,'level':f.level})
            if hasattr(f,'table_name'):self.current_table=f.table_name
            elif f.level==0:self.current_table=''

toc=TableOfContents();toc.levelStyles=[
ParagraphStyle('toc0',fontName='DBHei',fontSize=11,leading=18,spaceBefore=7,textColor=NAVY,wordWrap='CJK'),
ParagraphStyle('toc1',fontName='DBSong',fontSize=9,leading=14.2,spaceBefore=1,leftIndent=12,textColor=INK,wordWrap='CJK')]
story=[Spacer(1,20),PageBreak(),P('阅读目录','h1'),P('可按业务链路顺序阅读，也可通过表名及中文功能定位。PDF同时提供书签。','small'),toc,PageBreak()]
lines=md.splitlines();i=next(i for i,l in enumerate(lines) if l.startswith('# 1.'))
first=True;aid=0;is_table=False
while i<len(lines):
    l=lines[i].strip()
    if not l:i+=1;continue
    if l.startswith('# '):
        if not first:story.append(PageBreak())
        first=False;is_table=False;aid+=1;p=P(l[2:],'h1');p.anchor=f'a{aid}';p.level=0;story.append(p);i+=1;continue
    if l.startswith('## '):
        mt=re.match(r'## \d{2} ([a-z_]+)｜',l)
        if mt:
            # Start a new card only where its heading and usage explanation have ample room.
            # Allow a preceding card's short lifecycle tail to share the page with the next card.
            if is_table:story.append(CondPageBreak(260))
            is_table=True
        aid+=1;p=P(l[3:],'h2');p.anchor=f'a{aid}';p.level=1
        if mt:p.table_name=mt.group(1)
        story.append(p);i+=1;continue
    if l.startswith('### '):story.append(P(l[4:],'h3'));i+=1;continue
    if l.startswith('|'):
        rows=[]
        while i<len(lines) and lines[i].strip().startswith('|'):
            row=[v.strip() for v in lines[i].strip().strip('|').split('|')]
            if not all(re.fullmatch(r'[\s:\-]+',v) for v in row):rows.append(row)
            i+=1
        n=len(rows[0]);widths=[CW/n]*n
        if n==4:widths=[CW*.27,CW*.22,CW*.13,CW*.38]
        if n==3:widths=[CW*.23,CW*.40,CW*.37]
        if n==2:widths=[CW*.51,CW*.49]
        if rows[0][0]=='领域':widths=[CW*.23,CW*.07,CW*.35,CW*.35]
        table=Table([[P(v,'th' if ri==0 else 'cell') for v in r] for ri,r in enumerate(rows)],colWidths=widths,repeatRows=1,hAlign='LEFT')
        table.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),NAVY),('VALIGN',(0,0),(-1,-1),'TOP'),
          ('ROWBACKGROUNDS',(0,1),(-1,-1),[colors.HexColor('#EEF4F6'),colors.white]),
          ('LEFTPADDING',(0,0),(-1,-1),7),('RIGHTPADDING',(0,0),(-1,-1),7),
          ('TOPPADDING',(0,0),(-1,-1),6),('BOTTOMPADDING',(0,0),(-1,-1),6),
          ('LINEBELOW',(0,0),(-1,-1),.3,colors.HexColor('#CFDEE3'))]))
        story.extend([table,Spacer(1,9)]);continue
    buf=[l];i+=1
    while i<len(lines) and lines[i].strip() and not lines[i].startswith(('#','|')):buf.append(lines[i].strip());i+=1
    txt=' '.join(buf)
    st='constraint' if txt.startswith(('FK ','CHECK ','唯一约束 ','索引 ')) else 'p'
    story.append(P(txt,st))

doc=Doc();doc.multiBuild(story)
reader=PdfReader(PDF)
text='\n'.join(p.extract_text() for p in reader.pages)
flat=re.sub(r'\s+','',text)
assert all(n in flat for n in tables)
assert '\ufffd' not in text and '<!--' not in text
assert sum(1 for r in doc.records if re.match(r'^\d{2} [a-z_]+｜',r['title']))==99
field_count=sum(len(t['fields']) for t in tables.values())
checks={
 'table_count':len(tables),'field_count':field_count,'explicit_constraint_lines':sum(len(t['constraints']) for t in tables.values()),
 'pages':len(reader.pages),'chinese_chars':len(re.findall(r'[\u4e00-\u9fff]',md)),
 'source_dictionary_sha256':hashlib.sha256(DICT.read_bytes()).hexdigest(),
 'pdf_sha256':hashlib.sha256(PDF.read_bytes()).hexdigest(),
 'tables':[{'name':n,'fields':len(tables[n]['fields']),'usage_complete':all(USAGE[n].values())} for n in tables],
 'headings':doc.records,
}
(TMP/'verification.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in checks.items() if k not in ('tables','headings')},ensure_ascii=False))
print(str(PDF))
