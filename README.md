# OpenSHA

Primary [OpenSHA](https://opensha.org) code repository

## Compilation and project configuration

This repository contains OpenSHA code and models implementations. This project is intended for the core APIs and stable models. Exploratory code or one-off tests should instead go in our development sandbox, [opensha-dev](https://github.com/opensha/opensha-dev), which has this project as a dependency.

### Requirements

* Java 11 JDK or later, 64-bit: [AdoptOpenJDK](https://adoptopenjdk.net/) or [OpenJDK](https://jdk.java.net/)
* [Git](https://git-scm.com/downloads)
    - Git is included in the macOS [developer tools](https://developer.apple.com/xcode/).
    - Windows users may want to consider [Git for Windows](https://git-for-windows.github.io), which includes a linux-like terminal (Git BASH) in which subsequent commands listed here will work.
 
Other dependencies are managed with [Gradle](https://gradle.org/), which does not require a separate installation. Gradle is clever about finding Java, but some users may have to explicitly define a `JAVA_HOME` environment variable. 

### Cloning in a terminal

To clone this project in a terminal (simplest and quickest for most users):

```bash
cd ~/git # create this directory or navigate an alternative directory of your choosing
git clone https://github.com/opensha/opensha.git
```

### Building in a terminal with Gradle

OpenSHA uses Gradle to handle the build process from dependency management to compilation. You should compile OpenSHA from your lowest level project, *opensha-dev* for the example above.

```bash
cd opensha
./gradlew assemble
```

This will build all source files in opensha. It will also build a jar file for each project, not including any dependencies. You can build a "fat jar" which includes dependent libraries as follows:

```bash
cd opensha
./gradlew fatJar
```

### Developing & building OpenSHA with Eclipse

Most active OpenSHA development is done through [Eclipse](https://eclipse.org). You will need the Eclipse IDE for Java Developers.

>**NOTE:** The following instructions assume that you have already cloned the OpenSHA projects on a terminal, though you can clone them through Eclipse. If you chose to go this route, be sure to leave the "Import all existing Eclipse projects after clone finishes" check-box **UNSELECTED**, as this feature will cause issues with gradle.

Once you have eclipse installed and running, do the following:

* `File > Import`  
* Select `Gradle > Existing Gradle Project` and hit `Next`  
* Browse to the location of `opensha` under `Project root directory`  
* Hit `Finish`  

You can either use Eclipse's built in Git tools, or the Git command line client to pull/push changes. Any time any of the `.gradle` files are modified, or you see many unexpected compilation errors, right click on the project within eclipse and select `Gradle >  Refresh Gradle Project`.

## Repository history

OpenSHA has been in active development since the early 2000's. It was originally in CVS version control, and was ported to [this SVN repository](https://source.usc.edu/svn/opensha/trunk/) circa 2008. In 2017, it was migrated to GitHub and split into a number of sub-projects. History from the SVN repository was not retained, but it is [archived here](https://github.com/opensha/opensha-svn-archive). The main codebase, which was re-unified into this repository in 2021, was previously stored in the now-archived [opensha-commons](https://github.com/opensha/opensha-commons), [opensha-core](https://github.com/opensha/opensha-core), [opensha-ucerf3](https://github.com/opensha/opensha-ucerf3), and [opensha-apps](https://github.com/opensha/opensha-apps).

A development sandbox, with which we're more relaxed with write-permissions, can be found [here](https://github.com/opensha/opensha-dev). Otherwise, outside contributions should come in the form of pull requests on this repository.
