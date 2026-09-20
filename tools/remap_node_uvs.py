"""Remap node base/trim/channel faces at one texel per unit; preserve crystal artwork.

Geometry, winding, normals and materials are preserved. --check audits without writing.
"""
import argparse
import itertools
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODELS = ROOT / "art/blockbench"
RUNTIME = ROOT / "src/main/resources/assets/astral_repository/models/block/node_meshes"


def sub(a, b): return tuple(x-y for x, y in zip(a,b))
def dot(a, b): return sum(x*y for x,y in zip(a,b))
def scale(a, s): return tuple(x*s for x in a)
def unit(a): return scale(a, 1/math.sqrt(dot(a,a)))


def unwrap(points, old_uv):
    # Recover the artwork orientation, then use orthonormal physical axes. Unlike
    # dominant-axis projection this also preserves square pixels on sloped tips.
    a, b, c = points[:3]
    ab, ac = sub(b,a), sub(c,a)
    ub, uc = sub(old_uv[1],old_uv[0]), sub(old_uv[2],old_uv[0])
    det = ub[0]*uc[1]-ub[1]*uc[0]
    assert abs(det)>1e-10
    tangent = unit(scale(sub(scale(ab,uc[1]),scale(ac,ub[1])),1/det))
    bitangent = scale(sub(scale(ac,ub[0]),scale(ab,uc[0])),1/det)
    bitangent = unit(sub(bitangent,scale(tangent,dot(tangent,bitangent))))
    uv = [(dot(sub(p,a),tangent), dot(sub(p,a),bitangent)) for p in points]
    low = [min(p[i] for p in uv) for i in range(2)]
    high = [max(p[i] for p in uv) for i in range(2)]
    assert all(high[i]-low[i] <= 16.00001 for i in range(2)), "Face exceeds the atlas tile"
    return [[round(p[i]-(low[i]+high[i])/2+8,8) for i in range(2)] for p in uv]


def audit(points, uv, density=True):
    assert all(-1e-6<=c<=16.000001 for p in uv for c in p), "UV outside material tile"
    if not density: return
    for i,j in itertools.combinations(range(len(points)),2):
        assert abs(math.dist(points[i],points[j])-math.dist(uv[i],uv[j])) < .0001, "Stretched texture"


def remap(path, check):
    model=json.loads(path.read_text(encoding="utf-8"))
    elements=model["elements"]
    crystal_textures={i for i,t in enumerate(model["textures"]) if t["name"]=="node_crystal.png"}
    for e in elements:
        assert not any(e.get("rotation", [0,0,0])), "Rotated authoring element requires an export transform"
        if e["type"]=="cube":
            x,y,z=sub(e["to"],e["from"])
            for side,face in e["faces"].items():
                if face["texture"] in crystal_textures:
                    assert all(-1e-6<=v<=16.000001 for v in face["uv"])
                    continue
                w,h=(x,z) if side in ("up","down") else (z,y) if side in ("east","west") else (x,y)
                expected=[8-w/2,8-h/2,8+w/2,8+h/2]
                if check: assert max(abs(a-b) for a,b in zip(face["uv"],expected))<1e-6
                else: face["uv"]=expected
        else:
            for face in e["faces"].values():
                keys=face["vertices"]; points=[e["vertices"][k] for k in keys]
                uv=[face["uv"][k] for k in keys]
                crystal=face["texture"] in crystal_textures
                if not check and not crystal: face["uv"]=dict(zip(keys,unwrap(points,uv)))
                audit(points,[face["uv"][k] for k in keys],not crystal)
    obj=RUNTIME/(path.stem+".obj")
    lines=obj.read_text().splitlines(); vertices=[];uvs=[];uv_lines=[];changes={};count=0
    element_iter=iter(elements);material=None
    for line_index,line in enumerate(lines):
        parts=line.split()
        if not parts: continue
        if parts[0]=="o":
            e=next(element_iter);face_index=0
            assert e["name"]==line[2:]
        elif parts[0]=="usemtl": material=parts[1]
        elif parts[0]=="v": vertices.append(tuple(float(v)*16 for v in parts[1:4]))
        elif parts[0]=="vt": uvs.append(tuple(map(float,parts[1:3])));uv_lines.append(line_index)
        elif parts[0]=="f":
            indices=[tuple(map(int,p.split('/'))) for p in parts[1:]]
            points=[vertices[i[0]-1] for i in indices]
            if e["type"]=="cube":
                face=e["faces"][["north","east","south","west","up","down"][face_index]]
                old=[uvs[i[1]-1] for i in indices]
                lo=[min(p[i] for p in old) for i in range(2)];hi=[max(p[i] for p in old) for i in range(2)]
                u0,v0,u1,v1=face["uv"]
                mapped=[(u0+(p[0]-lo[0])/(hi[0]-lo[0])*(u1-u0),v1-(p[1]-lo[1])/(hi[1]-lo[1])*(v1-v0)) for p in old]
            else:
                matches=[f for f in e["faces"].values() if len(f["vertices"])==len(points) and all(any(math.dist(p,e["vertices"][k])<.0001 for k in f["vertices"]) for p in points)]
                assert len(matches)==1,(path,e["name"],points)
                face=matches[0]
                mapped=[face["uv"][min(face["vertices"],key=lambda k:math.dist(p,e["vertices"][k]))] for p in points]
            assert (face["texture"] in crystal_textures)==(material=="node_crystal"), "Material mismatch"
            audit(points,mapped,material!="node_crystal")
            for index,uv in zip(indices,mapped):
                target=(uv[0]/16,1-uv[1]/16);slot=index[1]-1
                if check: assert math.dist(uvs[slot],target)<1e-7,(path,slot,"Authoring/runtime mismatch")
                else:
                    assert slot not in changes or math.dist(changes[slot],target)<1e-7
                    changes[slot]=target
            face_index+=1;count+=1
    if not check:
        for slot,uv in changes.items(): lines[uv_lines[slot]]=f"vt {uv[0]:.10f} {uv[1]:.10f}"
        obj.write_text("\n".join(lines)+"\n",encoding="utf-8")
        path.write_text(json.dumps(model,ensure_ascii=False,separators=(',',':'))+"\n",encoding="utf-8")
    return count


if __name__=="__main__":
    args=argparse.ArgumentParser();args.add_argument("--check",action="store_true");options=args.parse_args()
    models=sorted(RUNTIME.glob("*.obj"))
    total=sum(remap(MODELS/(p.stem+".bbmodel"),options.check) for p in models)
    print(f"{'Verified' if options.check else 'Remapped'} {total} faces across {len(models)} models: authored crystal UVs, uniform base/trim/channel texel scale, bounded UVs, matching authoring/runtime maps.")
