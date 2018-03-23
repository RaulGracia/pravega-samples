/*
 * Copyright (c) 2018 Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 */
package io.pravega.examples.flink.alert;

import io.pravega.connectors.flink.FlinkPravegaReader;
import io.pravega.connectors.flink.util.FlinkPravegaParams;
import io.pravega.connectors.flink.util.StreamId;
import io.pravega.shaded.com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * This application has the following input parameters
 *     stream - Pravega stream name to write to
 *     controller - the Pravega controller URI, e.g., tcp://localhost:9090
 *                  Note that this parameter is processed in pravega flink connector
 */
public class CEPAlerter {

    // Logger initialization
    private static final Logger LOG = LoggerFactory.getLogger(CEPAlerter.class);

    // The application reads data from specified Pravega stream and once every 10 seconds
    // prints the distinct words and counts from the previous 10 seconds.

    // Application parameters
    //   stream - default myscope/wordcount
    //   controller - default tcp://127.0.0.1:9090. It is processed by FlinkPravegaParams

    public static void main(String[] args) throws Exception {
        LOG.info("Starting CEPAlerter...");

        // initialize the parameter utility tool in order to retrieve input parameters
        ParameterTool params = ParameterTool.fromArgs(args);

        // create pravega helper utility for Flink using the input paramaters
        FlinkPravegaParams helper = new FlinkPravegaParams(params);

        // get the Pravega stream from the input parameters
        StreamId streamId = helper.getStreamFromParam(Constants.STREAM_PARAM,
                                                      Constants.DEFAULT_STREAM);

        // create the Pravega stream is not exists.
        helper.createStream(streamId);

        // initialize Flink execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // create the Pravega stream reader
        long startTime = 0;
        FlinkPravegaReader<String> reader = helper.newReader(streamId, startTime, String.class);

        // If needed - add the below for example on creating checkpoint
        // long checkpointInterval = appConfiguration.getPipeline().getCheckpointIntervalInMilliSec();
        // env.enableCheckpointing(checkpointInterval, CheckpointingMode.EXACTLY_ONCE);
        //

        // add the Pravega reader as the data source
        DataStream<String> inputStream = env.addSource(reader);

        // create an output sink to print to stdout for verification
        inputStream.print();

        DataStream<AccessLog> dataStream = inputStream.map(new ParseLogData());

        dataStream.print();

        // execute within the Flink environment
        env.execute("CEPAlerter");

        LOG.info("Ending CEPAlerter...");
    }

    //Parse the incoming streams & convert into Java PoJos
    private static class ParseLogData implements MapFunction<String, AccessLog>{
        public AccessLog map(String record) throws Exception {
            Gson gson = new Gson();
            AccessLog accessLog = new AccessLog();
            JsonParser parser = new JsonParser();
            JsonObject obj = parser.parse(record).getAsJsonObject();
            if (obj.has("verb")) {
                String verb = obj.get("verb").getAsString();
                accessLog.setVerb(verb);
            }
            if (obj.has("status")) {
                String status = obj.get("status").getAsString();
                accessLog.setStatus(status);
            }
            return accessLog;
        }
    }

}
