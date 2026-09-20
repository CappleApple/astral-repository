package com.cappleapple.astralrepository.client;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Cancels view bob in the astral volume while leaving the visible model and camera effects intact. */
public final class AstralViewBobbing {
    private static final Matrix4f BEFORE = new Matrix4f();
    private static final Matrix4f CORRECTION = new Matrix4f();
    private static final Matrix4f WORLD_BOB = new Matrix4f();
    private static boolean hand, world;

    public static void beginWorld() { world = true; WORLD_BOB.identity(); }
    public static void endWorld() { world = false; WORLD_BOB.identity(); }
    public static Matrix4f worldProjectionBob() { return WORLD_BOB; }

    public static void beginHand() { hand = true; CORRECTION.identity(); }
    public static void endHand() { hand = false; CORRECTION.identity(); }
    public static void beforeBob(Matrix4fc modelView, Matrix4fc pose) {
        if (hand) BEFORE.set(modelView).mul(pose);
        else if (world) BEFORE.set(pose);
    }
    public static void afterBob(Matrix4fc modelView, Matrix4fc pose) {
        if (hand) correction(BEFORE, new Matrix4f(modelView).mul(pose), CORRECTION);
        else if (world) WORLD_BOB.set(BEFORE).invert().mul(pose);
    }
    public static Matrix4f correction() { return CORRECTION; }

    static Matrix4f correction(Matrix4fc before, Matrix4fc after, Matrix4f destination) {
        return destination.set(after).invert().mulLocal(before);
    }
    private AstralViewBobbing() {}
}
