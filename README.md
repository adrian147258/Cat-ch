# Cat-ch 🐾

Cat-ch 是一个可爱的猫咪分享 Android 应用，用来记录和分享小猫的日常。你可以浏览猫咪动态、发布照片、点赞和收藏，也可以拥有自己的猫猫窝。

## 功能

- 猫咪动态浏览、照片发布、点赞和收藏
- 邮箱密码登录与邮箱验证码登录
- 个人主页、可编辑签名与封面
- 封面按比例完整显示

本仓库包含 Android 客户端源码；登录、发布和互动需要兼容的后端服务。个人封面和签名目前存储在设备本地。项目仍处于开发阶段，等级制度尚未实现。

## 运行

使用 Android Studio 打开项目，安装 Android SDK 35、JDK 17 和 Gradle 所需组件。最低支持 Android 8.0。构建前在本地 `local.properties` 中增加：

```properties
catch.apiBaseUrl=https://your-api.example.com
```

这个文件被 Git 忽略。不要提交密码、API 密钥、会话令牌或签名密钥。未配置服务时应用无法联网登录。调试构建使用本机生成的 Android 调试签名；发布签名应自行安全配置和保管。

## 许可

源码以 MIT 许可证发布，详见 [LICENSE](LICENSE)。
