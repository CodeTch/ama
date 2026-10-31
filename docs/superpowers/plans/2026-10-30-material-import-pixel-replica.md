# 材料导入界面原型可见文案复刻实施计划

## 文件范围

- 修改：`web/src/features/import/UploadImportView.vue`，只调整主文件拖入区的可见格式说明。
- 修改：`web/tests/unit/upload-import-view.test.ts`，固定原型可见文案，同时保护隐藏选择器的现有格式能力。
- 新增：本计划与设计文档。

## 实施步骤

1. **RED：固定原型文案边界**
   - 渲染空态 `UploadImportView`。
   - 断言可见文本为 `视频：MP4 / MOV / M4V / AVI / MKV　文本：TXT / DOCX / SRT / VTT`。
   - 断言旧的扩展可见文本不存在。
   - 断言隐藏文件输入仍包含 `.doc`、`.pdf`、`.pptx`。
   - 已执行并确认测试因当前扩展文案失败。
2. **GREEN：调整一行可见文案**
   - 在 `UploadImportView.vue` 中删除可见行的 `DOC / PDF / PPTX`，保留 input 的 `accept` 和服务能力提示。
3. **验证**
   - 运行材料导入相关单测。
   - 运行 `npm run typecheck` 和 `npm run build`。
   - 用当前本地浏览器复现空态截图，并与原型页面逐像素比较，确认该差异消失。
