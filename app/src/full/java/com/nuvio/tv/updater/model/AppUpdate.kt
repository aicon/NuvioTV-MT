package com.nuvio.tv.updater.model

data class AppUpdate(
    val tag: String,
    val title: String,
    val notes: String,
    val releaseUrl: String?,
    val assetName: String,
    val assetUrl: String,
    val assetSizeBytes: Long?,
    /** GitHub asset digest (`sha256:<hex>`), when the release publishes one. */
    val assetDigest: String? = null
)
