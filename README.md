# opensha-commons
Base OpenSHA Commons library

## Compilation and project configuration
OpenSHA is split into multiple projects with the following dependencies:

| Name       | Depends On | Description                                      |
|------------|------------|--------------------------------------------------|
| commons    | -          | Base commons library (this project)              |
| core       | commons    | Core OpenSHA library with calculators and models |
| ucerf3     | core       | UCERF3 model code and data                       |
| apps       | ucerf3     | GUI applications                                 |
| dev        | apps       | Development sandbox for shared prototyping       |
| cybershake | apps       | CyberShake interface code and calculators        |

All users will need to clone the top level commons project, as well as any additional projects of interest. For example, if you need ucerf3 code, you will need to check out ucerf3, core, and commons (ucerf3 depends on core, which in turn depends on commons). Many users will need all projects (except CyberShake).

### Requirements

* Java 8 JDK: [Oracle](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) or [OpenJDK](http://openjdk.java.net/install/)
* [Git](https://git-scm.com/downloads)
    - Git is included in the macOS [developer tools](https://developer.apple.com/xcode/).
    - Windows users may want to consider [Git for Windows](https://git-for-windows.github.io), which includes a linux-like terminal (Git BASH) in which subsequent commands listed here will work.
 
Other dependencies are managed with [Gradle](https://gradle.org/), which does not require a separate installation. Gradle is clever about finding Java, but some users may have to explicitly define a `JAVA_HOME` environment variable. 

### Cloning in a terminal

To clone all projects in a terminal (simplest and quickest for most users):

```bash
cd ~/git # create this directory or navigate an alternative directory of your choosing
git clone https://github.com/opensha/opensha-commons.git
git clone https://github.com/opensha/opensha-core.git
git clone https://github.com/opensha/opensha-ucerf3.git
git clone https://github.com/opensha/opensha-apps.git
git clone https://github.com/opensha/opensha-dev.git
```

### Building in a terminal with Gradle

OpenSHA uses Gradle to handle the build process from dependency management to compilation. You should compile OpenSHA from your lowest level project, *opensha-dev* for the example above.

```bash
cd opensha-dev # or whichever project you are interested in
./gradlew assemble
```

This will build all source files in opensha-dev and parent projects. It will also build a jar file for each project, not including any dependencies. You can build a "fat jar" which includes dependent libraries as follows:

```bash
cd opensha-dev # or whichever project you are interested in
./gradlew fatJar
```

### Developing & building OpenSHA with Eclipse

Most active OpenSHA development is done through [Eclipse](https://eclipse.org). You will need the Eclipse IDE for Java Developers.

>**NOTE:** The following instructions assume that you have already cloned the OpenSHA projects on a terminal, though you can clone them through Eclipse. If you chose to go this route, be sure to leave the "Import all existing Eclipse projects after clone finishes" check-box **UNSELECTED**, as this feature will cause issues with gradle.

For each project, you will need to do the following:
* `File > Import`  
* Select `Gradle > Existing Gradle Project` and hit `Next`  
* Browse to the location of `opensha-commons` under `Project root directory`  
* Hit `Finish`  
* Repeat for all sub-projects. **IMPORTANT: projects must be imported in order, dependent projects first. commons, then core, then ucerf3, then apps, then dev**  

You can either use Eclipse's built in Git tools, or the Git command line client to pull/push changes. If any of the `.gradle` files are modified, right click on the project within eclipse and select `Gradle >  Refresh Gradle Project`
