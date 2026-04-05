package com.qzone.mobile.bridge;

public final class Bridge {
    private Bridge() {}

    public static Client newClient(String baseDir) {
        return new Client(baseDir);
    }
}
