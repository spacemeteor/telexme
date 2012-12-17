package telexme.tag;

import javaaddin.math.BigInteger;

public class UnreducedCoord {
	private BigInteger[] b = new BigInteger[5]; // little-endian, b[4] most
	// significant
	
	public UnreducedCoord() {
		b[0] = BigInteger.valueOf(0);
		b[1] = BigInteger.valueOf(0);
		b[2] = BigInteger.valueOf(0);
		b[3] = BigInteger.valueOf(0);
		b[4] = BigInteger.valueOf(0);
	}
	
	public BigInteger get(int i) {
		return b[i];
	}
	
	public void set(int i, BigInteger in) {
		b[i] = in;
	}
}
