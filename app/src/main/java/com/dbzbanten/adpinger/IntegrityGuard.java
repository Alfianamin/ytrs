package com.dbzbanten.adpinger;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.security.MessageDigest;
import java.util.Locale;

/**
 * Validates package name, minimum version and signing certificate.
 */
public final class IntegrityGuard {
    private IntegrityGuard() {}

    public static Result verify(Context context, ExpiryConfig.Result remote) {
        if (remote == null) {
            return Result.fail("Konfigurasi lisensi kosong");
        }

        try {
            if (remote.packageName != null
                    && !remote.packageName.isEmpty()
                    && !context.getPackageName().equals(remote.packageName)) {
                return Result.fail("Package aplikasi tidak sesuai");
            }

            if (remote.minVersionCode > 0) {
                long version = getVersionCode(context);
                if (version < remote.minVersionCode) {
                    return Result.fail("Versi aplikasi sudah terlalu lama");
                }
            }

            if (remote.signatureSha256 == null
                    || remote.signatureSha256.trim().isEmpty()) {
                return Result.ok("Pemeriksaan sertifikat belum dikunci");
            }

            String actual = getSigningCertificateSha256(context);
            String expected = normalize(remote.signatureSha256);

            if (actual.isEmpty()) {
                return Result.fail(
                        "Sertifikat penandatangan APK tidak ditemukan");
            }

            if (!expected.equals(actual)) {
                return Result.fail(
                        "Integritas aplikasi tidak valid (SHA-256 sertifikat tidak cocok)");
            }

            return Result.ok("Integritas aplikasi valid");

        } catch (Exception e) {
            String detail = e.getMessage();

            if (detail == null || detail.trim().isEmpty()) {
                detail = e.getClass().getSimpleName();
            }

            return Result.fail(
                    "Pemeriksaan integritas gagal: " + detail);
        }
    }

    private static long getVersionCode(Context context) throws Exception {
        PackageManager pm = context.getPackageManager();
        PackageInfo pi = pm.getPackageInfo(
                context.getPackageName(), 0);

        if (Build.VERSION.SDK_INT >= 28) {
            return pi.getLongVersionCode();
        }

        return pi.versionCode;
    }

    private static String getSigningCertificateSha256(
            Context context) throws Exception {

        PackageManager pm = context.getPackageManager();
        Signature[] signatures;

        if (Build.VERSION.SDK_INT >= 28) {

            PackageInfo pi = pm.getPackageInfo(
                    context.getPackageName(),
                    PackageManager.GET_SIGNING_CERTIFICATES);

            if (pi.signingInfo == null) {
                throw new IllegalStateException("SigningInfo kosong");
            }

            if (pi.signingInfo.hasMultipleSigners()) {
                signatures = pi.signingInfo.getApkContentsSigners();
            } else {
                signatures = pi.signingInfo.getSigningCertificateHistory();
            }

        } else {

            PackageInfo pi = pm.getPackageInfo(
                    context.getPackageName(),
                    PackageManager.GET_SIGNATURES);

            signatures = pi.signatures;
        }

        if (signatures == null
                || signatures.length == 0
                || signatures[0] == null) {
            throw new IllegalStateException(
                    "Sertifikat APK kosong");
        }

        MessageDigest md =
                MessageDigest.getInstance("SHA-256");

        return normalize(
                toHex(md.digest(signatures[0].toByteArray())));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out =
                new StringBuilder(bytes.length * 3);

        for (byte b : bytes) {
            out.append(String.format(
                    Locale.US, "%02X:", b & 0xff));
        }

        if (out.length() > 0) {
            out.setLength(out.length() - 1);
        }

        return out.toString();
    }

    private static String normalize(String s) {
        return s == null
                ? ""
                : s.replace(" ", "")
                   .replace("-", ":")
                   .toUpperCase(Locale.US);
    }

    public static final class Result {
        public final boolean valid;
        public final String message;

        private Result(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        static Result ok(String m) {
            return new Result(true, m);
        }

        static Result fail(String m) {
            return new Result(false, m);
        }
    }
}
