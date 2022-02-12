package de.hfu.ep;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import org.apache.beam.runners.flink.FlinkRunner;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.kafka.KafkaIO;
import org.apache.beam.sdk.io.kafka.KafkaRecord;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.*;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.KV;
import org.apache.kafka.common.serialization.DoubleDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.joda.time.Duration;

import java.time.Instant;

public class FinanceBeamPipeline {

    public static class KafkaToKV extends DoFn<KafkaRecord<String, Double>, KV<String, Double>> {

        @ProcessElement
        public void processElement(@Element KafkaRecord<String, Double> input, OutputReceiver<KV<String, Double>> receiver) {
            receiver.output(input.getKV());
        }

    }

    public static class InfluxDBWrite extends DoFn<KV<String, Double>, KV<String, Double>> {

        private static final String TOKEN = "GQjCO1YRjF1fcL4YfCp9";
        private static final String BUCKET = "finance";
        private static final String ORG = "ep";

        private static final InfluxDBClient client = InfluxDBClientFactory.create("http://10.0.0.55:8086", TOKEN.toCharArray());


        @ProcessElement
        public void processElement(@Element KV<String, Double> input, OutputReceiver<KV<String, Double>> receiver) {

            String currency = input.getKey();
            Double value = input.getValue();

            Point dataPoint = Point
                    .measurement("finance-"+currency)
                    .addTag("currency", currency)
                    .addField("value", value)
                    .time(Instant.now(), WritePrecision.MS);

            client.getWriteApiBlocking().writePoint(BUCKET, ORG, dataPoint);

            receiver.output(input);
        }

    }

    public static void main(String[] args) {
        PipelineOptions options = PipelineOptionsFactory.create();
        options.setRunner(FlinkRunner.class);
        Pipeline pipeline = Pipeline.create(options);
        pipeline.apply(KafkaIO.<String, Double>read()
                .withKeyDeserializer(StringDeserializer.class)
                .withValueDeserializer(DoubleDeserializer.class)
                .withBootstrapServers("10.0.0.20:9092")
                .withTopic("finance"))
                .apply(ParDo.of(new KafkaToKV()))
                .apply(Window.into(FixedWindows.of(Duration.standardSeconds(5))))
                .apply(Mean.perKey())
                .apply(ParDo.of(new InfluxDBWrite()));
        pipeline.run().waitUntilFinish();
    }
}
