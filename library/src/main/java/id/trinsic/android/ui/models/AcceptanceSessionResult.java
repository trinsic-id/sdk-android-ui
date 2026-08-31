package id.trinsic.android.ui.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/**
 * The result of executing a Session via `TrinsicUI.LaunchSession()` or `TrinsicUI.LaunchSessionWithPendingIntent()`.
 */
public class AcceptanceSessionResult implements Parcelable {
    private String sessionId;

    private String redirectToken;

    private boolean canceled;

    @Deprecated
    private String resultsAccessKey;

    @Deprecated
    private boolean success;

    public AcceptanceSessionResult(String sessionId, String redirectToken, boolean canceled) {
        this(sessionId, redirectToken, null, true, canceled);
    }

    @Deprecated
    public AcceptanceSessionResult(String sessionId, String redirectToken, String resultsAccessKey, boolean success, boolean canceled) {
        this.sessionId = sessionId;
        this.redirectToken = redirectToken;
        this.resultsAccessKey = resultsAccessKey;
        this.success = success;
        this.canceled = canceled;
    }

    private AcceptanceSessionResult(Parcel in) {
        sessionId = in.readString();
        redirectToken = in.readString();
        resultsAccessKey = in.readString();
        success = in.readByte() != 0;
        canceled = in.readByte() != 0;
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
     * The ID of the Acceptance Session this result is for
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * The `redirectToken` from the callback redirect, if present.
     *
     * NOTE: As of September 1 2026, this field always has a value of NULL, as the relevant platform changes
     * have not yet been released. However, in the very near future, this field will be populated
     * for all successful Sessions.
     *
     * If this value is non-NULL, send it to your backend; it will play a core role in future
     * high-assurance / same-device guarantees.
     *
     * For now, your backend should not do anything with this value if it is sent. When
     * this feature is fully released, Trinsic will provide guidance
     *
     * This field is present before the feature is fully released to ensure that the necessary
     * changes upon release are backend-only, and do not require app or SDK updates.
     */
    public String getRedirectToken() { return redirectToken; }

    /**
     * The deprecated `resultsAccessKey` from the callback redirect, if present.
     *
     * @deprecated Per the February 2026 changes to the Trinsic platform, this field is now deprecated
     * and will be removed from redirect URLs in the future. The `resultsAccessKey` stored in your backend
     * from the response to Session creation should be used instead.
     */
    @Deprecated
    public String getResultsAccessKey() {
        return resultsAccessKey;
    }

    /**
     * The deprecated `success` parameter from the callback redirect, if present.
     * If not present, this is always `true`.
     *
     * @deprecated Per the February 2026 changes to the Trinsic platform, this field is now deprecated
     * and will be removed in the future. The Trinsic API should be used as the only authoritative
     * source for Session success.
     */
    @Deprecated
    public boolean getSuccess() {
        return success;
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
        dest.writeString(resultsAccessKey);
        dest.writeByte((byte) (success ? 1 : 0));
        dest.writeByte((byte) (canceled ? 1 : 0));
    }
}
