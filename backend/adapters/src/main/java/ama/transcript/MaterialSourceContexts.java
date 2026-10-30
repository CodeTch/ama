package ama.transcript;

import ama.config.ConfigBindings;
import ama.contract.MinutesContracts.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Generation input and complete exact material/parser context are frozen in the same guarded transaction. */
final class MaterialSourceContexts {
 static void freeze(JdbcTemplate j,String input,MinutesGraphCapture.Graph graph,ConfigBindings.ValidatedGenerationBinding binding){var root=graph.discovery().root();if(root.get("material_snapshot_id")==null)return;var catalog=binding.profiles().definition(ProfileKind.CATALOG);require(j.update("INSERT INTO source_context(id,meeting_id,origin_kind,generation_input_id,transcript_confirmation_id,material_snapshot_id,catalog_version,catalog_sha256,created_at) VALUES(?,?,'GENERATION_INPUT',?,?,?,?,?,UTC_TIMESTAMP(6))",input,graph.discovery().meeting(),input,root.get("transcript_confirmation_id"),root.get("material_snapshot_id"),catalog.version(),catalog.sha256())==1,PROVENANCE_UNAVAILABLE);for(var member:graph.rows().stream().filter(r->r.table()==Table.MATERIAL_SNAPSHOT_ITEM&&root.get("material_snapshot_id").equals(r.string("snapshot_id"))).toList()){String revision=member.string("material_revision_id");var documents=graph.rows().stream().filter(r->r.table()==Table.PARSED_DOCUMENT&&revision.equals(r.string("material_revision_id"))).toList();require(documents.size()==1,SOURCE_CONTEXT_AMBIGUOUS);require(j.update("INSERT INTO source_context_material VALUES(?,?,?)",input,revision,documents.getFirst().string("id"))==1,PROVENANCE_UNAVAILABLE);}}
 static boolean writeProjection(JdbcTemplate j,FullRow row){if(row.table()!=Table.EVIDENCE_SOURCE_CONTEXT&&row.table()!=Table.ELEMENT_SOURCE_CONTEXT)return false;String table=row.table()==Table.EVIDENCE_SOURCE_CONTEXT?"evidence_link":"minutes_element";require(j.update("UPDATE "+table+" SET source_context_id=? WHERE id=? AND source_context_id IS NULL",row.string("source_context_id"),row.string("id"))==1,PROVENANCE_UNAVAILABLE);return true;}
 private MaterialSourceContexts(){}
}
