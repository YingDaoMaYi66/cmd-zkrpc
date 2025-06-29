package zkrpc;

import com.zkrpc.HelloZkrpc;
import com.zkrpc.annotation.ZkrpcService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    //需要注入一个代理对象
    @ZkrpcService
    private HelloZkrpc helloZkrpc;

    @GetMapping("hello")
    public String hello(){
        return helloZkrpc.sayHi("provider");
    }

}
