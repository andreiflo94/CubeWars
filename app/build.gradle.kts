import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
}

/**
 * libGDX ships its Android native libraries as plain jars that contain `libgdx.so` at the archive
 * root, so the Android packager cannot consume them directly. This task explodes each
 * `gdx-platform:<version>:natives-<abi>` jar into `<out>/<abi>/libgdx.so`, which is the layout AGP
 * expects from a `jniLibs` source directory.
 */
abstract class UnpackGdxNativesTask : DefaultTask() {

    @get:InputFiles
    abstract val nativeJars: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val archives: ArchiveOperations

    @get:Inject
    abstract val files: FileSystemOperations

    @TaskAction
    fun unpack() {
        val target = outputDirectory.get().asFile
        target.deleteRecursively()
        nativeJars.forEach { jar ->
            val abi = jar.name.substringAfterLast("natives-").removeSuffix(".jar")
            files.copy {
                from(archives.zipTree(jar)) { include("**/*.so") }
                into(target.resolve(abi))
            }
        }
    }
}

val gdxNatives: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val unpackGdxNatives = tasks.register<UnpackGdxNativesTask>("unpackGdxNatives") {
    description = "Extracts libGDX .so files into a jniLibs-shaped directory."
    nativeJars.from(gdxNatives)
    outputDirectory.set(layout.buildDirectory.dir("gdx/jniLibs"))
}

android {
    namespace = "com.heixss.cubewars"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.heixss.cubewars"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(
            unpackGdxNatives,
            UnpackGdxNativesTask::outputDirectory,
        )
    }
}

dependencies {
    // The game needs nothing from AndroidX: libGDX owns the window and draws every pixel, so the
    // only runtime dependencies are the engine and coroutines.
    implementation(libs.gdx.core)
    implementation(libs.gdx.backend.android)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    val gdxVersion = libs.versions.gdx.get()
    gdxNatives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
    gdxNatives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
    gdxNatives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86")
    gdxNatives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
