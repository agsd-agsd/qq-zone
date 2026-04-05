package com.qzone.mobile.bridge;

public class Client {
    private final String baseDir;

    Client(String baseDir) {
        this.baseDir = baseDir;
    }

    public byte[] startLogin() throws Exception {
        throw new UnsupportedOperationException(
                "Go bridge AAR not found. Run scripts/bind_android_bridge.ps1 first. baseDir=" + baseDir
        );
    }

    public String pollLogin() {
        return "{\"status\":\"error\",\"message\":\"Go bridge AAR not found. Run scripts/bind_android_bridge.ps1 first.\",\"qq\":\"\",\"nickname\":\"\"}";
    }

    public String importWebLogin(String cookieHeader) {
        return "{\"status\":\"error\",\"message\":\"Go bridge AAR not found. Run scripts/bind_android_bridge.ps1 first.\",\"qq\":\"\",\"nickname\":\"\"}";
    }

    public String listSelfAlbums() {
        return "{\"status\":\"error\",\"message\":\"Go bridge AAR not found. Run scripts/bind_android_bridge.ps1 first.\",\"qq\":\"\",\"nickname\":\"\",\"hiddenCount\":0,\"albums\":[]}";
    }

    public String startSelfDownload() throws Exception {
        throw new UnsupportedOperationException(
                "Go bridge AAR not found. Run scripts/bind_android_bridge.ps1 first."
        );
    }

    public String startSelectedDownload(String selectedAlbumIdsJson) throws Exception {
        throw new UnsupportedOperationException(
                "Go bridge AAR not found. Run scripts/bind_android_bridge.ps1 first."
        );
    }

    public String getJobStatus(String jobId) {
        return "{\"status\":\"error\",\"phase\":\"error\",\"total\":0,\"success\":0,\"failed\":0,\"images\":0,\"videos\":0,\"currentAlbum\":\"\",\"currentFile\":\"\",\"saveDir\":\"" + escape(baseDir) + "\",\"message\":\"Go bridge AAR not found. Run scripts/bind_android_bridge.ps1 first.\"}";
    }

    public void cancelJob(String jobId) throws Exception {
        throw new UnsupportedOperationException(
                "Go bridge AAR not found. Run scripts/bind_android_bridge.ps1 first."
        );
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\");
    }
}
