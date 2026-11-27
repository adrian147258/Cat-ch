# 客户端优化回归检查

`java/com/meowclub/app/UploadBodyChecks.java` 是不依赖 Android 运行时的检查入口。使用 JDK 17，将它与主源码中的 `ImageFiles.java`、`UploadBody.java` 一起编译，然后运行 `com.meowclub.app.UploadBodyChecks`。检查 multipart 长度、进度、图片数量与大小限制、上传文件被修改、有限大小拷贝、解码采样和取消。

`../androidTest/java/com/meowclub/app/OptimizationChecks.java` 是设备上的 Instrumentation 检查。必须打包到独立的 `com.meowclub.optimizationtest` 应用，以 `com.meowclub.app.MainActivity` 为 Activity，并在清单中声明 `com.meowclub.app.OptimizationChecks` 为 instrumentation（targetPackage 同测试包）。构建测试包时将 `BuildConfig.API_BASE_URL` 设置为 `https://catch.test`。入口会拦截测试进程内全部 HTTPS 请求，使用模拟图片、帖子和上传响应，不访问生产服务，不覆盖正式应用。

安装测试包后运行：

```text
adb shell am instrument -w com.meowclub.optimizationtest/com.meowclub.app.OptimizationChecks
```

设备检查覆盖长列表卡片数量、图片磁盘缓存、超过 5 MB 的原图压缩、草稿账号隔离与顺序、Activity 重建恢复、上传失败后重试、上传期间重建和离线重试入口。
