# Documentation

## Issue #1: Updating Gradle and Modifying Deprecated Configurations

### Overview

During our efforts to build the project, we encountered several issues that were rooted in the 
outdated version of Gradle that we were using. It quickly became clear that we needed to 
update Gradle to a newer version to solve these problems and align ourselves with the current 
best practices.

Upon updating Gradle, we found that several configurations used in our `build.gradle` file were 
deprecated or completely removed in the newer versions of Gradle. This required us to revise our 
`build.gradle` file and update our dependency configurations.

### Main Results

The transition to a newer version of Gradle and the update of the `build.gradle` file was successful.
We replaced the `compile` configuration with `implementation` and `testCompile` with `testImplementation`
as per the new Gradle conventions.

By adapting to these new conventions, we have managed to bring our project up to date with the current 
Gradle practices, enhancing our build performance and dependency management. This update also ensures 
that our project can leverage the latest features and improvements offered by Gradle.

### Lessons Learnt

This process reiterated the importance of maintaining updated build tools in our development 
environment. By updating Gradle and aligning our project with the latest conventions, we have 
improved the efficiency of our builds and learned a great deal about Gradle's dependency management
and the implications of its different configurations.

Going forward, we have recognized the need to stay updated with the changes and enhancements in our
build tools to maximize our development efficiency and ensure the robustness of our builds.
