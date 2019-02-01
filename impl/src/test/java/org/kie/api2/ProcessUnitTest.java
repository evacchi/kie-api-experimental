package org.kie.api2;

import org.junit.Test;
import org.kie.api2.api.ProcessUnit;
import org.kie.api2.api.ProcessUnitInstance;
import org.kie.api2.impl.KieRuntimeImpl;

import static org.junit.Assert.assertEquals;

public class ProcessUnitTest {

    @Test
    public void simpleProcess() {
        // example usage of a ProcessUnit
        // notice how the API is type-safe in the type of the Unit, returning
        // a ProcessUnitInstance because MyProcessUnit implements ProcessUnit
        MyProcessUnit unit = new MyProcessUnit();

        // result field in unit defaults to null
        ProcessUnitInstance<MyProcessUnit> instance = new KieRuntimeImpl().factory().of(unit);
        instance.run();
        // after running, the field equals to Hello World
        assertEquals("Hello World", unit.result);
    }

    @Test
    public void ruleUnitProcess() {
        BusinessRuleProcessUnit u = new BusinessRuleProcessUnit();
        ProcessUnitInstance<BusinessRuleProcessUnit> p = new KieRuntimeImpl().factory().of(u);
        p.run();
    }
}

class BusinessRuleProcessUnit implements ProcessUnit {

}

class MyProcessUnit implements ProcessUnit {
    String result = null;
}
