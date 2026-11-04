package ama.minutes;
import java.util.*;
/** Literal excluded excerpts, including spacing/width changes, without semantic claims. */
public final class MinutesSensitiveContent {
 public static boolean reintroduced(List<String> excerpts,List<String> content){var normalized=content.stream().map(MinutesSensitiveContent::normalize).toList();return excerpts.stream().map(MinutesSensitiveContent::normalize).filter(s->!s.isEmpty()).anyMatch(excerpt->normalized.stream().anyMatch(text->text.contains(excerpt)));}
 private static String normalize(String value){return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKC).replaceAll("[\\s\\p{Z}]+","");}
 private MinutesSensitiveContent(){}
}
