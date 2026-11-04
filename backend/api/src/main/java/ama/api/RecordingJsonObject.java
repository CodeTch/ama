package ama.api;

import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.*;

final class RecordingJsonObject {
    private RecordingJsonObject(){}
    static JsonNode read(HttpServletRequest request,int limit)throws java.io.IOException{
        try(var input=request.getInputStream()){
            byte[] bytes=input.readNBytes(limit+1);if(bytes.length>limit)throw new IllegalArgumentException("Recording request too large");
            var mapper=new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
            var value=mapper.readTree(bytes);if(value==null||!value.isObject())throw new IllegalArgumentException("Object required");return value;
        }
    }
}
