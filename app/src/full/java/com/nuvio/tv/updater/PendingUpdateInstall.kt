package com.nuvio.tv.updater

/**
 * Install attempt recorded before PackageInstaller / system installer runs, so
 * [UpdatePackageReplacedReceiver] and the next cold start can tell success from cancel.
 */
data class PendingUpdateInstall(
    val source: Source,
    val tag: String,
    val versionCode: Int?
) {
    enum class Source {
        MANUAL,
        IDLE
    }
}
