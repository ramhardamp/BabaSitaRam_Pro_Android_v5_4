package com.babasitaram.pro

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var rvPasswords: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var fabAdd: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var tvEmpty: TextView
    private lateinit var tvCount: TextView
    private var adapter: PasswordAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!VaultManager.isUnlocked) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish(); return
        }

        rvPasswords = findViewById(R.id.rvPasswords)
        etSearch    = findViewById(R.id.etSearch)
        fabAdd      = findViewById(R.id.fabAdd)
        tvEmpty     = findViewById(R.id.tvEmpty)
        tvCount     = findViewById(R.id.tvCount)

        setupRecyclerView()
        setupSearch()
        fabAdd.setOnClickListener { openAddEdit(null) }
        renderList()
    }

    override fun onResume() {
        super.onResume()
        if (!VaultManager.isUnlocked) { startActivity(Intent(this, LoginActivity::class.java)); finish(); return }
        renderList()
    }

    private fun setupRecyclerView() {
        rvPasswords.layoutManager = LinearLayoutManager(this)
        adapter = PasswordAdapter(
            onEdit = { openAddEdit(it) },
            onDelete = { confirmDelete(it) },
            onCopyUser = { copyToClipboard("Username", it.username) },
            onCopyPass = { copyToClipboard("Password", it.password) }
        )
        rvPasswords.adapter = adapter
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString() ?: ""
                val list = if (q.isEmpty()) VaultManager.getPasswords()
                           else VaultManager.searchPasswords(q)
                adapter?.submitList(list)
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun renderList() {
        val list = VaultManager.getPasswords()
        adapter?.submitList(list)
        tvCount.text = "${list.size} passwords"
        tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openAddEdit(entry: PasswordEntry?) {
        val intent = Intent(this, AddEditActivity::class.java)
        entry?.let { intent.putExtra("entry_id", it.id) }
        startActivity(intent)
    }

    private fun confirmDelete(entry: PasswordEntry) {
        AlertDialog.Builder(this)
            .setTitle("Delete Password?")
            .setMessage("\"${entry.site}\" delete karna chahte ho?")
            .setPositiveButton("Delete") { _, _ ->
                VaultManager.deletePassword(this, entry.id)
                renderList()
                toast("Deleted!")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun copyToClipboard(label: String, text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        toast("$label copied!")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

// ── Adapter ──
class PasswordAdapter(
    private val onEdit: (PasswordEntry) -> Unit,
    private val onDelete: (PasswordEntry) -> Unit,
    private val onCopyUser: (PasswordEntry) -> Unit,
    private val onCopyPass: (PasswordEntry) -> Unit
) : RecyclerView.Adapter<PasswordAdapter.VH>() {

    private var list: List<PasswordEntry> = emptyList()

    fun submitList(newList: List<PasswordEntry>) {
        list = newList; notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvSite: TextView     = view.findViewById(R.id.tvSite)
        val tvUser: TextView     = view.findViewById(R.id.tvUser)
        val tvCat: TextView      = view.findViewById(R.id.tvCategory)
        val tvAvatar: TextView   = view.findViewById(R.id.tvAvatar)
        val btnCopyUser: ImageButton = view.findViewById(R.id.btnCopyUser)
        val btnCopyPass: ImageButton = view.findViewById(R.id.btnCopyPass)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
        val btnDel: ImageButton  = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_password, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = list[position]
        holder.tvSite.text  = entry.site.ifEmpty { "Unknown Site" }
        holder.tvUser.text  = entry.username
        holder.tvCat.text   = entry.category
        holder.tvAvatar.text = entry.site.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        // Avatar background color by category
        val colors = mapOf(
            "Banking" to 0xFF1565C0.toInt(), "Social" to 0xFF6A1B9A.toInt(),
            "Email" to 0xFFE65100.toInt(), "Work" to 0xFF2E7D32.toInt(),
            "Shopping" to 0xFFC62828.toInt(), "Other" to 0xFF37474F.toInt()
        )
        holder.tvAvatar.setBackgroundColor(colors[entry.category] ?: 0xFF37474F.toInt())

        holder.btnCopyUser.setOnClickListener { onCopyUser(entry) }
        holder.btnCopyPass.setOnClickListener { onCopyPass(entry) }
        holder.btnEdit.setOnClickListener { onEdit(entry) }
        holder.btnDel.setOnClickListener { onDelete(entry) }
    }

    override fun getItemCount() = list.size
}
