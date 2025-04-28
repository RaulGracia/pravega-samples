package io.pravega.example.flink.wordcount;

import io.pravega.connectors.flink.FlinkPravegaReader;
import io.pravega.connectors.flink.PravegaConfig;
import io.pravega.connectors.flink.serialization.JsonSerializer;
import io.pravega.connectors.flink.serialization.PravegaDeserializationSchema;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.net.URI;
import java.util.Objects;

public class FastqRecordReaderJob {

    static final Logger log = LoggerFactory.getLogger(FastqRecordReaderJob.class);

    public static void main(String[] args) throws Exception {

        final String pravegaControllerUri = "tcp://localhost:9090"; // Update as needed
        final String pravegaScope = "scope-fastq";
        final String pravegaStream = "stream";

        // Set up the Flink execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Create Pravega config
        PravegaConfig pravegaConfig = PravegaConfig.fromDefaults()
                .withControllerURI(URI.create(pravegaControllerUri))
                .withDefaultScope(pravegaScope);

        // Create Pravega reader
        FlinkPravegaReader<FastqRecord> reader = FlinkPravegaReader.<FastqRecord>builder()
                .withPravegaConfig(pravegaConfig)
                .forStream(pravegaStream)
                .withDeserializationSchema(new PravegaDeserializationSchema<>(FastqRecord.class, new JsonSerializer<>(FastqRecord.class)))
                .build();

        // Read from Pravega
        DataStream<FastqRecord> records = env.addSource(reader);

        // Print the records
        records.print();

        // Execute the job
        env.execute("FastqRecord Reader from Pravega");
    }

    // FastqRecord class
    public static class FastqRecord implements Serializable {

        private static final long serialVersionUID = 1L;

        private long id;
        private String header;
        private String sequence;
        private String optionalHeader;
        private String quality;

        public FastqRecord() {
            // Required for Flink POJO serialization
        }

        // Getters and setters (important for Flink POJO recognition)
        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getHeader() {
            return header;
        }

        public void setHeader(String header) {
            this.header = header;
        }

        public String getSequence() {
            return sequence;
        }

        public void setSequence(String sequence) {
            this.sequence = sequence;
        }

        public String getOptionalHeader() {
            return optionalHeader;
        }

        public void setOptionalHeader(String optionalHeader) {
            this.optionalHeader = optionalHeader;
        }

        public String getQuality() {
            return quality;
        }

        public void setQuality(String quality) {
            this.quality = quality;
        }

        @Override
        public String toString() {
            return "FastqRecord{" +
                    "id=" + id +
                    ", header='" + header + '\'' +
                    ", sequence='" + sequence + '\'' +
                    ", optionalHeader='" + optionalHeader + '\'' +
                    ", quality='" + quality + '\'' +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FastqRecord that = (FastqRecord) o;
            return id == that.id &&
                    Objects.equals(header, that.header) &&
                    Objects.equals(sequence, that.sequence) &&
                    Objects.equals(optionalHeader, that.optionalHeader) &&
                    Objects.equals(quality, that.quality);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, header, sequence, optionalHeader, quality);
        }
    }
}