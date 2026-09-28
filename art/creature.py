"""
From a body plan to a model class, a measured size and a skin.

art/bodyplans.py says what a creature is made of; this turns that into the
three things a module ships for it: the Java model (a real ModelPart tree,
generated the way elysium-mobs generates the eight), the bounds the renderer
scales onto the hitbox, and a Skin for the sheet writer. Shared across the
modules that draw creatures — bestiary, space, comforts, agronomy — so there is
one emitter, one measuring rule and one idea of what a plate is.
"""
import math

import numpy as np

from boxmodel import Skin, hex_to_rgb, part_lines, rotation_of, parent_of, smallest_sheet

# ---------------------------------------------------------------------------
# Measuring
# ---------------------------------------------------------------------------


def _rotation(rot):
    """Minecraft applies a part's rotation as Z, then Y, then X."""
    if rot is None:
        return np.identity(3)
    x, y, z = rot
    cx, sx = math.cos(x), math.sin(x)
    cy, sy = math.cos(y), math.sin(y)
    cz, sz = math.cos(z), math.sin(z)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def bounds(rows):
    """
    The axis-aligned box every corner of every part lands in, at bind pose,
    as (wide, tall, deep, low_y, high_y) in model pixels.

    Measured through the hierarchy — each part's pivot and rotation composed
    onto its parent's — because a limb bent forty degrees is not where its
    table row says. The renderer divides the hitbox by this, so a plan drawn
    at any size still stands exactly as tall as the thing a player swings at.
    """
    by_name = {row[0]: row for row in rows}
    frames = {}

    def frame(name):
        if name is None:
            return np.identity(3), np.zeros(3)
        if name in frames:
            return frames[name]
        row = by_name[name]
        parent_r, parent_t = frame(parent_of(row))
        pivot = np.array(row[7:10], dtype=float)
        r = parent_r @ _rotation(rotation_of(row))
        t = parent_t + parent_r @ pivot
        frames[name] = (r, t)
        return frames[name]

    low = np.array([np.inf] * 3)
    high = np.array([-np.inf] * 3)
    for row in rows:
        r, t = frame(row[0])
        ox, oy, oz, sx, sy, sz = row[1:7]
        for dx in (0, sx):
            for dy in (0, sy):
                for dz in (0, sz):
                    corner = t + r @ np.array([ox + dx, oy + dy, oz + dz], dtype=float)
                    low = np.minimum(low, corner)
                    high = np.maximum(high, corner)
    size = high - low
    return float(size[0]), float(size[1]), float(size[2]), float(low[1]), float(high[1])


# ---------------------------------------------------------------------------
# What a part is made of
# ---------------------------------------------------------------------------

MATERIALS = {
    "deep":  (0.62, 0.85),
    "cloth": (0.82, 0.95),
    "base":  (1.00, 1.00),
    "plate": (1.52, 0.55),
    "bone":  (1.86, 0.28),
}
MATERIAL_GRAIN = {"deep": "stone", "base": "stone", "cloth": "cloth", "plate": "metal", "bone": "smooth"}

PLATE_PREFIXES = ("visor", "crest", "spine", "carapace", "gorget", "scute", "pauldron",
                  "shell", "shield", "brow", "hood", "fin", "vane")
BONE_PREFIXES = ("claw", "toe", "tusk", "horn", "fang", "mandible", "talon", "spur",
                 "beak", "antler", "knuckle")
DEEP_PREFIXES = ("thorax", "belly", "waist", "hips", "haunch", "neck", "abdomen", "girdle",
                 "barrel", "rump", "chest", "jaw", "muzzle", "antenna", "tail", "upper", "fore")


def material_palette(base):
    """One base colour as the five materials a creature is built from."""
    red, green, blue = hex_to_rgb(base)
    lum = 0.2126 * red + 0.7152 * green + 0.0722 * blue
    out = {}
    for name, (scale, keep) in MATERIALS.items():
        target = lum * scale
        out[name] = "#%02x%02x%02x" % tuple(
            max(0, min(255, int(channel * scale * keep + target * (1 - keep))))
            for channel in (red, green, blue))
    return out


def materials_for(rows):
    """part name -> material, by the role its name carries."""
    out = {}
    for row in rows:
        name = row[0]
        stem = name.split("_")[0]
        if stem in BONE_PREFIXES and name.endswith(("_2", "_3")):
            out[name] = "bone"
        elif stem in BONE_PREFIXES:
            out[name] = "base"
        elif stem in PLATE_PREFIXES or any(part in PLATE_PREFIXES for part in name.split("_")):
            out[name] = "plate"
        elif stem in DEEP_PREFIXES or any(part in ("thigh", "shin", "meta") for part in name.split("_")):
            out[name] = "deep"
    return out


def accents_for(rows):
    """Which parts carry the lit band: the visor, the leading shells, the tips."""
    marks = set()
    for row in rows:
        name = row[0]
        if (name.startswith("visor_") or name.startswith("crest_")
                or name in ("spine_0", "spine_1", "carapace_0", "carapace_1", "scute_0", "scute_1")
                or (name.startswith("cranium_") and name.endswith(("_0", "_1")))
                or (name.startswith(("claw_", "knuckle_", "mandible_")) and name.endswith("_3"))):
            marks.add(name)
    return marks


def skin_for(rows, base):
    """A Skin for these rows in this base colour."""
    return Skin(material_palette(base), materials_for(rows), accents_for(rows), MATERIAL_GRAIN)


# ---------------------------------------------------------------------------
# The model class
# ---------------------------------------------------------------------------

MODEL_TEMPLATE = '''package {package};

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.Mob;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * GENERATED by {generator}. Do not edit.
 *
 * A {plan} of {count} boxes from art/bodyplans.py, emitted with its texture
 * sheet from the same rows so the UVs here and the pixels there cannot
 * disagree. {doc}
 */
public class {Cls}<T extends Mob> extends EntityModel<T> {{

    /** The bind pose measured through the hierarchy, in model pixels: what the renderer scales. */
    public static final float WIDE = {wide:.2f}F;
    public static final float TALL = {tall:.2f}F;
    public static final float DEEP = {deep:.2f}F;
    public static final int SHEET = {sheet};

    private final ModelPart root;
{fields}

    public {Cls}(ModelPart root) {{
        this.root = root;
{assigns}
    }}

    public static LayerDefinition createBodyLayer() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{parts}
        return LayerDefinition.create(mesh, {sheet}, {sheet});
    }}

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {{
{anim}
    }}

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light,
                               int overlay, int colour) {{
        this.root.render(pose, buffer, light, overlay, colour);
    }}
}}
'''


def java_field(name):
    head, *rest = name.split("_")
    return head + "".join(word.capitalize() for word in rest)


def anim_java(gait):
    """setupAnim's body for one gait."""
    lines = []
    lines.append('        this.head.yRot = netHeadYaw * ((float) Math.PI / 180F);')
    lines.append('        this.head.xRot = headPitch * ((float) Math.PI / 180F) + %.2fF;' % gait.get("head_pitch", 0.0))
    lines.append('        float amount = Math.min(limbSwingAmount, 1.0F);')
    lines.append('        float cycle = limbSwing * 0.6662F * %.2fF;' % gait["rate"])
    lines.append('        float stride = (float) Math.cos(cycle) * %.2fF * amount;' % gait["stride"])
    for part, phase in gait.get("legs", ()):
        if phase == 0.0:
            lines.append('        this.%s.xRot = stride;' % java_field(part))
        else:
            lines.append('        this.%s.xRot = -stride;' % java_field(part))
    if gait.get("hunt"):
        # It reaches when it has something to reach for and hangs when it does
        # not: the arms are the only tell that it is hunting.
        lines.append('        boolean hunting = entity != null && entity.getTarget() != null;')
    for part, sign in gait.get("arms", ()):
        if gait.get("hunt"):
            lines.append('        this.%s.xRot = (hunting ? -1.4F : 0.0F) + %s(float) Math.sin(ageInTicks * 0.08F) * 0.08F;'
                         % (java_field(part), "" if sign > 0 else "-"))
            continue
        # Folded arms twitch with the stride and drift when standing.
        lines.append('        this.%s.xRot = %s(float) Math.cos(cycle) * %.2fF * amount'
                     ' + (float) Math.sin(ageInTicks * 0.06F) * 0.08F;'
                     % (java_field(part), "" if sign > 0 else "-", gait["stride"] * 0.5))
    if gait.get("tail", 0.0):
        lines.append('        this.tail0.yRot = (float) Math.sin(cycle) * %.2fF * amount'
                     ' + (float) Math.sin(ageInTicks * 0.09F) * 0.10F;' % gait["tail"])
    if gait.get("flight"):
        # Off the ground the wings beat off the age and the legs tuck; on it, the
        # wings fold and the legs stride. The entity is null only in the previewer.
        lines.append('        boolean flying = entity != null && !entity.onGround();')
        lines.append('        float beat = (float) Math.cos(ageInTicks * 0.9F) * 0.9F + 0.4F;')
        for part, sign in gait.get("wings", ()):
            lines.append('        this.%s.zRot = flying ? %s(beat) : %s(0.25F + (float) Math.abs(Math.sin(cycle * 2.0F)) * 0.9F * amount);'
                         % (java_field(part), "" if sign > 0 else "-", "" if sign > 0 else "-"))
        for part, _phase in gait.get("legs", ()):
            lines.append('        if (flying) { this.%s.xRot = 0.8F; }' % java_field(part))
    else:
        for part, sign in gait.get("wings", ()):
            # Wings flap with the stride and settle folded when standing.
            lines.append('        this.%s.zRot = %s(0.25F + (float) Math.abs(Math.sin(cycle * 2.0F)) * 0.9F * amount'
                         ' + (float) Math.sin(ageInTicks * 0.05F) * 0.04F);'
                         % (java_field(part), "" if sign > 0 else "-"))
    for part, sign in gait.get("beat", ()):
        # An insect's wings beat off its own age whether or not it moves; the
        # entity id keeps two side by side out of lockstep.
        lines.append('        this.%s.zRot = %s((float) Math.sin(ageInTicks * 2.1F + (entity == null ? 0 : entity.getId() %% 7)) * 0.6F);'
                     % (java_field(part), "" if sign > 0 else "-"))
    if gait.get("hover"):
        lines.append('        this.root.y = (float) Math.sin(ageInTicks * 0.12F) * %.2fF;' % gait["hover"])
    for index, part in enumerate(gait.get("drift", ())):
        # Tendrils drift out of step with one another off the age.
        lines.append('        this.%s.xRot = (float) Math.sin(ageInTicks * 0.07F + %.2fF) * 0.18F;'
                     % (java_field(part), index * 1.3))
    if gait.get("wag"):
        part, amp = gait["wag"]
        # A swimmer's tail never stops; the body follows a little behind it.
        lines.append('        this.%s.yRot = (float) Math.sin(ageInTicks * 0.25F + cycle) * %.2fF;'
                     % (java_field(part), amp))
        lines.append('        this.body.yRot = (float) Math.sin(ageInTicks * 0.25F + cycle - 0.6F) * %.2fF;'
                     % (amp * 0.25))
    if gait.get("abdomen"):
        lines.append('        this.%s.xRot = -0.90F + (float) Math.sin(ageInTicks * 0.07F) * 0.06F;'
                     % java_field(gait["abdomen"]))
    if not gait.get("hover"):
        lines.append('        this.root.y = (float) Math.abs(Math.sin(cycle)) * %.2fF * amount;' % gait["bob"])
    lines.append('        this.root.zRot = (float) Math.sin(cycle) * %.2fF * amount;' % gait["roll"])
    return "\n".join(lines)


def animated_parts(gait):
    names = ["head"]
    names += [part for part, _ in gait.get("legs", ())]
    names += [part for part, _ in gait.get("arms", ())]
    if gait.get("tail", 0.0):
        names.append("tail_0")
    if gait.get("abdomen"):
        names.append(gait["abdomen"])
    names += [part for part, _ in gait.get("wings", ())]
    names += [part for part, _ in gait.get("beat", ())]
    names += list(gait.get("drift", ()))
    if gait.get("wag"):
        names.append(gait["wag"][0])
        names.append("body")
    return list(dict.fromkeys(names))


def model_java(package, cls, plan, skin, generator, doc=""):
    """
    The Java for one model class, and the sheet size it was packed to.

    Animated parts are looked up by their path through the tree, because a
    tail hangs off the body and a claw off a forearm: getChild alone finds only
    roots.
    """
    rows = plan.rows
    sheet = smallest_sheet(rows, skin=skin)
    parts = part_lines(rows, sheet, skin)
    wide, tall, deep, _low, _high = bounds(rows)
    by_name = {row[0]: row for row in rows}

    def path(name):
        chain = []
        while name is not None:
            chain.append(name)
            name = parent_of(by_name[name])
        return list(reversed(chain))

    names = animated_parts(plan.gait)
    fields = "\n".join("    private final ModelPart %s;" % java_field(n) for n in names)
    assigns = "\n".join(
        "        this.%s = root%s;" % (java_field(n), "".join('.getChild("%s")' % step for step in path(n)))
        for n in names)
    return MODEL_TEMPLATE.format(
        package=package, Cls=cls, plan=plan.name, count=len(rows), doc=doc,
        generator=generator, wide=wide, tall=tall, deep=deep, sheet=sheet,
        fields=fields, assigns=assigns, parts=parts, anim=anim_java(plan.gait)), sheet
