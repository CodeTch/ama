import json
from pathlib import Path
import unittest
import yaml
from jsonschema import Draft202012Validator, FormatChecker, ValidationError
ROOT=Path(__file__).resolve().parents[2]/'contracts/openapi'
def resolve(node,file):
 if isinstance(node,list):return [resolve(v,file) for v in node]
 if not isinstance(node,dict):return node
 if '$ref' in node:
  location,pointer=node['$ref'].split('#',1);target=(file.parent/location).resolve() if location else file
  assert target.is_relative_to(ROOT.resolve())
  value=yaml.safe_load(target.read_text())
  for part in pointer.lstrip('/').split('/'):value=value[part.replace('~1','/').replace('~0','~')]
  return resolve(value,target)
 return {k:resolve(v,file) for k,v in node.items()}
class IntentConflictContract(unittest.TestCase):
 def test_actual_stale_response_shape_has_closed_route_specific_contract(self):
  file=ROOT/'minutes.yaml';doc=json.loads(file.read_text());schema=resolve(doc['savedIntents']['post']['responses']['409']['content']['application/json']['schema'],file)
  validator=Draft202012Validator(schema,format_checker=FormatChecker())
  # Exact shape of JdbcMinutesIntentReceipts.StaleIntent.response; schema fixture only, not runtime evidence.
  current=dict(meetingId='cd2e160e-6b11-4f4e-871e-bb8aa90ecdee',state='KNOWN',intentEpochToken='1',observation=dict(revision=9007199254740993,revisionToken='9007199254740993',accessRevision=0,accessRevisionToken='0',currentConfirmationId=None,currentMinutesRevisionId=None))
  value=dict(error=dict(code='REVISION_CONFLICT'),current=current);validator.validate(value)
  for bad in [dict(error=value['error']),dict(value,extra=True),dict(value,error=dict(code='OTHER')),dict(value,current=dict(current,observation={}))]:
   with self.assertRaises(ValidationError):validator.validate(bad)
if __name__=='__main__':unittest.main()
