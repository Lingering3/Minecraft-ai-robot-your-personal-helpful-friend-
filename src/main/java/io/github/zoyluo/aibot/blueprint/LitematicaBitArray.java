/*
 * Litematica BitArray —— 精简版
 *
 * 原项目: Litematica (https://github.com/sakura-ryoko/litematica)
 * 原作者: masa (masady) / Sakura-Ryoko
 * 许可证: GNU LGPL-3.0-only
 *
 * 本文件仅保留 litematic 格式 BlockStates 的跨 long 位解包/打包核心算法,
 * 删除了原类中用于网络与序列化的 Codec/StreamCodec,以及 commons-lang Validate 依赖。
 * 按 LGPL-3.0 要求,本衍生文件同样以 LGPL-3.0 授权,保留原始版权与来源声明。
 */
package io.github.zoyluo.aibot.blueprint;

/**
 * litematic 格式的位打包数组:每个条目占固定 bit 数,紧密排列在 long[] 中,
 * 一个条目允许跨两个 long 边界。
 */
public final class LitematicaBitArray {
    private final long[] longArray;
    private final int bitsPerEntry;
    private final long maxEntryValue;
    private final long arraySize;

    public LitematicaBitArray(int bitsPerEntryIn, long arraySizeIn, long[] longArrayIn) {
        if (bitsPerEntryIn < 1 || bitsPerEntryIn > 32) {
            throw new IllegalArgumentException("bitsPerEntry out of range: " + bitsPerEntryIn);
        }
        this.arraySize = arraySizeIn;
        this.bitsPerEntry = bitsPerEntryIn;
        this.maxEntryValue = (1L << bitsPerEntryIn) - 1L;
        this.longArray = longArrayIn;
    }

    public int getAt(long index) {
        long startOffset = index * (long) this.bitsPerEntry;
        int startArrIndex = (int) (startOffset >> 6);
        int endArrIndex = (int) (((index + 1L) * (long) this.bitsPerEntry - 1L) >> 6);
        int startBitOffset = (int) (startOffset & 0x3F);
        if (startArrIndex == endArrIndex) {
            return (int) (this.longArray[startArrIndex] >>> startBitOffset & this.maxEntryValue);
        } else {
            int endOffset = 64 - startBitOffset;
            return (int) ((this.longArray[startArrIndex] >>> startBitOffset
                    | this.longArray[endArrIndex] << endOffset) & this.maxEntryValue);
        }
    }

    public long size() {
        return this.arraySize;
    }
}
