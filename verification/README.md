# FuckShareExt JVM 回归验证

在项目根目录执行：

```bash
python3 verification/run_regressions.py
```

验证范围：原方法调用次数与异常传播。

脚本使用 GRADLE_USER_HOME（未设置时为 ~/.gradle）中缓存的 Kotlin 2.2.20 编译器及其依赖，要求 JDK 21，不下载软件。测试直接读取当前项目源码；需要 Android 环境的局部方法使用类型替身。临时编译产物自动删除。

测试验证局部控制流，不能替代完整 Gradle/APK/R8 构建、设备运行、Binder 或 ContentProvider 集成、真实 GIF 编码和性能测量。
