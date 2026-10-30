import {test} from 'node:test';import assert from 'node:assert/strict';import {validRecording} from '../../src/features/recording/recording-store';
const actor='owner',meeting='00000000-0000-4000-8000-000000000001',part='00000000-0000-4000-8000-000000000002';
const record={schema:1,actor,meeting,finished:false,parts:[{id:part,index:1,name:'录音片段 01.webm',mime:'audio/webm;codecs=opus',state:'PAUSED',elapsedMs:1200,savedElapsedMs:1100,bytes:3,chunks:[part+':1'],incomplete:false}]};
test('recording checkpoint is finite, actor/meeting scoped and contains only its own ordered chunk references',()=>{
 assert(validRecording(record,actor,meeting));for(const changes of [{actor:'foreign'},{meeting:part},{schema:2},{parts:[{...record.parts[0],chunks:['foreign:1']}]},{parts:[{...record.parts[0],chunks:[part+':1',part+':1']}]},{parts:[{...record.parts[0],bytes:32*1024*1024+1}]},{parts:[{...record.parts[0],savedElapsedMs:1300}]},{parts:[{...record.parts[0],elapsedMs:Infinity}]},{parts:[{...record.parts[0],incomplete:'no'}]},{parts:[{...record.parts[0],index:2}]},{unexpected:true}])assert.equal(validRecording({...record,...changes},actor,meeting),false,JSON.stringify(changes));
});
