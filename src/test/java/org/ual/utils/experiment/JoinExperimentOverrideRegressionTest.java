package org.ual.utils.experiment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JoinExperimentOverrideRegressionTest {

    @Test
    void getOverrideNumberOfQueries_fallsBackToLegacyNumberOfQueries() {
        JoinExperiment join = new JoinExperiment();
        join.setNumberOfQueries(7);

        assertEquals(Integer.valueOf(7), join.getOverrideNumberOfQueries());
    }

    @Test
    void explicitOverrideNumberOfQueries_takesPrecedence() {
        JoinExperiment join = new JoinExperiment();
        join.setNumberOfQueries(7);
        join.setOverrideNumberOfQueries(3);

        assertEquals(Integer.valueOf(3), join.getOverrideNumberOfQueries());
    }

    @Test
    void setFixedAlpha_storesConfiguredValue() {
        JoinExperiment join = new JoinExperiment();

        join.setFixedAlpha(0.6);

        assertEquals(Double.valueOf(0.6), join.getFixedAlpha());
    }

    @Test
    void getOverrideNumberOfQueries_returnsNullWhenUnsetAndLegacyMissing() {
        JoinExperiment join = new JoinExperiment();

        assertNull(join.getOverrideNumberOfQueries());
    }
}

