import {test} from 'node:test';import assert from 'node:assert/strict';
import {scalarOffset,utf16Offset,uniqueAnchor} from '../../src/shared/scalar-anchors.js';
test('emoji offsets count Unicode scalars without UTF16 or revision-number round trips',()=>{
 const text='甲😀乙𝄞丙';assert.equal(scalarOffset(text,3),'2');assert.equal(utf16Offset(text,'4'),6);assert.throws(()=>scalarOffset(text,2));assert.throws(()=>utf16Offset(text,'9007199254740993'));assert.throws(()=>utf16Offset(text,'01'));
});
test('unpaired surrogates and out-of-range or fractional selections are rejected',()=>{
 for(const bad of ['\ud800','a\udc00','\ud800x']){assert.throws(()=>scalarOffset(bad,0));assert.throws(()=>uniqueAnchor(bad,'a'));}
 for(const offset of [-1,0.5,99])assert.throws(()=>scalarOffset('abc',offset));
});
test('ambiguous quotes cannot silently produce a different evidence anchor',()=>{
 assert.deepEqual(uniqueAnchor('甲😀乙','😀'),{start:'1',end:'2'});assert.equal(uniqueAnchor('甲😀乙😀','😀'),null);assert.equal(uniqueAnchor('abc',''),null);assert.equal(uniqueAnchor('é','é'),null);
});
