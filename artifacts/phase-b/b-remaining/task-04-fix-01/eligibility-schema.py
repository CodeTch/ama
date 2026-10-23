import json,sys
from pathlib import Path
from jsonschema import Draft202012Validator
r=Path.cwd(); schema=json.loads((r/'contracts/openapi/processing.yaml').read_text())
eligibility=schema['CompletedFileSummary']['properties']['intakeEligibility']
validator=Draft202012Validator(eligibility)
samples=[{'state':'ELIGIBLE','reason':None}]+[{'state':'INELIGIBLE' if reason!='SOURCE_ROUTE' else 'UNAVAILABLE','reason':reason} for reason in ['SOURCE_STATE','SOURCE_EPOCH','SOURCE_PURPOSE','SOURCE_ROUTE','SOURCE_PROVENANCE','READ_ONLY']]
errors=[]
for sample in samples:
 found=[e.message for e in validator.iter_errors(sample)];print(json.dumps({'sample':sample,'errors':found}));errors.extend(found)
assert not errors,errors
for invalid in [123,'UNKNOWN']:
 assert list(validator.iter_errors({'state':'INELIGIBLE','reason':invalid}))
print('PASS: 7 representative eligibility responses; invalid reason number/unknown rejected')
