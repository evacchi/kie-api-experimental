package org.kie.api2;

import java.util.List;

import org.kie.api2.api.DataSource;
import org.kie.api2.api.ProcessUnit;

public class BusinessRuleProcessUnit implements ProcessUnit {

    private DataSource<String> strings;
    private final List<String> list;

    BusinessRuleProcessUnit(DataSource<String> strings, List<String> list) {
        this.strings = strings;
        this.list = list;
    }

    public DataSource<String> list() {
        return strings;
    }
}
