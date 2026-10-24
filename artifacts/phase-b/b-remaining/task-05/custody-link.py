import json,subprocess
from pathlib import Path
p=Path(__file__).with_name("custody-link-command.json")
subprocess.run(json.loads(p.read_text()),check=True)
