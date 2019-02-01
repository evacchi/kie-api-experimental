package org.kie.api2.impl;

import java.util.Map;

import org.drools.beliefs.bayes.JunctionTree;
import org.drools.beliefs.bayes.assembler.BayesPackage;
import org.drools.core.definitions.InternalKnowledgePackage;
import org.kie.api.KieBase;
import org.kie.api.internal.io.ResourceTypePackage;
import org.kie.api.io.ResourceType;
import org.kie.api2.api.BayesUnit;
import org.kie.api2.api.BayesUnitInstanceFactory;
import org.kie.api2.api.Unit;
import org.kie.api2.api.UnitInstance;

public class BayesUnitInstanceFactoryImpl implements BayesUnitInstanceFactory {

    private KieBase kBase;

    public BayesUnitInstanceFactoryImpl(KieBase kBase) {
        this.kBase = kBase;
    }

    @Override
    public <U extends Unit> UnitInstance<U> create(U unit) {
        BayesUnit bu = (BayesUnit) unit;
        Class<? extends BayesUnit> cls = bu.getClass();
        InternalKnowledgePackage kpkg = (InternalKnowledgePackage) kBase.getKiePackage(cls.getPackage().getName());
        Map<ResourceType, ResourceTypePackage> map = kpkg.getResourceTypePackages();
        BayesPackage bayesPkg = (BayesPackage) map.get(ResourceType.BAYES);
        JunctionTree jtree = bayesPkg.getJunctionTree(cls.getSimpleName());

        return new BayesUnitInstanceImpl((BayesUnit) unit, jtree);
    }
}
