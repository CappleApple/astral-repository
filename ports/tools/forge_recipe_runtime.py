"""Prepare an isolated production Forge 1.20.1 recipe compatibility run from cached installs."""
from pathlib import Path
import argparse
import json
import shutil
import shlex
import re

parser=argparse.ArgumentParser()
parser.add_argument("kind",choices=["server","server-plain","emi","jei","jei-latest"])
parser.add_argument("name")
args=parser.parse_args()
root=Path(__file__).resolve().parents[2]
project=root/"ports/forge-1.20.1"
runtime=project/"build/recipe-runtime"
run=runtime/args.name
if run.exists():raise SystemExit("Refusing to overwrite an earlier runtime run")
(run/"mods").mkdir(parents=True)
version=re.search(r"(?m)^version\s*=\s*['\"]([^'\"]+)['\"]",(project/"build.gradle").read_text()).group(1)
for name in [f"astral_repository-1.20.1-forge-{version}.jar",f"astral_repository-1.20.1-forge-{version}-audit-harness.jar"]:
    shutil.copy2(project/"build/libs"/name,run/"mods"/name)
patterns=[] if args.kind=="server-plain" else ["polymorph-forge-*.jar", "ironfurnaces-*.jar"]
if args.kind=="emi":patterns += ["emi-*.jar"]
elif args.kind=="jei":patterns += ["jei-1.20.1-forge-15.59.0.212.jar"]
elif args.kind=="jei-latest":patterns += ["jei-1.20.1-forge-15.74.0.230.jar","mezz_config-1.20.1-forge-0.6.9.jar"]
for pattern in patterns:
    files=list((runtime/"dependencies").glob(pattern))
    if len(files)!=1:raise SystemExit(f"Expected one dependency for {pattern}: {files}")
    shutil.copy2(files[0],run/"mods"/files[0].name)
if args.kind.startswith("server"):
    install=project/"build/packaged-server"
    launch=shlex.split((install/"libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt").read_text(),posix=True)
    launch=[value.replace("libraries/",str(install/"libraries").replace("\\","/")+"/").replace("-DlibraryDirectory=libraries","-DlibraryDirectory="+str(install/"libraries")) for value in launch]
    launch=["-Xmx3G","-Dastral_repository.recipeCompatServer=true"]+(["-Dastral_repository.recipeCompatPlain=true"] if args.kind=="server-plain" else [])+launch+["nogui"]
    accepted=(install/"eula.txt").read_text()
    assert "eula=true" in accepted
    (run/"eula.txt").write_text(accepted)
    (run/"server.properties").write_text('online-mode=false\nserver-port=0\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"height":1,"block":"minecraft:bedrock"},{"height":2,"block":"minecraft:dirt"},{"height":1,"block":"minecraft:grass_block"}],"biome":"minecraft:plains"}\nspawn-protection=0\nview-distance=3\nsimulation-distance=3\nmax-tick-time=120000\n')
else:
    install=project/"build/packaged-client"
    launch=json.loads((install/"launch-args.json").read_text())
    launch=[value for value in launch if value!="-Dastral_repository.portGameplay=true"]
    launch.insert(1,"-Dastral_repository.recipeCompatClient=true")
    launch[launch.index("--gameDir")+1]=str(run)
    (run/"natives").mkdir()
    launch=[value.replace(str(install/"natives"),str(run/"natives")) for value in launch]
    (run/"options.txt").write_text("soundCategory_master:0.0\npauseOnLostFocus:false\nfullscreen:false\nmaxFps:30\nrenderDistance:3\nsimulationDistance:5\nguiScale:2\n")
def quote(value):return '"'+value.replace("\\","\\\\").replace('"','\\"')+'"'
(run/"launch-args.txt").write_text("\n".join(quote(value) for value in launch)+"\n")
(run/"launch-args.json").write_text(json.dumps(launch,indent=2)+"\n")
assert not any("build/classes" in value.replace("\\","/") or "remapRefMap" in value for value in launch)
print(run)
