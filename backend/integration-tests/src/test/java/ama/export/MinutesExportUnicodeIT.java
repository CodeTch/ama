package ama.export;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual installed-font output. No business authorization or export-job qualification is implied. */
class MinutesExportUnicodeIT {
 @Test void installedChineseAndEmojiFontsPreserveSavedTextAcrossPdfPages()throws Exception{
  Path cjk=Path.of(required("AMA_EXPORT_TEST_CJK")),unicode=Path.of(required("AMA_EXPORT_TEST_UNICODE"));
  assertTrue(Files.isRegularFile(cjk));assertTrue(Files.isRegularFile(unicode));
  String title="项目评审会议纪要😀";
  String body="## 会议结论\n\n| 负责人 | 行动事项 |\n| --- | --- |\n| 张三 | 核对事实😀 |\n\n"+java.util.stream.IntStream.range(0,180).mapToObj(i->"第 "+i+" 项：保留中文来源，明确负责人和后续行动。").collect(java.util.stream.Collectors.joining("\n\n"));
  var document=new MinutesExportDocument(title,body);var fonts=new MinutesExportRenderer.Fonts(cjk,unicode);
  byte[] pdf=MinutesExportRenderer.render(document,MinutesExportRenderer.Format.PDF,fonts,()->false);
  try(var parsed=Loader.loadPDF(pdf)){
   assertTrue(parsed.getNumberOfPages()>1);String text=new PDFTextStripper().getText(parsed);
   assertTrue(text.contains(title),text.substring(0,Math.min(text.length(),300)));assertTrue(text.contains("核对事实😀"));
   for(int i=0;i<180;i++)assertTrue(text.contains("第 "+i+" 项：保留中文来源，明确负责人和后续行动。"),"Missing Chinese paragraph "+i);
  }
  Path output=Path.of(required("AMA_EXPORT_TEST_OUTPUT")).toAbsolutePath().normalize();
  Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();while(root!=null&&!Files.isRegularFile(root.resolve("DEVELOPMENT_HANDOFF.md")))root=root.getParent();assertNotNull(root);
  assertTrue(output.startsWith(root.resolve(".tools/local-app/export-conversion")));Files.createDirectory(output);
  Files.write(output.resolve("minutes.pdf"),pdf,StandardOpenOption.CREATE_NEW);
  Files.write(output.resolve("minutes.docx"),MinutesExportRenderer.render(document,MinutesExportRenderer.Format.DOCX,fonts,()->false),StandardOpenOption.CREATE_NEW);
  Files.write(output.resolve("minutes.md"),MinutesExportRenderer.render(document,MinutesExportRenderer.Format.MD,fonts,()->false),StandardOpenOption.CREATE_NEW);
  Files.writeString(output.resolve("scope.txt"),"Converter component output; actual Chinese/emoji extraction; not an authorized export job or model-quality acceptance.\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
 }
 private static String required(String name){String value=System.getenv(name);if(value==null||value.isBlank())throw new IllegalArgumentException(name+" must explicitly name this run's installed fixture");return value;}
}
