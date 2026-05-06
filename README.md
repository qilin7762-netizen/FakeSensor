# FakeSensor

全局传感器伪造 Xposed 模块。

通过 hook `SystemSensorManager.registerListenerImpl` 和 `unregisterListenerImpl`，在 Zygote 阶段注入代理 `SensorEventListener`，拦截并替换目标 App 的传感器数据。

## 使用说明

### 前提条件

- 设备已 Root 并安装 LSPosed 框架
- 本模块在 LSPosed 管理器中激活
- 在 LSPosed 作用域中勾选需要伪造传感器的目标 App
- 目前资源有限，仅在 MIUI 13(Android 12) 一台设备上生效并模拟成功

### 基本流程

1. 安装模块并重启
2. 打开 LSPosed 管理器，启用本模块，勾选目标 App（必选系统框架）
3. 打开 FakeSensor App
4. 勾选需要伪造的传感器（至少一个）
5. 点击 **静态模拟** 或 **动态模拟**

### 静态模拟

使用用户在输入框中输入的具体数值作为传感器数据，不会随时间变化。

- 每个传感器对应的输入框（如 X/Y/Z 轴数值）在勾选后会显示默认值
- 点击 "静态模拟" 后会将这些固定值写入配置
- 目标 App 读取传感器时将始终返回这些固定值
- 适合需要精确控制传感器数值的场景（如测试特定数值下的 App 行为）

### 动态模拟

基于预设场景实时生成变化的数据，模拟真实运动状态。

- **静止** — 微小噪声扰动，幅度极小
- **步行** — 中等幅度周期性波动，叠加随机噪声
- **跑步** — 大幅度高频波动
- **骑行** — 低频平滑摆动
- 步数传感器会在后台自动递增

### 配置文件路径

模块会按以下优先级读取配置，命中即返回：

| 优先级 | 来源 | 路径/URI |
|--------|------|----------|
| 1 | ContentProvider | `content://com.app.fakesensor.config/all` |
| 2 | 外置存储文件 | `/sdcard/Android/data/com.app.fakesensor/files/fake_sensor_config.txt` |
| 3 | 根目录文件 | `/sdcard/fake_sensor_config.txt` |
| 4 | 临时目录 | `/data/local/tmp/fake_sensor_config.txt` |
| 5 | SharedPreferences | XSharedPreferences (同包名) |

配置文件格式为 `key=value`，用竖线 `|` 分隔：

```
enabled=true|simulate=true|scenario=walking|types=1,4,9|accel_x=0.0|accel_y=0.0|accel_z=9.8
```

关键字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `enabled` | boolean | 是否启用伪造 |
| `simulate` | boolean | true=动态随机值, false=固定值 |
| `scenario` | String | `stationary` / `walking` / `running` / `cycling` |
| `types` | String | 逗号分隔的传感器类型码 |
| `accel_x` / `accel_y` / `accel_z` | float | 传感器各轴固定值（非模拟模式下生效） |

## 📋 免责声明 & 已知限制

- ⚠️ **本模块仅在 LSPosed 中激活后才能生效**，单独启动 App 无作用
- ⚠️ **仅在 MIUI 13 (Android 12) 测试过**，其他版本兼容性未知，欢迎反馈
- ⚠️ **仅供测试开发使用**，不得用于欺骗或违规用途
- 📱 **其他 Android 版本和设备的兼容性反馈** 欢迎在 Issue 中提交

## ⚖️ 法律声明

### 使用限制

**禁止用途：**

❌ 绕过应用的反欺诈、风控系统（如银行 App、支付平台）  
❌ 欺骗任何第三方服务获取不当利益（如健身 App、位置服务、考勤打卡等）  
❌ 违反任何应用的服务条款（ToS）或隐私政策  
❌ 商业目的的分发、销售或盈利性使用  
❌ 任何违反当地法律的行为  

**允许用途：**

✅ 个人应用的测试和开发  
✅ 学术研究和学习  
✅ 在受控环境下的功能验证  

### 法律责任

**使用者需知：**

- 📌 使用者**全权负责**所有因使用本项目产生的法律后果
- 📌 开发者**不承担任何直接或间接的损害赔偿责任**
- 📌 请在使用前确保你的使用方式**符合当地法律**

### 地区特定提示

> 🇨🇳 **中国用户**  
> 修改自己设备上的系统受法律保护，但伪造传感器数据用于欺骗金融 App、绕过反欺诈系统可能触犯《刑法》第285条。

> 🇺🇸 **美国用户**  
> 美国《数字千年版权法》(DMCA) 可能对绕过应用防护机制有限制。建议了解当地法律。

> 🌍 **其他地区用户**  
> 请自行评估本项目在你所在地的法律适用性。如有疑问，建议咨询法律专家。

---

## 工作原理

### Xposed 注入

模块同时实现了 `IXposedHookZygoteInit` 和 `IXposedHookLoadPackage`：

```
initZygote()
  ├─ SensorHook.init()          → 初始化配置
  └─ SensorHook.hookSensorManagerInClassLoader(null)
       └─ 寻找 SystemSensorManager 中的 registerListenerImpl / unregisterListenerImpl
            └─ 通过 XposedBridge.hookMethod() 注入代理
```

Zygote 阶段的 hook 会被所有 App 进程继承（因为 `SystemSensorManager` 是 boot classloader 加载的），无需在每个 App 加载时重复 hook。

### Hook 点：registerListenerImpl

当目标 App 注册传感器监听时：

```
App: sensorManager.registerListener(listener, sensor, delay)
                     ↓
SystemSensorManager.registerListenerImpl(listener, sensor, ...)
                     ↓  (hook: beforeHookedMethod)
SensorListenerHook.beforeHookedMethod()
  ├─ reloadConfig()                        → 读取最新配置
  ├─ shouldFake(sensor.getType())          → 检查该传感器是否在伪造列表中
  └─ param.args[0] = new FakeSensorEventListener(listener, sensor)
       └─ 用代理对象替换原始 listener
            └─ 如果是步数传感器：注册到 stepListeners，启动全局定时器
```

`FakeSensorEventListener` 的 `onSensorChanged()` 在收到真实传感器事件时：

1. 调用 `reloadConfig()` 获取最新配置（500ms 节流）
2. 如果该传感器需要伪造：
   - 计步器：记录真实步数基数（stepBase），不转发（由定时器负责推送）
   - 其他传感器：用 `getFakeValues()` 的返回值替换 `event.values`
3. 转发修改后的 event 给原始的 listener

### Hook 点：unregisterListenerImpl

当目标 App 取消注册传感器监听时：

```
SensorUnregisterHook.beforeHookedMethod()
  ├─ listenerMap.remove(original)          → 查找对应的 FakeSensorEventListener
  ├─ stepListeners.remove(fake)            → 从步数监听列表中移除
  └─ param.args[0] = fake                  → 替换为代理对象，确保框架能找到并正确取消
```

这一步防止了内存泄漏 —— 不会持有已销毁的 Activity 或 Service 的 listener 引用。

### 步数传感器模拟

步数传感器（TYPE_STEP_COUNTER + TYPE_STEP_DETECTOR）有独立推送机制：

```
全局 HandlerThread "FakeSensor-step" (每秒执行一次)
        │
        ├─ 检查 enabled && simulate
        │
        ├─ 根据场景递增步数：
        │   静止   → 不递增
        │   步行   → 1-2 步
        │   跑步   → 2-4 步
        │   骑行   → 30% 概率 1 步
        │
        └─ 遍历 stepListeners，调用每个 listener 的 pushStepValue()
             └─ 用最新的 SensorEvent 对象，设置 values[0] = stepBase + stepCount
             └─ 更新 timestamp = System.nanoTime()
             └─ 调用 original.onSensorChanged() 推送
```

`stepBase` 在收到第一个真实步数事件时记录，之后所有累加都基于这个基数：

```
返回给 App 的值 = stepBase (真实基数) + stepCount (模块累加值)
```

### 动态场景参数

每个场景定义了不同的振幅（amp）和频率（freq），用于生成传感器数据的波形：

| 场景 | 振幅 | 频率 | 加速度计特征 |
|------|------|------|------------|
| 静止 | 0.05 | 0.5 | 极微小抖动，Z 轴接近 9.8 |
| 步行 | 1.0 | 2.0 | 中等摆动 + 正弦波，Z 轴上下波动 |
| 跑步 | 4.0 | 5.0 | 大幅度高频震荡 |
| 骑行 | 0.3 | 1.0 | 平滑低幅摆动 |

加速度计数据生成示例（步行）：

```
x = rand(-1.5, 1.5) + sin(t * 2) * 1.5
y = rand(-1, 1) + cos(t * 2) * 1
z = rand(9.5, 10.0) + |sin(t * 2)| * 2
```

其中 `t = (time % period) / period * 2π`

## 项目结构

```
com.app.fakesensor/
├── XposedEntry.java           — Xposed 入口，Zygote 初始化 + LoadPackage 回调
├── SensorHook.java            — 核心：hook 逻辑、配置加载、数据伪造、代理 Listener
├── MainActivity.java          — 主界面：传感器勾选、固定值输入、模式选择
├── SimulationActivity.java    — 模拟界面：实时数值显示、场景切换
└── ConfigProvider.java        — ContentProvider：跨进程配置读取
```

## 构建

```
./gradlew assembleDebug
```

依赖 `libs/XposedBridgeAPI-82.jar`。

最低 SDK 版本：28（Android 9），目标 SDK 版本：36（Android 16）。

## 📄 License

MIT License - 详见 [LICENSE](./LICENSE) 文件
