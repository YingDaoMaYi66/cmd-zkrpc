package zkrpc;

import com.zkrpc.annotation.ZkrpcService;
import com.zkrpc.proxy.ZkrpcProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

@Component
public class ZkrpcProxuBeanPostProcessor implements BeanPostProcessor {

    //他会拦截所有bean的创建，并在每一个bean初始化后被调用
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        //想办法生成一个代理
        Field[] fields = bean.getClass().getDeclaredFields();

        for(Field field : fields){
            ZkrpcService zkrpcService = field.getAnnotation(ZkrpcService.class);
            if(zkrpcService != null){
                //获取一个代理
                //获得一个字段的Class对象
                Class<?> type = field.getType();
                //设置私有属性可以访问
                field.setAccessible(true);
                Object proxy = ZkrpcProxyFactory.getProxy(type);
                try {
                    field.set(bean,proxy );
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return bean;
    }
}
