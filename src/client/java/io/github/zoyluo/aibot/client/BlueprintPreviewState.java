package io.github.zoyluo.aibot.client;

import net.minecraft.util.math.BlockPos;

/**
 * 客户端蓝图预览状态(Litematica 式投影交互)。
 * blueprintId: 当前手持蓝图物品的 id;
 * candidateAnchor: 未锁定时跟随准星实时计算的候选锚点;
 * locked: 是否已用中键锁定位置;anchor: 锁定后的锚点;
 * rotation: 0-3,专用旋转键(R)调整。
 */
public final class BlueprintPreviewState {
    public static final BlueprintPreviewState INSTANCE = new BlueprintPreviewState();

    private String blueprintId = "";
    private BlockPos candidateAnchor;
    private boolean locked;
    private BlockPos anchor;
    private int rotation;

    private BlueprintPreviewState() {
    }

    public void begin(String id) {
        this.blueprintId = id;
        this.locked = false;
        this.anchor = null;
        this.candidateAnchor = null;
        this.rotation = 0;
    }

    public void clear() {
        this.blueprintId = "";
        this.locked = false;
        this.anchor = null;
        this.candidateAnchor = null;
        this.rotation = 0;
    }

    /** 每 tick 更新未锁定时的候选锚点(跟随准星)。 */
    public void updateCandidate(BlockPos pos) {
        this.candidateAnchor = pos == null ? null : pos.toImmutable();
    }

    /** 中键第一次按下:锁定当前候选位置。 */
    public void lock(BlockPos pos) {
        this.anchor = pos == null ? null : pos.toImmutable();
        this.locked = this.anchor != null;
    }

    /** 旋转键按下:旋转 90°。 */
    public void rotate() {
        this.rotation = (this.rotation + 1) % 4;
    }

    /** 中键第二次按下(确认搭建)后复位。 */
    public void resetAfterConfirm() {
        this.locked = false;
        this.anchor = null;
        this.candidateAnchor = null;
    }

    public String blueprintId() {
        return blueprintId;
    }

    public boolean active() {
        return blueprintId != null && !blueprintId.isBlank();
    }

    public boolean locked() {
        return locked;
    }

    /** 当前渲染/操作使用的锚点:锁定用 anchor,否则用候选。 */
    public BlockPos currentAnchor() {
        return locked ? anchor : candidateAnchor;
    }

    public BlockPos anchor() {
        return anchor;
    }

    public int rotation() {
        return rotation;
    }
}
