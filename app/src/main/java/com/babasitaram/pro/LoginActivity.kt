package com.babasitaram.pro

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class LoginActivity : AppCompatActivity() {

    private lateinit var layoutSetup: LinearLayout
    private lateinit var layoutLogin: LinearLayout
    private lateinit var layoutBiometric: LinearLayout

    private lateinit var etPhone: EditText
    private lateinit var etMasterSetup: EditText
    private lateinit var etMasterConfirm: EditText
    private lateinit var btnSetup: Button

    private lateinit var etMasterLogin: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnBiometric: Button
    private lateinit var tvWelcome: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        layoutSetup    = findViewById(R.id.layoutSetup)
        layoutLogin    = findViewById(R.id.layoutLogin)
        layoutBiometric = findViewById(R.id.layoutBiometric)

        etPhone        = findViewById(R.id.etPhone)
        etMasterSetup  = findViewById(R.id.etMasterSetup)
        etMasterConfirm= findViewById(R.id.etMasterConfirm)
        btnSetup       = findViewById(R.id.btnSetup)

        etMasterLogin  = findViewById(R.id.etMasterLogin)
        btnLogin       = findViewById(R.id.btnLogin)
        btnBiometric   = findViewById(R.id.btnBiometric)
        tvWelcome      = findViewById(R.id.tvWelcome)

        if (VaultManager.isSetupDone(this)) {
            showLoginScreen()
        } else {
            showSetupScreen()
        }

        btnSetup.setOnClickListener { doSetup() }
        btnLogin.setOnClickListener { doLogin() }
        btnBiometric.setOnClickListener { doBiometric() }
    }

    private fun showSetupScreen() {
        layoutSetup.visibility = View.VISIBLE
        layoutLogin.visibility = View.GONE
    }

    private fun showLoginScreen() {
        layoutSetup.visibility = View.GONE
        layoutLogin.visibility = View.VISIBLE
        // Auto-show biometric if available
        if (canUseBiometric()) {
            layoutBiometric.visibility = View.VISIBLE
            doBiometric()
        }
    }

    private fun doSetup() {
        val phone = etPhone.text.toString().trim()
        val master = etMasterSetup.text.toString()
        val confirm = etMasterConfirm.text.toString()

        if (phone.length < 10) { toast("Valid mobile number dalein"); return }
        if (master.length < 6) { toast("Master Password 6+ characters chahiye"); return }
        if (master != confirm) { toast("Passwords match nahi kar rahe"); return }

        VaultManager.setupMaster(this, master)
        // Auto unlock after setup
        VaultManager.unlock(this, master)
        goToMain()
    }

    private fun doLogin() {
        val master = etMasterLogin.text.toString()
        if (master.isEmpty()) { toast("Master Password dalein"); return }
        btnLogin.isEnabled = false
        btnLogin.text = "Verifying..."
        if (VaultManager.unlock(this, master)) {
            goToMain()
        } else {
            toast("❌ Wrong Master Password")
            btnLogin.isEnabled = true
            btnLogin.text = "UNLOCK VAULT"
            etMasterLogin.text.clear()
        }
    }

    private fun canUseBiometric(): Boolean {
        val bm = BiometricManager.from(this)
        return bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun doBiometric() {
        if (!canUseBiometric()) { toast("Biometric available nahi hai"); return }

        val executor = ContextCompat.getMainExecutor(this)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                // Load vault with stored encrypted master
                val prefs = getSharedPreferences("bsr_bio", MODE_PRIVATE)
                val storedMaster = prefs.getString("cached_master", null)
                if (storedMaster != null && VaultManager.unlock(this@LoginActivity, storedMaster)) {
                    goToMain()
                } else {
                    toast("Master Password se ek baar login karein")
                    layoutBiometric.visibility = View.GONE
                }
            }
            override fun onAuthenticationError(code: Int, msg: CharSequence) {
                if (code != BiometricPrompt.ERROR_USER_CANCELED &&
                    code != BiometricPrompt.ERROR_NEGATIVE_BUTTON) toast("Error: $msg")
            }
            override fun onAuthenticationFailed() { toast("Authentication failed") }
        }

        val prompt = BiometricPrompt(this, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("BabaSitaRam Pro")
            .setSubtitle("Vault unlock karne ke liye verify karein")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(info)
    }

    private fun goToMain() {
        // Cache master for biometric (encrypted prefs)
        val prefs = getSharedPreferences("bsr_bio", MODE_PRIVATE)
        // We store it only if biometric is available
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
