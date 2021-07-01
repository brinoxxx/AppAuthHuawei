package com.hms.demo.appauthhuawei

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import net.openid.appauth.*
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity(), View.OnClickListener {

    val AUTH_ENDPOINT = "https://oauth-login.cloud.huawei.com/oauth2/v3/authorize"
    val TOKEN_ENDPOINT = "https://oauth-login.cloud.huawei.com/oauth2/v3/token"
    val APP_ID = "102839067"
    val HW_REDIRECT_URI = "com.huawei.apps.$APP_ID:/oauth2redirect"
    val HW_ID_CODE = 100
    val TAG = "Login"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hwid.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.hwid -> handleHuaweiId()
        }
    }

    private fun handleHuaweiId() {

        val serviceConfig = AuthorizationServiceConfiguration(
            Uri.parse(AUTH_ENDPOINT), // authorization endpoint
            Uri.parse(TOKEN_ENDPOINT)// token endpoint
        )
        val authRequestBuilder = AuthorizationRequest.Builder(
            serviceConfig,  // the authorization service configuration
            APP_ID,  // the client ID, typically pre-registered and static
            ResponseTypeValues.CODE,  //
            Uri.parse(HW_REDIRECT_URI)//The redirect URI
        )

        authRequestBuilder.setScope("openid email profile")
        val authRequest = authRequestBuilder.build()
        val authService = AuthorizationService(this)
        val authIntent = authService.getAuthorizationRequestIntent(authRequest)
        startActivityForResult(authIntent, HW_ID_CODE)
        authService.dispose()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            HW_ID_CODE -> handleHuaweiSignIn(data)
        }
    }

    private fun handleHuaweiSignIn(data: Intent?) {
        if (data == null) {
            return
        }

        val response = AuthorizationResponse.fromIntent(data)
        val ex = AuthorizationException.fromIntent(data)
        val authState = AuthState(response, ex)
        if (response == null) {
            Snackbar.make(hwid, "Response is null, please try again", Snackbar.LENGTH_SHORT).show()
            return
        }
        val service = AuthorizationService(this)
        service.performTokenRequest(
            response.createTokenExchangeRequest(),
        ) { tokenResponse, exception ->
            service.dispose()
            if (tokenResponse == null) {
                Snackbar.make(
                    hwid,
                    "Token Exchange failed ${exception?.code}",
                    Snackbar.LENGTH_SHORT
                ).show()
            } else {
                authState.update(tokenResponse, exception)
                hwid.visibility = View.GONE
                obtainUserInfo(tokenResponse.idToken)
            }
        }
    }

    private fun obtainUserInfo(idToken: String?) {
        CoroutineScope(IO).launch {
            if (idToken == null) return@launch
            val json = decoded(idToken)
            if (json != null) {
                Log.e("JSON", json.toString())
                runOnUiThread {
                    name.text = json.getString("display_name")
                    name.visibility = View.VISIBLE
                    if (json.has("email")) {
                        mail.text = json.getString("email")
                        mail.visibility = View.VISIBLE
                    }
                }
                val bitmap = getBitmap(json.getString("picture"))
                if (bitmap != null) {
                    val resizedBitmap = getResizedBitmap(bitmap, 480, 480)
                    runOnUiThread {
                        pic.setImageBitmap(resizedBitmap)
                        pic.visibility = View.VISIBLE
                    }
                }
            }
            //val conn=URL("https://oauth-login.cloud.huawei.com/.well-known/openid-configuration").openConnection() as HttpURLConnection
            //conn.requestMethod="POST"

        }
    }

    @Throws(Exception::class)
    private fun decoded(JWTEncoded: String): JSONObject? {

        try {
            val split = JWTEncoded.split(".").toTypedArray()
            return JSONObject(getJson(split[1]))
        } catch (e: UnsupportedEncodingException) {
            //Error
            return null
        }
    }

    @Throws(UnsupportedEncodingException::class)
    private fun getJson(strEncoded: String): String {
        val decodedBytes: ByteArray = Base64.decode(strEncoded, Base64.URL_SAFE)
        return String(decodedBytes, StandardCharsets.UTF_8)
    }
}

private fun getBitmap(avatarUriString: String?): Bitmap? {
    try {
        val url = URL(avatarUriString)
        val connection = url.openConnection()
        connection.doInput = true
        connection.connect()
        val input: InputStream = connection.getInputStream()
        return BitmapFactory.decodeStream(input)
    } catch (e: Exception) {
        return null
    }
}

private fun getResizedBitmap(bitmap: Bitmap, newHeight: Int, newWidth: Int): Bitmap {
    val width: Int = bitmap.width
    val height: Int = bitmap.height
    val scaleWidth = newWidth.toFloat() / width
    val scaleHeight = newHeight.toFloat() / height
    // CREATE A MATRIX FOR THE MANIPULATION
    val matrix = Matrix()
    // RESIZE THE BIT MAP
    matrix.postScale(scaleWidth, scaleHeight)
    // "RECREATE" THE NEW BITMAP
    return Bitmap.createBitmap(
        bitmap, 0, 0, width, height,
        matrix, false
    )
}

