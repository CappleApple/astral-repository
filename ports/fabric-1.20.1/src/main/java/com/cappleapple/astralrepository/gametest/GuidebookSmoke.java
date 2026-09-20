package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralRepository;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import vazkii.patchouli.api.PatchouliAPI;
import vazkii.patchouli.client.book.BookEntry;
import vazkii.patchouli.client.book.gui.GuiBookEntry;
import vazkii.patchouli.client.book.gui.GuiBookLanding;
import vazkii.patchouli.common.book.Book;
import vazkii.patchouli.common.book.BookRegistry;

/** Actual Patchouli client screens; excluded from the release JAR with the other smoke fixtures. */
public final class GuidebookSmoke {
    private static final ResourceLocation BOOK = id("field_guide");
    private static final List<String> ENTRIES = java.util.stream.Stream.concat(
            java.util.stream.Stream.of("first_nexus", "storage", "runes", "filters", "crafting", "gems", "remote"),
            com.cappleapple.astralrepository.content.AstralContent.activeNodes().stream()
                    .filter(node -> com.cappleapple.astralrepository.content.PowerNodeVisibility.visible(node.get().asItem()))
                    .map(node -> "crystal_" + node.getId().getPath())).toList();
    private static final Path OUTPUT = Path.of("../build/client-smoke");
    private record Spread(BookEntry entry, int page) {}
    private static final List<Spread> spreads = new ArrayList<>();
    private static final Set<ResourceLocation> images = new LinkedHashSet<>();
    private static Book book;
    private static int ticks, index = -1;
    private static boolean done;

    /** Call once per client tick after the world's book contents have loaded. */
    public static boolean tick() throws Exception {
        check(Boolean.getBoolean("astral_repository.clientSmoke"), "Guidebook smoke is restricted to the development client gate");
        RenderSystem.assertOnRenderThread();
        Minecraft client = Minecraft.getInstance();
        check(client.level != null && client.player != null, "Guidebook smoke needs a loaded client world");
        check(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0, "Guidebook smoke client must stay muted");
        client.mouseHandler.releaseMouse();
        if (done) return true;
        if (book == null) {
            initialize(client);
            // Start this fixture on the landing screen even if a previous manual opening was remembered.
            book.getContents().guiStack.clear();
            book.getContents().currentGui = new GuiBookLanding(book);
            PatchouliAPI.get().openBookGUI(BOOK);
            ticks = 0;
            return false;
        }
        if (++ticks < 12) return false;
        check(!book.getContents().isErrored(), "Patchouli failed while rendering the guide: " + book.getContents().getException());
        check(BOOK.equals(PatchouliAPI.get().getOpenBookGui()), "A different book replaced the guide during rendering");
        if (index < 0) {
            check(client.screen instanceof GuiBookLanding landing && landing.book.id.equals(BOOK), "Patchouli did not render the field guide landing page");
            screenshot(client, "guide_landing.png");
        } else {
            Spread expected = spreads.get(index);
            check(!expected.entry().isLocked(), "Field guide entry is locked: " + expected.entry().getId());
            check(client.screen instanceof GuiBookEntry, "Patchouli did not open the requested entry screen");
            GuiBookEntry screen = (GuiBookEntry)client.screen;
            check(screen.getEntry().getId().equals(expected.entry().getId()), "Patchouli rendered the wrong entry");
            check(screen.getSpread() == expected.page() / 2, "Patchouli rendered the wrong spread for " + expected.entry().getId());
            for (int page = expected.page(); page < Math.min(expected.page() + 2, expected.entry().getPages().size()); page++) {
                var visible = expected.entry().getPages().get(page);
                check(visible.isPageUnlocked() && visible.parent == screen, "Guide page was not displayed by its actual Patchouli screen: " + expected.entry().getId() + "/" + page);
            }
            screenshot(client, "guide_" + expected.entry().getId().getPath() + "_" + screen.getSpread() + ".png");
        }
        index++;
        if (index == spreads.size()) {
            done = true;
            AstralRepository.LOGGER.info("Guidebook client smoke passed: {} entries, two categories, {} rendered spreads, and {} valid screenshot resources", ENTRIES.size(), spreads.size(), images.size());
            return true;
        }
        Spread next = spreads.get(index);
        // The public API accepts an absolute page; Patchouli converts it to a two-page spread.
        PatchouliAPI.get().openBookEntry(BOOK, next.entry().getId(), next.page());
        ticks = 0;
        return false;
    }

    private static void initialize(Minecraft client) throws Exception {
        check(!PatchouliAPI.get().isStub(), "Patchouli93 implementation is unavailable");
        book = BookRegistry.INSTANCE.books.get(BOOK);
        check(book != null, "Astral Field Guide was not registered by Patchouli");
        var contents = book.getContents();
        check(contents != null && !contents.isErrored(), "Astral Field Guide content failed to load: " + (contents == null ? "no contents" : contents.getException()));
        check(contents.entries.size() == ENTRIES.size(), "Field guide must cover every registered crystal and workshop entry; found " + contents.entries.keySet());
        check(contents.categories.size() == 2 && contents.categories.containsKey(id("workshop")) && contents.categories.containsKey(id("crystals")), "Field guide must contain Workshop and Network Crystals categories");
        check(PatchouliAPI.get().getSubtitle(BOOK) != null, "Registered field guide subtitle lookup failed");
        // Patchouli93's API stack factory ignores custom_book_item; the loaded book owns that lookup.
        var stack = book.getBookItem();
        check(!stack.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(BOOK), "Patchouli must resolve the mod-owned astral_repository:field_guide item");
        check(book.noBook, "Patchouli must not generate a second guide_book stack");
        var model = client.getItemRenderer().getModel(stack, client.level, client.player, 0);
        check(model != client.getModelManager().getMissingModel(), "Field guide item uses the missing model");
        for (String name : ENTRIES) {
            BookEntry entry = contents.entries.get(id(name));
            check(entry != null && !entry.isLocked() && !entry.getPages().isEmpty(), "Field guide entry is missing, locked or empty: " + name);
            for (int page = 0; page < entry.getPages().size(); page += 2) spreads.add(new Spread(entry, page));
            validateImages(client, name);
        }
        check(!images.isEmpty(), "The illustrated guide does not reference any screenshots");
        Files.createDirectories(OUTPUT);
    }

    private static void validateImages(Minecraft client, String entry) throws Exception {
        ResourceLocation source = id("patchouli_books/field_guide/en_us/entries/" + entry + ".json");
        try (var stream = client.getResourceManager().open(source); var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var pages = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("pages");
            for (var value : pages) {
                var page = value.getAsJsonObject();
                if (!page.has("type") || !page.get("type").getAsString().equals("patchouli:image")) continue;
                check(page.has("images") && page.getAsJsonArray("images").size() > 0, "Image page has no screenshot resource: " + source);
                for (var image : page.getAsJsonArray("images")) {
                    ResourceLocation texture = new ResourceLocation(image.getAsString());
                    check(client.getResourceManager().getResource(texture).isPresent(), "Guide screenshot resource is missing: " + texture);
                    if (images.add(texture)) try (var input = client.getResourceManager().open(texture); NativeImage decoded = NativeImage.read(input)) {
                        check(decoded.getWidth() > 1 && decoded.getHeight() > 1, "Guide screenshot resource is empty: " + texture);
                    }
                }
            }
        }
    }
    private static void screenshot(Minecraft client, String name) throws Exception {
        Path path = OUTPUT.resolve(name);
        try (NativeImage frame = Screenshot.takeScreenshot(client.getMainRenderTarget())) { frame.writeToFile(path); }
        check(Files.size(path) > 0, "Guide screenshot was not saved: " + name);
    }
    private static ResourceLocation id(String path) { return new ResourceLocation(AstralRepository.MOD_ID, path); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private GuidebookSmoke() {}
}
