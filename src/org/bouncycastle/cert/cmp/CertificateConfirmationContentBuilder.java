package org.bouncycastle.cert.cmp;


import javaaddin.math.BigInteger;
import javaaddin.util.ArrayList;
import javaaddin.util.List;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.cmp.CertConfirmContent;
import org.bouncycastle.asn1.cmp.CertStatus;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.OperatorCreationException;

public class CertificateConfirmationContentBuilder
{
    private DigestAlgorithmIdentifierFinder digestAlgFinder;
    private List acceptedCerts = new ArrayList();
    private List acceptedReqIds = new ArrayList();

    public CertificateConfirmationContentBuilder()
    {
        this(new DefaultDigestAlgorithmIdentifierFinder());
    }

    public CertificateConfirmationContentBuilder(DigestAlgorithmIdentifierFinder digestAlgFinder)
    {
        this.digestAlgFinder = digestAlgFinder;
    }
    
    public CertificateConfirmationContentBuilder addAcceptedCertificate(X509CertificateHolder certHolder, BigInteger certReqID)
    {
        acceptedCerts.add(certHolder);
        acceptedReqIds.add(certReqID);

        return this;
    }

    public CertificateConfirmationContent build(DigestCalculatorProvider digesterProvider)
        throws CMPException
    {
        ASN1EncodableVector v = new ASN1EncodableVector();

        for (int i = 0; i != acceptedCerts.size(); i++)
        {
            X509CertificateHolder certHolder = (X509CertificateHolder)acceptedCerts.get(i);
            BigInteger reqID = (BigInteger)acceptedReqIds.get(i);

            AlgorithmIdentifier digAlg = digestAlgFinder.find(certHolder.toASN1Structure().getSignatureAlgorithm());
            if (digAlg == null)
            {
                throw new CMPException("cannot find algorithm for digest from signature");
            }

            DigestCalculator digester;

            try
            {
                digester = digesterProvider.get(digAlg);
            }
            catch (OperatorCreationException e)
            {
                throw new CMPException("unable to create digest: " + e.getMessage(), e);
            }

            CMPUtil.derEncodeToStream(certHolder.toASN1Structure(), digester.getOutputStream());

            v.add(new CertStatus(digester.getDigest(), reqID));
        }

        return new CertificateConfirmationContent(CertConfirmContent.getInstance(new DERSequence(v)), digestAlgFinder);
    }

}
