package ama.material;

import java.io.InputStream;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Source-preserving document extraction. No authorization is implied by a parser result. */
public interface MaterialParser {
 String PROFILE="ama.material-documents.v1";
 record Budget(long sourceBytes,long utf16,int units){public Budget{if(sourceBytes<1||utf16<1||units<1)throw new IllegalArgumentException("Positive material bounds required");}}
 record Unit(long ordinal,String kind,Long pageNo,Long paragraphNo,String sectionPath,String body){}
 record Result(String format,String sourceSha256,List<Unit> units,byte[] textBytes){
  public Result{units=List.copyOf(units);textBytes=textBytes.clone();}
  @Override public byte[] textBytes(){return textBytes.clone();}
 }
 final class ParseFailure extends Exception {
  private final String code;
  public ParseFailure(String code){super(code);this.code=code;}
  public String code(){return code;}
 }
 Result parse(InputStream source,String name,String expectedSha256,Budget budget,BooleanSupplier cancelled)throws ParseFailure;
}
