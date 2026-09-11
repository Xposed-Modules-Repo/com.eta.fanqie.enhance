# 番茄畅听净化模块

一个 [Vector](https://github.com/AAswordman/Vector) / LSPosed Xposed 模块：净化番茄畅听（com.xs.fm）的使用体验——破解会员、隐藏营销入口与广告弹窗。

> **当前版本：v1.9.10**
> **适配目标应用：番茄畅听 `6.7.1.16`（versionCode `671`）实测通过**
> 需要 Root + Vector 或 LSPosed（xposedminversion 82，Android 8.0+）

---

## 🔗 项目主页（源码 / 更新 / 问题反馈）

> **👉 [https://github.com/guoxpeng/fanqie-purify](https://github.com/guoxpeng/fanqie-purify)**

**本仓库仅为 LSPosed / Vector 模块仓库的发布镜像，完整源码、最新版本与详细说明请前往上面的主仓库。**

- 📦 **主仓库（源码 + 最新 APK + 完整文档）**：<https://github.com/guoxpeng/fanqie-purify>
- 🐛 **问题反馈 / 建议**：<https://github.com/guoxpeng/fanqie-purify/issues>
- ⬇️ **直接下载最新版**：[fanqie-enhance-v1.9.10.apk](https://github.com/guoxpeng/fanqie-purify/raw/main/fanqie-enhance-v1.9.10.apk)

---

## 📌 适配版本（重要）

| 项目 | 值 |
|------|-----|
| **适配应用** | 番茄畅听 `com.xs.fm` |
| **适配版本名** | **`6.7.1.16`** |
| **适配 versionCode** | **`671`** |
| 本模块版本 | `1.9.10`（versionCode `191000`） |

- 本版已把模块用到的 **16 个混淆类名 / 资源 id** 在 6.7.1.16 上逐一复核，全部仍然存在（含 `h80` / `bpz` / `e33` / `ccv` / `c1` / `e10` 等）。
- 番茄的类名与资源 id 逐版强混淆变动，**只保证 6.7.1.16（671）实测可用**。应用大版本升级（如 6.7 → 6.8）后极可能失效，需重新适配。
- ⚠️ **注意：模块版本号 `1.9.x` 与番茄版本号 `6.7.1.16` 是两套独立编号，不要混淆。**

---

## 🆓 免登录说明

**本模块的核心能力不依赖登录账号。**

| 能力 | 是否依赖登录 | 说明 |
|------|--------------|------|
| 去广告（贴片广告 / 广告卡 / 弹窗 / 广告页 / 倒计时条） | ❌ **不依赖** | 纯本机 View 树 + 类名 + 资源 id 层面拦截，未登录 100% 生效 |
| 入口隐藏（商城 / 领现金 / 金币球 / 福利 / 我的资产 / 我的消息 / 全天畅听…） | ❌ **不依赖** | 同上，纯 UI 层面 |
| VIP 状态写入（`isVip` / `freeAd` / `expireTime` …） | ⚠️ 可写入，实际免听看服务端 | 未登录（游客态）时 `AcctManager.INSTANCE` 依然存在，模块能成功写入；但会员权益由番茄**服务端**校验，未登录能否真正试听 VIP 内容取决于服务端策略，模块不绕过服务端 |

**结论**：只想 **去广告 + 界面净化** → **不用登录**；想要 **完整 VIP 免听** → 建议登录账号。

---

## 功能列表

### 会员相关
- **VIP 免听破解**：自动将 `AcctManager.INSTANCE.userModel` 写入 VIP 状态（`isVip` / `freeAd` / 过期时间 2099 / `leftTime` 等）
- **账号态无关**：未登录同样写入；登录 / 退出登录切换实例后自动重新 patch

### 入口净化（隐藏）
- 底部导航「商城」「领现金」tab（保留 首页/听歌/我的 三 tab）
- 「我的」页：我的资产 / 金币余额(币) / 现金余额(元) / 福利面板 / 购物车 / 优惠券 / 商城入口 / 借钱 / 我的公益 / 我的消息
- 阅读页右上角「700金币」自绘小面板（按 viewId `h80` + 位置校验隐藏）
- 右侧悬浮「立即领取」金币球
- 首页顶部「直播」tab、首页浮动「登录领取」红包
- 章节页「看小视频免30分钟广告」提示链接、「2500金币待领取」入口
- 听歌页「全天畅听」广告卡、「看小视频免广告」横幅
- 听歌页 MusicAdUnlockTimeView（广告解锁倒计时条）
- 全屏覆盖型广告自动检测与隐藏

### 弹窗拦截
- 类名匹配：luckycat / 更新升级 / 广告弹窗
- 内容匹配：「签到」「听歌领金币」「领取+金币」类弹窗自动关闭
- **DialogFragment 拦截**：章节末「看小视频免30分钟广告」等广告弹窗是 DialogFragment（不走 `Dialog.show()`），hook `androidx.fragment.app.DialogFragment.show()` + 已知广告 Fragment 的 `onCreateView` 直接阻止渲染
- **广告文本实时过滤**：hook `TextView.setText()`，广告文本被设置的瞬间即隐藏（覆盖模式切换重建 View 的场景，零扫描零卡顿）

### 广告拦截（v1.9.9 增强）
- **听歌页贴片广告**：番茄的「贴片广告」由字节 Lynx（`com.ss.android.mannor`）模板渲染，装在 `MusicPatchAdContainer` 里、**没有固定资源 id**。改为**视觉层面**拦截：hook 该容器构造 + `View.setVisibility`，只置 `GONE`、绝不 `removeView`（`ViewStub` 只能 inflate 一次，remove 会抛异常）

### 页面拦截
- 商城 Activity、luckycat 激励页、开屏广告、沉浸式广告、免费听广告页、广告解锁页等启动即 finish
- AdUnlockTimeDialogManager 弹窗拦截（源码级 hook）
- 激励广告 SDK 调用拦截（showAd / preloadAd 等）
- 广告倒计时 View 创建即 GONE（MusicAdUnlockTimeView / AdUnlockTimeFloatingView）

---

## 安装步骤

> ✅ **顺序要求已放宽**：v1.9.10 起去广告与界面净化**不再要求先登录**。推荐顺序为「装模块 → 启用作用域 → 强停重启目标 App」，登录与否随意。

1. **安装模块 APK**
   ```bash
   pm install -r /path/to/fanqie-enhance-v1.9.10.apk
   ```
2. **在 Vector / LSPosed 管理器中启用模块**，作用域勾选番茄畅听
   ```
   /data/adb/lspd/cli modules enable com.eta.fanqie.enhance
   /data/adb/lspd/cli scope add com.eta.fanqie.enhance com.xs.fm/0
   ```
3. **强制停止番茄畅听后重新打开**（模块随目标进程注入，必须重启目标进程才生效）
   ```bash
   am force-stop com.xs.fm
   ```
4. （可选）登录账号，仅用于获得完整 VIP 免听体验。

### 如何确认模块已生效（⚠️ 日志不在 logcat 里）

`XposedBridge.log` 的输出**不会**出现在 `logcat` 中。Vector/LSPosed 会把它写进自己的日志文件，logcat 里只能看到 `VectorZygiskBridge: GET_BINDER` 这类框架噪声，**不要被它误导**。

```bash
adb shell "su -c 'grep FanqieEnhance /data/adb/lspd/log/verbose_*.log | tail -20'"
```

看到 `[FanqieEnhance] v1.9.10 加载: process=com.xs.fm` 与 `已patch userModel: isVip=true freeAd=true leftTime=999999999` 即成功。

### 已知限制
- 卸载后重装模块需**重新 enable + scope add**
- **番茄大版本更新可能改变混淆类名 / 资源 id**（如 `h80`），届时需按新版本重新适配
- 模块只影响 `com.xs.fm` 主进程，不影响其他应用

---

## 工作原理（简述）

| 机制 | 说明 |
|------|------|
| patchVip | 反射 `com.dragon.read.user.AcctManager.INSTANCE.userModel`，写入 `isVip`/`freeAd`/`expireTime`/`leftTime` 等字段；实例身份变化时自动重 patch |
| hideAll | `OnGlobalLayout` 监听（1.2s 限频），遍历 View 树按文本规则隐藏入口 |
| hideChain | 触底隐藏：向上连藏最多 3 层父容器，带页面级 / 导航栏双重保护防误伤 |
| **isAppBarLike 整链放弃** | `hideChain` 入口先沿父链向上查找 `AppBar`/`Toolbar`/`ActionBar`/`TabLayout`，命中即**整条链放弃**。用于区分「我的页头部账号入口」与「浮动红包广告」——两者文案都是「登录领取」，**必须用结构判定而非文案白名单** |
| scanAllWindows | 反射 `WindowManagerGlobal.mViews`，覆盖独立悬浮窗里的金币球 |
| Dialog / DialogFragment blocker | hook `Dialog.show()` 与 `DialogFragment.show()`，类名 + 内容双重匹配；广告 Fragment `onCreateView` 返回 null |
| TextView 文本过滤 | hook `TextView.setText()`：广告文本被设置瞬间隐藏自身及卡片容器 |
| View.setVisibility 拦截 | 已知广告 View 被设 `VISIBLE` 时强制 `GONE`，防重建闪现 |
| 贴片广告视觉拦截 | 针对 Lynx 模板广告（`MusicPatchAdContainer`），只 `GONE` 不 `remove`，避免 `ViewStub` 二次 inflate 崩溃 |

完整源码见 [module/src/com/eta/fanqie/enhance/MainHook.java](module/src/com/eta/fanqie/enhance/MainHook.java)（约 2100 行，单文件实现），或前往[主仓库](https://github.com/guoxpeng/fanqie-purify)。

---

## ⚠️ 免责声明

1. **本项目仅供学习与技术交流使用**，用于研究 Android Framework、Xposed Hook 机制与 UI 自动化净化技术。
2. 本模块**不修改番茄畅听 APK 本体**，不联网、不上传任何数据；所有效果均在本机内存中临时生效。
3. 番茄畅听及其内容版权归字节跳动所有。会员权益属付费服务，**请支持正版**；本模块仅供个人学习研究，**严禁用于商业用途或二次分发牟利**。
4. 使用本模块产生的一切后果（包括但不限于账号风控、功能异常）由使用者自行承担。作者不对任何直接或间接损失负责。
5. 如你是有权方并认为本项目侵犯合法权益，请联系删除。
6. **下载即视为已阅读并同意以上全部条款。**

详见 [DISCLAIMER.md](DISCLAIMER.md)。

---

## 许可证

MIT License — 详见 [LICENSE](LICENSE)

**再次提醒：仅供学习交流，请在 24 小时内自行决定是否保留，支持正版。**

---

> 本仓库为发布镜像。**最新版本、完整源码与问题反馈请访问主仓库：<https://github.com/guoxpeng/fanqie-purify>**
