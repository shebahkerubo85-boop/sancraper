plugins { kotlin("jvm"); kotlin("plugin.serialization"); application }
kotlin { jvmToolchain(17) }
dependencies {
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
 implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
 implementation("com.squareup.okhttp3:okhttp:5.1.0")
 implementation("org.jsoup:jsoup:1.21.2")
 implementation("org.mozilla:rhino:1.7.15")
 implementation("io.ktor:ktor-server-netty-jvm:3.6.0")
 implementation("io.ktor:ktor-server-content-negotiation-jvm:3.6.0")
 implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.6.0")
 testImplementation(kotlin("test"))
 testImplementation("io.ktor:ktor-server-test-host-jvm:3.6.0")
}
application { mainClass.set("ani.sanin.universal.MainKt") }
tasks.test { useJUnitPlatform() }
