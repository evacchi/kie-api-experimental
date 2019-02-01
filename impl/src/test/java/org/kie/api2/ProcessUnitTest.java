package org.kie.api2;

import java.util.ArrayList;
import java.util.List;

import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.ruleflow.core.RuleFlowProcessFactory;
import org.junit.Ignore;
import org.junit.Test;
import org.kie.api2.api.DataSource;
import org.kie.api2.api.Kie;
import org.kie.api2.api.ProcessUnit;
import org.kie.api2.api.ProcessUnitInstance;
import org.kie.api2.api.RuleUnitInstance;
import org.kie.api2.glue.KieRuntime;
import org.kie.api2.impl.DataSourceImpl;
import org.kie.api2.impl.KieRuntimeImpl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        DataSource<String> strings = new DataSourceImpl<>();
        strings.add("abc");
        ArrayList<String> list = new ArrayList<>();
        BusinessRuleProcessUnit u = new BusinessRuleProcessUnit(strings, list);

        Kie.Runtime rt = KieRuntime.create();
        Kie.Runtime.Factory f = rt.factory();

        ProcessUnitInstance<BusinessRuleProcessUnit> p = f.of(u);
        p.run();

        assertFalse(list.isEmpty());
        assertTrue(list.contains("GOT: abc"));
    }

    @Test @Ignore("action is null")
    public void fluentProcess() {
        RuleFlowProcessFactory factory =
                RuleFlowProcessFactory.createProcess("org.kie.api2.MyProcessUnit");
        factory
                // Header
                .name("HelloWorldProcess")
                .version("1.0")
                .packageName("org.jbpm")
                // Nodes
                .startNode(1).name("Start").done()
                .actionNode(2).name("Action")
                .action("java", "System.out.println(\"Hello World1\");").done()
                .endNode(3).name("End").done()
                // Connections
                .connection(1, 2)
                .connection(2, 3);
        RuleFlowProcess process = factory.validate().getProcess();

        MyProcessUnit unit = new MyProcessUnit();

        // result field in unit defaults to null
        Kie.Runtime runtime = KieRuntime.create();
        runtime.kieBase().addProcess(process);
        ProcessUnitInstance<MyProcessUnit> instance = runtime.factory().of(unit);
        instance.run();

    }
}

