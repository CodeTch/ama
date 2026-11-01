package ama.export;

import java.nio.file.*;
import java.time.Duration;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MinutesExportConfinedUnicodeIT {
 @TempDir Path work;
 @Test void actualConfinedChildPreservesChineseEmojiAndTables()throws Exception{
  var fonts=new MinutesExportRenderer.Fonts(Path.of(required("AMA_EXPORT_TEST_CJK")),Path.of(required("AMA_EXPORT_TEST_UNICODE")));
  var process=new MinutesExportProcess(work,fonts,Duration.ofSeconds(30));
  var source=new MinutesExportDocument("项目评审会议纪要😀","## 会议结论\n\n| 负责人 | 行动事项 |\n| --- | --- |\n| 张三 | 核对事实😀 |\n\n"+"保留中文来源，明确负责人和后续行动。\n\n".repeat(150));
  byte[] pdf=process.render(source,MinutesExportRenderer.Format.PDF,()->false);
  assertArrayEquals(pdf,process.render(source,MinutesExportRenderer.Format.PDF,()->false),"Independent confined processes must preserve PDF bytes for the same installed fonts and saved source");
  try(var document=Loader.loadPDF(pdf)){String text=new PDFTextStripper().getText(document);assertTrue(document.getNumberOfPages()>1);assertTrue(text.contains(source.title()));assertTrue(text.contains("核对事实😀"));assertEquals(150,text.split("保留中文来源，明确负责人和后续行动。",-1).length-1);}
  byte[] word=process.render(source,MinutesExportRenderer.Format.DOCX,()->false),md=process.render(source,MinutesExportRenderer.Format.MD,()->false);
  try(var files=Files.list(work)){assertEquals(0,files.count());}
  Path output=Path.of(required("AMA_EXPORT_TEST_OUTPUT")).toAbsolutePath().normalize();Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();while(root!=null&&!Files.isRegularFile(root.resolve("DEVELOPMENT_HANDOFF.md")))root=root.getParent();assertNotNull(root);assertTrue(output.startsWith(root.resolve(".tools/local-app/export-conversion")));Files.createDirectory(output);
  Files.write(output.resolve("minutes.pdf"),pdf,StandardOpenOption.CREATE_NEW);Files.write(output.resolve("minutes.docx"),word,StandardOpenOption.CREATE_NEW);Files.write(output.resolve("minutes.md"),md,StandardOpenOption.CREATE_NEW);
 }
 private static String required(String name){String value=System.getenv(name);if(value==null||value.isBlank())throw new IllegalArgumentException("Explicit fixture required: "+name);return value;}
}
