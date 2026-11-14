val copyPlugin = tasks.register<Copy>("copyPlugin") {
    from(tasks.named<AbstractArchiveTask>("shadowJar").flatMap { it.archiveFile })
    into("C:\\Users\\SKYMIL\\Desktop\\Main\\Work\\Coding\\Java\\MagicKPvP\\run\\plugins")
}

tasks.register<Exec>("runServer") {
    dependsOn(copyPlugin)
    workingDir = file("C:/Users/SKYMIL/Desktop/Main/Work/Coding/Java/MagicKPvP/run")
    val serverJavaHome = providers.gradleProperty("serverJavaHome")
        .getOrElse("C:/Program Files/Java/jdk-26.0.1")
    commandLine(
        "$serverJavaHome/bin/java.exe",
        "-Xms1G",
        "-Xmx4G",
        "-XX:+UseG1GC",
        "-XX:+ParallelRefProcEnabled",
        "-XX:MaxGCPauseMillis=200",
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+DisableExplicitGC",
        "-XX:+AlwaysPreTouch",
        "-XX:G1NewSizePercent=30",
        "-XX:G1MaxNewSizePercent=40",
        "-XX:G1HeapRegionSize=8M",
        "-XX:G1ReservePercent=20",
        "-XX:G1HeapWastePercent=5",
        "-XX:G1MixedGCCountTarget=4",
        "-XX:InitiatingHeapOccupancyPercent=15",
        "-XX:G1MixedGCLiveThresholdPercent=90",
        "-XX:G1RSetUpdatingPauseTimePercent=5",
        "-XX:SurvivorRatio=32",
        "-XX:+PerfDisableSharedMem",
        "-XX:MaxTenuringThreshold=1",
        "-Dusing.aikars.flags=https://mcflags.emc.gs",
        "-Daikars.new.flags=true",
        "-jar", "paper.jar", "--nogui"
    )
    standardInput = System.`in`
}