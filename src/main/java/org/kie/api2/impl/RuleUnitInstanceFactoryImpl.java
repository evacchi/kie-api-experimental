package org.kie.api2.impl;

import org.kie.api2.api.Kie;
import org.kie.api2.api.RuleUnit;
import org.kie.api2.api.RuleUnitInstanceFactory;
import org.kie.api2.api.Unit;
import org.kie.api2.api.UnitInstance;

public class RuleUnitInstanceFactoryImpl implements RuleUnitInstanceFactory {

    private Kie.Runtime runtime;

    public RuleUnitInstanceFactoryImpl(Kie.Runtime runtime) {
        this.runtime = runtime;
    }

    @Override
    public <U extends Unit> UnitInstance<U> create(U unit) {
        RuleUnit ru = (RuleUnit) unit;
        return new RuleUnitInstanceImpl(ru, runtime);
    }
}
