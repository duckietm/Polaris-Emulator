package polaris.crypto;

public class PolarisRC4 {

    private int i;
    private int j;
    private final int[] table = new int[256];

    public PolarisRC4(byte[] key) {
        int length = key.length;

        while (this.i < 256) {
            table[this.i] = this.i;
            this.i++;
        }

        this.i = 0;
        this.j = 0;

        while (this.i < 256) {
            this.j = ((this.j + this.table[this.i]) + (key[this.i % length] & 0xff)) % 256;
            this.swap(this.i, this.j);
            this.i++;
        }

        this.i = 0;
        this.j = 0;
    }

    private void swap(int a, int b) {
        int num = table[a];
        table[a] = table[b];
        table[b] = num;
    }

    public void parse(byte[] bytes) {
        for (int index1 = 0; index1 < bytes.length; index1++) {
            this.i = (this.i + 1) % 256;
            this.j = (this.j + this.table[this.i]) % 256;
            this.swap(this.i, this.j);

            bytes[index1] =
                    (byte) ((bytes[index1] & 0xFF) ^ this.table[(this.table[this.i] + this.table[this.j]) % 256]);
        }
    }
}
