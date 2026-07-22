import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    // IntelliJ 플러그인 빌드/실행(runIde)/테스트를 담당하는 공식 Gradle 플러그인 (2.x)
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "io.github.jeongdonghee"
version = "0.2.0"

repositories {
    mavenCentral()
    // IntelliJ 플랫폼 아티팩트(IDE 자체 등)를 받아오는 저장소들
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // gradle.properties 의 platformType/platformVersion 으로 타겟 IDE 결정
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))

        // 번들 플러그인 의존 (쉼표 구분 문자열 -> 리스트)
        bundledPlugins(
            providers.gradleProperty("platformBundledPlugins").map { csv ->
                csv.split(',').map(String::trim).filter(String::isNotEmpty)
            }
        )

        // "중간 테스트"용 플랫폼 테스트 프레임워크 (IDE 안 띄우고 분석 로직 검증)
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
    }
}

// 설정 검색 인덱스 생성 태스크는 헤드리스 IDE 를 띄워 runIde 샌드박스와 충돌한다. 선택사항이라 끈다.
tasks.named("buildSearchableOptions") {
    enabled = false
}
