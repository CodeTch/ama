from pathlib import Path
import os,subprocess,json,hashlib
root=Path.cwd();art=root/'artifacts/phase-b/b04-s3/fix-round-1';cp=':'.join(json.loads((root/'artifacts/phase-b/b04-s3/final-canonical-classpath.json').read_text())['entries'])
with (art/'sdk-2.56.0-retry-api.txt').open('w') as out:
    for cls in ['software.amazon.awssdk.core.client.config.ClientOverrideConfiguration$Builder','software.amazon.awssdk.retries.StandardRetryStrategy','software.amazon.awssdk.retries.StandardRetryStrategy$Builder','software.amazon.awssdk.retries.api.RetryStrategy','software.amazon.awssdk.retries.api.RetryStrategy$Builder']:
        subprocess.run([str(Path(os.environ['JAVA_HOME'])/'bin/javap'),'-classpath',cp,cls],stdout=out,stderr=subprocess.STDOUT,check=True)
print((art/'sdk-2.56.0-retry-api.txt').read_text())
