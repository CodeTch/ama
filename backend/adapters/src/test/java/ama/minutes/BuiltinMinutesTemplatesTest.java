package ama.minutes;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BuiltinMinutesTemplatesTest {
 @Test void distinctBuiltinTemplatesHaveRealImmutableThemes(){
  var definitions=BuiltinMinutesTemplates.definitions();
  assertEquals(List.of("正式会议纪要","简明总结"),definitions.stream().map(BuiltinMinutesTemplates.Definition::name).toList());
  assertNotEquals(definitions.getFirst().document(),definitions.getLast().document());
  assertEquals(definitions.getFirst().themes(),PersonalTemplateDocument.parse(definitions.getFirst().document()).themes());
 }
}
