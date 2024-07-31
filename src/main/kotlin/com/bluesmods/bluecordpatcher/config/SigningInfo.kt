package com.bluesmods.bluecordpatcher.config

import java.io.File

abstract class SigningInfo {

    data class PlainSigningInfo(
        /**
         * The APK signing key to use when signing the newly built APK.
         */
        val apkSigningKey: File,

        /**
         * The APK signing certificate to use when signing the newly built APK.
         */
        val apkSigningCert: File,
    ) : SigningInfo()

    data class PasswordSigningInfo(
        /**
         * The APK signing key to use when signing the newly built APK.
         */
        val apkSigningKey: File,

        /**
         * The APK keystore password to use when signing the newly built APK.
         *
         * Set to null if there is no password protection on the [apkSigningKey].
         */
        val apkKeyStorePassword: String
    ) : SigningInfo()

    /**
     * V3 signature rotation.
     *
     * Example setup:
     * ```
     *  apksigner \
     *      rotate \
     *      --out /path/to/lineage/file \
     *      --old-signer --key /key.pk8 --cert cert.pem \ # If your signer uses jks, use --ks / --ks-pass instead
     *      --set-installed-data true \
     *      --set-shared-uid true \
     *      --set-permission true \
     *      --set-rollback false \
     *      --set-auth true \
     *      --new-signer --ks new_signer.jks --ks-pass "pass:your_password"
     * ```
     */
    data class RotationSigningInfo(
        /**
         * Lineage file previously generated using `apksigner rotate`
         */
        val lineage: File,
        /**
         * List of signers to use.
         */
        val signers: List<SigningInfo>
    ) : SigningInfo()
}
