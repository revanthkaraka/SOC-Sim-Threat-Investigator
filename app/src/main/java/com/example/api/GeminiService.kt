package com.example.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ThreatAnalysis(
    val explanation: String,
    val containment: String,
    val eradication: String,
    val recovery: String,
    val mitreTactic: String = "Unknown Tactic",
    val mitreTechnique: String = "Unknown Technique",
    val remediationScript: String = "# Script generation failed",
    val blastRadiusPath: String = "Attacker ➔ Compromised Asset"
)

object GeminiService {
    private const val TAG = "GeminiService"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeSecurityLog(alertTitle: String, logData: String): ThreatAnalysis = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "API Key is empty or placeholder!")
            return@withContext ThreatAnalysis(
                explanation = "Error: Gemini API Key is missing. Please configure your API key in the Secrets Panel in AI Studio.",
                containment = "Go to the Settings/Secrets panel in Google AI Studio.",
                eradication = "Add a valid GEMINI_API_KEY.",
                recovery = "Restart the application to reload configuration."
            )
        }

        val prompt = """
            You are a senior Tier-3 Incident Responder and Threat Intel Specialist.
            Analyze the following security alert: "$alertTitle".
            Here is the raw telemetry log data:
            $logData
            
            Based on this information, provide:
            1. A plain-English, easy-to-understand breakdown of the attack vector (what is happening, who is the attacker, what they are trying to achieve).
            2. Concrete, actionable 3-step incident response instructions:
               - Containment (how to stop the immediate bleeding/activity)
               - Eradication (how to remove the threat from the environment)
               - Recovery (how to restore operations safely)
            3. A complete, ready-to-run, localized mitigation Python script. Keep it very simple and direct, using standard libraries (like `subprocess`, `socket`, `os`, or `urllib.request`) to configure local system access lists, drop firewall entries, terminate exfiltration endpoints, or revoke API sessions (e.g., using `iptables` or standard network requests). Avoid any enclosing triple backticks or markdown decorators inside the string itself.
            4. The mapped MITRE ATT&CK Tactic name (e.g., "Initial Access", "Credential Access", "Discovery", "Exfiltration").
            5. The mapped MITRE ATT&CK Technique ID and title (e.g., "T1110 (Brute Force)", "T1046 (Network Service Discovery)").
            6. A concise representation of the network attack blast radius. Depict the path using the format: Source Attacker/IP ➔ Impacted System/Hop ➔ Next vulnerable nodes in proximity.
            
            You must return your response as a valid JSON object. Do not include markdown formatting or backticks around the JSON itself.
            The JSON MUST strictly contain the following keys with string values:
            - "explanation": Explanation of the attack vector, its threat level, and potential impact.
            - "containment": Actionable immediate containment steps.
            - "eradication": Actionable eradication steps.
            - "recovery": Actionable recovery/hardening steps.
            - "mitreTactic": The specific MITRE tactic category name.
            - "mitreTechnique": The mapped technique ID and title (e.g., T1110 (Brute Force)).
            - "remediationScript": The raw Python script code block.
            - "blastRadiusPath": The single-line text representation of the blast path.
        """.trimIndent()

        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            
            // Build request JSON
            val requestJson = JSONObject()
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()
            val partObj = JSONObject()
            partObj.put("text", prompt)
            partsArray.put(partObj)
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            requestJson.put("contents", contentsArray)

            // Request structured JSON format
            val generationConfig = JSONObject()
            val responseFormat = JSONObject()
            responseFormat.put("responseMimeType", "application/json")
            generationConfig.put("responseFormat", responseFormat)
            requestJson.put("generationConfig", generationConfig)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)
            
            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Request failed: Code ${response.code}, Msg: $responseBodyStr")
                    return@withContext ThreatAnalysis(
                        explanation = "API Error: Received HTTP ${response.code} from Gemini. Please make sure your API key is valid.",
                        containment = "Verify connectivity to Google Services.",
                        eradication = "Double-check your API quotas in the Google AI Studio console.",
                        recovery = "Try triaging with a less intensive model if possible."
                    )
                }

                val responseJson = JSONObject(responseBodyStr)
                val candidates = responseJson.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val content = firstCandidate?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val rawText = parts?.optJSONObject(0)?.optString("text") ?: ""

                if (rawText.isEmpty()) {
                    return@withContext ThreatAnalysis(
                        explanation = "Error: Gemini returned an empty response. Check if your alert log is valid.",
                        containment = "N/A",
                        eradication = "N/A",
                        recovery = "N/A",
                        mitreTactic = "N/A",
                        mitreTechnique = "N/A",
                        remediationScript = "# Generation failed",
                        blastRadiusPath = "Attacker ➔ Impacted Host"
                    )
                }

                // Parse the inner JSON returned by Gemini
                val innerJson = JSONObject(rawText)
                ThreatAnalysis(
                    explanation = innerJson.optString("explanation", "No explanation available."),
                    containment = innerJson.optString("containment", "No containment playbook generated."),
                    eradication = innerJson.optString("eradication", "No eradication playbook generated."),
                    recovery = innerJson.optString("recovery", "No recovery playbook generated."),
                    mitreTactic = innerJson.optString("mitreTactic", "Initial Access"),
                    mitreTechnique = innerJson.optString("mitreTechnique", "T1566 (Phishing)"),
                    remediationScript = innerJson.optString("remediationScript", "# Python script placeholder"),
                    blastRadiusPath = innerJson.optString("blastRadiusPath", "External Attacker ➔ Edge Firewall ➔ DMZ Web Host")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during log analysis: ${e.message}", e)
            ThreatAnalysis(
                explanation = "Network Error: ${e.localizedMessage ?: "Failed to connect to Gemini Services"}.",
                containment = "Check device internet connection.",
                eradication = "Review the logs for network request blockages.",
                recovery = "Ensure API keys and .env configuration are set correctly."
            )
        }
    }

    suspend fun analyzePhishingPayload(payload: String): PhishingAnalysis = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext PhishingAnalysis(
                senderDomain = "N/A",
                spfStatus = "FAIL",
                dkimStatus = "FAIL",
                flagsFound = listOf("API Key is missing"),
                urlAssessment = "Cannot scan URL without Gemini API Key.",
                explanation = "Please configure your GEMINI_API_KEY inside the Secrets panel of Google AI Studio.",
                verdict = "SUSPICIOUS",
                dynamicPlaybook = "### Playbook\n1. Go to Google AI Studio.\n2. Configure API key."
            )
        }

        val prompt = """
            You are an advanced Phishing Forensic Analyst and Email Security expert.
            Analyze the following text, which is either a raw Email Header block, or a suspicious URL link, or both:
            $payload

            Perform a forensic inspection of this payload. Determine:
            1. The Sender Domain (if it's an email header, extract the 'From' domain; if it's a URL, extract the host domain).
            2. SPF verification status (extract SPF headers/alignment, or determine based on domain patterns).
            3. DKIM verification status (extract or determine alignment).
            4. Phishing Indicators / Flag elements (e.g., domain typo-squatting, spoofed headers, mismatched envelope, aggressive call-to-actions, tracking parameters). Return these as an array of short bullet phrases.
            5. URL Risk Assessment (assess the safety of any URLs, looking for redirect chains, dynamic IP hosts, obfuscated subdomains, or fake login portals).
            6. A plain-English forensic explanation of what this payload is trying to achieve.
            7. The security threat VERDICT: must be exactly one of: "MALICIOUS", "SUSPICIOUS", or "SAFE".
            8. A 3-step dynamic incident response mitigation playbook for this threat.

            You must return your response as a valid JSON object. Do not include markdown formatting or backticks around the JSON itself.
            The JSON MUST strictly contain the following keys with string or array values:
            - "senderDomain": string (e.g. "secure-paypal-billing.com")
            - "spfStatus": string (e.g. "FAIL (Mismatched alignment)", "PASS", "NONE")
            - "dkimStatus": string (e.g. "FAIL (Signature invalid)", "PASS", "NONE")
            - "flagsFound": array of strings (e.g. ["Mismatched Sender Envelope", "Obfuscated login URL"])
            - "urlAssessment": string (e.g. "Subdomain typo-squats PayPal brand to trick users into entering credit cards.")
            - "explanation": string (Forensic explanation)
            - "verdict": string (exactly "MALICIOUS", "SUSPICIOUS", or "SAFE")
            - "dynamicPlaybook": string (Markdown formatted IR instructions)
        """.trimIndent()

        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            
            val requestJson = JSONObject()
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()
            val partObj = JSONObject()
            partObj.put("text", prompt)
            partsArray.put(partObj)
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            requestJson.put("contents", contentsArray)

            val generationConfig = JSONObject()
            val responseFormat = JSONObject()
            responseFormat.put("responseMimeType", "application/json")
            generationConfig.put("responseFormat", responseFormat)
            requestJson.put("generationConfig", generationConfig)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)
            
            val request = Request.Builder().url(endpoint).post(requestBody).build()

            client.newCall(request).execute().use { response ->
                val responseBodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext PhishingAnalysis(
                        senderDomain = "ERROR",
                        spfStatus = "FAIL",
                        dkimStatus = "FAIL",
                        flagsFound = listOf("HTTP Request failed"),
                        urlAssessment = "Received HTTP ${response.code} from API.",
                        explanation = "Failed to query Gemini API due to server status.",
                        verdict = "SUSPICIOUS",
                        dynamicPlaybook = "Verify network and API key settings."
                    )
                }

                val responseJson = JSONObject(responseBodyStr)
                val rawText = responseJson.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text") ?: ""

                if (rawText.isEmpty()) {
                    return@withContext PhishingAnalysis(
                        senderDomain = "N/A",
                        spfStatus = "NONE",
                        dkimStatus = "NONE",
                        flagsFound = listOf("Empty response received"),
                        urlAssessment = "N/A",
                        explanation = "Gemini returned an empty reply.",
                        verdict = "SUSPICIOUS",
                        dynamicPlaybook = "N/A"
                    )
                }

                val innerJson = JSONObject(rawText)
                val jsonFlags = innerJson.optJSONArray("flagsFound")
                val flagsList = mutableListOf<String>()
                if (jsonFlags != null) {
                    for (i in 0 until jsonFlags.length()) {
                        flagsList.add(jsonFlags.getString(i))
                    }
                } else {
                    flagsList.add("No specific indicators flagged.")
                }

                PhishingAnalysis(
                    senderDomain = innerJson.optString("senderDomain", "Unknown Domain"),
                    spfStatus = innerJson.optString("spfStatus", "NONE"),
                    dkimStatus = innerJson.optString("dkimStatus", "NONE"),
                    flagsFound = flagsList,
                    urlAssessment = innerJson.optString("urlAssessment", "No custom assessment."),
                    explanation = innerJson.optString("explanation", "No analysis explanation available."),
                    verdict = innerJson.optString("verdict", "SUSPICIOUS"),
                    dynamicPlaybook = innerJson.optString("dynamicPlaybook", "No playbook available.")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Phishing analysis failed: ${e.message}", e)
            PhishingAnalysis(
                senderDomain = "ERROR",
                spfStatus = "NONE",
                dkimStatus = "NONE",
                flagsFound = listOf("Exception occurred: ${e.localizedMessage}"),
                urlAssessment = "Failed to verify links due to connection exception.",
                explanation = "Network or JSON error analyzing email: ${e.localizedMessage}",
                verdict = "SUSPICIOUS",
                dynamicPlaybook = "Check device connection."
            )
        }
    }
}

data class PhishingAnalysis(
    val senderDomain: String,
    val spfStatus: String,
    val dkimStatus: String,
    val flagsFound: List<String>,
    val urlAssessment: String,
    val explanation: String,
    val verdict: String,
    val dynamicPlaybook: String
)
