# mc_aiplayer + StepFun/StepFun 交接说明

## 当前目标

以 `mc_aiplayer` 作为主体模组，保留它的真实服务端 AI 玩家实体能力，在其上增加：

- 自然聊天触发，不必每次输入 `/aibot brain say ...`
- 生存模式 AI 任务规划
- 创造模式蓝图伪放置建造
- StepFun 对话/大脑调用
- StepFun 仅用于生存模式任务识别

当前项目位置：

```text
C:\Users\jiexu\Documents\Codex\2026-09-27\gen-j\work\mc_aiplayer
```

最新打包 jar：

```text
C:\Users\jiexu\Documents\Codex\2026-09-27\gen-j\outputs\mc_aiplayer-creative-split-pseudobuild-stepfun-personal.jar
```

## 当前模式分层

### 生存模式

生存模式保留原来的完整工作链：

```text
玩家聊天
  -> StepFun 意图识别
  -> StepFun 大脑
  -> ToolRegistry / GoalPlanner / TaskManager
  -> 真实移动、采集、制作、挖矿、建造
```

生存模式下可以继续使用：

- 挖矿
- 采集
- 制作
- 熔炼
- 食物
- 跟随
- 战斗
- 建造
- GoalPlanner 自动备料

### 创造模式

创造模式不再走 StepFun，也不走生存任务规划。

创造模式只支持：

- 普通对话
- 蓝图建造

创造模式流程：

```text
玩家聊天
  -> 本地关键词判断
  -> 如果是建造：BlueprintCatalog 检索蓝图 -> BuildTask 伪放置
  -> 如果是普通聊天：StepFun 对话，但不给工具
  -> 如果是挖矿/采集/制作/战斗/跟随等生存任务：直接拒绝
```

## 已实现功能

### 1. 聊天触发

原本需要：

```mcfunction
/aibot brain say steve 跟我来
```

现在支持：

- 只有一个 bot 且只有一个真人玩家时，普通聊天默认发给该 bot。
- 多个 bot 时，按触发词路由。
- 默认触发词是 bot 名字。
- 仍兼容 `@bot` 形式。

命令：

```mcfunction
/aibot trigger steve 助手
/aibot trigger steve
```

相关文件：

```text
src/main/java/io/github/zoyluo/aibot/brain/ChatCaptureListener.java
src/main/java/io/github/zoyluo/aibot/manager/AIPlayerManager.java
src/main/java/io/github/zoyluo/aibot/command/AIBotCommand.java
```

### 2. Bot 游戏内说话

bot 的 `say` 和任务报告现在会进入游戏聊天栏。

相关文件：

```text
src/main/java/io/github/zoyluo/aibot/brain/BotSpeaker.java
src/main/java/io/github/zoyluo/aibot/brain/BrainCoordinator.java
src/main/java/io/github/zoyluo/aibot/brain/BotReporter.java
```

注意：当前 bot 仍然偏话痨，用户明确说暂时不改。

### 3. StepFun 生存意图识别

StepFun API：

```text
POST http://106.13.186.155:9072/predict
GET  http://106.13.186.155:9072/health
```

用途：

- 只在生存模式使用
- 用于识别 `build / mine / gather / craft / follow / fight / chat`
- build 时还识别 `house / tower / bridge / farm / castle / workstation`

如果 StepFun 失败或低置信度，生存模式会跳过 StepFun，并提示任务识别失败。

相关文件：

```text
src/main/java/io/github/zoyluo/aibot/intent/StepFunIntent.java
src/main/java/io/github/zoyluo/aibot/intent/StepFunIntentClient.java
src/main/java/io/github/zoyluo/aibot/brain/BrainCoordinator.java
```

### 4. StepFun 配置

当前默认大脑配置已切到 StepFun：

```text
baseUrl=https://api.stepfun.com/v1
model=step-3.5-flash
```

代码里有个人测试 key，后续公开仓库前必须移除，改成环境变量或配置文件。

优先读取：

```text
STEPFUN_API_KEY
```

兼容旧变量：

```text
DEEPSEEK_API_KEY
```

相关文件：

```text
src/main/java/io/github/zoyluo/aibot/AIBotConfig.java
src/main/java/io/github/zoyluo/aibot/brain/DeepSeekApiClient.java
```

### 5. 建造蓝图检索

当前 build 检索是本地关键词匹配，不联网。

来源：

- 内置蓝图
- 游戏目录 `blueprints/index.json`
- 游戏目录 `blueprints/*.json`

内置测试蓝图：

```text
small_hut
hut_5x5
glass_cabin
watch_tower
simple_bridge
```

相关文件：

```text
src/main/java/io/github/zoyluo/aibot/blueprint/BlueprintCatalog.java
src/main/java/io/github/zoyluo/aibot/task/BlueprintLoader.java
src/main/java/io/github/zoyluo/aibot/task/BlueprintSchema.java
```

### 6. 本地伪放置

本地单人/集成服：

- `BuildTask` 使用伪放置
- 按蓝图直接 `setBlockState`
- bot 会看向目标方块并摆手
- 整地也直接替换
- 创造模式不检查/消耗材料
- 生存模式伪放置仍检查/消耗材料

Dedicated 服务器：

- 仍使用原来的真实放置逻辑

伪放置速率：

```mcfunction
/aibot buildrate
/aibot buildrate 16
```

默认每 tick 8 个方块，可调范围 1-256。

相关文件：

```text
src/main/java/io/github/zoyluo/aibot/task/BuildTask.java
src/main/java/io/github/zoyluo/aibot/command/AIBotCommand.java
```

## 常用测试命令

生成 bot：

```mcfunction
/aibot spawn steve
```

设置触发词：

```mcfunction
/aibot trigger steve 助手
```

设置伪放置速度：

```mcfunction
/aibot buildrate 16
```

创造模式测试：

```text
助手 盖个玻璃小屋
助手 建一座桥
助手 造个塔
助手 你好
助手 去挖铁矿
```

预期：

- 玻璃小屋 -> `glass_cabin`
- 桥 -> `simple_bridge`
- 塔 -> `watch_tower`
- 你好 -> 普通聊天
- 挖铁矿 -> 创造模式拒绝，因为创造模式只支持对话和建造

生存模式测试：

```text
助手 挖一些铁矿
助手 搞点吃的
助手 盖个小屋
```

预期：

- 先走 StepFun
- 再进入 StepFun/GoalPlanner
- 按生存逻辑执行

## 构建方式

不要跑 `build`，因为它会触发 GameTest。

使用：

```powershell
$env:GRADLE_USER_HOME='C:\Users\jiexu\Documents\Codex\2026-09-27\gen-j\work\.gradle-home-mcaiplayer'
$env:JAVA_HOME='C:\Users\jiexu\Documents\Codex\2026-09-27\gen-j\work\tools\jdk21\jdk-21.0.12.1+1'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF-8 -Djava.net.preferIPv4Stack=true -Dhttps.protocols=TLSv1.2,TLSv1.3'
.\gradlew.bat --no-daemon assemble
```

输出 jar：

```text
build/libs/aibot-0.0.2-stepfun-personal.jar
```

## 需要注意的问题

1. 代码里还有个人 StepFun key，公开前必须移除。
2. 创造模式 build 现在是关键词判断，不是 StepFun/LLM 语义理解。
3. 蓝图仍只支持项目自己的 JSON 格式，尚未支持 `.litematic` / `.schem`。
4. bot 当前汇报较多，用户说先不改。
5. `docs/GAMETEST_GUIDE.md` 是当前工作树里已有的未跟踪文件，不确定来源，未处理。
6. 当前工作树还有多处未提交修改，需要后续整理 commit。

## 下一步建议

1. 给创造模式建造做更可靠的本地解析器，支持“现代房/木屋/石塔/桥”等更多关键词。
2. 做 `.litematic` / `.schem` 导入器，转换成 `BlueprintSchema`。
3. 给 `blueprints/index.json` 做管理命令，如 `/aibot blueprints list/search/import`。
4. 把密钥从源码移到配置/环境变量。
5. 后续再降低 bot 的任务播报频率。

