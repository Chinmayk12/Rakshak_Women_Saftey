package com.kalpesh.women_safety
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class BlogActivity : AppCompatActivity() {

    private lateinit var currentLocationOnMap: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blog)

        currentLocationOnMap = findViewById(R.id.location_icon)
        val webView = findViewById<WebView>(R.id.webview)

        currentLocationOnMap.setOnClickListener{
            val intent = Intent(this, CurrentLocationMap::class.java)
            startActivity(intent)
        }

        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
        webView.loadUrl("https://www.unwomen.org/en")
    }

}
