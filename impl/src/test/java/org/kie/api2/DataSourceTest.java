package org.kie.api2;

import org.junit.Test;
import org.kie.api2.api.DataSource;
import org.kie.api2.api.RuleUnit;
import org.kie.api2.impl.DataSourceImpl;

public class DataSourceTest {

    @Test
    public void testDs() {
        // example usage of a data source
        DataSource<String> ds = new DataSourceImpl<>();
        ds.add("foo");
        ds.add("bar");
        ds.add("baz");
    }
}

class MyUnit implements RuleUnit {

    private DataSource<String> strings;

    public MyUnit(DataSource<String> strings) {
        this.strings = strings;
    }
}
