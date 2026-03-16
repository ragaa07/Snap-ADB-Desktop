package com.ragaa.snapadb.feature.network.model

enum class ProxyPreset(val displayName: String, val defaultHost: String, val defaultPort: Int) {
    CHARLES("Charles Proxy", "127.0.0.1", 8888),
    FIDDLER("Fiddler", "127.0.0.1", 8888),
    MITMPROXY("mitmproxy", "127.0.0.1", 8080),
    PROXYMAN("Proxyman", "127.0.0.1", 9090),
}
