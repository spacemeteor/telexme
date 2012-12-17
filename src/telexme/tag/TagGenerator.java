package telexme.tag;

import java.io.IOException;
import java.io.InputStream;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;

import org.bouncycastle.crypto.digests.SHA256Digest;

import javaaddin.security.SecureRandom;

public class TagGenerator {
	
	public static final int MAX_CONTEXT_LEN = 9;
	
	private static byte[] maingen = new byte[PTwist.PTWIST_BYTES];
	private static byte[] twistgen = new byte[PTwist.PTWIST_BYTES];
	private static byte[] mainpub = new byte[PTwist.PTWIST_BYTES];
	private static byte[] twistpub = new byte[PTwist.PTWIST_BYTES];
	
	public static byte[] temptag = new byte[28];
	public static byte[] tempkey = new byte[16];
	
	static {
		for (int i = 0; i < PTwist.PTWIST_BYTES; i++) {
			maingen[i] = 0;
			twistgen[i] = 0;
		}
		maingen[0] = 2;
		
		tag_load_pubkey();
	}
	
	private static void tag_load_pubkey() {
		try {
			FileConnection fc = (FileConnection) Connector.open("file:///root1/pubkey");
			InputStream is = fc.openInputStream();
			is.read(mainpub, 0, PTwist.PTWIST_BYTES);
			is.read(twistpub, 0, PTwist.PTWIST_BYTES);
//			for (int i = 0; i < mainpub.length; i++) {
//				System.out.print(" "+mainpub[i]);
//			}
//			System.out.println();
//			for (int i = 0; i < mainpub.length; i++) {
//				System.out.print(" "+twistpub[i]);
//			}
//			System.out.println();
			fc.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void gen_tag(byte[] tag, byte[] key, byte[] context) {
		byte[] seckey = new byte[PTwist.PTWIST_BYTES];
		byte[] sharedsec = new byte[PTwist.PTWIST_BYTES+MAX_CONTEXT_LEN];
		byte usetwist;
		byte[] taghashout = new byte[32];
		
		// assert(context_len <= MAX_CONTEXT_LEN);
		
		for (int i = 0; i < tag.length; i++) {
			tag[i] = (byte) 0xAA;
		}
		
		SecureRandom srng = new SecureRandom();
		
	    /* Use the main or the twist curve? */
		usetwist = (byte) srng.nextInt();
		usetwist &= 1;
		
	    /* Create seckey*G and seckey*Y */
		srng.nextBytes(seckey);
		PTwist.ptwist_pointmul(tag, (usetwist != 0) ? twistgen : maingen, seckey);
		PTwist.ptwist_pointmul(sharedsec, (usetwist != 0) ? twistpub : mainpub, seckey);
		
	    /* Create the tag hash keys */
		System.arraycopy(context, 0, sharedsec, PTwist.PTWIST_BYTES, context.length);
		SHA256Digest s256d = new SHA256Digest();
		byte[] shaplain = new byte[PTwist.PTWIST_BYTES+context.length];
		System.arraycopy(sharedsec, 0, shaplain, 0, PTwist.PTWIST_BYTES+context.length);
		s256d.update(shaplain, 0, shaplain.length);
		s256d.doFinal(taghashout, 0);
		System.arraycopy(taghashout, 0, tag, PTwist.PTWIST_BYTES, 7);
		System.arraycopy(taghashout, 16, key, 0, 16);
	}
}
