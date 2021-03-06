package org.bouncycastle.asn1.x509;

import javaaddin.math.BigInteger;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.DERBoolean;
import org.bouncycastle.asn1.DERSequence;

public class BasicConstraints
    extends ASN1Object
{
    DERBoolean  cA = new DERBoolean(false);
    ASN1Integer  pathLenConstraint = null;

    public static BasicConstraints getInstance(
        ASN1TaggedObject obj,
        boolean          explicit)
    {
        return getInstance(ASN1Sequence.getInstance(obj, explicit));
    }

    public static BasicConstraints getInstance(
        Object  obj)
    {
        if (obj instanceof BasicConstraints)
        {
            return (BasicConstraints)obj;
        }
        if (obj instanceof X509Extension)
        {
            return getInstance(X509Extension.convertValueToObject((X509Extension)obj));
        }
        if (obj != null)
        {
            return new BasicConstraints(ASN1Sequence.getInstance(obj));
        }

        return null;
    }
    
    private BasicConstraints(
        ASN1Sequence   seq)
    {
        if (seq.size() == 0)
        {
            this.cA = null;
            this.pathLenConstraint = null;
        }
        else
        {
            if (seq.getObjectAt(0) instanceof DERBoolean)
            {
                this.cA = DERBoolean.getInstance(seq.getObjectAt(0));
            }
            else
            {
                this.cA = null;
                this.pathLenConstraint = ASN1Integer.getInstance(seq.getObjectAt(0));
            }
            if (seq.size() > 1)
            {
                if (this.cA != null)
                {
                    this.pathLenConstraint = ASN1Integer.getInstance(seq.getObjectAt(1));
                }
                else
                {
                    throw new IllegalArgumentException("wrong sequence in constructor");
                }
            }
        }
    }

    public BasicConstraints(
        boolean cA)
    {
        if (cA)
        {
            this.cA = new DERBoolean(true);
        }
        else
        {
            this.cA = null;
        }
        this.pathLenConstraint = null;
    }

    /**
     * create a cA=true object for the given path length constraint.
     * 
     * @param pathLenConstraint
     */
    public BasicConstraints(
        int     pathLenConstraint)
    {
        this.cA = new DERBoolean(true);
        this.pathLenConstraint = new ASN1Integer(pathLenConstraint);
    }

    public boolean isCA()
    {
        return (cA != null) && cA.isTrue();
    }

    public BigInteger getPathLenConstraint()
    {
        if (pathLenConstraint != null)
        {
            return pathLenConstraint.getValue();
        }

        return null;
    }

    /**
     * Produce an object suitable for an ASN1OutputStream.
     * <pre>
     * BasicConstraints := SEQUENCE {
     *    cA                  BOOLEAN DEFAULT FALSE,
     *    pathLenConstraint   INTEGER (0..MAX) OPTIONAL
     * }
     * </pre>
     */
    public ASN1Primitive toASN1Primitive()
    {
        ASN1EncodableVector  v = new ASN1EncodableVector();

        if (cA != null)
        {
            v.add(cA);
        }

        if (pathLenConstraint != null)  // yes some people actually do this when cA is false...
        {
            v.add(pathLenConstraint);
        }

        return new DERSequence(v);
    }

    public String toString()
    {
        if (pathLenConstraint == null)
        {
            if (cA == null)
            {
                return "BasicConstraints: isCa(false)";
            }
            return "BasicConstraints: isCa(" + this.isCA() + ")";
        }
        return "BasicConstraints: isCa(" + this.isCA() + "), pathLenConstraint = " + pathLenConstraint.getValue();
    }
}
