package com.example.overlayai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object AiNetworkClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun sendRequest(
        context: Context,
        apiUrl: String,
        apiKey: String,
        prompt: String,
        apiFormat: Int,
        thinkingLevel: Int,
        bitmaps: List<Bitmap>
    ): String {
        val base64Images = bitmaps.map { bitmapToBase64(it) }
        val thinkingStr = getThinkingLevelString(thinkingLevel)?.uppercase() ?: ""

        val jsonString = loadTemplate(context, apiFormat)
        val jsonBody = JSONObject(jsonString)

        val variables = mutableMapOf(
            "{{PROMPT}}" to prompt,
            "{{THINKING_LEVEL}}" to thinkingStr
        )

        if (base64Images.size <= 1) {
            variables["{{IMAGE_BASE64}}"] = base64Images.firstOrNull() ?: ""
            injectVariables(jsonBody, variables)
        } else {
            duplicateImageNode(jsonBody, base64Images)
            injectVariables(jsonBody, variables)
        }

        val requestBuilder = Request.Builder()
            .url(apiUrl)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))

        if (apiKey.isNotEmpty()) {
            if (apiFormat == 0 || apiFormat == 1) { // OpenAI
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            } else { // Gemini
                requestBuilder.addHeader("x-goog-api-key", apiKey)
            }
        }

        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return "Error: ${response.code} - ${response.body?.string()}"

            val responseBody = response.body?.string() ?: return "Empty response"
            return parseResponse(responseBody, apiFormat)
        }
    }

    private fun loadTemplate(context: Context, apiFormat: Int): String {
        val prefs = context.getSharedPreferences("OverlayAiPrefs", Context.MODE_PRIVATE)
        val templateKey = "json_template_$apiFormat"
        val customTemplate = prefs.getString(templateKey, null)
        if (customTemplate != null) {
            return customTemplate
        }

        val templateName = when (apiFormat) {
            0 -> "openai_vision_template.json"
            1 -> "openai_text_template.json"
            2 -> "gemini_vision_template.json"
            3 -> "gemini_text_template.json"
            else -> "openai_vision_template.json"
        }
        return context.assets.open(templateName).bufferedReader().use { it.readText() }
    }

    private fun injectVariables(json: Any, variables: Map<String, String>) {
        if (json is JSONObject) {
            val keys = json.keys().asSequence().toList()
            for (key in keys) {
                val value = json.opt(key)
                if (value is String) {
                    var newValueStr: String = value as String
                    
                    // Completely remove {{THINKING_LEVEL}} key if it's not set
                    if (newValueStr == "{{THINKING_LEVEL}}" && variables["{{THINKING_LEVEL}}"].isNullOrEmpty()) {
                        json.remove(key)
                        continue
                    }
                    
                    for ((k, v) in variables) {
                        if (newValueStr.contains(k)) {
                            newValueStr = newValueStr.replace(k, v)
                        }
                    }
                    if (newValueStr != value) {
                        json.put(key, newValueStr)
                    }
                } else if (value is JSONObject) {
                    injectVariables(value, variables)
                    if (value.length() == 0 && (key == "thinkingConfig" || key == "generationConfig")) {
                        json.remove(key)
                    }
                } else if (value != null) {
                    injectVariables(value, variables)
                }
            }
        } else if (json is JSONArray) {
            for (i in 0 until json.length()) {
                val value = json.opt(i)
                if (value is String) {
                    var newValueStr: String = value as String
                    for ((k, v) in variables) {
                        if (newValueStr.contains(k)) {
                            newValueStr = newValueStr.replace(k, v)
                        }
                    }
                    if (newValueStr != value) {
                        json.put(i, newValueStr)
                    }
                } else if (value != null) {
                    injectVariables(value, variables)
                }
            }
        }
    }

    private fun duplicateImageNode(json: Any, base64Images: List<String>): Boolean {
        if (json is JSONObject) {
            val keys = json.keys().asSequence().toList()
            for (key in keys) {
                val value = json.opt(key)
                if (value is JSONArray) {
                    if (processJsonArrayForImages(value, base64Images)) return true
                } else if (value is JSONObject) {
                    if (duplicateImageNode(value, base64Images)) return true
                }
            }
        } else if (json is JSONArray) {
            if (processJsonArrayForImages(json, base64Images)) return true
        }
        return false
    }

    private fun processJsonArrayForImages(array: JSONArray, base64Images: List<String>): Boolean {
        var targetIndex = -1
        for (i in 0 until array.length()) {
            val element = array.opt(i)
            if (element.toString().contains("{{IMAGE_BASE64}}")) {
                targetIndex = i
                break
            } else if (element is JSONObject || element is JSONArray) {
                if (duplicateImageNode(element, base64Images)) return true
            }
        }

        if (targetIndex != -1) {
            val targetElementStr = array.opt(targetIndex).toString()
            val newElements = mutableListOf<Any>()
            for (i in 0 until array.length()) {
                if (i == targetIndex) {
                    for (base64 in base64Images) {
                        val duplicatedStr = targetElementStr.replace("{{IMAGE_BASE64}}", base64)
                        val newObj = try { JSONObject(duplicatedStr) } catch(e:Exception) { 
                            try { JSONArray(duplicatedStr) } catch (e2:Exception) { duplicatedStr } 
                        }
                        newElements.add(newObj)
                    }
                } else {
                    newElements.add(array.opt(i)!!)
                }
            }
            
            while (array.length() > 0) {
                array.remove(0)
            }
            for (el in newElements) {
                array.put(el)
            }
            return true
        }
        return false
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        var scaledBitmap = bitmap
        val maxDim = 1080
        if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = Math.min(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
            scaledBitmap = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt(),
                (bitmap.height * ratio).toInt(),
                true
            )
        }

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun getThinkingLevelString(level: Int): String? {
        return when (level) {
            1 -> "low"
            2 -> "medium"
            3 -> "high"
            4 -> "minimal"
            else -> null
        }
    }

    private fun parseResponse(responseStr: String, apiFormat: Int): String {
        return try {
            val json = JSONObject(responseStr)
            if (apiFormat == 0 || apiFormat == 1) {
                val choices = json.getJSONArray("choices")
                val message = choices.getJSONObject(0).getJSONObject("message")
                message.getString("content")
            } else {
                val candidates = json.getJSONArray("candidates")
                val content = candidates.getJSONObject(0).getJSONObject("content")
                val parts = content.getJSONArray("parts")
                parts.getJSONObject(0).getString("text")
            }
        } catch (e: Exception) {
            "Parse Error: ${e.message}\nPayload: $responseStr"
        }
    }
}
