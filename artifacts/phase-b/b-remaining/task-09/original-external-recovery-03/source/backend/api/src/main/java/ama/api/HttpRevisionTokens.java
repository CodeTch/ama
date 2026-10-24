package ama.api;
import com.fasterxml.jackson.databind.JsonNode;
/** Lossless HTTP representation; normalization precedes every semantic digest. */
final class HttpRevisionTokens {
    private static final String MAX="9223372036854775806";
    private HttpRevisionTokens() {
    }
    static long expected(JsonNode body) {
        JsonNode integer=body.get("expectedRevision"),token=body.get("expectedRevisionToken");
        if(integer==null&&token==null)throw new IllegalArgumentException("Revision required");
        Long a=null,b=null;
        if(integer!=null) {
            if(!integer.isIntegralNumber()||!integer.canConvertToLong())throw new IllegalArgumentException("Invalid revision");
            a=integer.longValue();
            if(a<0||a==Long.MAX_VALUE)throw new IllegalArgumentException("Invalid revision");
        }
        if(token!=null) {
            if(!token.isTextual())throw new IllegalArgumentException("Invalid revision token");
            String value=token.textValue();
            if(value.isEmpty()||value.length()>19||(value.length()>1&&value.charAt(0)=='0'))throw new IllegalArgumentException("Invalid revision token");
            for(int i=0;i<value.length();i++)if(value.charAt(i)<'0'||value.charAt(i)>'9')throw new IllegalArgumentException("Invalid revision token");
            if(value.length()==19&&value.compareTo(MAX)>0)throw new IllegalArgumentException("Invalid revision token");
            b=Long.parseLong(value);
        }
        if(a!=null&&b!=null&&!a.equals(b))throw new IllegalArgumentException("Revision representations differ");
        return a==null?b:a;
    }
    static String token(long value) {
        if(value<0)throw new IllegalArgumentException("Invalid counter");
        return Long.toString(value);
    }
}
