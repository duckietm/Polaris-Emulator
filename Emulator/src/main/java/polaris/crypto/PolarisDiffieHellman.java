package polaris.crypto;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import polaris.crypto.exceptions.PolarisCryptoException;
import polaris.crypto.utils.BigIntegerUtils;
import polaris.util.HexUtils;

public class PolarisDiffieHellman {

    private static final int DH_PRIMES_BIT_SIZE = 128;
    private static final int DH_KEY_BIT_SIZE = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PolarisRSACrypto crypto;

    private BigInteger DHPrime;
    private BigInteger DHGenerator;
    private BigInteger DHPrivate;
    private BigInteger DHPublic;

    public PolarisDiffieHellman(PolarisRSACrypto crypto) {
        this.crypto = crypto;
        this.generateDHPrimes();
        this.generateDHKeys();
    }

    public BigInteger getDHPrime() {
        return DHPrime;
    }

    public BigInteger getDHGenerator() {
        return DHGenerator;
    }

    private void generateDHPrimes() {
        this.DHPrime = BigInteger.probablePrime(DH_PRIMES_BIT_SIZE, RANDOM);
        this.DHGenerator = BigInteger.probablePrime(DH_PRIMES_BIT_SIZE, RANDOM);

        if (this.DHGenerator.compareTo(this.DHPrime) > 0) {
            BigInteger temp = this.DHPrime;

            this.DHPrime = this.DHGenerator;
            this.DHGenerator = temp;
        }
    }

    private void generateDHKeys() {
        this.DHPrivate = BigInteger.probablePrime(DH_KEY_BIT_SIZE, RANDOM);
        this.DHPublic = this.DHGenerator.modPow(this.DHPrivate, this.DHPrime);
    }

    private String encryptBigInteger(BigInteger integer) throws PolarisCryptoException {
        String str = integer.toString(10);
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = this.crypto.Sign(bytes);

        return HexUtils.toHex(encrypted).toLowerCase();
    }

    private BigInteger decryptBigInteger(String str) throws PolarisCryptoException {
        byte[] bytes = HexUtils.toBytes(str);
        // A number fits one RSA block; a longer value only made the server run one RSA operation per block.
        if (bytes.length != this.crypto.blockSize()) {
            throw new PolarisCryptoException(
                    "Expected one RSA block of " + this.crypto.blockSize() + " bytes, got " + bytes.length + ".");
        }

        byte[] decrypted = this.crypto.Decrypt(bytes);
        String intStr = new String(decrypted, StandardCharsets.UTF_8);

        return new BigInteger(intStr, 10);
    }

    public String getPublicKey() throws PolarisCryptoException {
        return encryptBigInteger(this.DHPublic);
    }

    public String getSignedPrime() throws PolarisCryptoException {
        return encryptBigInteger(this.DHPrime);
    }

    public String getSignedGenerator() throws PolarisCryptoException {
        return encryptBigInteger(this.DHGenerator);
    }

    public void doHandshake(String signedPrime, String signedGenerator) throws PolarisCryptoException {
        this.DHPrime = decryptBigInteger(signedPrime);
        this.DHGenerator = decryptBigInteger(signedGenerator);

        if (this.DHPrime == null || this.DHGenerator == null) {
            throw new PolarisCryptoException("DHPrime or DHGenerator was null.");
        }

        if (this.DHPrime.compareTo(BigInteger.valueOf(2)) < 1) {
            throw new PolarisCryptoException("Prime cannot be <= 2!\nPrime: " + this.DHPrime.toString());
        }

        if (this.DHGenerator.compareTo(this.DHPrime) > -1) {
            throw new PolarisCryptoException("Generator cannot be >= Prime!\nPrime: " + this.DHPrime.toString()
                    + "\nGenerator: " + this.DHGenerator.toString());
        }

        generateDHKeys();
    }

    public byte[] getSharedKey(String publicKeyStr) throws PolarisCryptoException {
        BigInteger publicKey = this.decryptBigInteger(publicKeyStr);
        BigInteger sharedKey = publicKey.modPow(this.DHPrivate, this.DHPrime);

        return BigIntegerUtils.toUnsignedByteArray(sharedKey);
    }
}
