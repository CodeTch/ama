import subprocess,sys
for name,selector in [("closing02-original-settlement-01","Task10ClosingOriginalSettlementIT"),("closing02-second-original-get-01","Task10ClosingSecondLookupIT")]:
 r=subprocess.run([sys.executable,"artifacts/phase-b/b-remaining/task-10/run-window.py",name,"it",selector]);print({"selector":selector,"exit":r.returncode},flush=True)
 if r.returncode:break
