package dev.danielblasina.androidbackup

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

var bc_initialized = false
const val BC_PROVIDER_NAME = "BC"

fun getBCProvider(): String {
    if (!bc_initialized){
        Security.removeProvider(BC_PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())
        bc_initialized = true
    }
    return BC_PROVIDER_NAME
}
