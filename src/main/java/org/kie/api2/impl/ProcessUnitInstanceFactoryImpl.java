package org.kie.api2.impl;

import org.drools.core.impl.InternalKnowledgeBase;
import org.kie.api.KieBase;
import org.kie.api2.api.ProcessUnit;
import org.kie.api2.api.ProcessUnitInstanceFactory;
import org.kie.api2.api.Unit;
import org.kie.api2.api.UnitInstance;

public class ProcessUnitInstanceFactoryImpl implements ProcessUnitInstanceFactory {

    private KieBase kBase;

    public ProcessUnitInstanceFactoryImpl(KieBase kBase) {
        this.kBase = kBase;
    }

    @Override
    public <U extends Unit> UnitInstance<U> create(U unit) {
        ProcessUnit pu = (ProcessUnit) unit;
        return new ProcessUnitInstanceImpl(pu, (InternalKnowledgeBase) kBase);
    }
}
