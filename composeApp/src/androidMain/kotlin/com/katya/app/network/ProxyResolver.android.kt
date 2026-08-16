package com.katya.app.network

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

object ProxyResolver {

    /**
     * Parses a URI string into a java.net.Proxy object if it represents a direct proxy.
     * Returns null if the URI is not a direct proxy (e.g., a subscription URL or vless:// link).
     */
    fun resolveDirectProxy(uriString: String): Proxy? {
        if (uriString.isBlank()) return null
        
        // Treat socks5:// or socks:// as direct SOCKS proxy
        if (uriString.startsWith("socks5://") || uriString.startsWith("socks://")) {
            try {
                val uri = URI(uriString)
                val host = uri.host ?: return null
                val port = if (uri.port > 0) uri.port else 1080
                return Proxy(Proxy.Type.SOCKS, InetSocketAddress(host, port))
            } catch (e: Exception) {
                return null
            }
        }
        
        // Treat http:// as HTTP proxy ONLY if there is no path (or path is /)
        // Subscription links typically have a longer path
        if (uriString.startsWith("http://")) {
            try {
                val uri = URI(uriString)
                val host = uri.host ?: return null
                val port = if (uri.port > 0) uri.port else 80
                val path = uri.path ?: ""
                
                // If it's a proxy link, usually the path is empty or just "/"
                if (path.isEmpty() || path == "/") {
                    return Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
                }
            } catch (e: Exception) {
                return null
            }
        }
        
        return null
    }
}
