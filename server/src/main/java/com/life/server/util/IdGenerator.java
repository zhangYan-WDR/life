package com.life.server.util;

import cn.hutool.core.util.IdUtil;

public final class IdGenerator {

    private IdGenerator() {
    }

    public static long nextId() {
        return IdUtil.getSnowflakeNextId();
    }
}
