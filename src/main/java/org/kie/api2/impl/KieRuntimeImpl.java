package org.kie.api2.impl;

import org.drools.core.impl.InternalKnowledgeBase;
import org.kie.api.KieServices;
import org.kie.api2.api.Kie;

public class KieRuntimeImpl implements Kie.Runtime {

    private final InternalKnowledgeBase kieBase;

    public KieRuntimeImpl() {
        this.kieBase = (InternalKnowledgeBase) KieServices.Factory.get().newKieClasspathContainer().getKieBase();
    }

    @Override
    public InternalKnowledgeBase kieBase() {
        return kieBase;
    }
}
