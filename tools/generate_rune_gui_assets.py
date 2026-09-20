"""Bake rune editor field wells and nine-slice widgets; Python standard library only."""
from pathlib import Path
import json,struct,zlib
OUT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/astral_repository/textures/gui'
class Canvas:
    def __init__(self,width,height): self.width,self.height=width,height;self.pixels=bytearray(width*height*4)
    def rect(self,x,y,width,height,color):
        rgba=bytes.fromhex(color)+(b'\xff' if len(color)==6 else b'')
        for row in range(y,y+height):
            for column in range(x,x+width):
                i=(row*self.width+column)*4;self.pixels[i:i+4]=rgba
    def save(self,name):
        def chunk(kind,data): return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
        raw=b''.join(b'\0'+self.pixels[y*self.width*4:(y+1)*self.width*4] for y in range(self.height))
        path=OUT/name;path.parent.mkdir(parents=True,exist_ok=True)
        path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',self.width,self.height,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))
def well(c,x,y,width,height):
    c.rect(x,y,width,height,'080c24');c.rect(x+1,y+1,width-1,height-1,'344882');c.rect(x+1,y+1,width-2,height-2,'171e3b')
def track(c,x,y,width,height):
    c.rect(x,y,width,height,'080c24');c.rect(x+1,y+1,width-2,height-2,'11162f')
settings=Canvas(340,240)
well(settings,10,72,305,71)
for y in [90,107,124]:settings.rect(12,y,301,1,'29355b')
track(settings,320,72,10,71)
for x,width in [(10,104),(120,104),(230,100)]:well(settings,x,184,width,18)
settings.save('rune_settings_controls.png')
picker=Canvas(340,240);well(picker,10,35,320,18);track(picker,320,84,10,116);picker.save('rune_filter_controls.png')
for name,body,highlight in [('rune_button','272e56','4256a4'),('rune_button_hovered','34396d','6475d2'),('rune_button_disabled','191f37','29365b'),('rune_scroll_thumb','3a477d','7aa3eb'),('rune_field','10162e','344882'),('rune_field_focused','151d3a','8aa9ee')]:
    c=Canvas(16,16);c.rect(0,0,16,16,'080a20');c.rect(1,1,14,14,highlight);c.rect(2,2,13,13,'181833');c.rect(2,2,12,12,body);c.save('sprites/'+name+'.png')
    (OUT/'sprites'/f'{name}.png.mcmeta').write_text(json.dumps({'gui':{'scaling':{'type':'nine_slice','width':16,'height':16,'border':2}}},indent=2)+'\n',encoding='utf-8')
print('Wrote rune editor controls, filter controls and four nine-slice GUI sprites.')

c=Canvas(16,16)
c.rect(0,0,16,16,'34488288');c.rect(1,1,14,14,'10162e28');c.rect(1,1,14,1,'7aa3eb88')
c.save('sprites/rune_drop_area.png')
(OUT/'sprites/rune_drop_area.png.mcmeta').write_text(json.dumps({'gui':{'scaling':{'type':'nine_slice','width':16,'height':16,'border':2}}},indent=2)+'\n',encoding='utf-8')
