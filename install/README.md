# GlidePath Windows launcher

This folder contains the Windows installation and launch files. The Java/Gradle
project stays in the adjacent `desc-calc/` folder:

```text
desc-calc/                  Git repository
  install/
    Install-GlidePath.cmd   Double-click once to create Start menu shortcuts
    Install-GlidePath.ps1   Shortcut installation
    GlidePath.vbs          Launch without a console; accepts "stop"
    Start-GlidePath.ps1     Build, start, check readiness, open browser, or stop
    glidepath.ico          Windows icon
  desc-calc/
    gradlew.bat
    build.gradle
    src/
    data/                  Local saved plans (not in Git)
    .glidepath/             Local launcher logs/process state (not in Git)
```

Install a JDK (21 recommended), available through `JAVA_HOME` or `PATH`.
Double-click **Install-GlidePath.cmd**, then search Windows for **GlidePath**.
**Stop GlidePath** stops the launcher-started app; closing the browser does not.
Alternatively, double-click **GlidePath.vbs** to launch directly.

The launcher uses the adjacent project directory, independently of the current
working directory. It runs the Gradle Wrapper's `bootJar`, starts Java hidden and
opens the browser when the app is ready. First-time dependency downloads need
internet. A running GlidePath instance is reused without rebuilding.

Keep both folders together. After moving the repository or cloning it onto another
machine, run the installer again to refresh shortcut paths. No admin rights are
needed. To remove the installation, delete the GlidePath and Stop GlidePath Start
menu shortcuts. Saved plans remain untouched.

Additional launcher options and log locations are documented in
[the project README](../desc-calc/README.md#glidepath-windows-launcher).
