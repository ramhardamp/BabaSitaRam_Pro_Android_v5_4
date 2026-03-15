package com.babasitaram.pro

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.security.SecureRandom

class AddEditActivity : AppCompatActivity() {

    private var editingId: String? = null
    private lateinit var etSite: EditText
    private lateinit var etUrl: EditText
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var etNotes: EditText
    private lateinit var spinnerCat: Spinner
    private lateinit var btnSave: Button
    private lateinit var btnGenerate: Button
    private lateinit var btnShowPass: ImageButton
    private lateinit var tvTitle: TextView

    private val categories = arrayOf("Banking", "Social", "Email", "Work", "Shopping", "Games", "Other")
    private var passwordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit)

        etSite      = findViewById(R.id.etSite)
        etUrl       = findViewById(R.id.etUrl)
        etUsername  = findViewById(R.id.etUsername)
        etPassword  = findViewById(R.id.etPassword)
        etNotes     = findViewById(R.id.etNotes)
        spinnerCat  = findViewById(R.id.spinnerCategory)
        btnSave     = findViewById(R.id.btnSave)
        btnGenerate = findViewById(R.id.btnGenerate)
        btnShowPass = findViewById(R.id.btnShowPass)
        tvTitle     = findViewById(R.id.tvTitle)

        // Spinner
        spinnerCat.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)

        // Load existing entry if editing
        editingId = intent.getStringExtra("entry_id")
        editingId?.let { id ->
            tvTitle.text = "Edit Password"
            val entry = VaultManager.getPasswords().find { it.id == id }
            entry?.let {
                etSite.setText(it.site)
                etUrl.setText(it.url)
                etUsername.setText(it.username)
                etPassword.setText(it.password)
                etNotes.setText(it.notes)
                spinnerCat.setSelection(categories.indexOf(it.category).coerceAtLeast(0))
            }
        }

        btnSave.setOnClickListener { save() }
        btnGenerate.setOnClickListener { generatePassword() }
        btnShowPass.setOnClickListener { togglePasswordVisibility() }
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun save() {
        val site = etSite.text.toString().trim()
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString()

        if (site.isEmpty()) { toast("Site name zaroori hai"); return }
        if (username.isEmpty()) { toast("Username zaroori hai"); return }
        if (password.isEmpty()) { toast("Password zaroori hai"); return }

        val entry = PasswordEntry(
            id = editingId ?: java.util.UUID.randomUUID().toString(),
            site = site,
            url = etUrl.text.toString().trim(),
            username = username,
            password = password,
            notes = etNotes.text.toString().trim(),
            category = spinnerCat.selectedItem.toString(),
            updatedAt = System.currentTimeMillis()
        )

        if (editingId != null) VaultManager.updatePassword(this, entry)
        else VaultManager.addPassword(this, entry)

        toast("✓ Saved!")
        finish()
    }

    private fun generatePassword() {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#\$%^&*"
        val rng = SecureRandom()
        val pw = (1..16).map { chars[rng.nextInt(chars.length)] }.joinToString("")
        etPassword.setText(pw)
        toast("Strong password generated!")
    }

    private fun togglePasswordVisibility() {
        passwordVisible = !passwordVisible
        val inputType = if (passwordVisible)
            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        else
            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        etPassword.inputType = inputType
        etPassword.setSelection(etPassword.text.length)
        btnShowPass.setImageResource(if (passwordVisible) android.R.drawable.ic_menu_view else android.R.drawable.ic_secure)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
