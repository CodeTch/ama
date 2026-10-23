import hashlib,struct,json
from pathlib import Path
def encode(value):
 if isinstance(value,str):
  b=value.encode();return b'\x04'+struct.pack('>Q',len(b))+b
 if isinstance(value,int):return b'\x03'+struct.pack('>Q',value)
 if isinstance(value,dict):return b'\x06'+struct.pack('>Q',len(value))+b''.join(encode(k)+encode(value[k]) for k in sorted(value))
 raise AssertionError(type(value))
value={'profile':'ama.transcript-import-producer.v1','kind':'TASK_PRODUCER','data':{'taskType':'TRANSCRIPT_IMPORT_V1','meetingId':'00000000-0000-0000-0000-000000000001','inputId':'00000000-0000-0000-0000-000000000002','inputFingerprint':'a'*64,'businessVersion':9007199254740993,'cancelEpoch':7}}
expected=hashlib.sha256(encode(value)).hexdigest()
Path('artifacts/phase-b/b-remaining/task-04/producer-golden.json').write_text(json.dumps({'independent_algorithm':'Python struct big-endian canonical tags 3/4/6, UTF8 key order','value':value,'hex':encode(value).hex(),'sha256':expected},indent=2)+'\n')
code='''package ama.transcript;
import ama.contract.MinutesContracts.CanonicalBudget;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TranscriptImportProducerTest {
 @Test void independentProducerGoldenPreservesLongBitsAndEverySemanticField() {
  var budget=new CanonicalBudget(65536,8,128,1024,4096);
  String actual=TranscriptImportProducer.compute("00000000-0000-0000-0000-000000000001","00000000-0000-0000-0000-000000000002","a".repeat(64),9007199254740993L,7,budget,()->false);
  assertEquals("GOLDEN",actual);
  assertNotEquals(actual,TranscriptImportProducer.compute("00000000-0000-0000-0000-000000000001","00000000-0000-0000-0000-000000000002","a".repeat(64),9007199254740992L,7,budget,()->false));
  assertThrows(ama.contract.MinutesContracts.Failure.class,()->TranscriptImportProducer.compute("00000000-0000-0000-0000-000000000001","00000000-0000-0000-0000-000000000002","a".repeat(64),9007199254740993L,7,budget,()->true));
 }
}
'''.replace('GOLDEN',expected)
Path('backend/core/src/test/java/ama/transcript/TranscriptImportProducerTest.java').write_text(code)
print(expected)
