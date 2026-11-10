package id.trinsic.android.ui

import java.util.Base64
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NativeChallengeVersionTest {
    @Test fun parserRequiresV1Challenges() {
        val fixtures = fixtures()
        val expected = CredentialExchangeOuterRequest.parse(fixtures.single { it.name == "single-version" }.challenge)
        for (fixture in fixtures) {
            // Shared fixtures include legacy and hypothetical future support; this SDK requires v1.
            val expectedError = when (fixture.name) {
                "legacy", "ignore-non-version-keys" -> "unsupported_challenge_version"
                "no-downgrade", "numeric-order", "numeric-order-reversed" -> null
                "malformed-unselected-supported-version" -> "invalid_challenge"
                else -> fixture.expectedError
            }
            var actualError: String? = null
            try {
                val actual = CredentialExchangeOuterRequest.parse(fixture.challenge)
                assertEquals(fixture.name, expected.exchangeId, actual.exchangeId)
                assertEquals(fixture.name, expected.requestObject, actual.requestObject)
            } catch (error: CredentialExchangeException) {
                actualError = errorName(error)
            }
            assertEquals(fixture.name, expectedError, actualError)
        }
    }

    @Test fun versionSelectionContract() {
        val fixtures = fixtures()
        val expected = CredentialExchangeOuterRequest.parse(fixtures.single { it.name == "single-version" }.challenge)
        for (fixture in fixtures) {
            val expectedError = if (fixture.selectedVersion == "legacy") "unsupported_challenge_version"
                else fixture.expectedError
            var actualError: String? = null
            try {
                val selected = select(fixture)
                assertEquals(fixture.name, fixture.selectedVersion, selected.version)
                assertEquals(fixture.name, expected.exchangeId, selected.request.exchangeId)
                assertEquals(fixture.name, expected.requestObject, selected.request.requestObject)
            } catch (error: CredentialExchangeException) {
                actualError = errorName(error)
            } catch (_: JSONException) {
                actualError = "invalid_challenge"
            }
            assertEquals(fixture.name, expectedError, actualError)
        }
    }

    // Exercise the production selector with the shared fixtures' hypothetical supported versions.
    // Each fixture uses the v1 request schema, so wrap the selected request in a v1 envelope.
    private fun select(fixture: Fixture): SelectedChallenge {
        val outer = JSONObject(String(Base64.getUrlDecoder().decode(fixture.challenge), Charsets.UTF_8))
        val selected = CredentialExchangeOuterRequest.selectVersion(outer, fixture.supportedVersions.toSet())
        val request = outer.getJSONObject(selected)
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(JSONObject().put("v1", request).toString().toByteArray(Charsets.UTF_8))
        // No fallback after selection, including when the inner request is malformed.
        return SelectedChallenge(selected, CredentialExchangeOuterRequest.parse(encoded))
    }

    private fun errorName(error: CredentialExchangeException): String = when (error.errorType) {
        CredentialExchangeErrorType.INVALID_CHALLENGE -> "invalid_challenge"
        CredentialExchangeErrorType.UNSUPPORTED_CHALLENGE_VERSION -> "unsupported_challenge_version"
        else -> throw AssertionError("Unexpected error type", error)
    }

    private fun fixtures(): List<Fixture> {
        val resource = requireNotNull(javaClass.getResourceAsStream("/native-challenge-versions.json"))
        val values = resource.bufferedReader(Charsets.UTF_8).use { JSONArray(it.readText()) }
        return (0 until values.length()).map { index ->
            val value = values.getJSONObject(index)
            val supported = value.getJSONArray("supportedVersions")
            Fixture(
                name = value.getString("name"),
                challenge = value.getString("challenge"),
                supportedVersions = (0 until supported.length()).map { supported.getString(it) },
                selectedVersion = if (value.isNull("selectedVersion")) null else value.getString("selectedVersion"),
                expectedError = if (value.isNull("expectedError")) null else value.getString("expectedError")
            )
        }
    }

    private data class Fixture(
        val name: String,
        val challenge: String,
        val supportedVersions: List<String>,
        val selectedVersion: String?,
        val expectedError: String?
    )

    private data class SelectedChallenge(val version: String, val request: CredentialExchangeOuterRequest)
}
