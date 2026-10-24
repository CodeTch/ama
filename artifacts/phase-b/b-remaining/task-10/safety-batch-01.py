import subprocess,sys
for name,selector in [("retained-stale-green-01","Task10FinalRetainedStaleIT"),("safety-boundaries-01","Task10Safety*IT")]:
 r=subprocess.run([sys.executable,"artifacts/phase-b/b-remaining/task-10/run-window.py",name,"it",selector]);print({"selector":selector,"exit":r.returncode},flush=True)
