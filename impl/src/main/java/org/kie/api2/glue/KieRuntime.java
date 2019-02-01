package org.kie.api2.glue;

import org.kie.api2.api.Kie;
import org.kie.api2.impl.KieRuntimeImpl;

public final class KieRuntime {
    public static Kie.Runtime create() {
        return new KieRuntimeImpl();
    }
}
