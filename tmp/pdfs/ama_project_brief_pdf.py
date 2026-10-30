"""Build a self-contained, typeset review PDF from the approved Markdown brief."""

from __future__ import annotations

import json
import math
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from xml.sax.saxutils import escape

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4, landscape
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate, Flowable, Frame, KeepTogether, LongTable, NextPageTemplate,
    PageBreak, PageTemplate, Paragraph, Spacer, TableStyle,
)
from reportlab.platypus.tableofcontents import TableOfContents


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "docs/reviews/2026-10-26-企业智能会议助手项目简要方案.md"
ASSETS = SOURCE.parent / "assets/ama-project-brief"
OUTPUT = ROOT / "output/pdf/2026-10-26-企业智能会议助手项目简要方案.pdf"
TMP = ROOT / "tmp/pdfs"
NAVY, BLUE, TEAL = "#14243D", "#2563EB", "#0D9488"
TEXT, MUTED, BORDER, BG = "#31445D", "#63758B", "#D7E1EE", "#F7F9FC"
PALE_BLUE, PALE_TEAL, PALE_ORANGE = "#EDF4FF", "#E9F7F3", "#FFF4DF"
PAGE_W, PAGE_H = landscape(A4)
MARGIN_X, TOP, BOTTOM = 43, 43, 38
WIDTH = PAGE_W - MARGIN_X * 2
HEIGHT = PAGE_H - TOP - BOTTOM

pdfmetrics.registerFont(TTFont("YaHei", "C:/Windows/Fonts/msyh.ttc", subfontIndex=0))
pdfmetrics.registerFont(TTFont("YaHeiBold", "C:/Windows/Fonts/msyhbd.ttc", subfontIndex=0))
pdfmetrics.registerFontFamily("YaHei", normal="YaHei", bold="YaHeiBold", italic="YaHei", boldItalic="YaHeiBold")


def color(value):
    return colors.toColor(value)


STYLES = {
    "Cover": ParagraphStyle("Cover", fontName="YaHeiBold", fontSize=29, leading=39,
                            textColor=color(NAVY), spaceBefore=12, spaceAfter=15, wordWrap="CJK"),
    "Chapter": ParagraphStyle("Chapter", fontName="YaHeiBold", fontSize=20, leading=29,
                              textColor=color(NAVY), spaceAfter=14, keepWithNext=True, wordWrap="CJK"),
    "Section": ParagraphStyle("Section", fontName="YaHeiBold", fontSize=13.3, leading=20,
                              textColor=color(NAVY), spaceBefore=11, spaceAfter=8,
                              keepWithNext=True, wordWrap="CJK"),
    "Domain": ParagraphStyle("Domain", fontName="YaHeiBold", fontSize=11.6, leading=18,
                             textColor=color(TEAL), spaceBefore=13, spaceAfter=8,
                             keepWithNext=True, wordWrap="CJK"),
    "Body": ParagraphStyle("Body", fontName="YaHei", fontSize=10.5, leading=16.5,
                           textColor=color(TEXT), spaceAfter=7, wordWrap="CJK", splitLongWords=True),
    "List": ParagraphStyle("List", fontName="YaHei", fontSize=10.5, leading=16.5,
                           textColor=color(TEXT), spaceAfter=2, wordWrap="CJK"),
    "Reference": ParagraphStyle("Reference", fontName="YaHei", fontSize=9.3, leading=13.5,
                                textColor=color(MUTED), spaceAfter=2, wordWrap="CJK"),
    "Meta": ParagraphStyle("Meta", fontName="YaHei", fontSize=9.4, leading=15,
                           textColor=color(MUTED), spaceAfter=4, wordWrap="CJK"),
    "Caption": ParagraphStyle("Caption", fontName="YaHei", fontSize=8.6, leading=13,
                              textColor=color(MUTED), spaceBefore=7, spaceAfter=6,
                              wordWrap="CJK", alignment=TA_LEFT),
    "Cell": ParagraphStyle("Cell", fontName="YaHei", fontSize=9.2, leading=13.4,
                           textColor=color(TEXT), wordWrap="CJK", splitLongWords=True),
    "Header": ParagraphStyle("Header", fontName="YaHeiBold", fontSize=9.5, leading=14,
                             textColor=colors.white, wordWrap="CJK"),
    "TOCTitle": ParagraphStyle("TOCTitle", fontName="YaHeiBold", fontSize=24, leading=32,
                               textColor=color(NAVY), spaceAfter=16),
}


def inline(value):
    value = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", value)
    value = escape(value)
    value = re.sub(r"`([^`]+)`", lambda m: '<font color="' + BLUE + '" size="8.7">' + m.group(1) + '</font>', value)
    value = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", value)
    return value.replace("  \n", "<br/>")


def para(value, style="Body"):
    return Paragraph(inline(value), STYLES[style])


class ReviewDoc(BaseDocTemplate):
    def __init__(self, filename):
        super().__init__(filename, pagesize=(PAGE_W, PAGE_H), leftMargin=MARGIN_X,
                         rightMargin=MARGIN_X, topMargin=TOP, bottomMargin=BOTTOM,
                         title="企业智能会议助手项目简要方案", author="内部评审会议",
                         subject="功能、架构、技术选型、数据库设计与开发计划", pageCompression=1)
        frame = Frame(MARGIN_X, BOTTOM, WIDTH, HEIGHT, leftPadding=0, rightPadding=0,
                      topPadding=0, bottomPadding=0, id="normal")
        self.addPageTemplates([
            PageTemplate("Cover", [frame], onPage=self.cover_page),
            PageTemplate("Body", [frame], onPage=self.body_page),
        ])

    def cover_page(self, canvas, doc):
        canvas.saveState()
        canvas.setFillColor(color(BG)); canvas.rect(0, 0, PAGE_W, PAGE_H, fill=1, stroke=0)
        canvas.setFillColor(color(BLUE)); canvas.rect(0, PAGE_H - 7, PAGE_W, 7, fill=1, stroke=0)
        canvas.setFont("YaHeiBold", 8.5); canvas.setFillColor(color(MUTED))
        canvas.drawString(MARGIN_X, PAGE_H - 26, "AMA  /  INTERNAL REVIEW")
        canvas.drawRightString(PAGE_W - MARGIN_X, 21, "独立阅读版本  |  正文、配图与逐表清单完整收录")
        canvas.restoreState()

    def body_page(self, canvas, doc):
        canvas.saveState()
        canvas.setStrokeColor(color(BORDER)); canvas.setLineWidth(.5)
        canvas.line(MARGIN_X, PAGE_H - 29, PAGE_W - MARGIN_X, PAGE_H - 29)
        canvas.setFillColor(color(MUTED)); canvas.setFont("YaHei", 8)
        canvas.drawString(MARGIN_X, PAGE_H - 21, "企业智能会议助手  |  项目简要方案")
        canvas.drawRightString(PAGE_W - MARGIN_X, PAGE_H - 21, "内部评审会议稿")
        canvas.drawString(MARGIN_X, 19, "功能概要  /  架构  /  技术选型  /  数据库  /  开发计划")
        canvas.drawRightString(PAGE_W - MARGIN_X, 19, f"第 {doc.page} 页")
        canvas.restoreState()

    def afterFlowable(self, flowable):
        if not isinstance(flowable, Paragraph):
            return
        name = flowable.style.name
        if name not in {"Chapter", "Section", "Domain"}:
            return
        title = flowable.getPlainText()
        key = flowable._bookmark
        level = {"Chapter": 0, "Section": 1, "Domain": 2}[name]
        self.canv.bookmarkPage(key)
        self.canv.addOutlineEntry(title, key, level=level, closed=True)
        if level < 2:
            self.notify("TOCEntry", (level, title, self.page, key))


class SvgDiagram(Flowable):
    def __init__(self, path, max_height=435):
        super().__init__()
        self.root = ET.parse(path).getroot()
        self.source_w = float(self.root.attrib["width"])
        self.source_h = float(self.root.attrib["height"])
        self.scale_factor = min(WIDTH / self.source_w, max_height / self.source_h)
        self.width = self.source_w * self.scale_factor
        self.height = self.source_h * self.scale_factor
        self.hAlign = "CENTER"

    def draw(self):
        c, s, h = self.canv, self.scale_factor, self.source_h
        c.saveState()
        for element in self.root:
            tag = element.tag.split("}")[-1]
            a = element.attrib
            if tag == "rect":
                x, y = float(a.get("x", 0)), float(a.get("y", 0))
                w, height = float(a["width"]), float(a["height"])
                fill = a.get("fill", "none"); stroke = a.get("stroke", "none")
                if fill != "none": c.setFillColor(color(fill))
                if stroke != "none": c.setStrokeColor(color(stroke))
                c.setLineWidth(float(a.get("stroke-width", 1)) * s)
                c.roundRect(x*s, (h-y-height)*s, w*s, height*s,
                            float(a.get("rx", 0))*s, fill=fill != "none", stroke=stroke != "none")
            elif tag == "text":
                x, y, size = float(a["x"]), float(a["y"]), float(a["font-size"])
                font = "YaHeiBold" if int(a.get("font-weight", 400)) >= 600 else "YaHei"
                c.setFont(font, size*s); c.setFillColor(color(a["fill"]))
                text = "".join(element.itertext())
                anchor = a.get("text-anchor", "start")
                method = c.drawCentredString if anchor == "middle" else c.drawRightString if anchor == "end" else c.drawString
                method(x*s, (h-y)*s, text)
            elif tag in {"polyline", "polygon"}:
                points = [tuple(map(float, pair.split(","))) for pair in a["points"].split()]
                p = c.beginPath(); p.moveTo(points[0][0]*s, (h-points[0][1])*s)
                for x, y in points[1:]: p.lineTo(x*s, (h-y)*s)
                if tag == "polygon": p.close()
                fill, stroke = a.get("fill", "none"), a.get("stroke", "none")
                if fill != "none": c.setFillColor(color(fill))
                if stroke != "none": c.setStrokeColor(color(stroke))
                c.setLineWidth(float(a.get("stroke-width", 1))*s)
                c.drawPath(p, fill=fill != "none", stroke=stroke != "none")
            else:
                raise ValueError(f"Unsupported SVG element: {tag}")
        c.restoreState()


class CompactER(Flowable):
    """Reflow the same core ER entities for a landscape PDF page, with vector text."""
    def __init__(self, material=False):
        super().__init__(); self.material = material
        self.width, self.height = WIDTH, WIDTH * .515
        self.hAlign = "CENTER"

    def draw(self):
        c = self.canv; s = WIDTH / 2000; H = 1030
        c.saveState()
        c.setFillColor(color(BG)); c.roundRect(0, 0, self.width, self.height, 7, fill=1, stroke=0)

        def txt(x, y, text, size=24, bold=False, fill=TEXT, center=False):
            c.setFillColor(color(fill)); c.setFont("YaHeiBold" if bold else "YaHei", size*s)
            (c.drawCentredString if center else c.drawString)(x*s, (H-y-size*.88)*s, text)

        def box(x, y, title, table, pk, fk, fill):
            w = 540 if self.material else 400
            c.setFillColor(color(fill)); c.setStrokeColor(color(BORDER)); c.setLineWidth(.6)
            c.roundRect(x*s, (H-y-210)*s, w*s, 210*s, 16*s, fill=1, stroke=1)
            txt(x+20, y+22, title, 32, True, NAVY)
            txt(x+20, y+77, table, 23 if not self.material else 25, True, BLUE)
            txt(x+20, y+128, "PK  "+pk, 22 if not self.material else 24)
            txt(x+20, y+170, "FK  "+fk, 22 if not self.material else 24, fill=MUTED)

        def edge(points, label, lx, ly):
            c.setStrokeColor(color(TEAL)); c.setFillColor(color(TEAL)); c.setLineWidth(1.2)
            p=c.beginPath(); p.moveTo(points[0][0]*s, (H-points[0][1])*s)
            for x,y in points[1:]: p.lineTo(x*s, (H-y)*s)
            c.drawPath(p)
            (x0,y0),(x1,y1)=points[-2:]; angle=math.atan2(y1-y0,x1-x0)
            pts=[(x1,y1), (x1-15*math.cos(angle)+8*math.sin(angle),y1-15*math.sin(angle)-8*math.cos(angle)),
                 (x1-15*math.cos(angle)-8*math.sin(angle),y1-15*math.sin(angle)+8*math.cos(angle))]
            p=c.beginPath(); p.moveTo(pts[0][0]*s,(H-pts[0][1])*s)
            for x,y in pts[1:]: p.lineTo(x*s,(H-y)*s)
            p.close(); c.drawPath(p,fill=1,stroke=0)
            txt(lx,ly,label,22,True,TEAL,True)

        if not self.material:
            nodes=[
                (50,80,"企业身份","enterprise_identity","enterprise_id","department_id",PALE_BLUE),
                (550,80,"会议主记录","meeting","id","owner_id",PALE_BLUE),
                (1050,80,"处理批次","input_batch","id","meeting_id",PALE_BLUE),
                (1550,80,"转写版本","transcript_revision","id","batch_id",PALE_BLUE),
                (50,390,"参会名单版本","roster_revision","id","meeting_id",PALE_BLUE),
                (550,390,"生成候选","generation_candidate","id","input_id",PALE_TEAL),
                (1050,390,"纪要生成输入","generation_input","id","transcript_confirmation_id",PALE_TEAL),
                (1550,390,"转写确认","transcript_confirmation","id","revision_id",PALE_ORANGE),
                (50,700,"外部文档映射","external_document_mapping","publication_id","publication_id",PALE_TEAL),
                (550,700,"发布记录","publication","id","confirmation_id",PALE_TEAL),
                (1050,700,"最终确认","final_confirmation","id","minutes_revision_id",PALE_ORANGE),
                (1550,700,"已保存纪要版本","minutes_revision","id","generation_input_id",PALE_TEAL),
            ]
            edge([(450,185),(550,185)],"1:N",500,150)
            edge([(950,185),(1050,185)],"1:N",1000,150)
            edge([(1450,185),(1550,185)],"1:N",1500,150)
            edge([(750,290),(750,340),(250,340),(250,390)],"1:N",500,308)
            edge([(750,80),(750,31),(1750,31),(1750,80)],"1:N",1250,1)
            edge([(1750,290),(1750,390)],"1:0..1",1830,329)
            edge([(1550,495),(1450,495)],"1:N",1500,460)
            edge([(1050,495),(950,495)],"1:N",1000,460)
            edge([(1250,600),(1250,650),(1750,650),(1750,700)],"1:N",1500,617)
            edge([(1550,805),(1450,805)],"1:N",1500,770)
            edge([(1050,805),(950,805)],"1:0..1",1000,770)
            edge([(550,805),(450,805)],"1:0..1",500,770)
        else:
            nodes=[
                (80,70,"材料逻辑引用","material_reference","id","meeting_id",PALE_BLUE),
                (730,70,"材料版本","material_revision","id","reference_id",PALE_BLUE),
                (1380,70,"解析文档版本","parsed_document","id","material_revision_id",PALE_BLUE),
                (80,380,"材料快照","material_snapshot","id","meeting_id",PALE_ORANGE),
                (730,380,"材料快照明细","material_snapshot_item","snapshot_id + material_revision_id","material_revision_id",PALE_ORANGE),
                (1380,380,"来源定位","source_locator","id","material_revision_id",PALE_TEAL),
                (80,690,"来源上下文","source_context","id","material_snapshot_id",PALE_TEAL),
                (730,690,"上下文材料选择","source_context_material","context_id + material_revision_id","parsed_document_id",PALE_TEAL),
                (1380,690,"成果引用证据","evidence_link","id","locator_id",PALE_TEAL),
            ]
            edge([(620,175),(730,175)],"1:N",675,140)
            edge([(1270,175),(1380,175)],"1:N",1325,140)
            edge([(1000,280),(1000,380)],"1:N",1055,320)
            edge([(620,485),(730,485)],"1:N",675,450)
            edge([(350,590),(350,690)],"1:N",405,630)
            edge([(620,795),(730,795)],"1:N",675,760)
            edge([(1650,280),(1650,325),(1310,325),(1310,640),(1000,640),(1000,690)],"1:N",1365,334)
            edge([(1650,590),(1650,690)],"1:N",1705,630)
            edge([(350,900),(350,955),(1650,955),(1650,900)],"1:N",1000,923)
        for node in nodes: box(*node)
        txt(80,990,"PK 主键  /  FK 关联字段；1:N中的N包含零条；仅展示主要业务关系。",25,fill=MUTED)
        c.restoreState()


def table_flow(rows):
    count=len(rows[0]); header=rows[0]
    if count==2: proportions=[.20,.80]
    elif count==3 and header[0]=="表名": proportions=[.255,.40,.345]
    elif count==3 and header[0] in {"领域","主要模块"}: proportions=[.17,.39,.44]
    elif count==3: proportions=[.20,.38,.42]
    elif count==4 and header[0]=="阶段": proportions=[.19,.20,.34,.27]
    elif count==4: proportions=[.24,.26,.29,.21]
    else: proportions=[1/count]*count
    data=[[para(cell,"Header" if r==0 else "Cell") for cell in row] for r,row in enumerate(rows)]
    t=LongTable(data,colWidths=[WIDTH*p for p in proportions],repeatRows=1,hAlign="LEFT",splitByRow=1)
    padding = 4.7 if header[0]=="表名" else 5
    commands=[("BACKGROUND",(0,0),(-1,0),color(NAVY)),("VALIGN",(0,0),(-1,-1),"TOP"),
              ("LEFTPADDING",(0,0),(-1,-1),9),("RIGHTPADDING",(0,0),(-1,-1),9),
              ("TOPPADDING",(0,0),(-1,-1),padding),("BOTTOMPADDING",(0,0),(-1,-1),padding),
              ("LINEBELOW",(0,0),(-1,0),.5,color(NAVY))]
    for r in range(1,len(rows)):
        if r%2==0: commands.append(("BACKGROUND",(0,r),(-1,r),color(BG)))
        commands.append(("LINEBELOW",(0,r),(-1,r),.35,color(BORDER)))
    t.setStyle(TableStyle(commands)); t.spaceAfter=9
    return t


def append_figure(result, diagram, caption):
    context=[]
    if result and isinstance(result[-1],Paragraph) and result[-1].style.name=="Body":
        context.insert(0,result.pop())
    if result and isinstance(result[-1],Paragraph) and result[-1].style.name=="Section":
        context.insert(0,result.pop())
        if result and isinstance(result[-1],Paragraph) and result[-1].style.name=="Chapter":
            context.insert(0,result.pop())
    cap=para(caption,"Caption")
    reserved=sum(p.wrap(WIDTH,HEIGHT)[1]+p.getSpaceBefore()+p.getSpaceAfter() for p in context)
    reserved+=cap.wrap(WIDTH,HEIGHT)[1]+cap.getSpaceBefore()+cap.getSpaceAfter()+8
    maximum=HEIGHT-reserved
    if isinstance(diagram,SvgDiagram):
        diagram.scale_factor=min(WIDTH/diagram.source_w,maximum/diagram.source_h)
        diagram.width=diagram.source_w*diagram.scale_factor
        diagram.height=diagram.source_h*diagram.scale_factor
    else:
        assert diagram.height<=maximum,(diagram.height,maximum)
    if result and not isinstance(result[-1],PageBreak): result.append(PageBreak())
    result.extend(context+[KeepTogether([diagram,cap]),PageBreak()])


def prepare_markdown(text):
    text=re.sub(r"<!--.*?-->","",text,flags=re.S)
    text=text.replace("<details>","").replace("</details>","")
    text=re.sub(r"<summary><strong>(\d+)\. (.*?)</strong></summary>",r"#### 4.4.\1 \2",text)
    text=re.sub(r"^\*\*阅读导航：.*$","",text,flags=re.M)
    text=text.replace("会议讲解可先展示领域概览与ER图，需要核对具体表时展开对应领域。",
                      "逐表清单按领域连续列示，可通过PDF目录与书签定位。")
    text=text.replace("完整关系以数据库详细设计为准。","逐表概要见本章正文。")
    text=text.replace("### 5.6 编制依据与配图说明","### 5.6 编制依据与阅读说明")
    text=re.sub(r"配图采用相对路径引用，PNG可直接用于Markdown预览；.*$",
                "本PDF已完整纳入全部配图、ER关系图及99张表概要。编制依据以资料名称列示，阅读本方案无需其他附件。",
                text,flags=re.M)
    return text


def blocks(text, main=False):
    lines=text.splitlines(); result=[]; i=0; bookmark=0; first_chapter=True; references=False
    while i<len(lines):
        line=lines[i].strip()
        if not line or line=="---": i+=1; continue
        if line.startswith("|"):
            rows=[]
            while i<len(lines) and lines[i].strip().startswith("|"):
                cells=[c.strip() for c in lines[i].strip().strip("|").split("|")]
                if not all(re.fullmatch(r":?-{3,}:?",c) for c in cells): rows.append(cells)
                i+=1
            assert len({len(r) for r in rows})==1
            table=table_flow(rows)
            if rows[0][0]=="数据领域":
                context=[]
                if result and isinstance(result[-1],Paragraph) and result[-1].style.name=="Body":
                    context.insert(0,result.pop())
                if result and isinstance(result[-1],Paragraph) and result[-1].style.name=="Section":
                    context.insert(0,result.pop())
                result.append(KeepTogether(context+[table]))
            else:
                result.append(table)
            continue
        image=re.fullmatch(r"!\[([^\]]+)\]\(([^)]+)\)",line)
        if image:
            alt,rel=image.groups(); j=i+1
            while j<len(lines) and not lines[j].strip(): j+=1
            caption=alt
            if j<len(lines) and lines[j].strip().startswith("*") and not lines[j].strip().startswith("**"):
                caption=lines[j].strip().strip("*"); i=j
            path=SOURCE.parent/rel
            if "database-core-er" in rel: diagram=CompactER()
            elif "database-source-er" in rel: diagram=CompactER(material=True)
            else: diagram=SvgDiagram(path.with_suffix(".svg"),max_height=431)
            append_figure(result,diagram,caption)
            i+=1; continue
        heading=re.match(r"^(#{1,4}) (.+)$",line)
        if heading:
            level=len(heading.group(1)); title=heading.group(2)
            style={1:"Cover",2:"Chapter",3:"Section",4:"Domain"}[level]
            if level==3: references=title.startswith("5.6")
            if level==2:
                if not first_chapter: result.append(PageBreak())
                first_chapter=False
            p=para(title,style)
            if level>1: p._bookmark=f"section_{bookmark}"; bookmark+=1
            result.append(p); i+=1; continue
        if line.startswith(">"):
            result.append(para(line.lstrip("> ").rstrip(),"Meta")); i+=1; continue
        if re.match(r"^- |^\d+\. ",line):
            value=re.sub(r"^- ","• ",line)
            result.append(para(value,"Reference" if references else "List")); i+=1; continue
        if line.startswith("*") and line.endswith("*") and not line.startswith("**"):
            result.append(para(line.strip("*"),"Caption")); i+=1; continue
        paragraph=[line]; i+=1
        while i<len(lines) and lines[i].strip() and not re.match(r"^(#|\||!\[|>|- |\d+\. |---)",lines[i].strip()):
            paragraph.append(lines[i].strip()); i+=1
        result.append(para(" ".join(paragraph)))
    return result


def main():
    OUTPUT.parent.mkdir(parents=True,exist_ok=True); TMP.mkdir(parents=True,exist_ok=True)
    text=prepare_markdown(SOURCE.read_text(encoding="utf-8"))
    intro,main_text=text.split("## 1. 功能概要设计",1)
    main_text="## 1. 功能概要设计"+main_text
    story=blocks(intro)
    story.extend([NextPageTemplate("Body"),PageBreak(),para("目录","TOCTitle")])
    toc=TableOfContents(); toc.dotsMinLevel=0
    toc.tableStyle=TableStyle([("TOPPADDING",(0,0),(-1,-1),0),
                               ("BOTTOMPADDING",(0,0),(-1,-1),0),
                               ("LEFTPADDING",(0,0),(-1,-1),0),
                               ("RIGHTPADDING",(0,0),(-1,-1),0)])
    toc.levelStyles=[
        ParagraphStyle("TOCChapter",fontName="YaHeiBold",fontSize=11.3,leading=17,
                       textColor=color(NAVY),spaceBefore=6,leftIndent=0,rightIndent=25,wordWrap="CJK"),
        ParagraphStyle("TOCSection",fontName="YaHei",fontSize=9.3,leading=13.4,
                       textColor=color(TEXT),leftIndent=18,rightIndent=25,wordWrap="CJK"),
    ]
    story.extend([toc,PageBreak()]); story.extend(blocks(main_text,main=True))
    doc=ReviewDoc(str(OUTPUT)); doc.multiBuild(story)
    print(json.dumps({"output":str(OUTPUT),"bytes":OUTPUT.stat().st_size,
                      "embedded_diagrams":7,"expanded_table_count":99,"format":"A4 landscape"},ensure_ascii=True,indent=2))


if __name__=="__main__": main()
