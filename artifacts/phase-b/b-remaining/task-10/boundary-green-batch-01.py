import subprocess
for command in [['python3','artifacts/phase-b/b-remaining/task-10/run-window.py','f2-original-adopt-03','it','Task10F2OriginalAdoptIT'],['python3','artifacts/phase-b/b-remaining/task-10/run-window.py','saved-boundaries-green-01','it','Task10*BoundaryIT']]:
 result=subprocess.run(command)
 print({'selector':command[-1],'exit':result.returncode},flush=True)
