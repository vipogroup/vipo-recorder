pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    @Suppress("JcenterRepositoryObsolete")
    jcenter()
  }
}
rootProject.name = "VIPORecorder"
include(":app")
include(":kidlauncher")
