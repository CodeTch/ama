package ama.meeting;

import java.util.*;
import java.util.regex.*;

/** Restricted CHECK AST: literal case and AND/OR grouping survive; only byte-length aliases agree. */
final class RuntimeMetadataCheckOracle {
    private final List<String> tokens=new ArrayList<>();
    private int at;
    private RuntimeMetadataCheckOracle(String expression){
        String source=expression.replace("`","").replace("\\'","'");
        var pattern=Pattern.compile("\\s+|_ascii(?=')|'(?:[^']|'')*'|[A-Za-z_][A-Za-z_0-9]*|[0-9]+|[(),=]");
        var matcher=pattern.matcher(source);int end=0;
        while(matcher.find()){
            if(matcher.start()!=end)throw new IllegalArgumentException("Unsupported CHECK token");
            String token=matcher.group();end=matcher.end();
            if(!token.isBlank()&&!token.equals("_ascii"))tokens.add(token);
        }
        if(end!=source.length())throw new IllegalArgumentException("Unsupported CHECK suffix");
    }
    static String ast(String expression){
        var parser=new RuntimeMetadataCheckOracle(expression);String result=parser.or();
        if(parser.at!=parser.tokens.size())throw new IllegalArgumentException("Unparsed CHECK tokens");return result;
    }
    String peek(){return at<tokens.size()?tokens.get(at):"";}
    String take(){if(at==tokens.size())throw new IllegalArgumentException("Incomplete CHECK");return tokens.get(at++);}
    void require(String token){if(!take().equalsIgnoreCase(token))throw new IllegalArgumentException("Unexpected CHECK token");}
    String or(){var parts=new ArrayList<String>();parts.add(and());while(peek().equalsIgnoreCase("or")){take();parts.add(and());}return parts.size()==1?parts.getFirst():"OR"+parts;}
    String and(){var parts=new ArrayList<String>();parts.add(compare());while(peek().equalsIgnoreCase("and")){take();parts.add(compare());}return parts.size()==1?parts.getFirst():"AND"+parts;}
    String compare(){
        String left=atom();
        if(peek().equals("=")){take();return "EQ["+left+","+atom()+"]";}
        if(peek().equalsIgnoreCase("between")){take();String low=atom();require("and");return "BETWEEN["+left+","+low+","+atom()+"]";}
        return left;
    }
    String atom(){
        if(peek().equals("(")){take();String value=or();require(")");return value;}
        String value=take();
        if(peek().equals("(")){
            String function=value.toLowerCase(Locale.ROOT);
            if(!Set.of("length","octet_length").contains(function))throw new IllegalArgumentException("Unsupported CHECK function");
            take();String arg=or();require(")");return "CALL:octet_length["+arg+"]";
        }
        return value.startsWith("'")?"LITERAL:"+value:"TOKEN:"+value.toLowerCase(Locale.ROOT);
    }
}
