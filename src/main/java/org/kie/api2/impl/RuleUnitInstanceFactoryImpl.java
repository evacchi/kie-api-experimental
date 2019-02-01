package org.kie.api2.impl;

import org.drools.core.impl.InternalKnowledgeBase;
import org.kie.api.KieBase;
import org.kie.api2.api.RuleUnit;
import org.kie.api2.api.RuleUnitInstanceFactory;
import org.kie.api2.api.Unit;
import org.kie.api2.api.UnitInstance;
import org.kie.api2.impl.RuleUnitInstanceImpl;

public class RuleUnitInstanceFactoryImpl implements RuleUnitInstanceFactory {

    private KieBase kBase;

    public RuleUnitInstanceFactoryImpl(KieBase kBase) {
        this.kBase = kBase;
    }

    @Override
    public <U extends Unit> UnitInstance<U> create(U unit) {
        RuleUnit ru = (RuleUnit) unit;
        return new RuleUnitInstanceImpl(ru, (InternalKnowledgeBase) kBase);
    }
}
