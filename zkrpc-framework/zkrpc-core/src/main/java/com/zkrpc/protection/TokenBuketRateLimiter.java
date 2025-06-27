package com.zkrpc.protection;

/**
 * 基于令牌桶算法的限流器
 */
public class TokenBuketRateLimiter implements RateLimiter {
    //思考:令牌桶是个什么？令牌桶是个什么？

    // 代表令牌的数量    >0 说明有令牌，能放行，放行就减一， == 0 说明没有令牌，不能放行
    private int tokens;
    // 令牌桶的容量
    private  final int capacity;
    //；令牌桶的令牌，如果没了要怎么办？ 按照一定的速率给令牌桶添加令牌，如每秒添加500个
    //可以使用定时任-->启动一个定时任务，美妙启动一次,tokens+500 不能超过 capacity(不好)
    //杜宇单机版的限流器可以有更好的操作，每次请求发送的是由，添加一下令牌就好
    private  final int rate;
    //上一次放令牌的时间
    private Long lastTokenTime;

    public TokenBuketRateLimiter(int capacity, int rate) {
        this.capacity = capacity;
        this.rate = rate;
        lastTokenTime = System.currentTimeMillis();
        tokens = 0;
    }

    /**
     * 判断请求是否可以放行
     * @return 是否允许请求
     */
    public synchronized boolean allowRequest() {
        // 1、给令牌桶添加令牌
        // 计算从现在到上一次的时间间隔需要添加的令牌数
        Long currentTime = System.currentTimeMillis();
        long timeInterval = currentTime - lastTokenTime;
        //如果间隔时间超过一秒，放入令牌
        if (timeInterval >=1000/rate) {
            int needAddTokens = (int)(timeInterval * rate / 1000);
            System.out.print("needAddTokens = " + needAddTokens);
            tokens = Math.min(capacity, tokens + needAddTokens);
            System.out.println("tokens = " + tokens); 
            // 标记最后一次放入令牌的时间
            this.lastTokenTime = currentTime;
            
        }

        //2、自己获取令牌,如果令牌桶有令牌，就放行，否则就拦截
        if (tokens > 0) {
            tokens--;
            System.out.print("请求被放行-----------");
            return true;
        }else{
            System.out.print("请求被限流-----------");
            return false;
        }
     }
// 限流器测试方法
public static void main(String[] args) throws InterruptedException {
    // 创建一个容量为5，速率为2的令牌桶限流器（每秒2个令牌，最多5个）
    TokenBuketRateLimiter limiter = new TokenBuketRateLimiter(10, 10);

    // 模拟20次请求，每100毫秒发起一次
    for (int i = 1; i <= 1000; i++) {
        try{
            Thread.sleep(10);
        }catch(InterruptedException e){
            throw new RuntimeException(e);
        }
        boolean allowRequest = limiter.allowRequest();
        System.out.println("allowRequest = "+ allowRequest);
    }
}


}
