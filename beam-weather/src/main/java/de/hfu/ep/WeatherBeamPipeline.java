package de.hfu.ep;

import org.apache.beam.runners.flink.FlinkRunner;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.jdbc.JdbcIO;
import org.apache.beam.sdk.io.kafka.KafkaIO;
import org.apache.beam.sdk.io.kafka.KafkaRecord;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.Combine;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.joda.time.Duration;

import java.io.Serializable;
import java.sql.Timestamp;
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
                    Double.parseDouble(values[1]),
                    Double.parseDouble(values[2]),
                    Double.parseDouble(values[3]),
                    values[4]);
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
        double  sumPressure = 0;
        int count = 0;
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
                .apply(Window.<WeatherRecord>into(FixedWindows.of(Duration.standardSeconds(5))))
                .apply(Combine.globally(new AverageWeatherRecordFn()).withoutDefaults())
                .apply(JdbcIO.<WeatherRecord>write()
                        .withDataSourceConfiguration(JdbcIO.DataSourceConfiguration.create(
                                "com.mysql.jdbc.Driver", "jdbc:mysql://10.0.0.52/testapp")
                                .withUsername("root")
                                .withPassword("example")
                        )
                        .withStatement("INSERT INTO `weather` (`temperature`, `humidity`, `pressure`, `created_time`) " +
                                "VALUES (?, ?, ?, ?)")
                        .withPreparedStatementSetter(
                                (JdbcIO.PreparedStatementSetter<WeatherRecord>) (element, preparedStatement) -> {
                                    preparedStatement.setDouble(1, element.getTemperature());
                                    preparedStatement.setDouble(2, element.getHumidity());
                                    preparedStatement.setDouble(3, element.getPressure());
                                    preparedStatement.setTimestamp(4, Timestamp.valueOf(LocalDateTime.parse(element.getTimestamp())));
                                    System.out.println("setting... " + element);
                                }));
        pipeline.run().waitUntilFinish();
    }

}
