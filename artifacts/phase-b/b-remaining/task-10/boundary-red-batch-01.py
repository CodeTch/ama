import subprocess,sys
commands=[['python3','artifacts/phase-b/b-remaining/task-10/run-window.py','f2-original-adopt-02','it','Task10F2OriginalAdoptIT'],['python3','artifacts/phase-b/b-remaining/task-10/run-window.py','saved-boundaries-red-01','it','Task10*BoundaryIT']]
for command in commands:
 result=subprocess.run(command)
 print({'selector':command[-1],'exit':result.returncode},flush=True)
