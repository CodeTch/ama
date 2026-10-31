import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createResampler} from './resample';

test('48kHz one second produces 16000 samples',()=>{
 const r=createResampler(48000);const out=r.process(new Float32Array(48000));assert.equal(out.length,16000);assert.equal(r.flush().length,0);
});

test('resampling keeps state across frames',()=>{
 const r=createResampler(48000);const a=r.process(new Float32Array(24000).fill(.25));const b=r.process(new Float32Array(24000).fill(.5));
 assert.equal(a.length+b.length,16000);assert.equal(a.at(-1),Math.round(.25*32767));assert.equal(b[0],Math.round(.5*32767));
});

test('fractional resampling is independent of AudioWorklet frame boundaries',()=>{
 const input=Float32Array.from({length:4410},(_,i)=>Math.sin(i*.13));
 const whole=createResampler(44100),expected=[...whole.process(input),...whole.flush()];
 const framed=createResampler(44100),actual:number[]=[];
 for(let i=0;i<input.length;i+=7)actual.push(...framed.process(input.slice(i,i+7)));
 actual.push(...framed.flush());assert.deepEqual(actual,expected);
});

test('upsampling waits for the next frame instead of duplicating the boundary sample',()=>{
 const input=Float32Array.from({length:100},(_,i)=>Math.sin(i*.13));
 const whole=createResampler(8000),expected=[...whole.process(input),...whole.flush()];
 const framed=createResampler(8000),actual:number[]=[];
 for(let i=0;i<input.length;i+=7)actual.push(...framed.process(input.slice(i,i+7)));
 actual.push(...framed.flush());assert.deepEqual(actual,expected);
});
