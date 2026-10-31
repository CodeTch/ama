package ama.model;

import ama.contract.SpeechContracts;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HttpSpeechModelPortTest {
    static byte[] wav(int samples) {
        var b=ByteBuffer.allocate(44+samples*2).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+samples*2).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        b.putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16);
        b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(samples*2);return b.array();
    }
    @Test void wavEncodingHostAndSourceOffsetsArePreservedWithoutInventingOptionalFields() throws Exception {
        byte[] audio=wav(32000);var observed=new HashMap<String,byte[]>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange -> {
            assertEquals("inference.test.invalid",exchange.getRequestHeaders().getFirst("Host"));
            assertEquals("Bearer test-only",exchange.getRequestHeaders().getFirst("Authorization"));
            String path=exchange.getRequestURI().getPath();observed.put(path,exchange.getRequestBody().readAllBytes());
            String response=switch(path) {
                case "/vad" -> "{\"segments\":[[0,1000]]}";
                case "/diarize" -> "{\"timeline\":[[0,1000,\"speaker-0\"]]}";
                case "/embed" -> "{\"embedding\":[0.1,0.2]}";
                case "/inference/asr-punc/modelserving/punc" -> "{\"text\":\"原始，文字。\"}";
                default -> "{\"text\":\"原始文字\"}";
            };
            byte[] raw=response.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,raw.length);exchange.getResponseBody().write(raw);exchange.close();
        });server.start();
        try(var port=new HttpSpeechModelPort(HttpSpeechModelPort.Config.at(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"inference.test.invalid","Bearer test-only","fixture-embedding",2,Duration.ofSeconds(2)),clip -> new ByteArrayInputStream(audio))) {
            var clip=new SpeechContracts.WavClip("source-file",16000,32000);
            var speech=port.vad(clip);assertEquals(16000,speech.getFirst().startSample());assertEquals(32000,speech.getFirst().endSample());
            var speakers=port.diarize(clip,speech);assertNull(speakers.getFirst().enterpriseId());
            var raw=port.recognize(clip);assertEquals("原始文字",raw.text());assertNull(raw.tokens());
            assertEquals("原始，文字。",port.punctuate(raw.text()));assertEquals(2,port.embed(clip).dimension());
            var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
            assertArrayEquals(audio,Base64.getDecoder().decode(mapper.readTree(observed.get("/vad")).get("audio_b64").asText()));
            assertEquals("[[0,1000]]",mapper.readTree(observed.get("/diarize")).get("vad_segments").toString());
            assertArrayEquals(audio,observed.get("/inference/asr-nano/modelserving/asr"));assertArrayEquals(audio,observed.get("/embed"));
        }finally{server.stop(0);}
    }
    @Test void invalidRangeTokensResponseBudgetAndRedirectAreRejected() throws Exception {
        var body=new AtomicReference<>("{\"text\":\"text\",\"tokens\":[[\"字\",0,100]]}");var status=new java.util.concurrent.atomic.AtomicInteger(200);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange -> {exchange.getRequestBody().readAllBytes();byte[] raw=body.get().getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.getResponseHeaders().set("Location","http://127.0.0.1:1/never");exchange.sendResponseHeaders(status.get(),raw.length);exchange.getResponseBody().write(raw);exchange.close();});server.start();
        try(var port=new HttpSpeechModelPort(HttpSpeechModelPort.Config.at(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"inference.test.invalid",null,"fixture",2,Duration.ofSeconds(1)),c -> new ByteArrayInputStream(wav(32000)))) {
            var clip=new SpeechContracts.WavClip("f",0,32000);
            assertNull(port.recognize(clip).tokens().getFirst().confidence());
            body.set("{\"text\":\"text\",\"tokens\":[[\"字\",0,2001]]}");assertThrows(HttpSpeechModelPort.InvalidResponse.class,()->port.recognize(clip));
            body.set("{\"text\":\""+"x".repeat(1048577)+"\"}");assertThrows(HttpSpeechModelPort.InvalidResponse.class,()->port.recognize(clip));
            body.set("{}");status.set(302);assertThrows(HttpSpeechModelPort.Unavailable.class,()->port.recognize(clip));
            status.set(429);assertThrows(CompanySpeechAdapter.RateLimited.class,()->port.recognize(clip));
        }finally{server.stop(0);}
    }
    @Test void deadlineClosesSlowResponseAndReturnsCapacity() throws Exception {
        var slow=new java.util.concurrent.atomic.AtomicBoolean(true);
        var threads=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(threads);
        server.createContext("/",exchange -> {exchange.getRequestBody().readAllBytes();byte[] raw="{\"text\":\"text\"}".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,raw.length);if(slow.get())try{Thread.sleep(1500);}catch(InterruptedException e){Thread.currentThread().interrupt();}try{exchange.getResponseBody().write(raw);}catch(java.io.IOException closed){}finally{exchange.close();}});server.start();
        try(var port=new HttpSpeechModelPort(HttpSpeechModelPort.Config.at(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"inference.test.invalid",null,"fixture",2,Duration.ofMillis(200)),c -> new ByteArrayInputStream(wav(32000)))) {
            long start=System.nanoTime();assertThrows(CompanySpeechAdapter.TimedOut.class,()->port.recognize(new SpeechContracts.WavClip("f",0,32000)));
            assertTrue(Duration.ofNanos(System.nanoTime()-start).toMillis()<1200);
            slow.set(false);assertEquals("text",port.recognize(new SpeechContracts.WavClip("f",0,32000)).text());
        }finally{server.stop(0);threads.shutdownNow();threads.close();}
    }
    @Test void concurrencyAdmissionPrecedesWavReadingAndEncoding() throws Exception {
        var arrived=new java.util.concurrent.CountDownLatch(2);var release=new java.util.concurrent.CountDownLatch(1);var reads=new java.util.concurrent.atomic.AtomicInteger();
        var threads=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(threads);
        server.createContext("/",exchange->{exchange.getRequestBody().readAllBytes();arrived.countDown();try{release.await(2,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}byte[] raw="{\"text\":\"text\"}".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,raw.length);exchange.getResponseBody().write(raw);exchange.close();});server.start();
        try(var port=new HttpSpeechModelPort(HttpSpeechModelPort.Config.at(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"inference.test.invalid",null,"fixture",2,Duration.ofSeconds(3)),c->{reads.incrementAndGet();return new ByteArrayInputStream(wav(32000));})) {
            var clip=new SpeechContracts.WavClip("f",0,32000);
            var first=threads.submit(()->port.recognize(clip));var second=threads.submit(()->port.recognize(clip));
            assertTrue(arrived.await(2,java.util.concurrent.TimeUnit.SECONDS));
            try{assertThrows(HttpSpeechModelPort.Unavailable.class,()->port.recognize(clip));assertEquals(2,reads.get());}
            finally{release.countDown();}
            assertEquals("text",first.get().text());assertEquals("text",second.get().text());
        }finally{release.countDown();server.stop(0);threads.shutdownNow();threads.close();}
    }
}
