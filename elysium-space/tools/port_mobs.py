#!/usr/bin/env python3
"""Port Stellaris's mobs' art: geometry, keyframe animations and textures.

Geometry and animations are read by executing each model's createBodyLayer()
and each *Anim class's static initialiser symbolically (from
`javap -c -constants`); no decompiler is involved.

Only the builder calls models use are understood: MeshDefinition, getRoot,
addOrReplaceChild, CubeListBuilder.create/texOffs/addBox/mirror,
CubeDeformation, PartPose.offset/offsetAndRotation/rotation/ZERO and
LayerDefinition.create. Anything else stops the run with the instruction, so a
model is never half-read. Output: assets/elysiumspace/geometry/<entity>.json,
read by the client's GeometryLayers. Parts are keyed by path ("arm1/limb/hand");
a part's own name is the last segment and "" is the mesh root, so vanilla's
per-parent child names and addOrReplaceChild replacement both hold.

Credit: Stellaris, (c) Exodus Studio, CC BY-NC-SA 4.0. See CREDITS.md.
"""
import json
import os
import re
import subprocess
import sys

from mobnames import NAMES

STELLARIS = os.environ.get("STELLARIS", "/tmp/ref/stellaris")
HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(HERE, "src/main/resources/assets/elysiumspace/geometry")
MODELS = {  # our entity name: Stellaris model class
    "moonfolk": "alien/AlienModel", "hollow_moonfolk": "alienzombie/AlienZombieModel",
    "void_crawler": "starcrawler/StarCrawlerModel", "frost_raptor": "martianraptor/MartianRaptorModel",
    "cinder_boar": "mogler/MoglerModel", "cinderling": "pygro/PygroModel",
}
BASE = "com/st0x0ef/stellaris/client/renderers/entities/"


class Obj:
    def __init__(self, kind, **kw):
        self.kind = kind
        self.__dict__.update(kw)


def bytecode(cls, method="createBodyLayer"):
    path = os.path.join(STELLARIS, BASE + cls + ".class")
    out = subprocess.run(["javap", "-c", "-p", "-constants", path], capture_output=True, text=True).stdout
    head = re.escape(method) + (r";" if method == "static {}" else r"\([^)]*\);")
    m = re.search(head + r"\n\s*Code:\n(.*?)\n\n", out + "\n\n", re.S)
    if not m:
        sys.exit(f"{cls}: no {method}")
    return [l.strip() for l in m.group(1).splitlines() if re.match(r"\s*\d+:", l)]


def player_mesh(parts, inflate):
    """Vanilla PlayerModel.createMesh(deformation, slim=false), part for part."""
    def add(name, uv, origin, size, pose, grow=0.0, mirror=False, tex_scale=None):
        cube = {"uv": uv, "origin": origin, "size": size, "inflate": inflate + grow, "mirror": mirror}
        if tex_scale:
            cube["texScale"] = tex_scale
        parts[name] = {"parent": "", "pose": pose, "cubes": [cube]}
    z = [0, 0, 0, 0, 0, 0]
    add("head", [0, 0], [-4, -8, -4], [8, 8, 8], z)
    add("hat", [32, 0], [-4, -8, -4], [8, 8, 8], z, 0.5)
    add("body", [16, 16], [-4, 0, -2], [8, 12, 4], z)
    add("jacket", [16, 32], [-4, 0, -2], [8, 12, 4], z, 0.25)
    add("right_arm", [40, 16], [-3, -2, -2], [4, 12, 4], [-5, 2, 0, 0, 0, 0])
    add("right_sleeve", [40, 32], [-3, -2, -2], [4, 12, 4], [-5, 2, 0, 0, 0, 0], 0.25)
    add("left_arm", [32, 48], [-1, -2, -2], [4, 12, 4], [5, 2, 0, 0, 0, 0])
    add("left_sleeve", [48, 48], [-1, -2, -2], [4, 12, 4], [5, 2, 0, 0, 0, 0], 0.25)
    add("right_leg", [0, 16], [-2, 0, -2], [4, 12, 4], [-1.9, 12, 0, 0, 0, 0])
    add("right_pants", [0, 32], [-2, 0, -2], [4, 12, 4], [-1.9, 12, 0, 0, 0, 0], 0.25)
    add("left_leg", [16, 48], [-2, 0, -2], [4, 12, 4], [1.9, 12, 0, 0, 0, 0])
    add("left_pants", [0, 48], [-2, 0, -2], [4, 12, 4], [1.9, 12, 0, 0, 0, 0], 0.25)
    add("ear", [24, 0], [-3, -6, -1], [6, 6, 1], z)
    add("cloak", [0, 0], [-5, 0, -1], [10, 16, 1], z, tex_scale=[1.0, 0.5])


def run(cls, method="createBodyLayer", args_in=None, parts=None):
    stack, local = [], {}
    for i, a in enumerate(args_in or []):
        local[str(i)] = a
    parts = {} if parts is None else parts
    tex = None
    result = None
    statics = {}

    def push(v):
        stack.append(v)

    for line in bytecode(cls, method):
        body = line.split(":", 1)[1].strip()
        op = body.split()[0]
        comment = body.split("//", 1)[1].strip() if "//" in body else ""
        if op in ("new",):
            push(Obj("new", cls=comment.split()[-1]))
        elif op == "dup":
            push(stack[-1])
        elif op.startswith("ldc"):
            if comment.startswith("float") or comment.startswith("double"):
                push(float(comment.split()[1].rstrip("fdFD")))
            elif comment.startswith("int") or comment.startswith("long"):
                push(int(comment.split()[1].rstrip("lL")))
            elif comment.startswith("String"):
                push(comment.split(None, 1)[1])
            else:
                sys.exit(f"{cls}: ldc {comment}")
        elif op.startswith("fconst_"):
            push(float(op[-1]))
        elif op.startswith("iconst_"):
            push(-1 if op.endswith("m1") else int(op[-1]))
        elif op in ("bipush", "sipush"):
            push(int(body.split()[1]))
        elif op == "fneg":
            push(-stack.pop())
        elif op.startswith("astore"):
            slot = op.split("_")[1] if "_" in op else body.split()[1]
            local[slot] = stack.pop()
        elif op.startswith("aload"):
            slot = op.split("_")[1] if "_" in op else body.split()[1]
            push(local[slot])
        elif op == "anewarray":
            push([None] * stack.pop())
        elif op == "aastore":
            v, i, arr = stack.pop(), stack.pop(), stack.pop()
            arr[i] = v
        elif op == "putstatic":
            statics[comment.split()[1].split(":")[0].split(".")[-1]] = stack.pop()
        elif op == "return":
            break
        elif op == "pop":
            stack.pop()
        elif op == "areturn":
            result = stack.pop() if stack else None
            break
        elif op == "getstatic":
            if "PartPose.ZERO" in comment:
                push(Obj("pose", v=[0, 0, 0, 0, 0, 0]))
            elif "AnimationChannel$Targets." in comment:
                push(comment.split("Targets.")[1].split(":")[0].lower())
            elif "AnimationChannel$Interpolations." in comment:
                push(comment.split("Interpolations.")[1].split(":")[0].lower())
            elif "CubeDeformation.NONE" in comment:
                push(Obj("deform", v=0.0))
            else:
                sys.exit(f"{cls}: getstatic {comment}")
        elif op in ("invokespecial", "invokestatic", "invokevirtual"):
            sig = comment.split("Method ", 1)[1]
            name = sig.split(":")[0]
            argdesc = re.search(r"\((.*?)\)", sig).group(1)
            nargs = len(re.findall(r"L[^;]+;|[ZBCSIJFD]", argdesc))
            args = [stack.pop() for _ in range(nargs)][::-1]
            target = stack.pop() if op != "invokestatic" else None
            if "/" not in name.split(".")[0] and op == "invokestatic":     # a static method of this class
                _, sub = run(cls, name.split(".")[-1], args, parts)
                push(sub)
                continue
            if name.endswith("PlayerModel.createMesh") or name.endswith("HumanoidModel.createMesh"):
                player_mesh(parts, args[0].v if isinstance(args[0], Obj) else 0.0)
                push(Obj("mesh", cls="mesh"))
                continue
            if name.endswith("MeshDefinition.\"<init>\"") or name.endswith('MeshDefinition."<init>"'):
                target.kind, target.cls = "mesh", "mesh"
            elif name.endswith('CubeDeformation."<init>"'):
                target.kind, target.v = "deform", args[0]
            elif name.endswith("MeshDefinition.getRoot"):
                push(Obj("part", name=""))
            elif name.endswith("CubeListBuilder.create"):
                push(Obj("cubes", uv=[0, 0], mirror=False, list=[]))
            elif name.endswith("CubeListBuilder.texOffs"):
                target.uv = [args[0], args[1]]
                push(target)
            elif name.endswith("CubeListBuilder.mirror"):
                target.mirror = bool(args[0]) if args else True
                push(target)
            elif name.endswith("CubeListBuilder.addBox"):
                floats = [a for a in args if isinstance(a, (int, float)) and not isinstance(a, bool)]
                deform = next((a.v for a in args if isinstance(a, Obj) and a.kind == "deform"), 0.0)
                mirror = target.mirror
                if len(args) >= 7 and argdesc.endswith("Z"):
                    mirror = bool(args[-1])
                target.list.append({"uv": list(target.uv), "origin": floats[0:3], "size": floats[3:6],
                                    "inflate": deform, "mirror": mirror})
                push(target)
            elif name.endswith("PartPose.offset"):
                push(Obj("pose", v=list(args) + [0, 0, 0]))
            elif name.endswith("PartPose.offsetAndRotation"):
                push(Obj("pose", v=list(args)))
            elif name.endswith("PartPose.rotation"):
                push(Obj("pose", v=[0, 0, 0] + list(args)))
            elif name.endswith("PartDefinition.addOrReplaceChild"):
                child, cubes, pose = args
                path = f"{target.name}/{child}" if target.name else child   # children are per parent
                parts[path] = {"parent": target.name, "pose": pose.v, "cubes": cubes.list}
                push(Obj("part", name=path))
            elif name.endswith("AnimationDefinition$Builder.withLength"):
                push(Obj("anim", length=args[0], looping=False, channels=[]))
            elif name.endswith("AnimationDefinition$Builder.looping"):
                target.looping = True
                push(target)
            elif name.endswith("AnimationDefinition$Builder.addAnimation"):
                bone, channel = args
                target.channels.append({"bone": bone, "target": channel.target, "keyframes": channel.frames})
                push(target)
            elif name.endswith("AnimationDefinition$Builder.build"):
                push({"length": target.length, "looping": target.looping, "channels": target.channels})
            elif name.endswith('AnimationChannel."<init>"'):
                target.kind, target.target, target.frames = "channel", args[0], args[1]
            elif name.endswith('Keyframe."<init>"'):
                t, vec, interp = args
                target.kind, target.frame = "frame", [t] + vec + [interp]
            elif name.endswith("KeyframeAnimations.degreeVec") or name.endswith("KeyframeAnimations.posVec") \
                    or name.endswith("KeyframeAnimations.scaleVec"):
                push(list(args))                      # the loader re-applies degreeVec/posVec by target
            elif name.endswith("LayerDefinition.create"):
                tex = [args[1], args[2]]
                push(Obj("layer"))
            else:
                sys.exit(f"{cls}: unknown call {name}")
        else:
            sys.exit(f"{cls}: unknown instruction {body}")
    if method == "static {}":
        return {k: frames_out(v) for k, v in statics.items() if isinstance(v, dict)}
    if method != "createBodyLayer":
        return parts, result
    return {"texture": tex, "parts": parts}


def frames_out(anim):
    for ch in anim["channels"]:
        ch["keyframes"] = [f.frame for f in ch["keyframes"]]
    return anim


ANIMS = {  # our entity name: Stellaris AnimationDefinition holder
    "moonfolk": "alien/AlienAnim", "void_crawler": "starcrawler/StarCrawlerAnim",
    "frost_raptor": "martianraptor/MartianRaptorAnim", "cinder_boar": "mogler/MoglerAnim",
}
PI = 3.1415927
WALK = 0.6662          # vanilla's limb-swing rate, which every Stellaris model uses


def legs(*pairs, amp=1.4):
    return [{"bone": b, "axis": "x", "by": "walk", "rate": WALK, "amp": amp, "phase": ph} for b, ph in pairs]


# How each model moves: the rig our RiggedModel runs. Taken from each Stellaris
# model's setupAnim(): which bone looks, which limbs swing with walking (and at
# what phase), which keyframe animation walks, and which one-shot animations the
# entity starts (Animated.animation(state)). Bones are part paths.
RIGS = {
    "moonfolk": {"head": "head", "pitch": True, "swing": legs(("leg0", 0), ("leg1", PI)),
                 "states": {"shake": "elysiumspace:moonfolk/shake_head"}},
    "hollow_moonfolk": {"head": "head", "pitch": True,
                        "swing": legs(("leg0", 0), ("leg1", PI)) + legs(("arm1", PI), ("arm2", 0), amp=1.0)
                        + [{"bone": f"monsterarm{i + 1}", "axis": "y", "by": "age", "rate": 0.1, "amp": 0.2,
                            "phase": i * PI / 2} for i in range(4)]},
    "void_crawler": {"walk": {"animation": "elysiumspace:void_crawler/walk", "speed": 2.0, "scale": 2.5},
                     "states": {"attack": "elysiumspace:void_crawler/attack"}},
    "frost_raptor": {"head": "body/head", "pitch": True, "swing": legs(("leg1", 0), ("leg2", PI)),
                     "states": {"attack": "elysiumspace:frost_raptor/attack"}},
    "cinder_boar": {"head": "head", "pitch": False,
                    "swing": legs(("leg1", 0), ("leg2", PI), ("leg3", PI), ("leg4", 0), amp=1.2),
                    "states": {"attack": "elysiumspace:cinder_boar/attack"}},
    # cinderling: vanilla's PiglinModel animates it; its geometry is a piglin's.
}
TEXTURES = os.path.join(STELLARIS, "assets/stellaris/textures/entity")
TEX_OUT = os.path.join(HERE, "src/main/resources/assets/elysiumspace/textures/entity")
# Stellaris's alien job textures -> vanilla profession ids, which Moonfolk looks up by.
PROFESSIONS = {"tool_smith": "toolsmith", "leather_worker": "leatherworker", "weapon_smith": "weaponsmith"}


HOMES = {"moonfolk": "pale_moon", "hollow_moonfolk": "pale_moon", "void_crawler": "pale_moon",
         "frost_raptor": "frostfall", "cinder_boar": "cinderveil", "cinderling": "cinderveil",
         "cinderling_brute": "cinderveil"}          # kept in step with MobRules (the validator compares)
MOB_STRENGTH = 0.65     # mobs keep a third of their own colour, so a moonfolk's profession still reads


def copy_textures():
    """Stellaris's entity textures under our names, reskinned into each mob's home world (S8)."""
    from reskin import reskin_file
    for theirs, ours in NAMES.items():
        if theirs == "ice_spit":
            continue
        reskin_file(os.path.join(TEXTURES, theirs + ".png") if theirs != "alien"
                    else os.path.join(TEXTURES, "alien/alien.png"), os.path.join(TEX_OUT, ours + ".png"),
                    HOMES[ours], MOB_STRENGTH)
    os.makedirs(os.path.join(TEX_OUT, "moonfolk"), exist_ok=True)
    for f in sorted(os.listdir(os.path.join(TEXTURES, "alien"))):
        job = f[:-4]
        if job != "alien":
            reskin_file(os.path.join(TEXTURES, "alien", f),
                        os.path.join(TEX_OUT, "moonfolk", PROFESSIONS.get(job, job) + ".png"), "pale_moon", MOB_STRENGTH)
    reskin_file(os.path.join(TEXTURES, "ice_shard_arrow.png"), os.path.join(TEX_OUT, "rime_spit.png"), "pale_moon")
# NeoForge's own JSON animation format: one file per animation, loaded as
# elysiumspace:<entity>/<animation> by its AnimationLoader. Keyframe values are the
# arguments Stellaris gave degreeVec/posVec/scaleVec; the loader applies the same
# conversion by target, so they are written unchanged.
ANIM_OUT = os.path.join(HERE, "src/main/resources/assets/elysiumspace/neoforge/animations/entity")


def neoforge_anim(anim):
    return {"length": anim["length"], "loop": anim["looping"], "animations": [
        {"bone": ch["bone"], "target": "minecraft:" + ch["target"], "keyframes": [
            {"timestamp": f[0], "target": f[1:4], "interpolation": "minecraft:" + f[4]}
            for f in ch["keyframes"]]} for ch in anim["channels"]]}


def main():
    os.makedirs(OUT, exist_ok=True)
    for stale in os.listdir(OUT):
        os.remove(os.path.join(OUT, stale))
    copy_textures()
    for ours, cls in MODELS.items():
        geo = run(cls)
        if ours in RIGS:
            geo["rig"] = RIGS[ours]
        json.dump(geo, open(os.path.join(OUT, ours + ".json"), "w", newline="\n"), indent=1)
        print(f"{ours:15s} {len(geo['parts']):3d} parts, "
              f"{sum(len(p['cubes']) for p in geo['parts'].values()):3d} boxes, texture {geo['texture']}")
    import shutil
    shutil.rmtree(ANIM_OUT, ignore_errors=True)
    for ours, cls in ANIMS.items():
        anims = run(cls, "static {}")
        bones = {p.split("/")[-1] for p in run(MODELS[ours])["parts"]}
        for name, anim in anims.items():     # Stellaris's raptor attack names bones its model lacks;
            dropped = [c["bone"] for c in anim["channels"] if c["bone"] not in bones]    # the game skips them
            anim["channels"] = [c for c in anim["channels"] if c["bone"] in bones]
            if dropped:
                print(f"{ours:15s} {name}: dropped channels for missing bones {', '.join(sorted(set(dropped)))}")
        os.makedirs(os.path.join(ANIM_OUT, ours), exist_ok=True)
        for name, anim in anims.items():
            snake = re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()     # shakeHead -> shake_head
            json.dump(neoforge_anim(anim), open(os.path.join(ANIM_OUT, ours, snake + ".json"), "w", newline="\n"), indent=1)
        print(f"{ours:15s} animations " + ", ".join(f"{k} ({len(v['channels'])} channels, {v['length']}s)"
                                                   for k, v in anims.items()))


if __name__ == "__main__":
    main()
