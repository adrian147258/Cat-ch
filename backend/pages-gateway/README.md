# Pages 接口入口

`_worker.mjs` 通过 Pages Functions 将请求转发给现有后端。部署时将该文件作为 `_worker.js` 上传，并为生产环境配置名为 `CATCH_BACKEND` 的服务绑定，指向 `meowclub-api` Worker。它保留请求路径、内容和认证信息，并将入口自身的 Origin 转换为后端信任的地址。

部署完成后，在 Android 项目的 `local.properties` 中将 `catch.apiBaseUrl` 设为 Pages 项目的 HTTPS 地址，然后重新构建 APK。

运行 `node backend/pages-gateway/gateway-test.mjs` 可检查请求转发和 Origin 处理。
