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

}
