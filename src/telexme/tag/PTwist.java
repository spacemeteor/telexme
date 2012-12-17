package telexme.tag;

import org.bouncycastle.util.Arrays;

import javaaddin.math.BigInteger;

public class PTwist {
	public static final int PTWIST_BITS = 168;
	public static final int PTWIST_BYTES = PTWIST_BITS / 8; // 21 bytes
	public static final int PTWIST_TAG_BITS = 224;
	public static final int PTWIST_TAG_BYTES = PTWIST_TAG_BITS / 8;	// 28 bytes
	
	private static BigInteger getBigIntegerFromLE(byte[] inLE, int offset, int len) {
		if (offset < 0 || offset >= inLE.length || len < 0 || offset + len > inLE.length)
			throw new IllegalArgumentException();
		byte[] bytesBE = new byte[len];
		for (int i = 0; i < len; i++) {
			bytesBE[i] = inLE[offset + len - i - 1];
		}
		boolean hasNonZero = false;
		for (int i = 0; i < len; i++) {
			if (bytesBE[i] != 0) {
				hasNonZero = true;
				break;
			}
		}
		return new BigInteger(hasNonZero ? 1 : 0, bytesBE);	// work around BigInteger bug?
	}
	
	private static void bin21_to_felem(Coord out, byte[] in) {
		if (in.length != PTWIST_BYTES)
			throw new IllegalArgumentException();
		// in[0] to in[6] go to a[0]
		out.set(0, getBigIntegerFromLE(in, 0, 7));
		// in[7] to in[13] go to a[1]
		out.set(1, getBigIntegerFromLE(in, 7, 7));
		// in[14] to in[20] go to a[2]
		out.set(2, getBigIntegerFromLE(in, 14, 7));
	}
	
	private static void felem_to_bin21(byte[] out, Coord in) {
		for (int i = 0; i < 7; i++) {
			out[i]    = in.get(0).shiftRight(8*i).byteValue();
			out[i+7]  = in.get(1).shiftRight(8*i).byteValue();
			out[i+14] = in.get(2).shiftRight(8*i).byteValue();
		}
	}
	
	/******************************************************************************/
	/*				FIELD OPERATIONS
	 *
	 * Field operations, using the internal representation of field elements.
	 * NB! These operations are specific to our point multiplication and cannot be
	 * expected to be correct in general - e.g., multiplication with a large scalar
	 * will cause an overflow.
	 *
	 */
	
	/* Sum two field elements: out += in */
	private static void felem_sum64(Coord out, Coord in) {
		out.set(0, out.get(0).add(in.get(0)));
		out.set(1, out.get(1).add(in.get(1)));
		out.set(2, out.get(2).add(in.get(2)));
	}

	/* Subtract field elements: out -= in */
	/* Assumes in[i] < 2^57 */
	private static void felem_diff64(Coord out, Coord in) {
		/* a = 3*2^56 - 3 */
		/* b = 3*2^56 - 3*257 */
		BigInteger a = BigInteger.valueOf(3).shiftLeft(56).subtract(BigInteger.valueOf(3));
		BigInteger b = BigInteger.valueOf(3).shiftLeft(56).subtract(BigInteger.valueOf(771));
		
		/* Add 0 mod 2^168-2^8-1 to ensure out > in at each element */
		/* a*2^112 + a*2^56 + b = 3*p */
		out.set(0, out.get(0).add(b));
		out.set(1, out.get(1).add(a));
		out.set(2, out.get(2).add(a));
		
		out.set(0, out.get(0).subtract(in.get(0)));
		out.set(1, out.get(1).subtract(in.get(1)));
		out.set(2, out.get(2).subtract(in.get(2)));
	}
	
	/* Subtract in unreduced 128-bit mode: out128 -= in128 */
	/* Assumes in[i] < 2^119 */
	private static void felem_diff128(UnreducedCoord out, UnreducedCoord in) {
		/* a = 3*2^118 - 192
		   b = 3*2^118 - 49536
		   c = 3*2^118
		   d = 3*2^118 - 12681408

		   a*2^224 + a*2^168 + b*2^112 + c*2^56 + d
		    = (3*2^174 + 3*2^118 + 49344)*p
		*/
		BigInteger a = BigInteger.valueOf(3).shiftLeft(118).subtract(BigInteger.valueOf(192));
		BigInteger b = BigInteger.valueOf(3).shiftLeft(118).subtract(BigInteger.valueOf(49536));
		BigInteger c = BigInteger.valueOf(3).shiftLeft(118);
		BigInteger d = BigInteger.valueOf(3).shiftLeft(118).subtract(BigInteger.valueOf(12681408));
		
		/* Add 0 mod 2^168-2^8-1 to ensure out > in */
		out.set(0, out.get(0).add(d));
		out.set(1, out.get(1).add(c));
		out.set(2, out.get(2).add(b));
		out.set(3, out.get(3).add(a));
		out.set(4, out.get(4).add(a));
		
		out.set(0, out.get(0).subtract(in.get(0)));
		out.set(1, out.get(1).subtract(in.get(1)));
		out.set(2, out.get(2).subtract(in.get(2)));
		out.set(3, out.get(3).subtract(in.get(3)));
		out.set(4, out.get(4).subtract(in.get(4)));		
	}

	/* Subtract in mixed mode: out128 -= in64 */
	/* in[i] < 2^63 */
	private static void felem_diff_128_64(UnreducedCoord out, Coord in) {
		/* a = 3*2^62 - 192
		   b = 3*2^62 - 49344
		   a*2^112 + a*2^56 + b = 192*p
		*/
		BigInteger a = BigInteger.valueOf(3).shiftLeft(62).subtract(BigInteger.valueOf(192));
		BigInteger b = BigInteger.valueOf(3).shiftLeft(62).subtract(BigInteger.valueOf(49344));
		
		/* Add 0 mod 2^168-2^8-1 to ensure out > in */
		out.set(0, out.get(0).add(b));
		out.set(1, out.get(1).add(a));
		out.set(2, out.get(2).add(a));
		
		out.set(0, out.get(0).subtract(in.get(0)));
		out.set(1, out.get(1).subtract(in.get(1)));
		out.set(2, out.get(2).subtract(in.get(2)));		
	}

	/* Multiply a field element by a scalar: out64 = out64 * scalar
	 * The scalars we actually use are small, so results fit without overflow */
	private static void felem_scalar64(Coord out, BigInteger scalar) {
		out.set(0, out.get(0).multiply(scalar));
		out.set(1, out.get(1).multiply(scalar));
		out.set(2, out.get(2).multiply(scalar));
	}

	/* Multiply an unreduced field element by a scalar: out128 = out128 * scalar
	 * The scalars we actually use are small, so results fit without overflow */
	private static void felem_scalar128(UnreducedCoord out, BigInteger scalar) {
		out.set(0, out.get(0).multiply(scalar));
		out.set(1, out.get(1).multiply(scalar));
		out.set(2, out.get(2).multiply(scalar));
		out.set(3, out.get(3).multiply(scalar));
		out.set(4, out.get(4).multiply(scalar));
	}

	/* Square a field element: out = in^2 */
	private static void felem_square(UnreducedCoord out, Coord in) {
		// out[0] = ((uint128_t) in[0]) * in[0];
		BigInteger out0 = in.get(0).multiply(in.get(0)); 
		out.set(0, out0);
		
		// out[1] = ((uint128_t) in[0]) * in[1] * 2;
		BigInteger out1 = in.get(0).multiply(in.get(1)).multiply(BigInteger.valueOf(2)); 
		out.set(1, out1);
		
		// out[2] = ((uint128_t) in[0]) * in[2] * 2 + ((uint128_t) in[1]) * in[1];
		BigInteger out20 = in.get(0).multiply(in.get(2)).multiply(BigInteger.valueOf(2));
		BigInteger out21 = in.get(1).multiply(in.get(1));
		out.set(2, out20.add(out21));
		
		// out[3] = ((uint128_t) in[1]) * in[2] * 2;
		BigInteger out3 = in.get(1).multiply(in.get(2)).multiply(BigInteger.valueOf(2));
		out.set(3, out3);
		
		// out[4] = ((uint128_t) in[2]) * in[2];
		BigInteger out4 = in.get(2).multiply(in.get(2));
		out.set(4, out4);
	}

	/* Multiply two field elements: out = in1 * in2 */
	private static void felem_mul(UnreducedCoord out, Coord in1, Coord in2) {
		// 	out[0] = ((uint128_t) in1[0]) * in2[0];
		BigInteger out0 = in1.get(0).multiply(in2.get(0));
		out.set(0, out0);
		
		// out[1] = ((uint128_t) in1[0]) * in2[1] + ((uint128_t) in1[1]) * in2[0];
		BigInteger out10 = in1.get(0).multiply(in2.get(1));
		BigInteger out11 = in1.get(1).multiply(in2.get(0));
		out.set(1, out10.add(out11));
		
		// out[2] = ((uint128_t) in1[0]) * in2[2] + ((uint128_t) in1[1]) * in2[1] +
		//          ((uint128_t) in1[2]) * in2[0];
		BigInteger out20 = in1.get(0).multiply(in2.get(2));
		BigInteger out21 = in1.get(1).multiply(in2.get(1));
		BigInteger out22 = in1.get(2).multiply(in2.get(0));
		out.set(2, out20.add(out21).add(out22));
		
		// out[3] = ((uint128_t) in1[1]) * in2[2] + ((uint128_t) in1[2]) * in2[1];
		BigInteger out30 = in1.get(1).multiply(in2.get(2));
		BigInteger out31 = in1.get(2).multiply(in2.get(1));
		out.set(3, out30.add(out31));
		
		// out[4] = ((uint128_t) in1[2]) * in2[2];
		BigInteger out4 = in1.get(2).multiply(in2.get(2));
		out.set(4, out4);
	}

	private static BigInteger M257(BigInteger x) {
		return x.shiftLeft(8).add(x);
	}
	
	private static BigInteger Uint128Low64(BigInteger x) {
		BigInteger two64m1 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
		return x.and(two64m1);
	}
	
	/* Reduce 128-bit coefficients to 64-bit coefficients. Requires in[i] < 2^126,
	 * ensures out[0] < 2^56, out[1] < 2^56, out[2] < 2^57 */
	private static void felem_reduce(Coord out, UnreducedCoord in) {
		BigInteger two56m1 = BigInteger.ONE.shiftLeft(56).subtract(BigInteger.ONE);
		BigInteger[] output = new BigInteger[3];
		
		output[0] = in.get(0); /* < 2^126 */
		output[1] = in.get(1); /* < 2^126 */
		output[2] = in.get(2); /* < 2^126 */
		
		/* Eliminate in[3], in[4] */
		output[2] = output[2].add(M257(in.get(4).shiftRight(56))); /* < 2^126 + 2^79 */
		output[1] = output[1].add(M257(in.get(4).and(two56m1)));   /* < 2^126 + 2^65 */
		
		output[1] = output[1].add(M257(in.get(3).shiftRight(56))); /* < 2^126 + 2^65 + 2^79 */
		output[0] = output[0].add(M257(in.get(3).and(two56m1)));   /* < 2^126 + 2^65 */
		
		/* Eliminate the top part of output[2] */
		output[0] = output[0].add(M257(output[2].shiftRight(56))); /* < 2^126 + 2^65 + 2^79 */
		output[2] = output[2].and(two56m1);                        /* < 2^56 */
		
		/* Carry 0 -> 1 -> 2 */
		output[1] = output[1].add(output[0].shiftRight(56));       /* < 2^126 + 2^71 */
		output[0] = output[0].and(two56m1);                        /* < 2^56 */
		
		output[2] = output[2].add(output[1].shiftRight(56));       /* < 2^71 */
		output[1] = output[1].and(two56m1);                        /* < 2^56 */
		
		/* Eliminate the top part of output[2] */
		output[0] = output[0].add(M257(output[2].shiftRight(56))); /* < 2^57 */
		output[2] = output[2].and(two56m1);                        /* < 2^56 */
		
		/* Carry 0 -> 1 -> 2 */
		output[1] = output[1].add(output[0].shiftRight(56));       /* <= 2^56 */
		out.set(0, Uint128Low64(output[0].and(two56m1)));          /* < 2^56 */
		
		out.set(2, Uint128Low64(output[2].add(output[1].shiftRight(56)))); /* <= 2^56 */
		out.set(1, Uint128Low64(output[1].and(two56m1)));          /* < 2^56 */
	}
	
	/* Reduce to unique minimal representation */
	private static void felem_contract(Coord out, Coord in) {
		BigInteger two56m1 = BigInteger.ONE.shiftLeft(56).subtract(BigInteger.ONE);
		BigInteger two56m257 = BigInteger.ONE.shiftLeft(56).subtract(BigInteger.valueOf(257));
		BigInteger a;
		
		/* in[0] < 2^56, in[1] < 2^56, in[2] <= 2^56 */
		/* so in < 2*p for sure */
		
		/* Eliminate the top part of in[2] */
		BigInteger out0 = in.get(0).add(M257(in.get(2).shiftRight(56)));
		out.set(0, out0);                          /* < 2^57 */
		out.set(2, in.get(2).and(two56m1));        /* < 2^56, but if out[0] >= 2^56 then out[2] now = 0 */
		
		/* Carry 0 -> 1 -> 2 */
		BigInteger out1 = in.get(1).add(out.get(0).shiftRight(56));
		out.set(1, out1);                          /* < 2^56 + 2, but if out[1] >= 2^56 then out[2] = 0 */
		out.set(0, out.get(0).and(two56m1));       /* < 2^56 */
		
		out.set(2, out.get(2).add(out.get(1).shiftRight(56))); /* < 2^56 due to the above */
		out.set(1, out.get(1).and(two56m1));                   /* < 2^56 */
		
		/* Now out < 2^168, but it could still be > p */
		// TODO: not so sure, what are the return values of & and >= ?
		a = ((out.get(2).equals(two56m1))
				& (out.get(1).equals(two56m1))
				& (out.get(0).compareTo(two56m257) >= 0)) 
				? BigInteger.ONE : BigInteger.ZERO;
		out.set(2, out.get(2).subtract(two56m1.multiply(a)));
		out.set(1, out.get(1).subtract(two56m1.multiply(a)));
		out.set(0, out.get(0).subtract(two56m257.multiply(a)));
	}

	/* Negate a field element: out = -in */
	/* Assumes in[i] < 2^57 */
	private static void felem_neg(Coord out, Coord in) {
		/* a = 3*2^56 - 3 */
		/* b = 3*2^56 - 3*257 */
		BigInteger a = BigInteger.valueOf(3).shiftLeft(56).subtract(BigInteger.valueOf(3));
		BigInteger b = BigInteger.valueOf(3).shiftLeft(56).subtract(BigInteger.valueOf(771));
		BigInteger two56m1 = BigInteger.ONE.shiftLeft(56).subtract(BigInteger.ONE);
		Coord tmp = new Coord();
		
		/* Add 0 mod 2^168-2^8-1 to ensure out > in at each element */
		/* a*2^112 + a*2^56 + b = 3*p */
		tmp.set(0, b.subtract(in.get(0)));
		tmp.set(1, a.subtract(in.get(1)));
		tmp.set(2, a.subtract(in.get(2)));
		
		/* Carry 0 -> 1 -> 2 */
		tmp.set(1, tmp.get(1).add(tmp.get(0).shiftRight(56)));
		tmp.set(0, tmp.get(0).and(two56m1));                     /* < 2^56 */
		
		tmp.set(2, tmp.get(2).add(tmp.get(1).shiftRight(56)));   /* < 2^71 */
		tmp.set(1, tmp.get(1).and(two56m1));                     /* < 2^56 */
		
		felem_contract(out, tmp);
		
	}

	/* Zero-check: returns 1 if input is 0, and 0 otherwise.
	 * We know that field elements are reduced to in < 2^169,
	 * so we only need to check three cases: 0, 2^168 - 2^8 - 1,
	 * and 2^169 - 2^9 - 2 */
	private static boolean felem_is_zero(Coord in) {
		boolean zero, two168m8m1, two169m9m2;
		BigInteger two56m1 = BigInteger.ONE.shiftLeft(56).subtract(BigInteger.ONE);
		BigInteger two56m257 = BigInteger.ONE.shiftLeft(56).subtract(BigInteger.valueOf(257));
		BigInteger two57m1 = BigInteger.ONE.shiftLeft(57).subtract(BigInteger.ONE);
		BigInteger two56m514 = BigInteger.ONE.shiftLeft(56).subtract(BigInteger.valueOf(514));
		
		zero = (in.get(0).equals(BigInteger.ZERO))
				& (in.get(1).equals(BigInteger.ZERO))
				& (in.get(2).equals(BigInteger.ZERO));
		two168m8m1 = (in.get(2).equals(two56m1))
				& (in.get(1).equals(two56m1))
				& (in.get(0).equals(two56m257));
		two169m9m2 = (in.get(2).equals(two57m1))
				& (in.get(1).equals(two56m1))
				& (in.get(0).equals(two56m514));
		
		return (zero | two168m8m1 | two169m9m2);
	}
	
	/* Invert a field element */
	private static void felem_inv(Coord out, Coord in) {
		Coord ftmp = new Coord();
		Coord ftmp2 = new Coord();
		Coord ftmp3 = new Coord();
		Coord ftmp4 = new Coord();
		UnreducedCoord tmp = new UnreducedCoord();
		int i;
		
		felem_square(tmp, in); felem_reduce(ftmp, tmp);		/* 2 */
		felem_mul(tmp, in, ftmp); felem_reduce(ftmp, tmp);	/* 2^2 - 1 */
									/* = ftmp */

		felem_square(tmp, ftmp); felem_reduce(ftmp2, tmp);	/* 2^3 - 2 */
		felem_square(tmp, ftmp2); felem_reduce(ftmp2, tmp);	/* 2^4 - 2^2 */
		felem_mul(tmp, ftmp2, ftmp); felem_reduce(ftmp2, tmp);	/* 2^4 - 1 */
		felem_square(tmp, ftmp2); felem_reduce(ftmp2, tmp);	/* 2^5 - 2 */
		felem_square(tmp, ftmp2); felem_reduce(ftmp2, tmp);	/* 2^6 - 2^2 */
		felem_mul(tmp, ftmp2, ftmp); felem_reduce(ftmp, tmp);	/* 2^6 - 1 */
									/* = ftmp */

		felem_square(tmp, ftmp); felem_reduce(ftmp2, tmp);	/* 2^7 - 2 */
		for (i = 0; i < 5; ++i)					/* 2^12 - 2^6 */
			{
			felem_square(tmp, ftmp2); felem_reduce(ftmp2, tmp);
			}
		felem_mul(tmp, ftmp, ftmp2); felem_reduce(ftmp3, tmp);	/* 2^12 - 1 */
									/* = ftmp3 */

		felem_square(tmp, ftmp3); felem_reduce(ftmp2, tmp);	/* 2^13 - 2 */
		for (i = 0; i < 11; ++i)				/* 2^24 - 2^12 */
			{
			felem_square(tmp, ftmp2); felem_reduce(ftmp2, tmp);
			}
		felem_mul(tmp, ftmp2, ftmp3); felem_reduce(ftmp3, tmp);	/* 2^24 - 1 */
									/* = ftmp3 */
		felem_square(tmp, ftmp3); felem_reduce(ftmp2, tmp);	/* 2^25 - 2 */
		for (i = 0; i < 23; ++i)				/* 2^48 - 2^24 */
			{
			felem_square(tmp, ftmp2); felem_reduce(ftmp2, tmp);
			}
		felem_mul(tmp, ftmp2, ftmp3); felem_reduce(ftmp4, tmp);	/* 2^48 - 1 */
									/* = ftmp4 */
		felem_square(tmp, ftmp4); felem_reduce(ftmp2, tmp);	/* 2^49 - 2 */
		for (i = 0; i < 23; ++i)				/* 2^72 - 2^24 */
			{
			felem_square(tmp, ftmp2); felem_reduce(ftmp2, tmp);
			}
		felem_mul(tmp, ftmp2, ftmp3); felem_reduce(ftmp4, tmp);	/* 2^72 - 1 */
									/* = ftmp4 */

		felem_square(tmp, ftmp4); felem_reduce(ftmp2, tmp);	/* 2^73 - 2 */
		for (i = 0; i < 5; ++i)					/* 2^78 - 2^6 */
			{
			felem_square(tmp, ftmp2); felem_reduce(ftmp2, tmp);
			}
		felem_mul(tmp, ftmp, ftmp2); felem_reduce(ftmp2, tmp);	/* 2^78 - 1 */
		felem_square(tmp, ftmp2); felem_reduce(ftmp2, tmp);	/* 2^79 - 2 */
		felem_mul(tmp, in, ftmp2); felem_reduce(ftmp4, tmp);	/* 2^79 - 1 */
									/* = ftmp4 */
		felem_square(tmp, ftmp4); felem_reduce(ftmp2, tmp);	/* 2^80 - 2 */
		for (i = 0; i < 78; ++i)				/* 2^158 - 2^79 */
			{
			felem_square(tmp, ftmp2); felem_reduce(ftmp2, tmp);
			}
		felem_mul(tmp, ftmp4, ftmp2); felem_reduce(ftmp2, tmp); /* 2^158 - 1 */
		felem_square(tmp, ftmp2); felem_reduce(ftmp2, tmp);	/* 2^159 - 2 */
		felem_mul(tmp, in, ftmp2); felem_reduce(ftmp2, tmp);	/* 2^159 - 1 */
		for (i = 0; i < 7; ++i)					/* 2^166 - 2^7 */
			{
			felem_square(tmp, ftmp2); felem_reduce(ftmp2, tmp);
			}
		felem_mul(tmp, ftmp, ftmp2); felem_reduce(ftmp2, tmp);	/* 2^166 - 2^6 - 1 */
		felem_square(tmp, ftmp2); felem_reduce(ftmp2, tmp);	/* 2^167 - 2^7 - 2 */
		felem_square(tmp, ftmp2); felem_reduce(ftmp2, tmp);	/* 2^168 - 2^8 - 4 */
		felem_mul(tmp, in, ftmp2); felem_reduce(out, tmp);	/* 2^168 - 2^8 - 3 */
									/* = out */
	}

	/* Take the square root of a field element */
	private static void felem_sqrt(Coord out, Coord in) {
		Coord ftmp = new Coord();
		Coord ftmp2 = new Coord();
		UnreducedCoord tmp = new UnreducedCoord();
		int i;
		
		felem_square(tmp, in); felem_reduce(ftmp, tmp);		/* 2 */
		felem_mul(tmp, in, ftmp); felem_reduce(ftmp, tmp);	/* 2^2 - 1 */
									/* = ftmp */

		felem_square(tmp, ftmp); felem_reduce(ftmp2, tmp);	/* 2^3 - 2 */
		felem_square(tmp, ftmp2); felem_reduce(ftmp2, tmp);	/* 2^4 - 2^2 */
		felem_mul(tmp, ftmp2, ftmp); felem_reduce(ftmp2, tmp);	/* 2^4 - 1 */
		felem_square(tmp, ftmp2); felem_reduce(ftmp2, tmp);	/* 2^5 - 2 */
		felem_mul(tmp, ftmp2, in); felem_reduce(ftmp, tmp);	/* 2^5 - 1 */
									/* = ftmp */

		felem_square(tmp, ftmp); felem_reduce(ftmp2, tmp);	/* 2^6 - 2 */
		for (i = 0; i < 4; ++i)					/* 2^10 - 2^5 */
			{
			felem_square(tmp, ftmp2); felem_reduce(ftmp2, tmp);
			}
		felem_mul(tmp, ftmp, ftmp2); felem_reduce(ftmp, tmp);	/* 2^10 - 1 */
									/* = ftmp */

		felem_square(tmp, ftmp); felem_reduce(ftmp2, tmp);	/* 2^11 - 2 */
		for (i = 0; i < 9; ++i)					/* 2^20 - 2^10 */
			{
			felem_square(tmp, ftmp2); felem_reduce(ftmp2, tmp);
			}
		felem_mul(tmp, ftmp2, ftmp); felem_reduce(ftmp, tmp);	/* 2^20 - 1 */
									/* = ftmp */
		felem_square(tmp, ftmp); felem_reduce(ftmp2, tmp);	/* 2^21 - 2 */
		for (i = 0; i < 19; ++i)				/* 2^40 - 2^20 */
			{
			felem_square(tmp, ftmp2); felem_reduce(ftmp2, tmp);
			}
		felem_mul(tmp, ftmp2, ftmp); felem_reduce(ftmp, tmp);	/* 2^40 - 1 */
									/* = ftmp */
		felem_square(tmp, ftmp); felem_reduce(ftmp2, tmp);	/* 2^41 - 2 */
		for (i = 0; i < 39; ++i)				/* 2^80 - 2^40 */
			{
			felem_square(tmp, ftmp2); felem_reduce(ftmp2, tmp);
			}
		felem_mul(tmp, ftmp2, ftmp); felem_reduce(ftmp, tmp);	/* 2^80 - 1 */
									/* = ftmp */

		felem_square(tmp, ftmp); felem_reduce(ftmp2, tmp);	/* 2^81 - 2 */
		for (i = 0; i < 79; ++i)				/* 2^160 - 2^80 */
			{
			felem_square(tmp, ftmp2); felem_reduce(ftmp2, tmp);
			}
		felem_mul(tmp, ftmp, ftmp2); felem_reduce(ftmp2, tmp);	/* 2^160 - 1 */
		for (i = 0; i < 5; ++i)					/* 2^165 - 2^5 */
			{
			felem_square(tmp, ftmp2); felem_reduce(ftmp2, tmp);
			}
		felem_square(tmp, ftmp2); felem_reduce(out, tmp);	/* 2^166 - 2^6 */
									/* = out */
	}

	/* Copy in constant time:
	 * if icopy == 1, copy in to out,
	 * if icopy == 0, copy out to itself. */
	private static void copy_conditional(Coord out, Coord in, boolean icopy) {
		if (icopy) {
			out.copyFrom(in);
		} else {
			out.copyFrom(out); // TODO: time leak?
		}
	}

	/******************************************************************************/
	/*			 ELLIPTIC CURVE POINT OPERATIONS
	 *
	 * Points are represented in Jacobian projective coordinates:
	 * (X, Y, Z) corresponds to the affine point (X/Z^2, Y/Z^3),
	 * or to the point at infinity if Z == 0.
	 *
	 */

	
	/* Double an elliptic curve point:
	 * (X', Y', Z') = 2 * (X, Y, Z), where
	 * X' = (3 * (X - Z^2) * (X + Z^2))^2 - 8 * X * Y^2
	 * Y' = 3 * (X - Z^2) * (X + Z^2) * (4 * X * Y^2 - X') - 8 * Y^2
	 * Z' = (Y + Z)^2 - Y^2 - Z^2 = 2 * Y * Z
	 * Outputs can equal corresponding inputs, i.e., x_out == x_in is allowed,
	 * while x_out == y_in is not (maybe this works, but it's not tested). */
	private static void	point_double(Coord x_out, Coord y_out, Coord z_out,
		     Coord x_in, Coord y_in, Coord z_in) {
		UnreducedCoord tmp = new UnreducedCoord();
		UnreducedCoord tmp2 = new UnreducedCoord();
		Coord delta = new Coord();
		Coord gamma = new Coord();
		Coord beta = new Coord();
		Coord alpha = new Coord();
		Coord ftmp = new Coord(), ftmp2 = new Coord();
		ftmp.copyFrom(x_in);
		ftmp.copyFrom(x_in);
		
		/* delta = z^2 */
		felem_square(tmp, z_in);
		felem_reduce(delta, tmp);

		/* gamma = y^2 */
		felem_square(tmp, y_in);
		felem_reduce(gamma, tmp);

		/* beta = x*gamma */
		felem_mul(tmp, x_in, gamma);
		felem_reduce(beta, tmp);

		/* alpha = 3*(x-delta)*(x+delta) */
		felem_diff64(ftmp, delta);
		/* ftmp[i] < 2^57 + 2^58 + 2 < 2^59 */
		felem_sum64(ftmp2, delta);
		/* ftmp2[i] < 2^57 + 2^57 = 2^58 */
		felem_scalar64(ftmp2, BigInteger.valueOf(3));
		/* ftmp2[i] < 3 * 2^58 < 2^60 */
		felem_mul(tmp, ftmp, ftmp2);
		/* tmp[i] < 2^60 * 2^59 * 4 = 2^121 */
		felem_reduce(alpha, tmp);

		/* x' = alpha^2 - 8*beta */
		felem_square(tmp, alpha);
		/* tmp[i] < 4 * 2^57 * 2^57 = 2^116 */
		ftmp.copyFrom(beta);
		felem_scalar64(ftmp, BigInteger.valueOf(8));
		/* ftmp[i] < 8 * 2^57 = 2^60 */
		felem_diff_128_64(tmp, ftmp);
		/* tmp[i] < 2^116 + 2^64 + 8 < 2^117 */
		felem_reduce(x_out, tmp);

		/* z' = (y + z)^2 - gamma - delta */
		felem_sum64(delta, gamma);
		/* delta[i] < 2^57 + 2^57 = 2^58 */
		ftmp.copyFrom(y_in);
		felem_sum64(ftmp, z_in);
		/* ftmp[i] < 2^57 + 2^57 = 2^58 */
		felem_square(tmp, ftmp);
		/* tmp[i] < 4 * 2^58 * 2^58 = 2^118 */
		felem_diff_128_64(tmp, delta);
		/* tmp[i] < 2^118 + 2^64 + 8 < 2^119 */
		felem_reduce(z_out, tmp);

		/* y' = alpha*(4*beta - x') - 8*gamma^2 */
		felem_scalar64(beta, BigInteger.valueOf(4));
		/* beta[i] < 4 * 2^57 = 2^59 */
		felem_diff64(beta, x_out);
		/* beta[i] < 2^59 + 2^58 + 2 < 2^60 */
		felem_mul(tmp, alpha, beta);
		/* tmp[i] < 4 * 2^57 * 2^60 = 2^119 */
		felem_square(tmp2, gamma);
		/* tmp2[i] < 4 * 2^57 * 2^57 = 2^116 */
		felem_scalar128(tmp2, BigInteger.valueOf(8));
		/* tmp2[i] < 8 * 2^116 = 2^119 */
		felem_diff128(tmp, tmp2);
		/* tmp[i] < 2^119 + 2^120 < 2^121 */
		felem_reduce(y_out, tmp);
	}

	/* Add two elliptic curve points:
	 * (X_1, Y_1, Z_1) + (X_2, Y_2, Z_2) = (X_3, Y_3, Z_3), where
	 * X_3 = (Z_1^3 * Y_2 - Z_2^3 * Y_1)^2 - (Z_1^2 * X_2 - Z_2^2 * X_1)^3 -
	 * 2 * Z_2^2 * X_1 * (Z_1^2 * X_2 - Z_2^2 * X_1)^2
	 * Y_3 = (Z_1^3 * Y_2 - Z_2^3 * Y_1) * (Z_2^2 * X_1 * (Z_1^2 * X_2 - Z_2^2 * X_1)^2 - X_3) -
	 *        Z_2^3 * Y_1 * (Z_1^2 * X_2 - Z_2^2 * X_1)^3
	 * Z_3 = (Z_1^2 * X_2 - Z_2^2 * X_1) * (Z_1 * Z_2) */

	/* This function is not entirely constant-time:
	 * it includes a branch for checking whether the two input points are equal,
	 * (while not equal to the point at infinity).
	 * This case never happens during single point multiplication,
	 * so there is no timing leak for ECDH or ECDSA signing. */
	private static void point_add(Coord x3, Coord y3, Coord z3,
		Coord x1, Coord y1, Coord z1, Coord x2, Coord y2, Coord z2) {
		Coord ftmp = new Coord();
		Coord ftmp2 = new Coord();
		Coord ftmp3 = new Coord();
		Coord ftmp4 = new Coord();
		Coord ftmp5 = new Coord();
		UnreducedCoord tmp = new UnreducedCoord();
		UnreducedCoord tmp2 = new UnreducedCoord();
		boolean z1_is_zero, z2_is_zero, x_equal, y_equal;
		
		/* ftmp = z1^2 */
		felem_square(tmp, z1);
		felem_reduce(ftmp, tmp);

		/* ftmp2 = z2^2 */
		felem_square(tmp, z2);
		felem_reduce(ftmp2, tmp);

		/* ftmp3 = z1^3 */
		felem_mul(tmp, ftmp, z1);
		felem_reduce(ftmp3, tmp);

		/* ftmp4 = z2^3 */
		felem_mul(tmp, ftmp2, z2);
		felem_reduce(ftmp4, tmp);

		/* ftmp3 = z1^3*y2 */
		felem_mul(tmp, ftmp3, y2);
		/* tmp[i] < 4 * 2^57 * 2^57 = 2^116 */

		/* ftmp4 = z2^3*y1 */
		felem_mul(tmp2, ftmp4, y1);
		felem_reduce(ftmp4, tmp2);

		/* ftmp3 = z1^3*y2 - z2^3*y1 */
		felem_diff_128_64(tmp, ftmp4);
		/* tmp[i] < 2^116 + 2^64 + 8 < 2^117 */
		felem_reduce(ftmp3, tmp);

		/* ftmp = z1^2*x2 */
		felem_mul(tmp, ftmp, x2);
		/* tmp[i] < 4 * 2^57 * 2^57 = 2^116 */

		/* ftmp2 =z2^2*x1 */
		felem_mul(tmp2, ftmp2, x1);
		felem_reduce(ftmp2, tmp2);

		/* ftmp = z1^2*x2 - z2^2*x1 */
		felem_diff128(tmp, tmp2);
		/* tmp[i] < 2^116 + 2^64 + 8 < 2^117 */
		felem_reduce(ftmp, tmp);

		/* the formulae are incorrect if the points are equal
		 * so we check for this and do doubling if this happens */
		x_equal = felem_is_zero(ftmp);
		y_equal = felem_is_zero(ftmp3);
		z1_is_zero = felem_is_zero(z1);
		z2_is_zero = felem_is_zero(z2);
		/* In affine coordinates, (X_1, Y_1) == (X_2, Y_2) */
		if (x_equal && y_equal && !z1_is_zero && !z2_is_zero)
			{
			point_double(x3, y3, z3, x1, y1, z1);
			return;
			}

		/* ftmp5 = z1*z2 */
		felem_mul(tmp, z1, z2);
		felem_reduce(ftmp5, tmp);

		/* z3 = (z1^2*x2 - z2^2*x1)*(z1*z2) */
		felem_mul(tmp, ftmp, ftmp5);
		felem_reduce(z3, tmp);

		/* ftmp = (z1^2*x2 - z2^2*x1)^2 */
		ftmp5.copyFrom(ftmp);
		felem_square(tmp, ftmp);
		felem_reduce(ftmp, tmp);

		/* ftmp5 = (z1^2*x2 - z2^2*x1)^3 */
		felem_mul(tmp, ftmp, ftmp5);
		felem_reduce(ftmp5, tmp);

		/* ftmp2 = z2^2*x1*(z1^2*x2 - z2^2*x1)^2 */
		felem_mul(tmp, ftmp2, ftmp);
		felem_reduce(ftmp2, tmp);

		/* ftmp4 = z2^3*y1*(z1^2*x2 - z2^2*x1)^3 */
		felem_mul(tmp, ftmp4, ftmp5);
		/* tmp[i] < 4 * 2^57 * 2^57 = 2^116 */

		/* tmp2 = (z1^3*y2 - z2^3*y1)^2 */
		felem_square(tmp2, ftmp3);
		/* tmp2[i] < 4 * 2^57 * 2^57 < 2^116 */

		/* tmp2 = (z1^3*y2 - z2^3*y1)^2 - (z1^2*x2 - z2^2*x1)^3 */
		felem_diff_128_64(tmp2, ftmp5);
		/* tmp2[i] < 2^116 + 2^64 + 8 < 2^117 */

		/* ftmp5 = 2*z2^2*x1*(z1^2*x2 - z2^2*x1)^2 */
		ftmp5.copyFrom(ftmp2);
		felem_scalar64(ftmp5, BigInteger.valueOf(2));
		/* ftmp5[i] < 2 * 2^57 = 2^58 */

		/* x3 = (z1^3*y2 - z2^3*y1)^2 - (z1^2*x2 - z2^2*x1)^3 -
		   2*z2^2*x1*(z1^2*x2 - z2^2*x1)^2 */
		felem_diff_128_64(tmp2, ftmp5);
		/* tmp2[i] < 2^117 + 2^64 + 8 < 2^118 */
		felem_reduce(x3, tmp2);

		/* ftmp2 = z2^2*x1*(z1^2*x2 - z2^2*x1)^2 - x3 */
		felem_diff64(ftmp2, x3);
		/* ftmp2[i] < 2^57 + 2^58 + 2 < 2^59 */

		/* tmp2 = (z1^3*y2 - z2^3*y1)*(z2^2*x1*(z1^2*x2 - z2^2*x1)^2 - x3) */
		felem_mul(tmp2, ftmp3, ftmp2);
		/* tmp2[i] < 4 * 2^57 * 2^59 = 2^118 */

		/* y3 = (z1^3*y2 - z2^3*y1)*(z2^2*x1*(z1^2*x2 - z2^2*x1)^2 - x3) -
		   z2^3*y1*(z1^2*x2 - z2^2*x1)^3 */
		felem_diff128(tmp2, tmp);
		/* tmp2[i] < 2^118 + 2^120 < 2^121 */
		felem_reduce(y3, tmp2);

		/* the result (x3, y3, z3) is incorrect if one of the inputs is the
		 * point at infinity, so we need to check for this separately */

		/* if point 1 is at infinity, copy point 2 to output, and vice versa */
		copy_conditional(x3, x2, z1_is_zero);
		copy_conditional(x3, x1, z2_is_zero);
		copy_conditional(y3, y2, z1_is_zero);
		copy_conditional(y3, y1, z2_is_zero);
		copy_conditional(z3, z2, z1_is_zero);
		copy_conditional(z3, z1, z2_is_zero);
	}

	private static void affine(Point P) {
	    Coord z1 = new Coord();
	    Coord z2 = new Coord();
	    Coord xin = new Coord();
	    Coord yin = new Coord();
	    UnreducedCoord tmp = new UnreducedCoord();

	    if (felem_is_zero(P.get(2))) return;
	    felem_inv(z2, P.get(2));
	    felem_square(tmp, z2); felem_reduce(z1, tmp);
	    felem_mul(tmp, P.get(0), z1); felem_reduce(xin, tmp);
	    felem_contract(P.get(0), xin);
	    felem_mul(tmp, z1, z2); felem_reduce(z1, tmp);
	    felem_mul(tmp, P.get(1), z1); felem_reduce(yin, tmp);
	    felem_contract(P.get(1), yin);
	    P.set(2, new Coord());
	    P.get(2).set(0, BigInteger.ONE); // P[2][0] = 1;
	}

	private static void affine_x(Coord out, Point P) {
	    Coord z1 = new Coord();
	    Coord z2 = new Coord();
	    Coord xin = new Coord();
	    UnreducedCoord tmp = new UnreducedCoord();

	    if (felem_is_zero(P.get(2))) return;
	    felem_inv(z2, P.get(2));
	    felem_square(tmp, z2); felem_reduce(z1, tmp);
	    felem_mul(tmp, P.get(0), z1); felem_reduce(xin, tmp);
	    felem_contract(out, xin);
	}
	
	/* Multiply the given point by s */
	private static void point_mul(Point out, Point in, byte[] s) {
	    int i;
	    Point tmp = new Point();

	    Point[] table = new Point[16];
	    for (i = 0; i < 16; i++) {
	    	table[i] = new Point();
	    }
	    // memset(table[0], 0, sizeof(point)); // already done in for loop
	    //memmove(table[1], in, sizeof(point));
	    table[1].copyFrom(in);
	    for(i=2; i<16; i+=2) {
		point_double(table[i].get(0), table[i].get(1), table[i].get(2),
			     table[i/2].get(0), table[i/2].get(1), table[i/2].get(2));
		point_add(table[i+1].get(0), table[i+1].get(1), table[i+1].get(2),
			  table[i].get(0), table[i].get(1), table[i].get(2),
			  in.get(0), in.get(1), in.get(2));
	    }
	    /*
	    for(i=0;i<16;++i) {
		fprintf(stderr, "table[%d]:\n", i);
		affine(table[i]);
		dump_point(NULL, table[i]);
	    }
	    */

	    tmp = new Point();
	    for(i=0;i<21;i++) {
		byte oh = (byte) ((s[20-i] & 0xff) >>> 4);
		byte ol = (byte) (s[20-i] & 0x0f);
		point_double(tmp.get(0), tmp.get(1), tmp.get(2), tmp.get(0), tmp.get(1), tmp.get(2));
		point_double(tmp.get(0), tmp.get(1), tmp.get(2), tmp.get(0), tmp.get(1), tmp.get(2));
		point_double(tmp.get(0), tmp.get(1), tmp.get(2), tmp.get(0), tmp.get(1), tmp.get(2));
		point_double(tmp.get(0), tmp.get(1), tmp.get(2), tmp.get(0), tmp.get(1), tmp.get(2));
		if (oh != 0) {
		    point_add(tmp.get(0), tmp.get(1), tmp.get(2), tmp.get(0), tmp.get(1), tmp.get(2),
			      table[oh].get(0), table[oh].get(1), table[oh].get(2));
		}
		point_double(tmp.get(0), tmp.get(1), tmp.get(2), tmp.get(0), tmp.get(1), tmp.get(2));
		point_double(tmp.get(0), tmp.get(1), tmp.get(2), tmp.get(0), tmp.get(1), tmp.get(2));
		point_double(tmp.get(0), tmp.get(1), tmp.get(2), tmp.get(0), tmp.get(1), tmp.get(2));
		point_double(tmp.get(0), tmp.get(1), tmp.get(2), tmp.get(0), tmp.get(1), tmp.get(2));
		if (ol != 0) {
		    point_add(tmp.get(0), tmp.get(1), tmp.get(2), tmp.get(0), tmp.get(1), tmp.get(2),
			      table[ol].get(0), table[ol].get(1), table[ol].get(2));
		}
	    }
	    //memmove(out, tmp, sizeof(point));
	    out.copyFrom(tmp);
	}
	

	public static void ptwist_pointmul(byte out[], byte x[], byte seckey[]) {
		Point P = new Point(), Q = new Point();
		Coord z = new Coord();
		Coord r2 = new Coord();
		Coord Qx = new Coord();
		UnreducedCoord tmp = new UnreducedCoord();
		boolean ontwist;
		Coord three = new Coord(3, 0, 0);
		Coord b = new Coord(new BigInteger("46d320e01dc7d6", 16),
				new BigInteger("486ebc69bad316", 16), 
				new BigInteger("4e355e95cafedd", 16));
		
	    /* Convert the byte array to a coord */
	    bin21_to_felem(P.get(0), x);

	    /* Compute z = x^3 - 3*x + b */
	    felem_square(tmp, P.get(0)); felem_reduce(z, tmp);
	    felem_diff64(z, three);
	    felem_mul(tmp, z, P.get(0)); felem_reduce(z, tmp);
	    felem_sum64(z, b);

	    /*
	    dump_coord("z", z);
	    */
	    /* Compute r = P[1] = z ^ ((p+1)/4).  This will be a square root of
	     * z, if one exists. */
	    felem_sqrt(P.get(1), z);
	    /*
	    dump_coord("r", P[1]);
	    */

	    /* Is P[1] a square root of z? */
	    felem_square(tmp, P.get(1)); felem_diff_128_64(tmp, z); felem_reduce(r2, tmp);

	    if (felem_is_zero(r2)) {
		/* P(x,r) is on the curve */
		ontwist = false;
	    } else {
		/* (-x, r) is on the twist */
		ontwist = true;
		felem_neg(P.get(0), P.get(0));
	    }
	    /*
	    fprintf(stderr, "ontwist = %d\n", ontwist);
	    */
	    //memset(P[2], 0, sizeof(coord));
	    P.set(2, new Coord());
	    P.get(2).set(0, BigInteger.ONE);

	    /* All set.  Now do the point multiplication. */
	    /*
	    dump_point("P", P);
	    for(i=0;i<21;++i) {
		fprintf(stderr, "%02x", seckey[20-i]);
	    }
	    fprintf(stderr, "\n");
	    */
	    point_mul(Q, P, seckey);
	    affine_x(Qx, Q);
	    /*
	    dump_point("Q", Q);
	    */

	    /* Get the x-coordinate of the result, and negate it if we're on the
	     * twist. */
	    if (ontwist) {
		felem_neg(Qx, Qx);
	    }

	    /* Convert back to bytes */
	    felem_to_bin21(out, Qx);
	    /*
	    fprintf(stderr, "out: ");
	    for(i=0;i<21;++i) {
		fprintf(stderr, "%02x", out[i]);
	    }
	    fprintf(stderr, "\n");
	    */
		
	}
}
