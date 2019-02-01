package org.kie.api2.impl;

import org.kie.api.KieBase;
import org.kie.api2.api.Kie;
import org.kie.api2.api.ProcessUnit;
import org.kie.api2.api.ProcessUnitInstanceFactory;
import org.kie.api2.api.Unit;
import org.kie.api2.api.UnitInstance;

public class ProcessUnitInstanceFactoryImpl implements ProcessUnitInstanceFactory {

    private Kie.Runtime runtime;

    public ProcessUnitInstanceFactoryImpl(Kie.Runtime runtime) {
        this.runtime = runtime;
    }

    @Override
    public <U extends Unit> UnitInstance<U> create(U unit) {
        ProcessUnit pu = (ProcessUnit) unit;
        return new ProcessUnitInstanceImpl(pu, runtime);
    }
}
