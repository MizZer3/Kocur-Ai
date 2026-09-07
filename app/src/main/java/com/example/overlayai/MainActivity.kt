package com.example.overlayai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import android.text.TextWatcher
import android.text.Editable
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.view.View

class MainActivity : AppCompatActivity() {

    private lateinit var spinnerHomePromptProfile: Spinner
    private lateinit var spinnerHomeApiProfile: Spinner
    
    private lateinit var etPrompt: EditText
    private lateinit var spinnerPromptProfile: Spinner
    private lateinit var btnAddProfile: Button
    private lateinit var btnRenameProfile: Button
    private lateinit var btnDeleteProfile: Button
    
    private lateinit var spinnerApiProfile: Spinner
    private lateinit var btnAddApiProfile: Button
    private lateinit var btnRenameApiProfile: Button
    private lateinit var btnDeleteApiProfile: Button
    private lateinit var etProfileApiUrl: EditText
    private lateinit var etProfileApiKey: EditText
    private lateinit var spinnerProfileApiFormat: Spinner
    private lateinit var spinnerProfileThinkingLevel: Spinner
    
    private lateinit var etButtonColor: EditText
    private lateinit var etButtonSize: EditText
    private lateinit var btnSelectImage: Button
    private lateinit var btnClearImage: Button
    private lateinit var tvImageStatus: TextView
    private var imageUriString: String = ""
    
    private lateinit var tvActiveApiUrl: TextView
    private lateinit var tvActiveApiModel: TextView
    private lateinit var tvActiveApiThinking: TextView

    data class PromptProfile(val id: String, var name: String, var prompt: String)
    data class ApiProfile(
        val id: String, 
        var name: String, 
        var apiUrl: String = "",
        var apiKey: String = "",
        var apiFormat: Int = 0,
        var thinkingLevel: Int = 0
    )
    private var promptProfiles = mutableListOf<PromptProfile>()
    private var activePromptProfileId: String = ""
    private var isPromptProfileSpinning = false
    
    private var apiProfiles = mutableListOf<ApiProfile>()
    private var activeApiProfileId: String = ""
    private var isApiProfileSpinning = false
    
    private var activeHomePromptProfileId: String = ""
    private var activeHomeApiProfileId: String = ""
    private var isHomeSpinning = false

    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            imageUriString = it.toString()
            updateImageStatus()
            saveSettings()
            Toast.makeText(this, "Image Selected", Toast.LENGTH_SHORT).show()
        }
    }

    private lateinit var etButtonOpacity: EditText
    private lateinit var etMaxOutputLength: EditText
    private lateinit var switchHideAiButton: SwitchMaterial
    private var isLoadingSettings = false

    private fun EditText.addAutoSave() {
        this.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isLoadingSettings) saveSettings()
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinnerHomePromptProfile = findViewById(R.id.spinnerHomePromptProfile)
        spinnerHomeApiProfile = findViewById(R.id.spinnerHomeApiProfile)
        
        etPrompt = findViewById(R.id.etPrompt)
        spinnerPromptProfile = findViewById(R.id.spinnerPromptProfile)
        btnAddProfile = findViewById(R.id.btnAddProfile)
        btnRenameProfile = findViewById(R.id.btnRenameProfile)
        btnDeleteProfile = findViewById(R.id.btnDeleteProfile)
        
        spinnerApiProfile = findViewById(R.id.spinnerApiProfile)
        btnAddApiProfile = findViewById(R.id.btnAddApiProfile)
        btnRenameApiProfile = findViewById(R.id.btnRenameApiProfile)
        btnDeleteApiProfile = findViewById(R.id.btnDeleteApiProfile)
        etProfileApiUrl = findViewById(R.id.etProfileApiUrl)
        etProfileApiKey = findViewById(R.id.etProfileApiKey)
        spinnerProfileApiFormat = findViewById(R.id.spinnerProfileApiFormat)
        spinnerProfileThinkingLevel = findViewById(R.id.spinnerProfileThinkingLevel)
        
        etButtonColor = findViewById(R.id.etButtonColor)
        etButtonSize = findViewById(R.id.etButtonSize)
        btnSelectImage = findViewById(R.id.btnSelectImage)
        btnClearImage = findViewById(R.id.btnClearImage)
        tvImageStatus = findViewById(R.id.tvImageStatus)
        etButtonOpacity = findViewById(R.id.etButtonOpacity)
        etMaxOutputLength = findViewById(R.id.etMaxOutputLength)
        switchHideAiButton = findViewById(R.id.switchHideAiButton)
        
        tvActiveApiUrl = findViewById(R.id.tvActiveApiUrl)
        tvActiveApiModel = findViewById(R.id.tvActiveApiModel)
        tvActiveApiThinking = findViewById(R.id.tvActiveApiThinking)

        val containerHome = findViewById<View>(R.id.containerHome)
        val containerStyle = findViewById<View>(R.id.containerStyle)
        val containerProfiles = findViewById<View>(R.id.containerProfiles)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        bottomNav.setOnItemSelectedListener { item ->
            containerHome.visibility = View.GONE
            containerStyle.visibility = View.GONE
            containerProfiles.visibility = View.GONE
            
            when (item.itemId) {
                R.id.nav_home -> containerHome.visibility = View.VISIBLE
                R.id.nav_style -> containerStyle.visibility = View.VISIBLE
                R.id.nav_profiles -> containerProfiles.visibility = View.VISIBLE
            }
            true
        }

        btnSelectImage.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }

        btnClearImage.setOnClickListener {
            imageUriString = ""
            updateImageStatus()
            saveSettings()
            Toast.makeText(this, "Image Cleared", Toast.LENGTH_SHORT).show()
        }

        val btnEditJson = findViewById<Button>(R.id.btnEditJson)
        btnEditJson.setOnClickListener { showEditJsonDialog() }
        
        switchHideAiButton.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences("OverlayAiPrefs", Context.MODE_PRIVATE)
                .edit().putBoolean("isButtonHidden", isChecked).commit()
        }

        btnAddProfile.setOnClickListener { showAddPromptProfileDialog() }
        btnRenameProfile.setOnClickListener { showRenamePromptProfileDialog() }
        btnDeleteProfile.setOnClickListener { showDeletePromptProfileDialog() }
        
        btnAddApiProfile.setOnClickListener { showAddApiProfileDialog() }
        btnRenameApiProfile.setOnClickListener { showRenameApiProfileDialog() }
        btnDeleteApiProfile.setOnClickListener { showDeleteApiProfileDialog() }
        
        etPrompt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isPromptProfileSpinning && activePromptProfileId.isNotEmpty()) {
                    val activeIndex = promptProfiles.indexOfFirst { it.id == activePromptProfileId }
                    if (activeIndex != -1) {
                        promptProfiles[activeIndex].prompt = s?.toString() ?: ""
                        saveProfiles()
                    }
                }
            }
        })
        
        val apiTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isApiProfileSpinning && activeApiProfileId.isNotEmpty()) {
                    val activeIndex = apiProfiles.indexOfFirst { it.id == activeApiProfileId }
                    if (activeIndex != -1) {
                        apiProfiles[activeIndex].apiUrl = etProfileApiUrl.text.toString()
                        apiProfiles[activeIndex].apiKey = etProfileApiKey.text.toString()
                        saveProfiles()
                    }
                }
            }
        }
        etProfileApiUrl.addTextChangedListener(apiTextWatcher)
        etProfileApiKey.addTextChangedListener(apiTextWatcher)
        
        val apiSpinnerListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isApiProfileSpinning && activeApiProfileId.isNotEmpty()) {
                    val activeIndex = apiProfiles.indexOfFirst { it.id == activeApiProfileId }
                    if (activeIndex != -1) {
                        apiProfiles[activeIndex].apiFormat = spinnerProfileApiFormat.selectedItemPosition
                        apiProfiles[activeIndex].thinkingLevel = spinnerProfileThinkingLevel.selectedItemPosition
                        saveProfiles()
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinnerProfileApiFormat.onItemSelectedListener = apiSpinnerListener
        spinnerProfileThinkingLevel.onItemSelectedListener = apiSpinnerListener

        spinnerPromptProfile.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isPromptProfileSpinning && promptProfiles.isNotEmpty()) {
                    activePromptProfileId = promptProfiles[position].id
                    isPromptProfileSpinning = true
                    etPrompt.setText(promptProfiles[position].prompt)
                    isPromptProfileSpinning = false
                    saveProfiles()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        spinnerApiProfile.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isApiProfileSpinning && apiProfiles.isNotEmpty()) {
                    activeApiProfileId = apiProfiles[position].id
                    isApiProfileSpinning = true
                    val selected = apiProfiles[position]
                    etProfileApiUrl.setText(selected.apiUrl)
                    etProfileApiKey.setText(selected.apiKey)
                    spinnerProfileApiFormat.setSelection(selected.apiFormat)
                    spinnerProfileThinkingLevel.setSelection(selected.thinkingLevel)
                    isApiProfileSpinning = false
                    saveProfiles()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        spinnerHomePromptProfile.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isHomeSpinning && promptProfiles.isNotEmpty()) {
                    activeHomePromptProfileId = promptProfiles[position].id
                    saveProfiles()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        spinnerHomeApiProfile.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isHomeSpinning && apiProfiles.isNotEmpty()) {
                    activeHomeApiProfileId = apiProfiles[position].id
                    saveProfiles()
                    updateHomeApiDetails(apiProfiles[position])
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }



        etButtonColor.addAutoSave()
        etButtonSize.addAutoSave()
        etButtonOpacity.addAutoSave()
        etMaxOutputLength.addAutoSave()

        loadSettings()

        findViewById<Button>(R.id.btnOverlayPermission).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            } else {
                Toast.makeText(this, "Overlay Permission already granted", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnAccessibilityPermission).setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
    }

    private fun showEditJsonDialog() {
        val prefs = getSharedPreferences("OverlayAiPrefs", Context.MODE_PRIVATE)
        
        val activeProfile = apiProfiles.find { it.id == activeHomeApiProfileId }
        val apiFormat = activeProfile?.apiFormat ?: 0
        
        val templateKey = "json_template_$apiFormat"
        
        var currentJson = prefs.getString(templateKey, null)
        if (currentJson == null) {
            val templateName = when (apiFormat) {
                0 -> "openai_vision_template.json"
                1 -> "openai_text_template.json"
                2 -> "gemini_vision_template.json"
                3 -> "gemini_text_template.json"
                else -> "openai_vision_template.json"
            }
            currentJson = try {
                assets.open(templateName).bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                "{}"
            }
        }

        val editText = EditText(this).apply {
            setText(currentJson)
            setHorizontallyScrolling(false)
            maxLines = Integer.MAX_VALUE
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(32, 32, 32, 32)
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
        }

        AlertDialog.Builder(this)
            .setTitle("Edit JSON Template")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString(templateKey, editText.text.toString()).commit()
                Toast.makeText(this, "Template Saved", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Reset Default") { _, _ ->
                prefs.edit().remove(templateKey).commit()
                Toast.makeText(this, "Template Reset", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showAddPromptProfileDialog() {
        val editText = EditText(this)
        editText.hint = "Prompt Profile Name"
        AlertDialog.Builder(this)
            .setTitle("Add Prompt Profile")
            .setView(editText)
            .setPositiveButton("Add") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    val newProfile = PromptProfile(UUID.randomUUID().toString(), name, "")
                    promptProfiles.add(newProfile)
                    activePromptProfileId = newProfile.id
                    saveProfiles()
                    updateProfileSpinner()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenamePromptProfileDialog() {
        val activeIndex = promptProfiles.indexOfFirst { it.id == activePromptProfileId }
        if (activeIndex == -1) return
        
        val editText = EditText(this)
        editText.setText(promptProfiles[activeIndex].name)
        AlertDialog.Builder(this)
            .setTitle("Rename Prompt Profile")
            .setView(editText)
            .setPositiveButton("Rename") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    promptProfiles[activeIndex].name = name
                    saveProfiles()
                    updateProfileSpinner()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeletePromptProfileDialog() {
        if (promptProfiles.size <= 1) {
            Toast.makeText(this, "Cannot delete the last prompt profile.", Toast.LENGTH_SHORT).show()
            return
        }
        val activeIndex = promptProfiles.indexOfFirst { it.id == activePromptProfileId }
        val name = promptProfiles[activeIndex].name
        AlertDialog.Builder(this)
            .setTitle("Delete Prompt Profile")
            .setMessage("Are you sure you want to delete '$name'?")
            .setPositiveButton("Delete") { _, _ ->
                promptProfiles.removeAt(activeIndex)
                activePromptProfileId = promptProfiles[0].id
                saveProfiles()
                updateProfileSpinner()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddApiProfileDialog() {
        val editText = EditText(this)
        editText.hint = "API Profile Name"
        AlertDialog.Builder(this)
            .setTitle("Add API Profile")
            .setView(editText)
            .setPositiveButton("Add") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    val newProfile = ApiProfile(UUID.randomUUID().toString(), name)
                    apiProfiles.add(newProfile)
                    activeApiProfileId = newProfile.id
                    saveProfiles()
                    updateProfileSpinner()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameApiProfileDialog() {
        val activeIndex = apiProfiles.indexOfFirst { it.id == activeApiProfileId }
        if (activeIndex == -1) return
        
        val editText = EditText(this)
        editText.setText(apiProfiles[activeIndex].name)
        AlertDialog.Builder(this)
            .setTitle("Rename API Profile")
            .setView(editText)
            .setPositiveButton("Rename") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    apiProfiles[activeIndex].name = name
                    saveProfiles()
                    updateProfileSpinner()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteApiProfileDialog() {
        if (apiProfiles.size <= 1) {
            Toast.makeText(this, "Cannot delete the last API profile.", Toast.LENGTH_SHORT).show()
            return
        }
        val activeIndex = apiProfiles.indexOfFirst { it.id == activeApiProfileId }
        val name = apiProfiles[activeIndex].name
        AlertDialog.Builder(this)
            .setTitle("Delete API Profile")
            .setMessage("Are you sure you want to delete '$name'?")
            .setPositiveButton("Delete") { _, _ ->
                apiProfiles.removeAt(activeIndex)
                activeApiProfileId = apiProfiles[0].id
                saveProfiles()
                updateProfileSpinner()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateImageStatus() {
        if (imageUriString.isNotEmpty()) {
            tvImageStatus.text = getString(R.string.status_image_selected)
        } else {
            tvImageStatus.text = getString(R.string.status_no_image)
        }
    }

    private fun loadProfiles() {
        val prefs = getSharedPreferences("OverlayAiPrefs", Context.MODE_PRIVATE)
        val promptJson = prefs.getString("promptProfiles", "[]")
        val apiJson = prefs.getString("apiProfiles", "[]")
        promptProfiles.clear()
        apiProfiles.clear()
        
        try {
            val pArray = JSONArray(promptJson)
            for (i in 0 until pArray.length()) {
                val obj = pArray.getJSONObject(i)
                promptProfiles.add(PromptProfile(obj.getString("id"), obj.getString("name"), obj.getString("prompt")))
            }
        } catch (e: Exception) {}
        
        try {
            val aArray = JSONArray(apiJson)
            for (i in 0 until aArray.length()) {
                val obj = aArray.getJSONObject(i)
                apiProfiles.add(ApiProfile(
                    obj.getString("id"), obj.getString("name"), obj.optString("apiUrl", ""),
                    obj.optString("apiKey", ""), obj.optInt("apiFormat", 0), obj.optInt("thinkingLevel", 0)
                ))
            }
        } catch (e: Exception) {}
        
        if (promptProfiles.isEmpty() && apiProfiles.isEmpty()) {
            val pOld = prefs.getString("prompt", "") ?: ""
            val aUrl = prefs.getString("apiUrl", "") ?: ""
            val aKey = prefs.getString("apiKey", "") ?: ""
            val aFmt = prefs.getInt("apiFormat", 0)
            val aTlv = prefs.getInt("thinkingLevel", 0)
            promptProfiles.add(PromptProfile(UUID.randomUUID().toString(), "Default Prompt", pOld))
            apiProfiles.add(ApiProfile(UUID.randomUUID().toString(), "Default API", aUrl, aKey, aFmt, aTlv))
        } else {
            if (promptProfiles.isEmpty()) promptProfiles.add(PromptProfile(UUID.randomUUID().toString(), "Default Prompt", ""))
            if (apiProfiles.isEmpty()) apiProfiles.add(ApiProfile(UUID.randomUUID().toString(), "Default API", "", "", 0, 0))
        }
        
        activePromptProfileId = prefs.getString("activePromptProfileId", promptProfiles[0].id) ?: promptProfiles[0].id
        if (promptProfiles.none { it.id == activePromptProfileId }) activePromptProfileId = promptProfiles[0].id
        
        activeHomePromptProfileId = prefs.getString("activeHomePromptProfileId", promptProfiles[0].id) ?: promptProfiles[0].id
        if (promptProfiles.none { it.id == activeHomePromptProfileId }) activeHomePromptProfileId = promptProfiles[0].id
        
        activeApiProfileId = prefs.getString("activeApiProfileId", apiProfiles[0].id) ?: apiProfiles[0].id
        if (apiProfiles.none { it.id == activeApiProfileId }) activeApiProfileId = apiProfiles[0].id
        
        activeHomeApiProfileId = prefs.getString("activeHomeApiProfileId", apiProfiles[0].id) ?: apiProfiles[0].id
        if (apiProfiles.none { it.id == activeHomeApiProfileId }) activeHomeApiProfileId = apiProfiles[0].id
        
        updateProfileSpinner()
    }

    private fun saveProfiles() {
        val prefs = getSharedPreferences("OverlayAiPrefs", Context.MODE_PRIVATE)
        val pArray = JSONArray()
        for (p in promptProfiles) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("prompt", p.prompt)
            pArray.put(obj)
        }
        
        val aArray = JSONArray()
        for (a in apiProfiles) {
            val obj = JSONObject()
            obj.put("id", a.id)
            obj.put("name", a.name)
            obj.put("apiUrl", a.apiUrl)
            obj.put("apiKey", a.apiKey)
            obj.put("apiFormat", a.apiFormat)
            obj.put("thinkingLevel", a.thinkingLevel)
            aArray.put(obj)
        }
        
        val activeHomePrompt = promptProfiles.find { it.id == activeHomePromptProfileId }
        val activeHomeApi = apiProfiles.find { it.id == activeHomeApiProfileId }
        
        prefs.edit().apply {
            putString("promptProfiles", pArray.toString())
            putString("apiProfiles", aArray.toString())
            putString("activePromptProfileId", activePromptProfileId)
            putString("activeHomePromptProfileId", activeHomePromptProfileId)
            putString("activeApiProfileId", activeApiProfileId)
            putString("activeHomeApiProfileId", activeHomeApiProfileId)
            
            if (activeHomePrompt != null) putString("prompt", activeHomePrompt.prompt)
            if (activeHomeApi != null) {
                putString("apiUrl", activeHomeApi.apiUrl)
                putString("apiKey", activeHomeApi.apiKey)
                putInt("apiFormat", activeHomeApi.apiFormat)
                putInt("thinkingLevel", activeHomeApi.thinkingLevel)
            }
            commit()
        }
    }
    
    private fun updateHomeProfileDataIfActive() {
        if (activePromptProfileId == activeHomePromptProfileId || activeApiProfileId == activeHomeApiProfileId) {
            saveProfiles()
        }
    }

    private fun updateHomeApiDetails(selectedApi: ApiProfile?) {
        if (selectedApi != null) {
            tvActiveApiUrl.text = "URL: " + selectedApi.apiUrl
            val formats = resources.getStringArray(R.array.api_formats)
            tvActiveApiModel.text = "Format: " + (formats.getOrNull(selectedApi.apiFormat) ?: "Unknown")
            val levels = resources.getStringArray(R.array.thinking_levels)
            tvActiveApiThinking.text = "Thinking: " + (levels.getOrNull(selectedApi.thinkingLevel) ?: "Unknown")
        } else {
            tvActiveApiUrl.text = ""
            tvActiveApiModel.text = ""
            tvActiveApiThinking.text = ""
        }
    }

    private fun updateProfileSpinner() {
        isPromptProfileSpinning = true
        isApiProfileSpinning = true
        isHomeSpinning = true
        
        val pNames = promptProfiles.map { it.name }
        val pAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, pNames)
        pAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        
        val aNames = apiProfiles.map { it.name }
        val aAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, aNames)
        aAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        
        spinnerPromptProfile.adapter = pAdapter
        spinnerHomePromptProfile.adapter = pAdapter
        spinnerApiProfile.adapter = aAdapter
        spinnerHomeApiProfile.adapter = aAdapter

        val pIndex = promptProfiles.indexOfFirst { it.id == activePromptProfileId }.coerceAtLeast(0)
        spinnerPromptProfile.setSelection(pIndex)
        etPrompt.setText(promptProfiles[pIndex].prompt)
        
        val aIndex = apiProfiles.indexOfFirst { it.id == activeApiProfileId }.coerceAtLeast(0)
        spinnerApiProfile.setSelection(aIndex)
        val selectedApi = apiProfiles[aIndex]
        etProfileApiUrl.setText(selectedApi.apiUrl)
        etProfileApiKey.setText(selectedApi.apiKey)
        spinnerProfileApiFormat.setSelection(selectedApi.apiFormat)
        spinnerProfileThinkingLevel.setSelection(selectedApi.thinkingLevel)
        
        val pHpIndex = promptProfiles.indexOfFirst { it.id == activeHomePromptProfileId }.coerceAtLeast(0)
        spinnerHomePromptProfile.setSelection(pHpIndex)
        val aHpIndex = apiProfiles.indexOfFirst { it.id == activeHomeApiProfileId }.coerceAtLeast(0)
        spinnerHomeApiProfile.setSelection(aHpIndex)
        updateHomeApiDetails(apiProfiles.getOrNull(aHpIndex))

        isPromptProfileSpinning = false
        isApiProfileSpinning = false
        isHomeSpinning = false
    }

    private fun loadSettings() {
        isLoadingSettings = true
        loadProfiles()
        val prefs = getSharedPreferences("OverlayAiPrefs", Context.MODE_PRIVATE)
        
        etButtonColor.setText(prefs.getString("buttonColor", "#000000"))
        etButtonSize.setText(prefs.getInt("buttonSize", 64).toString())
        imageUriString = prefs.getString("buttonImageUri", "") ?: ""
        updateImageStatus()
        etButtonOpacity.setText(prefs.getInt("buttonOpacity", 50).toString())
        etMaxOutputLength.setText(prefs.getInt("maxOutputLength", 200).toString())
        switchHideAiButton.isChecked = prefs.getBoolean("isButtonHidden", false)
        
        val pNames = promptProfiles.map { it.name }
        val pAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, pNames)
        pAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        val aNames = apiProfiles.map { it.name }
        val aAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, aNames)
        aAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        
        isLoadingSettings = false
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("OverlayAiPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("buttonColor", etButtonColor.text.toString())
            putInt("buttonSize", etButtonSize.text.toString().toIntOrNull() ?: 64)
            putString("buttonImageUri", imageUriString)
            putInt("buttonOpacity", etButtonOpacity.text.toString().toIntOrNull() ?: 50)
            putInt("maxOutputLength", etMaxOutputLength.text.toString().toIntOrNull() ?: 200)
            putBoolean("isButtonHidden", switchHideAiButton.isChecked)
            
            commit()
        }
        saveProfiles()
    }

    private fun updateStatusText() {
        // Obsolete
    }
}
