export function materialParseError(code:string){
 const reasons:Record<string,string>={ARCHIVE_EXPANSION_EXCEEDED:'文档解压后的内容超过安全解析限额，请拆分文件。',
  NO_TEXT:'未提取到可读文字。扫描件暂不支持 OCR，请提供包含文本的文档。',
  FORMAT_UNSUPPORTED:'暂不支持该格式，请提供 PDF、PPTX 或 DOCX。',
  INVALID_DOCUMENT:'文件内容或格式损坏，请检查原文件后重新上传。',
  DOCUMENT_ENCRYPTED:'加密或禁止提取文字的文档无法解析，请提供可读取的版本。',
  SOURCE_HASH_MISMATCH:'文件内容与上传记录不一致，请重新上传原文件。',
  SOURCE_BYTES_EXCEEDED:'文件超过解析大小限制，请缩小文件后重试。',
  UNITS_EXCEEDED:'文档页数或段落数超过解析限制，请拆分文件。'};
 return reasons[code]??'该材料未能解析，请检查文件后重试。';
}
