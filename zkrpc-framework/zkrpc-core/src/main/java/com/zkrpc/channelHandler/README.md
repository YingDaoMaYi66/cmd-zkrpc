1、服务调用方

发送报文 writeAndFlush(object) 请求
此 object 应该是什么？ 应该包含什么样的信息？

YrpcRequest（
1、请求id（long）
2、压缩类型（1byte） 
3、序列化的方式（1byte）
4、负载 payload（接口的名字，方法的名字，参数列表）（不定长）
5、消息类型（普通请求、心跳检测请求））（1byte）
当发送报文后，pipeline 生效，报文开始出站，依次经过以下处理器（out 表示输出方向）
---->第一个处理器（将 object 转化为 msg（请求报文）进行序列化 进行压缩）



服务提供方
通过 netty 接受报文，pipeline 生效，报文开始入站，第一个处理器（进行解压缩） 。