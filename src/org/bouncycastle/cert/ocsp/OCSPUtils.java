package org.bouncycastle.cert.ocsp;

import java.util.Date;

import javaaddin.util.ArrayList;
import javaaddin.util.Arrays;
import javaaddin.util.Collections;
import javaaddin.util.HashSet;
import javaaddin.util.List;
import javaaddin.util.Set;

import org.bouncycastle.asn1.DERGeneralizedTime;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.cert.X509CertificateHolder;

class OCSPUtils
{
    static final X509CertificateHolder[] EMPTY_CERTS = new X509CertificateHolder[0];

    static Set EMPTY_SET = Collections.unmodifiableSet(new HashSet());
    static List EMPTY_LIST = Collections.unmodifiableList(new ArrayList());

    static Date extractDate(DERGeneralizedTime time)
    {
        try
        {
            return time.getDate();
        }
        catch (Exception e)
        {
            throw new IllegalStateException("exception processing GeneralizedTime: " + e.getMessage());
        }
    }

    static Set getCriticalExtensionOIDs(Extensions extensions)
    {
        if (extensions == null)
        {
            return EMPTY_SET;
        }

        return Collections.unmodifiableSet(new HashSet(Arrays.asList(extensions.getCriticalExtensionOIDs())));
    }

    static Set getNonCriticalExtensionOIDs(Extensions extensions)
    {
        if (extensions == null)
        {
            return EMPTY_SET;
        }

        // TODO: should probably produce a set that imposes correct ordering
        return Collections.unmodifiableSet(new HashSet(Arrays.asList(extensions.getNonCriticalExtensionOIDs())));
    }

    static List getExtensionOIDs(Extensions extensions)
    {
        if (extensions == null)
        {
            return EMPTY_LIST;
        }

        return Collections.unmodifiableList(Arrays.asList(extensions.getExtensionOIDs()));
    }
}
