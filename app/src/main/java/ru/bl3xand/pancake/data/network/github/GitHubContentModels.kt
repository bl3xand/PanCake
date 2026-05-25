package ru.bl3xand.pancake.data.network.github

import com.google.gson.annotations.SerializedName

data class GitHubPutContentRequest(
    val message: String,
    val content: String,
    val branch: String,
)

data class GitHubPutContentResponse(
    val content: GitHubContentInfo? = null,
)

data class GitHubContentInfo(
    val path: String? = null,
    @SerializedName("download_url")
    val downloadUrl: String? = null,
)

