package polaris.crypto;

public class PolarisEncryption {

    private final PolarisRSACrypto crypto;
    private final PolarisDiffieHellman diffie;

    public PolarisEncryption(String e, String n, String d) {
        this.crypto = new PolarisRSACrypto(e, n, d);
        this.diffie = new PolarisDiffieHellman(this.crypto);
    }

    public PolarisRSACrypto getCrypto() {
        return crypto;
    }

    public PolarisDiffieHellman getDiffie() {
        return diffie;
    }
}
