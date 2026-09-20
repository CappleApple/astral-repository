package com.cappleapple.astralrepository.content;

import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;

/** Portable JSON presets: base64 ARGB pixels, item IDs/transforms, and an SNBT filter snapshot. */
public final class RunePresetFiles {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    public static String encode(RunePreset p){JsonObject o=new JsonObject();o.addProperty("id",p.id().toString());o.addProperty("name",p.name());o.addProperty("mode",p.mode().name());o.addProperty("resolution",p.design().size());o.addProperty("pixelFormat","argb32");var bytes=java.nio.ByteBuffer.allocate(p.design().size()*p.design().size()*4);for(int color:p.design().argbPixels())bytes.putInt(color);o.addProperty("pixels",Base64.getEncoder().encodeToString(bytes.array()));o.addProperty("filter",p.filter().toString());o.addProperty("priority",p.priority());o.addProperty("enabled",p.enabled());o.addProperty("cadence",p.cadence().save().toString());JsonArray icons=new JsonArray();for(var icon:p.design().icons()){JsonObject i=new JsonObject();i.addProperty("item",icon.item().toString());i.addProperty("x",icon.x());i.addProperty("y",icon.y());i.addProperty("scale",icon.scale());i.addProperty("rotation",icon.rotation());icons.add(i);}o.add("icons",icons);return JSON.toJson(o);}
    public static RunePreset decode(String text,HolderLookup.Provider registries)throws Exception{if(text.length()>196608)throw new IllegalArgumentException("Preset too large");JsonObject o=JsonParser.parseString(text).getAsJsonObject();List<RuneDesign.Icon> icons=new ArrayList<>();for(var value:o.getAsJsonArray("icons")){var i=value.getAsJsonObject();icons.add(new RuneDesign.Icon(new ResourceLocation(i.get("item").getAsString()),i.get("x").getAsFloat(),i.get("y").getAsFloat(),i.get("scale").getAsFloat(),i.get("rotation").getAsFloat()));}FilterRules filter=new FilterRules();filter.load(TagParser.parseTag(o.get("filter").getAsString()),registries);return new RunePreset(UUID.fromString(o.get("id").getAsString()),o.get("name").getAsString(),RuneLayer.Mode.valueOf(o.get("mode").getAsString()),decodeDesign(o,icons),filter.save(registries),o.get("priority").getAsInt(),o.get("enabled").getAsBoolean(),o.has("cadence")?RuneCadence.load(TagParser.parseTag(o.get("cadence").getAsString())):RuneCadence.DEFAULT);}
    private static RuneDesign decodeDesign(JsonObject o,List<RuneDesign.Icon> icons){
        int n=o.get("resolution").getAsInt();byte[] bytes=Base64.getDecoder().decode(o.get("pixels").getAsString());
        if(!o.has("pixelFormat"))return new RuneDesign(n,bytes,icons);
        if(!o.get("pixelFormat").getAsString().equals("argb32")||n<1||n>RuneDesign.MAX_SIZE||bytes.length!=n*n*4)throw new IllegalArgumentException("Invalid pixel format");
        int[] pixels=new int[n*n];var buffer=java.nio.ByteBuffer.wrap(bytes);for(int i=0;i<pixels.length;i++)pixels[i]=buffer.getInt();return new RuneDesign(n,pixels,icons);
    }
    public static synchronized List<RunePreset> pack(HolderLookup.Provider registries){List<RunePreset> presets=new ArrayList<>();Path folder=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("astral_repository/rune_presets");try{boolean fresh=!Files.exists(folder);Files.createDirectories(folder);for(var mode:RuneLayer.Mode.values()){var base=RunePreset.initial(mode);var preset=new RunePreset(UUID.nameUUIDFromBytes(("astral_repository:"+mode).getBytes(StandardCharsets.UTF_8)),base.name(),mode,base.design(),base.filter(),0,true);Path file=folder.resolve(mode.name().toLowerCase(Locale.ROOT)+".json");if(fresh&&!Files.exists(file))Files.writeString(file,encode(preset));}try(var files=Files.list(folder)){for(Path file:files.filter(p->p.getFileName().toString().endsWith(".json")).sorted().limit(64).toList())try{if(Files.size(file)<=196608)presets.add(decode(Files.readString(file),registries));}catch(Exception invalid){com.cappleapple.astralrepository.AstralRepository.LOGGER.warn("Ignoring invalid rune preset {}",file,invalid);}}}catch(Exception failure){com.cappleapple.astralrepository.AstralRepository.LOGGER.error("Cannot read rune presets",failure);}return List.copyOf(presets);}
    private RunePresetFiles(){}
}
