package ama.minutes;
import org.junit.jupiter.api.Test;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class MinutesSensitiveContentTest {
 @Test void blocksExcludedTextReintroducedWithoutSourceLinks(){assertTrue(MinutesSensitiveContent.reintroduced(List.of("内部经营数据：营收１２３万元。"),List.of("纪要\n内部经营数据： 营收123万元。")));}
 @Test void doesNotBlockUnrelatedContentOrEmptyExcerpts(){assertFalse(MinutesSensitiveContent.reintroduced(List.of("内部经营数据：营收123万元。",""),List.of("会议讨论容量风险，结论待核实。")));}
 @Test void literalSpanningParagraphBreaksAndOverlappingExcerptsIsStillBlocked(){assertTrue(MinutesSensitiveContent.reintroduced(List.of("营收123万元，利润45万元", "利润45万元，成本78万元"),List.of("营收123万元，\n利润45万元，成本78万元")));}
 @Test void unrelatedParaphrasesOrPartialExcerptsAreNotMisrepresentedAsSemanticDetection(){assertFalse(MinutesSensitiveContent.reintroduced(List.of("内部经营数据：本季度营收123万元。"),List.of("本季收入一百二十三万元。", "营收123万元")));}
}
