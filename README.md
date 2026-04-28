# Clare-FF

## Ford-Fulkerson-GrALoG-Plugin

---

### Installation Existing Users

For those who already use GrALoG and have a number of your own plugins installed,
the easiest path is to add the new plugin folder and upgrade various existing Gralog Files:

#### Plugin Folder

Copy the plugin Installation-Existing-Users/gralog-max-flow folder to your gralog root folder, the folder should contain gradlew and gradlew.bat files.

#### Upgrade Existing GraLog files

1. Copy Installation-Existing/settings.gradle and Installation-Existing-Users/build.gradle files to gralog root folder
2. Copy Installation-Existing/config.xml to gralog-fx\src\main\java\gralog\gralogfx\
3. Copy Installation-Existing/EdgeRenderer.java to gralog-core\src\main\java\gralog\rendering\
4. Copy Installation-Existing/MainWindow.java to gralog-fx\src\main\java\gralog\gralogfx\
5. Copy Installation-Existing/MaxFlowLegendPanel.java to gralog-fx\src\main\java\gralog\gralogfx\ (this is a new file)
6. Copy Installation-Existing/PluginControlPanel.java to gralog-fx\src\main\java\gralog\gralogfx\panels\

### Installation for New Users

For those new to GrALoG, a precompiled "gralog-fx.jar" file is available for Linux, Windows and MacOS.

After installing Gralog, copy the contents of the appropriate distribution folder for your OS, Installation-New/dist-{OS} to the build/dist/ folder with-in the gralog root folder, the folder that contains gradlew and gradlew.bat files.

1. Copy contents of Installation-New/dist-{OS} folder into build/dist/
2. Navigate to the gralog-fx.jar file in \build\dist\

This can be run by double clicking.

PLEASE NOTE:
Java version JRE 11 is needed to run GrALoG.
This is available for download <a href="https://adoptium.net/installation" target="_blank">HERE</a>

If you are having trouble running GrALoG, ensure that you are running version 11 of Java using `java --version`

If you are on Linux and are struggling to run GrALoG, try running
`java -jar gralog-fx.jar`

All alterations to pre-existing GrALoG files are annotated with the phrase "MAXFLOW".
