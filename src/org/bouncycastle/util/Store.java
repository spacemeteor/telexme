package org.bouncycastle.util;

import javaaddin.util.Collection;

public interface Store
{
    Collection getMatches(Selector selector)
        throws StoreException;
}
