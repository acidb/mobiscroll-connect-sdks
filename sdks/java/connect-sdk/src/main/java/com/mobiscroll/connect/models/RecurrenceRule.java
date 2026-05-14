package com.mobiscroll.connect.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * iCal-style recurrence rule. {@code frequency} is {@code "DAILY"}, {@code "WEEKLY"},
 * {@code "MONTHLY"}, or {@code "YEARLY"}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class RecurrenceRule {

    private final String frequency;
    private final Integer interval;
    private final Integer count;
    private final String until;
    private final List<String> byDay;
    private final List<Integer> byMonthDay;
    private final List<Integer> byMonth;

    @JsonCreator
    public RecurrenceRule(
            @JsonProperty("frequency") String frequency,
            @JsonProperty("interval") Integer interval,
            @JsonProperty("count") Integer count,
            @JsonProperty("until") String until,
            @JsonProperty("byDay") List<String> byDay,
            @JsonProperty("byMonthDay") List<Integer> byMonthDay,
            @JsonProperty("byMonth") List<Integer> byMonth) {
        this.frequency = frequency;
        this.interval = interval;
        this.count = count;
        this.until = until;
        this.byDay = byDay;
        this.byMonthDay = byMonthDay;
        this.byMonth = byMonth;
    }

    private RecurrenceRule(Builder b) {
        this(b.frequency, b.interval, b.count, b.until, b.byDay, b.byMonthDay, b.byMonth);
    }

    public String getFrequency() { return frequency; }
    public Integer getInterval() { return interval; }
    public Integer getCount() { return count; }
    public String getUntil() { return until; }
    public List<String> getByDay() { return byDay; }
    public List<Integer> getByMonthDay() { return byMonthDay; }
    public List<Integer> getByMonth() { return byMonth; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String frequency;
        private Integer interval;
        private Integer count;
        private String until;
        private List<String> byDay;
        private List<Integer> byMonthDay;
        private List<Integer> byMonth;

        public Builder frequency(String v)        { this.frequency = v; return this; }
        public Builder interval(Integer v)        { this.interval = v; return this; }
        public Builder count(Integer v)           { this.count = v; return this; }
        public Builder until(String v)            { this.until = v; return this; }
        public Builder byDay(List<String> v)      { this.byDay = v; return this; }
        public Builder byMonthDay(List<Integer> v){ this.byMonthDay = v; return this; }
        public Builder byMonth(List<Integer> v)   { this.byMonth = v; return this; }

        public RecurrenceRule build() { return new RecurrenceRule(this); }
    }
}
