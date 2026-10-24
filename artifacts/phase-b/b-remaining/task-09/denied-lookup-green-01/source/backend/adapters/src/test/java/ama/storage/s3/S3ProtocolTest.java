package ama.storage.s3;

import static org.junit.jupiter.api.Assertions.*;

import ama.contract.StorageContracts.*;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.*;

class S3ProtocolTest {
  @Test
  void admissionOneIsRejectedBeforeClientConstruction() {
    var c = config();
    assertEquals(
        ErrorCode.INVALID_ARGUMENT,
        assertThrows(
                StorageException.class,
                () ->
                    new S3Config(
                        c.backendId(),
                        c.endpoint(),
                        c.region(),
                        c.dataBucket(),
                        c.controlBucket(),
                        c.credentials(),
                        c.pathStyle(),
                        c.keyBudget(),
                        c.chunkBytes(),
                        c.nativeMinimum(),
                        c.nativeMaximum(),
                        c.nativeMaxParts(),
                        c.maximumObject(),
                        1,
                        c.timeout()))
            .code());
  }

  static S3Config config() {
    return new S3Config(
        "s3",
        URI.create("http://127.0.0.1:1"),
        "us-east-1",
        "data-test",
        "control-test",
        StaticCredentialsProvider.create(AwsBasicCredentials.create("unused", "unused")),
        true,
        1024,
        8 * 1024 * 1024,
        5L * 1024 * 1024,
        5L * 1024 * 1024 * 1024,
        10000,
        50L * 1024 * 1024 * 1024,
        8,
        Duration.ofSeconds(10));
  }

  @Test
  void keyEncodingPreservesCodeUnitsPrefixesAndOrderWithoutNormalizing() {
    List<String> strings =
        List.of("a", "aa", "a/", "a?", "z", "~", "é", "\ud800", "\ud83d\ude00", "\uffff");
    var encoded = strings.stream().map(S3Layout::encode).toList();
    assertEquals(
        List.of("a", "aa", "a.1", "a?", "z", "~007e", "~00e9", "~d800", "~d83d~de00", "~ffff"),
        encoded);
    assertEquals(strings, encoded.stream().map(S3Layout::decode).toList());
    for (String a : strings)
      for (String b : strings) {
        assertEquals(
            Integer.signum(a.compareTo(b)),
            Integer.signum(S3Layout.encode(a).compareTo(S3Layout.encode(b))));
        assertEquals(a.startsWith(b), S3Layout.encode(a).startsWith(S3Layout.encode(b)));
      }
    for (String invalid : List.of("~", "~0061", "~00E9", "~gggg", "é"))
      assertThrows(StorageException.class, () -> S3Layout.decode(invalid));
  }

  @Test
  void nativePlannerUsesOverflowSafeCeilingAndNeverLogicalPartNumbers() {
    var p = new NativePublisher(new S3Objects(null, config()), new S3Layout(config()));
    assertEquals(5242880, p.interval(0));
    assertEquals(5242880, p.interval(3));
    assertEquals(5242880, p.interval(5242880L * 10000));
    assertEquals(5243405, p.interval(5242880L * 10001));
    assertEquals(
        ErrorCode.INVALID_ARGUMENT,
        assertThrows(StorageException.class, () -> p.interval(Long.MAX_VALUE)).code());
  }

  @Test
  void canonicalRecordsRejectTrailingTruncatedUnknownSchemaAndNoncanonicalNumbers() {
    byte[] encoded = ControlCodec.pack("test-v1", "\ud800", "123");
    assertArrayEquals(
        new String[] {"test-v1", "\ud800", "123"}, ControlCodec.unpack(encoded, "test-v1", 3));
    assertThrows(
        StorageException.class,
        () -> ControlCodec.unpack(Arrays.copyOf(encoded, encoded.length + 1), "test-v1", 3));
    assertThrows(
        StorageException.class,
        () -> ControlCodec.unpack(Arrays.copyOf(encoded, encoded.length - 1), "test-v1", 3));
    assertThrows(StorageException.class, () -> ControlCodec.unpack(encoded, "test-v2", 3));
    assertThrows(StorageException.class, () -> ControlCodec.number("01"));
    assertThrows(StorageException.class, () -> ControlCodec.number("-1"));
  }
}
