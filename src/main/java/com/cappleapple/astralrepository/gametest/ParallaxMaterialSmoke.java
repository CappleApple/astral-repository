package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralRepository;
import com.cappleapple.astralrepository.AstralClientConfig;
import net.minecraft.world.phys.Vec3;
import com.cappleapple.astralrepository.client.AstralPlaneRenderType;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.*;

/** Development-only, frozen-time renders through the actual loaded astral material shader. */
public final class ParallaxMaterialSmoke {
    private static final int SIZE = 384;
    private static final float DISTANCE = 3, HALF_FRUSTUM = 1.35F;
    private record View(float eyeX, float angle, boolean orthographic) {}
    private static final View FRONT = new View(0, 0, false), SHIFT = new View(.75F, 0, false), ANGLE = new View(0, 38, false);
    private ParallaxMaterialSmoke() {}

    public static void verify() throws Exception { verify(Path.of("../build/client-smoke/parallax")); }
    public static void verify(Path output) throws Exception {
        check(Boolean.getBoolean("astral_repository.clientSmoke"), "Parallax fixture is only available to the development smoke client");
        RenderSystem.assertOnRenderThread();
        check(AstralPlaneRenderType.ready(), "Astral shader is not loaded");
        Files.createDirectories(output);
        var minecraft = Minecraft.getInstance();
        var previousShader = RenderSystem.getShader();
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        float gameTime = RenderSystem.getShaderGameTime(), fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        float[] color = RenderSystem.getShaderColor().clone(), clearColor = new float[4];
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        int[] viewport = new int[4], scissorBox = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport); GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING), arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND), scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int[] textures = new int[3], bindings = new int[3];
        for (int unit = 0; unit < 3; unit++) { textures[unit] = RenderSystem.getShaderTexture(unit); RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit); bindings[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D); }
        RenderSystem.activeTexture(activeTexture);
        var atlas = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        atlas.setBlurMipmap(false, false);
        RenderSystem.getModelViewStack().pushMatrix();
        TextureTarget target = null;
        try {
            RenderSystem.disableScissor(); RenderSystem.depthMask(true); RenderSystem.disableBlend(); RenderSystem.enableDepthTest(); RenderSystem.enableCull();
            RenderSystem.setShaderFogStart(1000); RenderSystem.setShaderFogEnd(2000); RenderSystem.setShaderColor(1, 1, 1, 1);
            target = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
            TextureAtlasSprite sprite = minecraft.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(new ResourceLocation(AstralRepository.MOD_ID,"block/astral_geode"));
            try (NativeImage zero = render(target, sprite, 0, FRONT, 4200);
                 NativeImage zeroShift = render(target, sprite, 0, SHIFT, 4200);
                 NativeImage zeroAngle = render(target, sprite, 0, ANGLE, 4200);
                 NativeImage front = render(target, sprite, 1, FRONT, 4200);
                 NativeImage repeat = render(target, sprite, 1, FRONT, 4200);
                 NativeImage shifted = render(target, sprite, 1, SHIFT, 4200);
                 NativeImage angled = render(target, sprite, 1, ANGLE, 4200);
                 NativeImage drift = render(target, sprite, 1, FRONT, 4440);
                 NativeImage gui = render(target, sprite, 1, new View(0, 0, true), 4200);
                 NativeImage guiShift = render(target, sprite, 1, new View(.75F, 0, true), 4200);
                 NativeImage standard = render(target, sprite, .18F, FRONT, 4200);
                 NativeImage standardShift = render(target, sprite, .18F, SHIFT, 4200)) {
                double baseShift = difference(zero, zeroShift), repeatDifference = difference(front, repeat);
                double parallax = difference(front, shifted), movement = difference(front, drift), guiMovement = difference(gui, guiShift);
                double baseAngle = materialDifference(zero, FRONT, zeroAngle, ANGLE), angleResponse = materialDifference(front, FRONT, angled, ANGLE);
                double brightness = meanBrightness(zero), baseChanged = changedFraction(zero, zeroShift, .03), visiblyChanged = changedFraction(front, shifted, .03);
                double defaultShift = difference(standard, standardShift), driftingFraction = changedFraction(front, drift, .03);
                // Save every comparison before asserting: a failed gate must retain inspectable GPU evidence.
                zero.writeToFile(output.resolve("parallax_opacity_zero.png")); zeroShift.writeToFile(output.resolve("parallax_opacity_zero_shift.png")); zeroAngle.writeToFile(output.resolve("parallax_opacity_zero_angle.png"));
                front.writeToFile(output.resolve("parallax_base.png")); repeat.writeToFile(output.resolve("parallax_frozen_repeat.png")); shifted.writeToFile(output.resolve("parallax_eye_shift.png"));
                angled.writeToFile(output.resolve("parallax_angle.png")); drift.writeToFile(output.resolve("parallax_time_drift.png"));
                gui.writeToFile(output.resolve("parallax_orthographic.png")); guiShift.writeToFile(output.resolve("parallax_orthographic_shift.png"));
                standard.writeToFile(output.resolve("parallax_default_base.png")); standardShift.writeToFile(output.resolve("parallax_default_eye_shift.png"));
                String report = "Actual GPU shader; 384x384 FBO; frozen GameTime=4200 ticks; compensated off-axis perspective keeps the portal quad and UVs fixed.\n"
                        + "ROI=254x254; RGB differences normalized to 0..1; visibly changed means mean channel delta > 0.03 (7.65 byte levels).\n"
                        + "base_brightness=" + brightness + "\nopacity0_eye_shift=" + baseShift + "\nopacity0_visibly_changed_fraction=" + baseChanged
                        + "\nfrozen_repeat=" + repeatDifference + "\nopacity1_eye_shift=" + parallax + "\nopacity1_visibly_changed_fraction=" + visiblyChanged
                        + "\nopacity018_eye_shift=" + defaultShift + "\nopacity0_reprojected_rotation=" + baseAngle + "\nopacity1_reprojected_rotation=" + angleResponse
                        + "\n12_second_drift=" + movement + "\n12_second_visibly_changed_fraction=" + driftingFraction + "\northographic_compensated_shift=" + guiMovement + "\n"
                        + "Eye gate: mean > 0.007 and > 8x opacity-zero baseline; visibly changed fraction > 0.002 and > 8x baseline.\n";
                Files.writeString(output.resolve("parallax_metrics.txt"), report);
                AstralRepository.LOGGER.info("Parallax GPU measurements before assertions:\n{}", report);
                check(brightness > .12, "The fixture failed to draw the actual mineral texture");
                check(baseShift < .001, "Compensated eye translation moved the zero-opacity artwork: " + baseShift);
                check(repeatDifference == 0, "Frozen time and view did not reproduce the same shader framebuffer: " + repeatDifference);
                // Sparse bright particles should not need to change most of a subdued nebula. Require
                // both an average above quantization and >129 distinctly changed pixels in this ROI.
                check(parallax > .007 && parallax > baseShift * 8 && visiblyChanged > .002 && visiblyChanged > baseChanged * 8,
                        "Eye translation lacks visible interior parallax: mean=" + parallax + ", visible fraction=" + visiblyChanged + ", base=" + baseShift);
                check(baseAngle < .012 && angleResponse > baseAngle + .006, "Material rotation did not change the interior at the same source texels: base=" + baseAngle + ", overlay=" + angleResponse);
                check(movement > .0005, "The frozen-view interior did not drift over twelve seconds: " + movement);
                check(guiMovement < .001, "Moving an orthographic item changed its interior as if attached to the HUD: " + guiMovement);
                AstralRepository.LOGGER.info("Parallax GPU gate passed: eye={}, visibleFraction={}, rotation={}, drift={}, base={}, orthographic={}", parallax, visiblyChanged, angleResponse, movement, baseShift, guiMovement);
            }
            verifyBobbing(target, sprite, output);
            verifyWorldBobbing(target, sprite, output);
            verifyWallStability(target, sprite, output);
            verifyWorld(target, sprite, output);
            verifySpeeds(target, sprite, output);
        } finally {
            AstralPlaneRenderType.endWorld();
            if (target != null) target.destroyBuffers();
            RenderSystem.getModelViewStack().popMatrix(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            RenderSystem.setShaderGameTime(0, gameTime * 24000); RenderSystem.setShaderFogStart(fogStart); RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]); RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            atlas.restoreLastBlurMipmap(); RenderSystem.setShader(() -> previousShader);
            for (int unit = 0; unit < 3; unit++) { RenderSystem.setShaderTexture(unit, textures[unit]); RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit); RenderSystem.bindTexture(bindings[unit]); }
            RenderSystem.activeTexture(activeTexture); RenderSystem.depthFunc(depthFunction); RenderSystem.depthMask(depthMask);
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (scissor) RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]); else RenderSystem.disableScissor();
            BufferUploader.reset(); GL30.glBindVertexArray(vao); GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
            GlStateManager._glUseProgram(program);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer); GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    private static void verifyBobbing(TextureTarget target, TextureAtlasSprite sprite, Path output) throws Exception {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        float walk = player.walkDist, previousWalk = player.walkDistO, bob = player.bob, previousBob = player.oBob;
        var method = net.minecraft.client.renderer.GameRenderer.class.getDeclaredMethod("bobView", PoseStack.class, float.class);
        method.setAccessible(true);
        AstralPlaneRenderType.endWorld();
        var report = new StringBuilder("Actual GameRenderer.bobView injection; frozen GPU material; screen displacement compensated only in fixture.\n");
        try (NativeImage base = render(target,sprite,1,FRONT,4200)) {
            for (int phase = 0; phase < 4; phase++) {
                player.walkDist = player.walkDistO = phase * .43F + .2F;
                player.bob = player.oBob = .1F;
                RenderSystem.getModelViewStack().identity(); RenderSystem.applyModelViewMatrix();
                var pose = new PoseStack();
                // The same call during world projection must leave material coordinates alone.
                com.cappleapple.astralrepository.client.AstralViewBobbing.endHand();
                method.invoke(mc.gameRenderer,pose,.5F);
                check(com.cappleapple.astralrepository.client.AstralViewBobbing.correction().equals(new Matrix4f()), "World bob changed shader eye coordinates");
                pose = new PoseStack();
                com.cappleapple.astralrepository.client.AstralViewBobbing.beginHand();
                method.invoke(mc.gameRenderer,pose,.5F);
                Matrix4f transform = new Matrix4f(pose.last().pose());
                check(!com.cappleapple.astralrepository.client.AstralViewBobbing.correction().equals(new Matrix4f()), "Hand bob injection did not capture the transform");
                try (NativeImage corrected = render(target,sprite,1,FRONT,4200,false,1,transform)) {
                    com.cappleapple.astralrepository.client.AstralViewBobbing.endHand();
                    try (NativeImage uncorrected = render(target,sprite,1,FRONT,4200,false,1,transform)) {
                        double stable = difference(base,corrected), changed = difference(base,uncorrected);
                        report.append("phase_").append(phase).append(" corrected=").append(stable).append(" uncorrected=").append(changed).append('\n');
                        Files.writeString(output.resolve("bobbing_metrics.txt"),report);
                        check(stable < .0001, "Hand bob still changes the shader: " + stable);
                        check(changed > .0005, "Bob fixture did not exercise parallax: " + changed);
                        if (phase == 0) { base.writeToFile(output.resolve("bob_base.png")); corrected.writeToFile(output.resolve("bob_corrected.png")); uncorrected.writeToFile(output.resolve("bob_uncorrected.png")); }
                    }
                }
            }
            AstralRepository.LOGGER.info("Astral bobbing GPU checks:\n{}",report);
        } finally {
            player.walkDist=walk; player.walkDistO=previousWalk; player.bob=bob; player.oBob=previousBob;
            com.cappleapple.astralrepository.client.AstralViewBobbing.endHand();
        }
    }

    private static void verifyWorldBobbing(TextureTarget target, TextureAtlasSprite sprite, Path output) throws Exception {
        var mc = Minecraft.getInstance(); var player = mc.player;
        float walk = player.walkDist, previousWalk = player.walkDistO, bob = player.bob, previousBob = player.oBob;
        boolean shooting = AstralClientConfig.astralShootingStars.get();
        var method = net.minecraft.client.renderer.GameRenderer.class.getDeclaredMethod("bobView", PoseStack.class, float.class);
        method.setAccessible(true);
        var report = new StringBuilder("Actual GameRenderer world projection bob; same screen pixels compared without compensating model movement.\n");
        try {
            AstralClientConfig.astralShootingStars.set(false);
            com.cappleapple.astralrepository.client.AstralViewBobbing.beginWorld();
            // Exercise both the middle of a volume cell and an exact four-block
            // layer boundary, where a wrong virtual entry point changes the layer.
            for (int location=0;location<2;location++) {
            AstralPlaneRenderType.beginWorld(new Matrix4f(),location==0?new Vec3(17,13,DISTANCE+1):new Vec3(0,0,DISTANCE));
            try (NativeImage base = render(target,sprite,1,FRONT,4200,false,1,new Matrix4f(),true);
                 NativeImage baseTexture = render(target,sprite,0,FRONT,4200,false,1,new Matrix4f(),true)) {
                for (int phase = 0; phase < 4; phase++) {
                    player.walkDist=player.walkDistO=phase*.43F+.2F; player.bob=player.oBob=.1F;
                    RenderSystem.getModelViewStack().identity(); RenderSystem.applyModelViewMatrix();
                    com.cappleapple.astralrepository.client.AstralViewBobbing.beginWorld();
                    var pose = new PoseStack(); method.invoke(mc.gameRenderer,pose,.5F);
                    var transform = new Matrix4f(pose.last().pose());
                    check(!com.cappleapple.astralrepository.client.AstralViewBobbing.worldProjectionBob().equals(new Matrix4f()),"World bob injection did not capture the projection transform");
                    try (NativeImage corrected = render(target,sprite,1,FRONT,4200,false,1,transform,true);
                         NativeImage bobbedTexture = render(target,sprite,0,FRONT,4200,false,1,transform,true)) {
                        com.cappleapple.astralrepository.client.AstralViewBobbing.endWorld();
                        try (NativeImage uncorrected = render(target,sprite,1,FRONT,4200,false,1,transform,true)) {
                            double stable=difference(base,corrected,96), changed=difference(base,uncorrected,96), visibleBob=difference(baseTexture,bobbedTexture,96);
                            report.append("location_").append(location).append(" phase_").append(phase).append(" same_pixel_corrected=").append(stable)
                                    .append(" uncorrected=").append(changed).append(" base_texture_bob=").append(visibleBob).append('\n');
                            Files.writeString(output.resolve("world_bobbing_metrics.txt"),report);
                            if(phase==0&&location==0){base.writeToFile(output.resolve("world_bob_off.png"));corrected.writeToFile(output.resolve("world_bob_on.png"));uncorrected.writeToFile(output.resolve("world_bob_uncorrected.png"));bobbedTexture.writeToFile(output.resolve("world_bob_base_texture.png"));}
                            check(visibleBob>.005,"World bob fixture incorrectly held the block texture still: "+visibleBob);
                            check(stable<.001&&stable<changed*.1,"World shader still bounces with the camera: corrected="+stable+", uncorrected="+changed);
                        }
                    }
                }
            }
            }
            AstralRepository.LOGGER.info("World shader bobbing GPU checks:\n{}",report);
        } finally {
            player.walkDist=walk; player.walkDistO=previousWalk; player.bob=bob; player.oBob=previousBob;
            AstralClientConfig.astralShootingStars.set(shooting);
            com.cappleapple.astralrepository.client.AstralViewBobbing.endWorld(); AstralPlaneRenderType.endWorld();
        }
    }

    private static NativeImage world(TextureTarget target, TextureAtlasSprite sprite, View view, long time,
            boolean split, double x, double y, float opacity) {
        AstralPlaneRenderType.beginWorld(new Matrix4f(), new Vec3(x + view.eyeX(), y, DISTANCE));
        return render(target, sprite, opacity, view, time, split);
    }
    private static void verifyWorld(TextureTarget target, TextureAtlasSprite sprite, Path output) throws Exception {
        boolean shooting = AstralClientConfig.astralShootingStars.get();
        try {
            AstralClientConfig.astralShootingStars.set(false);
            try (NativeImage whole = world(target, sprite, FRONT, 4200, false, 0, 0, 1);
                 NativeImage split = world(target, sprite, FRONT, 4200, true, 0, 0, 1);
                 NativeImage shifted = world(target, sprite, SHIFT, 4200, true, 0, 0, 1);
                 NativeImage moved = world(target, sprite, FRONT, 4200, true, 17, 8, 1);
                 NativeImage wrapped = world(target, sprite, FRONT, 4200, true, 4096, 0, 1);
                 NativeImage zero = world(target, sprite, FRONT, 4200, false, 0, 0, 0);
                 NativeImage zeroShift = world(target, sprite, SHIFT, 4200, false, 0, 0, 0)) {
                whole.writeToFile(output.resolve("world_shared_plane.png")); split.writeToFile(output.resolve("world_restarted_uvs.png"));
                shifted.writeToFile(output.resolve("world_eye_shift.png")); moved.writeToFile(output.resolve("world_other_location.png"));
                double seams = difference(whole, split), parallax = difference(whole, shifted), relocation = difference(whole, moved);
                double wrap = difference(whole, wrapped), base = difference(zero, zeroShift);
                String report = "Shared world planes; split faces each restart the full sprite UV rectangle.\n"
                        + "split_uv_difference=" + seams + "\neye_parallax=" + parallax + "\nother_location=" + relocation
                        + "\nworld_period_difference=" + wrap + "\nbase_eye_difference=" + base + "\n";
                Files.writeString(output.resolve("world_metrics.txt"), report); AstralRepository.LOGGER.info("World astral GPU checks:\n{}", report);
                check(seams < .0001, "World field resets across independent face UVs: " + seams);
                check(parallax > .004, "World field lacks eye-dependent depth: " + parallax);
                check(relocation > .004, "World field repeats per block: " + relocation);
                check(wrap < .0001 && base < .0001, "World precision wrapping or base artwork changed");
            }
            int activeFrames = 0, quietFrames = 0, largest = 0; long peakTime = 0;
            for (long time = 0; time < 480; time += 2) {
                AstralClientConfig.astralShootingStars.set(false);
                try (NativeImage quiet = world(target, sprite, FRONT, time, false, 0, 0, 1)) {
                    AstralClientConfig.astralShootingStars.set(true);
                    try (NativeImage stars = world(target, sprite, FRONT, time, false, 0, 0, 1)) {
                        int changed = 0;
                        for (int y = 65; y < SIZE - 65; y++) for (int x = 65; x < SIZE - 65; x++)
                            if (delta(quiet.getPixelRGBA(x,y), stars.getPixelRGBA(x,y)) > .025) changed++;
                        if (changed > 0) activeFrames++; else quietFrames++;
                        if (changed > largest) { largest = changed; peakTime = time; stars.writeToFile(output.resolve("shooting_star_peak.png")); quiet.writeToFile(output.resolve("shooting_star_disabled.png")); }
                    }
                }
            }
            Files.writeString(output.resolve("shooting_star_metrics.txt"), "frames=240\nactive="+activeFrames+"\nquiet="+quietFrames+"\npeak_pixels="+largest+"\npeak_tick="+peakTime+"\n");
            check(activeFrames > 0 && activeFrames <= 8 && quietFrames > 230 && largest > 5, "Shooting stars are missing or always active: " + activeFrames + "/" + quietFrames + "/" + largest);
            AstralClientConfig.astralShootingStars.set(false);
            try(NativeImage quiet=world(target,sprite,FRONT,peakTime,false,0,0,.18F)) {
                AstralClientConfig.astralShootingStars.set(true);
                try(NativeImage trail=world(target,sprite,FRONT,peakTime,false,0,0,.18F)) {
                    int hx=0,hy=0;double peak=0;int minX=SIZE,maxX=0,minY=SIZE,maxY=0,away=0;
                    for(int y=65;y<SIZE-65;y++)for(int x=65;x<SIZE-65;x++){double d=delta(quiet.getPixelRGBA(x,y),trail.getPixelRGBA(x,y));if(d>peak){peak=d;hx=x;hy=y;}if(d>.02){minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}}
                    for(int y=65;y<SIZE-65;y++)for(int x=65;x<SIZE-65;x++)if((x-hx)*(x-hx)+(y-hy)*(y-hy)>144&&delta(quiet.getPixelRGBA(x,y),trail.getPixelRGBA(x,y))>.02)away++;
                    trail.writeToFile(output.resolve("shooting_star_default_trail.png"));quiet.writeToFile(output.resolve("shooting_star_default_disabled.png"));
                    Files.writeString(output.resolve("shooting_star_default_metrics.txt"),"opacity=0.18\npeak_contrast="+peak+"\ntrail_pixels_beyond_head="+away+"\nwidth="+(maxX-minX+1)+"\nheight="+(maxY-minY+1)+"\n");
                    check(peak>.10&&away>20&&maxX-minX>30,"Default-opacity shooting star lacks a visible extended trail: "+peak+" / "+away+" / "+(maxX-minX));
                }
            }
            for (int step = 0; step < 4; step++) try (NativeImage frame = world(target, sprite, FRONT, peakTime + step * 4, false, 0, 0, 1)) {
                frame.writeToFile(output.resolve("shooting_star_motion_" + step + ".png"));
            }
        } finally { AstralClientConfig.astralShootingStars.set(shooting); AstralPlaneRenderType.endWorld(); }
    }

    private static void verifyWallStability(TextureTarget target, TextureAtlasSprite sprite, Path output) throws Exception {
        boolean shooting=AstralClientConfig.astralShootingStars.get();
        try {
            AstralClientConfig.astralShootingStars.set(false);
            double maximum=0;int flashes=0;
            for(int i=0;i<8;i++) {
                double x=20+i*13, y=12+i*7;
                AstralPlaneRenderType.beginWorld(new Matrix4f(),new Vec3(x,y,DISTANCE+4-.0001));
                try(NativeImage a=render(target,sprite,1,FRONT,4200,true,3)) {
                    AstralPlaneRenderType.beginWorld(new Matrix4f(),new Vec3(x,y,DISTANCE+4+.0001));
                    try(NativeImage b=render(target,sprite,1,FRONT,4200,true,3)) {
                        maximum=Math.max(maximum,difference(a,b));
                        for(int py=65;py<SIZE-65;py++)for(int px=65;px<SIZE-65;px++)if(delta(a.getPixelRGBA(px,py),b.getPixelRGBA(px,py))>.15)flashes++;
                        if(i==0){a.writeToFile(output.resolve("wall_boundary_before.png"));b.writeToFile(output.resolve("wall_boundary_after.png"));}
                    }
                }
            }
            String report="Actual GPU; nine independent one-block faces, eight world locations, frozen time, +/-0.0001 block around a depth-plane boundary.\nmaximum_mean_delta="+maximum+"\nflashing_pixels_over_015="+flashes+"\n";
            Files.writeString(output.resolve("wall_stability_metrics.txt"),report);AstralRepository.LOGGER.info("Wall stability checks:\n{}",report);
            check(maximum<.001&&flashes==0,"Tiny surface-coordinate perturbation switches astral depth layers: "+maximum+" / "+flashes);
        }finally{AstralClientConfig.astralShootingStars.set(shooting);AstralPlaneRenderType.endWorld();}
    }

    private static void verifySpeeds(TextureTarget target, TextureAtlasSprite sprite, Path output) throws Exception {
        var settings = java.util.List.of(AstralClientConfig.astralLayerDriftSpeed, AstralClientConfig.astralLayerWobbleSpeed,
                AstralClientConfig.astralParticleSpeed, AstralClientConfig.astralTwinkleSpeed, AstralClientConfig.astralShootingStarSpeed);
        var names = java.util.List.of("layer_drift", "layer_wobble", "particle", "twinkle", "shooting_star");
        var saved = settings.stream().map(value -> value.get()).toList();
        boolean shooting = AstralClientConfig.astralShootingStars.get();
        StringBuilder report = new StringBuilder("Actual GPU; all other clocks frozen during each independent speed comparison.\n");
        try {
            settings.forEach(value -> value.set(0.0)); AstralClientConfig.astralShootingStars.set(true);
            try (NativeImage a = world(target, sprite, FRONT, 4200, false, 0, 0, 1);
                 NativeImage b = world(target, sprite, FRONT, 4440, false, 0, 0, 1);
                 NativeImage shifted = world(target, sprite, SHIFT, 4200, false, 0, 0, 1)) {
                double frozen = difference(a, b), parallax = difference(a, shifted);
                report.append("all_zero_time_difference=").append(frozen).append("\nfrozen_camera_parallax=").append(parallax).append('\n');
                check(frozen == 0, "Zero speed did not freeze the field: " + frozen);
                check(parallax > .004, "Freezing animation disabled world parallax: " + parallax);
            }
            for (int i = 0; i < settings.size(); i++) {
                var setting = settings.get(i); setting.set(1.0);
                long sample = 4200; double motion = 0;
                if (i == 4) {
                    try (NativeImage quiet = world(target, sprite, FRONT, 0, false, 0, 0, 1)) {
                        for (long time = 2; time < 480; time += 2) try (NativeImage frame = world(target, sprite, FRONT, time, false, 0, 0, 1)) {
                            double delta = difference(quiet, frame);
                            if (delta > motion) { motion = delta; sample = time; }
                        }
                    }
                } else try (NativeImage a = world(target, sprite, FRONT, 4200, false, 0, 0, 1);
                            NativeImage b = world(target, sprite, FRONT, 4440, false, 0, 0, 1)) { motion = difference(a, b); }
                try (NativeImage normal = world(target, sprite, FRONT, sample, false, 0, 0, 1)) {
                    setting.set(2.0);
                    try (NativeImage twice = world(target, sprite, FRONT, sample / 2, false, 0, 0, 1)) {
                        double scaling = difference(normal, twice);
                        report.append(names.get(i)).append("_motion=").append(motion).append("\n")
                                .append(names.get(i)).append("_twice_speed_half_time_difference=").append(scaling).append('\n');
                        Files.writeString(output.resolve("speed_metrics.txt"), report);
                        check(motion > .000001, names.get(i) + " speed had no visible effect");
                        check(scaling < .00001, names.get(i) + " did not run at twice the requested speed: " + scaling);
                    }
                }
                setting.set(0.0);
            }
            AstralRepository.LOGGER.info("Astral animation speed GPU checks:\n{}", report);
        } finally {
            for (int i = 0; i < settings.size(); i++) settings.get(i).set(saved.get(i));
            AstralClientConfig.astralShootingStars.set(shooting); AstralPlaneRenderType.endWorld();
        }
    }

    private static Matrix4f projection(View view) {
        Matrix4f result;
        if (view.orthographic()) {
            result = new Matrix4f().setOrtho(-HALF_FRUSTUM, HALF_FRUSTUM, -HALF_FRUSTUM, HALF_FRUSTUM, .1F, 100);
            result.m30(result.m00() * view.eyeX());
        } else {
            result = new Matrix4f().setPerspective(2 * (float)Math.atan(HALF_FRUSTUM / DISTANCE), 1, .1F, 100);
            // Off-axis lens shift exactly compensates screen displacement, not the eye ray.
            result.m20(-result.m00() * view.eyeX() / DISTANCE);
        }
        return result;
    }
    private static Matrix4f modelView(View view) { return new Matrix4f().translation(-view.eyeX(), 0, -DISTANCE).rotateY((float)Math.toRadians(view.angle())); }
    private static NativeImage render(TextureTarget target, TextureAtlasSprite sprite, float opacity, View view, long time) {
        return render(target, sprite, opacity, view, time, false);
    }
    private static NativeImage render(TextureTarget target, TextureAtlasSprite sprite, float opacity, View view, long time, boolean split) {
        return render(target,sprite,opacity,view,time,split,1);
    }
    private static NativeImage render(TextureTarget target,TextureAtlasSprite sprite,float opacity,View view,long time,boolean split,int grid) {
        return render(target,sprite,opacity,view,time,split,grid,new Matrix4f());
    }
    private static NativeImage render(TextureTarget target,TextureAtlasSprite sprite,float opacity,View view,long time,boolean split,int grid,Matrix4f bob) {
        return render(target,sprite,opacity,view,time,split,grid,bob,false);
    }
    private static NativeImage render(TextureTarget target,TextureAtlasSprite sprite,float opacity,View view,long time,boolean split,int grid,Matrix4f bob,boolean worldProjection) {
        target.setClearColor(0, 0, 0, 1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
        RenderSystem.getModelViewStack().set(worldProjection?new Matrix4f():bob).mul(modelView(view)); RenderSystem.applyModelViewMatrix();
        Matrix4f projection = grid==1?projection(view):new Matrix4f().setPerspective(2*(float)Math.atan(1.8F/DISTANCE),1,.1F,100);
        RenderSystem.setProjectionMatrix(projection.mul(worldProjection?bob:bob.invert(new Matrix4f())), VertexSorting.DISTANCE_TO_ORIGIN); RenderSystem.setShaderGameTime(time, 0);
        AstralPlaneRenderType.ASTRAL_PLANE.setupRenderState();
        try {
            RenderSystem.getShader().safeGetUniform("AstralOverlayOpacity").set(opacity);
            BufferBuilder vertices = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            int pieces = grid>1?grid:split?2:1;float radius=grid>1?1.5F:1;
            for(int row=0;row<grid;row++)for (int i = 0; i < pieces; i++) {
                float left=-radius+2*radius*i/pieces,right=-radius+2*radius*(i+1)/pieces;
                float bottom=-radius+2*radius*row/grid,top=-radius+2*radius*(row+1)/grid;
                vertex(vertices,left,bottom,sprite.getU0(),sprite.getV1());vertex(vertices,right,bottom,sprite.getU1(),sprite.getV1());
                vertex(vertices,right,top,sprite.getU1(),sprite.getV0());vertex(vertices,left,top,sprite.getU0(),sprite.getV0());
            }
            BufferUploader.drawWithShader(vertices.buildOrThrow());
        } finally { AstralPlaneRenderType.ASTRAL_PLANE.clearRenderState(); }
        return Screenshot.takeScreenshot(target);
    }
    private static void vertex(VertexConsumer vertices, float x, float y, float u, float v) {
        vertices.addVertex(x, y, 0).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 0, 1);
    }
    private static double difference(NativeImage a, NativeImage b) { return difference(a,b,65); }
    private static double difference(NativeImage a, NativeImage b, int inset) {
        double sum = 0; int count = 0;
        for (int y = inset; y < SIZE - inset; y++) for (int x = inset; x < SIZE - inset; x++) { sum += delta(a.getPixelRGBA(x, y), b.getPixelRGBA(x, y)); count++; }
        return sum / count;
    }
    private static double changedFraction(NativeImage a, NativeImage b, double threshold) {
        int changed = 0, count = 0;
        for (int y = 65; y < SIZE - 65; y++) for (int x = 65; x < SIZE - 65; x++) {
            if (delta(a.getPixelRGBA(x, y), b.getPixelRGBA(x, y)) > threshold) changed++;
            count++;
        }
        return (double)changed / count;
    }    private static double materialDifference(NativeImage a, View av, NativeImage b, View bv) {
        Matrix4f am = projection(av).mul(modelView(av)), bm = projection(bv).mul(modelView(bv));
        double sum = 0;
        // Centers of the real 16x16 mineral texels avoid resampling its facet boundaries.
        for (int v = 0; v < 16; v++) for (int u = 0; u < 16; u++) {
            float x = (u + .5F) / 8 - 1, y = 1 - (v + .5F) / 8;
            sum += delta(projectedPixel(a, am, x, y), projectedPixel(b, bm, x, y));
        }
        return sum / 256;
    }
    private static int projectedPixel(NativeImage image, Matrix4f transform, float x, float y) {
        Vector4f point = transform.transform(new Vector4f(x, y, 0, 1));
        int px = com.cappleapple.astralrepository.platform.Backport.clamp((int)((point.x / point.w * .5F + .5F) * SIZE), 0, SIZE - 1);
        int py = com.cappleapple.astralrepository.platform.Backport.clamp((int)((.5F - point.y / point.w * .5F) * SIZE), 0, SIZE - 1);
        return image.getPixelRGBA(px, py);
    }
    private static double meanBrightness(NativeImage image) {
        double sum = 0; int count = 0;
        for (int y = 65; y < SIZE - 65; y++) for (int x = 65; x < SIZE - 65; x++) { int pixel = image.getPixelRGBA(x, y); sum += ((pixel & 255) + ((pixel >>> 8) & 255) + ((pixel >>> 16) & 255)) / (3.0 * 255); count++; }
        return sum / count;
    }
    private static double delta(int a, int b) { return (Math.abs((a & 255) - (b & 255)) + Math.abs(((a >>> 8) & 255) - ((b >>> 8) & 255)) + Math.abs(((a >>> 16) & 255) - ((b >>> 16) & 255))) / (3.0 * 255); }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}