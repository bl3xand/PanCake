package ru.bl3xand.pancake.ui.activity

import android.content.Intent
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.net.toUri
import com.google.android.material.appbar.MaterialToolbar
import ru.bl3xand.pancake.R

class OpenSourceLicensesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_open_source_licenses)

        setupToolbar(findViewById(R.id.licensesToolbar))
        setupWebView(findViewById(R.id.licensesWebView))
    }

    private fun setupToolbar(toolbar: MaterialToolbar) {
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = topInset)
            insets
        }
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupWebView(webView: WebView) {
        webView.settings.javaScriptEnabled = false
        webView.settings.domStorageEnabled = false
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.loadsImagesAutomatically = true

        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString().orEmpty()
                if (url.isBlank()) return true
                startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                return true
            }
        }

        webView.loadDataWithBaseURL(
            null,
            buildLicensesHtml(),
            "text/html",
            "utf-8",
            null
        )
    }

    private fun buildLicensesHtml(): String {
        val sections = listOf(
            "AndroidX" to listOf(
                "androidx.core:core-ktx (Apache-2.0)",
                "androidx.appcompat:appcompat (Apache-2.0)",
                "androidx.activity:activity (Apache-2.0)",
                "androidx.fragment:fragment-ktx (Apache-2.0)",
                "androidx.constraintlayout:constraintlayout (Apache-2.0)",
                "androidx.lifecycle:lifecycle-viewmodel-ktx (Apache-2.0)",
                "androidx.lifecycle:lifecycle-livedata-ktx (Apache-2.0)",
                "androidx.navigation:navigation-ui-ktx (Apache-2.0)",
                "androidx.work:work-runtime-ktx (Apache-2.0)",
                "androidx.preference:preference-ktx (Apache-2.0)",
                "androidx.legacy:legacy-support-v4 (Apache-2.0)",
                "androidx.credentials:credentials (Apache-2.0)",
                "androidx.credentials:credentials-play-services-auth (Apache-2.0)",
                "androidx.test.ext:junit (Apache-2.0)",
                "androidx.test.espresso:espresso-core (Apache-2.0)"
            ),
            "Google/Firebase" to listOf(
                "com.google.android.material:material (Apache-2.0)",
                "com.google.firebase:firebase-analytics (Apache-2.0)",
                "com.google.firebase:firebase-auth-ktx (Apache-2.0)",
                "com.google.firebase:firebase-database (Apache-2.0)",
                "com.google.firebase:firebase-bom (Apache-2.0)",
                "com.google.android.libraries.identity.googleid:googleid (Google API Terms)"
            ),
            "Networking and Markdown" to listOf(
                "com.squareup.retrofit2:retrofit (Apache-2.0)",
                "com.squareup.retrofit2:converter-gson (Apache-2.0)",
                "io.noties.markwon:core (Apache-2.0)",
                "io.noties.markwon:image-glide (Apache-2.0)",
                "io.noties.markwon:linkify (Apache-2.0)"
            ),
            "Media and UI" to listOf(
                "com.github.bumptech.glide:glide (BSD, MIT and Apache-2.0 mix)",
                "com.github.bumptech.glide:compiler (BSD, MIT and Apache-2.0 mix)",
                "com.github.chrisbanes:PhotoView (Apache-2.0)",
                "com.journeyapps:zxing-android-embedded (Apache-2.0)",
                "com.google.zxing:core (Apache-2.0)"
            ),
            "Testing" to listOf(
                "junit:junit (EPL-1.0)"
            )
        )

        val links = listOf(
            "AndroidX" to "https://developer.android.com/jetpack/androidx",
            "Material Components" to "https://github.com/material-components/material-components-android",
            "Firebase Android SDK" to "https://github.com/firebase/firebase-android-sdk",
            "Retrofit" to "https://github.com/square/retrofit",
            "Markwon" to "https://github.com/noties/Markwon",
            "Glide" to "https://github.com/bumptech/glide",
            "PhotoView" to "https://github.com/chrisbanes/PhotoView",
            "ZXing" to "https://github.com/zxing/zxing",
            "ZXing Android Embedded" to "https://github.com/journeyapps/zxing-android-embedded",
            "Google Identity" to "https://developers.google.com/identity",
            "JUnit" to "https://junit.org/"
        )

        val sectionsHtml = sections.joinToString(separator = "") { (title, items) ->
            val itemsHtml = items.joinToString(separator = "") { "<li>$it</li>" }
            """
                <section>
                    <h2>$title</h2>
                    <ul>$itemsHtml</ul>
                </section>
            """.trimIndent()
        }

        val linksHtml = links.joinToString(separator = "") { (title, url) ->
            "<li><a href=\"$url\">$title</a></li>"
        }

        return """
            <!doctype html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <style>
                    body { font-family: sans-serif; padding: 16px; color: #1f1f1f; }
                    h1 { margin: 0 0 12px; font-size: 24px; }
                    h2 { margin: 20px 0 8px; font-size: 18px; }
                    p { line-height: 1.4; }
                    ul { padding-left: 18px; margin: 8px 0; }
                    li { margin: 6px 0; line-height: 1.35; }
                    a { color: #1565c0; text-decoration: none; }
                </style>
            </head>
            <body>
                <h1>Open-source licenses</h1>
                <p>Ниже перечислены ключевые open-source зависимости, используемые в проекте.</p>
                $sectionsHtml
                <h2>Официальные страницы</h2>
                <ul>$linksHtml</ul>
            </body>
            </html>
        """.trimIndent()
    }
}

