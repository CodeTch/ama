import {test} from 'node:test';
import assert from 'node:assert/strict';
import {confirmationCheckPage} from '../../src/features/minutes/final-confirmation-checks';

test('resolving checks on a later page keeps remaining actions reachable',()=>{
 assert.equal(confirmationCheckPage(2,41),2);
 assert.equal(confirmationCheckPage(2,40),1);
 assert.equal(confirmationCheckPage(1,20),0);
 assert.equal(confirmationCheckPage(2,0),0);
 assert.equal(confirmationCheckPage(undefined,21),0);
});
