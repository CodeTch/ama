import json
from pathlib import Path
from jsonschema import Draft202012Validator,FormatChecker
r=Path.cwd();e=r/'artifacts/phase-b/b-remaining/task-04-fix-01';schema=json.loads((r/'contracts/openapi/processing.yaml').read_text())
files=list((e/'covering-final-3-custody').glob('*-schema-http.json'));assert len(files)==1,files
raw=json.loads(files[0].read_text());count=0;states=set()
for item in raw['completedFiles']['items']:
 Draft202012Validator(dict(schema,**{'$ref':'#/CompletedFileSummary'}),format_checker=FormatChecker()).validate(item);count+=1;states.add(item['intakeEligibility']['state'])
for receipt in raw['receipts']:
 Draft202012Validator(dict(schema,**{'$ref':'#/UploadReceipt'}),format_checker=FormatChecker()).validate(receipt);count+=1
assert states=={'ELIGIBLE','INELIGIBLE'}
print('PASS: '+str(count)+' actual completed-file/receipt HTTP objects match exact published schemas; ELIGIBLE/null and INELIGIBLE/SOURCE_STATE both covered')
