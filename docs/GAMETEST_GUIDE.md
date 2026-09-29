# AIBot GameTest 测试套件详解

本文档介绍 `src/gametest/java` 下的 Minecraft GameTest 集成测试套件：它测什么、怎么测、每个测试验证什么。

## 1. 什么是 GameTest

GameTest 是 Minecraft 官方的**游戏内集成测试框架**：

- 它在**真实的 Minecraft 服务端**（无头服务器，无客户端画面）中运行；
- 每个测试用 `@GameTest` 注解声明，从空结构（`FabricGameTest.EMPTY_STRUCTURE`）起步，按测试代码**现场摆方块、生成实体、生成 AIBot 假玩家**；
- 测试驱动真实的游戏 tick（每个测试有 `tickLimit` 上限），通过 `context.runAtEveryTick(...)`、`context.complete()`、`context.throwGameTestException(...)` 等在真实物理/方块/实体语义下断言；
- 因此它能验证**只有真世界才能验证的东西**：方块破坏与掉落、玩家移动物理、拾取统计、持久化跨 tick/跨重启恢复等。

### 与 JUnit 单元测试的区别

| | `src/test`（JUnit 单元测试） | `src/gametest`（GameTest 集成测试） |
|---|---|---|
| 运行方式 | `gradlew test`，普通 JVM 进程 | `gradlew runGameTest`，启动真实 MC 服务端 |
| 依赖 | 纯逻辑，可 mock | 真实注册表、方块、实体、物理 tick |
| 典型验证 | 预算计算、序列化往返、策略边界 | 机器人真的能走到、挖到、捡到、恢复 |

> 二者互补：JUnit 快而细；GameTest 慢而真，专抓"纸上逻辑对、世界里跑不通"的问题。

## 2. 测试套件概览

- 测试类总数：**26 个**（位于 `src/gametest/java/io/github/zoyluo/aibot/` 下，按 `action/brain/command/gametest/goal/task` 分包）
- 用例总数：**587 个**（来自 `build/test-results/gametest/TEST-aibot-gametest.xml`）
- 上一次完整运行（2026-09-29）：**587 / 587 全部通过**，耗时约 **127.7 秒**
- 另有两个支撑类不是测试：`AIBotHarnessTestMod`（测试模组入口）、`AIBotTestSubcommand` / `AIBotVerifySubcommand`（`/aibot test`、`/aibot verify` 命令）、`AIBotRestartHarnessCommand`（重启 harness 服务器命令）

### 运行方式

```bash
# 完整跑一遍（含 587 个 GameTest，约 2 分钟）
gradlew runGameTest

# 只跑编译/打包（不跑测试）
gradlew build -x runGameTest
```

## 3. 各测试类逐一介绍

---

### action 包

#### 1. ActionPackPhysicalSnapGameTests（18 个）
**主题**：严格生存模式下，A* 起点无效时"单格物理回收/吸附"的回归验证。

- `centerReturnClearsResidualWalkVelocityBeforeNextServerTick` — 中心返回在下一服务端 tick 前清除残余步行速度
- `centerReturnRejectsLivingEntityOccupyingLanding` — 中心返回拒绝落点上已有活体实体
- `consecutiveHorizontalJumpsPublishEachVerifiedLanding` — 连续水平跳跃逐个发布已验证的落点
- `constrainedEmptyAndSingletonExecutorsRequireExactTerminal` — 受限空/单例执行器要求精确终点
- `constrainedInvalidStartFailsWithoutCrossCellSnap` — 受限无效起点失败且不做跨格吸附
- `dynamicRearClosureFailsBeforeConstrainedTerminalSuccess` — 动态后方封闭在受限终点成功前失败
- `edgePerchedObservedDropUsesPhysicalSupportInsteadOfOnGroundFlag` — 边缘悬挂的可见掉落物用物理支撑而非"在地面"标志
- `horizontalJumpRejectsLivingEntityOnLanding` — 水平跳跃拒绝落点上的实体
- `invalidStartUsesAdjacentPhysicalLandingBeforePrivilegedSnap` — 无效起点先尝试相邻物理落点，再特权吸附
- `minimumYSearchFindsLongSafeRouteInsteadOfShortDescent` — 最小 Y 搜索找到更长的安全路线而非短下降
- `reserve16Rejects16AndPillarsSpends17thStone` — 预留 16 时拒绝第 16 个、竖柱花第 17 个石头（76/37 同理，边界值）
- `reserve37Rejects37AndPillarsSpends38thStone` — 预留 37 边界
- `reserve76Rejects76AndPillarsSpends77thStone` — 预留 76 边界
- `sameCellEdgeDropRequiresPhysicalNudgeBeforeVanillaPickup` — 同格边缘掉落物在原版拾取前需要物理推动
- `sameGoalDifferentReturnAnchorReplacesInsteadOfThrottling` — 相同目标不同返回锚点直接替换而非限流
- `sameLevelPickupPrefersExactDropCellOverCurrentNeighbour` — 同层拾取优先精确掉落格而非当前邻居
- `standableBodySnapPreservesSafeOffsetAndRecentersRealCornerOverlap` — 可站立身体吸附保留安全偏移并使真实角落重叠居中
- `surfacePathReplanCannotEscalateIntoDigOrPillar` — 地表路径重规划不能升级为挖掘或竖柱

#### 2. BuildActionEdgeVisibilityGameTests（7 个）
**主题**：建造动作对"只在可到达边缘暴露的支撑面"的物理可见性回归。

- `lavaSourceMaybeObservableOnlyThroughAnInsetRay` — 岩浆源可能只能通过内嵌射线观察到
- `lowPerceptionRadiusRejectsOtherwiseReachableInsetPlacement` — 低感知半径拒绝本可到达的内嵌放置
- `ordinarySupportedPlacementStillUsesVanillaInteraction` — 普通有支撑的放置仍走原版交互
- `placeAtUsesExactInsetWhenAllTargetFaceCentersAreHidden` — 所有目标面中心被隐藏时使用精确内嵌
- `strictModeNeverUsesDirectHiddenPlacementFallback` — 严格模式绝不使用直接隐藏放置回退
- `supportCenterAndFaceCenterMayBeBeyondReachWhenInsetIsLegal` — 内嵌合法时支撑中心和面中心可超出触及距离
- `waterSourceMaybeObservableOnlyThroughAnInsetRay` — 水源可能只能通过内嵌射线观察到

#### 3. OffhandExecutionGameTests（9 个）
**主题**：严格生存下"可执行的背包路径"必须尊重副手资源。

- `fullInventoryToolResupplyDropsJunkAndCraftsUsablePickaxe` — 满背包工具补货丢弃垃圾并合成可用镐
- `insufficientCraftOutputCapacityLeavesInventoryBitExact` — 合成输出容量不足时背包逐位精确不变
- `insufficientMixedFamilyCapacityLeavesInventoryBitExact` — 混合家族容量不足时背包逐位精确
- `miningChannelBreaksSoftObstructionWithEmptyHand` — 挖矿通道用空手打破软障碍
- `mixedOakAndBirchLogsCompleteOneAtomicStickPlan` — 混合橡木/白桦原木完成一个原子木棍计划
- `offhandFoodPromotionPreservesAFullSelectedSlot` — 副手食物提升保留完整选中槽
- `offhandOnlyCraftingTableCompletesThreeByThreeRecipe` — 仅副手工作台完成 3×3 配方
- `raw33OffhandDiamondPickIsSelectedAndBreaksObsidian` — 副手 33 钻石镐被选中并破坏黑曜石
- `softBlockSpeedTiePreservesWoodenSwordDurability` — 软方块速度平局保留木剑耐久

---

### brain 包

#### 4. ToolRegistryMiningGameTests（5 个）
**主题**：`mine_ore` 参数契约的运行时注册表覆盖。

- `aliasesResolveOnlyTheirRequestedOreFamily` — 别名只解析其请求的矿石家族（如 diamond→钻石矿）
- `obsidianAndNonOreTargetsFailClosed` — 黑曜石和非矿石目标安全失败
- `strictAssignTaskRejectsWithoutDisturbingActiveWork` — 严格模式指派任务被拒绝且不打扰活动工作
- `strictDirectHandlersRejectWithoutDisturbingActiveWork` — 严格模式直接处理器拒绝且不打扰活动工作
- `strictPlayerCommandsRejectWithoutDisturbingActiveWork` — 严格模式玩家命令拒绝且不打扰活动工作

---

### command 包

#### 5. AIBotVerifyFailFastGameTests（5 个）
**主题**：`/aibot verify` 对"从零开始严格验收"的快速失败边界（世界级契约）。

- `fromZeroMiningAllowedPrivilegeFailsInOneVerifierPollAndClearsAudit` — 从零挖矿 + 允许特权在一次轮询即失败并清除审计
- `fromZeroMiningDeathFailsInOneVerifierPollAndCancelsAllIntent` — 从零挖矿 + 死亡在一次轮询即失败并取消全部意图
- `fromZeroMiningNonSurvivalModeFailsInOneVerifierPollAndClearsAudit` — 从零挖矿 + 非生存模式一次轮询即失败并清除审计
- `strictStripMineScenarioRequiresExactTypedRejection` — 严格条带开采场景要求精确的类型化拒绝
- `verifyAllExpandsLegacyStripMineByOperatingProfile` — `verify all` 按运行档案展开旧版条带开采场景

---

### gametest 包

#### 6. AIBotDeterministicGameTests（4 个）
**主题**：独立 GameTest 源集的最小世界级冒烟测试（刻意不依赖随机、外部服务、持久化）。

- `blockedEndpointPrefersNearestUpperStandOverDeeperCave` — 被堵终点偏好最近的上一层站立点而非更深洞穴
- `blockMutationIsVisible` — 方块变更可被测试上下文看到（框架冒烟）
- `missionSpecsRoundTripWithBootstrappedRegistries` — MissionSpec 用启动后的注册表往返序列化
- `scheduledAssertionRunsAtExpectedTick` — 计划断言在预期 tick 运行（框架冒烟）

---

### goal 包

#### 7. DeathRecoveryMissionGameTests（2 个）
**主题**：活动/排队采矿任务在机器人死亡时的确定性挂起覆盖。

- `haveItemSurvivesDeathRecoveryAndPreservesQueuedMineOre` — haveItem 目标在死亡恢复后存活并保留队列中的挖矿目标
- `mineOreSurvivesDeathRecoveryAndPreservesQueuedHaveItem` — 挖矿目标在死亡恢复后存活并保留队列中的 haveItem

#### 8. GoalPlannerMiningGameTests（37 个）
**主题**：需要启动 Minecraft 注册表的世界运行时挖矿计划覆盖（钻石/黑曜石远征、食物/火把/工具供给）。

- `diamondStackReplansExactNetHuntAfterShelterConsumesRawMeat` — 钻石任务在庇护所消耗生肉后重规划精确净猎杀
- `diamondStackStrictlyAlternatesBoundedBatchesAndServiceCheckpoints` — 钻石任务严格交替有界批次和服务检查点
- `directFoodGoalDoesNotInheritExpeditionBestEffortFlag` — 直接食物目标不继承远征"尽力而为"标志
- `emptyInventoryBootstrapIsNotMistakenForOptionalProvisioning` — 空背包启动不被误认为可选补给
- `emptyInventoryObsidianGoalBuildsDiamondPickaxeChain` — 空背包黑曜石目标构建钻石镐合成链
- `finalDiamondAtMineLayerServicesMissingChannelToolsBeforeOreDig` — 矿层最后一个钻石先服务缺失通道工具再挖矿
- `fourDeliveredDiamondsResumeToTheNextEightBoundary` — 交付 4 颗钻石恢复到下一个 8 格边界
- `lowAndExactNetheriteDurabilityUseTheSameObsidianContract` — 低耐久与精确耐久下界合金用同一黑曜石契约
- `missingStoneSwordHasAnIndependentPreWaterBudget` — 缺失石剑有独立的取水前预算
- `mixedDiamondAndNetheriteDurabilityUsesTheAggregateBoundary` — 混合钻石/下界合金耐久用聚合边界
- `mixedLogFamiliesAggregateBeforePlanningOneRemainingPlankGap` — 混合原木家族先聚合再规划剩余木板缺口
- `mixedLogFuelInventoryOnlyPlansTheFamilyDeficit` — 混合原木燃料背包只规划家族缺口
- `nearBrokenStonePicksStillPlanRareKitImmediatelyBeforeFinalDescent` — 近破损石镐仍在最后下降前立即规划稀有装备包
- `nestedCoalTorchProvisionDoesNotRepeatTheSameLayerHandoff` — 嵌套煤炭/火把补给不重复同层交接
- `netheriteTierDoesNotSilentlyDowngrade` — 下界合金等级不静默降级
- `obsidianReadinessUsesAggregatedDiamondPickDurability` — 黑曜石就绪判定使用聚合钻石镐耐久
- `partialDiamondStackRetainsLongMissionServiceIdentity` — 部分钻石任务保留长任务服务身份
- `partialSurfaceObsidianStackRetainsThirtyTwoItemShelterContract` — 部分地表黑曜石保留 32 格庇护所契约
- `preparedAtMineLayerStartsDirectlyWithDiamondBatch` — 矿层已准备时直接从钻石批次开始
- `preparedObsidianExpeditionAcquiresWaterBeforeMining` — 已准备黑曜石远征先取水再挖矿
- `preparedObsidianKitStillPlansItsMissingCarriedCraftingTable` — 已准备黑曜石装备包仍规划缺失的随身工作台
- `preparedSevenAndEightTorchesFundTheWholePreWaterDescentBatch` — 准备 7/8 火把支撑整个取水前下降批次
- `preparedWaterBucketSkipsDuplicateAcquisition` — 已准备水桶跳过重复获取
- `rawTwoDiamondPickUsesLooseDiamondsBeforeAddingAnAcquisitionPick` — 2 钻石镐先用散钻石再添加获取镐
- `runningKitRestoreKeepsOnlyServiceAndProvenDescentTail` — 运行中装备包恢复只保留服务并证明下降尾部
- `sixteenDiamondsUseExactlyTwoBatchesWithOneCumulativeCheckpoint` — 16 颗钻石正好两个批次、一个累计检查点
- `substackRareTargetsRetainDirectFreshCraftWithoutTarget64Kit` — 子堆稀有目标保留直接新合成、无需 64 目标装备包
- `surfaceCoalWithoutVisibleOreDescendsToRockLayerBeforeMining` — 无可见矿石的地表煤先下降到岩石层再挖
- `surfaceDiamondStackAddsExactlyFourteenRawLogsShelterReserve` — 地表钻石任务加正好 14 原木庇护所储备
- `surfaceDiamondStackDoesNotTreatPlanksAsHardShelterReserve` — 地表钻石任务不把木板当硬庇护所储备
- `surfaceDiamondStackTopsUpThirteenButNotFourteenShelterBlocks` — 地表钻石任务补足 13 但不超过 14 庇护方块
- `surfaceObsidianStackAlsoReservesFourteenShelterBlocks` — 地表黑曜石任务也储备 14 庇护方块
- `undergroundDiamondStackResumeDoesNotGatherShelterWood` — 地下钻石任务恢复不收集庇护木材
- `undergroundObsidianReplanConsumesMixedLogFuelAsOneFamily` — 地下黑曜石重规划把混合原木燃料当一个家族
- `undergroundObsidianReplanUsesBirchLogsForMissingStickPlanks` — 地下黑曜石重规划用白桦原木补缺失木棍/木板
- `undergroundObsidianReplanUsesCarriedKitWithoutSurfaceWork` — 地下黑曜石重规划用随身装备包、不做地表工作
- `undergroundRawMeatCannotMasqueradeAsMiningFoodReserve` — 地下生肉不能冒充挖矿食物储备

#### 9. MiningCheckpointMissionGameTests（70 个）
**主题**：任务级证明——活动的矿石批次跨重启恢复其持久化分支游标（容量服务、稀有纪元、命名空间、账本字节级精确）。

- `activeOreBatchRestoresSameMissionAndBranchCursor` — 活动矿石批次恢复同一任务和分支游标
- `activeOreRestoreRejectsSameFamilyWithWrongLogicalBatchCount` — 活动矿石恢复拒绝逻辑批次计数错误的同家族
- `activePocketKindIsInferredAndSemanticFailureIsQuarantined` — 活动岩腔种类被推断且语义失败被隔离
- `activePocketWaitsForItsPersistedDimensionBeforeRestore` — 活动岩腔等待其持久化维度后再恢复
- `advancedCapacityWorkFaceSchedulesSecondServiceAcrossRestart` — 高级容量工作面跨重启安排第二次服务
- `bootstrapOrdinaryNamespaceIsDiscardedAtRareBoundaryZeroRestart` — 引导普通命名空间在稀有边界零重启时被丢弃
- `boundaryRareServiceRestoreRejectsMissingMiningNamespace` — 边界稀有服务恢复拒绝缺失挖矿命名空间
- `capacityParentIdentityRequiresExactDebitedFamilyFaceAndCursor` — 容量父身份要求精确扣款家族面与游标
- `capacityServiceCountCapRejectsAnotherProgressedHandoff` — 容量服务计数上限拒绝再次推进的交接
- `capacityServiceRestoreMigratesLegacyMissingWatermarkConservatively` — 容量服务恢复保守迁移旧版缺失水印
- `capacityServiceRestoreRejectsMissingDeclaredParent` — 容量服务恢复拒绝缺失的声明父级
- `capacityServiceRestoreRejectsServiceCountAboveTarget` — 拒绝高于目标的服务计数
- `capacityServiceRestoreRejectsStaleFaceAtServiceBoundary` — 服务边界拒绝陈旧面
- `capacityServiceRestoreRejectsUndebitedDeclaredParent` — 拒绝未扣款的声明父级
- `capacityServiceRestoreRejectsWatermarkAheadOfParent` — 拒绝超前于父级的水印
- `capacityServiceRestoreRejectsWatermarkWithoutParent` — 拒绝无水印的父级
- `capacityServiceRestoreRequiresWatermarkAtServiceBoundary` — 服务边界必须带水印
- `committedRareBatchResetsEpochAndNextBatchCanRetry` — 已提交稀有批次重置纪元、下一批可重试
- `completedCapacityRetryDefersSafetyAndRestoresClosedCommit` — 完成容量重试推迟安全并恢复封闭提交
- `completedChannelToolResupplyRetriesSmallDiamondBatchWithoutParentReplan` — 完成通道工具补货重试小钻石批次、不重规划父级
- `completedInterbatchServicePromotesAuxCursorToNextBatch` — 完成批间服务把辅助游标提升到下一批
- `completedToolResupplyRetriesTheSameOreBatchWithoutParentReplan` — 完成工具补货重试同一矿石批次、不重规划父级
- `diamond64BootstrapCoalUsesOrdinaryCheckpointAndPhysicalResupply` — 钻石 64 引导煤用普通检查点和物理补货
- `diamond64RestoreReplaysOnlyFourItemsBeforeBoundaryEight` — 钻石 64 恢复只在 8 边界前重放 4 项
- `diamond64RestoresMissionKitAndSealsInventoryBeforeFinalDescent` — 钻石 64 恢复任务装备包并在最终下降前封存背包
- `diamond64TailOfOneRestartsAndAdvancesItsRareResourceEpoch` — 钻石 64 剩 1 时重启并推进稀有资源纪元
- `epochOneTimeoutWithMissionMarginSurvivesAndDrawsOneEpoch` — 纪元 1 超时带任务余量存活并消耗一个纪元
- `epochTimeoutWithExhaustedMarginPoolStaysTerminal` — 纪元超时且余量池耗尽保持终点
- `failedCapacityHandoffWithoutParentFamilyRollsBackDebtAndRebatchSettles` — 无父家族的失败容量交接回滚债务并重批结算
- `failedInterbatchAuxiliaryServicePreservesLaterSameFamilyCursor` — 失败批间辅助服务保留更晚的同家族游标
- `failedNonPocketAuxiliaryServiceReplansWithoutStaleReplay` — 失败非岩腔辅助服务重规划、不陈旧重放
- `failedNonPocketPrimaryServiceReplansWithoutStaleReplay` — 失败非岩腔主服务重规划、不陈旧重放
- `firstRareChannelToolFailureSchedulesOneServiceAndPreservesCursor` — 首次稀有通道工具失败安排一次服务并保留游标
- `firstRareInventoryFailureSchedulesOneCursorBoundService` — 首次稀有背包失败安排一次游标边界服务
- `firstRareTorchEpochFailureSchedulesOneServiceAndPreservesCursor` — 首次稀有火把纪元失败安排一次服务并保留游标
- `foreignServiceIsBlockedButRotatedAxisIsAllowed` — 外部服务被阻止但旋转轴允许
- `guardOnlyRestoreUsesTypedReasonOnlyWhenCausalityIsUnique` — 仅守卫恢复只在因果唯一时使用类型化原因
- `interbatchServiceRestoreSurvivesFailedFreshPlan` — 批间服务恢复在新计划失败后存活
- `ordinaryFirstServiceWithoutMiningNamespaceRestoresFromOwnCursor` — 无挖矿命名空间的普通首次服务从自身游标恢复
- `ordinarySecondBatchOwnsItsSecondBoundedPhysicalChannelRepair` — 普通第二批拥有其第二次有界物理通道修复
- `ordinaryServicePreservesProtectedRareMiningNamespace` — 普通服务保留受保护的稀有挖矿命名空间
- `ordinaryServiceRestoreRejectsWrongFamilyOpenMiningNamespace` — 普通服务恢复拒绝错误家族的开放挖矿命名空间
- `ordinaryServiceRestoreRejectsWrongMission` — 普通服务恢复拒绝错误任务
- `ordinaryTaskAndMiningLedgerMustBeByteExact` — 普通任务和挖矿账本必须逐字节精确
- `progressedCapacityRetrySchedulesSecondServiceAcrossRestart` — 推进的容量重试跨重启安排第二次服务
- `protectedRareOrdinaryFullInventorySchedulesAuxiliaryCapacityParent` — 受保护稀有 + 普通满背包安排辅助容量父级
- `protectedRareRestoreKeepsByteExactRareAndAuxiliaryCapacityLedgers` — 受保护稀有恢复保持稀有与辅助容量账本逐字节精确
- `protectedRareRestoreSettlesClosedAuxiliaryCapacityCommit` — 受保护稀有恢复结算封闭辅助容量提交
- `rareServiceRestoreRejectsSelfConsistentWrongMissionTarget` — 稀有服务恢复拒绝自洽但错误的任务目标
- `replanBudgetAndProgressSnapshotRoundTrip` — 重规划预算和进度快照往返一致
- `sameBatchEpochOneChannelToolFailureIsTerminalWithoutAnotherService` — 同批纪元 1 通道工具失败、无另一服务时即终点
- `sameBatchEpochOneTorchExhaustionIsRejectedWithoutAnotherService` — 同批纪元 1 火把耗尽被拒绝、无另一服务
- `satisfiedGoalRejectsInvalidActiveOreCheckpoint` — 已满足目标拒绝无效活动矿石检查点
- `satisfiedGoalRejectsInvalidMiningNamespace` — 拒绝无效挖矿命名空间
- `satisfiedGoalRejectsOrphanedOrdinaryBreakLedger` — 拒绝孤立的普通破坏账本
- `satisfiedGoalRestoresFullyDeliveredOpenOreLedgerBeforeCommit` — 提交前恢复完全交付的开放矿石账本
- `satisfiedGoalRestoresPocketFirstThenFailsWithOriginalTypedReason` — 先恢复岩腔、再用原始类型化原因失败
- `secondRareInventoryFailureIsTerminalWithoutAnotherService` — 第二次稀有背包失败、无另一服务时即终点
- `serviceRestartReturnsToSavedFaceBeforeSecondDiamondBatch` — 服务重启在第二批钻石前回到保存面
- `settledServiceGuardNamespaceRestoresFailClosed` — 稳定服务守卫命名空间恢复时安全失败
- `settledServiceGuardRestoreCompatibilityIsStrict` — 稳定服务守卫恢复兼容性严格
- `smallRareFullInventorySchedulesOneCursorBoundCapacityService` — 小型稀有满背包安排一次游标边界容量服务
- `standaloneOrdinaryMiningLedgerCannotBeDiscarded` — 独立普通挖矿账本不能被丢弃
- `terminalCapacityGuardSurvivesRepairRestartAndStopsGenericReplan` — 终点容量守卫在修复重启后存活并停止通用重规划
- `terminalOrdinaryHandoffRestoresWithoutARepeatedOreSuccessor` — 终点普通交接恢复、不重复矿石后继
- `terminalReceiptKeepsOriginalReasonAcrossDimensionDrift` — 终点收据跨维度漂移保持原始原因
- `terminalServiceGuardAllowsSlotRepairAndRemainsDurable` — 终点服务守卫允许槽位修复并保持持久
- `terminalServiceGuardSurvivesCraftRestartAndBlocksWithoutMutation` — 终点服务守卫在合成重启后存活并无变更地阻止
- `trappedBlindBranchFailsMissionWithoutRecreatingOreDig` — 被困盲目分支任务失败、不重建挖矿任务
- `usedSmallRareCapacityDebitMakesTheSecondFullInventoryTerminal` — 已用的小稀有容量扣款使第二次满背包成为终点

---

### task 包

#### 10. AcquireWaterTaskGameTests（32 个）
**主题**：实地证明地表水被物理到达、经原版机制装水、且可检查点重启。

- `approachDescendsToExactReachableStandBeforeFilling` — 接近水源时先下降到精确可站立位置再装水
- `blockedSurfaceSectorClearsOnlyLocalLedgerAcrossRestart` — 被堵地表扇区跨重启只清除本地账本
- `checkpointCursorAndAuthorityForgeryFailsClosed` — 伪造检查点游标和权限会安全失败
- `currentCursorEndsAfterCompleteSeventhRing` — 完成第七圈搜索后当前游标结束
- `deepMineCarvesRestartableStairBackToSurfaceWater` — 深矿用可重启的阶梯挖回地表水
- `deepMineFailsFastWithoutStonePickMaterialsAndPreservesIron` — 无石镐材料时深矿快速失败且保留铁
- `deepMineLocallyCraftsAStonePickWhenAscentToolsAreExhausted` — 攀登工具耗尽时就地合成石镐
- `deepSkylitRavineDoesNotCountAsSurfaceExit` — 深的天光峡谷不算地表出口
- `displacedSearchRetriesOnItsFirstResumeTick` — 被位移的搜索在恢复第一 tick 重试
- `dryOverhangFootingUsesItsObservableSkyEdgeAsSurfaceExit` — 干燥悬空落脚点用可见天光边缘作为地表出口
- `dryReturnCollectsVisibleReachablePlainWaterWithoutMintingSurfaceProof` — 干燥回程收集可见可及静水、不需要地表证明
- `legacyHundredTerminalStaysTerminalAcrossSchemaFourRestore` — 旧 100 格终点在 schema4 恢复后仍是终点
- `legacyRunningAndSchemaTwoTerminalRetainOldAuthority` — 旧运行态和 schema2 终点保留旧权限
- `malformedCheckpointFailsClosed` — 格式错误检查点安全失败
- `multiLevelOpenCaveBuildsVanillaFoundationBridge` — 多层开放洞穴搭原版地基桥
- `nearAnchorSkyPocketWithoutLateralEgressKeepsAscending` — 锚点附近无侧面出口的天光岩腔继续上升
- `openCaveAscentPlacesOneVisibleSupportAndSurvivesRestart` — 开放洞穴上升放一个可见支撑且重启后存活
- `oscillationCannotResetWaypointBudgetAcrossRestart` — 震荡不能跨重启重置航点预算
- `reachedFluidRelocationSurvivesPauseAndSafetyDisplacement` — 到达流体后的位移在暂停和安全位移后存活
- `reachedRelocationPauseWithoutDisplacementSettlesBeforeResume` — 到达后的位移暂停（无位移）在恢复前稳定
- `restoredHardBudgetRemainsTypedAndDecoderValid` — 恢复的硬预算保持类型化且解码器有效
- `returnDoesNotReadOrCollectAnOccludedPlainWaterSource` — 回程不读取/收集被遮挡的静水源
- `schemaThreeSearchWithoutSurfaceExitFailsClosed` — schema3 搜索无地表出口时安全失败
- `schemaTwoSearchWithoutSurfaceExitFailsClosed` — schema2 搜索无地表出口时安全失败
- `sealedSurfaceFailsTypedWithoutBurningCursor` — 封堵地表类型化失败且不烧毁游标
- `searchContinuesPastHundredAndPhysicallyFillsAfterRestart` — 搜索越过 100 格限制、重启后实际装水
- `shallowWaterAtFeetHandsReturnToPhysicalRescueBeforeAscentInspection` — 脚边浅水：回程先物理救援再检查上升
- `sharedFluidArcCarvesDrySameLevelPocketBeforeAscent` — 共享流体弧线先挖干燥同层岩腔再上升
- `skyVisibleExitStartsSearchWithoutReenteringTheMinedStair` — 天光可见出口开始搜索且不重进已挖阶梯
- `surfaceTransitionCancelsPausedAscentCraftAndMotion` — 地表转换取消暂停的上升合成和移动
- `visibleFluidCeilingForcesSameLevelRelocationBeforeAscent` — 可见流体天花板迫使先同层位移再上升
- `visibleWaterBeyondInitialViewSurvivesCheckpointRestart` — 初始视野外的可见水在检查点重启后存活

#### 11. CreateObsidianMissionRecoveryGameTests（40 个）
**主题**：32 格黑曜石远征的任务级重启边界。

- `airAtRestoredActiveBreakRebuildsProtectedPickupTransaction` — 恢复的活动破坏处空气重建受保护拾取事务
- `airAtRestoredActiveBreakRetainsLiveSourceForFreshProtectionWindow` — 恢复的活动破坏处保留活动源以开新保护窗口
- `auditedServiceRestartDoesNotPromoteUnrelatedInventoryObsidian` — 被审计服务重启不提升无关背包黑曜石
- `auditScanDefersPreExistingObsidianAndBindsOnlyWaterBackedCell` — 审计扫描推迟既有黑曜石、只绑定有水格子
- `committedMakeDoneAdvancesToStockpileWithoutReplay` — 已提交 make 完成推进到库存阶段、不重放
- `committedPreflightIsNotReplayedAfterCrashWindowRestore` — 崩溃窗口恢复后不重放已提交预检
- `committedPreflightUsesCheckpointIdentityWhenFreshTargetIsUnknown` — 新目标未知时已提交预检用检查点身份
- `committedStalePreflightCannotSkipFreshTargetPreflight` — 已提交陈旧预检不能跳过新目标预检
- `compoundBuildCannotUseUnknownPreflightAuthority` — 复合建造不能使用未知预检权限
- `compoundBuildWithFailedFreshPlanCannotReplayOnlyBoundaryAndMake` — 新计划失败的复合建造不能只重放边界和 make
- `darkRestoredClosedMaskOutranksMissingTorch` — 黑暗恢复的闭合遮罩优先于缺失火把
- `doneBoundaryEightCannotAcknowledgePendingBoundarySixteen` — 已完成 8 格边界不能确认待处理 16 格边界
- `doneServiceCheckpointAcknowledgesBoundaryWithoutReplay` — 完成服务检查点确认边界、不重放
- `doneServiceCheckpointAlreadyAcknowledgedResumesWithoutReplay` — 已确认服务检查点恢复、不重放
- `eightBlockBoundaryRunsObsidianPolicyThenResumesOriginalThirtyTwo` — 8 格边界先跑黑曜石策略再恢复原 32 格
- `fifteenOfThirtyTwoRestoresMakeObsidianWithoutDroppingCheckpoint` — 32 格中完成 15 格恢复 make 黑曜石且不丢检查点
- `fourClosedSearchDirectionsFailTypedWithoutRotatingAsProgress` — 四个封闭搜索方向类型化失败、不算进度旋转
- `interruptedBoundaryServiceRestoresOnlyServiceThenOriginalMake` — 被打断边界服务只恢复服务再恢复原 make
- `interruptedPreflightReplacesTheFreshPlannerCopyWithoutBoundaryAck` — 被打断预检替换新计划副本、不确认边界
- `missingToolFailureWithOpenTransactionResuppliesBeforeResuming` — 缺工具失败且事务打开时先补货再恢复
- `pickupCheckpointPhysicallyCollectsSurvivingItemEntity` — 拾取检查点物理收集存活的物品实体
- `pickupMicroStepsCloseTwoCellGapWithoutAcceptingAdjacentPathSnap` — 拾取微步关闭两格间隙、不接受相邻路径吸附
- `placedWaterDebtRestoresRecoveryBeforeAcquireWater` — 已放置水的债务先恢复再执行取水
- `rawTwoPickSettlesFinalBreakAndPhysicalPickupAtRawOne` — 两个原版镐在剩 1 时结算最终破坏和物理拾取
- `remainingTargetPreflightCanRepairAnAcknowledgedTransaction` — 剩余目标预检可修复已确认事务
- `restoredScanReturnsToDedicatedRimBeforeBindingDisplacedObsidian` — 恢复扫描先返回专用边缘再绑定被位移黑曜石
- `runningBoundaryRestorePreservesUnrelatedBuildMaterialPrefix` — 运行中边界恢复保留无关建造材料前缀
- `runningBoundaryServiceCannotRestoreAfterBoundaryWasAcknowledged` — 边界已确认后运行中服务不能恢复
- `runningPreflightUsesCheckpointIdentityWhenFreshTargetIsUnknown` — 新目标未知时运行中预检用检查点身份
- `runningPreflightWithStaleTargetCannotReplaceFreshTarget` — 带陈旧目标的运行中预检不能替换新目标
- `searchPhysicallyLightsTheDarkTrailBehindItsReachedFace` — 搜索物理照亮到达面后的黑暗路径
- `searchPreservesHigherTierOreAndClosesTheStoneOnlyLeg` — 搜索保留更高等级矿石、只关闭石头支线
- `skylitSearchDoesNotSpendTheUndergroundTorchReserve` — 天光搜索不消耗地下火把储备
- `staleActiveMakeTargetCannotImpersonateFreshRemainingEight` — 陈旧活动 make 目标不能冒充新剩余 8 格
- `stalePreflightCannotUseUnknownFreshTargetAuthority` — 陈旧预检不能使用未知新目标权限
- `staleTargetThirtyTwoBoundaryCannotImpersonateFreshRemainingEight` — 陈旧 32 格边界目标不能冒充新剩余 8 格
- `strictAuditSessionLossAndReplacementMismatchFailBeforeAnotherAction` — 严格审计会话丢失/替换不匹配在其他动作前失败
- `thirtyOneOfThirtyTwoDoesNotCompleteOrReplaceCheckpoint` — 32 格中完成 31 格不完成也不替换检查点
- `threeBlockedDirectionsClearOnlyAfterTheOpenFaceIsReached` — 三个封闭方向只有到达开放面后才清除
- `unsafePickupEndpointIsRejectedWithoutDiscardingLedger` — 不安全拾取终点被拒绝且不丢弃账本

#### 12. CreeperDefenseGameTests（7 个）
**主题**：专用苦力怕所有者的黑盒安全契约（真实生存背包/实体观察/移动/放置）。

- `hiddenCreeperMemoryYieldsToNonCreeperLowHpShelter` — 隐藏苦力怕记忆让位给非苦力怕低血庇护所
- `hiddenNearMemoryIsNotOverwrittenByFarUnarmedCreeper` — 隐藏近处记忆不被远处空手苦力怕覆盖
- `lateFuseAssignmentStartsPhysicalDefenseSynchronously` — 迟到的引线分配同步开始物理防御
- `lateralOscillationCannotResetAwayProgress` — 横向震荡不能重置逃离进度
- `nonSafetyEatIsPausedAndResumedAsExactInstance` — 非安全进食被暂停并以精确实例恢复
- `olderOccludedRiskCannotCompleteWhileSecondRiskJustTurnedHidden` — 更早被遮挡风险在第二个风险刚变隐藏时不能完成
- `twoLegalBlocksCompleteCoreAndHoldWithoutSideMaterial` — 两个合法方块完成核心并用外部材料保持

#### 13. DangerWatcherLowHealthGameTests（44 个）
**主题**：DangerWatcher 低血量任务抢占边界的实时调度证明。

- `armorEquipRemainsIndependentFromMeleeWeaponFiltering` — 护甲装备独立于近战武器过滤
- `axeRemainsQualifiedWhilePickaxeCannotDisplaceIt` — 斧头保持合格、镐不能替换它
- `closerZombieCannotMaskObservableCreeper` — 更近僵尸不能掩盖可见苦力怕
- `combatReequipsBackupInTheSameAttackBoundary` — 战斗在同一攻击边界内重装备备用武器
- `combatRetreatAdmitsLateralSurfacePath` — 战斗撤退允许横向地表路径
- `completedCreeperDefenseReacquiresWithoutMissionStackGap` — 完成苦力怕防御重新获取、无任务栈间隙
- `contactHostileBlocksHealingAndForcesCounterattack` — 接触的敌对者阻止治疗并强制反击
- `createObsidianRawOneSettlementIsNotPreempted` — 造黑曜石剩 1 的结算不被抢占
- `creeperIsNeverHitFromStrikeOrSecondaryRetreat` — 苦力怕绝不被打击或二次撤退击中
- `directCombatNeverAttacksEnderman` — 直接战斗绝不攻击末影人
- `endermanAngryAtAnotherEntityDoesNotInterruptCurrentWork` — 对别实体生气的末影人不打断当前工作
- `entitylessLowHealthThreatCannotInventDownwardEscape` — 无实体的低血威胁不能编造向下逃跑
- `equalDamageWeaponSelectionPrefersRemainingDurability` — 等伤害武器选择偏好剩余耐久
- `equalDamageWeaponSelectionPrefersSwordBeforeDurability` — 等伤害武器选择先偏好剑再耐久
- `evadeExaminesFifthDirectionWithinBoundedAdmission` — 躲避在有限准入内检查第五方向
- `exactObsidianPickBudgetIsNotPreemptedByGenericResupply` — 精确黑曜石镐预算不被通用补货抢占
- `failedSurfacePathEvadeReleasesSprintAndAllowsPausedWorkResume` — 地表路径失败的躲避释放疾跑并允许暂停工作恢复
- `finalUseAxeIsNotAQualifiedMeleeWeapon` — 末次使用的斧头不是合格近战武器
- `finalUseSwordCannotAuthorizeCombat` — 末次使用的剑不能授权战斗
- `healthyMeleeCombatIsNotPreemptedByUndergroundEntomb` — 健康近战战斗不被地下埋没抢占
- `hostileLowHealthCannotInterruptAtomicHealingEat` — 敌对低血不能打断原子化治疗进食
- `leaseExitCannotCompleteWhileAHostileRemainsInContact` — 有敌对者接触时租约退出不能完成
- `lowHealthAloneDoesNotReplaceCurrentWorkWithEvade` — 仅低血不把当前工作替换为躲避
- `lowHealthCreeperCannotEnterEmergencyEntomb` — 低血苦力怕不能进入紧急埋没
- `lowHealthWithFoodPausesWorkToEatForHealing` — 低血有食物时暂停工作进食治疗
- `nearestSecondaryPressureBlocksFoodWithoutTakingPrimaryCredit` — 最近次级压力阻断进食但不占主信用
- `nightCreeperWithShelterMaterialsChoosesDedicatedDefense` — 夜晚苦力怕有庇护材料时选择专用防御
- `observableCreeperAtFifteenBlocksTriggersDedicatedDefense` — 15 格内可见苦力怕触发专用防御
- `observedCreeperDefenseExtendsBeyondFirstWaypoint` — 观察到的苦力怕防御延伸到第一航点之后
- `observedHostileInsideThreatCooldownBlocksNewNakedHealingEat` — 威胁冷却内可见敌对者阻止新的裸治疗进食
- `oreDigRawOnePickupAndActiveBreakRemainOwned` — 挖矿剩 1 镐的拾取和活动破坏保持归属
- `pausedDigDownClaimsObservedLavaAndPaysExactReturn` — 暂停下挖认领可见岩浆并支付精确回程
- `pausedMiningOwnerDoesNotTravelForDamagedCombatWeapon` — 暂停挖矿所有者不为损坏战斗武器旅行
- `pausedMiningOwnerResuppliesInPlaceWithoutBaseTravel` — 暂停挖矿所有者就地补货、不返回基地
- `pickaxeOnlyInventoryCannotAuthorizeCombat` — 只有镐的背包不能授权战斗
- `pointBlankLiveChargedCreeperDuringStalledEvadeSurvivesAndResumesMission` — 停滞躲避时贴脸活引线苦力怕：存活并恢复任务
- `primaryDeathDuringHealIsCreditedExactlyOnce` — 治疗期间的主要死亡只记一次
- `provokedEndermanRoutesToEvade` — 被激怒的末影人路线转为躲避
- `rangedLineOfSightBlocksCombatHealBeyondMeleeBoundary` — 超出近战边界的远程视线阻止战斗治疗
- `rangedSecondaryAtFourteenBlocksBlocksPrimarySettlementUntilItBreaks` — 14 格远程次级阻止主结算直到它消失
- `rawOnePickOnNonOwnerStillTriggersGenericResupply` — 非所有者的剩 1 镐仍触发通用补货
- `stonePickCraftIgnoresNearlyBrokenHeldPick` — 石镐合成忽略几乎破损的持有镐
- `terminalShelterEpisodeUsesClosedDefensiveCombatUntilRelocation` — 终点庇护所阶段用封闭防御战斗直到位移
- `unprovokedEndermanDoesNotInterruptCurrentWork` — 未激怒的末影人不打断当前工作

#### 14. DescendCheckpointGameTests（21 个）
**主题**：DescendToYTask 精确阶梯交接的重启契约。

- `bridgeWorldReceiptSurvivesRestartWithoutDuplicateMaterial` — 桥世界收据跨重启存活、不重复材料
- `committedDescentAtBudgetBoundaryIsAcknowledgedWithoutReplay` — 预算边界已提交下降被确认、不重放
- `descentLightingDoesNotConsumeToolServiceSticks` — 下降照明不消耗工具服务木棍
- `detourCheckpointSchemaRejectsInventedOrIncompleteEdgeHistory` — 绕路检查点 schema 拒绝编造/不完整边历史
- `edgesSeventeenCheckpointContinuesToANearbySupportedCaveRim` — 17 边检查点继续到附近有支撑洞穴边缘
- `exhaustedCheckpointFailsOnItsPersistedClock` — 耗尽检查点在其持久化时钟失败
- `fullDepthDeepslateDescentWithFiveStonePickaxesFitsItsPersistedWindow` — 5 把石镐深板岩全深下降适配持久化窗口
- `fullThirtyTwoEdgeCheckpointFailsClosedWithoutRefreshingOrReplaying` — 完整 32 边检查点安全失败、不刷新不重放
- `interruptedLandingAtOriginRejectsTheSameEdgeAfterRestore` — 原点被打断落点在恢复后拒绝同一边
- `isolatedPillarBuildsOnePhysicalFloorBeforeDetourMovement` — 孤立立柱先建一层物理地板再绕路移动
- `isolatedPillarKeepsEmergencyReserveAndFailsClosed` — 孤立立柱保留紧急储备并安全失败
- `knockbackLandingDriftReanchorsInsteadOfFailingTheMission` — 击退落点漂移重新锚定而非任务失败
- `plannerOmittedDescentStillReplaysTheActiveCheckpoint` — 计划器省略下降仍重放活动检查点
- `satisfiedGoalRejectsMissingDescendCheckpoint` — 已满足目标拒绝缺失下降检查点
- `schemaFourRestartKeepsTheOriginalDepthWindowAtANewHeight` — schema4 重启在新高度保持原深度窗口
- `settledLandingSurvivesSafetyTaskDisplacement` — 稳定落点在安全任务位移后存活
- `threatPausePreservesRejectionAtTheSettledLanding` — 威胁暂停保留稳定落点上的拒绝
- `traversedDetourEdgesSurviveRestartAndUnlockTheUpperSealRoute` — 已走绕路边跨重启存活并解锁上层封堵路线
- `unsupportedSolidDetourPreservesUpperRetreatAcrossRestart` — 无支撑固体绕路跨重启保留上层撤退
- `upperRetreatLandingBackOnTheObstacleFloorKeepsItsDetourDebt` — 上层撤退落回障碍物地板保持绕路债务
- `visibleAdjacentLavaRejectsBridgePlacement` — 可见相邻岩浆拒绝桥放置

#### 15. DigDownReturnGameTests（27 个）
**主题**：石头启动阶段"事实性阶梯返回"的严格生存回归。

- `disconnectedDescentImmediatelyBecomesSafetyReturn` — 断连的下降立即变成安全返回
- `exactReturnRejectsGrossCollectionWithNetDeliveryShortfall` — 精确返回拒绝毛收集但净交付短缺
- `exhaustedReturnBudgetFailsOnItsOwnClock` — 耗尽返回预算在其时钟失败
- `grossReservePaysTwoPillarsAndStillDeliversRequestedStone` — 毛储备支付两根柱子仍交付所需石头
- `horizontalOpenCorridorAdvancesFactuallyAndNeverBacktracks` — 水平开放走廊事实前进、绝不回溯
- `minedStoneReturnsAlongRecordedStaircaseBeforeCompleting` — 挖出石头沿记录阶梯返回后才完成
- `missingMineCheckpointIsRejectedEvenWhenGoalIsSatisfied` — 目标已满足也拒绝缺失挖矿检查点
- `movedDescendCheckpointStartsFreshAtCurrentPose` — 移动的下降检查点以当前姿态重新开始
- `nearBudgetHorizontalPickupDebtSurvivesRestartAndSettlesBeforeTimeout` — 近预算水平拾取债务重启后存活并在超时前结算
- `pausedCheckpointRestartKeepsOldEntryReturnDebt` — 暂停检查点重启保留旧入口返回债务
- `pillarRepairSpendsDirtBeforeMissionCobblestone` — 柱子修复先花泥土再花任务圆石
- `promotedSchema2ReturnClearsLocalWaterSealOwnership` — 提升的 schema2 返回清除本地水封所有权
- `rememberedWalledEntryPhysicallyRelocatesBeforeMining` — 记住的被墙入口先物理位移再挖掘
- `restoredDescentFailureReturnsBeforePublishingFailure` — 恢复的下降失败先返回再发布失败
- `restoredLargeQuotaContinuesPastLegacyBudgetAndKeepsNetDeliveryStrict` — 恢复大配额越过旧预算并保持净交付严格
- `returnPauseReanchorsTheCurrentFactualCell` — 返回暂停重新锚定当前事实格
- `safetyDisplacementCanDigBackAfterLegacyReturnLimit` — 安全位移可在旧返回限制后挖回
- `safetyInterruptedGoalReplanQuarantinesOldEntryAndRelocatesPhysically` — 安全中断目标重规划隔离旧入口并物理位移
- `safetyPauseRejoinsTrustedTailAndFailsOnlyAfterExactReturn` — 安全暂停重入可信尾部、只在精确返回后失败
- `safetyReturnHardcapSurvivesPauseAndResume` — 安全返回硬上限在暂停恢复后存活
- `safetyReturnStallLeaseFailsBeforeHardcap` — 安全返回停滞租约在硬上限前失败
- `satisfiedMissionStillRestoresReturnDebtBeforeCompleting` — 已满足任务仍先恢复返回债务再完成
- `timeoutWithRequestedNetDeliveryCompletesOnlyAfterExactReturn` — 有请求净交付的超时只在精确返回后完成
- `unsafeRecordedLandingIsSkippedAndReturnStillCompletes` — 不安全记录落点被跳过、返回仍完成
- `unsupportedAscendingWaypointGetsPhysicalSupportBeforeExactReturn` — 无支撑上升航点在精确返回前获得物理支撑
- `unsupportedExactEntryUsesTwoPhysicalPillarsInsteadOfSnapping` — 无支撑精确入口用两根物理柱子而非吸附
- `unsupportedNaturalSlopeRotatesToSupportedStoneStair` — 无支撑自然坡旋转到有支撑石阶

#### 16. EmergencyShelterAtomicRecoveryGameTests（17 个）
**主题**：庇护所有序建造和密封治疗事务的物理回归。

- `adjacentSecondShelterReusesResidualRoofWithoutBlockedSupportJump` — 相邻第二个庇护所复用残余屋顶、不做被堵支撑跳跃
- `aiEnabledClosePressureUsesOneStrikeAndLowHealthBotSurvives` — AI 启用近距离压力一击、低血机器人存活
- `buildTimeEdgeCorrectionPlacesWallInSameTick` — 建造期边缘修正同 tick 放墙
- `foodNineteenWaitsForNaturalHealingWithoutEating` — 食物 19 时等待自然治疗不进食
- `fourSidedPressureForcesPhysicalExitOnlyAfterGlobalDeadline` — 四面压力只在全局截止后强制物理退出
- `leafCanopyStillUsesSurfaceNightHold` — 树叶树冠仍使用地表夜晚保持
- `lowHealthShelterConsumesBackpackFoodAndHealsBeforeOpening` — 低血庇护所消耗背包食物并在开门前治疗
- `meleeForbiddenOccupiedEgressUsesAlternateOwnedExit` — 近战禁止的占用出口用替代自有出口
- `missingResealBlockCannotPublishTerminalInsideShelter` — 缺失重封方块不能在庇护所内发布终点
- `movingEdgeAnchorSettlesBeforeEnvelopePlacement` — 移动边缘锚在包络放置前稳定
- `observedHostileAtHeadPortIsResealedBeforeFootDoorOpens` — 头部端口可见敌对者先重封再开脚部门
- `occupiedCenteredAabbRejectsSameCellCorrection` — 被占用的居中 AABB 拒绝同格修正
- `persistentHostileGetsOneStrikeThenForcesPhysicalExit` — 持续敌对者获得一击后强制物理退出
- `sealedShelterReopensOwnedDoorBeforeEnvironmentalFailure` — 封闭庇护所在环境失败前重开自有门
- `shallowOverhangStillUsesSurfaceNightHold` — 浅悬垂仍使用地表夜晚保持
- `surfaceShelterStaysSealedUntilDaylight` — 地表庇护所保持封闭直到天亮
- `waterRescueAndBodyFluidRejectFixedShelterAdmission` — 水中救援和体液状态拒绝固定庇护所准入

#### 17. EmergencyShelterMaterialSchedulingGameTests（7 个）
**主题**：紧急木材材料与安全任务替换边界的实时回归。

- `criticalCreeperWithoutRouteOrMaterialsRetainsOneSafetyOwner` — 无路线无材料的危急苦力怕只保留一个安全所有者
- `emergencyShelterSupersedesSafetyEvadeWithoutNestingPauseFrame` — 紧急庇护所取代安全躲避、不嵌套暂停帧
- `emergencyWoodNeverAuthorizesPermanentMiningBarricade` — 紧急木材绝不允许永久采矿路障
- `genericThreatSupersedesNonDefenseSafetyWithoutNestingMission` — 通用威胁取代非防御安全、不嵌套任务
- `mixedWoodFallbackBuildsHoldsAndPhysicallyExitsWithDirtFirst` — 混合木回退先建墙再以泥土优先物理退出
- `oneBlockCannotDispatchDoomedShelterOrGrowPauseStack` — 一个方块不能派发注定失败的庇护所或增长暂停栈
- `trappedFightbackReplacesNonDefenseSafetyWithoutNestingMission` — 被困反击取代非防御安全、不嵌套任务

#### 18. GatherPickupGameTests（6 个）
**主题**：Gather 物理掉落事务的严格生存回归。

- `outOfReachRetryCannotRenewHarvestDeadline` — 够不到的失败重试不能续期收获期限
- `reachableHarvestRestartsImmediatelyAfterSafetyPause` — 可达收获在安全暂停后立即重启
- `realMissRetriesNearbyResourceBeforeRegionalRoam` — 真未命中先重试附近资源再区域游荡
- `repeatedSafetyResumeCannotRenewHarvestDeadline` — 重复安全恢复不能续期收获期限
- `safetyDisplacementReselectsInsteadOfMiningRemoteTarget` — 安全位移重新选择而非挖远处目标
- `vanillaPickupStatSurvivesConcurrentLogConsumption` — 原版拾取统计在并发日志消耗下存活

#### 19. HuntCrossRegionGameTests（24 个）
**主题**：证明严格狩猎能跨初始空白感知区并收集物理战利品。

- `boundedHuntWalksToPreyOutsideInitialPerception` — 有界狩猎走向初始感知外的猎物
- `closedReceiptDoesNotPoisonSuccessorHunt` — 封闭收据不毒化后续猎杀
- `creditedFireAspectKillWithoutRawMeatReturnsToAcquire` — 无生肉的火附加击杀计分后返回获取
- `distantPreyIsHuntedAcrossOpenGround` — 远处猎物跨开阔地被猎杀
- `distantPreySightWidensRangeButStillRequiresLineOfSight` — 远处猎物视野扩大范围但仍需视线
- `externallyKilledTargetNeverCreatesPickupDebt` — 外部击杀目标绝不产生拾取债务
- `freshHuntAcceptsFactualHighSurfaceAndStartsAcquiring` — 新猎杀接受事实高地表并开始获取
- `freshHuntRejectsSkyVisibleDeepMineAsSurfaceAnchor` — 新猎杀拒绝天光可见深矿作为地表锚
- `killedPreyDropInOneWayPitFailsWithoutFollowingIt` — 单向坑中猎物掉落失败且不跟随
- `movingPreyIsRetargetedOnSafeSurfaceAndPhysicallyCollected` — 移动猎物在安全地表重定向并物理收集
- `nearDeadlineRestoreDoesNotRefreshBoundDebt` — 临近截止的恢复不刷新绑定债务
- `observedWoolPickupTriggersPhysicalRecoveryOfMissedMutton` — 观察到的羊毛拾取触发遗漏羊肉物理恢复
- `oldNearbyRawDropCannotPoisonFreshKillTransaction` — 旧附近生肉掉落不能毒化新猎杀事务
- `preyApproachProofDigsNearLevelThroughObstacles` — 猎物接近证明近层挖穿障碍
- `rejectedCompassFanRotatesOntoReversibleRidge` — 被拒罗盘扇旋转到可逆山脊
- `rememberedKillCellRoutesAroundNewOccludingWall` — 记住的击杀格绕开新遮挡墙
- `replanShrunkQuotaStillSettlesOpenPickupDebt` — 缩小配额的重规划仍结算开放拾取债务
- `restoredPickupCollectsTheSameBoundDrop` — 恢复的拾取收集同一绑定掉落
- `satisfiedQuotaReturnsToSurfaceBeforePublishingCompletion` — 满足配额先回地表再发布完成
- `surfaceRoamRejectsOneWayDropPocket` — 地表游荡拒绝单向掉落岩腔
- `unloadedTargetIsReacquiredInsteadOfInventingPickupDebt` — 未加载目标重新获取而非编造拾取债务
- `vanillaPickupStatSettlesDebtAfterInventoryMeatIsConsumed` — 背包肉消耗后原版拾取统计结算债务
- `visiblePreyBelowMissionSurfaceFloorIsNeverPursued` — 任务地表以下可见猎物绝不追击
- `waterRescueDoesNotImmediatelyRetargetSamePrey` — 水中救援不立即重定向同一猎物

#### 20. MiningHostileRecoveryGameTests（7 个）
**主题**：敌对洞穴开口下保留挖矿任务的实时回归。

- `blindStripRotatesAtUnsupportedCaveLipBeforeFalling` — 盲目条带在无支撑洞穴边缘旋转、不坠落
- `markerOnlyRerouteRejectsStandableGeometricReverseWithoutMutation` — 仅标记重路由拒绝可站立几何反转、无变更
- `oneBlockCannotCommitATwoCellMiningBarricade` — 一个方块不能提交两格采矿路障
- `oreDigRetreatsAndPermanentlyBarricadesFourHostiles` — 挖矿撤退并永久路障四个敌对者
- `pausedWorkOnlyResumesAfterThreatAndDamageClear` — 暂停工作只在威胁和伤害清除后恢复
- `survivalGuardPausesMissionInstanceInsteadOfFailingIt` — 生存守卫暂停任务实例而非失败
- `unmarkedStraightLegRejectsWrongSideAndOwnsItsFrontBarricade` — 未标记直支线拒绝错误侧并拥有其前路障

#### 21. MiningServiceResourceGameTests（66 个）
**主题**：地下工具与安全食物服务的"关闭即失败"实时覆盖（仓库、黑曜石策略、封堵/处理事务）。

- `boundary63AcceptsExactlyOneUsableTargetBreak` — 63 边界接受正好一次可用目标破坏
- `boundaryZeroWorstCaseRepairLeavesRetryCushionUsable` — 零边界最坏修复留下可用重试余量
- `captureEmptyLedgerMoverRestartsReturnAndFailsOnlyAfterDoubleSeal` — 捕获空账本移动者重启返回、只双封后失败
- `committedDisposalRestoreWaitsPastPickupDelayAndSealsWithoutRedrop` — 已提交处理恢复越过拾取延迟并封堵不重掉
- `committedSettlePauseMoverResumeReturnsAndSealsBothMouthCells` — 已提交稳定暂停移动者恢复返回并封双口格
- `consecutiveSameFaceDisposalsUseIncrementalSinkBaseline` — 连续同面处理使用递增下沉基线
- `depotWithdrawsSafeFoodAndLeavesDangerousFoodUntouched` — 仓库提取安全食物、不碰危险食物
- `disposalAdmissionCentersResidualOreWalkBeforePublishingOpenDebt` — 处理准入在发布开放债务前居中残余矿石行走
- `disposalOreSealLossTerminatesWithinPocketRecoveryWindow` — 处理矿石封堵丢失在岩腔恢复窗口内终止
- `disposalPocketPreservesBothOreSidesAndFailsAfterDoubleSeal` — 处理岩腔保留两侧矿石、双封后失败
- `disposalPocketPreservesObservedOreAndRestartsThroughOppositeSide` — 处理岩腔保留可见矿石并从对侧重启
- `fullHungerAndRawMeatDoNotBypassSafeReserve` — 满饥饿和生肉不能绕过安全储备
- `fullInventoryReusedPocketFreesAStackBeforeCollectingOpeningSpoil` — 满背包复用岩腔先腾一叠再收集开口溢出物
- `localCraftsTunnelingToolsWithoutDepot` — 无仓库就地合成挖掘工具
- `lowerOnlySealRestartClosesHeadThenFailsLedgerVisibility` — 仅下层封堵重启先闭头再失败账本可见性
- `malformedCheckpointFailsClosed` — 格式错误检查点安全失败
- `naturalOpenPocketWithoutHeadSupportSealsFloorFirst` — 无头部支撑的自然开放岩腔先封地面
- `nearlyBrokenTunnelingToolsDoNotBypassDurabilityService` — 近破挖掘工具不能绕过耐久服务
- `noDepotThreeFreeSlotsReclaimsDeadPicksAndJunkBeforeService` — 无仓库三空槽先回收死镐和垃圾再服务
- `nonWhitelistedNaturalWorkFaceSpoilCannotConsumePromisedSlot` — 非白名单自然工作面溢出物不能消耗承诺槽
- `obsidianDepotPreservesDiamondBlackstoneAndSafetySupplies` — 黑曜石仓库保留钻石、黑石和安全物资
- `obsidianDepotReplenishesMixedEmergencyBlocksToSixteen` — 黑曜石仓库补充混合紧急方块到 16
- `obsidianPolicyAcceptsNineRawDurability` — 黑曜石策略接受 9 原始耐久
- `obsidianPolicyRejectsEightRawDurability` — 拒绝 8 原始耐久
- `obsidianPolicyRejectsMissingRepairSticksBeforeConsumingStone` — 消耗石头前拒绝缺失修复木棍
- `obsidianPolicyRoundTripsAndCannotRestoreAsDefaultOrePolicy` — 往返一致且不能恢复为默认矿石策略
- `obsidianPolicyWillNotSpendTheLastSixteenStoneLikeBlocks` — 不花费最后 16 个类石头方块
- `obsidianPreflightFailsTypedWithoutCarriedCraftingTable` — 无随身工作台时类型化失败
- `obsidianPreflightFailsTypedWithoutItsWaterBucket` — 无水桶时类型化失败
- `obsidianPreflightUsesItsOwnProfileAndValidatesTheExactKit` — 用自身档案并验证精确装备包
- `obsidianServiceFailsTypedWhenEmergencyBlocksCannotReachSixteen` — 紧急方块到不了 16 时类型化失败
- `openGeometryDebtMoverRestartsReturnAndFailsOnlyAfterDoubleSeal` — 开放几何债务移动者重启返回、只双封后失败
- `openRetryMarkerAtHardBudgetBecomesTerminalAndCannotReroute` — 硬预算开放重试标记成终点、不可重路由
- `pocketCheckpointCountsAndPhaseAuthorityAreStrictlyBounded` — 岩腔检查点计数和阶段权限严格有界
- `preBaselineCapturePauseMoverRestartReturnsInCaptureAndCompletes` — 预基线捕获暂停移动者重启返回捕获并完成
- `preservedOreFailureKeepsPocketIdentityWhenSinkEntityRemains` — 保留矿石失败在汇实体存在时保持岩腔身份
- `rareBoundary8AcceptsExactResourceHorizonAndRepairsChannel` — 稀有 8 边界接受精确资源地平线并修复通道
- `rareBoundary8CrowdedDepotReservesTheWholeRefillPeak` — 拥挤仓库保留整个补充满点
- `rareBoundary8PhysicallyWithdrawsMissionHorizonFromDepot` — 物理从仓库提取任务地平线
- `rareBoundary8RejectsOneFoodBelowHorizonBeforeRepair` — 修复前拒绝地平线以下一个食物
- `rareBoundary8RejectsOneStickBelowRepairHorizon` — 拒绝修复地平线下一个木棍
- `rareBoundary8RejectsOneTorchBelowHorizonBeforeRepair` — 拒绝地平线下一个火把
- `rareDescentKitFullInventoryRetiresOnlyCheapPicksThenMinesDiamond` — 稀有下降装备包满背包只退役便宜镐再挖钻石
- `rareDescentKitRejectsForgedSchemaAndIncompleteDoneCheckpoints` — 拒绝伪造 schema 和不完整完成检查点
- `rareDescentKitRejectsOwnerPositionDifferentFromCheckpointDepot` — 拒绝与检查点仓库不同的所有者位置
- `rareDescentKitRestoresSchema7OpenAlcoveCheckpoint` — 恢复 schema7 开放壁龛检查点
- `rareDescentKitWorldChestBeforeCommitRestoresWithoutDoubleSpend` — 提交前世界箱子恢复不双花
- `rareServiceSchema6PinsMissionTargetAndBoundary` — 稀有服务 schema6 钉住任务目标和边界
- `rareServiceUsesLocalPocketWithoutTouchingRemoteOwnedDepot` — 用本地岩腔不碰远程自有仓库
- `restoredCaptureEmptyLedgerSealsAtHardWindow` — 恢复捕获空账本在硬窗口封堵
- `restoredHardBudgetCannotBeResetByRestart` — 恢复硬预算不能被重启重置
- `restoredOpenClearZeroWithFactuallyBrokenEntrySealsAtHardWindow` — 恢复开放清零且事实破损入口在硬窗口封堵
- `sealedOldPocketAlternateStartFailureLeavesValidNonPocketCheckpoint` — 密封旧岩腔交替启动失败留下有效非岩腔检查点
- `sealPhaseTrackedEscapeFailsTypedAndCannotHideBehindNearerSpoil` — 封堵阶段被追踪逃离类型化失败、不能藏在更近溢出物后
- `sealRetryMarkerAtHardBudgetBecomesTerminalAndCannotPingPong` — 硬预算封堵重试标记成终点、不能乒乓
- `secondPocketEntryOreRetiresOnlyAfterVisibleDoubleClosure` — 第二岩腔入口矿石只在可见双重闭合后退役
- `secondPocketUpperOreRetiresOnlyAfterVisibleDoubleClosure` — 第二岩腔上层矿石同上
- `serviceReturnsToExactSavedWorkFace` — 服务回到精确保存工作面
- `settlePhaseInFlightTrackedEntityCannotBeImpersonatedAndTimesOut` — 稳定阶段飞行追踪实体不能被冒充并超时
- `settleTimeoutSealsBothMouthCellsBeforeTypedFailure` — 稳定超时在类型化失败前封双口格
- `straddlingTrackedItemMustEnterRawSinkBeforePresealStability` — 跨格追踪物品必须先进入原版汇再预封稳定
- `terminalPreBaselineReturnCheckpointRestoresSealsAndFailsOriginalReason` — 终点预基线返回检查点恢复封堵并以原始原因失败
- `thirtyTwoObsidianServiceHorizonFundsAllFourWorstCaseRepairs` — 32 黑曜石服务地平线支撑全部四次最坏修复
- `twoUnsafeOpenCavesSealOnceEachAndFailWithoutPingPong` — 两个不安全开放洞穴各封一次、不乒乓失败
- `unreachableUnsealedReturnFailsBoundedlyAndKeepsRestartableDebt` — 不可达未封返回有界失败、保持可重启债务
- `unsafeOpenCaveSealsThenUsesOppositeDisposalPocket` — 不安全开放洞穴先封再用对侧处理岩腔

#### 22. MiningTorchToolRestoreGameTests（2 个）
**主题**：满背包下副手火把提升后恢复活动挖矿工具的证明。

- `descendTorchAttemptRestoresActiveStonePick` — 下降火把尝试后恢复活动石镐
- `oreDigTorchAttemptRestoresActiveChannelPick` — 挖矿火把尝试后恢复活动通道镐

#### 23. OreDigPickupGameTests（88 个，全套件最大）
**主题**：OreDig 物理目标掉落账本的实时严格生存回归（分支/游标/工作姿态/封堵/预算/重启）。

- `activeBreakCancelsWhenItsOnlySurplusSupportDisappearsBeforeRestart` — 活动破坏在其唯一富余支撑消失时取消再重启
- `adjacentCoalOverFiveDeepShaftIsCaughtBeforeDeepFall` — 五格深竖井上的相邻煤在深坠前被拦截
- `airborneDropWaitsForLandingAndUsesNaturalRouteWithoutPillar` — 空中掉落物等待落地、用自然路线不竖柱
- `alignedRichZonePathDoesNotSpendBlindCursor` — 对齐富矿区路径不消耗盲目游标
- `allObservedDangerousBranchesFailTypedAndRestartWithoutBudgetReset` — 全部可见危险巷道类型化失败、重启不重置预算
- `blindBranchDoesNotSealOrMineItsDirectHeadWater` — 盲目巷道不封堵不挖其正头水
- `blindBranchSealsOneHeadSideFluidPerTickAcrossRestart` — 盲目巷道跨重启每 tick 封一个头侧流体
- `blindBranchSealsVisibleSideFluidAndKeepsItsExactCursor` — 封可见侧流体并保持精确游标
- `blindStripFailsFiniteWhenOnlyOldCorridorsRemain` — 只剩旧走廊时盲目条带有穷失败
- `blindStripPersistsFreshLateralDetourAcrossCheckpoint` — 盲目条带跨检查点坚持新横向绕路
- `blockedBodyRetreatImmediatelyPublishesMarkerFreeRestart` — 身体撤退被堵立即发布无标记重启
- `bonusOreMinesSideWallWithoutRemovingCurrentSupport` — 奖励矿石挖侧墙不拆当前支撑
- `channelToolExhaustionFailsBeforeBlacklistingOrIronUse` — 通道工具耗尽先失败、不拉黑不用铁
- `channelToolFailureReportsTheBlockedOreTier` — 通道工具失败报告被堵矿石等级
- `collapsedLateralDetourTriesRemainingFreshSideWithoutClosingLeg` — 坍塌横向绕路试剩余新侧、不关支线
- `completedBatchPublishesZeroBudgetSuccessor` — 完成批次发布零预算后继
- `consecutiveEyeHeightDiamondsWaitForEachPhysicalPickup` — 连续眼高钻石等待每次物理拾取
- `denseBonusOreCannotStarveActiveChannelBlock` — 密集奖励矿石不能饿死活动通道方块
- `diagonalEyeHeightOreWaitsForCardinalWorkPose` — 对角眼高矿石等待主向工作姿态
- `diagonalPhysicalPickupClearsDebtAndContinuesMining` — 对角物理拾取清债继续挖
- `directLavaBoundaryAndRestartRetainOreDigOwnership` — 直接岩浆边界和重启保留挖矿所有权
- `distantCommittedCursorRebasesToTheCurrentPhysicalBranch` — 远处已提交游标重基到当前物理巷道
- `distantOpenCursorStillRetainsTheExactSavedFace` — 远处开放游标仍保留精确保存面
- `elevatedDropUsesLowerAdjacentStandWithoutPillar` — 抬升掉落物用更低相邻站立点、不竖柱
- `exactProtectedReserveRejectsOpenShaftOreBeforeBreakAndRestart` — 精确受保护储备拒绝开放竖井矿石、再破坏重启
- `exactTunnelStepAndQueuedHighWorkPoseArePhysicallyRecovered` — 精确隧道步和排队高工作姿态被物理恢复
- `factualCornerLavaUsesUntriedReverseAndRestartsExactly` — 事实角落岩浆用未试反转并精确重启
- `footLevelDiamondDropIsRecoveredByWalkingIntoItsCell` — 脚层钻石掉落走进其格恢复
- `freshStripUsesUpperEscapeBeforeMiningUnsupportedLateralSupport` — 新条带先上层逃生再挖无支撑横向支撑
- `gravityClosedLegRetainsRearAcrossImmediateGravitySuccessorAndRestart` — 重力闭合支线跨即时重力后继和重启保留后方
- `headSideFluidCannotConsumeExactProtectedReserve` — 头侧流体不能消耗精确受保护储备
- `hiddenDiagonalDropUsesExactLRouteWithoutChangingCornerWalls` — 隐藏对角掉落用精确 L 路线不改角落墙
- `hiddenLowerTransitionRemainsUnknownWhetherBlockedOrOpen` — 隐藏下层过渡保持未知（无论堵或开）
- `hiddenSideFluidAndHiddenStoneOpenTheSameSealedChannel` — 隐藏侧流体和隐藏石头打开同一密封通道
- `higherTierNonTargetOreClosesBlindBranchWithoutBreakingIt` — 更高等级非目标矿石关闭盲目巷道、不破坏
- `legacyOpenCheckpointWithoutDeliveredLedgerFailsClosed` — 无交付账本的旧开放检查点安全失败
- `longRareTailRetainsMissionIdentityAcrossRestartAndServiceDebits` — 长稀有尾部跨重启和服务扣款保留任务身份
- `lowerFloorCoalOverOpenShaftGetsPhysicalDropSupport` — 开放竖井上低层煤获得物理掉落支撑
- `lowerFloorOreClearsSweptPickupEgressBeforeBreaking` — 低层矿石先清除清扫拾取出口再破坏
- `lowerStepFluidGateRejectsUnobservableNeighbourInStrictMode` — 低步流体门严格模式拒绝不可观察邻居
- `malformedCheckpointFailsClosed` — 格式错误检查点安全失败
- `minedOpenDropBodyRetainsFactualRearAcrossTicksAndRestart` — 挖出开放掉落体跨 tick 和重启保留事实后方
- `nearbyRestartPositionCannotReplaceTheExactSavedFace` — 附近重启位置不能替换精确保存面
- `ordinaryFullInventoryFailsWithoutMutatingInventoryOrCreatingDrops` — 普通满背包失败不变背包不产生掉落
- `partialDeliveryRebasesOnlyTheTransientStallWindow` — 部分交付只重基瞬态停滞窗口
- `pedestalLandedDropIsPhysicallyRecovered` — 基座落地掉落被物理恢复
- `pendingPickupCheckpointResumesBeforeAnyNewMining` — 待定拾取检查点在任一新挖掘前恢复
- `pendingPickupGravityRetreatDoesNotBecomeBlindBranchTerminal` — 待定拾取重力撤退不成为盲目巷道终点
- `progressedHigherTierBoundaryPublishesSuccessorAndSurvivesRestart` — 推进更高等级边界发布后继并跨重启存活
- `progressedOpenDropLipRetreatsOneFactualStepAndRestartsSuccessor` — 推进开放掉落唇沿事实退一步、重启后继
- `progressedOpenDropWithUnsafeRearFailsWithoutMovingOrResettingBudget` — 不安全后方推进开放掉落失败、不动不重置预算
- `progressedStripClosesVisibleGravityLegAndRestartsSuccessor` — 推进条带关闭可见重力支线、重启后继
- `queuedHighOreWithoutWorkPoseStaysIntactAndSearchContinues` — 无工作姿态排队高矿石保持完整、搜索继续
- `queuedOreBeyondVanillaReachIsReleasedWithoutCursorLivelock` — 超出原版触及的排队矿石被释放、无游标活锁
- `rareDarkBranchWithoutTorchFailsWithItsExactEpoch` — 无火把稀有黑暗巷道以其精确纪元失败
- `rareFullInventoryFailsWithoutCreatingOpenRearDrops` — 稀有满背包失败不产生开放后方掉落
- `rarePartialDeliveryRestartOnlyMinesTheLogicalBatchRemainder` — 稀有部分交付重启只挖逻辑批次剩余
- `rareTorchEpochStopsAtFortyBeforeExtendingDarkBranch` — 稀有火把纪元在扩展黑暗巷道前于 40 停止
- `realBlindWalkerConsumesExactlyOneCursorStep` — 真实盲目行走者正好消耗一个游标步
- `rearRetreatKeepsTurnMarkerForImmediateSuccessorDrop` — 后方撤退为即时后继掉落保留转弯标记
- `rememberedHighWorkPoseCapacityEvictsDeterministicFarthestUnpinnedOwner` — 记住高工作姿态容量逐出确定性最远未钉所有者
- `rememberedHighWorkPoseCheckpointRejectsForgedEntries` — 记住高工作姿态检查点拒绝伪造条目
- `rememberedHighWorkPoseOwnerLeaseExpiresAcrossSuccessfulReplans` — 所有者租约跨成功重规划过期
- `restartAtFullyDeliveredOpenBatchSettlesDebtWithoutBreakingAnotherOre` — 完全交付开放批次处重启结算债务、不挖另一矿石
- `restartCannotResetHardBudget` — 重启不能重置硬预算
- `restartCannotResetNoProgressBudget` — 重启不能重置无进度预算
- `restartKeepsUnknownActiveTargetWithoutInventingPickupDebt` — 重启保留未知活动目标、不编造拾取债务
- `restartRejectsIntactHighActiveBreakButPreservesGoneBreakDebt` — 重启拒绝完整高活动破坏但保留消失破坏债务
- `restoredObservedHighWorkPoseRoutesWithoutDigging` — 恢复可见高工作姿态路线、不挖掘
- `sameColumnDropBelowMinerUsesPhysicalDescent` — 矿工下方同列掉落用物理下降
- `sameOriginFluidCascadeBacktracksOneObservedStepAndRestarts` — 同原点流体级联回退一个可见步并重启
- `sameOriginWaterThenLavaTriesUnvisitedReverseAndSurvivesRestart` — 同原点先水后岩浆试未访问反转并跨重启存活
- `scanDelayCheckpointRestoresSaferRearBeforeReplayingBranch` — 扫描延迟检查点在重放巷道前恢复安全后方
- `stairDescentImmediatelyPublishesMarkerFreeRestartWithoutReverse` — 阶梯下降立即发布无标记重启、不反转
- `stairDescentSkipsUnsupportedPreferredDirection` — 阶梯下降跳过无支撑首选方向
- `stripLightingDoesNotConsumeToolServiceSticks` — 条带照明不消耗工具服务木棍
- `stripPhysicallyRetreatsWhenGravityReoccupiesItsHead` — 重力重占头部时条带物理撤退
- `supportOreRejectsElevatedRelocationOutsideBreakEnvelope` — 支撑矿石拒绝破坏包络外抬升位移
- `survivalGuardPauseDisplacementRestoresUnpublishedRearAndCursor` — 生存守卫暂停位移恢复未发布后方和游标
- `targetApproachMovementDoesNotSpendBlindBranchProjection` — 目标接近移动不消耗盲目巷道投影
- `targetApproachUsesOnlyObservedSupportedOneBlockLowerStep` — 目标接近只用可见支撑低一格步
- `targetLowerStepRejectsObservedFluidNeighbourInStrictMode` — 目标低步严格模式拒绝可见流体邻居
- `threeAboveOreRequiresReachableHighWorkPoseForNaturalPickup` — 三格上矿石需要可达高工作姿态自然拾取
- `twoAboveCardinalOreUsesDropShaftWorkPoseForNaturalPickup` — 两格上主向矿石用掉落竖井工作姿态自然拾取
- `unreachableVisibleLastSeenFallsBackToReachableBreakCell` — 不可达可见最后位置回退到可达破坏格
- `visibleLavaRotatesTheBranchInsteadOfAssigningImpossibleEvade` — 可见岩浆旋转巷道而非派发不可能躲避
- `watcherDoesNotMistakeAVisibleLavaPoolForTheActiveBranchCell` — 监视器不把可见岩浆池误认为活动巷道格
- `zeroMovementHigherTierBoundaryStillFailsClosed` — 零移动更高等级边界仍安全失败

#### 24. SmeltFurnacePlacementGameTests（4 个）
**主题**：复现 DigDown 回地表后"首个无支撑空气熔炉放置"的问题。

- `craftsLocalFurnaceInsteadOfChasingFarRememberedSurfaceFurnace` — 就地合成熔炉而非追远处记住的地表熔炉
- `craftsLocalFurnaceWhenRememberedSurfaceFurnaceIsBeyondLookupRadius` — 记住熔炉超出查找半径时就地合成
- `skipsUnsupportedStairMouthAndCooksOnSupportedSide` — 跳过无支撑楼梯口、在支撑侧烹饪
- `unsupportedAirRetryCannotReplaceActiveClearingPickaxe` — 无支撑空气重试不能替换活动清障镐

#### 25. SurfaceWaterRecoveryGameTests（12 个）
**主题**：实地证明无客户端假玩家通过相邻物理动作离开浅水。

- `connectedShoreBeatsAnUnneededVerticalAirStroke` — 连通岸边胜过无谓的垂直空气挥击
- `descendDoesNotRetrySafetyRejectedLanding` — 下降不重试安全拒绝的落点
- `descendFreshEntryRelocationCannotCutADiagonalCorner` — 下降新入口位移不能切对角角落
- `descendHorizontalFallbackNeverMinesItsOwnedWaterSeal` — 下降水平回退绝不挖自有水封
- `descendNeverMinesTheWaterSealItJustPlaced` — 绝不挖刚放置的水封
- `descendRelocatesFromAShorelineDeadStarBeforeMutating` — 从岸边死胡同先位移再变更
- `descendSealsIngressAndHandsOffADryOreLayer` — 封堵入口并交接干燥矿石层
- `digDownPreservesItsWaterSealAndProtectedWorkstation` — 下挖保留水封和受保护工作站
- `emergencyVerticalStepRequiresLowAir` — 紧急垂直步需要低空气
- `horizontalFallbackNeverMinesTheOwnedWaterSeal` — 水平回退绝不挖自有水封
- `proactiveRescueStepsOntoDryGround` — 主动救援踏上干地
- `rescueRoutesAroundAWallEvenWhenTheFirstStepMovesAwayFromShore` — 救援绕过墙、即使第一步远离岸边

#### 26. UndergroundSafetyGameTests（26 个）
**主题**：有界地下安全契约的确定性回归。

- `adjacentFakePlayerStepRejectsAnEntityOccupiedLanding` — 相邻假玩家步拒绝实体占用落点
- `defensiveCombatDoesNotDescendTowardAHostileBelowItsAnchorFloor` — 防御战斗不向锚地板下敌对者下降
- `descendLateralBudgetResetsOnlyAfterAConfirmedLowerLanding` — 下降横向预算只在确认更低落点后重置
- `descendLateralDetourKeepsHeadingInsteadOfReversingInTwoCellLoop` — 横向绕路保持朝向、不两格循环反转
- `descendLateralDetourNeverReplaysATraversedDirectedEdge` — 横向绕路绝不重放已走过有向边
- `descendLateralDetourRejectsUnsupportedAirShaft` — 横向绕路拒绝无支撑空气竖井
- `descendMinesItsOccupiedHeadWhenNoPhysicalRetreatLandingExists` — 无物理撤退落点时挖被占头部
- `descendNeverCompletesBelowTarget` — 绝不完成低于目标
- `descendPhysicallyRetreatsWhenGravelReoccupiesItsHeadInStrictSurvival` — 严格生存中砂砾重占头部时物理撤退
- `descendRestoresUpperDetourRetreatsDownToPersistedOrigin` — 恢复上层绕路撤退到持久化原点
- `descendRollsBackCollapsedSameLevelDetourAndRetriesFromOrigin` — 回滚坍塌同层绕路、从原点重试
- `displacedPartialShelterReleasesItsStaleAnchorWithoutSpinning` — 位移部分庇护所释放陈旧锚、不旋转
- `emergencyShelterBuildsAFoundationFromASingleSupportedLanding` — 紧急庇护所从单个支撑落点建地基
- `emergencyShelterCannotCompleteBeforeRoofAndAllSidesArePhysicallySealed` — 屋顶和所有侧物理密封前不能完成
- `emergencyShelterRejectsAnUnstableOriginBeforeWorldMutation` — 变更世界前拒绝不稳定原点
- `emergencyShelterRejectsInsufficientFoundationBudgetBeforeWorldMutation` — 变更前拒绝不足地基预算
- `partialHeightDirtPathTopDoesNotTriggerSuffocation` — 部分高度泥土路径顶部不触发窒息
- `partialShelterFailureOpensOwnedDoorwayBeforePublishingFailure` — 部分庇护所失败在发布失败前开自有门
- `shelterExitNeverMinesPreExistingWorldBlocks` — 庇护所出口绝不挖已有世界方块
- `shelterMaterialLossBeforeFirstPlacementFailsWithoutInventingExitDebt` — 首次放置前材料丢失失败、不编造出口债务
- `shelterOpensItsOwnedDoorBeforeFailingWhenExitSupportDisappears` — 出口支撑消失时先开自有门再失败
- `shelterSkipsFoundationHiddenBelowAPreExistingTunnelWall` — 跳过已有隧道墙下隐藏地基
- `standableCornerOverlapRecentersOnceAndRetiresStaleRoute` — 可站立角落重叠只居中一次并退役陈旧路线
- `standableStepRejectsUnsupportedAndWaterCells` — 可站立步拒绝无支撑和水格
- `strictSuffocationDenialFallsBackToAdjacentPhysicalExit` — 严格窒息拒绝回退相邻物理出口
- `unreachableDropRecoveryTypedFailsWithinItsNoProgressBudget` — 不可达掉落恢复在其无进度预算内类型化失败

---

## 4. 覆盖维度小结

按模块看，587 个测试主要覆盖五大类行为：

1. **挖矿（OreDig / StripMine / 通道工具）**：约 290 个 —— 分支旋转、游标恢复、工作姿态、封堵、预算边界、满背包、稀有纪元、账本字节级精确。
2. **取水 / 下降 / 返回（AcquireWater / Descend / DigDown）**：约 92 个 —— 地表出口判定、物理装水、阶梯交接、返回债务、重启恢复。
3. **安全（EmergencyShelter / DangerWatcher / Creeper / UndergroundSafety）**：约 101 个 —— 低血抢占、苦力怕/末影人/僵尸响应、庇护所原子事务、窒息与出口。
4. **任务 / 目标（GoalPlanner / MiningCheckpoint / CreateObsidian / DeathRecovery）**：约 149 个 —— 钻石/黑曜石远征计划、批次与服务检查点、死亡恢复、计划器注册表依赖。
5. **动作与工具（ActionPack / BuildAction / Offhand / Gather / Hunt / Smelt / ToolRegistry）**：约 63 个 —— 物理吸附、内嵌放置可见性、副手背包路径、拾取事务、跨区狩猎、熔炉放置、命令契约。
