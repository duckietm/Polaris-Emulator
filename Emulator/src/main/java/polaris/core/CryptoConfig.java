package polaris.core;

public class CryptoConfig {

    private final boolean enabled;
    private final String exponent;
    private final String modulus;
    private final String privateExponent;

    public CryptoConfig(boolean enabled, String exponent, String modulus, String privateExponent) {
        this.enabled = enabled;
        this.exponent = exponent;
        this.modulus = modulus;
        this.privateExponent = privateExponent;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getExponent() {
        return exponent;
    }

    public String getModulus() {
        return modulus;
    }

    public String getPrivateExponent() {
        return privateExponent;
    }
}
