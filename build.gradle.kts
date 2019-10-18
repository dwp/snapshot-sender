import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
	id("org.springframework.boot") version "2.1.8.RELEASE"
	id("io.spring.dependency-management") version "1.0.8.RELEASE"
	kotlin("jvm") version "1.3.21"
	kotlin("plugin.spring") version "1.3.21"
}

group = "snapshot-sender"
java.sourceCompatibility = JavaVersion.VERSION_1_8

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-batch")
	implementation("org.springframework.retry:spring-retry")
	implementation("org.springframework.boot:spring-boot-starter-aop")

	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation("org.bouncycastle:bcprov-ext-jdk15on:1.62")
	implementation("org.apache.httpcomponents:httpclient:4.5.10")
	implementation("com.google.code.gson:gson:2.8.5")
	implementation("org.apache.commons:commons-compress:1.5")

	// sdk v1
	implementation("com.amazonaws:aws-java-sdk-s3:1.11.603")
	implementation("com.amazonaws:aws-java-sdk-core:1.11.603")

	// tests
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.batch:spring-batch-test")
	testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
	// integration tests
	testImplementation("io.kotlintest:kotlintest-runner-junit5:3.3.2")
	testImplementation("org.apache.httpcomponents:fluent-hc:4.5.10")
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

	useJUnitPlatform { }
	testLogging {
		exceptionFormat = TestExceptionFormat.FULL
		events = setOf(TestLogEvent.SKIPPED, TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.STANDARD_OUT)
	}
}
