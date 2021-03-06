package org.bouncycastle.openpgp.operator.bc;

import javaaddin.security.SecureRandom;

import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.operator.PBESecretKeyEncryptor;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;

public class BcPBESecretKeyEncryptorBuilder
{
    private int encAlgorithm;
    private PGPDigestCalculator s2kDigestCalculator;
    private SecureRandom random;

    public BcPBESecretKeyEncryptorBuilder(int encAlgorithm)
    {
        this(encAlgorithm, new SHA1PGPDigestCalculator());
    }

    public BcPBESecretKeyEncryptorBuilder(int encAlgorithm, PGPDigestCalculator s2kDigestCalculator)
    {
        this.encAlgorithm = encAlgorithm;
        this.s2kDigestCalculator = s2kDigestCalculator;
    }

    /**
     * Provide a user defined source of randomness.
     *
     * @param random  the secure random to be used.
     * @return  the current builder.
     */
    public BcPBESecretKeyEncryptorBuilder setSecureRandom(SecureRandom random)
    {
        this.random = random;

        return this;
    }

    public PBESecretKeyEncryptor build(char[] passPhrase)
    {
        if (this.random == null)
        {
            this.random = new SecureRandom();
        }

        return new PBESecretKeyEncryptor(encAlgorithm, s2kDigestCalculator, this.random, passPhrase)
        {
            private byte[] iv;

            public byte[] encryptKeyData(byte[] key, byte[] keyData, int keyOff, int keyLen)
                throws PGPException
            {
                try
                {
                    if (this.random == null)
                    {
                        this.random = new SecureRandom();
                    }

                    BlockCipher engine = BcImplProvider.createBlockCipher(this.encAlgorithm);

                    iv = new byte[engine.getBlockSize()];

                    this.random.nextBytes(iv);

                    BufferedBlockCipher c = BcUtil.createSymmetricKeyWrapper(true, engine, key, iv);

                    byte[] out = new byte[keyLen];
                    int    outLen = c.processBytes(keyData, keyOff, keyLen, out, 0);

                    outLen += c.doFinal(out, outLen);

                    return out;
                }
                catch (InvalidCipherTextException e)
                {
                    throw new PGPException("decryption failed: " + e.getMessage(), e);
                }
            }

            public byte[] getCipherIV()
            {
                return iv;
            }
        };
    }
}
