package ama.api;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.*;
import ama.db.TransactionRunner;
import ama.recording.RecordingService;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RecordingControllerTest {
    final RecordingService service=new RecordingService();
    final TransactionRunner tx=mock(TransactionRunner.class);
    final MeetingAccess access=mock(MeetingAccess.class);
    final ama.recording.JdbcRecordingWriterLeases leases=mock(ama.recording.JdbcRecordingWriterLeases.class);
    final ama.recording.RecordingMutationGuard mutations=mock(ama.recording.RecordingMutationGuard.class);
    final RecordingIngressBudget ingress=mock(RecordingIngressBudget.class);
    final String collector=java.util.UUID.randomUUID().toString();
    final MeetingAccess.Locked held=mock(MeetingAccess.Locked.class);
    final Actor owner=new Actor("owner",Status.ACTIVE,Set.of(Role.USER));
    final RecordingController controller;
    final String archiveTask=java.util.UUID.randomUUID().toString();
    final java.util.concurrent.atomic.AtomicBoolean admissionFails=new java.util.concurrent.atomic.AtomicBoolean();
    RecordingControllerTest() {
        when(tx.required(any())).thenAnswer(i -> ((Callable<?>)i.getArgument(0)).call());
        when(access.lock(any(),anyString())).thenReturn(held);
        controller=new RecordingController(service,new MockEnvironment(),tx,access,(actor,session)->{if(admissionFails.get())throw new ama.contract.FileContracts.Unavailable();return archiveTask;},leases,mutations,ingress);
    }
    MockHttpServletRequest request() {
        var r=new MockHttpServletRequest();r.setAttribute(SessionSecurityFilter.SESSION,new ResolvedSession(owner,"csrf",java.time.Instant.now().plusSeconds(600)));return r;
    }
    static String sha(MockHttpServletRequest request)throws Exception{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(request.getContentAsByteArray()));}
    @Test void missingAuthenticatedActorCannotOpenRecording() throws Exception {
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new RecordingErrors()).build();
        mvc.perform(post("/api/v1/meetings/meeting-1/recordings")).andExpect(status().isForbidden());
        verifyNoInteractions(access);
    }
    @Test void localHttpBoundaryReturnsDurableAckAndRejectsGapFinish() throws Exception {
        var s=controller.create("meeting-1",request()).getBody();assertNotNull(s);
        var r=request();r.setContent(new byte[]{1,2,3,4});
        assertEquals(1,controller.chunk(s.sessionId(),s.writerEpoch(),s.partId(),1,0,2,r,sha(r),collector).highestContiguousSeq());
        var finished=controller.finish(s.sessionId(),s.writerEpoch(),Map.of(s.partId(),1L),request(),collector);assertEquals(s.sessionId(),finished.sessionId());assertEquals(archiveTask,finished.archiveTaskId());
        verify(access,times(3)).require(held,MeetingAccess.Action.EDIT);
    }
    @Test void failedArchiveAdmissionLeavesSealedBytesForExactFinishRetry()throws Exception {
        var s=controller.create("meeting-1",request()).getBody();var r=request();r.setContent(new byte[]{1,2,3,4});
        controller.chunk(s.sessionId(),1,s.partId(),1,0,2,r,sha(r),collector);admissionFails.set(true);
        assertThrows(ama.contract.FileContracts.Unavailable.class,()->controller.finish(s.sessionId(),1,Map.of(s.partId(),1L),request(),collector));
        assertEquals(1,service.ack(s.sessionId(),s.partId()).highestContiguousSeq());admissionFails.set(false);
        assertEquals(archiveTask,controller.finish(s.sessionId(),1,Map.of(s.partId(),1L),request(),collector).archiveTaskId());
    }
    @Test void currentPermissionIsRequiredForEverySessionOperation() throws Exception {
        var s=service.open("protected-meeting","local");
        doThrow(new MeetingAccess.AccessDeniedException()).when(access).require(held,MeetingAccess.Action.EDIT);
        assertThrows(MeetingAccess.AccessDeniedException.class,() -> controller.create("protected-meeting",request()));
        var r=request();r.setContent(new byte[4]);
        assertThrows(MeetingAccess.AccessDeniedException.class,() -> controller.chunk(s.sessionId(),1,s.partId(),1,0,2,r,sha(r),collector));
        assertThrows(MeetingAccess.AccessDeniedException.class,() -> controller.pause(s.sessionId(),1,request(),collector));
        assertThrows(MeetingAccess.AccessDeniedException.class,() -> controller.resume(s.sessionId(),1,request(),collector));
        assertThrows(MeetingAccess.AccessDeniedException.class,() -> controller.finish(s.sessionId(),1,Map.of(s.partId(),1L),request(),collector));
        assertThrows(MeetingAccess.AccessDeniedException.class,() -> controller.ack(s.sessionId(),s.partId(),request()));
        assertThrows(MeetingAccess.AccessDeniedException.class,() -> controller.current(s.sessionId(),request()));
        assertThrows(MeetingAccess.AccessDeniedException.class,() -> controller.nextPart(s.sessionId(),1,request(),collector));
        assertEquals(0,service.ack(s.sessionId(),s.partId()).highestContiguousSeq());
        verify(access,times(8)).lock(owner,"protected-meeting");
    }
    @Test void binaryInputIsBoundedBeforeAnyWrite() {
        var s=service.open("meeting","local");var r=request();r.setContent(new byte[64001]);
        assertThrows(RecordingController.TooLarge.class,() -> controller.chunk(s.sessionId(),1,s.partId(),1,0,32000,r,sha(r),collector));
        verifyNoInteractions(access);assertEquals(0,service.ack(s.sessionId(),s.partId()).highestContiguousSeq());
    }
    @Test void deniedCollectorCannotPersistOrPauseEvenWithCurrentOwnerAccess()throws Exception {
        var s=service.open("meeting","local");doThrow(new RecordingService.StaleWriter()).when(leases).require(s.sessionId(),1,collector);
        var r=request();r.setContent(new byte[4]);
        assertThrows(RecordingService.StaleWriter.class,()->controller.chunk(s.sessionId(),1,s.partId(),1,0,2,r,sha(r),collector));
        assertThrows(RecordingService.StaleWriter.class,()->controller.pause(s.sessionId(),1,request(),collector));
        assertThrows(RecordingService.StaleWriter.class,()->controller.nextPart(s.sessionId(),1,request(),collector));
        assertThrows(RecordingService.StaleWriter.class,()->controller.resume(s.sessionId(),1,request(),collector));
        assertThrows(RecordingService.StaleWriter.class,()->controller.finish(s.sessionId(),1,Map.of(s.partId(),1L),request(),collector));
        assertEquals(0,service.ack(s.sessionId(),s.partId()).highestContiguousSeq());assertFalse(service.finished(s.sessionId()));
    }
}
