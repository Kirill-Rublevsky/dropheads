plugins {
    java
    id("com.gradleup.shadow") version "9.2.2"
}

group = "io.github.kirillrublevsky"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("org.xerial:sqlite-jdbc:3.51.0.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.6")
    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
}

tasks {
    test { useJUnitPlatform() }
    processResources {
        filesMatching("plugin.yml") { expand("version" to project.version) }
    }
    shadowJar {
        archiveClassifier.set("")
        relocate("com.zaxxer.hikari", "io.github.kirillrublevsky.soulblade.lib.hikari")
        relocate("org.sqlite", "io.github.kirillrublevsky.soulblade.lib.sqlite")
        relocate("org.mariadb.jdbc", "io.github.kirillrublevsky.soulblade.lib.mariadb")
        mergeServiceFiles()
    }
    build { dependsOn(shadowJar) }
}
