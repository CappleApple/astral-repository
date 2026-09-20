package com.cappleapple.astralrepository.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AstralViewBobbingTest {
    @Test void removesTranslationAndRotationWithoutRemovingHurtOrItemTransforms() {
        for (int phase = 0; phase < 24; phase++) {
            float t = phase / 24F * (float)Math.PI * 2;
            var before = new Matrix4f().rotateZ(.14F).rotateX(-.08F);
            var bob = new Matrix4f().translate((float)Math.sin(t)*.05F, -(float)Math.abs(Math.cos(t))*.1F, 0)
                    .rotateZ((float)Math.sin(t)*.02F).rotateX((float)Math.abs(Math.cos(t-.2F))*.04F);
            var after = new Matrix4f(before).mul(bob);
            var item = new Matrix4f().translate(.6F,-.4F,-1.2F).rotateY(.8F).scale(.7F);
            var point = new Vector3f(.1F,.2F,.3F);
            var expected = new Matrix4f(before).mul(item).transformPosition(new Vector3f(point));
            var actual = AstralViewBobbing.correction(before, after, new Matrix4f()).mul(after).mul(item)
                    .transformPosition(new Vector3f(point));
            assertTrue(expected.distance(actual) < .000001F);
        }
    }
    @Test void scopesCorrectionToHandsAndResetsForDisabledBobbing() {
        var identity = new Matrix4f(); var bob = new Matrix4f().translate(.1F,.2F,0).rotateZ(.03F);
        AstralViewBobbing.beginHand();
        AstralViewBobbing.beforeBob(identity,identity); AstralViewBobbing.afterBob(identity,bob);
        assertFalse(AstralViewBobbing.correction().equals(identity));
        AstralViewBobbing.endHand();
        AstralViewBobbing.beforeBob(identity,identity); AstralViewBobbing.afterBob(identity,bob);
        assertEquals(identity,AstralViewBobbing.correction());
        AstralViewBobbing.beginHand(); assertEquals(identity,AstralViewBobbing.correction());
        AstralViewBobbing.endHand();
    }
    @Test void worldSampleUsesTheUnbobbedRayAtTheActualRasterPixel() {
        var projection = new Matrix4f().perspective(1.1F, 1.6F, .1F, 100);
        var hurt = new Matrix4f().rotateZ(.14F).rotateX(-.08F);
        var view = new Matrix4f().rotateY(.72F).rotateX(-.21F);
        try {
            for (int phase = 0; phase < 24; phase++) {
                float t = phase / 24F * (float)(Math.PI*2);
                var bob = new Matrix4f().translate((float)Math.sin(t)*.05F, -(float)Math.abs(Math.cos(t))*.1F, 0)
                        .rotateZ((float)Math.sin(t)*.02F).rotateX((float)Math.abs(Math.cos(t-.2F))*.04F);
                AstralViewBobbing.beginWorld();
                AstralViewBobbing.beforeBob(new Matrix4f(), hurt);
                AstralViewBobbing.afterBob(new Matrix4f(), new Matrix4f(hurt).mul(bob));
                var point = new Vector4f(.3F,.7F,-6,1);
                var raster = new Matrix4f(projection).mul(hurt).mul(bob).mul(view).transform(new Vector4f(point));
                var worldSample = new Matrix4f(view).invert().mul(AstralViewBobbing.worldProjectionBob()).mul(view)
                        .transform(new Vector4f(point));
                var unbobbed = new Matrix4f(projection).mul(hurt).mul(view).transform(worldSample);
                assertEquals(raster.x/raster.w,unbobbed.x/unbobbed.w,.000001);
                assertEquals(raster.y/raster.w,unbobbed.y/unbobbed.w,.000001);
                assertFalse(new Matrix4f(projection).mul(hurt).mul(view).transform(new Vector4f(point)).equals(raster));
            }
        } finally { AstralViewBobbing.endWorld(); }
    }

    @Test void worldCaptureResetsEachFrameAndSurvivesTheSeparateHandPass() {
        var identity = new Matrix4f(); var bob = new Matrix4f().translate(.1F,.2F,0).rotateZ(.03F);
        AstralViewBobbing.beginWorld();
        AstralViewBobbing.beforeBob(identity,identity); AstralViewBobbing.afterBob(identity,bob);
        assertTrue(AstralViewBobbing.worldProjectionBob().equals(bob, .000001F));
        AstralViewBobbing.beginHand();
        AstralViewBobbing.beforeBob(identity,identity); AstralViewBobbing.afterBob(identity,new Matrix4f().rotateX(.1F));
        AstralViewBobbing.endHand();
        assertTrue(AstralViewBobbing.worldProjectionBob().equals(bob, .000001F));
        AstralViewBobbing.endWorld();
        assertEquals(identity,AstralViewBobbing.worldProjectionBob());
        AstralViewBobbing.beginWorld();
        assertEquals(identity,AstralViewBobbing.worldProjectionBob());
        AstralViewBobbing.endWorld();
    }

}
