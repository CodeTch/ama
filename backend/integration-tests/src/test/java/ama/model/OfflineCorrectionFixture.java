package ama.model;

import java.util.List;
import java.util.Map;

/** Match the production serializer's messages without sending a second model request. */
public final class OfflineCorrectionFixture {
 private OfflineCorrectionFixture(){}
 public static Map<String,Object> match(String text){
  return Map.of("field_equals",Map.of("messages",OfflineCorrectionProtocol.request("fixture",text,List.of()).get("messages")));
 }
}
