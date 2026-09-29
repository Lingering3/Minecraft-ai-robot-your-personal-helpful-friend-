# 指令 Skill

> 状态：设计契约。适用于 Laya 已确认 `command`，即玩家要求使用游戏管理指令或发放蓝图物品。

## 目的

StepFun 从受支持的动作和枚举参数中选出一项。这里的“指令”不是泛指“请你做某事”：建造、挖矿、跟随仍属于各自任务。

| 动作 | 允许参数 | 含义 |
|---|---|---|
| `gamemode` | `survival` / `creative` / `adventure` / `spectator` | 设置请求玩家的游戏模式 |
| `keep_inventory` | `on` / `off` | 开启或关闭死亡不掉落 |
| `weather` | `clear` / `rain` / `thunder` | 设置天气 |
| `time` | `day` / `noon` / `night` / `midnight` | 设置时间 |
| `give_blueprint` | 空 | 向玩家发放蓝图物品；具体蓝图再交由 StepFun 从目录选择 |
| `none` | 空 | 未明确要求受支持的指令，不执行 |

StepFun 返回 `{"action":"gamemode","value":"survival"}` 形式的结构化结果。程序先检查动作与参数是否属于表中集合，再解析目标玩家并执行。未知指令、不合法参数或无法确定目标均不得执行。成功后给玩家一条明确的“已使用指令”消息；失败时报告原因。
