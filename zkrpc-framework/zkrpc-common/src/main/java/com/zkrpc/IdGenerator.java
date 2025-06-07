package com.zkrpc;
import org.apache.zookeeper.data.Id;

import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 请求id的生成器
 */
public class IdGenerator {
//    这是单机版本的线程安全的id发号器，一旦变成集群状态，就不行了，意思就是说这个应用是给集群使用的，每一个服务都会有一个IdGenerator实例，这样的话没有意义
    //雪花算法 -- 世界上没有一片雪花是相同的
    //机房号（数据中心）5bit 32
    //机器号          5bit 32
    //时间戳（long 1970-1-1）原本64位表示的时间，b必须减少(64),自由选择一个较近的时间
    //比如公司成立的日期
    //同一个机房的同一个机器号的同一个时间可以因为并发量很大需要多个id
    //序列号12bit       5+5+42+32 = 64bit

    // 起始时间戳
    public static final long START_STAMP = DateUtil.get("2022-1-1").getTime();

    public static final long DATA_CENTER_BIT = 5L; // 数据中心ID占用的位数
    public static final long MACHINE_BIT = 5L; // 机器ID占用的位数
    public static final long SEQUENCE_BIT = 12L; // 序列号占用的位数

    //最大值 Math.pow(2, n) - 1
    public static final long DATA_CENTER_MAX = ~(-1L << DATA_CENTER_BIT); // 数据中心ID最大值
    public static final long MACHINE_MAX = ~(-1L << MACHINE_BIT); // 机器ID最大值
    public static final long SEQUENCE_MAX = ~(-1L << SEQUENCE_BIT); // 序列号最大值

    // 1、时间戳（42） 机房号码（5） 机器号（5） 序列号（12）
    // 101010101010101010101010101010101010101011 10101 10101 101010101010

    //时间戳需要移动22位
    public static final long TIMESTAMP_LEFT = SEQUENCE_BIT + MACHINE_BIT + DATA_CENTER_BIT;
    //机房号码需要移动17位
    public static final long DATA_CENTER_LEFT = SEQUENCE_BIT + MACHINE_BIT;
    //机器号需要移动12位
    public static final long MACHINE_LEFT = SEQUENCE_BIT;
    //序列号不需要移动

    private Long dataCenterId;
    private Long machineId;
    private AtomicLong sequenceId = new AtomicLong(0);

    //时钟回拨的问题，我们需要去处理
    private long lastTimeStamp = -1L;

    public IdGenerator(Long dataCenterId, Long machineId) {
        //判断传入的参数是否合法
        if(dataCenterId > DATA_CENTER_MAX || machineId > MACHINE_MAX) {
            throw new IllegalArgumentException("你传入的数据中心编号或机器号不合法");
        }
        this.dataCenterId = dataCenterId;
        this.machineId = machineId;
    }

    public synchronized long getId(){
        //处理时间戳的问题
        long currentTime = System.currentTimeMillis();
        //从START_STAMP的年份到几十年上百年后
        long timestamp = currentTime - START_STAMP;
        //判断时钟回拨
        if(timestamp < lastTimeStamp) {
            timestamp = lastTimeStamp + 1;
        }
        //sequenceId需要做一些处理，如果是同一个时间节点，必须自增
        if(timestamp == lastTimeStamp){
            long sequence = sequenceId.incrementAndGet();
            //防止超高并发下，在统一时间戳的情况下，sequenceId超过最大值，让系统等待
            if (sequence >= SEQUENCE_MAX) {
                //序列号溢出，等待下一个时间戳
                timestamp = getNextTimeStamp();
                sequenceId.set(0);
            }
        }else{
            sequenceId.set(0);
        }

        //执行结束将时间戳赋值给lastTimeStamp
        lastTimeStamp = timestamp;
        long sequence = sequenceId.get();
        return timestamp << TIMESTAMP_LEFT | dataCenterId << DATA_CENTER_LEFT | machineId << MACHINE_LEFT | sequence;
    }

    /**
     * 防止在同一个时间戳内将sequence用光，拿到下一个时间戳
     * @return 时间戳
     */
    private long getNextTimeStamp() {
        //获取当前的时间戳
        long current = System.currentTimeMillis() - START_STAMP;
        //如果一样就等待下一个时间戳
        while (current == lastTimeStamp){
            current = System.currentTimeMillis() - START_STAMP;
        }
        return current;
    }

    public static void main(String[] args) {
        IdGenerator idGenerator = new IdGenerator(1L, 2L);
        ConcurrentSkipListSet<Long> idSet = new ConcurrentSkipListSet<>();
        int threadCount = 1000000;
        long start = System.currentTimeMillis();
        for (int i = 0; i < threadCount; i++) {
            idGenerator.getId();
        }
        long end = System.currentTimeMillis();
        System.out.println(end - start);
//        for (int i = 0; i < threadCount; i++) {
//            new Thread(() -> {
//                long id = idGenerator.getId();
//                if (!idSet.add(id)) {
//                    System.out.println("重复的ID: " + id);
//                }
//            }).start();
//        }


    }
}
