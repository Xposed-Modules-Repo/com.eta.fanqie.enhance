# 番茄畅听净化模块 v1.9.3 (versionCode 19300)

## 本版更新

### 拦截增强
- **新增拦截「激励视频」全屏广告页**：`com.ss.android.excitingvideo.ExcitingVideoActivity`
  - 该页面是番茄畅听所有「看视频领奖励」漏斗的容器（看视频免广告倒计时、金币任务等），广告内容（如游戏推广）在此全屏播放
  - 页面一进前台（onResume）即自动 finish，无需等倒计时

### 说明
- 此前的 `AdBrowserActivity` 规则只覆盖广告落地浏览器，未覆盖激励视频容器本身，本版补齐
