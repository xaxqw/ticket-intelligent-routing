package com.ticket.common.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;

/**
 * 雪花算法 ID 生成器（workerId/dataCenterId 可通过配置注入）。
 * 用于生成全局唯一且不依赖数据库的工单 id / 历史记录 id。
 */
public class SnowflakeId {

    private final long workerId;
    private final long dataCenterId;
    private long sequence = 0L;

    private static final long EPOCH = 1700000000000L; // 2023-11-14，自定义基准
    private static final long WORKER_BITS = 5L;
    private static final long DATACENTER_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER = ~(-1L << WORKER_BITS);
    private static final long MAX_DATACENTER = ~(-1L << DATACENTER_BITS);
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_SHIFT = SEQUENCE_BITS + WORKER_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_BITS + DATACENTER_BITS;

    private long lastTimestamp = -1L;

    public SnowflakeId(long workerId, long dataCenterId) {
        if (workerId > MAX_WORKER || workerId < 0) throw new IllegalArgumentException("workerId 越界");
        if (dataCenterId > MAX_DATACENTER || dataCenterId < 0) throw new IllegalArgumentException("dataCenterId 越界");
        this.workerId = workerId;
        this.dataCenterId = dataCenterId;
    }

    public synchronized long nextId() {
        long timestamp = now();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("时钟回拨，拒绝生成 ID");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (dataCenterId << DATACENTER_SHIFT)
                | (workerId << WORKER_SHIFT)
                | sequence;
    }

    public String nextStr() {
        return String.valueOf(nextId());
    }

    private long now() {
        return Instant.now().toEpochMilli();
    }

    private long waitNextMillis(long last) {
        long t = now();
        while (t <= last) t = now();
        return t;
    }

    /** 基于本机 IP 末位推导 workerId，方便无配置启动 */
    public static SnowflakeId auto() {
        long wid = 1L;
        try {
            byte[] addr = InetAddress.getLocalHost().getAddress();
            wid = (addr[addr.length - 1] & 0xFF) % 32L;
        } catch (UnknownHostException ignored) {
        }
        return new SnowflakeId(wid, 1L);
    }
}
