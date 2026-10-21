"""Independent baseline/delta assertions against preserved bytes and actual metadata."""
import hashlib, importlib.util, json, pathlib, re, subprocess
ROOT=pathlib.Path(__file__).resolve().parents[3]; OUT=ROOT/'artifacts/phase-b/b00-generation-design'; HISTORY=ROOT/'docs/database/design-history/B00-1.2'
manifest=json.loads((HISTORY/'manifest.json').read_text())
for row in manifest['files']:
    b=(ROOT/row['preserved']).read_bytes();assert hashlib.sha256(b).hexdigest()==row['sha256']
    assert subprocess.check_output(['git','show',manifest['base']+':'+row['source']],cwd=ROOT)==b
for p,h in manifest['frozen_inputs'].items():assert hashlib.sha256((ROOT/p).read_bytes()).hexdigest()==h,p
base=json.loads((ROOT/'docs/acceptance/b/database-upload-addendum-metadata-evidence.json').read_text())['metadata']
current=json.loads((ROOT/'docs/acceptance/b/database-generation-addendum-metadata-evidence.json').read_text())['metadata']
old_names={row['table'] for row in base['tables']}
new_names={row['table'] for row in current['tables']}-old_names
assert new_names=={'sensitive_detection','sensitive_detection_source','background_note','background_note_source','action_suggestion','result_review','result_review_evidence','final_result_review'}
base_only={k:[x for x in values if x['table'] in old_names] for k,values in current.items()}
for k in ['tables','columns','foreign_keys']:assert base_only[k]==base[k],k
removed_indexes=[x for x in base['indexes'] if x not in base_only['indexes']]
added_indexes=[x for x in base_only['indexes'] if x not in base['indexes']]
assert not removed_indexes and added_indexes==[dict(table='sensitive_exclusion',name='PRIMARY',unique=1,seq=3,column='category',direction='A',prefix=None,type='BTREE')]
removed_checks=[x for x in base['checks'] if x not in base_only['checks']]
added_checks=[x for x in base_only['checks'] if x not in base['checks']]
assert not removed_checks and len(added_checks)==1 and added_checks[0]['table']=='sensitive_exclusion'
old_sql=(HISTORY/'ama-schema-draft.sql').read_text(); new_sql=(ROOT/'docs/database/ama-schema-draft.sql').read_text()
def declaration(sql,name):return re.search(r'CREATE TABLE '+re.escape(name)+r' \(.*?\) ENGINE=InnoDB[^;]*;',sql,re.S).group(0)
def fks(sql,name):return '\n'.join(line for line in sql.splitlines() if line.startswith('ALTER TABLE '+name+' '))
assert all(declaration(old_sql,n)==declaration(new_sql,n) for n in old_names-{'sensitive_exclusion'})
assert all(fks(old_sql,n)==fks(new_sql,n) for n in old_names)
v011=['input_batch','input_batch_file','transcript_revision','transcript_segment','transcript_confirmation','speaker','transcription_input','transcript_segment_mark']
proof=[]
for name in v011:
    a=(declaration(old_sql,name)+'\n'+fks(old_sql,name)).encode();b=(declaration(new_sql,name)+'\n'+fks(new_sql,name)).encode();assert a==b
    checks={k:[x for x in base[k] if x['table']==name]==[x for x in current[k] if x['table']==name] for k in base};assert all(checks.values())
    incoming_old=[x for x in base['foreign_keys'] if x['target']==name]; incoming_new=[x for x in current['foreign_keys'] if x['target']==name];assert incoming_old==incoming_new
    proof.append({'table':name,'declaration_and_FK_sha256':hashlib.sha256(a).hexdigest(),'byte_equal':True,'metadata_equal':checks,'incoming_FK_unchanged':True})
report={'base':manifest['base'],'initial_proposal_base':manifest['original_proposal_base'],'preservation_verified':manifest['files'],'frozen_inputs_verified':manifest['frozen_inputs'],'original_tables':len(old_names),'new_tables':sorted(new_names),'all_original_columns_tables_foreign_keys_unchanged':True,'all_original_CREATE_blocks_unchanged_except':['sensitive_exclusion'],'added_old_table_index_rows':added_indexes,'added_old_table_checks':added_checks,'v011_proof':proof,'new_table_metadata_counts':{k:len([x for x in v if x['table'] in new_names]) for k,v in current.items()},'scope':'source bytes and actual MySQL metadata equivalence; no application acceptance'}
(ROOT/'docs/acceptance/b/database-generation-addendum-delta-evidence.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'old_tables':len(old_names),'new_tables':len(new_names),'v011':len(proof),'all_frozen_inputs_verified':True,'only_existing_table_delta':'sensitive_exclusion PK category + CHECK'},ensure_ascii=False))
