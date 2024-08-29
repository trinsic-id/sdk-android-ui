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

    /**
     * Redirect URL to be used for the Acceptance Session.
     */
    private String redirectUrl;

    public AcceptanceSessionLaunchParams(String sessionId, String launchUrl, String redirectUrl) {
        this.sessionId = sessionId;
        this.launchUrl = launchUrl;
        this.redirectUrl = redirectUrl;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getLaunchUrl() {
        return launchUrl;
    }

    public String getRedirectUrl() { return redirectUrl; }

}
