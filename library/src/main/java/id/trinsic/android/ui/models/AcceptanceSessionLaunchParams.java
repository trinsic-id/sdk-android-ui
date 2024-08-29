package id.trinsic.android.ui.models;

/**
 * Internal library class used to package Acceptance Session
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
     * Redirect scheme to be used for the Acceptance Session
     * TODO: Change to redirect URL
     */
    private String redirectScheme;

    public AcceptanceSessionLaunchParams(String sessionId, String launchUrl, String redirectScheme) {
        this.sessionId = sessionId;
        this.launchUrl = launchUrl;
        this.redirectScheme = redirectScheme;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getLaunchUrl() {
        return launchUrl;
    }

    public String getRedirectScheme() { return redirectScheme; }

}
