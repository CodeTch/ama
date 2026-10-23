from pathlib import Path
import json,subprocess
p=Path(__file__).parent
with (p/"output-link.log").open("w") as f: r=subprocess.run(json.loads((p/"command-link.json").read_text()),stdout=f,stderr=subprocess.STDOUT)
print("exit",r.returncode)
raise SystemExit(r.returncode)
