package id.trinsic.android.ui.models;

/**
 * Internal library class used to package Acceptance Session launch arguments
 */
public class AcceptanceSessionLaunchParams {
    /**
     * The ID of the Acceptance Session being launched
     */
    private String sessionId;

    /**
     * The Launch URL of the Acceptance Session, returned from the Create Session API
     */
    private String launchUrl;

    public AcceptanceSessionLaunchParams(String sessionId, String launchUrl) {
        this.sessionId = sessionId;
        this.launchUrl = launchUrl;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getLaunchUrl() {
        return launchUrl;
    }
}
