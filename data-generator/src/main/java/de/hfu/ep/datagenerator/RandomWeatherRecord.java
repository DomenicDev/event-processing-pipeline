package de.hfu.ep.datagenerator;

import java.util.Random;

public class RandomWeatherRecord {

    private final String sensorName;
    private double temperature;
    private double humidity;
    private double pressure;

    private final Random random = new Random();

    private int extraMultiplier = 1;

    public RandomWeatherRecord(String sensorName) {
        this.sensorName = sensorName;
        this.temperature = 20;
        this.humidity = 70;
        this.pressure = 80;
    }

    public void nextRandomChange() {
        this.temperature += nextRandom(3);
        this.humidity += nextRandom(5);
        this.pressure += nextRandom(80);
    }

    private double nextRandom(int multiplier) {
        double rand = random.nextDouble();
        if (rand > 0.5) {
            return (rand - 0.5) * multiplier * extraMultiplier;
        } else {
            return rand * multiplier * extraMultiplier * -1;
        }
    }


    public void setExtraMultiplier(int extraMultiplier) {
        this.extraMultiplier = extraMultiplier;
    }

    public double getTemperature() {
        return temperature;
    }

    public double getHumidity() {
        return humidity;
    }

    public double getPressure() {
        return pressure;
    }

    public String getSensorName() {
        return sensorName;
    }
}
