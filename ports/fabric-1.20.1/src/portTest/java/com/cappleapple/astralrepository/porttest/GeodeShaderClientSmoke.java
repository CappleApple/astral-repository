package com.cappleapple.astralrepository.porttest;

import com.cappleapple.astralrepository.client.AstralPlaneRenderType;
import com.cappleapple.astralrepository.client.CrystalModelRenderer;
import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.content.CrystalNodeBlock;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** Excluded fixture: world-frame visibility and depth with the reported Iris shader pack. */
public final class GeodeShaderClientSmoke implements ClientModInitializer {
    private static final Path OUT = Path.of("geode-shader-captures");
    private static final String PACK = "ComplementaryUnbound_r5.9.3.zip";
    private static final BlockPos TARGET = new BlockPos(0, -58, 0);
    private static final StringBuilder REPORT = new StringBuilder();
    private static List<Sample> samples;
    private static int phase, ticks, sampleIndex, mode;
    private static boolean done, irisExpected;
    private static long startedAt;
    private static CompletableFuture<Void> pending;
    private static NativeImage emptyFirst, emptySecond, occluded;
    private static Object irisApi, irisConfig;
    private static Method packInUse, setEnabled;

    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(GeodeShaderClientSmoke::tick);
    }

    private static void tick(Minecraft mc) {
        if (done || !Boolean.getBoolean("astral_repository.geodeShaderSmoke")) return;
        mc.options.pauseOnLostFocus = false;
        mc.mouseHandler.releaseMouse();
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0D);
        try {
            if (startedAt == 0) startedAt = System.nanoTime();
            check((System.nanoTime() - startedAt) / 1_000_000_000L < 300,
                    "Geode shader fixture timed out: phase=" + phase + " mode=" + mode + " sample=" + sampleIndex);
            ++ticks;
            if (pending != null) {
                if (!pending.isDone()) return;
                pending.join(); pending = null; ticks = 0;
            }
            switch (phase) {
                case 0 -> {
                    if (mc.screen == null || mc.getOverlay() != null) return;
                    Files.createDirectories(OUT);
                    irisExpected = Boolean.getBoolean("astral_repository.geodeShaderIris");
                    initializeIris();
                    samples = List.of(
                            new Sample("astral_geode", AstralContent.ASTRAL_GEODE.get().defaultBlockState(), 2500, true),
                            new Sample("budding_astral", AstralContent.BUDDING_ASTRAL.get().defaultBlockState(), 2500, true),
                            new Sample("small_astral_bud", bud(AstralContent.SMALL_ASTRAL_BUD.get().defaultBlockState(), Direction.UP), 90, false),
                            new Sample("medium_astral_bud", bud(AstralContent.MEDIUM_ASTRAL_BUD.get().defaultBlockState(), Direction.UP), 150, false),
                            new Sample("large_astral_bud", bud(AstralContent.LARGE_ASTRAL_BUD.get().defaultBlockState(), Direction.UP), 220, false),
                            new Sample("astral_cluster", bud(AstralContent.ASTRAL_CLUSTER.get().defaultBlockState(), Direction.UP), 300, false),
                            new Sample("astral_cluster_south", bud(AstralContent.ASTRAL_CLUSTER.get().defaultBlockState(), Direction.SOUTH), 300, false),
                            new Sample("seed_storage_crystal", AstralContent.SEED_STORAGE_CRYSTAL.get().defaultBlockState()
                                    .setValue(CrystalNodeBlock.FACING, Direction.DOWN), 300, false));
                    mc.getWindow().setWindowed(1280, 720);
                    mc.options.guiScale().set(2); mc.options.renderDistance().set(3); mc.options.fov().set(60);
                    mc.options.bobView().set(false); mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON);
                    mc.resizeDisplay(); mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                    next(1);
                    mc.createWorldOpenFlows().createFreshLevel("astral-geode-shader-" + System.currentTimeMillis(),
                            new LevelSettings("Geode shader regression", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                                    new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(42, false, false),
                            registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                    .value().createWorldDimensions());
                }
                case 1 -> {
                    if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
                    mc.setScreen(null); server(GeodeShaderClientSmoke::prepare); next(2);
                }
                case 2 -> {
                    if (ticks < 60 || !mc.level.getBlockState(TARGET.below()).is(Blocks.SMOOTH_STONE)) return;
                    assertMode(); checkQuiet(mc); assertNodeModel(mc);
                    sampleIndex = 0; server(GeodeShaderClientSmoke::emptySample); next(3);
                }
                case 3 -> {
                    if (ticks < 30 || !mc.level.getBlockState(TARGET).isAir()) return;
                    emptyFirst = screenshot(label() + "-empty-first.png"); next(4);
                }
                case 4 -> {
                    if (ticks < 8) return;
                    emptySecond = screenshot(label() + "-empty.png");
                    server(p -> setTarget(p, samples.get(sampleIndex).state())); next(5);
                }
                case 5 -> {
                    if (ticks < 35 || !mc.level.getBlockState(TARGET).equals(samples.get(sampleIndex).state())) return;
                    assertMode();
                    var sample = samples.get(sampleIndex);
                    try (var visible = screenshot(label() + "-visible.png")) {
                        var measurement = difference(emptyFirst, emptySecond, visible, sample.cube());
                        REPORT.append(modeName()).append(' ').append(sample.name()).append(" changed=").append(measurement.changed())
                                .append(" stable=").append(measurement.stable()).append(" threshold=").append(sample.minimum()).append('\n');
                        Files.writeString(OUT.resolve("measurements.txt"), REPORT);
                        check(measurement.changed() >= sample.minimum(), "Invisible world geometry: " + label() + "; " + measurement);
                        if (sample.cube()) check(measurement.changed() * 2 > measurement.stable(), "Cube has a transparent center: " + label());
                    }
                    closeEmpty();
                    if (++sampleIndex < samples.size()) {
                        server(GeodeShaderClientSmoke::emptySample); next(3);
                    } else {
                        server(p -> { setTarget(p, AstralContent.ASTRAL_GEODE.get().defaultBlockState()); wall(p, true); }); next(6);
                    }
                }
                case 6 -> {
                    if (ticks < 35 || !mc.level.getBlockState(TARGET.south(2)).is(Blocks.SMOOTH_STONE)) return;
                    occluded = screenshot(modeName() + "-opaque-wall-geode.png");
                    server(p -> setTarget(p, Blocks.AIR.defaultBlockState())); next(7);
                }
                case 7 -> {
                    if (ticks < 35 || !mc.level.getBlockState(TARGET).isAir()) return;
                    try (var absent = screenshot(modeName() + "-opaque-wall-empty.png")) {
                        var measurement = difference(occluded, occluded, absent, true);
                        REPORT.append(modeName()).append(" opaque-wall changed=").append(measurement.changed()).append('\n');
                        Files.writeString(OUT.resolve("measurements.txt"), REPORT);
                        check(measurement.changed() < 150, "Geode draws through an opaque foreground wall: " + measurement);
                    }
                    occluded.close(); occluded = null;
                    server(p -> wall(p, false));
                    if (irisExpected && mode < 2) {
                        ++mode; setEnabled.invoke(irisConfig, mode != 1); next(2);
                    } else {
                        checkQuiet(mc); done = true;
                        Files.writeString(OUT.resolve("result.txt"), "PASS: " + samples.size() + " world mineral/node cases in "
                                + (irisExpected ? "Iris 1.7.6 + Complementary Unbound r5.9.3, shaders disabled, and shaders re-enabled" : "vanilla without Iris")
                                + "; stable-background pixel comparisons, opaque occlusion, hidden window, muted audio, and free mouse.\n");
                        LogUtils.getLogger().info("GEODE_SHADER_SMOKE_OK"); mc.stop();
                    }
                }
                default -> throw new AssertionError("Unexpected fixture phase " + phase);
            }
        } catch (Throwable failure) {
            done = true; closeEmpty(); if (occluded != null) { occluded.close(); occluded = null; }
            LogUtils.getLogger().error("GEODE_SHADER_SMOKE_FAILED", failure);
            try { Files.createDirectories(OUT); Files.writeString(OUT.resolve("result.txt"), "FAIL: " + failure + "\n"); }
            catch (Exception writeFailure) { failure.addSuppressed(writeFailure); }
            mc.stop();
        }
    }

    private static void initializeIris() throws Exception {
        var loader = FabricLoader.getInstance();
        check(loader.isModLoaded("iris") == irisExpected, "Unexpected Iris presence");
        if (!irisExpected) { REPORT.append("No Iris installed.\n"); return; }
        String version = loader.getModContainer("iris").orElseThrow().getMetadata().getVersion().getFriendlyString();
        check(version.equals("1.7.6+mc1.20.1"), "Unexpected Iris version " + version);
        check(loader.isModLoaded("indium"), "Sodium 0.5.x requires Indium for the node OBJ renderer");
        var api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
        irisApi = api.getMethod("getInstance").invoke(null);
        packInUse = api.getMethod("isShaderPackInUse");
        irisConfig = api.getMethod("getConfig").invoke(irisApi);
        setEnabled = Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig").getMethod("setShadersEnabledAndApply", boolean.class);
        REPORT.append("Iris ").append(version).append("; pack=").append(PACK).append('\n');
    }

    private static void assertMode() throws Exception {
        boolean active = irisExpected && mode != 1;
        if (irisExpected) {
            check((boolean) packInUse.invoke(irisApi) == active, "Iris pack state differs from requested mode " + modeName());
            if (active) {
                String actual = (String) Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPackName").invoke(null);
                check(PACK.equals(actual), "Unexpected active shader pack: " + actual);
            }
        }
        if (!Boolean.getBoolean("astral_repository.geodeVisibilityOnly"))
            check(AstralPlaneRenderType.ready() == !active, "Astral shader readiness did not follow Iris mode " + modeName());
    }

    private static void assertNodeModel(Minecraft mc) {
        var id = CrystalModelRenderer.modelLocation(AstralContent.SEED_STORAGE_CRYSTAL.get());
        var model = mc.getModelManager().getModel(id);
        check(model != mc.getModelManager().getMissingModel(), "Node standalone OBJ model did not load: " + id);
        int quads = model.getQuads(null, null, RandomSource.create(42)).size();
        for (var direction : Direction.values()) quads += model.getQuads(null, direction, RandomSource.create(42)).size();
        check(quads > 0, "Node standalone OBJ model is empty: " + id);
        REPORT.append(modeName()).append(" standalone-node-quads=").append(quads).append('\n');
    }

    private static void prepare(ServerPlayer player) {
        var level = player.serverLevel(); var server = level.getServer();
        level.setDayTime(6000); level.setWeatherParameters(12000, 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
        for (int x = -5; x <= 5; ++x) for (int z = -4; z <= 7; ++z) {
            level.setBlockAndUpdate(new BlockPos(x, -61, z), Blocks.SMOOTH_STONE.defaultBlockState());
            for (int y = -60; y <= -53; ++y) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        }
        for (int x = -4; x <= 4; ++x) for (int y = -60; y <= -54; ++y)
            level.setBlockAndUpdate(new BlockPos(x, y, -2), Blocks.SMOOTH_STONE.defaultBlockState());
        level.setBlockAndUpdate(TARGET.below(), Blocks.SMOOTH_STONE.defaultBlockState());
        player.closeContainer(); player.getAbilities().flying = true; player.onUpdateAbilities();
        double feet = TARGET.getY() + .5 - player.getEyeHeight();
        player.teleportTo(level, .5, feet, 4.5, 180, 0); player.setDeltaMovement(Vec3.ZERO);
    }

    private static void setTarget(ServerPlayer player, BlockState state) {
        player.serverLevel().setBlockAndUpdate(TARGET, state);
    }

    private static void emptySample(ServerPlayer player) {
        setTarget(player, Blocks.AIR.defaultBlockState());
        // A side-attached cluster needs a support; cube baselines leave open space
        // between the target and backdrop so neighbor-face culling cannot fake visibility.
        player.serverLevel().setBlockAndUpdate(TARGET.north(), sampleIndex == 6
                ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        // Crossed cluster quads viewed along their growth axis are edge-on. Use the
        // same oblique pose for both empty and visible frames of the side cluster.
        double x = sampleIndex == 6 ? 1.5 : .5;
        double feet = TARGET.getY() + .5 - player.getEyeHeight();
        float yaw = 180 - (float) Math.toDegrees(Math.atan2(x - .5, 4));
        player.teleportTo(player.serverLevel(), x, feet, 4.5, yaw, 0);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void wall(ServerPlayer player, boolean enabled) {
        for (int x = -2; x <= 2; ++x) for (int y = -60; y <= -55; ++y)
            player.serverLevel().setBlockAndUpdate(new BlockPos(x, y, 2), enabled ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
    }

    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance(); var id = mc.player.getUUID();
        pending = mc.getSingleplayerServer().submit(() -> action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id)));
    }

    private static NativeImage screenshot(String name) throws Exception {
        var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget());
        image.writeToFile(OUT.resolve(name)); return image;
    }

    private static Measurement difference(NativeImage first, NativeImage background, NativeImage visible, boolean cube) {
        check(first.getWidth() == visible.getWidth() && background.getHeight() == visible.getHeight(), "Framebuffer dimensions changed");
        int width = visible.getWidth(), height = visible.getHeight(), changed = 0, stable = 0;
        int halfWidth = cube ? 45 : 145, halfHeight = cube ? 45 : 125;
        for (int y = height / 2 - halfHeight; y < height / 2 + halfHeight; ++y)
            for (int x = width / 2 - halfWidth; x < width / 2 + halfWidth; ++x) {
                int baseline = background.getPixelRGBA(x, y);
                if (distance(first.getPixelRGBA(x, y), baseline) >= 20) continue;
                ++stable;
                if (distance(visible.getPixelRGBA(x, y), baseline) >= 45) ++changed;
            }
        return new Measurement(changed, stable);
    }

    private static int distance(int a, int b) {
        return Math.abs((a & 255) - (b & 255)) + Math.abs((a >> 8 & 255) - (b >> 8 & 255))
                + Math.abs((a >> 16 & 255) - (b >> 16 & 255));
    }

    private static void checkQuiet(Minecraft mc) {
        check(Boolean.getBoolean("astral_repository.testClient"), "Background client property missing");
        check(!mc.mouseHandler.isMouseGrabbed(), "Fixture captured the mouse");
        check(mc.options.getSoundSourceVolume(SoundSource.MASTER) == 0, "Fixture is not muted");
        check(GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE, "Fixture window is visible");
    }

    private static BlockState bud(BlockState state, Direction facing) { return state.setValue(AmethystClusterBlock.FACING, facing); }
    private static void next(int value) { phase = value; ticks = 0; }
    private static String modeName() { return irisExpected ? switch (mode) { case 0 -> "iris-on"; case 1 -> "iris-off"; default -> "iris-restored"; } : "vanilla"; }
    private static String label() { return modeName() + "-" + samples.get(sampleIndex).name(); }
    private static void closeEmpty() { if (emptyFirst != null) emptyFirst.close(); if (emptySecond != null) emptySecond.close(); emptyFirst = emptySecond = null; }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private record Sample(String name, BlockState state, int minimum, boolean cube) {}
    private record Measurement(int changed, int stable) {}
}
