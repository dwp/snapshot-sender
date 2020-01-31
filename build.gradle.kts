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
	jcenter()
	maven(url="https://jitpack.io")
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
	implementation("org.apache.commons:commons-text:1.8")

	// sdk v1
	implementation("com.amazonaws:aws-java-sdk-s3:1.11.603")
	implementation("com.amazonaws:aws-java-sdk-core:1.11.603")

	// tests
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.batch:spring-batch-test")
	testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
	testImplementation("com.beust", "klaxon", "4.0.2")
	// integration tests
	testImplementation("org.apache.commons:commons-compress:1.5")
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
	environment("S3_BUCKET", System.getenv("S3_BUCKET"))
	environment("S3_PREFIX_FOLDER", System.getenv("S3_PREFIX_FOLDER"))
	environment("S3_HTME_ROOT_FOLDER", System.getenv("S3_HTME_ROOT_FOLDER"))
	environment("S3_STATUS_FOLDER", System.getenv("S3_STATUS_FOLDER"))
	environment("S3_SERVICE_ENDPOINT", System.getenv("S3_SERVICE_ENDPOINT"))
	environment("NIFI_ROOT_FOLDER", System.getenv("NIFI_ROOT_FOLDER"))
	environment("NIFI_FILE_NAMES_CSV", System.getenv("NIFI_FILE_NAMES_CSV"))
	environment("NIFI_TIME_STAMPS_CSV", System.getenv("NIFI_TIME_STAMPS_CSV"))
	environment("NIFI_LINE_COUNTS_CSV", System.getenv("NIFI_LINE_COUNTS_CSV"))

	useJUnitPlatform { }
	testLogging {
		exceptionFormat = TestExceptionFormat.FULL
		events = setOf(TestLogEvent.SKIPPED, TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.STANDARD_OUT)
	}
}
