pluginManagement {
    repositories { google(); maven("https://maven-central.storage-download.googleapis.com/maven2/"); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Зеркало Maven Central от Google: обходит редиректы крупных файлов на gcp.mirrors.maven.org
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}
rootProject.name = "Zdorovyak"
include(":app")
