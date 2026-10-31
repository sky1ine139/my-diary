# 我的日记 (My Diary)

一个自己写给自己用的安卓日记应用。轻量、离线、数据完全属于自己。

## 为什么做这个

手机自带便签记日记，日子一长就乱了：找不到哪天写的、没法搜索、导出困难。
应用商店的日记 App 大多要付费解锁，或者要求注册账号、把日记同步到别人的服务器上。

所以自己写一个：**只做记日记这件事，做好**。

## 特点

- **完全离线**：日记存在手机本地 SQLite，不联网、不注册、不上传
- **一天一篇**：按日期组织，重复编辑自动覆盖，不会产生重复条目
- **心情 + 天气**：6 种心情、6 种天气，点一下记录，再点一下取消
- **标签与全文搜索**：搜索走 SQL，几千篇也不卡
- **日历视图**：有日记的日子标出来，一眼看到哪里断更了
- **自动草稿**：输入停顿自动存草稿，切后台、被系统杀进程都不丢字
- **导出与备份**：Markdown（可丢进任何笔记软件）+ JSON（可恢复）
- **深色模式**：跟随系统自动切换，晚上写不刺眼
- **体积小**：约 4.8MB，无第三方 SDK 追踪

## 安装

1. 下载 [Releases](../../releases) 里的 `MyDiary-<版本>.apk`
2. 手机上打开安装（首次需允许「安装未知来源应用」）
3. 需要 Android 8.0 及以上

> 升级时直接覆盖安装即可，日记数据会保留。请勿卸载后重装，那会清空数据。
> 换手机前先用「更多 → 备份数据到文件」导出 JSON，新手机用「从备份恢复」导入。

## 数据安全

你的日记只在这一台手机的本地数据库里（`/data/data/com.mydiary.app/databases/diary.db`）。
应用不联网同步、不上传。唯一的网络请求是手动点击「检查更新」时访问 GitHub 的公开接口。

建议定期导出一份备份存到自己的网盘/电脑。

## 从源码构建

需要 JDK 17 和 Android SDK 34。

```bash
# 1. 修改 local.properties 指向你的 SDK
echo "sdk.dir=/你的/SDK/路径" > local.properties

# 2. 配置签名（keystore.properties）
cat > keystore.properties <<EOF
storeFile=/你的/keystore.jks 的路径
storePassword=你的密码
keyAlias=你的别名
keyPassword=你的密码
EOF

# 3. 构建
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

没有 `keystore.properties` 时会退化为 debug 签名，也能装，但**不能覆盖安装正式版**。

## 更新检查

代码里 `UpdateChecker.kt` 顶部的 `OWNER` / `REPO` 指向发布仓库。
每次发新版：把 `app/build.gradle` 里的 `versionName` 递增，然后在 GitHub 上创建一个
tag 形如 `v1.0.1` 的 Release，附上 APK 和更新说明。应用里点「检查更新」就会读到。

## 技术栈

Kotlin + 原生 View（ViewBinding）+ SQLiteOpenHelper。没有 Room、没有协程框架、
没有图片加载库——这个体量的应用不需要，少一层依赖就少一个出问题的地方。

## 许可

个人使用。代码随便看、随便改。
