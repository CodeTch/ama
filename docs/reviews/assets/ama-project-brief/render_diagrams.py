"""Render the review brief's PNG and editable SVG diagrams (Pillow required)."""

from __future__ import annotations

import json
import math
import re
from pathlib import Path
from xml.sax.saxutils import escape

from PIL import Image, ImageDraw, ImageFont


HERE = Path(__file__).resolve().parent
FONT_DIR = Path("C:/Windows/Fonts")
REGULAR = FONT_DIR / "msyh.ttc"
BOLD = FONT_DIR / "msyhbd.ttc"
if not REGULAR.exists():
    candidates = [
        Path("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"),
        Path("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"),
    ]
    REGULAR = next((p for p in candidates if p.exists()), REGULAR)
    BOLD = REGULAR

NAVY = "#14243D"
TEXT = "#31445D"
MUTED = "#63758B"
BLUE = "#2563EB"
TEAL = "#0D9488"
ORANGE = "#D97706"
LINE = "#D7E1EE"
PALE_BLUE = "#EDF4FF"
PALE_TEAL = "#E9F7F3"
PALE_ORANGE = "#FFF4DF"
BG = "#F7F9FC"


class Canvas:
    def __init__(self, height: int):
        self.width, self.height = 2000, height
        self.image = Image.new("RGB", (self.width, self.height), BG)
        self.draw = ImageDraw.Draw(self.image)
        self.svg = [
            f'<svg xmlns="http://www.w3.org/2000/svg" width="2000" '
            f'height="{height}" viewBox="0 0 2000 {height}">',
            f'<rect width="2000" height="{height}" fill="{BG}"/>',
        ]
        self.text_count = 0

    def rect(self, x, y, w, h, fill="white", stroke=LINE, radius=22, width=2):
        self.draw.rounded_rectangle((x, y, x + w, y + h), radius=radius,
                                    fill=fill, outline=stroke, width=width)
        self.svg.append(
            f'<rect x="{x}" y="{y}" width="{w}" height="{h}" '
            f'rx="{radius}" fill="{fill}" stroke="{stroke}" stroke-width="{width}"/>'
        )

    def text(self, x, y, value, size=34, color=TEXT, bold=False,
             align="left", max_width=None):
        font = ImageFont.truetype(str(BOLD if bold else REGULAR), size)
        length = self.draw.textlength(value, font=font)
        if max_width is not None:
            assert length <= max_width, (value, length, max_width)
        left = x - length / 2 if align == "center" else x
        if align == "right":
            left = x - length
        bounds = self.draw.textbbox((left, y), value, font=font, anchor="lt")
        assert bounds[0] >= 0 and bounds[1] >= 0, (value, bounds)
        assert bounds[2] <= self.width and bounds[3] <= self.height, (value, bounds)
        self.draw.text((left, y), value, font=font, fill=color, anchor="lt")
        anchor = {"left": "start", "center": "middle", "right": "end"}[align]
        self.svg.append(
            f'<text x="{x}" y="{y + size * 0.88:.1f}" '
            f'font-family="Microsoft YaHei, Noto Sans CJK SC, sans-serif" '
            f'font-size="{size}" font-weight="{700 if bold else 400}" '
            f'text-anchor="{anchor}" fill="{color}">{escape(value)}</text>'
        )
        self.text_count += 1

    def path(self, points, color=LINE, width=4):
        self.draw.line(points, fill=color, width=width, joint="curve")
        coords = " ".join(f"{x},{y}" for x, y in points)
        self.svg.append(f'<polyline points="{coords}" fill="none" stroke="{color}" '
                        f'stroke-width="{width}" stroke-linejoin="round"/>')

    def arrow(self, points, color=BLUE, width=5, both=False):
        self.path(points, color, width)
        pairs = [(points[-2], points[-1])]
        if both:
            pairs.append((points[1], points[0]))
        for (x0, y0), (x1, y1) in pairs:
            angle = math.atan2(y1 - y0, x1 - x0)
            a, b = 17, 9
            pts = [(x1, y1),
                   (x1 - a * math.cos(angle) + b * math.sin(angle),
                    y1 - a * math.sin(angle) - b * math.cos(angle)),
                   (x1 - a * math.cos(angle) - b * math.sin(angle),
                    y1 - a * math.sin(angle) + b * math.cos(angle))]
            self.draw.polygon(pts, fill=color)
            coords = " ".join(f"{x:.1f},{y:.1f}" for x, y in pts)
            self.svg.append(f'<polygon points="{coords}" fill="{color}"/>')

    def title(self, number, title, subtitle):
        self.rect(80, 55, 88, 62, fill=NAVY, stroke=NAVY, radius=14)
        self.text(124, 67, f"{number:02}", 36, "white", True, "center")
        self.text(192, 58, title, 49, NAVY, True)
        self.text(80, 140, subtitle, 31, MUTED, max_width=1840)

    def card(self, x, y, w, h, title, lines, fill="white", accent=BLUE,
             title_size=37, body_size=31):
        self.rect(x, y, w, h, fill=fill)
        self.rect(x + 26, y + 30, 7, 42, fill=accent, stroke=accent, radius=3)
        self.text(x + 51, y + 30, title, title_size, NAVY, True,
                  max_width=w - 78)
        for i, line in enumerate(lines):
            self.text(x + 31, y + 104 + i * 53, line, body_size, TEXT,
                      max_width=w - 62)

    def save(self, name):
        self.image.save(HERE / f"{name}.png", optimize=True)
        (HERE / f"{name}.svg").write_text("\n".join(self.svg + ["</svg>"]),
                                         encoding="utf-8")
        return {"name": name, "size": [self.width, self.height],
                "text_elements": self.text_count}


def overview():
    c = Canvas(1110)
    c.title(1, "核心业务能力全景", "从会议输入到知识沉淀，六个业务模块形成统一工作链路")
    items = [
        ("01  会议采集与材料", ["文件导入 / 在线录音", "多段录音 / 背景材料", "产出：可用会议输入"], PALE_BLUE, BLUE),
        ("02  增强转写与核对", ["原始与增强转写", "说话人 / 术语 / 人工修订", "产出：确认转写"], PALE_BLUE, BLUE),
        ("03  AI要素与纪要", ["七类要素 / 模板与编辑", "行动项 / 来源 / 敏感内容", "产出：要素与纪要正文"], PALE_TEAL, TEAL),
        ("04  成果确认与发布", ["信息校验 / 最终确认", "知识库发布 / 状态核验", "产出：正式成果与发布记录"], PALE_ORANGE, ORANGE),
        ("05  会议资产管理", ["列表 / 检索 / 个人专题", "授权回看 / 文档导出", "产出：可复用会议资产"], PALE_TEAL, TEAL),
        ("06  运营与质量治理", ["反馈审核 / 声纹治理", "质量评测 / 配置 / 邮件", "产出：质量改进与治理记录"], PALE_BLUE, BLUE),
    ]
    for i, (title, lines, fill, accent) in enumerate(items):
        c.card(80 + (i % 3) * 630, 230 + (i // 3) * 335,
               580, 290, title, lines, fill, accent)
    c.rect(80, 930, 1840, 100, fill=NAVY, stroke=NAVY)
    c.text(1000, 958, "公共支撑：企业身份与权限  ·  版本与来源  ·  可靠任务  ·  操作审计",
           34, "white", True, "center", max_width=1780)
    c.text(80, 1060, "交付顺序：P0主链路优先，P1在线录音、个人管理与治理能力分阶段补齐。", 28, MUTED)
    return c.save("01-business-overview")


def architecture():
    c = Canvas(1470)
    c.title(2, "系统整体架构", "模块化业务后端 + 独立实时接入 + 后台Worker；目标架构示意")
    c.rect(80, 220, 1320, 150, fill=PALE_BLUE)
    c.text(740, 244, "展示与接入层", 40, NAVY, True, "center")
    c.text(740, 312, "Web工作台  ·  HTTPS / WSS统一入口", 33, TEXT, align="center")
    c.arrow([(740, 370), (740, 440)])
    c.text(80, 394, "WSS直连实时接入", 26, BLUE)
    c.arrow([(80, 295), (26, 295), (26, 855), (80, 855)])
    c.rect(80, 440, 1320, 230)
    c.text(110, 468, "业务服务层  |  API", 38, NAVY, True)
    labels = ["会议与授权", "文件与材料", "转写与核对", "要素与纪要", "确认与发布", "回看与导出"]
    for i, label in enumerate(labels):
        x, y = 110 + (i % 3) * 423, 535 + (i // 3) * 65
        c.rect(x, y, 398, 51, fill=PALE_BLUE, stroke=PALE_BLUE, radius=12)
        c.text(x + 199, y + 8, label, 29, BLUE, True, "center")
    for x in [285, 735, 1190]:
        c.arrow([(x, 670), (x, 755)])
    c.card(80, 755, 410, 215, "实时接入", ["录音分片 / 实时转写", "会话恢复 / 状态推送"], PALE_BLUE, BLUE, 37, 29)
    c.card(530, 755, 410, 215, "后台Worker", ["解析 / 增强 / 纪要", "发布 / 导出 / 重试"], PALE_TEAL, TEAL, 37, 29)
    c.card(980, 755, 420, 215, "处理适配能力", ["模型 / 媒体 / 文档", "受控文件读取与写入"], PALE_BLUE, BLUE, 37, 29)
    c.path([(285, 970), (285, 1010), (1190, 1010), (1190, 970)], MUTED)
    c.path([(735, 970), (735, 1010)], MUTED)
    c.path([(80, 555), (51, 555), (51, 1010), (285, 1010)], MUTED)
    c.arrow([(410, 1010), (410, 1080)], MUTED)
    c.arrow([(1095, 1010), (1095, 1080)], MUTED)
    c.rect(80, 1080, 640, 160, fill=PALE_BLUE)
    c.text(400, 1104, "MySQL", 41, NAVY, True, "center")
    c.text(400, 1170, "业务状态 / 版本 / 任务 / 审计", 30, TEXT, align="center")
    c.rect(770, 1080, 630, 160, fill=PALE_TEAL)
    c.text(1085, 1104, "统一文件存储接口", 39, NAVY, True, "center")
    c.text(1085, 1170, "本地文件系统  或  S3", 32, TEXT, align="center")
    c.rect(80, 1315, 1320, 90, fill=NAVY, stroke=NAVY)
    c.text(740, 1341, "贯穿各层：权限校验  ·  版本控制  ·  任务恢复  ·  审计观测",
           30, "white", True, "center")
    c.rect(1460, 220, 460, 1185, fill="#EEF2F7")
    c.text(1690, 250, "企业能力接入", 38, NAVY, True, "center")
    c.text(1690, 309, "经统一适配接口对接", 29, MUTED, align="center")
    services = [("统一登录", "用户身份与会话"), ("人员目录", "账号与组织信息"),
                ("公司模型", "转写 / 说话人 / 纪要"), ("企业知识库", "材料引用与成果发布"),
                ("公司邮件", "受控成果链接分享")]
    for i, (title, subtitle) in enumerate(services):
        y = 400 + i * 183
        c.rect(1490, y, 400, 140)
        c.text(1690, y + 25, title, 36, NAVY, True, "center")
        c.text(1690, y + 85, subtitle, 27, MUTED, align="center")
    c.arrow([(1400, 555), (1460, 555)], TEAL)
    c.arrow([(1400, 850), (1460, 850)], TEAL)
    return c.save("02-architecture")


def flow():
    c = Canvas(1130)
    c.title(3, "会议成果生成与发布流程", "统一处理链路，两个确认节点，保留人工判断与来源依据")
    c.rect(80, 215, 1840, 108, fill=NAVY, stroke=NAVY)
    c.text(1000, 249, "输入：文件导入或在线录音  +  可选背景材料", 37, "white", True, "center")
    positions = [(80, 415), (730, 415), (1380, 415),
                 (1380, 770), (730, 770), (80, 770)]
    items = [
        ("01  输入准备", ["保存主文件与材料", "固定本次处理输入"], PALE_BLUE, BLUE),
        ("02  转写与增强", ["内容解析与模型处理", "形成待核对转写"], PALE_BLUE, BLUE),
        ("03  人工确认转写", ["修订文字与说话人", "确认可用于生成的版本"], PALE_ORANGE, ORANGE),
        ("04  生成与编辑纪要", ["提炼七类会议要素", "核对来源并编辑正文"], PALE_TEAL, TEAL),
        ("05  最终确认", ["校验信息、成果与权限", "冻结本次发布版本"], PALE_ORANGE, ORANGE),
        ("06  发布与回看", ["发布知识库并核验状态", "按权限回看、检索与导出"], PALE_TEAL, TEAL),
    ]
    c.arrow([(350, 323), (350, 415)])
    for (x, y), (title, lines, fill, accent) in zip(positions, items):
        c.card(x, y, 540, 225, title, lines, fill, accent, 37, 32)
    c.arrow([(620, 525), (730, 525)])
    c.arrow([(1270, 525), (1380, 525)])
    c.arrow([(1650, 640), (1650, 770)])
    c.arrow([(1380, 880), (1270, 880)])
    c.arrow([(730, 880), (620, 880)])
    c.text(80, 1060, "异常处理：保留草稿与历史；失败可重试；发布结果未知时先查回状态。", 31, MUTED)
    return c.save("03-business-flow")


def deployment():
    c = Canvas(1330)
    c.title(4, "双机部署与外部服务关系", "两台RHEL 8.10虚拟机；应用共同承载，私有数据按单写方式运行")
    c.rect(650, 215, 700, 130, fill=NAVY, stroke=NAVY)
    c.text(1000, 237, "统一入口", 40, "white", True, "center")
    c.text(1000, 301, "HTTPS / WSS  ·  健康检查", 31, "white", align="center")
    c.arrow([(1000, 345), (1000, 390), (525, 390), (525, 450)])
    c.arrow([(1000, 390), (1475, 390), (1475, 450)])
    for x, node in [(80, "节点 A"), (1030, "节点 B")]:
        c.rect(x, 450, 890, 510)
        c.text(x + 35, 485, node, 42, NAVY, True)
        c.text(x + 845, 495, "16核 / 32GB", 30, MUTED, align="right")
        c.rect(x + 30, 570, 830, 125, fill=PALE_BLUE)
        c.text(x + 445, 596, "API / 实时接入 / Worker", 36, BLUE, True, "center")
        c.text(x + 445, 655, "业务与处理进程同时运行", 29, TEXT, align="center")
    c.rect(110, 750, 830, 155, fill=PALE_ORANGE)
    c.text(525, 775, "私有数据资源：活动侧（示意）", 36, ORANGE, True, "center")
    c.text(525, 841, "MySQL（若私有）+ 所选文件服务", 31, TEXT, align="center")
    c.rect(1060, 750, 830, 155, fill=PALE_TEAL)
    c.text(1475, 775, "私有数据资源：备用侧（示意）", 36, TEAL, True, "center")
    c.text(1475, 841, "同步副本  ·  满足条件后接管", 31, TEXT, align="center")
    c.arrow([(940, 828), (1060, 828)], TEAL, both=True)
    c.text(1000, 785, "同步", 26, TEAL, True, "center")
    c.text(1000, 1015, "活动资源可在任一节点；完成隔离、同步与健康验证后才执行接管。",
           33, TEXT, align="center")
    c.rect(80, 1110, 1840, 145, fill="#EAF0F8")
    c.text(1000, 1136, "公司外部服务：统一登录  /  人员目录  /  模型  /  知识库  /  邮件",
           34, NAVY, True, "center")
    c.text(1000, 1201, "公司MySQL和S3可按配置独立接入；仅部署实际选用的私有数据组件。",
           31, MUTED, align="center")
    return c.save("04-deployment")


def entity(c, x, y, title, table, pk, fk, fill=PALE_BLUE):
    c.rect(x, y, 540, 220, fill=fill)
    c.text(x + 28, y + 24, title, 36, NAVY, True, max_width=484)
    c.text(x + 28, y + 83, table, 26, BLUE, True, max_width=484)
    c.text(x + 28, y + 135, "PK  " + pk, 25, TEXT, max_width=484)
    c.text(x + 28, y + 177, "FK  " + fk, 25, MUTED, max_width=484)


def relation(c, points, label, label_x, label_y, color=TEAL):
    c.arrow(points, color, 4)
    width = c.draw.textlength(label, font=ImageFont.truetype(str(REGULAR), 24)) + 20
    c.rect(label_x - width / 2, label_y - 2, width, 35, fill=BG, stroke=BG, radius=5)
    c.text(label_x, label_y, label, 24, color, True, "center")


def er_source(name, nodes, edges):
    """Keep Mermaid source and verify every drawn association against the draft DDL."""
    root = HERE.parents[3]
    ddl = (root / "docs/database/ama-schema-draft.sql").read_text(encoding="utf-8")
    dictionary = (root / "docs/database/ama-data-dictionary.md").read_text(encoding="utf-8")
    lines = ["erDiagram"]
    for _, _, _, table, pk, fk, _ in nodes:
        lines.append(f"    {table} {{")
        fields = {}
        for field in pk.split(" + "):
            fields.setdefault(field, []).append("PK")
        for field in fk.split(" + "):
            fields.setdefault(field, []).append("FK")
        for field, roles in fields.items():
            lines.append(f"        string {field} {', '.join(roles)}")
        lines.append("    }")
    for parent, child, fk, cardinality in edges:
        pattern = (r"ALTER TABLE " + re.escape(child) + r" ADD CONSTRAINT [^\n]+ "
                   r"FOREIGN KEY \(" + re.escape(fk) + r"\) REFERENCES "
                   + re.escape(parent) + r" \(")
        assert re.search(pattern, ddl), (parent, child, fk)
        endpoint = "o|" if cardinality == "1:0..1" else "o{"
        section = re.search(r"^## " + re.escape(child) + r"\n(.*?)(?=^## |\Z)",
                            dictionary, re.M | re.S).group(1)
        row = next(line for line in section.splitlines() if line.startswith("|" + fk + "|"))
        parent_end = "o|" if row.split("|")[3] == "是" else "||"
        lines.append(f'    {parent} {parent_end}--{endpoint} {child} : "{fk}"')
    (HERE / f"{name}.mmd").write_text("\n".join(lines) + "\n", encoding="utf-8")


def core_er():
    c = Canvas(1630)
    c.title(5, "会议成果主链 ER 关系图", "从会议与输入到确认、生成和发布；图示抽取核心实体与主外键关系")
    nodes = [
        (80, 230, "企业身份", "enterprise_identity", "enterprise_id", "department_id", PALE_BLUE),
        (730, 230, "会议主记录", "meeting", "id", "owner_id", PALE_BLUE),
        (1380, 230, "参会名单版本", "roster_revision", "id", "meeting_id", PALE_BLUE),
        (80, 575, "处理批次", "input_batch", "id", "meeting_id", PALE_BLUE),
        (730, 575, "转写版本", "transcript_revision", "id", "batch_id", PALE_BLUE),
        (1380, 575, "转写确认", "transcript_confirmation", "id", "revision_id", PALE_ORANGE),
        (80, 920, "纪要生成输入", "generation_input", "id", "transcript_confirmation_id", PALE_TEAL),
        (730, 920, "已保存纪要版本", "minutes_revision", "id", "generation_input_id", PALE_TEAL),
        (1380, 920, "最终确认", "final_confirmation", "id", "minutes_revision_id", PALE_ORANGE),
        (80, 1265, "生成候选", "generation_candidate", "id", "input_id", PALE_TEAL),
        (730, 1265, "发布记录", "publication", "id", "confirmation_id", PALE_TEAL),
        (1380, 1265, "外部文档映射", "external_document_mapping", "publication_id", "publication_id", PALE_TEAL),
    ]
    relation(c, [(620, 340), (730, 340)], "1:N", 675, 300)
    relation(c, [(1270, 340), (1380, 340)], "1:N", 1325, 300)
    relation(c, [(865, 450), (865, 510), (350, 510), (350, 575)], "1:N", 595, 477)
    relation(c, [(1000, 450), (1000, 575)], "1:N", 1050, 495)
    relation(c, [(620, 685), (730, 685)], "1:N", 675, 645)
    relation(c, [(1270, 685), (1380, 685)], "1:0..1", 1325, 645)
    relation(c, [(1650, 795), (1650, 850), (350, 850), (350, 920)], "1:N", 1190, 817)
    relation(c, [(620, 1030), (730, 1030)], "1:N", 675, 990)
    relation(c, [(1270, 1030), (1380, 1030)], "1:N", 1325, 990)
    relation(c, [(350, 1140), (350, 1265)], "1:N", 400, 1184)
    relation(c, [(1650, 1140), (1650, 1205), (1000, 1205), (1000, 1265)], "1:0..1", 1335, 1172)
    relation(c, [(1270, 1375), (1380, 1375)], "1:0..1", 1325, 1335)
    for node in nodes:
        entity(c, *node)
    c.text(80, 1540, "PK 主键  /  FK 关联字段；1:N中的N包含零条；部分会议归属、输入关联及当前指针省略。",
           28, MUTED, max_width=1840)
    edges = [
        ("enterprise_identity", "meeting", "owner_id", "1:N"),
        ("meeting", "roster_revision", "meeting_id", "1:N"),
        ("meeting", "input_batch", "meeting_id", "1:N"),
        ("meeting", "transcript_revision", "meeting_id", "1:N"),
        ("input_batch", "transcript_revision", "batch_id", "1:N"),
        ("transcript_revision", "transcript_confirmation", "revision_id", "1:0..1"),
        ("transcript_confirmation", "generation_input", "transcript_confirmation_id", "1:N"),
        ("generation_input", "minutes_revision", "generation_input_id", "1:N"),
        ("minutes_revision", "final_confirmation", "minutes_revision_id", "1:N"),
        ("generation_input", "generation_candidate", "input_id", "1:N"),
        ("final_confirmation", "publication", "confirmation_id", "1:0..1"),
        ("publication", "external_document_mapping", "publication_id", "1:0..1"),
    ]
    er_source("05-database-core-er", nodes, edges)
    return c.save("05-database-core-er")


def source_er():
    c = Canvas(1310)
    c.title(6, "背景材料与来源 ER 关系图", "材料引用、版本、快照、解析与证据分层保存；图示抽取主要关联")
    nodes = [
        (80, 230, "材料逻辑引用", "material_reference", "id", "meeting_id", PALE_BLUE),
        (730, 230, "材料版本", "material_revision", "id", "reference_id", PALE_BLUE),
        (1380, 230, "解析文档版本", "parsed_document", "id", "material_revision_id", PALE_BLUE),
        (80, 575, "材料快照", "material_snapshot", "id", "meeting_id", PALE_ORANGE),
        (730, 575, "材料快照明细", "material_snapshot_item", "snapshot_id + material_revision_id", "material_revision_id", PALE_ORANGE),
        (1380, 575, "来源定位", "source_locator", "id", "material_revision_id", PALE_TEAL),
        (80, 920, "来源上下文", "source_context", "id", "material_snapshot_id", PALE_TEAL),
        (730, 920, "上下文材料选择", "source_context_material", "context_id + material_revision_id", "parsed_document_id", PALE_TEAL),
        (1380, 920, "成果引用证据", "evidence_link", "id", "locator_id", PALE_TEAL),
    ]
    relation(c, [(620, 340), (730, 340)], "1:N", 675, 300)
    relation(c, [(1270, 340), (1380, 340)], "1:N", 1325, 300)
    relation(c, [(1000, 450), (1000, 575)], "1:N", 1050, 495)
    relation(c, [(620, 685), (730, 685)], "1:N", 675, 645)
    relation(c, [(350, 795), (350, 920)], "1:N", 400, 840)
    relation(c, [(620, 1030), (730, 1030)], "1:N", 675, 990)
    relation(c, [(1650, 450), (1650, 490), (1310, 490), (1310, 860), (1000, 860), (1000, 920)],
             "1:N", 1360, 495)
    relation(c, [(1650, 795), (1650, 920)], "1:N", 1700, 840)
    relation(c, [(350, 1140), (350, 1200), (1650, 1200), (1650, 1140)], "1:N", 1000, 1165)
    for node in nodes:
        entity(c, *node)
    c.text(80, 1260, "来源上下文固定材料与解析选择；证据关联到具体要素或正文。完整关系与约束见数据字典。",
           28, MUTED, max_width=1840)
    edges = [
        ("material_reference", "material_revision", "reference_id", "1:N"),
        ("material_revision", "parsed_document", "material_revision_id", "1:N"),
        ("material_revision", "material_snapshot_item", "material_revision_id", "1:N"),
        ("material_snapshot", "material_snapshot_item", "snapshot_id", "1:N"),
        ("material_snapshot", "source_context", "material_snapshot_id", "1:N"),
        ("source_context", "source_context_material", "context_id", "1:N"),
        ("parsed_document", "source_context_material", "parsed_document_id", "1:N"),
        ("source_locator", "evidence_link", "locator_id", "1:N"),
        ("source_context", "evidence_link", "source_context_id", "1:N"),
    ]
    er_source("06-database-source-er", nodes, edges)
    return c.save("06-database-source-er")


def roadmap():
    c = Canvas(1340)
    c.title(7, "分阶段开发路线图", "以阶段目标、关键交付物和通过条件推进，按业务依赖组织实施")
    c.rect(80, 220, 1840, 100, fill=NAVY, stroke=NAVY)
    c.text(1000, 250, "推进主线：业务闭环  →  实时与完整业务  →  运营治理  →  生产验证",
           35, "white", True, "center", max_width=1780)
    c.card(80, 390, 540, 260, "阶段一  业务闭环", ["数据 / 权限 / 文件 / 任务", "导入 → 确认 → 纪要 → 发布", "里程碑：主链路可演示"], PALE_BLUE, BLUE, 36, 30)
    c.card(730, 390, 540, 260, "阶段二  音频与实时", ["录音 / 实时转写 / 恢复", "音文定位 / 模型能力验证", "里程碑：音频链路可用"], PALE_BLUE, BLUE, 36, 30)
    c.card(1380, 390, 540, 260, "阶段三  完整业务", ["材料 / 来源 / 模板 / 回看", "导出 / 列表 / 专题 / 治理", "里程碑：核心业务完整"], PALE_TEAL, TEAL, 36, 30)
    c.card(1380, 850, 540, 260, "阶段四  治理与分享", ["反馈 / 声纹 / 评测 / 配置", "受控链接邮件 / 审核生效", "里程碑：治理机制可验证"], PALE_TEAL, TEAL, 36, 30)
    c.card(730, 850, 540, 260, "阶段五  生产与交接", ["平台联调 / 容量 / 故障恢复", "备份恢复 / 部署 / 运维交接", "里程碑：具备上线条件"], PALE_ORANGE, ORANGE, 36, 30)
    c.arrow([(620, 520), (730, 520)])
    c.arrow([(1270, 520), (1380, 520)])
    c.arrow([(1650, 650), (1650, 850)])
    c.arrow([(1380, 980), (1270, 980)])
    c.rect(730, 710, 1190, 76, fill="#EAF0F8")
    c.text(1325, 733, "音频实时与完整业务：公共契约稳定后可按依赖部分并行", 29, MUTED, align="center")
    c.rect(80, 850, 540, 260, fill="#EAF0F8")
    c.text(110, 880, "贯穿各阶段", 36, NAVY, True)
    c.text(110, 951, "接口确认 / 权限与版本", 31, TEXT)
    c.text(110, 1007, "异常恢复 / 回归与验收", 31, TEXT)
    c.rect(80, 1190, 1840, 85, fill=PALE_ORANGE, stroke="#EEDFC5")
    c.text(1000, 1218, "每阶段形成：可演示成果  +  验证记录  +  遗留条件清单", 34, TEXT, align="center")
    return c.save("07-roadmap")


if __name__ == "__main__":
    report = [overview(), architecture(), flow(), deployment(), core_er(), source_er(), roadmap()]
    print(json.dumps({"rendered": report, "font": str(REGULAR)}, ensure_ascii=False, indent=2))
