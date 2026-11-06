package polaris.networking.gameserver.ssl;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SSLCertificateLoader {
    private static final String SSL_PATH = "ssl";
    private static final Logger LOGGER = LoggerFactory.getLogger(SSLCertificateLoader.class);

    public static SslContext getContext() {
        try {
            File certFile = new File(SSL_PATH + File.separator + "cert.pem");
            File keyFile = new File(SSL_PATH + File.separator + "privkey.pem");

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
