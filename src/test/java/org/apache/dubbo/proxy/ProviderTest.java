package org.apache.dubbo.proxy;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * <p> Description:
 * <p>
 * <p>
 *
 * @author sunshujie 2022/4/18
 */
public class ProviderTest {
    public static void main(String[] args) {
        ServiceConfig<Test> testServiceConfig = new ServiceConfig<>();
        testServiceConfig.setInterface(ProxyTest.class);
        testServiceConfig.setRef(new Test());
        ApplicationConfig applicationConfig = new ApplicationConfig();
        applicationConfig.setQosEnable(false);
        applicationConfig.setName("test");
        RegistryConfig registryConfig = new RegistryConfig();
        registryConfig.setAddress("zookeeper://zookeeper-0-svc:4180");
        applicationConfig.setRegistry(registryConfig);
        testServiceConfig.setApplication(applicationConfig);
        testServiceConfig.export();

        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static class Test implements ProxyTest {

        @Override
        public String test(String a) {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(10) * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return a + ".resp";
        }
    }
}
