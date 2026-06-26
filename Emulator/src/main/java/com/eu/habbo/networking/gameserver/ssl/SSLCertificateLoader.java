package com.eu.habbo.networking.gameserver.ssl;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class SSLCertificateLoader {
    private static final String SSL_PATH = "ssl";
    private static final Logger LOGGER = LoggerFactory.getLogger(SSLCertificateLoader.class);

    public static SslContext getContext() {
        try {
            var certFile = new File(SSL_PATH + File.separator + "cert.pem");
            var keyFile = new File(SSL_PATH + File.separator + "privkey.pem");

            if (!certFile.exists() || !keyFile.exists()) {
                LOGGER.debug("SSL certificates not found in '{}' directory, WSS disabled", SSL_PATH);
                return null;
            }

            SslContext context = SslContextBuilder.forServer(certFile, keyFile).build();
            LOGGER.info("SSL certificates loaded successfully, WSS enabled");
            return context;
        } catch (Exception e) {
            LOGGER.warn("Failed to load SSL certificates: {}", e.getMessage());
            return null;
        }
    }
}
