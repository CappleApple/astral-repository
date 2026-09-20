"""Bake the fixed pixel layout and small GUI glyphs to resource-pack PNG textures.
No game-state rendering or fonts are baked into these assets. Python standard library only.
"""
from pathlib import Path
import struct,zlib
OUT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/astral_repository/textures/gui'
class Canvas:
    def __init__(self,w,h): self.w,self.h=w,h; self.data=bytearray(w*h*4)
    def rect(self,x,y,w,h,c):
        if isinstance(c,str): c=tuple(bytes.fromhex(c))+((255,) if len(c)==6 else ())
        for yy in range(max(0,y),min(self.h,y+h)):
            for xx in range(max(0,x),min(self.w,x+w)): self.data[(yy*self.w+xx)*4:(yy*self.w+xx+1)*4]=bytes(c)
    def save(self,name):
        def chunk(kind,data): return struct.pack('!I',len(data))+kind+data+struct.pack('!I',zlib.crc32(kind+data)&0xffffffff)
        raw=b''.join(b'\0'+self.data[y*self.w*4:(y+1)*self.w*4] for y in range(self.h))
        OUT.mkdir(parents=True,exist_ok=True)
        (OUT/name).write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!IIBBBBB',self.w,self.h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))
def well(c,x,y,w,h):
    c.rect(x,y,w,h,'080c24');c.rect(x+1,y+1,w-1,h-1,'344882');c.rect(x+1,y+1,w-2,h-2,'11162f');c.rect(x+2,y+2,w-3,h-3,'171e3b')
def plate(c,x,y,w,h,active=True,hover=False):
    c.rect(x,y,w,h,'080a20');c.rect(x+1,y+1,w-2,h-2,'6475d2' if hover else '4256a4');c.rect(x+2,y+2,w-3,h-3,'181833');c.rect(x+2,y+2,w-4,h-4,('34396d' if hover else '272e56') if active else '191f37')
def panel(w,h):
    c=Canvas(w,h);c.rect(1,0,w-2,h,'07091d');c.rect(0,1,w,h-2,'07091d');c.rect(1,1,w-2,h-2,'29396b');c.rect(2,2,w-4,h-4,'4059a5');c.rect(3,3,w-6,h-6,'14182f');c.rect(3,h-5,w-6,2,'0b1027');c.rect(w-5,3,2,h-6,'0b1027')
    # Cut blue and violet inlays follow the gem palette; the field animates behind this panel.
    c.rect(5,5,w-10,1,'5366b4');c.rect(5,20,w-10,1,'21294e');c.rect(5,21,w-10,1,'343f78')
    c.rect(8,5,42,1,'6592dc');c.rect(w-50,5,42,1,'8963c9')
    return c
panel(318,266).save('nexus.png')
c=Canvas(318,266)
well(c,10,27,174,18)
for row in range(6):
    for col in range(9): well(c,10+20*col,49+18*row,18,18)
for x,y in [(284,72)]+[(208+x*18,54+y*18) for y in range(3) for x in range(3)]+[(8+x*18,180+y*18) for y in range(3) for x in range(9)]+[(8+x*18,238) for x in range(9)]: well(c,x-1,y-1,18,18)
well(c,207,133,18,18);well(c,231,133,44,20)
c.rect(7,162,297,1,'21294e');c.rect(7,163,297,1,'343f78')
c.save('nexus_controls.png')
# Reusable regions drawn only for visible jobs and overflowing scrollable lists.
# Atlas: job row (0,0,97,22), storage track (100,0,7,108), jobs track (110,0,6,70).
d=Canvas(128,128)
d.rect(0,0,97,22,'0a1028');d.rect(1,1,95,20,'1c2447');well(d,2,2,18,18)
well(d,100,0,7,108);well(d,110,0,6,70)
d.save('nexus_dynamic.png')
c=panel(340,240);c.save('rune_settings.png')
a=Canvas(128,128)
# Shared atlas: hover, selection, scrollbar, buttons, and pixel glyphs.
a.rect(0,0,16,16,(140,176,255,48))
a.rect(18,0,18,18,(85,80,176,96));a.rect(19,1,16,16,(122,199,242,96));a.rect(20,2,14,14,(0,0,0,0))
plate(a,38,0,7,16);plate(a,47,0,7,16,False)
for n,(active,hover) in enumerate([(True,False),(True,True),(False,False)]): plate(a,n*24,20,24,20,active,hover)
# Smaller square icon buttons.
for n,(active,hover) in enumerate([(True,False),(True,True),(False,False)]): plate(a,76+n*16,20,16,16,active,hover)
# Crafting/hammer, cancel, warning, complete, waiting, running, external and search.
glyphs=[
 ['....#####...','...#######..','...#######..','.....###....','....###.....','...###......','..###.......','.###........','###.........'],
 ['##......##','.##....##.','..##..##..','...####...','....##....','...####...','..##..##..','.##....##.','##......##'],
 ['....##....','...####...','...####...','..##..##..','..##..##..','.###..###.','.########.','####..####','##########'],
 ['.........##','........##.','.......##..','##....##...','.##..##....','..####.....','...##......'],
 ['.########.','..#....#..','...#..#...','....##....','....##....','...#..#...','..######..','.########.'],
 ['.##........','.####......','.######....','.########..','.######....','.####......','.##........'],
 ['..####....','.#....#...','#..##..#..','#.#..#.#..','#.#..#.#..','#..##..#..','.#....#...','..####....'],
 ['..####.....','.##..##....','##....##...','##....##...','.##..##....','..####.....','......##...','.......##..','........##.'],
]
colors=['9dbcf5','f18caf','f0be79','7bd1c6','aba2e5','77b9f1','bc9ff3','a1c5f4']
for n,rows in enumerate(glyphs):
    for y,row in enumerate(rows):
        for x,ch in enumerate(row):
            if ch=='#': a.rect(n*16+x,48+y,1,1,colors[n])
# Progress rail and three colored bar variants. Blit width clips to progress.
a.rect(0,64,70,4,'090f29');a.rect(1,65,68,2,'242d51')
for yy,color in [(70,'629af1'),(76,'65c3b9'),(82,'d877a5')]:
    a.rect(0,yy,70,4,'111630');a.rect(0,yy+1,70,2,color);a.rect(0,yy,70,1,'8ab1ef')
# Small craftable star, intentionally an icon rather than a unicode character.
for x,y,w,h in [(4,0,1,7),(1,3,7,1),(3,2,3,3)]: a.rect(112+x,72+y,w,h,'baadff')
# Return ingredients to storage: arrow into a tray.
for x,y,w,h in [(5,96,2,7),(3,100,6,2),(4,102,4,1),(1,103,2,5),(9,103,2,5),(1,106,10,2)]: a.rect(x,y,w,h,'9dbcf5')
a.save('nexus_widgets.png')
# Bundled Not Siloed's native side rail: its buttons are drawn separately by BNS.
t=Canvas(21,64)
t.rect(2,0,19,64,'07091d');t.rect(0,2,21,60,'07091d')
t.rect(2,1,19,62,'14182f');t.rect(1,2,20,60,'14182f')
t.rect(2,1,17,1,'4059a5');t.rect(1,2,1,60,'4059a5')
t.rect(2,62,17,1,'0b1027');t.rect(2,2,1,60,'29396b')
t.rect(3,2,8,1,'5366b4');t.rect(3,61,8,1,'594285')
t.save('bundled_tab.png')
print('Wrote Nexus panels, dynamic regions, rune-editor, widget, and Bundled tab textures.')
