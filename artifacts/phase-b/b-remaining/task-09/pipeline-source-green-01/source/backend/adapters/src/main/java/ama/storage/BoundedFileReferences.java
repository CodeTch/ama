package ama.storage;

import ama.contract.ConfigArchiveContracts.ArchiveReadBudget;
import ama.contract.FileContracts.Unavailable;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

/** No reference text crosses JDBC unless the whole bounded selection fits configured limits. */
final class BoundedFileReferences {
 static List<FileRegistry.Reference> read(JdbcTemplate j,ArchiveReadBudget b,String file){
  if(b==null)throw new Unavailable();ama.contract.MinutesContracts.check(()->false);
  long cap=Math.min(b.maxFactRows(),Integer.MAX_VALUE-1L);
  String admissible="n<="+cap+" AND n<="+(b.maxFactEdges()/3)+" AND bytes<="+b.maxTotalBytes()+" AND points<="+b.maxAggregateScalars()+" AND largest<="+b.maxStringUtf8Bytes()+" AND units<="+b.maxStringUtf16();
  String sql="SELECT CASE WHEN "+admissible+" THEN owner_kind END,CASE WHEN "+admissible+" THEN owner_id END,CASE WHEN "+admissible+" THEN purpose END,("+admissible+") FROM (SELECT a.*,COUNT(*) OVER() n,SUM(OCTET_LENGTH(owner_kind)+OCTET_LENGTH(owner_id)+OCTET_LENGTH(purpose)) OVER() bytes,SUM(CHAR_LENGTH(owner_kind)+CHAR_LENGTH(owner_id)+CHAR_LENGTH(purpose)) OVER() points,MAX(GREATEST(OCTET_LENGTH(owner_kind),OCTET_LENGTH(owner_id),OCTET_LENGTH(purpose))) OVER() largest,MAX(GREATEST(OCTET_LENGTH(CONVERT(owner_kind USING utf16)) DIV 2,OCTET_LENGTH(CONVERT(owner_id USING utf16)) DIV 2,OCTET_LENGTH(CONVERT(purpose USING utf16)) DIV 2)) OVER() units FROM (SELECT owner_kind,owner_id,purpose FROM file_reference WHERE file_id=? ORDER BY owner_kind,owner_id,purpose LIMIT ?) a) bounded ORDER BY owner_kind,owner_id,purpose";
  return j.query(sql,rs->{var result=new ArrayList<FileRegistry.Reference>();while(rs.next()){ama.contract.MinutesContracts.check(()->false);if(!rs.getBoolean(4))throw new Unavailable();result.add(new FileRegistry.Reference(rs.getString(1),rs.getString(2),rs.getString(3)));}return List.copyOf(result);},file,cap+1);
 }
}
