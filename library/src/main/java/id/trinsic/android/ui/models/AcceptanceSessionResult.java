package id.trinsic.android.ui.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/**
 * The result of executing a Session via `TrinsicUI.LaunchSession()` or `TrinsicUI.LaunchSessionWithPendingIntent()`.
 */
public class AcceptanceSessionResult implements Parcelable {
    private String sessionId;

    @Deprecated
    private String resultsAccessKey;

    @Deprecated
    private boolean success;

    private boolean canceled;

    public AcceptanceSessionResult(String sessionId, boolean canceled) {
        this(sessionId, null, true, canceled);
    }

    @Deprecated
    public AcceptanceSessionResult(String sessionId, String resultsAccessKey, boolean success, boolean canceled) {
        this.sessionId = sessionId;
        this.resultsAccessKey = resultsAccessKey;
        this.success = success;
        this.canceled = canceled;
    }

    private AcceptanceSessionResult(Parcel in) {
        sessionId = in.readString();
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
        dest.writeString(resultsAccessKey);
        dest.writeByte((byte) (success ? 1 : 0));
        dest.writeByte((byte) (canceled ? 1 : 0));
    }
}
