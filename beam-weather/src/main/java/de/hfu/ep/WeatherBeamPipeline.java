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
import org.apache.beam.sdk.transforms.Combine;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;

public class WeatherBeamPipeline {


    static class ConvertToPojo extends DoFn<KafkaRecord<String, String>, WeatherRecord> {

        @ProcessElement
        public void processElement(@Element KafkaRecord<String, String> input, OutputReceiver<WeatherRecord> outputReceiver) {
            String message = input.getKV().getValue();
            if (message == null || message.isEmpty()) {
                return;
            }
            String[] values = message.split("\\|");
            WeatherRecord weatherRecord = new WeatherRecord(
                    Double.parseDouble(values[0]),
                    Double.parseDouble(values[1]),
                    Double.parseDouble(values[2]),
                    values[3]);
            outputReceiver.output(weatherRecord);
        }

    }

    public static class AverageWeatherRecordFn extends Combine.CombineFn<WeatherRecord, Accum, WeatherRecord> {
        @Override
        public Accum createAccumulator() {
            return new Accum();
        }

        @Override
        public Accum addInput(Accum mutableAccumulator, WeatherRecord input) {
            if (input == null) {
                return mutableAccumulator;
            }
            mutableAccumulator.sumTemperature += input.getTemperature();
            mutableAccumulator.sumHumidity += input.getHumidity();
            mutableAccumulator.sumPressure += input.getPressure();
            mutableAccumulator.count++;
            return mutableAccumulator;
        }

        @Override
        public Accum mergeAccumulators(Iterable<Accum> accumulators) {
            Accum merged = createAccumulator();
            for (Accum accum : accumulators) {
                merged.sumTemperature += accum.sumTemperature;
                merged.sumHumidity += accum.sumHumidity;
                merged.sumPressure += accum.sumPressure;
                merged.count += accum.count;
            }
            return merged;
        }

        @Override
        public WeatherRecord extractOutput(Accum accumulator) {
            if (accumulator == null || accumulator.count == 0) {
                return new WeatherRecord();
            }
            WeatherRecord average = new WeatherRecord();
            average.setTemperature(accumulator.sumTemperature / accumulator.count);
            average.setHumidity(accumulator.sumHumidity / accumulator.count);
            average.setPressure(accumulator.sumPressure / accumulator.count);
            average.setTimestamp(LocalDateTime.now().toString());
            return average;
        }
    }

    public static class Accum implements Serializable {
        double sumTemperature = 0;
        double sumHumidity = 0;
        double sumPressure = 0;
        int count = 0;
    }

    public static class InfluxDBWrite extends DoFn<WeatherRecord, WeatherRecord> {

        private static final String TOKEN = "GQjCO1YRjF1fcL4YfCp9";
        private static final String BUCKET = "weather";
        private static final String ORG = "ep";

        private static final InfluxDBClient client = InfluxDBClientFactory.create("http://10.0.0.55:8086", TOKEN.toCharArray());


        @ProcessElement
        public void processElement(@Element WeatherRecord input, OutputReceiver<WeatherRecord> receiver) {

            Point dataPoint = Point
                    .measurement("Furtwangen")
                    .addTag("location", "Furtwangen")
                    .addField("temperature", input.getTemperature())
                    .addField("humidity", input.getHumidity())
                    .addField("pressure", input.getPressure())
                    .time(Instant.now(), WritePrecision.S);

            client.getWriteApiBlocking().writePoint(BUCKET, ORG, dataPoint);

            receiver.output(input);
        }

    }


    public static void main(String[] args) {
        PipelineOptions options = PipelineOptionsFactory.create();
        options.setRunner(FlinkRunner.class);
        Pipeline pipeline = Pipeline.create(options);
        pipeline
                .apply("Read", KafkaIO
                        .<String, String>read()
                        .withKeyDeserializer(StringDeserializer.class)
                        .withValueDeserializer(StringDeserializer.class)
                        .withBootstrapServers("10.0.0.20:9092")
                        .withTopic("weather"))
                .apply("ConvertToPojo", ParDo.of(new ConvertToPojo()))
                //.apply(Window.into(FixedWindows.of(Duration.standardSeconds(5))))
                //.apply(Combine.globally(new AverageWeatherRecordFn()).withoutDefaults())
                .apply(ParDo.of(new InfluxDBWrite()));
        pipeline.run().waitUntilFinish();
    }

}
