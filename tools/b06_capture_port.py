import json,pathlib,subprocess,shlex
root=pathlib.Path.cwd()
e=root/"artifacts/phase-b/b06-queue-seams"
p=root/".tools/b06-queue-seams"
owned=json.loads((p/"owned.json").read_text())
cmd=["docker","inspect","--format","{{json .NetworkSettings.Ports}}",owned["name"]]
r=subprocess.run(cmd,text=True,capture_output=True)
(e/"fixture-allocated-ports.log").write_text(r.stdout+r.stderr)
(e/"fixture-allocated-ports.command.json").write_text(json.dumps({"argv":cmd,"exit_code":r.returncode},indent=2)+"\n")
assert r.returncode==0
ports=json.loads(r.stdout)["3306/tcp"]
assert len(ports)==1 and ports[0]["HostIp"]=="127.0.0.1" and 1<=int(ports[0]["HostPort"])<=65535
port=ports[0]["HostPort"]
credentials=dict(line.split("=",1) for line in (p/"mysql.env").read_text().splitlines())
v={"AMA_TEST_DATABASE_URL":"jdbc:mysql://127.0.0.1:"+port+"/ama_b01_fixture","AMA_TEST_DATABASE_USERNAME":"ama_test","AMA_TEST_DATABASE_PASSWORD":credentials["MYSQL_PASSWORD"],"AMA_TEST_CONTAINER":owned["name"]}
(p/"env.sh").write_text("".join("export "+k+"="+shlex.quote(x)+"\n" for k,x in v.items()))
(p/"env.sh").chmod(0o600)
(e/"fixture-setup-correction.md").write_text("The initial fixture launcher recorded requested HostConfig.PortBindings, where an ephemeral HostPort remains empty. Before any JDBC invocation, selected NetworkSettings.Ports supplied the actual allocated loopback port. Same owned container; no second creation, no restart, no reset. This preparation correction is not an assertion RED.\n")
print("selected owned actual loopback port="+port)
