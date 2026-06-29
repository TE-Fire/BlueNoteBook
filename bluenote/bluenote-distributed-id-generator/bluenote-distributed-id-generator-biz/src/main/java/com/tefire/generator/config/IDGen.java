package com.tefire.generator.config;

import com.tefire.generator.config.common.Result;

public interface IDGen {
    Result get(String key);
    boolean init();
}
