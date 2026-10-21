import importlib.util, subprocess, json, pathlib
p=pathlib.Path(__file__).resolve().parents[1]/"tools/dev/mysql_fixture.py"
s=importlib.util.spec_from_file_location("fixture",p); m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
original=m.docker
def owned(*args):
    if args[0]=="run":
        args=list(args);args[1:1]=["--cpus","2","--memory","1g","--memory-swap","1g","--pids-limit","256"];args.append("--innodb-use-native-aio=0")
    if args[0]=="inspect" and len(args)==2:
        value=original("inspect","--format",'{{json .NetworkSettings.Ports}}',args[1]);return json.dumps([{"NetworkSettings":{"Ports":json.loads(value)}}])
    return original(*args)
m.docker=owned
m.startup_failure=lambda *a: "preserved; no cleanup"
m.start()
