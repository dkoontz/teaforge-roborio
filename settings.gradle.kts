import me.champeau.gradle.igp.gitRepositories
rootProject.name = "teaforge-roborio"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention").version("0.9.0")
    id("me.champeau.includegit") version "0.2.0"
}


gitRepositories {
    include("teaforge") {
        uri.set("https://github.com/dkoontz/teaforge.git")
        branch.set("main")
        tag.set("0.0.1")
    }
}
