"""Extract the exact audited response-schema closure. No path objects or runtime dependencies."""
import json, pathlib, sys
root=pathlib.Path(__file__).resolve().parents[2]
selected={'minutes.yaml':['MinutesCandidates','MinutesCandidate','SavedHistory','SavedBodyDetail','SavedSetDetail','SavedSettings','SavedReviewHistory','SavedReviewDetail','SavedSource','SavedReadiness'], 'publications.yaml':['FinalReadiness','FinalDetail','FinalHistory','FinalAcknowledgment','PublicationCurrent','PublicationAcknowledgment','SubmissionPage','AttemptPage','DestinationLibraryPage','DestinationFolderPage']}
annotations={'description','title','example','examples','deprecated','readOnly','writeOnly','default'}
keywords={'$ref','type','const','enum','oneOf','anyOf','allOf','required','properties','additionalProperties','items','maxItems','minItems','uniqueItems','pattern','maxLength','minLength','format','minimum','maximum','exclusiveMinimum','exclusiveMaximum'}
result={}
def include(file,name):
 key=file+'#/'+name
 if key in result:return key
 result[key]={}
 raw=json.loads((root/'contracts/openapi'/file).read_text())[name]
 def walk(v):
  if not isinstance(v,dict):raise ValueError('Schema object required')
  unknown=set(v)-keywords-annotations
  if unknown:raise ValueError(str(unknown))
  out={}
  for k,x in v.items():
   if k in annotations:continue
   if k=='$ref':
    path,pointer=x.split('#/');out[k]=include(path.removeprefix('./') or file,pointer)
   elif k=='properties':out[k]={n:walk(s) for n,s in x.items()}
   elif k in ('items','additionalProperties') and isinstance(x,dict):out[k]=walk(x)
   elif k in ('oneOf','anyOf','allOf'):out[k]=[walk(s) for s in x]
   else:out[k]=x
  return out
 result[key]=walk(raw)
 return key
for f,names in selected.items():
 for n in names:include(f,n)
text='// Generated from contracts/openapi by scripts/generate-minutes-guards.py; do not edit.\nexport const responseSchemas:Record<string,unknown>='+json.dumps(result,ensure_ascii=False,separators=(',',':'))+';\n'
output=root/'web/src/shared/api/minutes-response-schemas.ts'
if '--check' in sys.argv:assert output.read_text()==text,'Response schema drift'
else:output.write_text(text)
print(str(len(result))+' response schemas')
