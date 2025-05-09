package com.kalpesh.women_safety
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class BlogActivity : AppCompatActivity() {

    private lateinit var currentLocationOnMap: ImageView
    private lateinit var logoutButton: ImageView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blog)

        logoutButton = findViewById(R.id.logoutBtn)
        currentLocationOnMap = findViewById(R.id.location_icon)
        val webView = findViewById<WebView>(R.id.webview)

        currentLocationOnMap.setOnClickListener{
            val intent = Intent(this, CurrentLocationMap::class.java)
            startActivity(intent)
        }

        logoutButton.setOnClickListener {

            AlertDialog.Builder(this)
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Yes") { dialog, _ ->
                    FirebaseAuth.getInstance().signOut()
                    // Optional: Clear activity stack and go to login screen
                    val intent = Intent(this, Login::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("No") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }

        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
        webView.loadUrl("https://www.unwomen.org/en")
    }

}
