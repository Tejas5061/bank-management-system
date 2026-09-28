package com.bankms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "number_sequences")
@Getter
@NoArgsConstructor
public class NumberSequence {

    @Id
    @Column(length = 30)
    private String name;

    @Column(name = "next_value", nullable = false)
    private long nextValue;

    /** Returns the current value and advances the sequence. The caller must hold the row lock. */
    public long takeNext() {
        return nextValue++;
    }
}
