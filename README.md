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

### Requirements ###

OpenSHA requires Java 8 or greater. Dependency management is through [gradle](https://gradle.org/), which is included with the repositories.

### Cloning on a terminal ###

To clone all projects in a terminal (simplest and quickest for most users):

```bash
cd ~/git # create this directory or navigate an alternative directory of your choosing
git clone https://github.com/OpenSHA/opensha-commons.git
git clone https://github.com/OpenSHA/opensha-core.git
git clone https://github.com/OpenSHA/opensha-ucerf3.git
git clone https://github.com/OpenSHA/opensha-apps.git
git clone https://github.com/OpenSHA/opensha-dev.git
```

### Building on a terminal with gradle ###

OpenSHA uses gradle to handle the build process from dependency management to compilation. You should compile OpenSHA from the lowest level project, "opensha-dev" for the example above.

```bash
cd opensha-dev # or whichever project you are interested in
./gradlew assemble
```

This will build all source files in opensha-dev and parent projects. It will also build a jar file for each project, not including any dependnecies. You can build a "fat jar" which includes dependent libraries as follows:

```bash
cd opensha-dev # or whichever project you are interested in
./gradlew fatJar
```

### Developing & building OpenSHA with Eclipse ###

Most active OpenSHA development is done through [Eclipse](https://eclipse.org). You will need the Eclipse IDE for Java Developers.

***NOTE:** The following instructions assume that you have already cloned the OpenSHA projects on a terminal, though you can clone them through Eclipse. If you chose to go this route, be sure to leave the "Import all existing Eclipse projects after clone finishes" check-box **UNSELECTED**, as this feature will cause issues with gradle.*

For each project, you will need to do the following:
* File -> Import
* Select "Gradle" -> "Existing Gradle Project" and hit "Next"
* Browse to the location of "opensha-commons" under "Project root directory"
* Hit "Finish"
* Repeat for all sub-projects. **IMPORTANT: projects must be checked out in order, dependent projects first. commons, then core, then ucerf3, then apps, then dev**

You can either use Eclipse's built in git tools, or the git command line client to pull/push changes. If any of the .gradle files are modified, right click on the project within eclipse and select "Gradle" ->  "Refresh Gradle Project"