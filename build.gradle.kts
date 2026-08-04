plugins {
    java
    id("com.gradleup.shadow") version "8.3.11"
}

group = "com.npcvoice"
version = "1.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.citizensnpcs.co/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://maven.maxhenkel.de/repository/public")
    maven("https://repo.alessiodp.com/releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("net.citizensnpcs:citizens-main:2.0.35-SNAPSHOT") {
        exclude(group = "io.papermc.paper")
        exclude(group = "net.byteflux")
        exclude(group = "com.mojang")
        exclude(group = "net.minecraft")
    }
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.5.36")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.12")
    compileOnly("net.luckperms:api:5.5")
    compileOnly("net.kyori:adventure-api:4.17.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.17.0")
    implementation("org.jetbrains:annotations:24.1.0")
    implementation("javazoom:jlayer:1.0.1")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }

    shadowJar {
        archiveBaseName.set("NPCVoice")
        archiveClassifier.set("")
        archiveVersion.set(project.version.toString())
        minimize()
    }

    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }
}
