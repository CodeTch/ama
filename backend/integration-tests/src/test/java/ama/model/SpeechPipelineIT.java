package ama.model;

import ama.contract.SpeechContracts;
import ama.worker.TranscriptionHandler;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Fixed sibling harness protocol evidence, explicitly not real model quality or latency acceptance. */
class SpeechPipelineIT {
    static byte[] wav(int samples) {
        var b=ByteBuffer.allocate(44+samples*2).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+samples*2).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        b.putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16);
        b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(samples*2);return b.array();
    }
    @Test void twoSecondWavPassesEveryRealHttpStageWithSourceOffsets() {
        String origin=System.getenv("AMA_SPEECH_HARNESS_ORIGIN");
        if(!"http://127.0.0.1:8792".equals(origin))throw new IllegalArgumentException("Explicit fixed sibling harness required");
        var clip=new SpeechContracts.WavClip("owned-two-second-wav",16000,32000);var stages=new LinkedHashMap<String,Object>();
        try(var http=new HttpSpeechModelPort(HttpSpeechModelPort.Config.at(URI.create(origin),"inference.dev.zsfund.com",null,"harness-fixture-embedding-v1",256,Duration.ofSeconds(3)),c->new ByteArrayInputStream(wav(32000)))) {
            var result=new TranscriptionHandler(new CompanySpeechAdapter(http)).transcribe(clip,stages::put);
            assertEquals(List.of("VAD","DIARIZATION","ASR","ENHANCEMENT","EMBEDDING"),List.copyOf(stages.keySet()));
            assertSame(result.raw(),stages.get("ASR"));assertFalse(result.raw().text().isBlank());assertEquals(256,result.embedding().dimension());
            assertTrue(result.speech().stream().allMatch(s->s.startSample()>=16000&&s.endSample()<=48000));
            assertTrue(result.speakers().stream().allMatch(s->s.enterpriseId()==null));
            if(result.raw().tokens()!=null)assertTrue(result.raw().tokens().stream().allMatch(t->t.confidence()==null&&t.startSample()>=16000&&t.endSample()<=48000));
        }
    }
}
