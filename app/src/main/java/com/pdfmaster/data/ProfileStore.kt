package com.pdfmaster.data

import com.pdfmaster.core.CryptoBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Personal details used to auto-fill forms. Encrypted on-device with a Keystore key. */
data class Profile(
    val fullName: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val city: String = "",
    val postalCode: String = "",
    val country: String = "",
    val dateOfBirth: String = "",
    val idNumber: String = "",
    val company: String = "",
) {
    fun toJson(): String = JSONObject().apply {
        put("fullName", fullName); put("firstName", firstName); put("lastName", lastName)
        put("email", email); put("phone", phone); put("address", address); put("city", city)
        put("postalCode", postalCode); put("country", country); put("dateOfBirth", dateOfBirth)
        put("idNumber", idNumber); put("company", company)
    }.toString()

    /** Picks the profile value that best matches a form field name, or null. */
    fun valueForField(fieldName: String): String? {
        val n = fieldName.lowercase().replace(Regex("[^a-z]"), "")
        val value = when {
            "email" in n || "mail" in n -> email
            "phone" in n || "mobile" in n || "tel" in n -> phone
            "first" in n || "given" in n || "forename" in n -> firstName
            "last" in n || "surname" in n || "family" in n -> lastName
            "birth" in n || "dob" in n -> dateOfBirth
            "passport" in n || "idnumber" in n || "nationalid" in n || "emirates" in n || n == "id" -> idNumber
            "zip" in n || "postal" in n || "postcode" in n -> postalCode
            "city" in n || "town" in n -> city
            "country" in n || "nationality" in n -> country
            "company" in n || "employer" in n || "organisation" in n || "organization" in n -> company
            "address" in n || "street" in n -> address
            "name" in n -> fullName.ifBlank { listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ") }
            else -> ""
        }
        return value.ifBlank { null }
    }

    companion object {
        fun fromJson(json: String): Profile {
            val o = JSONObject(json)
            fun s(k: String) = o.optString(k, "")
            return Profile(
                s("fullName"), s("firstName"), s("lastName"), s("email"), s("phone"), s("address"),
                s("city"), s("postalCode"), s("country"), s("dateOfBirth"), s("idNumber"), s("company"),
            )
        }
    }
}

class ProfileStore(private val file: File, private val crypto: CryptoBox) {
    suspend fun load(): Profile = withContext(Dispatchers.IO) {
        crypto.readEncrypted(file)?.let { runCatching { Profile.fromJson(String(it)) }.getOrNull() } ?: Profile()
    }

    suspend fun save(profile: Profile) = withContext(Dispatchers.IO) {
        crypto.writeEncrypted(file, profile.toJson().toByteArray())
    }
}
