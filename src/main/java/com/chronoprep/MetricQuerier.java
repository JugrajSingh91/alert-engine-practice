package com.chronoprep;

/**
 * Runs the alert's query against the metrics backend.
 * In the interview this is provided; here you get the interface.
 */
public interface MetricQuerier {
    /**
     * @return the current value of the metric for the given query
     * @throws Exception if the query fails (your engine must decide what to do)
     */
    double executeQuery(String query) throws Exception;
}
