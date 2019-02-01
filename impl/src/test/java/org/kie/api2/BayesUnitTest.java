package org.kie.api2;

import org.drools.beliefs.bayes.VarName;
import org.junit.Test;
import org.kie.api2.api.BayesUnit;
import org.kie.api2.api.BayesUnitInstance;
import org.kie.api2.glue.KieRuntime;

import static org.junit.Assert.assertTrue;

public class BayesUnitTest {

    @Test
    public void testBayesRuntimeManager() throws Exception {
        Garden garden = new Garden();
        BayesUnitInstance<Garden> instance = KieRuntime.create().factory().of(garden);
        instance.run();
        assertTrue(garden.isWetGrass());
    }
}

class Garden implements BayesUnit {

    @VarName("WetGrass")
    private boolean wetGrass;

    @VarName("Cloudy")
    private boolean cloudy;

    @VarName("Sprinkler")
    private boolean sprinkler;

    @VarName("Rain")
    private boolean rain;

    public Garden() {

    }

    public void update(@VarName("WetGrass") boolean wetGrass, @VarName("Cloudy") boolean cloudy,
                       @VarName("Sprinkler") boolean sprinkler, @VarName("Rain") boolean rain) {
        this.wetGrass = wetGrass;
        this.cloudy = cloudy;
        this.sprinkler = sprinkler;
        this.rain = rain;
    }

    @Override
    public String toString() {
        return "Garden{" +
                "wetGrass=" + wetGrass +
                ", cloudy=" + cloudy +
                ", sprinkler=" + sprinkler +
                ", rain=" + rain +
                '}';
    }

    public boolean isWetGrass() {
        return wetGrass;
    }

    public boolean isCloudy() {
        return cloudy;
    }

    public boolean isSprinkler() {
        return sprinkler;
    }

    public boolean isRain() {
        return rain;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Garden garden = (Garden) o;

        if (cloudy != garden.cloudy) {
            return false;
        }
        if (rain != garden.rain) {
            return false;
        }
        if (sprinkler != garden.sprinkler) {
            return false;
        }
        if (wetGrass != garden.wetGrass) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = (wetGrass ? 1 : 0);
        result = 31 * result + (cloudy ? 1 : 0);
        result = 31 * result + (sprinkler ? 1 : 0);
        result = 31 * result + (rain ? 1 : 0);
        return result;
    }
}