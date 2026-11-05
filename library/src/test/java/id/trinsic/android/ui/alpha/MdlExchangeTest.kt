package id.trinsic.android.ui.alpha

import android.content.Context
import android.os.CancellationSignal
import android.os.Looper
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.DigitalCredential
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetDigitalCredentialOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import id.trinsic.android.ui.BuildConfig
import java.util.Base64
import java.lang.reflect.Modifier
import java.util.concurrent.Executor
import java.util.function.Consumer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@OptIn(ExperimentalDigitalCredentialApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class MdlExchangeTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test fun innerPayloadIsForwardedUnchangedIncludingUnknownDocumentsAndSignedRequests() {
        val payloads = listOf(
            """ { "providers" : [{"protocol":"preview","request":{"selector":{"doctype":"org.iso.18013.5.1.mDL"}}}], "unknown" : [1,true,null] } """,
            """{"requests":[{"protocol":"openid4vp","data":{"dcql_query":{"credentials":[{"id":"passport","format":"mso_mdoc","meta":{"doctype_value":"com.example.passport"}}]},"future":{"unicode":"日本語 🪪"}}}]}""",
            """ { "requests": [{"protocol":"openid4vp", "data": {"request":"eyJhbGciOiJFUzI1NiJ9.exact_payload.exact-signature", "request_uri":"https://example.test/signed?x=a%2Bb"}}] } """,
            "{}"
        )
        for (payload in payloads) {
            val provider = FakeProvider()
            val results = mutableListOf<MdlExchangeResult>()
            val call = MdlExchangeClient(provider).start(context, envelope(payload), Consumer(results::add))
            idle()
            assertEquals(1, provider.calls)
            assertEquals(payload, (provider.request.credentialOptions.single() as GetDigitalCredentialOption).requestJson)
            assertTrue(results.isEmpty())
            call.cancel()
            idle()
        }
    }

    @Test fun responseTokenIsPaddedBase64UrlOfExactUtf8Json() {
        for (json in listOf("{}", """ {"data":"日本語 😀 ÿ", "unknown": [1, true]} """)) {
            val provider = FakeProvider()
            val results = mutableListOf<MdlExchangeResult>()
            MdlExchangeClient(provider).start(context, envelope(), Consumer(results::add))
            idle()
            provider.succeed(json)
            idle()
            val result = results.single()
            assertTrue(result.success)
            assertNull(result.error)
            assertNull(result.exception)
            assertEquals("exchange-123", result.exchangeId)
            assertEquals(Base64.getUrlEncoder().encodeToString(json.toByteArray(Charsets.UTF_8)), result.token)
            assertArrayEquals(json.toByteArray(Charsets.UTF_8), Base64.getUrlDecoder().decode(result.token))
            assertFalse(result.token!!.contains('\n'))
            if (json == "{}") assertEquals("e30=", result.token)
        }
    }

    @Test fun malformedInputAlwaysCompletesAsInvalidChallengeWithoutCallingProvider() {
        val invalid = mutableListOf<String?>(null, "", " ", "%not-base64", encode("not json"),
            encode("[]"), encode("null"), encode("{} trailing"),
            Base64.getUrlEncoder().encodeToString(byteArrayOf(0xc3.toByte(), 0x28)))
        for (field in listOf("exchangeId", "type", "platform", "exchangeMechanism", "requestObject")) {
            for (value in listOf(null, JSONObject.NULL, 123, true, JSONObject(), "", "  ")) {
                val outer = outer()
                if (value == null) outer.remove(field) else outer.put(field, value)
                invalid.add(encode(outer.toString()))
            }
        }
        invalid += listOf(
            encode(outer().put("type", "other").toString()),
            encode(outer().put("platform", "apple-wallet").toString()),
            encode(outer().put("exchangeMechanism", "DigitalCredentialsApi").toString()),
            encode(outer().put("exchangeMechanism", "future").toString()),
            envelope("not json"), envelope("[]"), envelope("null"), envelope("{} trailing"), envelope("{\"broken\":"),
            envelope("{'invalid':'quotes'}"), envelope("{unquoted: true}"), envelope("{\"invalid\":NaN}"),
            envelope("{\"trailing\":[1,]}"), encode(outer().toString().replace("\"exchangeId\"", "'exchangeId'"))
        )
        for (input in invalid) {
            val provider = FakeProvider()
            val results = mutableListOf<MdlExchangeResult>()
            MdlExchangeClient(provider).start(context, input, Consumer(results::add))
            assertTrue("Validation must be asynchronous", results.isEmpty())
            idle()
            assertEquals(0, provider.calls)
            assertFailure(results.single(), MdlExchangeErrorType.INVALID_CHALLENGE)
            assertNotNull(results.single().error!!.cause)
        }
    }

    @Test fun validationRetainsExchangeIdWhenItWasAvailable() {
        val results = mutableListOf<MdlExchangeResult>()
        val events = mutableListOf<MdlDiagnosticEvent>()
        MdlExchangeClient(FakeProvider()).start(context, envelope("broken"), Consumer(results::add), events::add)
        idle()
        assertEquals("exchange-123", results.single().exchangeId)
        assertEquals("exchange-123", events.last().exchangeId)
        assertEquals(MdlExchangeErrorType.INVALID_CHALLENGE, events.last().errorType)
    }

    @Test fun publicAndLegacyCallbackApisDeliverInvalidInputOnMainThread() {
        val results = mutableListOf<MdlExchangeResult>()
        val callback = Consumer<MdlExchangeResult> {
            assertEquals(Looper.getMainLooper(), Looper.myLooper())
            results.add(it)
        }
        val worker = Thread {
            TrinsicMdl.performMdlExchange(context, "bad", callback)
            TrinsicMdl.performMdlExchangeAsync(context, "bad", callback)
        }
        worker.start()
        worker.join()
        assertTrue(results.isEmpty())
        idle()
        assertEquals(2, results.size)
        results.forEach { assertFailure(it, MdlExchangeErrorType.INVALID_CHALLENGE) }
    }

    @Test fun androidFailuresHaveStableTypesUsefulMessagesAndOriginalCauses() {
        val cases = listOf(
            GetCredentialCancellationException() to MdlExchangeErrorType.CANCELLED,
            NoCredentialException("") to MdlExchangeErrorType.NO_MATCHING_CREDENTIAL,
            GetCredentialUnknownException("   ") to MdlExchangeErrorType.PROVIDER_FAILURE,
            GetCredentialProviderConfigurationException() to MdlExchangeErrorType.PROVIDER_FAILURE,
            GetCredentialUnsupportedException() to MdlExchangeErrorType.PROVIDER_FAILURE,
            GetCredentialInterruptedException() to MdlExchangeErrorType.PROVIDER_FAILURE,
            GetCredentialUnknownException("Provider unavailable") to MdlExchangeErrorType.PROVIDER_FAILURE
        )
        for ((exception, expected) in cases) {
            val provider = FakeProvider()
            val results = mutableListOf<MdlExchangeResult>()
            MdlExchangeClient(provider).start(context, envelope(), Consumer(results::add))
            idle()
            provider.callback.onError(exception)
            idle()
            assertFailure(results.single(), expected)
            assertSame(exception, results.single().error!!.cause)
            assertSame(results.single().error, results.single().exception)
            if (!exception.message.isNullOrBlank()) assertEquals(exception.message, results.single().error!!.message)
        }
    }

    @Test fun synchronousProviderFailureIsAlsoAResult() {
        val exception = IllegalStateException("")
        val provider = FakeProvider().apply { onGet = { throw exception } }
        val results = mutableListOf<MdlExchangeResult>()
        MdlExchangeClient(provider).start(context, envelope(), Consumer(results::add))
        idle()
        assertFailure(results.single(), MdlExchangeErrorType.PROVIDER_FAILURE)
        assertSame(exception, results.single().error!!.cause)
    }

    @Test fun unexpectedCredentialIsControlledFailure() {
        val provider = FakeProvider()
        val results = mutableListOf<MdlExchangeResult>()
        MdlExchangeClient(provider).start(context, envelope(), Consumer(results::add))
        idle()
        provider.callback.onResult(GetCredentialResponse(PasswordCredential("user", "secret")))
        idle()
        assertFailure(results.single(), MdlExchangeErrorType.UNEXPECTED_RESPONSE)
    }

    @Test fun duplicateProviderCallbacksOnlyCompleteOnce() {
        for (errorFirst in listOf(false, true)) {
            val provider = FakeProvider()
            val results = mutableListOf<MdlExchangeResult>()
            val events = mutableListOf<MdlDiagnosticEvent>()
            MdlExchangeClient(provider).start(context, envelope(), Consumer(results::add), events::add)
            idle()
            if (errorFirst) provider.callback.onError(NoCredentialException())
            provider.succeed()
            provider.callback.onError(GetCredentialUnknownException())
            provider.succeed()
            idle()
            assertEquals(!errorFirst, results.single().success)
            assertEquals(1, events.count { it.stage == MdlExchangeStage.COMPLETED })
        }
    }

    @Test fun synchronousCallbackFollowedByProviderThrowOnlyCompletesOnce() {
        val provider = FakeProvider().apply { onGet = { succeed(); throw IllegalStateException() } }
        val results = mutableListOf<MdlExchangeResult>()
        MdlExchangeClient(provider).start(context, envelope(), Consumer(results::add))
        idle()
        assertEquals(1, results.size)
    }

    @Test fun applicationCallbackExceptionIsNotTurnedIntoAnotherResult() {
        val provider = FakeProvider()
        var completions = 0
        val failure = IllegalStateException("application failure")
        MdlExchangeClient(provider).start(context, envelope(), Consumer {
            completions++
            throw failure
        })
        idle()
        provider.succeed()
        try {
            idle()
            fail("Application exception should escape the callback")
        } catch (e: IllegalStateException) {
            assertSame(failure, e)
        }
        provider.callback.onError(GetCredentialUnknownException())
        idle()
        assertEquals(1, completions)
    }

    @Test fun cancellingBeforeLaunchSkipsProviderAndCompletesOnce() {
        val provider = FakeProvider()
        val results = mutableListOf<MdlExchangeResult>()
        val call = MdlExchangeClient(provider).start(context, envelope(), Consumer(results::add))
        call.cancel()
        call.cancel()
        assertTrue(results.isEmpty())
        idle()
        assertEquals(0, provider.calls)
        assertFailure(results.single(), MdlExchangeErrorType.CANCELLED)
    }

    @Test fun cancellingDuringRequestCancelsAndroidEvenWithoutProviderAcknowledgment() {
        val provider = FakeProvider()
        val results = mutableListOf<MdlExchangeResult>()
        val events = mutableListOf<MdlDiagnosticEvent>()
        val call = MdlExchangeClient(provider).start(context, envelope(), Consumer(results::add), events::add)
        idle()
        val thread = Thread { call.cancel(); call.cancel() }
        thread.start()
        thread.join()
        idle()
        assertTrue(provider.signal.isCanceled)
        assertFailure(results.single(), MdlExchangeErrorType.CANCELLED)
        assertEquals("exchange-123", results.single().exchangeId)
        assertEquals(MdlExchangeErrorType.CANCELLED, events.last().errorType)
        provider.succeed()
        provider.callback.onError(GetCredentialCancellationException())
        idle()
        assertEquals(1, results.size)
        assertEquals(1, events.count { it.stage == MdlExchangeStage.COMPLETED })
    }

    @Test fun cancellationWinsOverQueuedResponseButIsNoOpAfterCompletion() {
        for (cancelBeforeDelivery in listOf(false, true)) {
            val provider = FakeProvider()
            val results = mutableListOf<MdlExchangeResult>()
            val call = MdlExchangeClient(provider).start(context, envelope(), Consumer(results::add))
            idle()
            provider.succeed()
            if (!cancelBeforeDelivery) idle()
            call.cancel()
            idle()
            assertEquals(!cancelBeforeDelivery, results.single().success)
            assertEquals(cancelBeforeDelivery, provider.signal.isCanceled)
        }
    }

    @Test fun faultyProviderCancellationCannotPreventCancelledCompletion() {
        val provider = FakeProvider()
        val results = mutableListOf<MdlExchangeResult>()
        val failure = IllegalStateException("provider cancellation failure")
        val call = MdlExchangeClient(provider).start(context, envelope(), Consumer(results::add))
        idle()
        provider.signal.setOnCancelListener { throw failure }
        call.cancel()
        idle()
        assertFailure(results.single(), MdlExchangeErrorType.CANCELLED)
        assertSame(failure, results.single().error!!.suppressed.single())
    }

    @Test fun diagnosticListenerCanCancelBeforeProviderLaunch() {
        val provider = FakeProvider()
        val results = mutableListOf<MdlExchangeResult>()
        lateinit var call: MdlExchangeCall
        call = MdlExchangeClient(provider).start(context, envelope(), Consumer(results::add)) {
            if (it.stage == MdlExchangeStage.REQUESTING_CREDENTIAL) call.cancel()
        }
        idle()
        assertEquals(0, provider.calls)
        assertFailure(results.single(), MdlExchangeErrorType.CANCELLED)
    }

    @Test fun coroutineReturnsResultForSuccessAndWalletDismissal() = runTest {
        for (dismissed in listOf(false, true)) {
            val provider = FakeProvider()
            val deferred = async { MdlExchangeClient(provider).await(context, envelope()) }
            runCurrent()
            idle()
            if (dismissed) provider.callback.onError(GetCredentialCancellationException()) else provider.succeed()
            idle()
            runCurrent()
            if (dismissed) assertFailure(deferred.await(), MdlExchangeErrorType.CANCELLED)
            else assertTrue(deferred.await().success)
        }
    }

    @Test fun publicSuspendingApiReturnsInvalidChallenge() = runTest {
        val deferred = async { TrinsicMdl.awaitMdlExchange(context, "bad") }
        runCurrent()
        idle()
        runCurrent()
        assertFailure(deferred.await(), MdlExchangeErrorType.INVALID_CHALLENGE)
    }

    @Test fun coroutineCancellationPropagatesAndIgnoresLateCallbacks() = runTest {
        val provider = FakeProvider()
        val events = mutableListOf<MdlDiagnosticEvent>()
        val deferred = async { MdlExchangeClient(provider).await(context, envelope(), events::add) }
        runCurrent()
        idle()
        deferred.cancel()
        runCurrent()
        idle()
        assertTrue(provider.signal.isCanceled)
        assertTrue(deferred.isCancelled)
        try {
            deferred.await()
            fail("Coroutine cancellation must propagate")
        } catch (_: CancellationException) { }
        provider.succeed()
        provider.callback.onError(GetCredentialUnknownException())
        idle()
        runCurrent()
        assertEquals(1, events.count { it.stage == MdlExchangeStage.COMPLETED })
        assertEquals(MdlExchangeErrorType.CANCELLED, events.last().errorType)
    }

    @Test fun coroutineCancellationBeforeAndroidLaunchSkipsProvider() = runTest {
        val provider = FakeProvider()
        val deferred = async { MdlExchangeClient(provider).await(context, envelope()) }
        runCurrent()
        deferred.cancel()
        runCurrent()
        idle()
        assertEquals(0, provider.calls)
        assertTrue(deferred.isCancelled)
    }

    @Test fun coroutineHasPromptCancellationAfterResponseBeforeResumption() = runTest {
        val provider = FakeProvider()
        val deferred = async { MdlExchangeClient(provider).await(context, envelope()) }
        runCurrent()
        idle()
        provider.succeed()
        idle() // Result resumed, but the coroutine's dispatcher has not run yet.
        deferred.cancel()
        runCurrent()
        idle()
        assertTrue(deferred.isCancelled)
    }

    @Test fun diagnosticsAreOrderedMetadataOnlyAndListenerFailureIsIsolated() {
        val provider = FakeProvider()
        val events = mutableListOf<MdlDiagnosticEvent>()
        val results = mutableListOf<MdlExchangeResult>()
        MdlExchangeClient(provider).start(context, envelope(), Consumer(results::add)) {
            assertEquals(Looper.getMainLooper(), Looper.myLooper())
            events.add(it)
            throw IllegalStateException("observer failure")
        }
        idle()
        provider.succeed("""{"token":"private-credential"}""")
        idle()
        assertTrue(results.single().success)
        assertEquals(listOf(MdlExchangeStage.VALIDATING_REQUEST, MdlExchangeStage.REQUESTING_CREDENTIAL,
            MdlExchangeStage.ENCODING_RESPONSE, MdlExchangeStage.COMPLETED), events.map { it.stage })
        assertNull(events.first().exchangeId)
        assertTrue(events.drop(1).all { it.exchangeId == "exchange-123" })
        assertTrue(events.all { it.sdkVersion == BuildConfig.SDK_VERSION && it.errorType == null })
        assertEquals(setOf("stage", "exchangeId", "sdkVersion", "errorType"),
            MdlDiagnosticEvent::class.java.declaredFields
                .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }.map { it.name }.toSet())
    }

    @Test fun diagnosticsNeverReceiveProviderMessageOrCause() {
        val provider = FakeProvider()
        val events = mutableListOf<MdlDiagnosticEvent>()
        val results = mutableListOf<MdlExchangeResult>()
        MdlExchangeClient(provider).start(context, envelope(), Consumer(results::add), events::add)
        idle()
        provider.callback.onError(GetCredentialUnknownException("secret-provider-token"))
        idle()
        assertEquals(MdlExchangeErrorType.PROVIDER_FAILURE, events.last().errorType)
        assertFalse(events.joinToString().contains("secret-provider-token"))
        assertEquals("secret-provider-token", results.single().error!!.cause!!.message)
    }

    private fun assertFailure(result: MdlExchangeResult, type: MdlExchangeErrorType) {
        assertFalse(result.success)
        assertNull(result.token)
        assertEquals(type, result.error!!.errorType)
        assertFalse(result.error!!.message.isNullOrBlank())
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun outer(inner: String = "{}") = JSONObject()
        .put("exchangeId", "exchange-123")
        .put("type", "mdlRequest")
        .put("platform", "google-wallet")
        .put("exchangeMechanism", "NativeApp")
        .put("requestObject", inner)
        .put("futureEnvelopeField", JSONObject().put("keep", true))

    private fun envelope(inner: String = "{}") = encode(outer(inner).toString())
    private fun encode(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private class FakeProvider : MdlCredentialProvider {
        var calls = 0
        lateinit var request: GetCredentialRequest
        lateinit var signal: CancellationSignal
        lateinit var callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>
        var onGet: FakeProvider.() -> Unit = {}

        override fun getCredential(context: Context, request: GetCredentialRequest, cancellationSignal: CancellationSignal,
            executor: Executor, callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>) {
            assertEquals(Looper.getMainLooper(), Looper.myLooper())
            calls++
            this.request = request
            signal = cancellationSignal
            this.callback = callback
            onGet()
        }

        fun succeed(json: String = "{}") = callback.onResult(GetCredentialResponse(DigitalCredential(json)))
    }
}
