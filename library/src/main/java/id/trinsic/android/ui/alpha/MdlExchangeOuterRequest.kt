package id.trinsic.android.ui.alpha

import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONObject
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64

/** Validates the transport envelope without interpreting or reserializing document requests. */
internal class MdlExchangeOuterRequest(val exchangeId: String, val requestObject: String) {
    companion object {
        private val versionKey = Regex("v[1-9][0-9]*")
        private val supportedVersions = setOf("v1")

        fun parse(encodedRequest: String?): MdlExchangeOuterRequest {
            var exchangeId: String? = null
            try {
                require(!encodedRequest.isNullOrBlank()) { "The request object must not be empty." }
                val bytes = try {
                    Base64.getUrlDecoder().decode(encodedRequest)
                } catch (e: IllegalArgumentException) {
                    throw MdlExchangeException(MdlExchangeErrorType.INVALID_CHALLENGE,
                        "The request object must be base64url encoded.", e)
                }
                val json = Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString()
                validateJsonObject(json)
                val envelope = JSONObject(json)
                val version = selectVersion(envelope, supportedVersions)
                // A version contains a complete request. Never merge with or fall back to legacy fields.
                val outer = envelope.getJSONObject(version)
                exchangeId = requiredString(outer, "exchangeId")
                require(requiredString(outer, "type") == "mdlRequest") {
                    "Invalid request object: `type` must be 'mdlRequest'."
                }
                require(requiredString(outer, "platform") == "google-wallet") {
                    "Invalid request object: `platform` must be 'google-wallet'."
                }
                require(requiredString(outer, "exchangeMechanism") == "NativeApp") {
                    "Invalid request object: `exchangeMechanism` must be 'NativeApp'."
                }
                val inner = requiredString(outer, "requestObject")
                // Validate syntax only. Preserve whitespace, unknown fields, and signed requests.
                validateJsonObject(inner)
                return MdlExchangeOuterRequest(exchangeId, inner)
            } catch (e: MdlExchangeException) {
                throw e
            } catch (e: Exception) {
                // Parser exception messages can contain the request; keep them in the cause only.
                val message = if (e is IllegalArgumentException) e.message
                    else "The request object must contain valid UTF-8 JSON objects."
                throw MdlExchangeException(MdlExchangeErrorType.INVALID_CHALLENGE, message, e, exchangeId)
            }
        }

        /** Requires a supported version, including when the envelope contains only legacy fields. */
        internal fun selectVersion(envelope: JSONObject, supportedVersions: Set<String>): String {
            // Compare digit counts before text so arbitrarily large version numbers cannot overflow.
            return envelope.keys().asSequence().filter { versionKey.matches(it) && it in supportedVersions }
                .maxWithOrNull(compareBy<String> { it.length }.thenBy { it })
                ?: throw MdlExchangeException(MdlExchangeErrorType.UNSUPPORTED_CHALLENGE_VERSION)
        }

        private fun requiredString(json: JSONObject, field: String): String {
            val value = json.opt(field)
            require(value is String && value.isNotBlank()) {
                "Invalid request object: missing, empty, or non-string `$field` field."
            }
            return value
        }

        private fun validateJsonObject(json: String) {
            // JSONObject alone accepts non-JSON syntax such as single quotes and unquoted keys.
            JsonReader(StringReader(json)).use { reader ->
                require(reader.peek() == JsonToken.BEGIN_OBJECT) {
                    "The request object must contain JSON objects."
                }
                reader.skipValue()
                require(reader.peek() == JsonToken.END_DOCUMENT) {
                    "The request object must contain JSON objects without trailing data."
                }
            }
        }
    }
}
