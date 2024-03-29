import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "2.4.2"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    kotlin("jvm") version "1.4.21"
    kotlin("plugin.spring") version "1.4.21"
}

group = "snapshot-sender"
java.sourceCompatibility = JavaVersion.VERSION_1_8
version= "0.0.0"

repositories {
    mavenCentral()
    jcenter()
    maven(url = "https://jitpack.io")
}

dependencies {
    // spring
    implementation("org.springframework.boot:spring-boot-starter-batch")
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.bouncycastle:bcprov-ext-jdk15on:1.62")
    implementation("org.apache.httpcomponents:httpclient:4.5.13")
    implementation("com.google.code.gson:gson:2.8.5")
    implementation("org.apache.commons:commons-compress:1.20")
    implementation("org.apache.commons:commons-text:1.8")
    implementation("com.github.dwp:dataworks-common-logging:0.0.6")

    // sdk v1
    implementation("com.amazonaws:aws-java-sdk-dynamodb:1.11.946")
    implementation("com.amazonaws:aws-java-sdk-s3:1.11.946")
    implementation("com.amazonaws:aws-java-sdk-core:1.11.946")    
    implementation("com.amazonaws:aws-java-sdk-sns:1.11.946")
    implementation("com.amazonaws:aws-java-sdk-sqs:1.11.946")

    //metrics
    implementation("io.micrometer:micrometer-core:1.6.3")
    implementation("io.micrometer:micrometer-registry-prometheus:1.6.3")
    implementation("io.prometheus:simpleclient:0.9.0")
    implementation("io.prometheus:simpleclient_pushgateway:0.9.0")
    implementation("io.prometheus:simpleclient_logback:0.9.0")
    implementation("io.prometheus:simpleclient_caffeine:0.9.0")
    implementation("io.prometheus:simpleclient_spring_web:0.9.0")

    // tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.batch:spring-batch-test")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
    testImplementation("com.beust", "klaxon", "4.0.2")

    // integration tests
    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.3.2")
    testImplementation("io.kotest:kotest-assertions-core-jvm:4.3.2")
    testImplementation("io.kotest:kotest-assertions-json-jvm:4.3.2")
    testImplementation("org.apache.httpcomponents:fluent-hc:4.5.13")
    testImplementation("io.ktor:ktor-client-core:1.5.1")
    testImplementation("io.ktor:ktor-client-gson:1.5.1")
    testImplementation("io.ktor:ktor-client-apache:1.5.1")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "1.8"
    }
}

sourceSets {
    create("integration") {
        java.srcDir(file("src/integration/kotlin"))
        compileClasspath += sourceSets.getByName("main").output + configurations.testRuntimeClasspath
        runtimeClasspath += output + compileClasspath
    }
}

tasks.register<Test>("integration") {
    description = "Runs the integration tests"
    group = "verification"
    testClassesDirs = sourceSets["integration"].output.classesDirs
    classpath = sourceSets["integration"].runtimeClasspath
    useJUnitPlatform()
    testLogging {
        exceptionFormat = TestExceptionFormat.FULL
        events = setOf(TestLogEvent.SKIPPED, TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.STANDARD_OUT)
    }
}
