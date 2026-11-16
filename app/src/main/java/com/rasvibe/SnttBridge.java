package com.rasvibe;

import android.content.Context;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * JavaScript bridge for the SNTT pages. The method names match the ones the
 * SNTT HTML already calls (Android.openTool, TinyDB.getValue, ...), so the
 * original tool buttons and calibration flow work again.
 */
final class SnttBridge {

    interface Host {
        void openTool(String tag);
        void openShip(String shipJson);
        void goHome();
        void startDownload();
        void showToast(String message);
        void saveImage(String base64Data, String fileName);
        void shareImage(String base64Data, String fileName);
    }

    private final Context context;
    private final Host host;
    private volatile String lastWebViewString = "";
    private volatile boolean modalOpen = false;

    SnttBridge(Context context, Host host) {
        this.context = context;
        this.host = host;
    }

    @JavascriptInterface
    public void openTool(String tag) {
        if (tag != null && !tag.isEmpty()) host.openTool(tag);
    }

    @JavascriptInterface
    public void onShipSelected(String shipJson) {
        if (shipJson != null) host.openShip(shipJson);
    }

    @JavascriptInterface
    public void setWebViewString(String value) {
        lastWebViewString = value == null ? "" : value;
        if (value != null && value.startsWith("{") && value.contains("Ship Name")) {
            host.openShip(value);
        }
    }

    @JavascriptInterface
    public String getWebViewString() {
        return lastWebViewString;
    }

    @JavascriptInterface
    public String getValue(String tag) {
        return SnttHome.getValue(context, tag, "");
    }

    @JavascriptInterface
    public String getValue(String tag, String defaultValue) {
        return SnttHome.getValue(context, tag, defaultValue);
    }

    @JavascriptInterface
    public void storeValue(String tag, String value) {
        SnttHome.putValue(context, tag, value);
    }

    @JavascriptInterface
    public boolean hasTag(String tag) {
        return SnttHome.hasTag(context, tag);
    }

    @JavascriptInterface
    public String getAllTags() {
        JSONArray arr = new JSONArray();
        for (String tag : SnttHome.allTags(context)) arr.put(tag);
        return arr.toString();
    }

    @JavascriptInterface
    public String getStatus() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("tagCount", SnttHome.allTags(context).size());
            obj.put("hasHomeUI", SnttHome.hasTag(context, SnttHome.HOME_TAG));
            obj.put("hasCalibration", SnttHome.hasTag(context, SnttHome.CALIBRATION_TAG));
            return obj.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    @JavascriptInterface
    public void startDownload() {
        host.startDownload();
    }

    @JavascriptInterface
    public void goHome() {
        host.goHome();
    }

    @JavascriptInterface
    public void showToast(String message) {
        host.showToast(message);
    }

    @JavascriptInterface
    public void saveImage(String base64Data, String fileName) {
        host.saveImage(base64Data, fileName);
    }

    @JavascriptInterface
    public void shareImage(String base64Data, String fileName) {
        host.shareImage(base64Data, fileName);
    }

    @JavascriptInterface
    public void setModalOpen(boolean open) {
        modalOpen = open;
    }

    @JavascriptInterface
    public void setPopupOpen(boolean open) {
        modalOpen = open;
    }

    @JavascriptInterface
    public boolean isModalOpen() {
        return modalOpen;
    }
}
