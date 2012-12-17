package telexme.tag;

import javaaddin.math.BigInteger;

public class Coord {
	private BigInteger[] a = new BigInteger[3]; // little-endian, a[2] most
												// significant
	
	public Coord() {
		a[0] = BigInteger.valueOf(0);
		a[1] = BigInteger.valueOf(0);
		a[2] = BigInteger.valueOf(0);
	}

	public Coord(int a0, int a1, int a2) {
		this(BigInteger.valueOf(a0), BigInteger.valueOf(a1),
				BigInteger.valueOf(a2));
	}

	public Coord(BigInteger a0, BigInteger a1, BigInteger a2) {
		a[0] = a0; a[1] = a1; a[2] = a2;
	}

	public BigInteger get(int i) {
		return a[i];
	}
	
	public void set(int i, BigInteger in) {
		a[i] = in;
	}
	
	public void copyFrom(Coord other) {
		for (int i = 0; i < a.length; i++) {
			a[i] = other.a[i];   // BigIntegers are immutable
		}
	}
}