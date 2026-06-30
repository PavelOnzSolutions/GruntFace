import java.util.Properties

plugins {
    java
    application
    groovy
    id("org.javamodularity.moduleplugin") version "2.0.1"
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.beryx.jlink") version "4.0.2"
    id("org.graalvm.buildtools.native") version "1.1.1"
}

group = "solutions.onz.toolbox.gruntface"
// version is on the tail of this file

val propsFile = file("gradle.properties")
val props = Properties().apply { load(propsFile.inputStream()) }

repositories {
    mavenCentral()
}

val junitVersion = "5.12.1"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

graalvmNative {
    toolchainDetection.set(true)
    binaries {
        named("main") {
            imageName.set("gruntface")
            mainClass.set("solutions.onz.toolbox.gruntface.app.Launcher")
            sharedLibrary.set(true)
            useFatJar.set(true)
        }
    }
}

application {
    mainModule.set("solutions.onz.toolbox.gruntface")
    mainClass.set("solutions.onz.toolbox.gruntface.app.GruntFaceApplication")
}

javafx {
    version = "25.0.2"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.swing", "javafx.graphics")
}

tasks.withType<org.gradle.jvm.tasks.Jar> {
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

dependencies {
    implementation("org.controlsfx:controlsfx:11.2.1")
    implementation("com.dlsc.formsfx:formsfx-core:11.6.0") {
        exclude(group = "org.openjfx")
    }
    implementation("org.kordamp.ikonli:ikonli-javafx:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-feather-pack:12.4.0")
    implementation("io.github.mkpaz:atlantafx-base:2.0.1")
    implementation("org.fxmisc.richtext:richtextfx:0.11.7")
    implementation("com.bertramlabs.plugins:hcl4j:0.9.8")
    implementation("org.commonmark:commonmark:0.22.0")
    implementation("org.eclipse.elk:org.eclipse.elk.core:0.11.0")
    implementation("org.eclipse.elk:org.eclipse.elk.alg.layered:0.11.0")
    implementation("org.eclipse.elk:org.eclipse.elk.alg.rectpacking:0.11.0")
    implementation("org.eclipse.elk:org.eclipse.elk.alg.force:0.11.0")
    implementation("org.eclipse.elk:org.eclipse.elk.graph:0.11.0")
    implementation("org.eclipse.xtext:org.eclipse.xtext.xbase.lib:2.31.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:${junitVersion}")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:${junitVersion}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.1")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

jlink {
    imageZip.set(layout.buildDirectory.file("/distributions/app-${javafx.platform.classifier}.zip"))
    options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
    launcher {
        name = "app"
    }
}

tasks.register("incrementBuild") {
    group = "versioning"
    description = "Increment build number in gradle.properties"
    dependsOn("classes")
    doLast {
        val buildNum = props["buildNumber"].toString().toInt()
        val newBuildNum = buildNum + 1
        props["buildNumber"] = newBuildNum.toString()

        // Save updated properties
        props.store(propsFile.outputStream(), null)

        println("Build number incremented to $newBuildNum")
    }
}


version = "${props["version"]}.${props["buildNumber"]}"

