package id.trinsic.android.ui.models;

/**
 * Internal library class used to package Acceptance Session launch arguments
 */
public class AcceptanceSessionLaunchParams {
    /**
     * The Launch URL of the Acceptance Session, returned from the Create Session API
     */
    private String launchUrl;

    public AcceptanceSessionLaunchParams(String launchUrl) {
        this.launchUrl = launchUrl;
    }

    public String getLaunchUrl() {
        return launchUrl;
    }
}
