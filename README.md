# 蘑菇鉴别

一款面向自然观察与科普学习的 Android 蘑菇辅助识别应用。用户可以拍摄或选择多张蘑菇照片，通过 MiniMax 模型获取候选结果，再结合随 App 发布的本地图鉴查看物种名称、形态特征、生态分布和毒性风险等资料。

> [!WARNING]
> 本项目不能判断蘑菇是否可以安全食用。AI 结果和图鉴资料仅供科普与检索参考，不得作为采食、售卖、加工、药用、诊断或治疗依据。请勿食用仅经本 App 识别的野生蘑菇。

## 功能
<img width="1672" height="941" alt="image" src="https://github.com/user-attachments/assets/b59da229-701b-4d38-92d4-3322b36ee736" />

- 拍照或从系统相册选择图片，单次最多支持 6 张照片
- 使用 MiniMax M3 流式分析图片并展示识别过程
- 输出多个候选物种、判断理由和安全提示
- 将 AI 候选与本地中文名、学名及别名进行匹配
- 内置离线图鉴，支持中文名、学名和别名搜索
- 按可食用、药用、有毒、需谨慎等类型筛选
- 查看分类、形态、用途、毒性、生态、分布和图片等详细资料
- 收藏物种，并在数据库更新后保留收藏状态
- 在本地保存识别历史及历史照片
- 通过 `mushroom.iflora.cn` 查看相关 3D 学术资料
- 手动同步 iFlora 的标本、综合名录、食用真菌和有毒真菌数据
- 支持同步断点续传、单任务防重和同步完成后的别名回填

## 技术栈

- Kotlin 1.9
- Jetpack Compose + Material 3
- Navigation Compose
- Hilt
- Room + 预打包 SQLite 数据库
- WorkManager
- Kotlin Coroutines / Flow
- Kotlin Serialization
- Coil
- MiniMax OpenAI 兼容流式接口
- JUnit、Robolectric、MockWebServer

## 环境要求

- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK 34
- Android 7.0（API 24）或更高版本的设备或模拟器
- MiniMax API Key，未配置时仍可浏览本地图鉴，但不能使用 AI 识别

## 快速开始

1. 克隆项目：

   ```bash
   git clone git@github.com:JackYangzg/mushroom.git
   cd mushroom
   ```

2. 在项目根目录的 `local.properties` 中配置 Android SDK 和 MiniMax API Key：

   ```properties
   sdk.dir=/path/to/Android/sdk
   MINI_MAX_API_KEY=sk-cp-your-api-key
   ```

   API Key 由 `app/build.gradle` 注入 `BuildConfig.MINIMAX_API_KEY`。`local.properties` 已被 Git 忽略，请勿将真实密钥写入源码或提交到仓库。更多说明见 `.env.example`。

3. 构建 Debug APK：

   ```bash
   ./gradlew :app:assembleDebug
   ```

4. 安装到已连接设备：

   ```bash
   ./gradlew :app:installDebug
   ```

Debug APK 默认输出到：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 数据库资产

项目将离线数据随 APK 一同发布，首次安装时通过 Room 复制到应用数据库目录。App 启动不会自动联网同步，用户可在“我的 > 设置 > 数据库同步”中主动更新。

| 文件 | 用途 |
| --- | --- |
| `app/src/main/assets/mushroom.db` | Room 预打包主数据库，当前包含 4 个来源的 5,922 条记录 |
| `app/src/main/assets/alias_mushroom.db` | 别名回填数据库，当前包含 745 条别名记录 |
| `app/src/main/assets/mushroom_index.json` | 识别流程使用的轻量名称索引 |
| `app/src/main/assets/seed_species.json` | 种子数据兼容资源 |

主数据库的数据来源包括：

- `SPECIMEN`：iFlora 标本检索
- `GENERAL_DIRECTORY`：综合物种名录
- `EDIBLE_FUNGI`：食用真菌名录
- `TOXIC_FUNGI`：有毒真菌名录

重新抓取四个来源并生成 `mushroom.db`：

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.yangzhiguo.mushroom.sync.GenerateOfflineDatabaseTest" \
  -PgenerateOfflineDatabase
```

该命令会访问远程数据源并替换 `app/src/main/assets/mushroom.db`。提交数据库前应检查文件差异、SQLite 完整性和打包数据库测试结果。

## 测试

运行单元测试：

```bash
./gradlew :app:testDebugUnitTest
```

验证 Debug 构建：

```bash
./gradlew :app:assembleDebug
```

检查随包数据库：

```bash
sqlite3 app/src/main/assets/mushroom.db "PRAGMA integrity_check;"
sqlite3 app/src/main/assets/alias_mushroom.db "PRAGMA integrity_check;"
```

测试覆盖数据库 Schema、搜索与匹配、收藏迁移、别名回填、同步合并、抓取客户端、流式响应解析和识别历史等关键逻辑。

## 项目结构

```text
app/src/main/
├── assets/                 # 离线数据库、别名库和识别索引
├── java/.../mushroom/
│   ├── data/               # Room、收藏和数据仓库
│   ├── recognition/        # MiniMax 客户端、解析、匹配和识别历史
│   ├── scraper/            # iFlora 数据抓取
│   ├── sync/               # WorkManager 同步、合并和别名回填
│   ├── ui/                 # Compose 页面、组件和导航
│   └── di/                 # Hilt 模块
└── res/                    # Android 资源
```

## 数据与隐私

- 只有用户主动发起识别时，所选图片才会发送至 MiniMax 模型服务。
- 图鉴、收藏、识别历史照片和图片缓存主要保存在设备本地。
- 数据库同步、远程图片和 3D 资料会访问第三方服务，服务方可能记录常规网络日志。
- App 不要求注册账号，不读取通讯录、短信或精确位置，也不出售个人信息。
- 请勿上传包含人脸、证件、住址、联系方式或医疗资料等无关个人信息的图片。

物种及相关公开资料主要来自中科院昆明植物研究所 iFlora 真菌子平台：

- [fungi.iflora.cn](https://fungi.iflora.cn)
- [mushroom.iflora.cn](https://mushroom.iflora.cn)

第三方数据、图片、商标及其他内容的权利归原权利人所有。引用不代表相关机构对本项目的认可或背书。

## 联系方式

意见反馈或侵权联系：`yzg37166@126.com`
