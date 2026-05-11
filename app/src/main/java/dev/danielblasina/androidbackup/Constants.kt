package dev.danielblasina.androidbackup

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

var bcInitialized = false
const val BC_PROVIDER_NAME = "BC"

fun getBCProvider(): String {
    if (!bcInitialized) {
        Security.removeProvider(BC_PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())
        bcInitialized = true
    }
    return BC_PROVIDER_NAME
}
