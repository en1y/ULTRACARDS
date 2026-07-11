package com.ultracards.recorder;

import jakarta.persistence.Embeddable;

@Embeddable
public class RecordedCard {
    private String suit;
    private String value;
    private int number;

    protected RecordedCard() {
    }

    public RecordedCard(String suit, String value, int number) {
        this.suit = suit;
        this.value = value;
        this.number = number;
    }

    public String suit() {
        return suit;
    }

    public String value() {
        return value;
    }

    public int number() {
        return number;
    }
}
