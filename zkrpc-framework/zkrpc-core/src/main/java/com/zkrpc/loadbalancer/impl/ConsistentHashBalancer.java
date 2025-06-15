package com.zkrpc.loadbalancer.impl;
import com.zkrpc.ZkrpcBootstrap;
import com.zkrpc.loadbalancer.AbstractLoadBalancer;
import com.zkrpc.loadbalancer.Selector;
import com.zkrpc.transport.message.ZkrpcRequest;
import lombok.extern.slf4j.Slf4j;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 轮询负载均衡策略
 */
@Slf4j
public class ConsistentHashBalancer extends AbstractLoadBalancer {

    @Override
    protected Selector getSelector(List<InetSocketAddress> serviceList) {
        return new ConsistentHashSelector(serviceList,128);
    }

    /**
     * 一致性hash的算法具体实现
     */
    private static class ConsistentHashSelector implements Selector{

        //hash环用来存储服务器节点
        private SortedMap<Integer,InetSocketAddress> circle = new TreeMap<>();
        //虚拟节点的个数
        private int virtualNodes;



        public ConsistentHashSelector(List<InetSocketAddress> serviceList,int virtualNodes) {
            //我们应该尝试将节点转化为虚拟节点，进行挂载
            this.virtualNodes = virtualNodes;
            for (InetSocketAddress inetSocketAddress : serviceList) {
                //需要把每一个节点加入到hash环中
                addNodeToCircle(inetSocketAddress);
            }
        }

        /**
         * 将每个节点挂载到hash环上
         * @param inetSocketAddress 节点的地址
         */
        private void addNodeToCircle(InetSocketAddress inetSocketAddress) {
            //为每一个节点生成匹配的虚拟节点进行挂载
            for (int i = 0; i< virtualNodes;i++){
                int hash = hash(inetSocketAddress.toString() + "-"+i);
                //挂载到hash环上
                circle.put(hash,inetSocketAddress);
                if (log.isDebugEnabled()){
                    log.debug("hash为[{}]的节点已经挂载到了哈希环上.",hash);
                }
            }
        }

        private void removeNodeFromCircle(InetSocketAddress inetSocketAddress) {
            //为每一个节点生成匹配的虚拟节点进行挂载
            for (int i = 0; i< virtualNodes;i++){
                int hash = hash(inetSocketAddress.toString() + "-"+i);
                //挂载到hash环上
                circle.remove(hash);
            }
        }
        @Override
        public InetSocketAddress getNext() {
            //1、hash环已经准备好了，接下来需要对请求的要素做处理，我们应该选择什么的要素来进行hash运算
            //有没有办法可以获取到具体的请求内容，yrpcRequest --> threadLocal
            ZkrpcRequest zkrpcRequest = ZkrpcBootstrap.REQUEST_THREAD_LOACL.get();
            //我们想根据请求的一些特征来选择服务器  id  但是string的hashcode的计算方式，会让连续的字符串，具有近似连续的hash值
            String requestId = Long.toString(zkrpcRequest.getRequestId());
            //请求的id做hash，字符串默认的hash不太好
            int hash = hash(requestId);
            //判断该hash值是否能落在服务上，和服务器上的hassh
            if (!circle.containsKey(hash)) {
                //寻找理我最近的一个节点
                //tailMao这个函数会寻找比这个hash值大的所有hash值，然后组成新的hashmap，它会将所有比传入hash值大的红黑树返回
                SortedMap<Integer, InetSocketAddress> tailMap = circle.tailMap(hash);
                hash = tailMap.isEmpty() ? circle.firstKey() : tailMap.firstKey();
            }
            return circle.get(hash);

        }


        /**
         * 具体的hash算法 todo 小小的遗憾，这样也是不均衡的
         * @param s string
         * @return int
         */
        private  int hash(String s){

            MessageDigest md;
            try{
                md = MessageDigest.getInstance("MD5");
            }catch (NoSuchAlgorithmException e){
                throw new RuntimeException(e);
            }

            byte[] digest = md.digest(s.getBytes());
            //md5得到的结果是一个字节数组，但是我们想要得到int 4个字节

            int res = 0;
            for (int i = 0; i < 4; i++) {
                res = res << 8;
                if (digest[i] < 0) {
                    res = res | (digest[i] & 255);
                }else{
                    res = res | digest[i];
                }
            }
            return res;
        }



        private String toBinary(int i){
            String s = Integer.toBinaryString(i);
            int index = 32 -s.length();
            StringBuilder sb = new StringBuilder();
            for (int j= 0; j< index; j++){
                sb.append(0);
            }
            sb.append(s);
            return sb.toString();
        }

    }


}
