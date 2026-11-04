package id.trinsic.android.ui.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The result of executing a Session via `TrinsicUI.LaunchSession()` or `TrinsicUI.LaunchSessionWithPendingIntent()`.
 */
public class AcceptanceSessionResult implements Parcelable {
    private String sessionId;

    private String redirectToken;

    private boolean canceled;

    private String callbackUrl;

    public AcceptanceSessionResult(@Nullable String sessionId, @Nullable String redirectToken, boolean canceled) {
        this(sessionId, redirectToken, null, canceled);
    }

    public AcceptanceSessionResult(@Nullable String sessionId, @Nullable String redirectToken,
                                   @Nullable String callbackUrl, boolean canceled) {
        this.sessionId = nullIfEmpty(sessionId);
        this.redirectToken = nullIfEmpty(redirectToken);
        this.callbackUrl = callbackUrl;
        this.canceled = canceled;
    }

    private AcceptanceSessionResult(Parcel in) {
        this(in.readString(), in.readString(), in.readString(), in.readByte() != 0);
    }

    @Nullable
    private static String nullIfEmpty(@Nullable String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    public static final Creator<AcceptanceSessionResult> CREATOR = new Creator<AcceptanceSessionResult>() {
        @Override
        public AcceptanceSessionResult createFromParcel(Parcel in) {
            return new AcceptanceSessionResult(in);
        }

        @Override
        public AcceptanceSessionResult[] newArray(int size) {
            return new AcceptanceSessionResult[size];
        }
    };

    /**
     * The ID of the Acceptance Session from the completion callback.
     * When the launch URL contains a non-empty {@code sessionId}, the callback must return the same ID.
     * On browser cancellation, this is the Session ID from the launch URL, if available.
     * Null when the relevant URL omits the ID or supplies an empty value.
     */
    @Nullable
    public String getSessionId() {
        return sessionId;
    }

    /**
     * The `redirectToken` from the completion callback.
     * Null when absent or empty, or when the user closes the browser before a callback arrives.
     */
    @Nullable
    public String getRedirectToken() { return redirectToken; }

    /**
     * The full completion callback URL, preserved as received, including its query and fragment.
     * Null when no callback URL was received, such as when the user closes the browser.
     */
    @Nullable
    public String getCallbackUrl() {
        return callbackUrl;
    }

    /**
     * Whether the Session was locally canceled by the user (e.g. by hitting the "X" button on the Android Custom Tab interface).
     * <p>
     * NOTE that if the user canceled the Session by, e.g., clicking a "Cancel" button in a web UI - such that the Trinsic Session itself
     * entered the canceled state - this will have a value of `false`.
     * <p>
     * This only has a value of `true` if the user canceled the Session using an Android OS feature which immediately
     * ended the Android Custom Tab session, such as the back button. In this case, Trinsic's backend never
     * receives notification that the Session has been canceled; therefore, the Session will not transition to the canceled state on the Trinsic platform.
     */
    public boolean getCanceled() {
        return canceled;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(sessionId);
        dest.writeString(redirectToken);
        dest.writeString(callbackUrl);
        dest.writeByte((byte) (canceled ? 1 : 0));
    }
}
