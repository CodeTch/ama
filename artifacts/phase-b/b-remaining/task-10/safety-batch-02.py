import subprocess,sys
for name,selector in [("retained-stale-green-02","Task10FinalRetainedStaleIT"),("rollback-cas-budget-02","Task10SafetyRollbackCasIT")]:
 r=subprocess.run([sys.executable,"artifacts/phase-b/b-remaining/task-10/run-window.py",name,"it",selector]);print({"selector":selector,"exit":r.returncode},flush=True)
