package com.desc.calc.processor.obj;

import lombok.Getter;
import lombok.Setter;

public enum CtrConflict {

    TOO_HIGH,
    TOO_LOW;

    @Setter
    @Getter
    public int value;
}
