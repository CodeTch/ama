from pathlib import Path
import json,subprocess
p=Path(__file__).parent
with (p/"output.log").open("w") as log: result=subprocess.run(json.loads((p/"command.json").read_text()),stdout=log,stderr=subprocess.STDOUT)
print("exit",result.returncode)
raise SystemExit(result.returncode)
