package ama.task;
import ama.worker.*;import ama.contract.TaskContracts.*;import java.util.*;
/** Test-only ServiceLoader provider. Never included in any production artifact. */
public final class TaskWorkerProvider implements WorkerRuntimeProvider {
 public WorkerLoop create(){var f=new TaskFixture();var q=f.queue();String pool=System.getenv("AMA_TEST_WORKER_POOL");if(pool==null)throw new IllegalStateException("Explicit owned test pool");return new WorkerLoop(q,Map.of(pool,1),Map.of("TEST_TASK",lease->{System.out.println("HANDLING "+lease.taskId());System.out.flush();if("true".equals(System.getenv("AMA_TEST_WORKER_BLOCK")))Thread.sleep(120000);return f.success(lease.spec());}),"proc-"+ProcessHandle.current().pid());}
}
