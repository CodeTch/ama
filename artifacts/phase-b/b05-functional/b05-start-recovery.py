import importlib.util,json,pathlib,subprocess,re,sys
root=pathlib.Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("fixture",root/"tools/dev/mysql_fixture.py")
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
import socket
with socket.socket() as reservation:
    reservation.bind(("127.0.0.1",0));fixed_port=reservation.getsockname()[1]
original=m.docker
log=root/"artifacts/phase-b/b05-functional/fixture-wrapper.jsonl"
def docker(*args):
    if args[0]=="run":
        name=args[args.index("--name")+1]
        credentials=pathlib.Path(args[args.index("--env-file")+1])
        expected=("run","-d","--name",name,"--label","ama.test-fixture=b01","--publish","127.0.0.1::3306","--env-file",str(credentials),m.IMAGE,"--character-set-server=utf8mb4","--collation-server=utf8mb4_0900_ai_ci","--default-time-zone=+00:00")
        assert args==expected and re.fullmatch("ama-test-b01-[0-9a-f]{12}",name)
        assert credentials.parent.parent==root/".tools/mysql-fixture" and credentials.parent.name==name
        args=tuple("127.0.0.1:"+str(fixed_port)+":3306" if arg=="127.0.0.1::3306" else arg for arg in args)+( "--innodb-use-native-aio=OFF",)
        with log.open("a") as out:out.write(json.dumps({"mode":"B05 fixed loopback recovery functional only; no native IO qualification","args":args})+"\n")
    if args[0]=="rm":
        # Preserve failed startup rather than invoking the old helper cleanup.
        name=args[-1]
        info=json.loads(original("inspect",name))[0]
        safe={"Id":info["Id"],"Name":info["Name"],"State":info["State"],"Mounts":info["Mounts"],"AutoRemove":info["HostConfig"]["AutoRemove"]}
        (log.parent/(name+"-failed-start.json")).write_text(json.dumps(safe,indent=2))
        raise RuntimeError("Startup failed; owned fixture retained; no rm performed")
    return original(*args)
m.docker=docker
m.start()
