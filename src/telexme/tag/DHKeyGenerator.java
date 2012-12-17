package telexme.tag;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;

import javaaddin.math.BigInteger;

public class DHKeyGenerator {
	private static final int SHA256_DIGEST_LENGTH = 32;

	public static byte[] telex_ssl_get_dh_key(byte[] state_secret) {
		String uniqstr = "Telex PRG";
		byte[] uniq = uniqstr.getBytes();
		byte[] buf = new byte[128];
		byte[] out = new byte[SHA256_DIGEST_LENGTH];
		byte[] in = new byte[128]; // > SHA256_DIGEST_LENTH + strlen(uniq) + sizeof(int)
		int out_len, in_len;
		
	    // buf will end up with
	    // x_{1...4}
	    // x_(i+1) = HMAC{state_secret}(x_i | uniq | i)
	    // uniq = "Telex PRG"
	    // x_0 = empty string 

		for (int i = 0; i < out.length; i++) {
			out[i] = 0;
		}
		out_len = 0;          // x_0 = ""
		for (int i = 0; i < 4; i++) {
	        // Load the input for the hmac: x_i | uniq | i
			in_len = 0;
			System.arraycopy(out, 0, in, in_len, out_len);
			in_len += out_len;
			
			System.arraycopy(uniq, 0, in, in_len, uniq.length);
			in_len += uniq.length;
			
			in[in_len] = (byte) i;
			in[in_len+1] = 0;
			in[in_len+2] = 0;
			in[in_len+3] = 0;
			in_len += 4;
			
			HMac hmac = new HMac(new SHA256Digest());
			hmac.init(new KeyParameter(state_secret));
			hmac.update(in, 0, in_len);
			hmac.doFinal(out, 0);
			
			System.arraycopy(out, 0, buf, i*SHA256_DIGEST_LENGTH, SHA256_DIGEST_LENGTH);
		}
		
		// big endian, make sure buf[0] == 01xxxxxx
		buf[0] |= (1<<6);
		buf[0] &= 0x7f;
		
		return buf;
	}
}
