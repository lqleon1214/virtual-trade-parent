package com.itheima.recharge;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;

@SpringBootApplication
public class MockApplication {

	@Value("${openProxy}")
	String openProxy ="0";

	public static void main(String[] args) {
		SpringApplication.run(MockApplication.class, args);
	}

	@Bean
	RestTemplate restTemplate() {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(60000);
		requestFactory.setReadTimeout(60000);
		InetSocketAddress addr = new InetSocketAddress("127.0.0.1",8888);
		Proxy proxy = new Proxy(Proxy.Type.HTTP, addr); // http 代理
		if("1".equals(openProxy)) {
			requestFactory.setProxy(proxy);
		}
		return new RestTemplate(requestFactory);
	}

}
