#!/usr/bin/env python3
"""
Generate the envoy model, renderer, skins and lang from one table.

Run from the repo root:   python3 tools/gen_npcs.py

Same argument as elysium-mobs' generator, and the same shared machinery in
art/boxmodel.py: a model says where a box's faces are on the sheet and the sheet
has to have them there, and hand-written those two drift the first time a box
moves. The box table below is the single source and both come out of it.

<h2>One model, five people</h2>

Every member of the court is a humanoid in player proportions. What differs is
the skin and the regalia - a crown, a cape, pauldrons, a collar, a visor, a
halo. So the model carries every accessory and hides the ones a kind does not
wear, which is one model to keep in step instead of five.

<h2>Why the palette is per-box and not per-sheet</h2>

The mobs' generator paints a whole creature from one base and one accent, which
is right for something armoured head to foot. These are people: a face is not
the colour of a cloak, and hair is not the colour of either. Each box picks its
colours by name from the kind's palette, so Lillith's hair is red on a black
coat without either of them being a special case in the drawing code.
"""
import json
import os
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
def _art_path():
    """
    The shared art package, wherever this script sits in the tree.

    Walks up looking for it rather than assuming a depth, because the same file
    ships at the repo root in one layout and two directories down in the other.
    """
    here = pathlib.Path(__file__).resolve()
    for base in [here.parent] + list(here.parents):
        if (base / "art" / "style.py").is_file():
            return str(base / "art")
    raise SystemExit("cannot find the art package from " + str(here.parent))


sys.path.insert(0, _art_path())

from PIL import Image, ImageDraw

from boxmodel import draw_box, pack, part_lines

JAVA = ROOT / "src/main/java/com/elysium/npcs"
RES = ROOT / "src/main/resources/assets/elysiumnpcs"
# 96, and measured rather than picked.
#
# Thirteen boxes with seven distinct accessory footprints do not fit in 64x64 -
# the packer says so and raises rather than overlapping, which is the whole
# reason it exists. 96 fits with room to spare and 128 would be a quarter more
# texture memory for empty sheet, so 96 it is. If a fourteenth box ever pushes
# past this, the packer will say so again.
SHEET = 96

# ---------------------------------------------------------------------------
# The box table
# ---------------------------------------------------------------------------
#
# (name, originX, originY, originZ, sizeX, sizeY, sizeZ, pivotX, pivotY, pivotZ)
# and optionally an eleventh element: the part this one hangs from.
#
# Player proportions, because these are people and a player already knows what
# size a person is. The six body parts are vanilla's own numbers; the seven
# accessories are sized to sit on them.

BOXES = [
    ("head", -4, -8, -4, 8, 8, 8, 0, 0, 0),
    ("body", -4, 0, -2, 8, 12, 4, 0, 0, 0),
    ("arm_left", -1, -2, -2, 4, 12, 4, 5, 2, 0),
    ("arm_right", -3, -2, -2, 4, 12, 4, -5, 2, 0),
    ("leg_left", -2, 0, -2, 4, 12, 4, 2, 12, 0),
    ("leg_right", -2, 0, -2, 4, 12, 4, -2, 12, 0),

    # Regalia. Each hangs from the part it is worn on, so a crown turns with
    # the head and a pauldron swings with the arm.
    ("crown", -4, -11, -4, 8, 3, 8, 0, 0, 0, "head"),
    ("halo", -5, -12, -5, 10, 4, 10, 0, 0, 0, "head"),
    ("visor", -4, -6, -5, 8, 3, 1, 0, 0, 0, "head"),
    ("collar", -5, 0, -3, 10, 3, 6, 0, 0, 0, "body"),
    ("cape", -5, 0, 2, 10, 16, 1, 0, 0, 0, "body"),
    ("pauldron_left", -1, -3, -3, 6, 4, 6, 0, 0, 0, "arm_left"),
    ("pauldron_right", -5, -3, -3, 6, 4, 6, 0, 0, 0, "arm_right"),
]

REGALIA = ["crown", "halo", "visor", "collar", "cape", "pauldron_left", "pauldron_right"]

# ---------------------------------------------------------------------------
# The five, and what they are made of
# ---------------------------------------------------------------------------
#
# Black and emerald with gold, throughout - that is the Empire's livery and the
# thing all five have in common. What separates them is which of those three is
# loudest and what a face is doing on top of it.

PALETTES = {
    "emperor": {
        "skin": "#c8a486", "hair": "#8d8b86",
        "base": "#16140f", "accent": "#c9a227",
        "cape": "#123d2a", "regalia": "#d8b13a",
    },
    "architect": {
        "skin": "#4a3b38", "hair": "#141118",
        "base": "#131118", "accent": "#c9a227",
        "cape": "#12261f", "regalia": "#1f7a4d",
    },
    "sentinel": {
        # Not a face: a mask. Skin and base are the same black, and the only
        # colour on the whole sheet is the circuitry.
        "skin": "#0d0f0e", "hair": "#0d0f0e",
        "base": "#101211", "accent": "#2ee06a",
        "cape": "#0d0f0e", "regalia": "#2ee06a",
    },
    "commander": {
        "skin": "#d8b49a", "hair": "#a81a20",
        "base": "#131315", "accent": "#c9a227",
        "cape": "#12402c", "regalia": "#c9a227",
    },
    "queen": {
        "skin": "#e2c9ae", "hair": "#ffe89a",
        "base": "#0f1a16", "accent": "#e0c14a",
        "cape": "#0e2c22", "regalia": "#ffe89a",
    },
}


def colours_for(box_name, palette):
    """Which two colours a given box is painted in."""
    if box_name == "head":
        return palette["skin"], palette["hair"]
    if box_name == "cape":
        return palette["cape"], palette["accent"]
    if box_name in ("crown", "halo", "visor"):
        return palette["regalia"], palette["accent"]
    return palette["base"], palette["accent"]


def write_sheet(path, palette):
    img = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    placed = pack(BOXES, SHEET)
    for box in BOXES:
        base, accent = colours_for(box[0], palette)
        draw_box(draw, box, placed[(box[4], box[5], box[6])], base, accent, SHEET)
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


# ---------------------------------------------------------------------------
# Java
# ---------------------------------------------------------------------------

MODEL = '''package com.elysium.npcs.client.model;

import com.elysium.npcs.entity.EnvoyKind;
import com.elysium.npcs.entity.ImperialEnvoy;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * GENERATED by tools/gen_npcs.py. Do not edit.
 *
 * One humanoid carrying every piece of regalia any of the five wears. Which
 * pieces are drawn is decided per entity in setupAnim, from the envoy's own
 * kind - so a Sentinel has a visor and no crown without there being a second
 * model, and adding a sixth member of the court is a row in a table.
 */
public class EnvoyModel<T extends ImperialEnvoy> extends EntityModel<T> {{

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart armLeft;
    private final ModelPart armRight;
    private final ModelPart legLeft;
    private final ModelPart legRight;

    private final ModelPart crown;
    private final ModelPart halo;
    private final ModelPart visor;
    private final ModelPart collar;
    private final ModelPart cape;
    private final ModelPart pauldronLeft;
    private final ModelPart pauldronRight;

    public EnvoyModel(ModelPart root) {{
        this.root = root;
        this.head = root.getChild("head");
        this.armLeft = root.getChild("arm_left");
        this.armRight = root.getChild("arm_right");
        this.legLeft = root.getChild("leg_left");
        this.legRight = root.getChild("leg_right");

        this.crown = this.head.getChild("crown");
        this.halo = this.head.getChild("halo");
        this.visor = this.head.getChild("visor");
        ModelPart body = root.getChild("body");
        this.collar = body.getChild("collar");
        this.cape = body.getChild("cape");
        this.pauldronLeft = this.armLeft.getChild("pauldron_left");
        this.pauldronRight = this.armRight.getChild("pauldron_right");
    }}

    public static LayerDefinition createBodyLayer() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{parts}
        return LayerDefinition.create(mesh, {sheet}, {sheet});
    }}

    /**
     * A walk, a look, and the regalia this particular person wears.
     *
     * Visibility is set every frame rather than once at construction because
     * one model instance draws every envoy on screen: a model built for the
     * Emperor and then used to draw a Sentinel would put a crown on the
     * Sentinel. Setting it per entity is the cost of having one model, and it
     * is seven booleans.
     */
    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {{
        this.head.yRot = netHeadYaw * ((float) Math.PI / 180F);
        this.head.xRot = headPitch * ((float) Math.PI / 180F);

        float swing = (float) Math.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
        this.legLeft.xRot = swing;
        this.legRight.xRot = -swing;
        this.armLeft.xRot = -swing * 0.7F;
        this.armRight.xRot = swing * 0.7F;

        EnvoyKind kind = entity.getKind();
        this.crown.visible = kind.wears(EnvoyKind.Regalia.CROWN);
        this.halo.visible = kind.wears(EnvoyKind.Regalia.HALO);
        this.visor.visible = kind.wears(EnvoyKind.Regalia.VISOR);
        this.collar.visible = kind.wears(EnvoyKind.Regalia.COLLAR);
        this.cape.visible = kind.wears(EnvoyKind.Regalia.CAPE);
        this.pauldronLeft.visible = kind.wears(EnvoyKind.Regalia.PAULDRONS);
        this.pauldronRight.visible = kind.wears(EnvoyKind.Regalia.PAULDRONS);
    }}

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light,
                               int overlay, int colour) {{
        this.root.render(pose, buffer, light, overlay, colour);
    }}
}}
'''

RENDERER = '''package com.elysium.npcs.client;

import com.elysium.npcs.client.model.EnvoyModel;
import com.elysium.npcs.entity.ImperialEnvoy;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * GENERATED by tools/gen_npcs.py. Do not edit.
 *
 * The texture comes from the envoy's kind, which is what lets one entity type
 * be five people.
 */
public class EnvoyRenderer extends MobRenderer<ImperialEnvoy, EnvoyModel<ImperialEnvoy>> {

    public EnvoyRenderer(EntityRendererProvider.Context context) {
        super(context, new EnvoyModel<>(context.bakeLayer(
                ElysiumNpcsClient.ENVOY_LAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(ImperialEnvoy entity) {
        return entity.getKind().texture();
    }
}
'''

CLIENT = '''package com.elysium.npcs.client;

import com.elysium.npcs.ElysiumNpcs;
import com.elysium.npcs.client.model.EnvoyModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * GENERATED by tools/gen_npcs.py. Do not edit.
 *
 * Client-only, and annotated so: a renderer class touched on a dedicated server
 * is a NoClassDefFoundError at load, and the annotation is what keeps this file
 * from ever being loaded there.
 */
@EventBusSubscriber(modid = ElysiumNpcs.MODID, bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ElysiumNpcsClient {

    private ElysiumNpcsClient() {
    }

    public static final ModelLayerLocation ENVOY_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ElysiumNpcs.MODID, "envoy"), "main");

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ENVOY_LAYER, EnvoyModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ElysiumNpcs.ENVOY.get(), EnvoyRenderer::new);
    }
}
'''

# ---------------------------------------------------------------------------
# Language
# ---------------------------------------------------------------------------

NAMES = {
    "emperor": ("Elysomnion", "The Code has no filing for you yet."),
    "architect": ("Sylphara Voss", "You have not been drawn on any plan of mine."),
    "sentinel": ("Sentinel", "Observation is not yet warranted."),
    "commander": ("Lillith", "The fleet takes recommendations. You have none."),
    "queen": ("Aurelia", "I have not yet had cause to look at you."),
}

TITLES = {
    "emperor": "Emperor of the Black and Emerald",
    "architect": "Chief Imperial Architect",
    "sentinel": "Stealth Envoy",
    "commander": "Fleet Commander of the Empire",
    "queen": "Queen of the Empire, Former Sentient Star",
}


def write_lang():
    lang = {
        "itemGroup.elysiumnpcs": "Elysium Court",
        "entity.elysiumnpcs.envoy": "Imperial Envoy",
        "elysiumnpcs.nothing_to_give":
            "They search their office and find nothing to give you.",
        "elysiumnpcs.arrival": "%s has come to find you.",

        # §27.1's amnesty row. The tooltip carries the floor caveat as well as
        # the failure message does, because a player reads the tooltip in the
        # shop and the failure message only after carrying the writ home.
        "item.elysiumnpcs.writ_of_amnesty": "Writ of Amnesty",
        "elysiumnpcs.amnesty.tooltip":
            "Discharges %s Suspicion when used.",
        "elysiumnpcs.amnesty.tooltip_floor":
            "Never below the structural floor of what you have claimed.",
        "elysiumnpcs.amnesty.granted":
            "The file is amended. %s discharged; %s outstanding.",
        "elysiumnpcs.amnesty.at_floor":
            "Nothing here can be discharged. Suspicion is already at the "
            "structural floor of %s — the Empire will not forget a fortress.",
        "elysiumnpcs.amnesty.nothing_filed":
            "There is nothing on file to amend.",

        # Court commissions, §14.5/§19.2. These belong here rather than in the
        # JSON because write_lang() REWRITES that file wholesale — a key added
        # by hand survives exactly until the next time anybody runs this script,
        # which is how all seven of these were lost once already. The validator
        # caught it; the fix is to put them where the generator can see them.
        "elysiumnpcs.commission.expired":
            "The commission lapsed. The Court has noted it.",
        "elysiumnpcs.commission.issued":
            "Commission: deliver %s %s within %s seconds.",
        "elysiumnpcs.commission.not_my_office":
            "This office does not commission work. Take it to the throne.",
        "elysiumnpcs.commission.nothing_wanted":
            "The Court wants for nothing it can name.",
        "elysiumnpcs.commission.outstanding":
            "Outstanding: %s more %s. %s seconds remain.",
        "elysiumnpcs.commission.settled":
            "The commission is discharged. %s rewards are entered against "
            "your name.",
        "elysiumnpcs.commission.unknown_item":
            "something no longer catalogued",
    }
    for kind, (name, refusal) in NAMES.items():
        lang[f"entity.elysiumnpcs.envoy.{kind}"] = name
        lang[f"elysiumnpcs.refusal.{kind}"] = refusal
        lang[f"item.elysiumnpcs.{kind}_summons"] = f"Writ of Audience: {name}"
        lang[f"elysiumnpcs.title.{kind}"] = TITLES[kind]
    path = RES / "lang/en_us.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(lang, handle, indent=2, ensure_ascii=False, sort_keys=True)
    return len(lang)


def write_models():
    """A plain generated model for each writ, so none of them is a purple cube."""
    written = 0
    path = RES / "models/item/writ_of_amnesty.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump({"parent": "minecraft:item/generated",
                   "textures": {"layer0": "elysiumnpcs:item/writ_of_amnesty"}},
                  handle, indent=2)
    written += 1
    for kind in NAMES:
        path = RES / f"models/item/{kind}_summons.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        with open(path, "w", encoding="utf-8") as handle:
            json.dump({"parent": "minecraft:item/generated",
                       "textures": {"layer0": f"elysiumnpcs:item/{kind}_summons"}},
                      handle, indent=2)
        written += 1
    return written


def write_writ_sprites():
    """
    A sealed writ, in the colours of whoever sent it.

    Deliberately the same shape five times: these are five copies of one
    document, and what differs is the seal. A player reads which is which by the
    seal's colour, which is exactly how they read the envoy it summons.
    """
    written = 0
    for kind, palette in PALETTES.items():
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        draw = ImageDraw.Draw(img)
        parchment = (216, 206, 180, 255)
        shadow = (168, 158, 134, 255)
        ink = (28, 26, 24, 255)
        draw.rectangle([3, 1, 12, 14], fill=parchment, outline=ink)
        draw.rectangle([3, 11, 12, 14], fill=shadow)
        for y in (4, 6, 8):
            draw.line([(5, y), (10, y)], fill=shadow)
        seal = tuple(int(palette["regalia"].lstrip("#")[i:i + 2], 16) for i in (0, 2, 4)) + (255,)
        draw.rectangle([6, 10, 9, 13], fill=seal, outline=ink)
        path = RES / f"textures/item/{kind}_summons.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
        written += 1
    return written


def write_amnesty_sprite():
    """
    The same document, with the seal broken.

    Deliberately built from the summons writ rather than given a shape of its
    own. Every writ in this mod is one piece of Imperial paperwork, and what
    tells them apart is the seal — five whole seals in five colours summon five
    offices, and the one BROKEN seal is the file being closed. A player who has
    learned to read the seals already knows what this is.

    It is also the only writ with a struck-through line, which is the second
    reading at a glance: something on the page has been cancelled.
    """
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    parchment = (216, 206, 180, 255)
    shadow = (168, 158, 134, 255)
    ink = (28, 26, 24, 255)
    gold = (216, 177, 58, 255)

    draw.rectangle([3, 1, 12, 14], fill=parchment, outline=ink)
    draw.rectangle([3, 11, 12, 14], fill=shadow)
    for y in (4, 6, 8):
        draw.line([(5, y), (10, y)], fill=shadow)
    # The cancellation, straight through the writing.
    draw.line([(4, 3), (11, 9)], fill=gold)

    # One seal with a break punched through it.
    #
    # Drawn whole and then split, rather than as two small rectangles. Two
    # 2-pixel rectangles with a 1-pixel outline each are 2 pixels of outline
    # and none of fill, so the "broken gold seal" rendered as two black blocks
    # — the difference is invisible in the source and obvious at 16x.
    draw.rectangle([4, 10, 11, 13], fill=gold, outline=ink)
    draw.rectangle([7, 10, 8, 13], fill=ink)

    path = RES / "textures/item/writ_of_amnesty.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    return 1


def main():
    (JAVA / "client/model").mkdir(parents=True, exist_ok=True)

    parts = part_lines(BOXES, SHEET)
    (JAVA / "client/model/EnvoyModel.java").write_text(
        MODEL.format(parts=parts, sheet=SHEET), encoding="utf-8")
    (JAVA / "client/EnvoyRenderer.java").write_text(RENDERER, encoding="utf-8")
    (JAVA / "client/ElysiumNpcsClient.java").write_text(CLIENT, encoding="utf-8")

    skins = 0
    for kind, palette in PALETTES.items():
        write_sheet(RES / f"textures/entity/envoy/{kind}.png", palette)
        skins += 1

    print(f"model + renderer  : 3 files")
    print(f"skins written     : {skins}")
    print(f"writ sprites      : {write_writ_sprites() + write_amnesty_sprite()}")
    print(f"item models       : {write_models()}")
    print(f"lang keys         : {write_lang()}")


if __name__ == "__main__":
    main()
