import subprocess,sys
for name,selector in [("final-required-01","Task10FinalCheck*IT"),("affected-regression-01","Task10Regression*IT")]:
 r=subprocess.run([sys.executable,"artifacts/phase-b/b-remaining/task-10/run-window.py",name,"it",selector]);print({"selector":selector,"exit":r.returncode},flush=True)
 if r.returncode:break
