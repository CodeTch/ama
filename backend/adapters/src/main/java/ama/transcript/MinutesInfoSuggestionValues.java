package ama.transcript;
import ama.contract.MinutesContracts.*;import java.util.*;
import static ama.contract.MinutesContracts.*;import static ama.contract.MinutesContracts.FailureCode.*;
/** Projection only; callers must first verify the complete candidate provenance. */
final class MinutesInfoSuggestionValues {
 static Map<String,String> from(List<FullRow> rows,String meeting,String body,String set,String input){
  var bodies=rows.stream().filter(r->r.table()==Table.MINUTES_REVISION&&body.equals(r.string("id"))).toList();require(bodies.size()==1,PROVENANCE_UNAVAILABLE);var selected=bodies.getFirst();require(meeting.equals(selected.string("meeting_id"))&&input.equals(selected.nullableString("generation_input_id"))&&"AI".equals(selected.string("title_origin")),PROVENANCE_UNAVAILABLE);
  var result=new LinkedHashMap<String,String>();if(!selected.string("title").isBlank())result.put("NAME",selected.string("title"));
  String summary=rows.stream().filter(r->r.table()==Table.MINUTES_ELEMENT&&set.equals(r.string("set_id"))&&"OVERVIEW".equals(r.string("category"))).sorted(Comparator.comparingLong(r->r.number("ordinal"))).map(r->r.string("body")).filter(s->!s.isBlank()).collect(java.util.stream.Collectors.joining("\n"));if(!summary.isBlank())result.put("SUMMARY",summary);return Map.copyOf(result);
 }
}
