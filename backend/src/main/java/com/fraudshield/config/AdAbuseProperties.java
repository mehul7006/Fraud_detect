package com.fraudshield.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 6.1: every ad-abuse weight and threshold, overridable in application.yml under fraudshield.adabuse.
 * The defaults below are the tuned values (see AdAbuseAccuracyTest for the before/after numbers).
 */
@Component
@ConfigurationProperties(prefix = "fraudshield.adabuse")
public class AdAbuseProperties {
    private int notifHigh = 20, notifHighWeight = 50;
    private int notifMid = 8, notifMidWeight = 30;
    private int notifLow = 4, notifLowWeight = 10;
    private int overlayWeight = 25;
    private int fullScreenWeight = 15;
    private int accessibilityWeight = 30;
    private int smsWeight = 25;
    private int listenerWeight = 15;
    private int selfLaunchHigh = 3, selfLaunchHighWeight = 45;
    private int selfLaunchLow = 1, selfLaunchLowWeight = 15;

    public int getNotifHigh() { return notifHigh; } public void setNotifHigh(int v) { notifHigh = v; }
    public int getNotifHighWeight() { return notifHighWeight; } public void setNotifHighWeight(int v) { notifHighWeight = v; }
    public int getNotifMid() { return notifMid; } public void setNotifMid(int v) { notifMid = v; }
    public int getNotifMidWeight() { return notifMidWeight; } public void setNotifMidWeight(int v) { notifMidWeight = v; }
    public int getNotifLow() { return notifLow; } public void setNotifLow(int v) { notifLow = v; }
    public int getNotifLowWeight() { return notifLowWeight; } public void setNotifLowWeight(int v) { notifLowWeight = v; }
    public int getOverlayWeight() { return overlayWeight; } public void setOverlayWeight(int v) { overlayWeight = v; }
    public int getFullScreenWeight() { return fullScreenWeight; } public void setFullScreenWeight(int v) { fullScreenWeight = v; }
    public int getAccessibilityWeight() { return accessibilityWeight; } public void setAccessibilityWeight(int v) { accessibilityWeight = v; }
    public int getSmsWeight() { return smsWeight; } public void setSmsWeight(int v) { smsWeight = v; }
    public int getListenerWeight() { return listenerWeight; } public void setListenerWeight(int v) { listenerWeight = v; }
    public int getSelfLaunchHigh() { return selfLaunchHigh; } public void setSelfLaunchHigh(int v) { selfLaunchHigh = v; }
    public int getSelfLaunchHighWeight() { return selfLaunchHighWeight; } public void setSelfLaunchHighWeight(int v) { selfLaunchHighWeight = v; }
    public int getSelfLaunchLow() { return selfLaunchLow; } public void setSelfLaunchLow(int v) { selfLaunchLow = v; }
    public int getSelfLaunchLowWeight() { return selfLaunchLowWeight; } public void setSelfLaunchLowWeight(int v) { selfLaunchLowWeight = v; }
}
