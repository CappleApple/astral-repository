package com.cappleapple.astralrepository.platform.client;

import com.google.gson.JsonObject;
import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.*;
import net.minecraft.client.resources.model.geometry.*;
import net.minecraft.client.resources.model.sprite.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.*;

/** Native Fabric loader for the repository's OBJ meshes, including UVs and MTL tint channels. */
public final class FabricObjModels {
    private record MaterialSpec(Identifier texture,int tint) {}
    private record Vertex(Vector3f position,Vector2f uv) {}
    private record Face(List<Vertex> vertices,MaterialSpec material) {}
    private record Mesh(List<Face> faces) {}
    private record ObjModel(Identifier mesh,TextureSlots.Data textureSlots,boolean flipV,boolean shade,Mesh data) implements UnbakedModel {
        ObjModel withMesh(Mesh value){return new ObjModel(mesh,textureSlots,flipV,shade,value);}
        @Override public UnbakedGeometry geometry(){return (textures,baker,state,name)->{
            if(data==null)throw new IllegalStateException("OBJ resource not loaded: "+mesh);
            var quads=new QuadCollection.Builder();
            for(var face:data.faces()){
                var material=baker.materials().get(new Material(face.material().texture()),name);
                var sprite=material.sprite();
                var vertices=face.vertices();
                // OBJ polygons are convex in the shipped meshes. Keep quads intact and fan
                // triangulate larger faces so exported beveled geometry retains its shape.
                if(vertices.size()==4)quad(quads,vertices,material,face.material().tint(),shade,state.transformation().getMatrix(),flipV);
                else for(int i=1;i+1<vertices.size();i++)quad(quads,List.of(vertices.getFirst(),vertices.get(i),vertices.get(i+1),vertices.get(i+1)),material,face.material().tint(),shade,state.transformation().getMatrix(),flipV);
            }
            return quads.build();
        };}
    }
    private static void quad(QuadCollection.Builder out,List<Vertex> vertices,Material.Baked material,int tint,boolean shade,Matrix4fc transform,boolean flipV){
        Vector3f[] positions=new Vector3f[4];long[] uv=new long[4];
        for(int i=0;i<4;i++){
            var vertex=vertices.get(i);positions[i]=new Vector3f(vertex.position()).sub(.5F,.5F,.5F);
            transform.transformPosition(positions[i]);positions[i].add(.5F,.5F,.5F);
            float u=vertex.uv().x(),v=flipV?1F-vertex.uv().y():vertex.uv().y();
            uv[i]=UVPair.pack(material.sprite().getU(u),material.sprite().getV(v));
        }
        var normal=new Vector3f(positions[1]).sub(positions[0]).cross(new Vector3f(positions[2]).sub(positions[0]));
        Direction direction=Direction.getApproximateNearest(normal.x(),normal.y(),normal.z());
        var info=BakedQuad.MaterialInfo.of(material,material.sprite().transparency(),tint,shade,0);
        out.addUnculledFace(new BakedQuad(positions[0],positions[1],positions[2],positions[3],uv[0],uv[1],uv[2],uv[3],direction,info));
    }
    public static void register(){
        UnbakedModelDeserializer.register(Identifier.fromNamespaceAndPath("astral_repository","obj"),(json,context)->new ObjModel(
                Identifier.parse(json.get("model").getAsString()),json.has("textures")?TextureSlots.parseTextureMap(json.getAsJsonObject("textures")):TextureSlots.Data.EMPTY,
                !json.has("flip_v")||json.get("flip_v").getAsBoolean(),!json.has("shade_quads")||json.get("shade_quads").getAsBoolean(),null));
        PreparableModelLoadingPlugin.register((shared,executor)->CompletableFuture.supplyAsync(()->load(shared.resourceManager()),executor),(meshes,context)->
                context.modifyModelOnLoad().register((model,ctx)->model instanceof ObjModel obj?obj.withMesh(Objects.requireNonNull(meshes.get(obj.mesh()),"Missing OBJ "+obj.mesh())):model));
    }
    private static Map<Identifier,Mesh> load(ResourceManager manager){
        Map<Identifier,Mesh> meshes=new HashMap<>();
        for(var entry:manager.listResources("models",id->id.getNamespace().equals("astral_repository")&&id.getPath().endsWith(".obj")).entrySet()){
            try(var reader=entry.getValue().openAsReader()){meshes.put(entry.getKey(),parse(manager,entry.getKey(),reader));}
            catch(IOException failure){throw new UncheckedIOException("Could not load OBJ "+entry.getKey(),failure);}
        }
        return Map.copyOf(meshes);
    }
    private static Mesh parse(ResourceManager manager,Identifier id,BufferedReader reader)throws IOException{
        List<Vector3f> positions=new ArrayList<>();List<Vector2f> uvs=new ArrayList<>();List<Face> faces=new ArrayList<>();
        Map<String,MaterialSpec> materials=new HashMap<>();String material="";
        for(String line;(line=reader.readLine())!=null;){
            line=line.strip();if(line.isEmpty()||line.startsWith("#"))continue;String[] words=line.split("\\s+");
            switch(words[0]){
                case "v"->positions.add(new Vector3f(Float.parseFloat(words[1]),Float.parseFloat(words[2]),Float.parseFloat(words[3])));
                case "vt"->uvs.add(new Vector2f(Float.parseFloat(words[1]),Float.parseFloat(words[2])));
                case "mtllib"->{
                    String directory=id.getPath().substring(0,id.getPath().lastIndexOf('/')+1);
                    var mtl=Identifier.fromNamespaceAndPath(id.getNamespace(),directory+words[1]);
                    try(var source=manager.getResourceOrThrow(mtl).openAsReader()){materials.putAll(materials(source));}
                }
                case "usemtl"->material=words[1];
                case "f"->{
                    List<Vertex> vertices=new ArrayList<>();
                    for(int i=1;i<words.length;i++){
                        String[] indices=words[i].split("/",-1);int pos=index(indices[0],positions.size());
                        var uv=indices.length>1&&!indices[1].isEmpty()?uvs.get(index(indices[1],uvs.size())):new Vector2f();
                        vertices.add(new Vertex(positions.get(pos),uv));
                    }
                    if(vertices.size()<3)throw new IOException("OBJ face has fewer than three vertices: "+id);
                    var spec=materials.get(material);if(spec==null)throw new IOException("Unknown OBJ material "+material+" in "+id);
                    faces.add(new Face(List.copyOf(vertices),spec));
                }
                default->{}
            }
        }
        return new Mesh(List.copyOf(faces));
    }
    private static int index(String text,int count){int value=Integer.parseInt(text);return value<0?count+value:value-1;}
    private static Map<String,MaterialSpec> materials(BufferedReader reader)throws IOException{
        Map<String,MaterialSpec> result=new HashMap<>();String name=null;Identifier texture=null;int tint=-1;
        for(String line;(line=reader.readLine())!=null;){String[] words=line.strip().split("\\s+");if(words.length<2)continue;
            if(words[0].equals("newmtl")){if(name!=null&&texture!=null)result.put(name,new MaterialSpec(texture,tint));name=words[1];texture=null;tint=-1;}
            else if(words[0].equals("map_Kd"))texture=Identifier.parse(words[1]);
            else if(words[0].equals("neoforge_TintIndex")||words[0].equals("forge_TintIndex"))tint=Integer.parseInt(words[1]);
        }
        if(name!=null&&texture!=null)result.put(name,new MaterialSpec(texture,tint));return result;
    }
    private FabricObjModels(){}
}
