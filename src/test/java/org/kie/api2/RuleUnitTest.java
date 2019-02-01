package org.kie.api2;

import org.junit.Test;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api2.api.DataSource;
import org.kie.api2.api.Kie;
import org.kie.api2.api.RuleUnitInstance;
import org.kie.api2.impl.DataSourceImpl;
import org.kie.api2.model.Person;
import org.kie.api2.model.Result;

import static org.junit.Assert.assertSame;

public class RuleUnitTest {

    @Test
    public void testRuleUnit() {
        DataSource<Person> ps = new DataSourceImpl<>();

        Person mark = new Person("Mark", 37);
        Person edson = new Person("Edson", 35);
        Person mario = new Person("Mario", 40);

        FactHandle markFH = ps.add(mark);
        FactHandle edsonFH = ps.add(edson);
        FactHandle marioFH = ps.add(mario);

        Kie.Runtime.Factory runtime = Kie.runtime();
        // create a RuleUnit instance.
        // Notice that the API is type safe, it the sub-type of PersonUnit (which implements RuleUnit)
        // in fact, it returns a RuleUnitInstance!
        RuleUnitInstance<PersonUnit> rui = runtime.of(new PersonUnit(ps));
        // start the unit
        rui.run();

        assertSame(mark, Result.value);
    }
}

