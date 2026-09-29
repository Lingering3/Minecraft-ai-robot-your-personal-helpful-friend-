# 创造模式建筑 + 双模式任务分流设计

> 项目: mc_aiplayer (aibot · Fabric 1.21.3)
> 日期: 2026-09-29
> 状态: 已实现并通过 assemble + compileGametestJava 构建验证(未跑 GameTest)

---

## 1. 目标

1. 机器人 **生存模式与创造模式都能正常对话**,对话信息统一经 **StepFun** 做类型判断:
   普通对话 / 任务型对话(任务型再细分: 建筑、搜集、挖矿、合成、农业畜牧、钓鱼、交易、对战、跟随、休息、囤积)。
2. 任务落地分流(非对话任务):
   | 任务 | 生存模式 | 创造模式 |
   |---|---|---|
   | 建筑任务 | mcaiplayer 回路(模型工具 → 真实建造) | **创造模式回路**: StepFun 选蓝图 → 发蓝图物品 → 玩家预览/旋转/确认 → 伪放置 |
   | 其他任务(搜集/挖矿/合成/对战/… ) | mcaiplayer 回路(照常执行) | 返回消息:**"创造模式暂不支持 XX 任务"** |
3. 创造模式建造 = **伪放置**:无视地形直接替换方块(本地集成服 `setBlockState`),不消耗材料。
4. 位置选择 = **迷你世界蓝图指令式**:给玩家一个蓝图物品,手持预览位置,中键确认,右键旋转,再次中键确认搭建。
5. 蓝图来源:先用 Minecraft 自带 **structure(.nbt)** 拟定几个,后续可继续引入。

---

## 2. 任务分类体系(StepFun intent)

StepFun 分类 payload 扩展为 13 类。每类的生存/创造行为如下:

| intent | 类别 | 典型表述 | 生存模式 | 创造模式 |
|---|---|---|---|---|
| `chat` | 普通对话 | "你好/今天心情如何/现在几点了" | 完整工具对话 | 无工具对话(正常聊天) |
| `build` | 建筑任务 | "给我盖个城堡/搭座桥/修个基地" | mcaiplayer 回路(模型选蓝图建造) | 创造回路: StepFun 选蓝图 → 发蓝图物品 → 玩家确认后伪放置 |
| `mine` | 挖矿任务 | "去挖铁/挖条矿道" | mcaiplayer 回路 | 暂不支持 |
| `gather` | 搜集任务 | "去砍树/收集羊毛/捡掉落物" | mcaiplayer 回路 | 暂不支持 |
| `craft` | 合成任务 | "做把石剑/熔炼铁锭/造个工作台" | mcaiplayer 回路 | 暂不支持 |
| `farm` | 农业畜牧 | "种小麦/养牛/挤奶/收割" | mcaiplayer 回路 | 暂不支持 |
| `fish` | 钓鱼任务 | "去钓鱼" | mcaiplayer 回路 | 暂不支持 |
| `trade` | 交易任务 | "找村民交易/换绿宝石" | mcaiplayer 回路 | 暂不支持 |
| `fight` | 对战任务 | "去打僵尸/守卫基地/打怪" | mcaiplayer 回路 | 暂不支持 |
| `follow` | 跟随移动 | "跟着我/过来/待命/去 x y z" | mcaiplayer 回路 | 暂不支持 |
| `sleep` | 休息照明 | "睡觉/点火把/照亮这里" | mcaiplayer 回路 | 暂不支持 |
| `stockpile` | 囤积补给 | "把东西放进箱子/整理仓库/补给" | mcaiplayer 回路 | 暂不支持 |
| `unknown` | 无法判断 | — | 跳过模型调用,提示换个说法 | 同左 |

建筑子类型(build_kind,由 StepFun 第二个问题判定):
`house / castle / tower / bridge / farm / workstation / gate / statue / temple / unknown`。

对应原项目功能的映射(分类 → 原项目 Task 类):
- 挖矿: OreDigTask / MineTask / StripMineTask / MiningServiceTask
- 搜集: GatherQuotaTask / RecoverDropsTask / StockpileTask / ContainerTask
- 合成: CraftTask / SmeltTask / ResupplyTask / PlaceStationsTask
- 农业畜牧: FarmTask / BreedTask / MilkCowTask / RaidCropsTask / IrrigateTask
- 钓鱼: FishTask
- 交易: TradeTask
- 对战: CombatTask / GuardTask / HuntTask / CreeperDefenseTask / EvadeTask
- 跟随移动: FollowTask / MoveTask / HoldTask
- 休息照明: SleepTask / EatTask / LightAreaTask
- 生存应急: EmergencyShelterTask / DigDownTask / DescendToYTask / LavaEscapeTask / CreateObsidianTask
- 建筑: BuildTask

---

## 3. 双模式分流链路

```
聊天消息
  └─ ChatCaptureListener.register() (@提及 / 触发词 / 单bot默认路由)
       └─ BotAuthorizationGate.authorize → IntentController 控制短语短路
            └─ BrainCoordinator.handleMessage(bot, sender, text)
                 ├─ creativeContext(bot)?  (bot 或 owner 为创造)
                 │    ├─ 是 → handleCreativeMessage
                 │    └─ 否 → StepFun 分类 → handleRecognizedMessage(生存 mcaiplayer 回路)
                 │
                 ├─ handleCreativeMessage: 同样先 StepFun 分类
                 │    ├─ chat      → handleCreativeChat(StepFun 无工具对话)
                 │    ├─ build     → startCreativeBuild(创造建筑回路)
                 │    ├─ 其他任务  → 回复"创造模式暂不支持XX任务"
                 │    └─ StepFun 失败 → 本地关键词兜底(旧行为) → 兜不到按对话
                 └─ startCreativeBuild
                      ├─ BlueprintRecommender.recommend(text, kind)
                      │    StepFun 从 本地蓝图库 + 模型掌握的网上建筑知识 选出最匹配蓝图 id
                      │    (失败回退 BlueprintCatalog.resolve 本地打分)
                      ├─ BlueprintLoader.load(blueprint) 校验可加载
                      ├─ giveBlueprintItem(player, id)    发放蓝图物品(背包)
                      └─ 播报: 中键确认位置 → 右键旋转 → 再次中键开始搭建
```

### 创造建筑回路(蓝图物品交互)

```
玩家手持蓝图物品
  ├─ 客户端每 tick: 计算预览位置(视线射线交点,未锁定)/ 读锁定位置(已锁定)
  ├─ 右键(use键) → 旋转 rotation = (rotation+1)%4,渲染实时反映
  ├─ 中键(第1次) → 锁定预览位置(渲染变橙色)
  └─ 中键(第2次) → ClientPlayNetworking.send(BlueprintBuildC2S{id, anchor, rotation})
       └─ AIBotServerNetworking.handleBlueprintBuild
            ├─ 校验: 玩家创造模式 / 主手持蓝图物品且 id 一致 / bot 存在且创造模式
            ├─ BlueprintLoader.load(id) → BlueprintTransformer.rotate(schema, rotation)
            ├─ GoalExecutor.clear(bot)
            └─ TaskManager.assign(bot, new BuildTask(rotated, anchor, false, flatten=true),
                                  PLAYER_COMMAND "creative_build:item:<id>")
                 └─ BuildTask: SITE(锚点已定) → FLATTEN(伪整地) → BUILD(伪放置)
                      └─ pseudoPlacement = !server.isDedicated() (本地=伪放置)
                      └─ isCreative(bot) → 不检查/不消耗材料
                      └─ 每 tick ≤ aibot.pseudoPlaceRate(默认8, /aibot buildrate 1-256 可调)
                      └─ 完成: StructureVerifier.verify 精确核验(含 AIR)才 complete
```

### 与旧行为差异

- 旧: 创造模式用本地关键词(creativeBuildKind)直接判断 → 自动选址(anchor=null) 立刻伪放置。
- 新: 创造模式同样先经 **StepFun 分类**;建造任务由 **StepFun 选蓝图** → **蓝图物品交互**确定位置/旋转 → 确认后才伪放置。StepFun 失败时保留本地关键词兜底。

---

## 4. 蓝图体系

| 来源 | 位置 | 说明 |
|---|---|---|
| 内置 json 蓝图(5个) | 代码内置 + 游戏目录 `blueprints/*.json` | small_hut(默认)/ hut_5x5 / glass_cabin / watch_tower / simple_bridge |
| 自定义 json 蓝图 | `blueprints/*.json`(自动扫描) | 沿用现有 BlueprintSchema 格式 |
| **structure 结构方块蓝图(新)** | `blueprints/structures/*.nbt` | Minecraft 结构方块导出的文件,自动识别、参与打分、可加载 |

- structure 解析: `NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes())`,
  读 `size`(宽高深) + `palette`(方块名表) + `blocks`(pos/state),转换成 BlueprintSchema 的 placements。
- 结构文件默认不记录空气方块 → 伪放置只替换列出的方块位置。
- 命令:
  - `/aibot blueprint list` — 列出全部候选(id/name/tags)
  - `/aibot blueprint give <id> [player]` — 发放蓝图物品
  - `/aibot blueprint info <id>` — 尺寸/方块数
  - `/aibot blueprint reload` — 重新扫描 structures 目录

---

## 5. StepFun 蓝图选择(BlueprintRecommender)

- 触发时机:**确认是建造任务之后**(StepFun intent=build)。
- 候选 = `BlueprintCatalog.entries()`(内置 + blueprints/*.json + structures/*.nbt),按 tag 数升序取前 60 个。
- Prompt 让 StepFun 模型结合自身建筑知识(含网上常见 Minecraft 建筑风格常识)与本地候选清单,
  **只输出一个蓝图 id**;解析失败回退本地关键词打分 `BlueprintCatalog.resolve`。
- 异步执行(独立守护线程池),不阻塞服务器 tick;API 未配置/调用失败时自动降级。

---

## 6. 使用手册(玩家侧)

1. `/aibot spawn` 生成助手;把 bot 或自己切成创造模式。
2. 对 bot 说:"给我盖个城堡"(任意建筑词)→
   bot 播报并往你背包放一个**蓝图物品**(StepFun 选好的蓝图)。
3. 手持蓝图物品:
   - 世界中出现**绿色线框预览**(跟随视线);
   - 对准位置,**中键** → 锁定(变橙色);
   - **右键** → 旋转 90°(可连按);
   - **再按中键** → 确认搭建,bot 开始伪放置(无视地形直接替换方块,不耗材)。
4. 确认后回到第 2 步再说一次建造即可获得新蓝图;也可直接 `/aibot blueprint give <id>` 指定。

### 生存模式行为不变
- 所有任务(含建造)走原 mcaiplayer 回路(模型工具对话落地);创造模式对非建筑任务明确回复"暂不支持"。

---

## 7. 改动文件清单

新增:
- `intent/StepFunIntentClient.java`(重写: 13 类 intent + 9 类 build_kind)
- `intent/StepFunIntent.java`(新增 isBuild/isChat + promptLine 扩展)
- `blueprint/BlueprintRecommender.java`(StepFun 选蓝图)
- `blueprint/BlueprintTransformer.java`(蓝图旋转)
- `blueprint/StructureImporter.java`(structure .nbt 导入)
- `item/BlueprintItem.java` + `item/BlueprintItems.java`(蓝图物品)
- `network/payload/BlueprintBuildC2S.java`(确认搭建包)
- `command/AIBotBlueprintSubcommand.java`(blueprint 子命令)
- `src/client/.../BlueprintPreviewState.java`(客户端预览状态)
- `src/client/.../BlueprintControls.java`(中键/右键交互)
- `src/client/.../BlueprintPreviewRenderer.java`(世界线框渲染)
- `src/client/resources/assets/aibot/models/item/blueprint.json`
- lang: `zh_cn.json` / `en_us.json` 增加 `item.aibot.blueprint`、`key.aibot.blueprint_confirm`

修改:
- `brain/BrainCoordinator.java`(创造分流重构: StepFun → StepFun 选蓝图 → 发蓝图物品;其他任务返回不支持)
- `blueprint/BlueprintCatalog.java`(纳入 structureEntries;exists 支持 .nbt)
- `task/BlueprintLoader.java`(json 缺失时回退结构文件)
- `network/payload/AIPayloads.java`(注册 BlueprintBuildC2S)
- `network/AIBotServerNetworking.java`(handleBlueprintBuild 落地)
- `command/AIBotCommand.java`(注册 blueprint 子命令)
- `AIBotMod.java`(注册蓝图物品)
- `src/client/.../AIBotClient.java`(注册按键与渲染)

---

## 8. 验证情况

- ✅ `gradlew --no-daemon assemble` BUILD SUCCESSFUL(compileJava / compileClientJava / jar / remapJar)
- ✅ `gradlew compileGametestJava` BUILD SUCCESSFUL(测试源集未被破坏;未运行 GameTest)
- ✅ jar 内含全部新类 + lang + item model;JSON 资源解析通过
- ⚠️ 未做运行时验证(未起游戏):蓝图预览渲染、中键/右键交互、structure 解析需进游戏实测;
  建议先 `/aibot blueprint give small_hut` 自测物品交互,再用结构方块导出一个 .nbt 放入
  `blueprints/structures/` 验证导入。

## 9. 待办/后续

- [ ] 进游戏实测蓝图物品交互(中键/右键/确认)
- [ ] 用结构方块制作首批正式 structure 蓝图
- [ ] "网上资源"目前由 StepFun 模型知识兜底,后续可接入真实结构库下载/检索
- [ ] StepFun key 移除问题(HANDOFF 遗留)仍未处理,发布前必须清理

