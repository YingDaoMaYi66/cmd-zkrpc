package com.zkrpc.protection;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * 最简单的熔断器实现
 */
public class CircuitBreaker {
    
    //理论上，标准的断路器应该有三种状态，open流量锁死出不去   close流量正常接受释放   half_open：释放一个请求，如果成功，则关闭，如果失败，则打开

    // volatile关键字用于保证多线程环境下变量的可见性和有序性。被volatile修饰的变量在被一个线程修改后，其他线程能够立即看到最新的值，防止线程缓存带来的数据不一致问题。
    private volatile boolean isOpen = false; // 是否处于打开（熔断）状态，volatile保证多线程下的可见性
    //总的请求数
    private AtomicInteger requestCount =  new AtomicInteger(0);
    //异常的请求数
    private AtomicInteger erroorRequest = new AtomicInteger(0 );
    //异常的阈值
    private int maxErrorRequest;
    private float maxErrorRate;

    public CircuitBreaker(int maxErrorRequest, float maxErrorRate) {
        this.maxErrorRequest = maxErrorRequest;
        this.maxErrorRate = maxErrorRate;
    }

    //断路器的核心方法，判断是否开启
    public boolean isBreak(){
        //优先返回，如果已经打开了，就直接返回true
        if (isOpen) {
            return true;
        }
        //需要判断数据指标，是否满足当前的阈值
        if ( erroorRequest.get() > maxErrorRequest) {
            this.isOpen = true;
            return true;
        }

        if (erroorRequest.get()>0 && requestCount.get()>0 &&
            erroorRequest.get()/(float)requestCount.get()> maxErrorRate
        ){
            this.isOpen = true;
            return true;
            
        }
        //闭合的
        return false;
    }

    //每次发生请求,获取发生异常应该进行记录
    public void recordRequest(){
        this.requestCount.getAndIncrement();
    }   
    public void recordErrorRequest(){
        this.erroorRequest.getAndIncrement();
    }
    /**
     * 重置熔断器
     */
    public void reset(){
        this.isOpen = false;
        this.requestCount.set(0);
        this.erroorRequest.set(0);
    }
    public static void main(String[] args) {

        CircuitBreaker circuitBreaker = new CircuitBreaker(3, 1.1F);
        for(int i = 0; i<1000 ; i++){
            circuitBreaker.recordRequest();
            int num = new Random().nextInt(100);
            if (num > 70) {
                circuitBreaker.recordErrorRequest();
            }

            boolean aBreak = circuitBreaker.isBreak();
        
            String result = aBreak ? "断路器阻塞了请求":"断路器放行了请求";

            System.out.println(result);
        }
    }

}
