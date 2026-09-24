package com.elysium.space.client.mob;

import com.elysium.space.entity.mob.Animated;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.entity.animation.json.AnimationLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One model class for every ported mob: the baked geometry, moved by its rig.
 * Keyframe animations are NeoForge JSON animations (ported by port_mobs.py),
 * looked up through NeoForge's AnimationLoader so a reload picks them up.
 */
public class RiggedModel<T extends LivingEntity> extends HierarchicalModel<T> {

    private final ModelPart root;
    private final Rig rig;
    private final ModelPart head;
    private final List<ModelPart> swingParts = new ArrayList<>();

    public RiggedModel(ModelPart root, Rig rig) {
        this.root = root;
        this.rig = rig;
        this.head = rig.head() == null ? null : bone(root, rig.head());
        for (Rig.Swing swing : rig.swings()) {
            swingParts.add(bone(root, swing.bone()));
        }
    }

    /** The part at a path ("body/head"); fails at bake time, naming the path, if the rig and geometry disagree. */
    static ModelPart bone(ModelPart root, String path) {
        ModelPart part = root;
        for (String segment : path.split("/")) {
            part = part.getChild(segment);
        }
        return part;
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        if (head != null) {
            head.yRot += netHeadYaw * Mth.DEG_TO_RAD;
            if (rig.pitch()) {
                head.xRot += headPitch * Mth.DEG_TO_RAD;
            }
        }
        for (int i = 0; i < swingParts.size(); i++) {
            Rig.Swing s = rig.swings().get(i);
            float t = s.byWalk() ? limbSwing : ageInTicks;
            float angle = Mth.cos(t * s.rate() + s.phase()) * s.amp() * (s.byWalk() ? limbSwingAmount : 1.0F);
            ModelPart part = swingParts.get(i);
            switch (s.axis()) {
                case X -> part.xRot += angle;
                case Y -> part.yRot += angle;
                case Z -> part.zRot += angle;
            }
        }
        if (rig.walk() != null) {
            animateWalk(AnimationLoader.INSTANCE.getAnimationHolder(rig.walk().animation()),
                    limbSwing, limbSwingAmount, rig.walk().speed(), rig.walk().scale());
        }
        if (entity instanceof Animated animated) {
            for (Map.Entry<String, net.minecraft.resources.ResourceLocation> e : rig.states().entrySet()) {
                AnimationState state = animated.animation(e.getKey());
                if (state != null) {
                    animate(state, AnimationLoader.INSTANCE.getAnimationHolder(e.getValue()), ageInTicks);
                }
            }
        }
    }
}
