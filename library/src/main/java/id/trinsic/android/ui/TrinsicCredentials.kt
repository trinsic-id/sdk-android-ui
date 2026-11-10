package id.trinsic.android.ui

import android.content.Context
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.DigitalCredential
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetDigitalCredentialOption
import androidx.credentials.exceptions.GetCredentialException
import java.util.Base64
import java.util.concurrent.CancellationException
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** An in-flight exchange. Cancellation is thread safe, idempotent, and a no-op after completion. */
interface CredentialExchangeCall {
    fun cancel()
}

/** Native credential exchanges using request objects created by Trinsic's API. */
class TrinsicCredentials {
    companion object {
        /**
         * Callback API. Completion (including invalid input) is delivered once on the main
         * thread. Use [performCredentialExchangeAsync] to retain a cancellation handle.
         */
        @JvmStatic
        fun performCredentialExchange(
            context: Context,
            requestObjectBase64Url: String?,
            callback: Consumer<CredentialExchangeResult>
        ) {
            performCredentialExchangeAsync(context, requestObjectBase64Url, callback)
        }

        /**
         * Starts an exchange using an Activity context (or Fragment.requireActivity()). Returns
         * immediately. Results and optional diagnostics are always posted to the main thread.
         * Cancel the returned handle when its owning screen is destroyed. Input/provider failures
         * are results, not synchronous exceptions. Exceptions from app callbacks are not swallowed.
         */
        @JvmStatic
        @JvmOverloads
        fun performCredentialExchangeAsync(
            context: Context,
            requestObjectBase64Url: String?,
            callback: Consumer<CredentialExchangeResult>,
            diagnostics: CredentialExchangeDiagnosticListener? = null
        ): CredentialExchangeCall = CredentialExchangeClient().start(context, requestObjectBase64Url, callback, diagnostics)

        /**
         * Suspends for an exchange result. Wallet dismissal returns CANCELLED; cancellation of the
         * calling coroutine cancels Android's request and propagates CancellationException normally.
         * The caller's dispatcher is preserved; the Android UI is launched on the main thread.
         */
        @JvmStatic
        suspend fun awaitCredentialExchange(
            context: Context,
            requestObjectBase64Url: String?,
            diagnostics: CredentialExchangeDiagnosticListener? = null
        ): CredentialExchangeResult = CredentialExchangeClient().await(context, requestObjectBase64Url, diagnostics)
    }
}

/** Narrow provider boundary, injectable internally so tests never launch wallet UI. */
internal fun interface DigitalCredentialProvider {
    fun getCredential(
        context: Context,
        request: GetCredentialRequest,
        cancellationSignal: CancellationSignal,
        executor: Executor,
        callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>
    )
}

internal class CredentialExchangeClient(
    private val provider: DigitalCredentialProvider = DigitalCredentialProvider { context, request, signal, executor, callback ->
        CredentialManager.create(context).getCredentialAsync(context, request, signal, executor, callback)
    }
) {
    fun start(
        context: Context,
        request: String?,
        callback: Consumer<CredentialExchangeResult>,
        diagnostics: CredentialExchangeDiagnosticListener? = null
    ): CredentialExchangeCall = ExchangeOperation(context, request, provider, callback, diagnostics).also { it.start() }

    suspend fun await(
        context: Context,
        request: String?,
        diagnostics: CredentialExchangeDiagnosticListener? = null
    ): CredentialExchangeResult = suspendCancellableCoroutine { continuation ->
        val operation = ExchangeOperation(context, request, provider, Consumer { continuation.resume(it) }, diagnostics)
        continuation.invokeOnCancellation { operation.cancel() }
        operation.start()
    }
}

/** All state and callbacks are confined to the main queue, except the atomic cancellation request. */
@OptIn(ExperimentalDigitalCredentialApi::class)
private class ExchangeOperation(
    private val context: Context,
    private val encodedRequest: String?,
    private val provider: DigitalCredentialProvider,
    private val callback: Consumer<CredentialExchangeResult>,
    private val diagnostics: CredentialExchangeDiagnosticListener?
) : CredentialExchangeCall {
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executor { handler.post(it) }
    private val cancellationSignal = CancellationSignal()
    private val cancellationRequested = AtomicBoolean()
    private var completed = false
    private var exchangeId: String? = null

    fun start() = dispatch {
        if (stopIfCancelled()) return@dispatch
        emit(CredentialExchangeStage.VALIDATING_REQUEST)
        val request = try {
            val parsed = CredentialExchangeOuterRequest.parse(encodedRequest)
            exchangeId = parsed.exchangeId
            GetCredentialRequest(listOf(GetDigitalCredentialOption(parsed.requestObject)))
        } catch (e: Exception) {
            val error = if (e is CredentialExchangeException) e else CredentialExchangeException(
                CredentialExchangeErrorType.INVALID_CHALLENGE, "The inner credential request is invalid.", e, exchangeId)
            exchangeId = error.exchangeId
            finish(CredentialExchangeResult(exchangeId, error))
            return@dispatch
        }
        if (stopIfCancelled()) return@dispatch
        emit(CredentialExchangeStage.REQUESTING_CREDENTIAL)
        if (stopIfCancelled()) return@dispatch
        try {
            provider.getCredential(context, request, cancellationSignal, executor,
                object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                    override fun onResult(result: GetCredentialResponse) = dispatch response@{
                        if (stopIfCancelled()) return@response
                        emit(CredentialExchangeStage.ENCODING_RESPONSE)
                        val outcome = try {
                            val credential = result.credential
                            if (credential !is DigitalCredential) {
                                CredentialExchangeResult(exchangeId, CredentialExchangeException(
                                    CredentialExchangeErrorType.UNEXPECTED_RESPONSE,
                                    "The Android credential provider returned a non-digital credential.",
                                    exchangeId = exchangeId))
                            } else {
                                val token = Base64.getUrlEncoder().encodeToString(
                                    credential.credentialJson.toByteArray(Charsets.UTF_8))
                                CredentialExchangeResult(exchangeId, token)
                            }
                        } catch (e: Exception) {
                            CredentialExchangeResult(exchangeId, CredentialExchangeException(
                                CredentialExchangeErrorType.UNEXPECTED_RESPONSE, cause = e, exchangeId = exchangeId))
                        }
                        finish(outcome)
                    }

                    override fun onError(e: GetCredentialException) = dispatch {
                        finish(CredentialExchangeResult(exchangeId, CredentialExchangeException.fromException(e, exchangeId)))
                    }
                })
        } catch (e: Exception) {
            finish(CredentialExchangeResult(exchangeId, CredentialExchangeException.fromException(e, exchangeId)))
        }
    }

    override fun cancel() {
        if (cancellationRequested.compareAndSet(false, true)) dispatch { stopIfCancelled() }
    }

    private fun stopIfCancelled(): Boolean {
        if (completed) return true
        if (!cancellationRequested.get()) return false
        completed = true
        val error = CredentialExchangeException.fromException(CancellationException("The credential exchange was cancelled."), exchangeId)
        try {
            cancellationSignal.cancel()
        } catch (e: Exception) {
            error.addSuppressed(e)
        }
        deliver(CredentialExchangeResult(exchangeId, error))
        return true
    }

    private fun finish(result: CredentialExchangeResult) {
        if (stopIfCancelled()) return
        completed = true
        deliver(result)
    }

    private fun deliver(result: CredentialExchangeResult) {
        emit(CredentialExchangeStage.COMPLETED, result.error?.errorType)
        callback.accept(result)
    }

    private fun emit(stage: CredentialExchangeStage, errorType: CredentialExchangeErrorType? = null) {
        try {
            diagnostics?.onEvent(CredentialExchangeDiagnosticEvent(stage, exchangeId, errorType))
        } catch (_: Exception) {
            // Observability must never change the outcome or prevent completion.
        }
    }

    private fun dispatch(block: () -> Unit) {
        handler.post { block() }
    }
}
