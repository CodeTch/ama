package ama.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class TranscriptMediaErrorsTest {
 private MockHttpServletResponse partialDownload() throws Exception {
  var response=new MockHttpServletResponse();
  response.setStatus(206);
  response.setHeader("Content-Disposition","inline");
  response.setContentType("audio/wav");
  response.setContentLengthLong(100);
  response.getOutputStream().write(new byte[]{1,2,3});
  return response;
 }

 @Test void revokedCommittedRangeEndsWithoutAppendingAnErrorDocument() throws Exception {
  var response=partialDownload();
  response.flushBuffer();
  new TranscriptErrors().denied(response);
  assertEquals(206,response.getStatus());
  assertArrayEquals(new byte[]{1,2,3},response.getContentAsByteArray());
 }

 @Test void revokedBufferedRangeReturnsOnlyTheAccessError() throws Exception {
  var response=partialDownload();
  new TranscriptErrors().denied(response);
  assertEquals(403,response.getStatus());
  assertNull(response.getHeader("Content-Disposition"));
  assertNull(response.getHeader("Content-Length"));
  assertTrue(response.getContentAsString().startsWith("{\"error\":"));
  assertTrue(response.getContentAsString().contains("MEETING_FORBIDDEN"));
 }
}
