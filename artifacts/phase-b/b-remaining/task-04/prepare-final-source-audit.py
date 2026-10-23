from pathlib import Path
p=Path('artifacts/phase-b/b-remaining/task-04');s=(p/'audit-final.py').read_text();s=s[:s.index('# Guarded read-only SQL')]+'''print('FINAL_SOURCE_AND_XML_AUDIT_PASS',len(sources))
print('FINAL_COUNTS',json.dumps({k:summary[k]['total'] for k in ['final-regressions','config-restart-final','all-unit-final-resumed']}))
''';(p/'audit-sources-final.py').write_text(s,newline='\n')
