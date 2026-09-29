#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成 AIBot 内置的高质量 .litematic 蓝图(12 个)。
参考社区最流行的建筑风格,用真实方块 + 楼梯/台阶做出层次,结构完整、可直接建造。

输出: src/main/resources/data/aibot/builtin_blueprints/<id>.litematic
"""
import os
from litemapy import Schematic, Region, BlockState

AUTHOR = "aibot-builtin"

# ---------- 常用方块 ----------
def B(name, **props):
    return BlockState(name, **props)

AIR = None
def oak_stairs(facing, half="bottom"):
    return B("minecraft:oak_stairs", facing=facing, half=half)
def spruce_stairs(facing, half="bottom"):
    return B("minecraft:spruce_stairs", facing=facing, half=half)
def cobble_stairs(facing):
    return B("minecraft:cobblestone_stairs", facing=facing)
def stonebrick_stairs(facing):
    return B("minecraft:stone_brick_stairs", facing=facing)
def deepslate_stairs(facing):
    return B("minecraft:deepslate_tile_stairs", facing=facing)
def oak_slab(top="bottom"):
    return B("minecraft:oak_slab", type=top)
def spruce_slab(top="bottom"):
    return B("minecraft:spruce_slab", type=top)
def stone_slab(top="bottom"):
    return B("minecraft:smooth_stone_slab", type=top)

# ---------- 辅助 ----------
def put(reg, x, y, z, state):
    """安全设置方块:越界自动忽略;state=None 表示清空为空气。"""
    if not (0 <= x < reg.width and 0 <= y < reg.height and 0 <= z < reg.length):
        return
    if state is None:
        state = B("minecraft:air")
    reg[x, y, z] = state

def box(reg, x0, y0, z0, x1, y1, z1, state):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                put(reg, x, y, z, state)

def shell(reg, x0, y0, z0, x1, y1, z1, state):
    """只填表面(墙),内部留空。"""
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1) or y in (y0, y1):
                    put(reg, x, y, z, state)

def gable_roof(reg, x0, x1, z0, z1, base_y, stair, ridge_block, axis="x"):
    """人字顶。axis='x' 表示屋脊沿 x 方向,斜坡沿 z。
    stair(facing) 返回朝对应方向的楼梯。"""
    if axis == "x":
        half = (z1 - z0 + 1)
        for layer in range((half + 1) // 2 + 1):
            za = z0 + layer
            zb = z1 - layer
            for x in range(x0, x1 + 1):
                if za == zb:
                    put(reg, x, base_y + layer, za, ridge_block)
                else:
                    put(reg, x, base_y + layer, za, stair("south"))
                    put(reg, x, base_y + layer, zb, stair("north"))
    else:
        half = (x1 - x0 + 1)
        for layer in range((half + 1) // 2 + 1):
            xa = x0 + layer
            xb = x1 - layer
            for z in range(z0, z1 + 1):
                if xa == xb:
                    put(reg, xa, base_y + layer, z, ridge_block)
                else:
                    put(reg, xa, base_y + layer, z, stair("east"))
                    put(reg, xb, base_y + layer, z, stair("west"))

def save(region, name, outdir):
    schem = region.as_schematic(name=name, author=AUTHOR, description=name)
    path = os.path.join(outdir, name + ".litematic")
    schem.save(path)
    print("saved", name)

# ============================================================
# 1. 中世纪小屋 medieval_cottage
# ============================================================
def medieval_cottage():
    w, l, h = 7, 7, 4
    r = Region(0, 0, 0, w, h + 4, l)
    # 石质地基
    box(r, 0, 0, 0, w-1, 0, l-1, B("minecraft:cobblestone"))
    # 木地板(留门洞)
    box(r, 1, 1, 1, w-2, 1, l-2, B("minecraft:oak_planks"))
    # 墙体:云杉木板,四角橡木立柱
    for y in range(2, 5):
        for x in range(0, w):
            for z in range(0, l):
                if x in (0, w-1) and z in (0, l-1):
                    put(r, x, y, z, B("minecraft:oak_log", axis="y"))
                elif x in (0, w-1) or z in (0, l-1):
                    put(r, x, y, z, B("minecraft:spruce_planks"))
    # 门洞(正面 z=0 中央)
    put(r, 3, 1, 0, AIR); put(r, 3, 2, 0, AIR)
    put(r, 3, 3, 0, B("minecraft:oak_slab", type="bottom"))
    # 窗户
    put(r, 1, 3, 0, B("minecraft:glass_pane"))
    put(r, 5, 3, 0, B("minecraft:glass_pane"))
    put(r, 0, 3, 3, B("minecraft:glass_pane"))
    put(r, w-1, 3, 3, B("minecraft:glass_pane"))
    # 门
    put(r, 3, 1, 0, B("minecraft:oak_door", facing="south", half="lower", hinge="left"))
    put(r, 3, 2, 0, B("minecraft:oak_door", facing="south", half="upper", hinge="left"))
    # 人字顶(橡木楼梯,屋脊沿 x)
    gable_roof(r, -1, w, -1, l, 4, oak_stairs, B("minecraft:oak_slab", type="bottom"), axis="x")
    # 床 + 工作台
    put(r, 5, 1, 5, B("minecraft:red_bed", facing="west", part="foot"))
    put(r, 4, 1, 5, B("minecraft:red_bed", facing="west", part="head"))
    put(r, 1, 1, 1, B("minecraft:crafting_table"))
    return r

# ============================================================
# 2. 小城堡 small_castle
# ============================================================
def small_castle():
    w, l = 13, 13
    total_h = 9
    r = Region(0, 0, 0, w, total_h, l)
    # 地基
    box(r, 0, 0, 0, w-1, 0, l-1, B("minecraft:stone_bricks"))
    # 外围城墙(高 4),内部空地
    for y in range(1, 5):
        for x in range(0, w):
            for z in range(0, l):
                edge = x in (0, w-1) or z in (0, l-1)
                if edge:
                    put(r, x, y, z, B("minecraft:stone_bricks"))
    # 城垛(垛口)
    for x in range(0, w):
        for z in range(0, l):
            edge = x in (0, w-1) or z in (0, l-1)
            if edge and (x + z) % 2 == 0:
                put(r, x, 5, z, B("minecraft:stone_brick_wall"))
    # 四角塔楼(高 8)
    for (cx, cz) in [(0,0),(w-1,0),(0,l-1),(w-1,l-1)]:
        for y in range(1, 8):
            for dx in (-1,0,1):
                for dz in (-1,0,1):
                    x, z = cx+dx, cz+dz
                    if 0 <= x < w and 0 <= z < l:
                        if (dx in (-1,1)) != (dz in (-1,1)) or (dx==0 and dz==0):
                            put(r, x, y, z, B("minecraft:stone_bricks"))
        # 塔顶垛口
        for dx in (-1,0,1):
            for dz in (-1,0,1):
                x, z = cx+dx, cz+dz
                if 0<=x<w and 0<=z<l and (dx in (-1,1) or dz in (-1,1)):
                    put(r, x, 8, z, B("minecraft:stone_brick_wall"))
    # 门楼(正面中央缺口 + 上方)
    put(r, 5, 1, 0, AIR); put(r, 6, 1, 0, AIR)
    put(r, 5, 2, 0, AIR); put(r, 6, 2, 0, AIR)
    put(r, 5, 3, 0, AIR); put(r, 6, 3, 0, AIR)
    put(r, 5, 4, 0, B("minecraft:stone_bricks"))
    put(r, 6, 4, 0, B("minecraft:stone_bricks"))
    # 中央水井
    put(r, 6, 1, 6, B("minecraft:water"))
    for dx in (-1,0,1):
        for dz in (-1,0,1):
            if not (dx==0 and dz==0):
                put(r, 6+dx,1,6+dz,B("minecraft:stone_brick_slab", type="bottom"))
    return r

# ============================================================
# 3. 瞭望塔 watchtower
# ============================================================
def watchtower():
    w, h = 5, 12
    r = Region(0,0,0,w,h,w)
    # 螺旋实心塔(石砖),内部留 1x1
    for y in range(0, 10):
        for x in range(w):
            for z in range(w):
                outer = x in (0,w-1) or z in (0,w-1)
                if outer:
                    put(r, x,y,z,B("minecraft:stone_bricks"))
                elif y == 0:
                    put(r, x,y,z,B("minecraft:cobblestone"))
    # 顶层平台(扩大)
    box(r, -1, 10, -1, w, 10, w, B("minecraft:stone_brick_slab", type="bottom"))
    # 垛口
    for x in range(-1, w+1):
        for z in range(-1, w+1):
            if x in (-1,w) or z in (-1,w):
                if (x+z) % 2 == 0:
                    put(r, x,11,z,B("minecraft:stone_brick_wall"))
    # 窗户(瞭望孔)
    put(r, 2, 7, 0, B("minecraft:glass_pane"))
    put(r, 2, 7, w-1, B("minecraft:glass_pane"))
    put(r, 0, 7, 2, B("minecraft:glass_pane"))
    put(r, w-1, 7, 2, B("minecraft:glass_pane"))
    # 梯子
    for y in range(1, 10):
        put(r, 0, y, 2, B("minecraft:ladder", facing="east"))
    return r

# ============================================================
# 4. 风车 windmill
# ============================================================
def windmill():
    w, h, l = 7, 11, 7
    r = Region(0,0,0,w,h,l)
    # 圆锥塔(逐层收窄)
    for y in range(0, 8):
        shrink = y // 3
        x0, x1 = shrink, w-1-shrink
        z0, z1 = shrink, l-1-shrink
        for x in range(x0, x1+1):
            for z in range(z0, z1+1):
                if x in (x0,x1) or z in (z0,z1) or y==0:
                    put(r, x,y,z,B("minecraft:spruce_planks"))
    # 顶部
    box(r, 2, 8, 2, 4, 8, 4, B("minecraft:spruce_planks"))
    # 门
    put(r, 3,1,0,AIR); put(r, 3,2,0,AIR)
    put(r, 3,1,0,B("minecraft:spruce_door",facing="south",half="lower",hinge="left"))
    put(r, 3,2,0,B("minecraft:spruce_door",facing="south",half="upper",hinge="left"))
    # 叶片(正面 z=0, 中心 3,7)十字 + 对角简化
    cy, cx = 7, 3
    for i in range(1, 5):
        put(r, cx, cy+i, 0, B("minecraft:oak_fence"))
        put(r, cx, cy-i, 0, B("minecraft:oak_fence"))
        put(r, cx+i, cy, 0, B("minecraft:oak_fence"))
        put(r, cx-i, cy, 0, B("minecraft:oak_fence"))
    put(r, cx, cy, 0, B("minecraft:spruce_planks"))
    return r

# ============================================================
# 5. 灯塔 lighthouse
# ============================================================
def lighthouse():
    w, h = 5, 12
    r = Region(0,0,0,w,h,w)
    # 红白条纹圆柱塔
    for y in range(0, 10):
        for x in range(w):
            for z in range(w):
                outer = x in (0,w-1) or z in (0,w-1)
                if outer:
                    mat = "minecraft:white_concrete" if (y//2)%2==0 else "minecraft:red_concrete"
                    put(r, x,y,z,B(mat))
                elif y==0:
                    put(r, x,y,z,B("minecraft:stone"))
    # 灯室
    box(r,0,10,0,w-1,10,w-1,B("minecraft:glass_pane"))
    put(r, 2,10,2,B("minecraft:lantern",hanging="false"))
    # 顶
    for x in range(w):
        for z in range(w):
            if x in (0,w-1) or z in (0,w-1):
                put(r, x,11,z,B("minecraft:red_concrete"))
    put(r, 2,11,2,B("minecraft:red_concrete"))
    # 门
    put(r, 2,1,0,AIR); put(r, 2,2,0,AIR)
    put(r, 2,1,0,B("minecraft:iron_door",facing="south",half="lower"))
    put(r, 2,2,0,B("minecraft:iron_door",facing="south",half="upper"))
    return r

# ============================================================
# 6. 日式小屋 japanese_house
# ============================================================
def japanese_house():
    w, l = 9, 8
    r = Region(0,0,0,w,9,l)
    # 抬高地板
    box(r,0,0,0,w-1,0,l-1,B("minecraft:stone"))
    box(r,1,1,1,w-2,1,l-2,B("minecraft:spruce_planks"))
    # 纸拉门/墙
    for y in range(2,5):
        for x in range(1,w-1):
            for z in range(1,l-1):
                if x in (1,w-2) or z in (1,l-2):
                    put(r, x,y,z,B("minecraft:white_wool"))
    # 立柱
    for (x,z) in [(1,1),(w-2,1),(1,l-2),(w-2,l-2)]:
        for y in range(1,5):
            put(r, x,y,z,B("minecraft:dark_oak_log",axis="y"))
    # 入口
    put(r, 4,1,1,AIR); put(r, 4,2,1,AIR); put(r, 4,3,1,AIR)
    # 歇山式大屋顶(深色橡木楼梯,出檐宽)
    gable_roof(r, -1, w, -1, l, 4,
               lambda f, half="bottom": B("minecraft:dark_oak_stairs",facing=f,half=half),
               B("minecraft:dark_oak_slab",type="bottom"), axis="x")
    # 鸟居(前方)
    put(r, 2,1,0,B("minecraft:red_concrete")); put(r, 6,1,0,B("minecraft:red_concrete"))
    put(r, 2,2,0,B("minecraft:red_concrete")); put(r, 6,2,0,B("minecraft:red_concrete"))
    box(r,1,3,0,7,3,0,B("minecraft:red_concrete"))
    put(r, 4,4,0,B("minecraft:red_concrete"))
    return r

# ============================================================
# 7. 石桥 stone_bridge
# ============================================================
def stone_bridge():
    length, h, w = 13, 6, 5
    r = Region(0,0,0,w,h,length)
    # 拱桥:台阶板 + 中间隆起
    mid = length//2
    for z in range(length):
        arc = mid - abs(z-mid)
        deck_y = max(1, arc//2 + 1)
        for x in range(w):
            put(r, x, deck_y, z, B("minecraft:stone_brick_slab",type="bottom"))
            put(r, x, deck_y-1, z, B("minecraft:stone_bricks"))
        # 护栏
        put(r, 0, deck_y+1, z, B("minecraft:stone_brick_wall"))
        put(r, w-1, deck_y+1, z, B("minecraft:stone_brick_wall"))
    return r

# ============================================================
# 8. 现代别墅 modern_villa
# ============================================================
def modern_villa():
    w, l, h = 11, 9, 6
    r = Region(0,0,0,w,h,l)
    # 一层
    box(r,0,0,0,w-1,0,l-1,B("minecraft:gray_concrete"))
    for y in range(1,4):
        for x in range(w):
            for z in range(l):
                if x in (0,w-1) or z in (0,l-1):
                    # 大面积玻璃 + 白色混凝土柱
                    if (x in (0,3,7,w-1) and z in (0,l-1)) or (z in (0,l-1) and x in (0,w-1)):
                        put(r, x,y,z,B("minecraft:white_concrete"))
                    else:
                        put(r, x,y,z,B("minecraft:glass_pane"))
    # 平顶
    box(r,0,4,0,w-1,4,l-1,B("minecraft:smooth_quartz"))
    # 二层(局部,偏左)
    for y in range(5,6):
        for x in range(0,7):
            for z in range(0,6):
                if x in (0,6) or z in (0,5):
                    put(r, x,y,z,B("minecraft:glass_pane"))
    box(r,0,6,0,6,6,5,B("minecraft:smooth_quartz"))
    # 入口
    put(r, 5,1,0,AIR); put(r, 5,2,0,AIR)
    put(r, 5,1,0,B("minecraft:glass_door",facing="south",half="lower"))
    put(r, 5,2,0,B("minecraft:glass_door",facing="south",half="upper"))
    # 泳池
    box(r,8,1,6,10,1,8,B("minecraft:water"))
    return r

# ============================================================
# 9. 树屋 treehouse
# ============================================================
def treehouse():
    w, l = 9, 9
    r = Region(0,0,0,w,12,l)
    cx, cz = 4, 4
    # 树干
    for y in range(0, 9):
        put(r, cx,y,cz,B("minecraft:oak_log",axis="y"))
    # 树冠
    for dx in range(-2,3):
        for dz in range(-2,3):
            for dy in range(8,11):
                if abs(dx)+abs(dz) <= 3 and (dy<10 or abs(dx)<=1 and abs(dz)<=1):
                    put(r, cx+dx,dy,cz+dz,B("minecraft:oak_leaves",persistent="true"))
    # 平台(高 6)
    box(r,2,6,2,6,6,6,B("minecraft:oak_slab",type="bottom"))
    # 围栏
    for x in range(2,7):
        for z in range(2,7):
            if x in (2,6) or z in (2,6):
                if not (x==cx and z==2):
                    put(r, x,7,z,B("minecraft:oak_fence"))
    # 梯子
    for y in range(0,6):
        put(r, cx,y,2,B("minecraft:ladder",facing="south"))
    # 小屋顶
    gable_roof(r,2,6,2,6,8,oak_stairs,B("minecraft:oak_slab",type="bottom"),axis="x")
    return r

# ============================================================
# 10. 喷泉 fountain
# ============================================================
def fountain():
    w = 7
    r = Region(0,0,0,w,4,w)
    # 圆形水池(用距离判断)
    c = 3
    for x in range(w):
        for z in range(w):
            d = abs(x-c)+abs(z-c)
            if d <= 3:
                put(r, x,0,z,B("minecraft:stone_bricks"))
                if d <= 2:
                    put(r, x,1,z,B("minecraft:water"))
            if d == 3:
                put(r, x,1,z,B("minecraft:stone_brick_wall"))
    # 中央水柱
    put(r, c,1,c,B("minecraft:stone_bricks"))
    put(r, c,2,c,B("minecraft:water"))
    put(r, c,3,c,B("minecraft:water"))
    return r

# ============================================================
# 11. 谷仓 barn
# ============================================================
def barn():
    w, l = 9, 10
    r = Region(0,0,0,w,9,l)
    # 地基
    box(r,0,0,0,w-1,0,l-1,B("minecraft:cobblestone"))
    # 木墙
    for y in range(1,5):
        for x in range(w):
            for z in range(l):
                if x in (0,w-1) or z in (0,l-1):
                    put(r, x,y,z,B("minecraft:spruce_planks"))
    # 大门洞
    put(r, 3,1,0,AIR); put(r, 4,1,0,AIR); put(r, 5,1,0,AIR)
    put(r, 3,2,0,AIR); put(r, 4,2,0,AIR); put(r, 5,2,0,AIR)
    put(r, 3,3,0,AIR); put(r, 4,3,0,AIR); put(r, 5,3,0,AIR)
    # 红色人字顶
    gable_roof(r,-1,w,-1,l,4,
               lambda f,half="bottom":B("minecraft:red_nether_brick_stairs",facing=f),
               B("minecraft:red_nether_brick_slab",type="bottom"),axis="x")
    # 干草堆
    box(r,2,1,7,3,2,8,B("minecraft:hay_block"))
    return r

# ============================================================
# 12. 市场摊位 market_stall
# ============================================================
def market_stall():
    w, l = 5, 5
    r = Region(0,0,0,w,5,l)
    # 柜台
    box(r,0,0,0,w-1,0,l-1,B("minecraft:oak_planks"))
    # 四柱
    for (x,z) in [(0,0),(w-1,0),(0,l-1),(w-1,l-1)]:
        for y in range(1,4):
            put(r, x,y,z,B("minecraft:oak_fence"))
    # 条纹顶棚
    for x in range(w):
        for z in range(l):
            mat = "minecraft:red_wool" if x%2==0 else "minecraft:white_wool"
            put(r, x,4,z,B(mat))
    # 商品箱
    put(r, 2,1,2,B("minecraft:barrel"))
    put(r, 1,1,2,B("minecraft:hay_block"))
    put(r, 3,1,2,B("minecraft:pumpkin"))
    return r

def main():
    outdir = os.path.join(os.path.dirname(__file__),
                          "..", "src", "main", "resources", "data",
                          "aibot", "builtin_blueprints")
    outdir = os.path.abspath(outdir)
    os.makedirs(outdir, exist_ok=True)
    builders = [
        ("medieval_cottage", medieval_cottage),
        ("small_castle", small_castle),
        ("watchtower", watchtower),
        ("windmill", windmill),
        ("lighthouse", lighthouse),
        ("japanese_house", japanese_house),
        ("stone_bridge", stone_bridge),
        ("modern_villa", modern_villa),
        ("treehouse", treehouse),
        ("fountain", fountain),
        ("barn", barn),
        ("market_stall", market_stall),
    ]
    for name, fn in builders:
        save(fn(), name, outdir)
    print("ALL DONE ->", outdir)

if __name__ == "__main__":
    main()
