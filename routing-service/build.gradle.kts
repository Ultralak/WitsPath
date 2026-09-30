plugins {
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

application {
    mainClass.set("com.example.witspath.service.RouteServer")
}

dependencies {
    implementation(project(":routing-core"))
    testImplementation(libs.junit)
}
