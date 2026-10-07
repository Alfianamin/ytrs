package com.dbzbanten.adpinger;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Remote expiry/license check hosted in GitHub. */
public final class ExpiryConfig {
    public static final String CONFIG_URL =
            "https://raw.githubusercontent.com/Alfianamin/ytrs/refs/heads/main/expiry.json";

    private ExpiryConfig() {}

    public static Result fetch() throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(CONFIG_URL).openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            c.setUseCaches(false);
            c.setRequestProperty("Cache-Control", "no-cache");
            c.setRequestProperty("Pragma", "no-cache");
            c.setRequestProperty("Accept", "application/json");

            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);

            StringBuilder body = new StringBuilder();
            try (InputStream in = c.getInputStream();
                 BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
                String line;
                while ((line = r.readLine()) != null) body.append(line);
            }

            JSONObject json = new JSONObject(body.toString());
            boolean enabled = json.optBoolean("enabled", true);
            String expiresAt = json.getString("expires_at").trim();
            String message = json.optString("message",
                    "Masa berlaku aplikasi telah berakhir.").trim();
            Date expiry = parseDate(expiresAt);

            // Prefer GitHub/CDN HTTP Date as the reference clock.
            long serverMillis = c.getHeaderFieldDate("Date", -1L);
            Date now = serverMillis > 0 ? new Date(serverMillis) : new Date();
            boolean expired = !enabled || !now.before(expiry);

            String signatureSha256 = json.optString("signature_sha256", "").trim();
            String packageName = json.optString("package_name", "").trim();
            long minVersionCode = json.optLong("min_version_code", 0L);

            return new Result(enabled, expired, expiry, now, message,
                    serverMillis > 0, body.toString(), signatureSha256, packageName, minVersionCode);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static Date parseDate(String value) {
        try {
            SimpleDateFormat f = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            f.setLenient(false);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(value);
        } catch (ParseException e) {
            throw new IllegalArgumentException(
                    "expires_at harus ISO-8601 UTC, contoh 2026-12-31T23:59:59Z", e);
        }
    }

    public static final class Result {
        public final boolean enabled;
        public final boolean expired;
        public final Date expiresAt;
        public final Date checkedAt;
        public final String message;
        public final boolean serverClock;
        public final String rawJson;
        public final String signatureSha256;
        public final String packageName;
        public final long minVersionCode;

        Result(boolean enabled, boolean expired, Date expiresAt, Date checkedAt,
               String message, boolean serverClock, String rawJson,
               String signatureSha256, String packageName, long minVersionCode) {
            this.enabled = enabled;
            this.expired = expired;
            this.expiresAt = expiresAt;
            this.checkedAt = checkedAt;
            this.message = message;
            this.serverClock = serverClock;
            this.rawJson = rawJson;
            this.signatureSha256 = signatureSha256;
            this.packageName = packageName;
            this.minVersionCode = minVersionCode;
        }

        public String statusText() {
            SimpleDateFormat f = new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return "Berlaku sampai " + f.format(expiresAt);
        }
    }
}
